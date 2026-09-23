package com.example.velocitysuites;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionHistoryActivity extends BaseNavigationActivity {

    /** Intent extra key + values used by DashboardActivity's Payment Status/Recent Bookings sections to land directly on a given chip. */
    public static final String EXTRA_OPEN_FILTER = "OPEN_FILTER";
    public static final String FILTER_PAYMENTS = "Payments";
    public static final String FILTER_BOOKINGS = "Bookings";
    /** Booking-vs-Reservation Payment Status routing (DashboardActivity#openTransactionHistoryForPaymentItem): a still-unconverted Reservation-type transaction opens under this chip. */
    public static final String FILTER_RESERVATIONS = "Reservations";
    /** Optional Booking ID to scroll to and highlight, passed when a specific dashboard payment/booking item is tapped. */
    public static final String EXTRA_SELECTED_BOOKING_ID = "SELECTED_BOOKING_ID";

    private RecyclerView rvTransactions;
    private View layoutEmptyState;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;
    private android.widget.AutoCompleteTextView dropdownTransactionStatus;
    private TextView tvEmptyTitle, tvEmptyDesc;
    private MaterialButton btnEmptyAction;

    private PaymentTransactionAdapter adapter;
    private RoomRepository repository;
    private List<Booking> allBookings = new ArrayList<>();
    private List<Booking> filteredBookings = new ArrayList<>();
    /** Payment-level rows rendered by rvTransactions - flattened from filteredBookings every time applyFilters() runs, see buildPaymentTransactions(). Booking-level filtering itself is completely unchanged. */
    private List<PaymentTransaction> displayedTransactions = new ArrayList<>();
    private String currentFilter = "All";
    private String searchQuery = "";
    private String selectedRoomType = null;
    private String selectedBookingStatus = null;
    private String exportFormat = "PDF";
    private boolean isExportingReport = true;
    private androidx.core.util.Pair<Long, Long> selectedDateRange = null;
    private String selectedBookingId = null;
    private boolean pendingScrollToSelected = false;

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri != null) {
                    saveFileToUri(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transactionhistory);
        setupGuestNavigation(R.id.nav_transaction_history);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);
        
        rvTransactions = findViewById(R.id.rvTransactions);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etSearch = findViewById(R.id.etSearch);
        dropdownTransactionStatus = findViewById(R.id.dropdownTransactionStatus);

        // The empty state is a shared include (view_empty_state.xml) whose title/description/
        // action are populated in code - without this it renders as a blank card (icon only).
        tvEmptyTitle = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyTitle) : null;
        tvEmptyDesc = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyDesc) : null;
        ImageView emptyIcon = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyIcon) : null;
        btnEmptyAction = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.btnEmptyAction) : null;
        if (emptyIcon != null) emptyIcon.setImageResource(R.drawable.ic_transaction_history);
        if (btnEmptyAction != null) {
            btnEmptyAction.setText(R.string.clear_filters);
            btnEmptyAction.setIconResource(R.drawable.ic_filter);
            btnEmptyAction.setOnClickListener(v -> clearAllFiltersAndSearch());
        }

        setupRecyclerView();
        setupFilters();
        setupSearch();
        setupSwipeRefresh();

        String openFilter = getIntent().getStringExtra(EXTRA_OPEN_FILTER);
        if (FILTER_PAYMENTS.equals(openFilter)) {
            setStatusFilter("Payments");
        } else if (FILTER_BOOKINGS.equals(openFilter)) {
            setStatusFilter("Bookings");
        } else if (FILTER_RESERVATIONS.equals(openFilter)) {
            setStatusFilter("Reservations");
        }

        selectedBookingId = getIntent().getStringExtra(EXTRA_SELECTED_BOOKING_ID);
        pendingScrollToSelected = selectedBookingId != null;

        loadTransactions();

        View btnExport = findViewById(R.id.btnExport);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> showExportOptions());
        }
    }

    private void showExportOptions() {
        String[] options = {getString(R.string.export_as_pdf), getString(R.string.export_as_csv)};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_options_title)
                .setItems(options, (dialog, which) -> {
                    String format = which == 0 ? "PDF" : "CSV";
                    performExport(format);
                })
                .show();
    }

    private void performExport(String format) {
        this.exportFormat = format;
        this.isExportingReport = true;
        String mimeType = format.equals("PDF") ? "application/pdf" : "text/csv";
        String fileName = "Transactions_" + System.currentTimeMillis() + (format.equals("PDF") ? ".pdf" : ".csv");
        createDocumentLauncher.launch(fileName);
    }

    private void saveFileToUri(Uri uri) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        TextView tvMsg = dialogView.findViewById(R.id.loadingMessage);
        
        if (isExportingReport) {
            tvMsg.setText(getString(R.string.msg_exporting_format, exportFormat));
        } else {
            tvMsg.setText(R.string.downloading_receipt);
        }
        
        builder.setView(dialogView);
        builder.setCancelable(false);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        new Thread(() -> {
            boolean success = false;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (isExportingReport) {
                    if (exportFormat.equals("PDF")) {
                        generatePdf(os);
                    } else {
                        generateCsv(os);
                    }
                }
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                // The file write itself already completed (or didn't) - that real
                // outcome isn't undone by the guest having navigated away in the
                // meantime; only the now-pointless dialog/Toast UI is skipped.
                dismissSafely(dialog);
                if (isFinishing() || isDestroyed()) return;
                if (finalSuccess) {
                    Toast.makeText(this, isExportingReport ?
                            getString(R.string.msg_export_success, getString(R.string.downloads_folder_name)) :
                            getString(R.string.receipt_saved_path), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.msg_export_error, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void generateCsv(OutputStream os) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("Booking ID,Room,Type,Check-In,Check-Out,Status,Amount,Transaction Ref\n");
        for (Booking b : filteredBookings) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    b.getId(),
                    b.getRoomName().replace(",", " "),
                    // Every room type, not just the first (see
                    // Booking#getAllRoomTypeNames()'s own doc) - joined with
                    // "; " since a single CSV cell can't hold a line break.
                    String.join("; ", b.getAllRoomTypeNames()).replace(",", " "),
                    b.getCheckInDate(),
                    b.getCheckOutDate(),
                    b.getStatus(),
                    b.getTotalAmount(),
                    b.getTransactionRef() != null ? b.getTransactionRef() : "N/A"));
        }
        os.write(csv.toString().getBytes());
    }

    private void generatePdf(OutputStream os) throws IOException {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        
        paint.setTextSize(18f);
        paint.setFakeBoldText(true);
        canvas.drawText("Velocity Suites - Transaction History", 50, 50, paint);
        
        paint.setTextSize(12f);
        paint.setFakeBoldText(false);
        canvas.drawText("Generated on: " + new java.util.Date().toString(), 50, 80, paint);
        canvas.drawText("Total Records: " + filteredBookings.size(), 50, 100, paint);

        int y = 140;
        paint.setFakeBoldText(true);
        canvas.drawText("ID", 50, y, paint);
        canvas.drawText("Room", 120, y, paint);
        canvas.drawText("Check-In", 300, y, paint);
        canvas.drawText("Status", 420, y, paint);
        canvas.drawText("Amount", 500, y, paint);
        
        paint.setFakeBoldText(false);
        y += 20;
        canvas.drawLine(50, y-10, 550, y-10, paint);

        for (Booking b : filteredBookings) {
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }
            canvas.drawText(b.getId(), 50, y, paint);
            canvas.drawText(b.getRoomName(), 120, y, paint);
            canvas.drawText(b.getCheckInDate(), 300, y, paint);
            canvas.drawText(b.getStatus(), 420, y, paint);
            canvas.drawText(String.valueOf(b.getTotalAmount()), 500, y, paint);
            y += 20;
        }

        document.finishPage(page);
        document.writeTo(os);
        document.close();
    }

    private void setupRecyclerView() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PaymentTransactionAdapter(new ArrayList<>(), this::showPaymentDetailsDialog);
        rvTransactions.setAdapter(adapter);
    }

    /**
     * Flattens filteredBookings (Booking-level, already filtered by every
     * existing chip/search/picker predicate unchanged) into one row per
     * individual payment event, newest first. A Booking with no itemized
     * paymentHistory yet (e.g. a Cash Pay-Later reservation nobody has paid
     * against) still gets exactly one synthetic summary row built from its
     * own current-snapshot fields, so it's never silently dropped from
     * history just for predating itemized payment tracking.
     */
    private List<PaymentTransaction> buildPaymentTransactions(List<Booking> bookings) {
        List<PaymentTransaction> result = new ArrayList<>();
        for (Booking b : bookings) {
            List<Booking.PaymentRecord> history = b.getPaymentHistory();
            if (history != null && !history.isEmpty()) {
                for (Booking.PaymentRecord record : history) {
                    result.add(new PaymentTransaction(b, record));
                }
            } else {
                result.add(new PaymentTransaction(b, null));
            }
        }
        java.util.Collections.sort(result, (a, c) -> Long.compare(c.getDateMillis(), a.getDateMillis()));
        return result;
    }

    /** Internal filter keys (used by applyFilters()'s switch), in the same order as STATUS_FILTER_LABEL_RES below - position-matched, not string-matched, so the dropdown's selection maps back to the correct key regardless of locale. */
    private static final String[] STATUS_FILTER_KEYS = {
            "All", "Bookings", "Reservations", "Payments", "Cancelled", "Stays", "FullyPaid", "PartiallyPaid", "PendingPayment"
    };
    private static final int[] STATUS_FILTER_LABEL_RES = {
            R.string.filter_all_transactions_label, R.string.filter_bookings, R.string.filter_reservations,
            R.string.filter_payments, R.string.filter_cancelled, R.string.filter_stays,
            R.string.status_fully_paid, R.string.status_partial_paid, R.string.filter_pending_payment
    };

    private void setupFilters() {
        String[] labels = new String[STATUS_FILTER_LABEL_RES.length];
        for (int i = 0; i < STATUS_FILTER_LABEL_RES.length; i++) labels[i] = getString(STATUS_FILTER_LABEL_RES[i]);
        dropdownTransactionStatus.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
        dropdownTransactionStatus.setOnItemClickListener((parent, view, position, id) -> {
            currentFilter = STATUS_FILTER_KEYS[position];
            applyFilters();
        });

        findViewById(R.id.chipRoomType).setOnClickListener(v -> showRoomTypeFilterDialog());
        findViewById(R.id.chipBookingStatus).setOnClickListener(v -> showBookingStatusFilterDialog());

        findViewById(R.id.chipDateFilter).setOnClickListener(v -> {
            if (selectedDateRange != null) {
                selectedDateRange = null;
                ((com.google.android.material.chip.Chip)v).setText(R.string.date_filter_label);
                applyFilters();
                return;
            }

            MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.date_filter_label)
                    .build();
            picker.show(getSupportFragmentManager(), "DATE_PICKER");
            picker.addOnPositiveButtonClickListener(selection -> {
                selectedDateRange = selection;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd", Locale.US);
                String rangeText = sdf.format(new java.util.Date(selection.first)) + " - " + sdf.format(new java.util.Date(selection.second));
                ((com.google.android.material.chip.Chip)v).setText(rangeText);
                applyFilters();
            });
        });
    }

    /** Sets currentFilter and reflects it in the dropdown's displayed text - used wherever the status filter needs to be changed programmatically (deep-link extras, Clear Filters, the deep-linked-booking-not-in-current-filter fallback) rather than by the guest tapping a dropdown item directly. */
    private void setStatusFilter(String key) {
        currentFilter = key;
        if (dropdownTransactionStatus == null) return;
        for (int i = 0; i < STATUS_FILTER_KEYS.length; i++) {
            if (STATUS_FILTER_KEYS[i].equals(key)) {
                dropdownTransactionStatus.setText(getString(STATUS_FILTER_LABEL_RES[i]), false);
                break;
            }
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase(Locale.US).trim();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        swipeRefresh.setOnRefreshListener(this::loadTransactions);
    }

    /** Shows the pull-to-refresh spinner for both the initial load and manual refreshes,
     *  so the guest gets visual feedback while transactions are being fetched. */
    private void loadTransactions() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                allBookings = result;
                applyFilters();
            }

            @Override
            public void onError(String message) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(TransactionHistoryActivity.this, getString(R.string.error_load_transactions_format, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Resets search text, filter chip, and date range, then re-applies - wired to the
     *  empty state's action button so guests can recover from an over-filtered view. */
    private void clearAllFiltersAndSearch() {
        if (etSearch != null) etSearch.setText("");
        setStatusFilter("All");
        if (selectedDateRange != null) {
            selectedDateRange = null;
            Chip dateChip = findViewById(R.id.chipDateFilter);
            if (dateChip != null) dateChip.setText(R.string.date_filter_label);
        }
        if (selectedRoomType != null) {
            selectedRoomType = null;
            Chip roomTypeChip = findViewById(R.id.chipRoomType);
            if (roomTypeChip != null) roomTypeChip.setText(R.string.room_type_filter_label);
        }
        if (selectedBookingStatus != null) {
            selectedBookingStatus = null;
            Chip statusChip = findViewById(R.id.chipBookingStatus);
            if (statusChip != null) statusChip.setText(R.string.booking_status_filter_label);
        }
        applyFilters();
    }

    /** Single-choice picker over the distinct statuses present among the guest's paid Bookings
     *  (isHasBooking() == true), e.g. Pending, Confirmed, Checked-In, Checked-Out, Cancelled. */
    private void showBookingStatusFilterDialog() {
        List<String> statuses = new ArrayList<>();
        for (Booking b : allBookings) {
            if (b.isHasBooking() && b.getStatus() != null && !statuses.contains(b.getStatus())) {
                statuses.add(b.getStatus());
            }
        }
        if (statuses.isEmpty()) {
            Toast.makeText(this, R.string.no_transactions_found, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = statuses.toArray(new String[0]);
        int checkedIndex = selectedBookingStatus != null ? statuses.indexOf(selectedBookingStatus) : -1;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.booking_status_filter_title)
                .setSingleChoiceItems(options, checkedIndex, (dialog, which) -> {
                    selectedBookingStatus = options[which];
                    Chip statusChip = findViewById(R.id.chipBookingStatus);
                    if (statusChip != null) statusChip.setText(selectedBookingStatus);
                    applyFilters();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.clear_filters, (dialog, which) -> {
                    selectedBookingStatus = null;
                    Chip statusChip = findViewById(R.id.chipBookingStatus);
                    if (statusChip != null) statusChip.setText(R.string.booking_status_filter_label);
                    applyFilters();
                })
                .show();
    }

    /** Single-choice picker over the distinct room types present in the guest's own bookings. */
    private void showRoomTypeFilterDialog() {
        // Every distinct room type across every transaction, including every
        // line of a multi-room-type one - not just each transaction's first/
        // legacy room type (see Booking#getAllRoomTypeNames()'s own doc for
        // why getRoomType() alone would silently omit a second/third type
        // from this list, e.g. "Suite" in a "Deluxe + Suite" booking).
        List<String> types = new ArrayList<>();
        for (Booking b : allBookings) {
            for (String roomTypeName : b.getAllRoomTypeNames()) {
                if (!types.contains(roomTypeName)) {
                    types.add(roomTypeName);
                }
            }
        }
        if (types.isEmpty()) {
            Toast.makeText(this, R.string.no_transactions_found, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = types.toArray(new String[0]);
        int checkedIndex = selectedRoomType != null ? types.indexOf(selectedRoomType) : -1;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.room_type_filter_title)
                .setSingleChoiceItems(options, checkedIndex, (dialog, which) -> {
                    selectedRoomType = options[which];
                    Chip roomTypeChip = findViewById(R.id.chipRoomType);
                    if (roomTypeChip != null) roomTypeChip.setText(selectedRoomType);
                    applyFilters();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.clear_filters, (dialog, which) -> {
                    selectedRoomType = null;
                    Chip roomTypeChip = findViewById(R.id.chipRoomType);
                    if (roomTypeChip != null) roomTypeChip.setText(R.string.room_type_filter_label);
                    applyFilters();
                })
                .show();
    }

    /** Lowercased human-readable payment status, so keyword search matches "fully"/"partial"/"pending". */
    private String paymentStatusSearchLabel(Booking b) {
        if (!b.isHasBooking()) return getString(R.string.no_payment_yet_label).toLowerCase(Locale.US);
        String status = b.getBillingStatus();
        if ("paid".equalsIgnoreCase(status)) return getString(R.string.status_fully_paid).toLowerCase(Locale.US);
        if ("partial".equalsIgnoreCase(status)) return getString(R.string.status_partial_paid).toLowerCase(Locale.US);
        return getString(R.string.status_pending_label).toLowerCase(Locale.US);
    }

    /** Same Cancelled+Rejected grouping TransactionCategorizer uses everywhere else. */
    private boolean isCancelledOrRejected(Booking b) {
        String status = b.getStatus();
        return "Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status);
    }

    private void applyFilters() {
        filteredBookings.clear();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US);

        for (Booking b : allBookings) {
            String paymentStatusLabel = paymentStatusSearchLabel(b);
            boolean matchesSearch = b.getId().toLowerCase(Locale.US).contains(searchQuery) ||
                                   b.getRoomName().toLowerCase(Locale.US).contains(searchQuery) ||
                                   b.anyRoomTypeContains(searchQuery) ||
                                   (b.getStatus() != null && b.getStatus().toLowerCase(Locale.US).contains(searchQuery)) ||
                                   (b.getTransactionRef() != null && b.getTransactionRef().toLowerCase(Locale.US).contains(searchQuery)) ||
                                   paymentStatusLabel.contains(searchQuery);

            boolean matchesFilter = false;
            switch (currentFilter) {
                case "All": matchesFilter = true; break;
                // Reservations = no payment made; Bookings = paid (even if
                // still pending staff verification) - same split as the
                // website's My Reservations / My Bookings. Rejected is folded
                // into the same bucket as Cancelled everywhere else in the app
                // (see TransactionCategorizer.categorize()) - kept consistent
                // here so a Rejected reservation shows up under "Cancelled"
                // instead of lingering in "Reservations" with no way to find it.
                case "Reservations": matchesFilter = !b.isHasBooking() && !isCancelledOrRejected(b); break;
                case "Bookings": matchesFilter = b.isHasBooking() && !isCancelledOrRejected(b); break;
                case "Payments": matchesFilter = b.getTransactionRef() != null; break;
                case "Cancelled": matchesFilter = isCancelledOrRejected(b); break;
                case "Stays": matchesFilter = "Checked-Out".equalsIgnoreCase(b.getStatus()) || "Checked-In".equalsIgnoreCase(b.getStatus()); break;
                case "FullyPaid": matchesFilter = b.isHasBooking() && "paid".equalsIgnoreCase(b.getBillingStatus()); break;
                case "PartiallyPaid": matchesFilter = b.isHasBooking() && "partial".equalsIgnoreCase(b.getBillingStatus()); break;
                case "PendingPayment": matchesFilter = b.isHasBooking() && (b.getBillingStatus() == null || "pending".equalsIgnoreCase(b.getBillingStatus())); break;
            }

            boolean matchesRoomType = selectedRoomType == null || b.hasRoomType(selectedRoomType);
            boolean matchesBookingStatus = selectedBookingStatus == null
                    || (b.isHasBooking() && selectedBookingStatus.equalsIgnoreCase(b.getStatus()));

            boolean matchesDate = true;
            if (selectedDateRange != null) {
                try {
                    long bookingTime = sdf.parse(b.getCheckInDate()).getTime();
                    matchesDate = bookingTime >= selectedDateRange.first && bookingTime <= selectedDateRange.second;
                } catch (Exception e) {
                    matchesDate = false;
                }
            }

            if (matchesSearch && matchesFilter && matchesRoomType && matchesBookingStatus
                    && matchesDate) {
                filteredBookings.add(b);
            }
        }

        // A deep-linked booking (e.g. tapped from the dashboard) must always be
        // reachable even if it doesn't match the chip it was opened on - e.g. a
        // Cancelled record opened on the Bookings chip, which excludes Cancelled.
        // Falls back to All so the highlight/scroll in applySelectedBookingHighlight()
        // never silently fails to find it.
        if (selectedBookingId != null && pendingScrollToSelected && !"All".equals(currentFilter)) {
            boolean selectedPresent = false;
            for (Booking b : filteredBookings) {
                if (selectedBookingId.equals(b.getId())) {
                    selectedPresent = true;
                    break;
                }
            }
            if (!selectedPresent) {
                // setStatusFilter() only updates the dropdown's displayed text - it doesn't
                // fire an item-click listener the way checking a Chip used to, so the
                // re-filter has to be triggered explicitly here.
                setStatusFilter("All");
                applyFilters();
                return;
            }
        }

        if (filteredBookings.isEmpty()) {
            rvTransactions.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);

            // Distinguish "nothing matches your filters/search" (recoverable via Clear
            // Filters) from "no transactions on the account yet" (nothing to clear) -
            // and give search vs. filter/payments its own wording per the empty-state spec.
            boolean filtersActive = !"All".equals(currentFilter) || !searchQuery.isEmpty() || selectedDateRange != null
                    || selectedRoomType != null || selectedBookingStatus != null;
            int descRes;
            if (allBookings.isEmpty()) {
                descRes = R.string.empty_transactions_no_activity_desc;
            } else if (!searchQuery.isEmpty()) {
                descRes = R.string.transaction_search_empty_desc;
            } else if ("Payments".equals(currentFilter)) {
                descRes = R.string.transaction_payment_empty_desc;
            } else {
                descRes = R.string.empty_transactions_desc;
            }
            // Same title everywhere a filter/search/sort yields nothing - the description
            // is what tells the guest why (no history yet vs. no match for their filter).
            if (tvEmptyTitle != null) tvEmptyTitle.setText(R.string.no_transactions_found);
            if (tvEmptyDesc != null) tvEmptyDesc.setText(descRes);
            if (btnEmptyAction != null) {
                btnEmptyAction.setVisibility(filtersActive ? View.VISIBLE : View.GONE);
            }
        } else {
            rvTransactions.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            displayedTransactions = buildPaymentTransactions(filteredBookings);
            adapter.updateList(displayedTransactions);
            applySelectedBookingHighlight();
        }
    }

    /**
     * Highlights the Booking ID passed via {@link #EXTRA_SELECTED_BOOKING_ID} (e.g. from a
     * dashboard Recent Bookings/Payment Status item tap), scrolls to it, and automatically
     * opens its full detail dialog - all once on initial load, so the guest lands directly on
     * the exact record's complete details without hunting for it. Highlight is re-applied on
     * every refresh (live-sync, pull-to-refresh) so it survives status changes, but the
     * scroll/auto-open only fire once so they don't fight the guest's own scrolling afterwards.
     */
    private void applySelectedBookingHighlight() {
        if (selectedBookingId == null) return;
        adapter.setHighlightedBookingId(selectedBookingId);
        if (!pendingScrollToSelected) return;
        int position = -1;
        for (int i = 0; i < displayedTransactions.size(); i++) {
            if (selectedBookingId.equals(displayedTransactions.get(i).parentBooking.getId())) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            final int scrollPosition = position;
            final PaymentTransaction selected = displayedTransactions.get(position);
            rvTransactions.post(() -> rvTransactions.smoothScrollToPosition(scrollPosition));
            showPaymentDetailsDialog(selected);
            pendingScrollToSelected = false;
        }
    }

    /**
     * "View Details" for a payment transaction row - opens TransactionDetailsActivity
     * (replaces the old inline dialog_payment_details.xml dialog; that layout's
     * field-binding logic was moved wholesale into the new Activity).
     */
    private void showPaymentDetailsDialog(PaymentTransaction transaction) {
        startActivity(TransactionDetailsActivity.newIntent(this, transaction));
    }

    private void simulateDownload() {
        // Method kept for compatibility or removed if not needed elsewhere
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTransactions();
    }
}
