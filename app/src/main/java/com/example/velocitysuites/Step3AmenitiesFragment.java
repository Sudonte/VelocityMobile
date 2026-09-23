package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 3: optional Additional Amenities, with an explicit Skip/Continue
 * Without Amenities affordance (new - the underlying selection was already
 * optional in BookingAndReservationActivity, just without a dedicated skip
 * button). Ported from #loadAmenityCatalog()/#renderAmenityCatalog().
 */
public class Step3AmenitiesFragment extends WizardStepFragment {

    private RoomRepository repository;
    private LinearLayout layoutAmenitiesItemsContainer;
    private View layoutAmenitiesEmptyState;
    private TextView tvAmenitiesEmptyState;
    private MaterialButton btnRetryAmenities;
    private View layoutAmenitiesSummary;
    private TextView tvSelectedAmenitiesCount;
    private TextView tvSelectedAmenitiesSubtotal;
    private List<AddOnAmenity> addOnCatalog = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step3_amenities, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = RoomRepository.getInstance(requireContext());

        layoutAmenitiesItemsContainer = view.findViewById(R.id.layoutAmenitiesItemsContainer);
        layoutAmenitiesEmptyState = view.findViewById(R.id.layoutAmenitiesEmptyState);
        tvAmenitiesEmptyState = view.findViewById(R.id.tvAmenitiesEmptyState);
        btnRetryAmenities = view.findViewById(R.id.btnRetryAmenities);
        if (btnRetryAmenities != null) {
            btnRetryAmenities.setOnClickListener(v -> loadAmenityCatalog());
        }
        layoutAmenitiesSummary = view.findViewById(R.id.layoutAmenitiesSummary);
        tvSelectedAmenitiesCount = view.findViewById(R.id.tvSelectedAmenitiesCount);
        tvSelectedAmenitiesSubtotal = view.findViewById(R.id.tvSelectedAmenitiesSubtotal);

        view.findViewById(R.id.btnSkipAmenities).setOnClickListener(v -> {
            getState().selectedAmenities.clear();
            getWizardActivity().goToNextStepSkippingValidation();
            updateSelectedSummary();
        });

