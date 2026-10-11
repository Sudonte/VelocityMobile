package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AdditionalAmenitySubmitRequest;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * "Request Additional Amenities": the guest picks paid amenities from the live catalog, sets quantities, adds an optional note,
 * sees each item's price and the total, and submits. The requests start PENDING; the front desk approves or rejects them and
 * only approved ones reach the bill. Whether the stay may still request (own, active booking) is decided by the server -
 * this screen just shows its answer, and the reason when the answer is no.
 */
public class AdditionalAmenitiesActivity extends AppCompatActivity {

    public static final String EXTRA_DIRECT_BOOKING = "EXTRA_DIRECT_BOOKING";
    public static final String EXTRA_ID = "EXTRA_ID";

    /** Sane stepper bound so a stray tap-and-hold can't ask for hundreds; the server enforces real stock. */
    private static final int MAX_QTY = 20;

    private RoomRepository repository;
    /** true: the id is a direct booking's; false: it is the reservation (a converted stay keeps the reservation id in the app). */
    private boolean hasBooking;
    private String id;

    private View stateBlock;
    private TextView stateText;
    private MaterialButton retryButton;
    private View form;
    private android.widget.LinearLayout list;
    private EditText noteField;
    private TextView totalLabel;
    private MaterialButton submitButton;

    private final List<AddOnAmenity> catalog = new ArrayList<>();
    private final List<Integer> quantities = new ArrayList<>();
    private final List<TextView> subtotalLabels = new ArrayList<>();
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
        list = findViewById(R.id.additionalAmenitiesList);
        noteField = findViewById(R.id.additionalAmenitiesNote);
        // Long notes scroll inside the field instead of dragging the whole screen.
        noteField.setOnTouchListener((v, event) -> {
            if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) v.performClick();
            }
            return false;
        });
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
                loadCatalog();
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                showState(message, true);
            }
        });
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
                renderCatalog(items);
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                showState(message, true);
            }
        });
    }

    private void renderCatalog(List<AddOnAmenity> items) {
        catalog.clear();
        catalog.addAll(items);
        quantities.clear();
        subtotalLabels.clear();
        list.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < catalog.size(); i++) {
            final int index = i;
            AddOnAmenity amenity = catalog.get(i);
            quantities.add(0);
            View row = inflater.inflate(R.layout.item_additional_amenity_option, list, false);
            ((TextView) row.findViewById(R.id.tvAmenityOptionName)).setText(amenity.getName());
            ((TextView) row.findViewById(R.id.tvAmenityOptionPrice)).setText(getString(R.string.additional_amenities_each_format, MoneyFormat.format(amenity.getPrice())));
            TextView qtyLabel = row.findViewById(R.id.tvAmenityOptionQty);
            TextView subtotalLabel = row.findViewById(R.id.tvAmenityOptionSubtotal);
            subtotalLabels.add(subtotalLabel);
            int cap = Math.min(MAX_QTY, amenity.getMaxQuantity());

            row.findViewById(R.id.btnAmenityOptionMinus).setOnClickListener(v -> {
                if (quantities.get(index) > 0) {
                    quantities.set(index, quantities.get(index) - 1);
                    refreshRow(index, qtyLabel);
                }
            });
            row.findViewById(R.id.btnAmenityOptionPlus).setOnClickListener(v -> {
                if (quantities.get(index) < cap) {
                    quantities.set(index, quantities.get(index) + 1);
                    refreshRow(index, qtyLabel);
                }
            });
            list.addView(row);
        }

        stateBlock.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);
        refreshTotal();
    }

    private void refreshRow(int index, TextView qtyLabel) {
        int qty = quantities.get(index);
        qtyLabel.setText(String.valueOf(qty));
        subtotalLabels.get(index).setText(qty > 0 ? MoneyFormat.format(catalog.get(index).getPrice() * qty) : "");
        refreshTotal();
    }

    private double total() {
        double sum = 0;
        for (int i = 0; i < catalog.size(); i++) sum += catalog.get(i).getPrice() * quantities.get(i);
        return sum;
    }

    private void refreshTotal() {
        double total = total();
        totalLabel.setText(getString(R.string.additional_amenities_total_format, MoneyFormat.format(total)));
        submitButton.setEnabled(total > 0 && !submitting);
    }

    private void submit() {
        if (submitting) return;
        String note = noteField.getText() != null ? noteField.getText().toString().trim() : "";
        AdditionalAmenitySubmitRequest request = new AdditionalAmenitySubmitRequest(note.isEmpty() ? null : note);
        for (int i = 0; i < catalog.size(); i++) {
            if (quantities.get(i) > 0) {
                request.items.add(new AdditionalAmenitySubmitRequest.Item(Long.parseLong(catalog.get(i).getId()), quantities.get(i)));
            }
        }
        if (request.items.isEmpty()) return;

        submitting = true;
        submitButton.setEnabled(false);
        repository.submitAdditionalAmenities(hasBooking, id, request, new RoomRepository.RepositoryCallback<AdditionalAmenityInfoDto>() {
            @Override
            public void onSuccess(AdditionalAmenityInfoDto info) {
                submitting = false;
                Toast.makeText(AdditionalAmenitiesActivity.this, R.string.additional_amenities_submitted, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                submitting = false;
                refreshTotal();
                Toast.makeText(AdditionalAmenitiesActivity.this, getString(R.string.additional_amenities_submit_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }
}
