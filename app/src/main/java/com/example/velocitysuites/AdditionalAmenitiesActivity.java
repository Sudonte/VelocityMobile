package com.example.velocitysuites;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AdditionalAmenitySubmitRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Request Additional Amenities". The guest first sees every room of their stay (one room or several, of one room type or
 * several); tapping a room opens a picker where several amenities can be chosen for THAT room, each with a quantity. Different
 * rooms can get different amenities in the same request. Before sending, a summary lists each room with its amenities and the
 * total. The requests start PENDING; the front desk approves or rejects each one and only approved ones reach the bill.
 * Whether the stay may request at all (checked in, own booking) is decided by the server - this screen shows its answer, and
 * the reason when the answer is no.
 */
public class AdditionalAmenitiesActivity extends SafeActivity {

    public static final String EXTRA_DIRECT_BOOKING = "EXTRA_DIRECT_BOOKING";
    public static final String EXTRA_ID = "EXTRA_ID";
    /** Optional: the stay (of a booking with several) the request is for. Without it the guest picks one when several are in house. */
    public static final String EXTRA_STAY_ID = "EXTRA_STAY_ID";

    /** Sane stepper bound so a stray tap-and-hold can't ask for hundreds; the server enforces real stock. */
    private static final int MAX_QTY = 20;

    /** One room of the stay as the form shows it. {@code id == 0} stands for "your room" when an older server sent no room list. */
    private static final class RoomEntry {
        final long id;
        final String title;
        final String subtitle;
        /** The room number ("1-001"), or null for the generic "your room". */
        final String number;

        RoomEntry(long id, String title, String subtitle) {
            this(id, title, subtitle, null);
        }

