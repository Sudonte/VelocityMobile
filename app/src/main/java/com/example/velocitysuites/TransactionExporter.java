package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Transaction History's Export: a CSV spreadsheet or a multi-page PDF of exactly the transactions the list is
 * showing (filters and search included), with the same status, grand total, paid and balance figures the cards
 * show - they come from the same {@link TransactionRow} snapshots, so an exported PAID is a card's PAID.
 * <p>
 * Everything here is written to be safe to run off the UI thread and to fail cleanly: null fields become empty
 * cells, CSV values are escaped per RFC 4180 (a room name with a comma or quote can't shift columns), the PDF
 * paginates instead of drawing off the page, and the PdfDocument is always closed. Callers catch the
 * IOException/RuntimeException and tell the guest - nothing in here swallows a failure.
 */
final class TransactionExporter {

    private TransactionExporter() {
    }

    private static final String[] CSV_HEADER = {
            "Type", "Reference", "Status", "Room", "Check-In", "Check-Out",
            "Grand Total", "Amount Paid (verified)", "Balance", "Submitted (awaiting verification)"
    };

    // ---- Writing to a document the guest picked ----

    /**
     * Writes the export to {@code uri} and reports how it went: null on success, else the guest-facing message to
     * show. Never throws - an unwritable location, a full disk, a provider that hands back no stream, or a bug
     * while drawing all end as a message (and a half-written file is removed, so it can't pass for a real report).
     * Synchronous by design: the caller runs it off the UI thread.
     */
    @androidx.annotation.Nullable
    static String exportToUri(@NonNull Context context, @NonNull android.net.Uri uri, boolean pdf,
                              @NonNull List<TransactionRow> rows, @NonNull String generatedOn) {
        String failure = null;
        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("No output stream for " + uri);
            if (pdf) {
                writePdf(out, context, rows, generatedOn);
            } else {
                writeCsv(out, context, rows);
            }
        } catch (IOException e) {
            failure = context.getString(R.string.msg_export_error_write);
        } catch (RuntimeException e) {
            failure = context.getString(R.string.msg_export_error_unexpected);
        }
        if (failure != null) discardPartialFile(context, uri);
        return failure;
    }

    /** A failed export must not leave an empty/half-written file behind, looking like a real report. */
    private static void discardPartialFile(Context context, android.net.Uri uri) {
        try {
            android.provider.DocumentsContract.deleteDocument(context.getContentResolver(), uri);
        } catch (Exception ignored) {
            // Best effort - the provider may not support deletion.
        }
    }

    // ---- File naming ----

    /** "Velocity_Suites_Transactions_20261001_2215.csv" - sortable, no characters a file system rejects. */
    static String fileName(String extension, long nowMillis) {
        return "Velocity_Suites_Transactions_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date(nowMillis))
                + "." + extension;
    }

    // ---- CSV ----

    static void writeCsv(@NonNull OutputStream out, @NonNull Context ctx, @NonNull List<TransactionRow> rows) throws IOException {
        out.write(csv(ctx, rows).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** UTF-8 with a BOM, so Excel opens it as UTF-8 (names may contain non-ASCII characters). Amounts are plain numbers, not "₱1,800.00" text, so a spreadsheet can sum them. */
    static String csv(Context ctx, List<TransactionRow> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendCsvLine(sb, CSV_HEADER);
        for (TransactionRow row : rows) {
            TransactionStatusHelper.Summary s = row.summary;
            appendCsvLine(sb, new String[]{
                    row.kind == TransactionRow.Kind.BOOKING ? "Booking" : "Reservation",
                    row.id,
                    ctx.getString(TransactionStatusHelper.styleFor(s.status).labelRes),
                    row.roomText,
                    row.checkIn,
                    row.checkOut,
                    plain(s.grandTotal),
                    plain(s.verifiedPaid),
                    plain(s.balance),
                    plain(s.pendingSubmitted),
            });
        }
        return sb.toString();
    }

    private static String plain(double amount) {
        return String.format(Locale.US, "%.2f", Double.isNaN(amount) || Double.isInfinite(amount) ? 0 : amount);
    }

    private static void appendCsvLine(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(cells[i]));
        }
        sb.append("\r\n");
    }

    /** RFC 4180: quote a cell containing a comma, quote, CR or LF, doubling any embedded quote. Also defuses spreadsheet formula injection (a cell starting with = + - @). */
    static String escapeCsv(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !looksLikeNumber(v)) v = "'" + v;
        boolean needsQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return needsQuotes ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static boolean looksLikeNumber(String v) {
        try {
            Double.parseDouble(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---- PDF ----

    private static final int PAGE_WIDTH = 595;   // A4 at 72dpi
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int LINE = 14;
    private static final int ROW_GAP = 8;
    private static final int ROW_HEIGHT = 2 * LINE + ROW_GAP;
    private static final int HEADER_HEIGHT = 54;   // title + subtitle + rule

    /** How many two-line records fit under the page header - fixed, because every record is the same height. */
    static int rowsPerPage() {
        return (PAGE_HEIGHT - 2 * MARGIN - HEADER_HEIGHT) / ROW_HEIGHT;
    }

    /** Always at least one page, so an empty export is still a valid document that says so. */
    static int pageCount(int records) {
        return Math.max(1, (records + rowsPerPage() - 1) / rowsPerPage());
    }

    /**
     * One record = two lines ("Reservation #588 · PENDING · Deluxe" and "Oct 01, 2026 – Oct 02, 2026 · Total ₱1,800.00 ·
     * Paid ₱0.00 · Balance ₱1,800.00"), with a header and "Page X of Y" on every page. A line too long for the page
     * is cut with an ellipsis rather than drawn off the edge.
     */
    static void writePdf(@NonNull OutputStream out, @NonNull Context ctx, @NonNull List<TransactionRow> rows,
                         @NonNull String generatedOn) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
            title.setTextSize(16f);
            title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
            body.setTextSize(10f);
            Paint bold = new Paint(body);
            bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            Paint muted = new Paint(body);
            muted.setColor(0xFF555555);

            int totalPages = pageCount(rows.size());
            int perPage = rowsPerPage();
            float textWidth = PAGE_WIDTH - 2f * MARGIN;

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PdfDocument.Page page = document.startPage(
                        new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create());
                Canvas canvas = page.getCanvas();
                canvas.drawText("Velocity Suites - Transaction History", MARGIN, MARGIN + 6, title);
                canvas.drawText("Generated " + generatedOn + "   |   " + rows.size() + (rows.size() == 1 ? " record" : " records")
                        + "   |   Page " + (pageIndex + 1) + " of " + totalPages, MARGIN, MARGIN + 32, muted);
                canvas.drawLine(MARGIN, MARGIN + 42, PAGE_WIDTH - MARGIN, MARGIN + 42, muted);

                int y = MARGIN + HEADER_HEIGHT;
                int first = pageIndex * perPage;
                int last = Math.min(rows.size(), first + perPage);
                if (rows.isEmpty()) {
                    canvas.drawText("No transactions to show.", MARGIN, y, body);
                }
                for (int i = first; i < last; i++) {
                    String[] lines = pdfLines(ctx, rows.get(i));
                    canvas.drawText(fit(lines[0], bold, textWidth), MARGIN, y, bold);
                    canvas.drawText(fit(lines[1], body, textWidth), MARGIN, y + LINE, body);
                    y += ROW_HEIGHT;
                }
                document.finishPage(page);
            }
            document.writeTo(out);
            out.flush();
        } finally {
            document.close();
        }
    }

    /** The two lines a record prints in the PDF: "Reservation #588  ·  PENDING  ·  Deluxe" and the stay/total/paid/balance line. Pure text, so it is unit-testable without drawing. */
    static String[] pdfLines(Context ctx, TransactionRow row) {
        TransactionStatusHelper.Summary s = row.summary;
        String status = ctx.getString(TransactionStatusHelper.styleFor(s.status).labelRes).toUpperCase(Locale.US);
        String kind = row.kind == TransactionRow.Kind.BOOKING ? "Booking" : "Reservation";
        String line1 = kind + " #" + row.id + "  ·  " + status + (row.roomText.isEmpty() ? "" : "  ·  " + row.roomText);
        String line2 = TransactionText.stay(ctx, row.checkIn, row.checkOut) + "  ·  Total " + MoneyFormat.format(s.grandTotal)
                + "  ·  Paid " + MoneyFormat.format(s.verifiedPaid) + "  ·  Balance " + MoneyFormat.format(s.balance)
                + (s.hasPendingPayment && MoneyFormat.isPositive(s.pendingSubmitted)
                ? "  ·  Submitted " + MoneyFormat.format(s.pendingSubmitted) : "");
        return new String[]{line1, line2};
    }

    /** Cuts {@code text} with an ellipsis so it is no wider than {@code maxWidth} in {@code paint}. */
    static String fit(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        int fits = paint.breakText(text, true, maxWidth - paint.measureText("…"), null);
        return text.substring(0, Math.max(0, fits)) + "…";
    }
}
