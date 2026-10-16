package com.example.velocitysuites;

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
public class AdditionalAmenitiesActivity extends AppCompatActivity {

    public static final String EXTRA_DIRECT_BOOKING = "EXTRA_DIRECT_BOOKING";
    public static final String EXTRA_ID = "EXTRA_ID";

    /** Sane stepper bound so a stray tap-and-hold can't ask for hundreds; the server enforces real stock. */
    private static final int MAX_QTY = 20;

    /** One room of the stay as the form shows it. {@code id == 0} stands for "your room" when an older server sent no room list. */
    private static final class RoomEntry {
        final long id;
        final String title;
        final String subtitle;

        RoomEntry(long id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
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

    private final List<AddOnAmenity> catalog = new ArrayList<>();
    private final List<RoomEntry> rooms = new ArrayList<>();
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
        repository = RoomRepository.getInstance(this);

        findViewById(R.id.btnAdditionalAmenitiesBack).setOnClickListener(v -> finish());
        stateBlock = findViewById(R.id.additionalAmenitiesState);
        stateText = findViewById(R.id.additionalAmenitiesStateText);
        retryButton = findViewById(R.id.additionalAmenitiesRetry);
        form = findViewById(R.id.additionalAmenitiesForm);
        roomsList = findViewById(R.id.additionalAmenitiesRooms);
        summaryList = findViewById(R.id.additionalAmenitiesSummary);
        totalLabel = findViewById(R.id.additionalAmenitiesTotal);
        submitButton = findViewById(R.id.additionalAmenitiesSubmit);

        retryButton.setOnClickListener(v -> load());
        submitButton.setOnClickListener(v -> submit());

        if (id == null || id.isEmpty()) {
            showState(getString(R.string.additional_amenities_load_error), true);
            retryButton.setVisibility(View.GONE);
            return;
        }
        load();
    }