        RoomEntry(long id, String title, String subtitle, String number) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.number = number;
        }
    }

    private RoomRepository repository;
    /** true: the id is a direct booking's; false: it is the reservation (a converted stay keeps the reservation id in the app). */
    private boolean hasBooking;
    private String id;

    private View stateBlock;
    private TextView stateText;
    private MaterialButton retryButton;
    private View form;
    private LinearLayout roomsList;
    private LinearLayout summaryList;
    private TextView totalLabel;
    private MaterialButton submitButton;
    private View bottomBar;
    private View hintAddOne;
    private final Map<Long, TextView> roomPickedLabels = new LinkedHashMap<>();

    private final List<AddOnAmenity> catalog = new ArrayList<>();
    private final List<RoomEntry> rooms = new ArrayList<>();
    /** The stay this request is for; 0 until known (a booking with one stay needs none). */
    private long stayId;
    /** room id -> quantity per catalog index. A room with all zeros simply has nothing chosen. */
    private final Map<Long, int[]> selections = new LinkedHashMap<>();
    private final Map<Long, TextView> roomSummaryLabels = new LinkedHashMap<>();
    private boolean submitting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.additional_amenities);

        hasBooking = getIntent().getBooleanExtra(EXTRA_DIRECT_BOOKING, false);
        id = getIntent().getStringExtra(EXTRA_ID);
        stayId = getIntent().getLongExtra(EXTRA_STAY_ID, 0L);
        repository = RoomRepository.getInstance(this);

        findViewById(R.id.btnAdditionalAmenitiesBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        stateBlock = findViewById(R.id.additionalAmenitiesState);
        stateText = findViewById(R.id.additionalAmenitiesStateText);
        retryButton = findViewById(R.id.additionalAmenitiesRetry);
        form = findViewById(R.id.additionalAmenitiesForm);
        roomsList = findViewById(R.id.additionalAmenitiesRooms);
        summaryList = findViewById(R.id.additionalAmenitiesSummary);
        totalLabel = findViewById(R.id.additionalAmenitiesTotal);
        submitButton = findViewById(R.id.additionalAmenitiesSubmit);
        bottomBar = findViewById(R.id.additionalAmenitiesBottomBar);
        hintAddOne = findViewById(R.id.additionalAmenitiesHint);

        retryButton.setOnClickListener(v -> load());
        submitButton.setOnClickListener(v -> confirmAndSubmit());
        UnsavedChanges.guard(this, this::hasSelection);

        if (id == null || id.isEmpty()) {
            showState(getString(R.string.additional_amenities_load_error), true);
            retryButton.setVisibility(View.GONE);
            return;
        }
        load();
    }

    private void showState(String message, boolean error) {
        form.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        stateBlock.setVisibility(View.VISIBLE);
        stateText.setText(message);
        retryButton.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void load() {
        showState(getString(R.string.additional_amenities_loading), false);
        repository.fetchAdditionalAmenities(hasBooking, id, new RoomRepository.RepositoryCallback<AdditionalAmenityInfoDto>() {
            @Override
            public void onSuccess(AdditionalAmenityInfoDto info) {
                if (isFinishing()) return;
                Booking known = currentStay();
                if (known != null) AmenityRoomSources.putApi(known, info);
                if (!info.can_request) {
                    showState(info.reason != null ? info.reason : getString(R.string.additional_amenities_not_available), false);
                    return;
                }
                // Several stays in a room right now: the guest says which one first (dates), then the room, then the amenities.
                if (stayId == 0L && info.stays != null && info.stays.size() > 1) {
                    askWhichStay(info);
                    return;
                }
                if (stayId == 0L && info.stays != null && info.stays.size() == 1) stayId = info.stays.get(0).stay_id;
                readRooms(info);
                bindStaySummary();
                loadCatalog();
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                showState(message, true);
            }
        });
    }

    /** "Which stay is this for?": each stay in house, with its dates; the guest picks one and the form continues for it. */
    private void askWhichStay(AdditionalAmenityInfoDto info) {
        String[] lines = new String[info.stays.size()];
        for (int i = 0; i < lines.length; i++) {
            AdditionalAmenityInfoDto.Stay s = info.stays.get(i);
            lines[i] = getString(R.string.amenity_stay_pick_line_format, s.check_in, s.check_out);
        }
        showState(getString(R.string.group_pick_stay_title), false);
        new SafeDialogs.Material(this)
                .setTitle(R.string.group_pick_stay_title)
                .setItems(lines, (d, which) -> {
                    stayId = info.stays.get(which).stay_id;
                    readRooms(info);
                    bindStaySummary();
                    loadCatalog();
                })
                .setNegativeButton(R.string.cancel_label, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    /** The rooms of the stay, from the server. An older server without the list gets one generic "Your room" entry. */
    private void readRooms(AdditionalAmenityInfoDto info) {
        rooms.clear();
        if (info.rooms != null) {
            for (AdditionalAmenityInfoDto.Room r : info.rooms) {
                if (stayId != 0L && r.stay_id != null && r.stay_id != stayId) continue; // a room of another stay
                String title = r.room_number != null && !r.room_number.isEmpty()
                        ? getString(R.string.additional_amenities_room_title_format, r.room_number)
                        : (r.room_name != null ? r.room_name : getString(R.string.additional_amenities_your_room));
                rooms.add(new RoomEntry(r.room_id, title, r.room_type != null ? r.room_type : "", r.room_number));
            }
        }
        if (rooms.isEmpty()) rooms.add(new RoomEntry(0, getString(R.string.additional_amenities_your_room), ""));
    }

    /** "Booking #567 · Room 303 · Oct 20 → Oct 21, 2026": which stay this request is for (from the cached transaction). */
    private Booking currentStay() {
        for (Booking b : repository.getAllBookings()) {
            if (b != null && id.equals(b.getId()) && b.isDirectBooking() == hasBooking) return b;
        }
        return null;
    }

    private void bindStaySummary() {
        Booking stay = currentStay();
        View card = findViewById(R.id.additionalAmenitiesStay);
        if (stay == null) {
            card.setVisibility(View.GONE);
            return;
        }
        List<String> titles = new ArrayList<>();
        for (RoomEntry room : rooms) if (room.id != 0) titles.add(room.title);
        String where = titles.isEmpty() ? TransactionRow.roomTextOf(stay) : android.text.TextUtils.join(", ", titles);
        List<String> parts = new ArrayList<>();
        parts.add(TransactionText.reference(this, stay.isHasBooking(), stay.getDisplayId()));
        if (!where.isEmpty()) parts.add(where);
        String dates = StayCardText.dates(stay);
        if (!dates.isEmpty()) parts.add(dates);
        ((TextView) findViewById(R.id.tvAmenitiesStaySummary)).setText(android.text.TextUtils.join(" · ", parts));
        card.setVisibility(View.VISIBLE);
    }

    private void loadCatalog() {
        repository.refreshAmenities("paid", new RoomRepository.RepositoryCallback<List<AddOnAmenity>>() {
            @Override
            public void onSuccess(List<AddOnAmenity> rawItems) {
                if (isFinishing()) return;
                // Active amenities only, each once (by id); same name under two ids is reported, not hidden.
                List<AddOnAmenity> items = AmenityCatalog.cleaned(rawItems);
                List<String> sameName = AmenityCatalog.sameNameDifferentId(items);
                if (!sameName.isEmpty()) {
                    com.example.velocitysuites.network.DiagnosticLog.w("AdditionalAmenities",
                            "same name under different ids (possible duplicate data): " + sameName);
                }
                if (items.isEmpty()) {
                    showState(getString(R.string.additional_amenities_catalog_empty), false);
                    return;
                }
                renderForm(items);
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                showState(message, true);
            }
        });
    }

    private void renderForm(List<AddOnAmenity> items) {
        catalog.clear();
        catalog.addAll(items);
        selections.clear();
        roomSummaryLabels.clear();
        roomPickedLabels.clear();
        roomsList.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (RoomEntry room : rooms) {
            selections.put(room.id, new int[catalog.size()]);
            View card = inflater.inflate(R.layout.item_additional_amenity_room, roomsList, false);
            ((TextView) card.findViewById(R.id.tvAmenityRoomTitle)).setText(room.title);
            TextView sub = card.findViewById(R.id.tvAmenityRoomType);
            sub.setText(room.subtitle);
            sub.setVisibility(room.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            roomSummaryLabels.put(room.id, card.findViewById(R.id.tvAmenityRoomSelection));
            roomPickedLabels.put(room.id, card.findViewById(R.id.tvAmenityRoomPicked));
            card.setOnClickListener(v -> openRoomPicker(room));
            roomsList.addView(card);
        }

        stateBlock.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        refreshAll();
    }

    /** The picker for one room: every requestable amenity once, a compact stepper each, several can be chosen. Changes apply immediately. */
    @SuppressLint("InflateParams") // a dialog's content view has no parent to inflate into
    private void openRoomPicker(RoomEntry room) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_additional_amenity_room_picker, null, false);
        ((TextView) content.findViewById(R.id.tvPickerTitle)).setText(getString(R.string.additional_amenities_picker_title_format, room.title));
        LinearLayout list = content.findViewById(R.id.pickerList);
        TextView pickerTotal = content.findViewById(R.id.tvPickerTotal);
        content.findViewById(R.id.tvPickerEmpty).setVisibility(catalog.isEmpty() ? View.VISIBLE : View.GONE);
        // Tall catalogs scroll inside the sheet so the footer (total, Done) always stays on screen.
        ((MaxHeightNestedScrollView) content.findViewById(R.id.pickerScroll))
                .setMaxHeightPx((int) (getResources().getDisplayMetrics().heightPixels * 0.5f));
        int[] quantities = selections.get(room.id);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < catalog.size(); i++) {
            final int index = i;
            AddOnAmenity amenity = catalog.get(i);
            View row = inflater.inflate(R.layout.item_additional_amenity_option, list, false);
            ((TextView) row.findViewById(R.id.tvAmenityOptionName)).setText(amenity.getName());
            ((TextView) row.findViewById(R.id.tvAmenityOptionPrice)).setText(getString(R.string.additional_amenities_each_format, MoneyFormat.format(amenity.getPrice())));
            bindDescription(row, amenity);
            int cap = Math.min(MAX_QTY, amenity.getMaxQuantity());
            bindRow(row, quantities[index], amenity, cap);

            row.findViewById(R.id.btnAmenityOptionMinus).setOnClickListener(v -> {
                if (quantities[index] > 0) {
                    quantities[index]--;
                    bindRow(row, quantities[index], amenity, cap);
                    pickerTotal.setText(pickerTotalText(quantities));
                    refreshAll();
                }
            });
            row.findViewById(R.id.btnAmenityOptionPlus).setOnClickListener(v -> {
                if (quantities[index] < cap) {
                    quantities[index]++;
                    bindRow(row, quantities[index], amenity, cap);
                    pickerTotal.setText(pickerTotalText(quantities));
                    refreshAll();
                }
            });
            list.addView(row);
        }
        pickerTotal.setText(pickerTotalText(quantities));
        content.findViewById(R.id.btnPickerDone).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(content);
        SafeDialogs.show(dialog);
    }

    /** "3 items · ₱200.00" for what is chosen in one room. */
    private String pickerTotalText(int[] quantities) {
        int items = 0;
        double total = 0;
        for (int i = 0; i < catalog.size(); i++) {
            items += quantities[i];
            total += catalog.get(i).getPrice() * quantities[i];
        }
        return getResources().getQuantityString(R.plurals.additional_amenities_items_total, items, items, MoneyFormat.format(total));
    }

    /** The description under the name (2 lines, "More" to read the rest); no description at all means no line - never a placeholder. */
    private void bindDescription(View row, AddOnAmenity amenity) {
        TextView description = row.findViewById(R.id.tvAmenityOptionDescription);
        TextView more = row.findViewById(R.id.tvAmenityOptionMore);
        String text = AmenityCatalog.descriptionOf(amenity);
        if (text == null) {
            description.setVisibility(View.GONE);
            more.setVisibility(View.GONE);
            return;
        }
        description.setText(text);
        description.setVisibility(View.VISIBLE);
        boolean long_ = text.length() > DESCRIPTION_MORE_CHARS;
        more.setVisibility(long_ ? View.VISIBLE : View.GONE);
        more.setOnClickListener(v -> {
            boolean expanding = description.getMaxLines() <= 2;
            description.setMaxLines(expanding ? Integer.MAX_VALUE : 2);
            more.setText(expanding ? R.string.additional_amenities_description_less : R.string.additional_amenities_description_more);
        });
    }

    /** More than this many characters will not fit two lines on a phone: offer "More". */
    private static final int DESCRIPTION_MORE_CHARS = 70;

    /** A row for its quantity: the count, the line subtotal and accent border when chosen, "-" off at 0 and "+" off at the stock limit. */
    private void bindRow(View row, int qty, AddOnAmenity amenity, int cap) {
        ((TextView) row.findViewById(R.id.tvAmenityOptionQty)).setText(String.valueOf(qty));
        TextView subtotal = row.findViewById(R.id.tvAmenityOptionSubtotal);
        subtotal.setVisibility(qty > 0 ? View.VISIBLE : View.GONE);
        if (qty > 0) {
            subtotal.setText(getString(R.string.additional_amenities_line_subtotal_format, qty,
                    MoneyFormat.format(amenity.getPrice()), MoneyFormat.format(amenity.getPrice() * qty)));
        }
        row.findViewById(R.id.btnAmenityOptionMinus).setEnabled(qty > 0);
        row.findViewById(R.id.btnAmenityOptionPlus).setEnabled(qty < cap);
        com.google.android.material.card.MaterialCardView card = row.findViewById(R.id.cardAmenityOption);
        card.setStrokeColor(getColor(qty > 0 ? R.color.velocity_red_primary : R.color.velocity_divider_hairline));
        card.setStrokeWidth(Math.round((qty > 0 ? 2 : 1) * getResources().getDisplayMetrics().density));
    }

    /** Redraws each room card's "what you picked" line, the per-room summary and the total. */
    private void refreshAll() {
        summaryList.removeAllViews();
        double total = 0;
        for (RoomEntry room : rooms) {
            int[] quantities = selections.get(room.id);
            List<String> picked = new ArrayList<>();
            List<String> short_ = new ArrayList<>();
            double roomTotal = 0;
            int roomItems = 0;
            for (int i = 0; i < catalog.size(); i++) {
                if (quantities[i] > 0) {
                    picked.add(getString(R.string.additional_amenities_line_format, catalog.get(i).getName(), quantities[i]));
                    short_.add(getString(R.string.additional_amenities_picked_format, quantities[i], catalog.get(i).getName()));
                    roomTotal += catalog.get(i).getPrice() * quantities[i];
                    roomItems += quantities[i];
                }
            }
            total += roomTotal;

            TextView label = roomSummaryLabels.get(room.id);
            if (label != null) {
                label.setText(picked.isEmpty() ? getString(R.string.additional_amenities_none_selected)
                        : getResources().getQuantityString(R.plurals.additional_amenities_items_total, roomItems, roomItems, MoneyFormat.format(roomTotal)));
            }
            TextView pickedLine = roomPickedLabels.get(room.id);
            if (pickedLine != null) {
                pickedLine.setText(android.text.TextUtils.join(", ", short_));
                pickedLine.setVisibility(short_.isEmpty() ? View.GONE : View.VISIBLE);
            }
            if (!picked.isEmpty()) addSummaryBlock(room, picked, roomTotal);
        }
        findViewById(R.id.additionalAmenitiesSummaryHeader).setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        totalLabel.setText(MoneyFormat.format(total));
        hintAddOne.setVisibility(total > 0 ? View.GONE : View.VISIBLE);
        submitButton.setEnabled(total > 0 && !submitting);
    }

    private void addSummaryBlock(RoomEntry room, List<String> lines, double roomTotal) {
        TextView head = new TextView(this);
        head.setText(getString(R.string.additional_amenities_summary_room_format, room.title, MoneyFormat.format(roomTotal)));
        head.setTextColor(getColor(R.color.velocity_text_primary));
        head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.setTextSize(13);
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        summaryList.addView(head, headParams);
        for (String line : lines) {
            TextView row = new TextView(this);
            row.setText(getString(R.string.additional_amenities_bullet_format, line));
            row.setTextColor(getColor(R.color.velocity_text_secondary));
            row.setTextSize(12);
            summaryList.addView(row);
        }
    }

    /** Asks before sending: a request goes to the front desk and cannot be taken back from here. */
    private void confirmAndSubmit() {
        if (submitting) return;
        new SafeDialogs.Material(this)
                .setTitle(R.string.amenity_send_confirm_title)
                .setMessage(R.string.amenity_send_confirm_message)
                .setNegativeButton(R.string.keep_editing, null)
                .setPositiveButton(R.string.amenity_send_confirm_yes, (d, w) -> submit())
                .show();
    }

    /** True once the guest has picked at least one item. */
    private boolean hasSelection() {
        for (int[] quantities : selections.values()) {
            for (int q : quantities) if (q > 0) return true;
        }
        return false;
    }

    private void submit() {
        if (submitting) return;
        AdditionalAmenitySubmitRequest request = new AdditionalAmenitySubmitRequest();
        if (stayId != 0L) request.stay_id = stayId;
        List<AmenityRecord> submitted = new ArrayList<>();
        for (RoomEntry room : rooms) {
            int[] quantities = selections.get(room.id);
            List<AdditionalAmenitySubmitRequest.Item> items = new ArrayList<>();
            for (int i = 0; i < catalog.size(); i++) {
                if (quantities[i] <= 0) continue;
                try {
                    items.add(new AdditionalAmenitySubmitRequest.Item(Long.parseLong(catalog.get(i).getId()), quantities[i]));
                    if (room.number != null) {
                        submitted.add(new AmenityRecord(0, catalog.get(i).getName(), quantities[i], catalog.get(i).getPrice() * quantities[i],
                                room.number, room.subtitle, AmenityRecord.Status.PENDING));
                    }
                } catch (NumberFormatException e) {
                    // an amenity without a numeric id can't be requested; skip it rather than crash
                }
            }
            if (!items.isEmpty()) request.addRoom(room.id, items);
        }
        if (request.isEmpty()) return;

        submitting = true;
        submitButton.setEnabled(false);
        repository.submitAdditionalAmenities(hasBooking, id, request, new RoomRepository.RepositoryCallback<AdditionalAmenityInfoDto>() {
            @Override
            public void onSuccess(AdditionalAmenityInfoDto info) {
                if (isFinishing() || isDestroyed()) return;
                submitting = false;
                Booking known = currentStay();
                if (known != null) {
                    AmenityRoomSources.remember(AdditionalAmenitiesActivity.this, known, submitted); // which room each item was for
                    AmenityRoomSources.putApi(known, info);
                }
                Feedback.later(getString(R.string.feedback_request_sent));
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                submitting = false;
                refreshAll();
                Toast.makeText(AdditionalAmenitiesActivity.this, getString(R.string.additional_amenities_submit_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }
}
