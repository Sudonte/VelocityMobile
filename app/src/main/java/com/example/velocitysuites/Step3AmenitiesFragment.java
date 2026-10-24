package com.example.velocitysuites;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Amenities (optional), organised the way the guest thinks about them: by STAY, then by ROOM ("Deluxe (room 1)"), then one row per amenity
 * with its bold name, description, price and a quantity stepper. A sticky bar at the bottom keeps "Amenities ₱300.00 · Grand total
 * ₱18,900.00" live on every change. Next shows a review of what was chosen (per stay and room, with the totals) and asks before moving on;
 * with nothing chosen it asks "Continue without additional amenities?" instead, and so does Skip.
 *
 * The choice is kept per room of each stay (see {@link BookingWizardState.Stay#roomAmenityQty}); the server prices and stores amenities per
 * stay, so the quantities of a stay's rooms are added up when the booking is sent.
 */
public class Step3AmenitiesFragment extends WizardStepFragment {

    private RoomRepository repository;
    private LinearLayout layoutAmenitiesItemsContainer;
    private View layoutAmenitiesEmptyState;
    private TextView tvAmenitiesEmptyState;
    private MaterialButton btnRetryAmenities;
    private TextView tvLiveTotal;
    private List<AddOnAmenity> addOnCatalog = new ArrayList<>();
    private final ClickGuard guard = new ClickGuard();

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
        tvLiveTotal = view.findViewById(R.id.tvAmenitiesLiveTotal);
        if (btnRetryAmenities != null) {
            btnRetryAmenities.setOnClickListener(v -> loadAmenityCatalog());
        }

        view.findViewById(R.id.btnSkipAmenities).setOnClickListener(v -> {
            if (!guard.tryAcquire()) return;
            getWizardActivity().confirmStep(getString(R.string.amenities_none_title), noAmenitiesSummary(), getString(R.string.wizard_confirm_yes), () -> {
                clearAllAmenities();
                getWizardActivity().goToNextStepSkippingValidation();
            });
        });

        updateLiveTotal();
        loadAmenityCatalog();
    }

    @Override
    public String stepTitle() {
        return getString(R.string.step_title_amenities);
    }

    // Always optional - Next never blocks here.

    private void clearAllAmenities() {
        BookingWizardState state = getState();
        for (BookingWizardState.Stay stay : state.committedStays()) {
            stay.roomAmenityQty.clear();
            stay.amenityCatalog.clear();
            stay.amenities.clear();
        }
        state.selectedAmenities.clear();
        state.reloadActiveAmenities();
    }

    private void loadAmenityCatalog() {
        // "Additional Amenities" is deliberately Paid-only - free/complimentary amenities are the room's already-included features, not
        // something the guest opts into paying extra for here (pricing_type=paid, see ApiService#getAmenities).
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
     * @param loadFailed distinguishes a genuinely empty catalog (no retry offered - there's nothing to retry) from a failed
     *                   network/server call (shows a retry button).
     */
    private void renderAmenityCatalog(boolean loadFailed) {
        layoutAmenitiesItemsContainer.removeAllViews();
        BookingWizardState state = getState();
        List<BookingWizardState.Stay> stays = state.committedStays();

        if (addOnCatalog.isEmpty()) {
            layoutAmenitiesEmptyState.setVisibility(View.VISIBLE);
            layoutAmenitiesItemsContainer.setVisibility(View.GONE);
            tvAmenitiesEmptyState.setText(loadFailed ? R.string.addon_amenities_load_error : R.string.addon_amenities_empty);
            if (btnRetryAmenities != null) btnRetryAmenities.setVisibility(loadFailed ? View.VISIBLE : View.GONE);
            updateLiveTotal();
            return;
        }
        layoutAmenitiesEmptyState.setVisibility(View.GONE);
        layoutAmenitiesItemsContainer.setVisibility(View.VISIBLE);

        // What was chosen before (going Back and forward, a Modify, a restored wizard) carries over by amenity id, at the live price.
        for (BookingWizardState.Stay stay : stays) {
            if (stay.roomAmenityQty.isEmpty() && !stay.amenities.isEmpty()) stay.seedFromAggregate(new ArrayList<>(stay.amenities));
            stay.dropAmenitiesOfMissingRooms();
            refreshFromCatalog(stay);
        }
        state.reloadActiveAmenities();

        SimpleDateFormat fmt = new SimpleDateFormat("MMM d", Locale.US);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < stays.size(); i++) {
            BookingWizardState.Stay stay = stays.get(i);
            if (stay.checkIn == null || stay.checkOut == null) continue;
            layoutAmenitiesItemsContainer.addView(stayHeading(getString(R.string.amenity_stay_heading_format,
                    fmt.format(stay.checkIn.getTime()), fmt.format(stay.checkOut.getTime())), i == 0));
            // amenities that have no room yet (older rows) wait in "Any room" until the guest moves them to one
            if (stay.hasAnyRoomAmenities()) {
                layoutAmenitiesItemsContainer.addView(roomHeading(getString(R.string.amenity_any_room_heading)));
                for (AddOnAmenity amenity : addOnCatalog) {
                    layoutAmenitiesItemsContainer.addView(buildRow(inflater, stay, stay.anyRoomSlot(), amenity));
                }
            }
            for (BookingWizardState.Stay.Slot slot : stay.roomSlots()) {
                layoutAmenitiesItemsContainer.addView(roomHeading(getString(R.string.amenity_room_heading_format, slot.room.getName(), slot.number)));
                for (AddOnAmenity amenity : addOnCatalog) {
                    layoutAmenitiesItemsContainer.addView(buildRow(inflater, stay, slot, amenity));
                }
            }
        }
        updateLiveTotal();
    }

    /** Swaps the stay's remembered amenities for the live catalog's entries (same ids), dropping any that are no longer offered. */
    private void refreshFromCatalog(BookingWizardState.Stay stay) {
        List<String[]> keep = new ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> room : new ArrayList<>(stay.roomAmenityQty.entrySet())) {
            for (java.util.Map.Entry<String, Integer> e : new ArrayList<>(room.getValue().entrySet())) {
                keep.add(new String[]{room.getKey(), e.getKey(), String.valueOf(e.getValue())});
            }
        }
        for (String[] k : keep) {
            AddOnAmenity fresh = null;
            for (AddOnAmenity a : addOnCatalog) if (a.getId().equals(k[1])) fresh = a;
            if (fresh == null) {
                AddOnAmenity old = stay.amenityCatalog.get(k[1]);
                if (old != null) stay.setAmenityQty(k[0], old, 0); // no longer offered
            } else {
                stay.setAmenityQty(k[0], fresh, Math.min(Integer.parseInt(k[2]), fresh.getMaxQuantity()));
            }
        }
    }

    private View stayHeading(String text, boolean first) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setTextColor(requireContext().getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(first ? 4 : 20);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View roomHeading(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setTextColor(requireContext().getColor(R.color.velocity_red_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View buildRow(LayoutInflater inflater, BookingWizardState.Stay stay, BookingWizardState.Stay.Slot slot, AddOnAmenity amenity) {
        View row = inflater.inflate(R.layout.item_amenity_room_row, layoutAmenitiesItemsContainer, false);
        ImageView icon = row.findViewById(R.id.ivAmenityRowIcon);
        TextView name = row.findViewById(R.id.tvAmenityRowName);
        TextView desc = row.findViewById(R.id.tvAmenityRowDesc);
        TextView price = row.findViewById(R.id.tvAmenityRowPrice);
        TextView qtyView = row.findViewById(R.id.tvAmenityRowQty);
        TextView line = row.findViewById(R.id.tvAmenityRowLine);
        TextView limit = row.findViewById(R.id.tvAmenityRowLimit);
        View minus = row.findViewById(R.id.btnAmenityRowMinus);
        View plus = row.findViewById(R.id.btnAmenityRowPlus);

        icon.setImageResource(RoomVisuals.getAmenityIcon(amenity.getName()));
        name.setText(amenity.getName());
        if (amenity.getDescription() == null || amenity.getDescription().trim().isEmpty()) desc.setVisibility(View.GONE);
        else desc.setText(amenity.getDescription());
        boolean isFree = amenity.getPrice() <= 0.009;
        price.setText(isFree ? getString(R.string.addon_amenity_free_label) : getString(R.string.amenity_price_each_format, MoneyFormat.format(amenity.getPrice())));

        Runnable refresh = () -> {
            int qty = stay.amenityQty(slot.key, amenity.getId());
            qtyView.setText(String.valueOf(qty));
            minus.setEnabled(qty > 0);
            minus.setAlpha(qty > 0 ? 1f : 0.4f);
            if (qty > 0 && !isFree) {
                line.setText(getString(R.string.amenity_line_format, qty, MoneyFormat.format(amenity.getPrice()), MoneyFormat.format(amenity.getPrice() * qty)));
                line.setVisibility(View.VISIBLE);
            } else {
                line.setVisibility(View.GONE);
            }
        };
        refresh.run();

        minus.setOnClickListener(v -> {
            limit.setVisibility(View.GONE);
            int qty = stay.amenityQty(slot.key, amenity.getId());
            if (qty <= 0) return;
            changeQty(stay, slot, amenity, qty - 1);
            refresh.run();
        });
        plus.setOnClickListener(v -> {
            int qty = stay.amenityQty(slot.key, amenity.getId());
            int roomCap = slot.room != null ? Math.max(1, slot.room.getCapacity()) : Math.max(1, stay.capacity());
            if (qty >= roomCap) {
                limit.setText(getString(R.string.error_amenity_qty_exceeds_capacity));
                limit.setVisibility(View.VISIBLE);
                return;
            }
            if (stay.amenityTotalQty(amenity.getId()) >= amenity.getMaxQuantity()) {
                limit.setText(getResources().getQuantityString(R.plurals.amenity_only_left_format, amenity.getMaxQuantity(), amenity.getMaxQuantity()));
                limit.setVisibility(View.VISIBLE);
                return;
            }
            limit.setVisibility(View.GONE);
            changeQty(stay, slot, amenity, qty + 1);
            refresh.run();
        });
        return row;
    }

    private void changeQty(BookingWizardState.Stay stay, BookingWizardState.Stay.Slot slot, AddOnAmenity amenity, int qty) {
        stay.setAmenityQty(slot.key, amenity, qty);
        getState().reloadActiveAmenities();
        updateLiveTotal();
    }

    /** "Amenities ₱300.00 · Grand total ₱18,900.00" */
    private void updateLiveTotal() {
        if (tvLiveTotal == null) return;
        BookingWizardState state = getState();
        tvLiveTotal.setText(getString(R.string.amenities_live_total_format, MoneyFormat.format(state.amenitiesTotal()),
                MoneyFormat.format(state.roomsTotal() + state.amenitiesTotal())));
    }

    private boolean anyAmenities() {
        return getState().amenitiesTotal() > 0.004 || hasAnyQuantity();
    }

    private boolean hasAnyQuantity() {
        for (BookingWizardState.Stay s : getState().allStays()) if (!s.amenities.isEmpty()) return true;
        return false;
    }

    private String noAmenitiesSummary() {
        BookingWizardState state = getState();
        return getString(R.string.amenities_none_summary_format, MoneyFormat.format(state.roomsTotal()));
    }

    /** With something chosen: the review (per stay and room, quantities and amounts, totals). With nothing: straight to the confirmation. */
    @Override
    public boolean interceptNext(Runnable proceed) {
        BookingWizardState state = getState();
        if (!anyAmenities()) return false;
        List<ReviewSheet.Section> sections = WizardReview.amenities(requireContext(), state);
        List<ReviewSheet.Row> totals = WizardReview.totals(requireContext(), state);
        ReviewSheet.show(getWizardActivity(), getString(R.string.review_amenities_title), null, sections, totals,
                getString(R.string.review_edit), getString(R.string.review_continue), null, proceed);
        return true;
    }

    @Override
    public String nextConfirmTitle() {
        return getString(anyAmenities() ? R.string.proceed_with_amenities : R.string.amenities_none_title);
    }

    @Override
    public String nextConfirmSummary() {
        BookingWizardState state = getState();
        return anyAmenities()
                ? getString(R.string.amenities_live_total_format, MoneyFormat.format(state.amenitiesTotal()), MoneyFormat.format(state.roomsTotal() + state.amenitiesTotal()))
                : noAmenitiesSummary();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