        loadAmenityCatalog();
    }

    @Override
    public String stepTitle() {
        return "Amenities";
    }

    // Always optional - Next never blocks here.

    private void loadAmenityCatalog() {
        // "Additional Amenities" is deliberately Paid-only - free/complimentary
        // amenities are the room's already-included features, not something
        // the guest opts into paying extra for here. pricing_type=paid maps
        // exactly to this (see ApiService#getAmenities's own doc comment:
        // "pricing_type=paid|free filters to Additional/Paid or Free/Included
        // amenities"). A prior pass here briefly changed this to fetch both,
        // on the mistaken assumption that excluding free amenities was a bug
        // rather than the intended business rule - reverted per explicit
        // confirmation that free amenities must not appear in this step.
        repository.refreshAmenities("paid", new RoomRepository.RepositoryCallback<List<AddOnAmenity>>() {
            @Override
            public void onSuccess(List<AddOnAmenity> amenities) {
                if (!isAdded()) return;
                addOnCatalog = amenities;
                renderAmenityCatalog(false);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                addOnCatalog = new ArrayList<>();
                renderAmenityCatalog(true);
            }
        });
    }

    /**
     * @param loadFailed distinguishes a genuinely empty catalog (no retry
     *                   offered - there's nothing to retry) from a failed
     *                   network/server call (shows a retry button), per spec.
     */
    private void renderAmenityCatalog(boolean loadFailed) {
        layoutAmenitiesItemsContainer.removeAllViews();

        if (addOnCatalog.isEmpty()) {
            layoutAmenitiesEmptyState.setVisibility(View.VISIBLE);
            layoutAmenitiesItemsContainer.setVisibility(View.GONE);
            tvAmenitiesEmptyState.setText(loadFailed ? R.string.addon_amenities_load_error : R.string.addon_amenities_empty);
            if (btnRetryAmenities != null) {
                btnRetryAmenities.setVisibility(loadFailed ? View.VISIBLE : View.GONE);
            }
            return;
        }

        layoutAmenitiesEmptyState.setVisibility(View.GONE);
        layoutAmenitiesItemsContainer.setVisibility(View.VISIBLE);
        if (layoutAmenitiesSummary != null) layoutAmenitiesSummary.setVisibility(View.GONE);

        // Preserve a selection made before this fragment instance was
        // recreated (e.g. returning from Step 4/5 - BookingWizardActivity
        // rebuilds a fresh fragment per step, and this catalog is refetched
        // fresh each time too) - AddOnAmenity has no equals() override, and
        // a fresh fetch never returns the same object instances, so match by
        // id rather than by list membership/reference.
        Map<String, AddOnAmenity> previouslySelectedById = new HashMap<>();
        for (AddOnAmenity a : getState().selectedAmenities) {
            previouslySelectedById.put(a.getId(), a);
        }
        getState().selectedAmenities.clear();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AddOnAmenity amenity : addOnCatalog) {
            AddOnAmenity previouslySelected = previouslySelectedById.get(amenity.getId());
            boolean startsSelected = previouslySelected != null;
            if (startsSelected) {
                amenity.setQuantity(previouslySelected.getQuantity());
                getState().selectedAmenities.add(amenity);
            }

            View row = inflater.inflate(R.layout.item_amenity_addon, layoutAmenitiesItemsContainer, false);

            MaterialCheckBox checkBox = row.findViewById(R.id.cbAmenityItem);
            TextView priceLabel = row.findViewById(R.id.tvAmenityItemPrice);
            TextView descLabel = row.findViewById(R.id.tvAmenityItemDesc);
            ImageView iconView = row.findViewById(R.id.ivAmenityItemIcon);
            View quantityRow = row.findViewById(R.id.layoutAmenityItemQuantity);
            MaterialButton btnMinus = row.findViewById(R.id.btnAmenityQtyMinus);
            MaterialButton btnPlus = row.findViewById(R.id.btnAmenityQtyPlus);
            TextView qtyLabel = row.findViewById(R.id.tvAmenityItemQty);
            TextView subtotalLabel = row.findViewById(R.id.tvAmenityItemSubtotal);

            boolean isFree = amenity.getPrice() <= 0.009;
            checkBox.setText(amenity.getCategory() != null && !amenity.getCategory().isEmpty()
                    ? amenity.getName() + " — " + amenity.getCategory()
                    : amenity.getName());
            priceLabel.setText(isFree ? getString(R.string.addon_amenity_free_label)
                    : getString(R.string.addon_amenity_price_format, amenity.getPrice()));
            descLabel.setText(amenity.getDescription());
            if (iconView != null) {
                iconView.setImageResource(RoomVisuals.getAmenityIcon(amenity.getName()));
            }

            Runnable refreshQtyUi = () -> {
                qtyLabel.setText(String.valueOf(amenity.getQuantity()));
                subtotalLabel.setText(isFree ? getString(R.string.addon_amenity_free_label)
                        : getString(R.string.addon_amenity_subtotal_format, amenity.getSubtotal()));
            };
            refreshQtyUi.run();
            quantityRow.setVisibility(startsSelected ? View.VISIBLE : View.GONE);

            btnMinus.setOnClickListener(v -> {
                if (amenity.getQuantity() > 1) {
                    amenity.setQuantity(amenity.getQuantity() - 1);
                    refreshQtyUi.run();
                    updateSelectedSummary();
                }
            });
            btnPlus.setOnClickListener(v -> {
                // Adults/children aren't chosen until Step 5, so the best
                // upper bound available here is the total room capacity
                // selected in Step 2 (Room Selection) - re-clamped to the
                // real guest count once it's known (see
                // Step5AdditionalGuestsFragment#clampAmenityQuantitiesToGuestCount()).
                int cap = Math.min(amenity.getMaxQuantity(), Math.max(1, getState().totalSelectedCapacity()));
                if (amenity.getQuantity() < cap) {
                    amenity.setQuantity(amenity.getQuantity() + 1);
                    refreshQtyUi.run();
                    updateSelectedSummary();
                } else if (cap == amenity.getMaxQuantity()) {
                    Toast.makeText(requireContext(), getString(R.string.no_more_amenity_stock_toast, amenity.getName()), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.error_amenity_qty_exceeds_capacity, Toast.LENGTH_SHORT).show();
                }
            });

            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) row;
            int strokeSubtle = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.velocity_red_soft);
            int strokeSelected = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.velocity_red_primary);
            int bgSubtle = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.velocity_red_subtle);
            int bgSelected = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.velocity_red_soft);
            int strokeWidthPx = (int) (getResources().getDisplayMetrics().density);

            Runnable applySelectedStyle = () -> {
                boolean selected = getState().selectedAmenities.contains(amenity);
                card.setStrokeColor(selected ? strokeSelected : strokeSubtle);
                card.setStrokeWidth(selected ? strokeWidthPx * 2 : strokeWidthPx);
                card.setCardBackgroundColor(selected ? bgSelected : bgSubtle);
            };
            applySelectedStyle.run();

            // Must be set before attaching the listener below, so restoring
            // a prior selection doesn't itself trigger the listener (which
            // would otherwise just redundantly re-add the same amenity -
            // harmless, but cleaner to avoid).
            checkBox.setChecked(startsSelected);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!getState().selectedAmenities.contains(amenity)) getState().selectedAmenities.add(amenity);
                    quantityRow.setVisibility(View.VISIBLE);
                } else {
                    getState().selectedAmenities.remove(amenity);
                    amenity.setQuantity(1);
                    refreshQtyUi.run();
                    quantityRow.setVisibility(View.GONE);
                }
                applySelectedStyle.run();
                updateSelectedSummary();
            });

            layoutAmenitiesItemsContainer.addView(row);
        }

        updateSelectedSummary();
    }

    /** Running "Selected Amenities: N" / subtotal recap - shown only once at least one amenity is selected. */
    private void updateSelectedSummary() {
        if (layoutAmenitiesSummary == null) return;
        List<AddOnAmenity> selected = getState().selectedAmenities;
        if (selected.isEmpty()) {
            layoutAmenitiesSummary.setVisibility(View.GONE);
            return;
        }
        double subtotal = 0;
        for (AddOnAmenity a : selected) subtotal += a.getSubtotal();

        layoutAmenitiesSummary.setVisibility(View.VISIBLE);
        tvSelectedAmenitiesCount.setText(getString(R.string.selected_amenities_count_format, selected.size()));
        tvSelectedAmenitiesSubtotal.setText(String.format(java.util.Locale.US, getString(R.string.price_format), subtotal));
    }
}
