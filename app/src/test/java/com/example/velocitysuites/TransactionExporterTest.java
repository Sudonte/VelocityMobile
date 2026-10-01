package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Export produces real files: a CSV whose columns line up with the header whatever the data contains, and a PDF
 * that paginates. (Robolectric supplies the Android Context/PdfDocument these need; the screen-level Export
 * flow itself is covered in TransactionHistoryScreenTest.)
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransactionExporterTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private static Booking booking(String id, String status, double total, String roomType) {
        Booking b = new Booking(id, "1", roomType, roomType, "Oct 01, 2026", "Oct 02, 2026", 2, total, status, "");
        b.setHasBooking(false);
        return b;
    }

    private static void pay(Booking b, String amount, String status) {
        List<Booking.PaymentRecord> rows = new ArrayList<>(b.getPaymentHistory());
        rows.add(new Booking.PaymentRecord(amount, "GCASH", "REF", "Sep 30, 2026 • 10:09 PM", status));
        b.setPaymentHistory(rows);
        if ("pending".equals(status)) b.setPaymentPendingVerification(true);
    }

    // ---- CSV ----

    @Test
    public void csvHasAHeaderAndOneLinePerTransaction_withTheSameFiguresAsTheCards() {
        Booking pending = booking("588", "Pending", 1800, "Deluxe");
        pay(pending, "1800.00", "pending");
        Booking partial = booking("589", "Confirmed", 1800, "Suite");
        partial.setHasBooking(true);
        pay(partial, "900.00", "completed");

        String csv = TransactionExporter.csv(context, TransactionRow.fromAll(Arrays.asList(pending, partial)));
        String[] lines = csv.split("\r\n");

        assertTrue("UTF-8 BOM so Excel reads it as UTF-8", csv.startsWith("\uFEFF"));
        assertEquals(3, lines.length);
        assertEquals("\uFEFFType,Reference,Status,Room,Check-In,Check-Out,Grand Total,Amount Paid (verified),Balance,Submitted (awaiting verification)", lines[0]);
        // newest first (589 > 588 on the id tie-break): a Booking, Partially Paid, P900 of P1,800 paid
        assertEquals("Booking,589,Partially Paid,Suite,\"Oct 01, 2026\",\"Oct 02, 2026\",1800.00,900.00,900.00,0.00", lines[1]);
        // the submitted-but-unverified payment is NOT counted as paid
        assertEquals("Reservation,588,Pending,Deluxe,\"Oct 01, 2026\",\"Oct 02, 2026\",1800.00,0.00,1800.00,1800.00", lines[2]);
    }

    @Test
    public void csvSurvivesHostileDataWithoutShiftingColumns() {
        Booking b = booking("7", "Pending", 100, "Deluxe, \"Garden\" View\nSuite");
        String csv = TransactionExporter.csv(context, TransactionRow.fromAll(Collections.singletonList(b)));
        // Re-parse with a tiny RFC 4180 reader: still exactly one record of 10 cells after the header.
        List<List<String>> records = parseCsv(csv.substring(1));
        assertEquals(2, records.size());
        assertEquals(10, records.get(1).size());
        assertEquals("Deluxe, \"Garden\" View\nSuite", records.get(1).get(3));
    }

    @Test
    public void csvOfNothingIsJustTheHeader() {
        String csv = TransactionExporter.csv(context, Collections.<TransactionRow>emptyList());
        assertEquals(1, csv.split("\r\n").length);
    }

    @Test
    public void writeCsvWritesUtf8Bytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransactionExporter.writeCsv(out, context, TransactionRow.fromAll(Collections.singletonList(booking("1", "Pending", 100, "Deluxe"))));
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(text.contains("Reservation,1,Pending,Deluxe"));
    }

    // ---- PDF ----
    //
    // Robolectric's native runtime does not implement PdfDocument (its native handle is 0, so every call throws
    // "document is closed!"), so the tests that need the real renderer are skipped there and only run where a
    // real PdfDocument exists. What is ALWAYS verified: the text each record prints (pdfLines), the page maths,
    // the ellipsis cut, and - through the CSV path - the whole write-and-report machinery the PDF path shares.

    private static boolean pdfRendererAvailable() {
        try {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            document.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(10, 10, 1).create());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    public void eachPdfRecordPrintsTheSameFiguresAsItsCard() {
        Booking pending = booking("588", "Pending", 1800, "Deluxe");
        pay(pending, "1800.00", "pending");
        String[] lines = TransactionExporter.pdfLines(context, TransactionRow.from(pending));
        assertEquals("Reservation #588  ·  PENDING  ·  Deluxe", lines[0]);
        assertEquals("Oct 01, 2026 – Oct 02, 2026  ·  Total ₱1,800.00  ·  Paid ₱0.00  ·  Balance ₱1,800.00  ·  Submitted ₱1,800.00", lines[1]);

        Booking paid = booking("589", "Confirmed", 1800, "Suite");
        paid.setHasBooking(true);
        pay(paid, "1800.00", "completed");
        lines = TransactionExporter.pdfLines(context, TransactionRow.from(paid));
        assertEquals("Booking #589  ·  PAID  ·  Suite", lines[0]);
        assertEquals("Oct 01, 2026 – Oct 02, 2026  ·  Total ₱1,800.00  ·  Paid ₱1,800.00  ·  Balance ₱0.00", lines[1]);
    }

    @Test
    public void aRecordWithNoRoomOrDatesStillPrintsCleanLines() {
        Booking bare = booking("9", "Pending", 0, "Deluxe");
        bare.setRoomType(null);
        bare.setRoomName(null);
        bare.setCheckInDate(null);
        bare.setCheckOutDate(null);
        String[] lines = TransactionExporter.pdfLines(context, TransactionRow.from(bare));
        assertEquals("Reservation #9  ·  PENDING", lines[0]);
        assertTrue(lines[1], lines[1].startsWith("—  ·  Total ₱0.00"));
        assertFalse(lines[0] + lines[1], (lines[0] + lines[1]).contains("null"));
    }

    @Test
    public void pdfIsAValidDocument_evenForAnEmptyList() throws IOException {
        org.junit.Assume.assumeTrue("PdfDocument is not implemented by Robolectric's native runtime", pdfRendererAvailable());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransactionExporter.writePdf(out, context, Collections.<TransactionRow>emptyList(), "Oct 1, 2026");
        assertTrue(out.size() > 100);
        assertTrue(new String(out.toByteArray(), 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    public void pdfPaginatesLongLists() throws IOException {
        org.junit.Assume.assumeTrue("PdfDocument is not implemented by Robolectric's native runtime", pdfRendererAvailable());
        List<Booking> many = new ArrayList<>();
        for (int i = 1; i <= TransactionExporter.rowsPerPage() * 2 + 3; i++) {
            Booking b = booking(String.valueOf(i), "Pending", 1800, "Deluxe");
            many.add(b);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransactionExporter.writePdf(out, context, TransactionRow.fromAll(many), "Oct 1, 2026");
        String pdf = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF"));
        assertEquals("2 full pages + 3 rows = 3 pages", 3, TransactionExporter.pageCount(many.size()));
        // Page objects are "/Type /Page" (the page TREE is "/Type /Pages", counted out).
        int pageObjects = countOccurrences(pdf, "/Type /Page") - countOccurrences(pdf, "/Type /Pages");
        assertEquals("the document really has those 3 pages", 3, pageObjects);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int index = haystack.indexOf(needle); index >= 0; index = haystack.indexOf(needle, index + needle.length())) count++;
        return count;
    }

    @Test
    public void aLongLineIsCutWithAnEllipsis_notDrawnOffThePage() {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(10f);
        String cut = TransactionExporter.fit("Reservation #588  ·  PENDING  ·  " + "Executive Garden Suite ".repeat(20), paint, 200f);
        assertTrue(cut.endsWith("…"));
        assertTrue(paint.measureText(cut) <= 210f);
        assertEquals("short text is untouched", "Deluxe", TransactionExporter.fit("Deluxe", paint, 200f));
    }

    // ---- writing to the document the guest picked ----

    private static final android.net.Uri TARGET = android.net.Uri.parse("content://com.example.docs/export");

    @Test
    public void exportToUri_writesTheCsv_andReportsSuccess() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        org.robolectric.Shadows.shadowOf(context.getContentResolver()).registerOutputStream(TARGET, sink);

        String failure = TransactionExporter.exportToUri(context, TARGET, false,
                TransactionRow.fromAll(Collections.singletonList(booking("588", "Pending", 1800, "Deluxe"))), "Oct 1, 2026");

        assertEquals(null, failure);
        assertTrue(new String(sink.toByteArray(), StandardCharsets.UTF_8).contains("Reservation,588,Pending,Deluxe"));
    }

    @Test
    public void exportToUri_writesThePdf_andReportsSuccess() {
        org.junit.Assume.assumeTrue("PdfDocument is not implemented by Robolectric's native runtime", pdfRendererAvailable());
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        org.robolectric.Shadows.shadowOf(context.getContentResolver()).registerOutputStream(TARGET, sink);

        String failure = TransactionExporter.exportToUri(context, TARGET, true,
                TransactionRow.fromAll(Collections.singletonList(booking("588", "Pending", 1800, "Deluxe"))), "Oct 1, 2026");

        assertEquals(null, failure);
        assertTrue(new String(sink.toByteArray(), 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF"));
    }

    @Test
    public void anUnwritableLocation_endsInAClearMessage_notACrash() {
        org.robolectric.Shadows.shadowOf(context.getContentResolver()).registerOutputStream(TARGET, new java.io.OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("disk full"); }
        });

        String failure = TransactionExporter.exportToUri(context, TARGET, false,
                TransactionRow.fromAll(Collections.singletonList(booking("1", "Pending", 100, "Deluxe"))), "Oct 1, 2026");

        assertEquals(context.getString(R.string.msg_export_error_write), failure);
    }

    @Test
    public void aBugWhileWriting_endsInAClearMessage_notACrash() {
        org.robolectric.Shadows.shadowOf(context.getContentResolver()).registerOutputStream(TARGET, new java.io.OutputStream() {
            @Override public void write(int b) { throw new IllegalStateException("boom"); }
        });

        String failure = TransactionExporter.exportToUri(context, TARGET, false,
                TransactionRow.fromAll(Collections.singletonList(booking("1", "Pending", 100, "Deluxe"))), "Oct 1, 2026");

        assertEquals(context.getString(R.string.msg_export_error_unexpected), failure);
    }

    // ---- a minimal RFC 4180 reader, to prove the writer's output round-trips ----

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                records.add(cells);
                cells = new ArrayList<>();
                i++;
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            records.add(cells);
        }
        return records;
    }
}