    private void showState(String message, boolean error) {
        form.setVisibility(View.GONE);
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
                if (!info.can_request) {
                    showState(info.reason != null ? info.reason : getString(R.string.additional_amenities_not_available), false);
                    return;
                }
                readRooms(info);
                loadCatalog();
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                showState(message, true);
            }
        });
    }

    /** The rooms of the stay, from the server. An older server without the list gets one generic "Your room" entry. */
    private void readRooms(AdditionalAmenityInfoDto info) {
        rooms.clear();
        if (info.rooms != null) {
            for (AdditionalAmenityInfoDto.Room r : info.rooms) {
                String title = r.room_number != null && !r.room_number.isEmpty()
                        ? getString(R.string.additional_amenities_room_title_format, r.room_number)
                        : (r.room_name != null ? r.room_name : getString(R.string.additional_amenities_your_room));
                rooms.add(new RoomEntry(r.room_id, title, r.room_type != null ? r.room_type : ""));
            }
        }
        if (rooms.isEmpty()) rooms.add(new RoomEntry(0, getString(R.string.additional_amenities_your_room), ""));
    }

    private void loadCatalog() {
        repository.refreshAmenities("paid", new RoomRepository.RepositoryCallback<List<AddOnAmenity>>() {
            @Override
            public void onSuccess(List<AddOnAmenity> items) {
                if (isFinishing()) return;
                if (items == null || items.isEmpty()) {
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
            card.setOnClickListener(v -> openRoomPicker(room));
            roomsList.addView(card);
        }

        stateBlock.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);
        refreshAll();
    }

    /** The picker for one room: every requestable amenity with a stepper; several can be chosen. Changes apply immediately. */
    private void openRoomPicker(RoomEntry room) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_additional_amenity_room_picker, null, false);
        ((TextView) content.findViewById(R.id.tvPickerTitle)).setText(getString(R.string.additional_amenities_picker_title_format, room.title));
        LinearLayout list = content.findViewById(R.id.pickerList);
        // Tall catalogs scroll inside the sheet so the Done button always stays on screen.
        ((MaxHeightNestedScrollView) content.findViewById(R.id.pickerScroll))
                .setMaxHeightPx((int) (getResources().getDisplayMetrics().heightPixels * 0.55f));
        int[] quantities = selections.get(room.id);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < catalog.size(); i++) {
            final int index = i;
            AddOnAmenity amenity = catalog.get(i);
            View row = inflater.inflate(R.layout.item_additional_amenity_option, list, false);
            ((TextView) row.findViewById(R.id.tvAmenityOptionName)).setText(amenity.getName());
            ((TextView) row.findViewById(R.id.tvAmenityOptionPrice)).setText(getString(R.string.additional_amenities_each_format, MoneyFormat.format(amenity.getPrice())));
            TextView qtyLabel = row.findViewById(R.id.tvAmenityOptionQty);
            TextView subtotalLabel = row.findViewById(R.id.tvAmenityOptionSubtotal);
            int cap = Math.min(MAX_QTY, amenity.getMaxQuantity());
            bindRow(qtyLabel, subtotalLabel, quantities[index], amenity);

            row.findViewById(R.id.btnAmenityOptionMinus).setOnClickListener(v -> {
                if (quantities[index] > 0) {
                    quantities[index]--;
                    bindRow(qtyLabel, subtotalLabel, quantities[index], amenity);
                    refreshAll();
                }
            });
            row.findViewById(R.id.btnAmenityOptionPlus).setOnClickListener(v -> {
                if (quantities[index] < cap) {
                    quantities[index]++;
                    bindRow(qtyLabel, subtotalLabel, quantities[index], amenity);
                    refreshAll();
                }
            });
            list.addView(row);
        }
        content.findViewById(R.id.btnPickerDone).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(content);
        SafeDialogs.show(dialog);
    }

    private static void bindRow(TextView qtyLabel, TextView subtotalLabel, int qty, AddOnAmenity amenity) {
        qtyLabel.setText(String.valueOf(qty));
        subtotalLabel.setText(qty > 0 ? MoneyFormat.format(amenity.getPrice() * qty) : "");
    }

    /** Redraws each room card's "what you picked" line, the per-room summary and the total. */
    private void refreshAll() {
        summaryList.removeAllViews();
        double total = 0;
        for (RoomEntry room : rooms) {
            int[] quantities = selections.get(room.id);
            List<String> picked = new ArrayList<>();
            double roomTotal = 0;
            for (int i = 0; i < catalog.size(); i++) {
                if (quantities[i] > 0) {
                    picked.add(getString(R.string.additional_amenities_line_format, catalog.get(i).getName(), quantities[i]));
                    roomTotal += catalog.get(i).getPrice() * quantities[i];
                }
            }
            total += roomTotal;

            TextView label = roomSummaryLabels.get(room.id);
            if (label != null) {
                label.setText(picked.isEmpty() ? getString(R.string.additional_amenities_none_selected)
                        : getString(R.string.additional_amenities_selected_count_format, picked.size()));
            }
            if (!picked.isEmpty()) addSummaryBlock(room, picked, roomTotal);
        }
        findViewById(R.id.additionalAmenitiesSummaryHeader).setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        totalLabel.setText(getString(R.string.additional_amenities_total_format, MoneyFormat.format(total)));
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

    private void submit() {
        if (submitting) return;
        AdditionalAmenitySubmitRequest request = new AdditionalAmenitySubmitRequest();
        for (RoomEntry room : rooms) {
            int[] quantities = selections.get(room.id);
            List<AdditionalAmenitySubmitRequest.Item> items = new ArrayList<>();
            for (int i = 0; i < catalog.size(); i++) {
                if (quantities[i] <= 0) continue;
                try {
                    items.add(new AdditionalAmenitySubmitRequest.Item(Long.parseLong(catalog.get(i).getId()), quantities[i]));
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
                Toast.makeText(AdditionalAmenitiesActivity.this, R.string.additional_amenities_submitted, Toast.LENGTH_LONG).show();
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
