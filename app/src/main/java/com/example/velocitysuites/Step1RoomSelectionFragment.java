package com.example.velocitysuites;

import android.content.res.ColorStateList;
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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Step 2: select one or more rooms - guest total capacity is auto-calculated
 * from the selection (BookingWizardState#totalSelectedCapacity()), and Next
 * is blocked with zero rooms selected (validateBeforeNext()). Ported from
 * BookingAndReservationActivity's Available Room Form
 * (showAvailableRoomsDialog()/buildAvailableRoomCard()/addRoomsToSelection()/
 * renderSelectedRooms()) - same dialog/item layouts, same staging mechanics,
 * now reading/writing BookingWizardState instead of Activity fields.
 * Dates are already chosen by the time this step is shown (step 1, see
 * Step2DatesFragment), so every availability fetch here is date-range-aware
 * (repository.refreshRooms(checkIn, checkOut, ...)) instead of the plain
 * undated availableCount this step used to show back when it ran first.
 * Any room selection carried over from a previous pass through this step (or
 * seeded before the wizard even opened, via PendingWizardRooms/Book Again) is
 * re-validated against the current dates every time this step is shown - see
 * reconcileSelectedRoomsWithAvailability().
 */
public class Step1RoomSelectionFragment extends WizardStepFragment {

    private RoomRepository repository;
    private List<Room> allRooms = new ArrayList<>();

    private LinearLayout layoutSelectedRooms;
    private LinearLayout layoutNoRoomsSelected;
    private TextView tvCapacityIndicator;
    private TextView tvRoomsSelectedCount;
    private TextView tvRoomSelectionHelper;
    private View layoutCheckingAvailability;
    private View layoutNoRoomsAvailableForDates;
    private View layoutRoomSelectionContent;
    private View layoutRoomLoadError;

    /** True once refreshRooms() has ever come back successfully for this step instance - distinguishes "never loaded, fetch just failed" (show the blocking error state) from "loaded fine before, this is just a background re-check that happened to fail" (fail open, same as before). */
    private boolean roomsLoadedSuccessfullyOnce = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step1_rooms, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = RoomRepository.getInstance(requireContext());

        // Step 1 (Dates) must run first - if this step is somehow reached
        // without a valid date range (restored/stale currentStepIndex after a
        // process-death recreation, since BookingWizardState itself is never
        // saved/restored - see BookingWizardActivity#onCreate()), bounce back
        // rather than showing/computing availability against missing dates.
        BookingWizardState guardState = getState();
        if (guardState.checkIn == null || guardState.checkOut == null || !guardState.checkOut.after(guardState.checkIn)) {
            Toast.makeText(requireContext(), R.string.select_date_hint, Toast.LENGTH_SHORT).show();
            // Deferred: this onViewCreated() call is itself running inside the
            // host's showStep()/commitNow() for THIS step - starting another
            // FragmentTransaction synchronously here would hit "FragmentManager
            // is already executing transactions". Posting runs it right after
            // the current transaction finishes instead.
            view.post(() -> getWizardActivity().goToStep(1));
            return;
        }

        layoutSelectedRooms = view.findViewById(R.id.layoutSelectedRooms);
        layoutNoRoomsSelected = view.findViewById(R.id.layoutNoRoomsSelected);
        tvCapacityIndicator = view.findViewById(R.id.tvCapacityIndicator);
        tvRoomsSelectedCount = view.findViewById(R.id.tvRoomsSelectedCount);
        tvRoomSelectionHelper = view.findViewById(R.id.tvRoomSelectionHelper);
        layoutCheckingAvailability = view.findViewById(R.id.layoutCheckingAvailability);
        layoutNoRoomsAvailableForDates = view.findViewById(R.id.layoutNoRoomsAvailableForDates);
        layoutRoomSelectionContent = view.findViewById(R.id.layoutRoomSelectionContent);
        layoutRoomLoadError = view.findViewById(R.id.layoutRoomLoadError);

        view.findViewById(R.id.btnAddRoom).setOnClickListener(v -> onAddRoomClicked());
        view.findViewById(R.id.btnChangeDates).setOnClickListener(v -> getWizardActivity().goToStep(1));
        view.findViewById(R.id.btnRetryRoomLoad).setOnClickListener(v -> loadAvailableRooms());

        renderSelectedRooms();
        updateCapacityIndicator();

        // Populate allRooms proactively (not just lazily inside
        // onAddRoomClicked()) - a guest arriving here with a room already
        // pre-selected from landing.xml/roombrowsing.xml never needs to open
        // the Add Room dialog at all, so without this, adjustRoomQuantity()'s
        // own allRooms lookup would stay empty and wrongly fall back to a
        // cap of "whatever's already selected", blocking + immediately
        // regardless of real availability (see that method's fallback fix).
        // Dated (state.checkIn/checkOut are guaranteed non-null past the
        // guard above) so availableCount reflects this exact stay, then used
        // to reconcile any carried-over selection against it.
        loadAvailableRooms();
    }

    /** Runs the date-aware availability fetch that backs this step's room list - called from onViewCreated() and again from the error state's Retry button. */
    private void loadAvailableRooms() {
        layoutCheckingAvailability.setVisibility(View.VISIBLE);
        layoutRoomSelectionContent.setVisibility(View.GONE);
        layoutNoRoomsAvailableForDates.setVisibility(View.GONE);
        layoutRoomLoadError.setVisibility(View.GONE);
        repository.refreshRooms(getState().checkIn, getState().checkOut, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (!isAdded()) return;
                allRooms = result;
                roomsLoadedSuccessfullyOnce = true;
                reconcileSelectedRoomsWithAvailability();
                showRoomAvailabilityResult();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                layoutCheckingAvailability.setVisibility(View.GONE);
                if (roomsLoadedSuccessfullyOnce) {
                    // A later background re-check (e.g. from Add Room) failing
                    // doesn't need to block the whole step - the guest already
                    // has a working room list from the earlier successful load.
                    layoutRoomSelectionContent.setVisibility(View.VISIBLE);
                } else {
                    // Never successfully loaded for this step instance - showing
                    // the normal Add Room UI here would let the guest open an
                    // empty dialog and see a misleading "no rooms available"
                    // toast, indistinguishable from a real zero-availability
                    // date range. Block with a real error + Retry instead.
                    layoutRoomLoadError.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /** Switches between the normal Add Room UI and the "No Available Rooms" empty state, based on whether any room type has availableCount > 0 for the current dates. */
    private void showRoomAvailabilityResult() {
        layoutCheckingAvailability.setVisibility(View.GONE);
        boolean anyAvailable = false;
        for (Room r : allRooms) {
            if (r.getAvailableCount() > 0) {
                anyAvailable = true;
                break;
            }
        }
        layoutNoRoomsAvailableForDates.setVisibility(anyAvailable ? View.GONE : View.VISIBLE);
        layoutRoomSelectionContent.setVisibility(anyAvailable ? View.VISIBLE : View.GONE);
    }

    /**
     * Re-validates whatever's already in state.selectedRooms (carried over
     * from an earlier pass through this step, or pre-seeded before the
     * wizard opened) against the just-fetched date-aware availableCount for
     * the guest's current check-in/check-out range - covers both "the guest
     * went back to Step 1 and changed the dates" and "another guest took the
     * remaining inventory in the meantime". Never silently keeps a selection
     * that's no longer available: trims it to whatever's left, or drops it
     * entirely if nothing's left, and tells the guest either way.
     */
    private void reconcileSelectedRoomsWithAvailability() {
        BookingWizardState state = getState();
        if (state.selectedRooms.isEmpty()) return;

        boolean changed = false;
        for (Map.Entry<String, List<Room>> entry : new ArrayList<>(state.selectedRoomsGroupedByType().entrySet())) {
            List<Room> group = entry.getValue();
            Room fresh = null;
            for (Room r : allRooms) {
                if (r.getId().equals(entry.getKey())) {
                    fresh = r;
                    break;
                }
            }
            int available = fresh != null ? fresh.getAvailableCount() : 0;
            if (available >= group.size()) continue;

            changed = true;
            int toRemove = group.size() - Math.max(0, available);
            for (int i = 0; i < toRemove; i++) {
                state.selectedRooms.remove(group.get(group.size() - 1 - i));
            }
        }

        if (changed) {
            Toast.makeText(requireContext(), R.string.error_room_no_longer_available_for_dates, Toast.LENGTH_LONG).show();
            renderSelectedRooms();
            updateCapacityIndicator();
        }
    }

    @Override
    public String stepTitle() {
        return "Room Selection";
    }

    @Override
    public void onWizardStepShown() {
        updateNextButtonState();
    }

    @Override
    public boolean validateBeforeNext() {
        if (getState().selectedRooms.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_no_rooms_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /** Keeps Next literally disabled (not just toast-blocked) while zero rooms are selected - re-checked after every add/remove/quantity change below. */
    private void updateNextButtonState() {
        getWizardActivity().setNextEnabled(!getState().selectedRooms.isEmpty());
    }

    private int currentQtyForId(String id) {
        int count = 0;
        for (Room r : getState().selectedRooms) {
            if (r.getId().equals(id)) count++;
        }
        return count;
    }

    private void onAddRoomClicked() {
        repository.refreshRooms(getState().checkIn, getState().checkOut, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                allRooms = result;
                roomsLoadedSuccessfullyOnce = true;
                openAvailableRoomsDialogOrToast();
            }

            @Override
            public void onError(String message) {
                if (roomsLoadedSuccessfullyOnce) {
                    // Fails open onto the last successfully-fetched list rather
                    // than blocking the guest over a transient re-check failure.
                    openAvailableRoomsDialogOrToast();
                } else if (isAdded()) {
                    // Nothing has ever loaded successfully - allRooms is empty,
                    // so falling through to openAvailableRoomsDialogOrToast()
                    // would show a misleading "no rooms available" toast
                    // instead of the real network/server error.
                    Toast.makeText(requireContext(), R.string.room_load_error_toast, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void openAvailableRoomsDialogOrToast() {
        List<Room> browsable = new ArrayList<>();
        for (Room r : allRooms) {
            if (r.getAvailableCount() - currentQtyForId(r.getId()) <= 0) continue;
            browsable.add(r);
        }

        // The one-time "Modify Reservation" flow can now fully replace the room
        // selection - multiple room types, multiple rooms per type - since
        // Api\ReservationController::update() accepts the same `rooms[]` shape
        // store() does (see RoomRepository#updateReservationFull(List<List<Room>>, ...)
        // and Step8ReviewPaymentFragment#saveEditedReservation()). No same-type
        // restriction during edit mode anymore - the Add Room dialog behaves
        // identically whether creating or modifying.
        if (browsable.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_rooms_available, Toast.LENGTH_LONG).show();
            return;
        }
        showAvailableRoomsDialog(browsable);
    }

    private void showAvailableRoomsDialog(List<Room> rooms) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_available_rooms, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        TextView tvSubtitle = dialogView.findViewById(R.id.tvAvailableRoomsSubtitle);
        View btnClose = dialogView.findViewById(R.id.btnCloseAvailableRooms);
        LinearLayout listContainer = dialogView.findViewById(R.id.layoutAvailableRoomsList);
        View emptyState = dialogView.findViewById(R.id.layoutAvailableRoomsEmpty);
        View layoutSelectionFooter = dialogView.findViewById(R.id.layoutSelectionFooter);
        TextView tvSelectionTotal = dialogView.findViewById(R.id.tvSelectionTotal);
        TextView tvViewSelectionDetails = dialogView.findViewById(R.id.tvViewSelectionDetails);
        MaterialButton btnCommit = dialogView.findViewById(R.id.btnBookOrReserveRoom);
        btnCommit.setText(getState().isBookingMode() ? R.string.book_now : R.string.reserve_now);

        tvSubtitle.setText(R.string.available_rooms_subtitle_placeholder);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        Map<String, Room> stagedRooms = new LinkedHashMap<>();
        Map<String, Integer> stagedQuantities = new LinkedHashMap<>();
        long nights = getState().nights();

        // Hidden (not just disabled) until at least one room is staged, and hidden
        // again the instant every staged room is deselected - reflects the actual
        // current selection state on every call, not just when the dialog opens
        // (see task's "Important Functional Requirement" - re-run after every
        // select/deselect/quantity change below, never assumed). Deliberately
        // compact - only Room Total (the same price x nights x qty formula
        // BookingWizardState#roomsTotal() uses, just applied to the not-yet-
        // committed staged selection) lives in this footer; the full room-type/
        // quantity/subtotal breakdown lives behind "View Selection Details"
        // (showSelectedRoomsSummaryDialog()) so this footer - and therefore the
        // room list above it - stays as compact/tall as possible.
        Runnable updateFooter = () -> {
            int totalQty = 0;
            double totalAmount = 0;
            for (Map.Entry<String, Integer> e : stagedQuantities.entrySet()) {
                int qty = e.getValue();
                totalQty += qty;
                Room staged = stagedRooms.get(e.getKey());
                if (staged != null) totalAmount += staged.getPricePerNight() * nights * qty;
            }

            layoutSelectionFooter.setVisibility(totalQty > 0 ? View.VISIBLE : View.GONE);
            btnCommit.setVisibility(totalQty > 0 ? View.VISIBLE : View.GONE);
            btnCommit.setEnabled(true);
            if (totalQty == 0) return;

            tvSelectionTotal.setText(String.format(Locale.US, getString(R.string.price_format), totalAmount));
        };
        updateFooter.run();

        tvViewSelectionDetails.setOnClickListener(v ->
                showSelectedRoomsSummaryDialog(stagedRooms, stagedQuantities, nights));

        listContainer.removeAllViews();
        for (Room room : rooms) {
            listContainer.addView(buildAvailableRoomCard(room, stagedRooms, stagedQuantities, updateFooter));
        }
        emptyState.setVisibility(rooms.isEmpty() ? View.VISIBLE : View.GONE);

        btnCommit.setOnClickListener(v -> {
            // Guards against a rapid double-tap adding the staged rooms twice
            // before the dialog has a chance to dismiss - this handler is
            // synchronous (no network call), so disabling immediately is
            // sufficient rather than needing an async in-flight flag.
            btnCommit.setEnabled(false);
            List<String> trimmedOrDropped = new ArrayList<>();
            for (Map.Entry<String, Room> entry : stagedRooms.entrySet()) {
                String roomId = entry.getKey();
                Room staged = entry.getValue();
                int requestedQty = stagedQuantities.getOrDefault(roomId, 0);
                if (requestedQty <= 0) continue;

                Room fresh = staged;
                for (Room r : allRooms) {
                    if (r.getId().equals(roomId)) {
                        fresh = r;
                        break;
                    }
                }
                int remaining = fresh.getAvailableCount() - currentQtyForId(roomId);
                int qtyToAdd = Math.min(requestedQty, Math.max(0, remaining));
                if (qtyToAdd <= 0) {
                    trimmedOrDropped.add(fresh.getName());
                    continue;
                }
                if (qtyToAdd < requestedQty) {
                    trimmedOrDropped.add(fresh.getName());
                }
                addRoomsToSelection(fresh, qtyToAdd);
            }
            if (!trimmedOrDropped.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.no_more_rooms_available_toast,
                        String.join(", ", trimmedOrDropped)), Toast.LENGTH_LONG).show();
            }
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        // Cap the dialog's window height to a fraction of the display (rather than
        // leaving it wrap_content/unbounded) so the NestedScrollView's layout_weight="1"
        // above has a real, finite height to distribute - without this, the window
        // sizes to fit ALL room cards unbounded, pushing the Book Now/Reserve Now
        // footer below the visible/clipped screen area once more than a couple of
        // room types are shown. Must run after show() - the window isn't attached
        // (getWindow() calls before this point can't resize it) until then.
        if (dialog.getWindow() != null) {
            int maxDialogHeightPx = (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, maxDialogHeightPx);
        }
    }

    /** "View Selection Details" - the full per-room-type price breakdown (quantity × price/night × nights = subtotal) behind the compact footer summary. */
    private void showSelectedRoomsSummaryDialog(Map<String, Room> stagedRooms, Map<String, Integer> stagedQuantities, long nights) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_selected_rooms_summary, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        LinearLayout listContainer = dialogView.findViewById(R.id.layoutSelectionSummaryList);
        TextView tvRoomTypesCount = dialogView.findViewById(R.id.tvSummaryRoomTypesCount);
        TextView tvTotalRooms = dialogView.findViewById(R.id.tvSummaryTotalRooms);
        TextView tvTotalAmount = dialogView.findViewById(R.id.tvSummaryTotalAmount);
        dialogView.findViewById(R.id.btnCloseSelectionSummary).setOnClickListener(v -> dialog.dismiss());

        listContainer.removeAllViews();
        int typeCount = 0;
        int totalQty = 0;
        double totalAmount = 0;
        for (Map.Entry<String, Room> entry : stagedRooms.entrySet()) {
            int qty = stagedQuantities.getOrDefault(entry.getKey(), 0);
            if (qty <= 0) continue;
            typeCount++;
            totalQty += qty;
            Room room = entry.getValue();
            double subtotal = room.getPricePerNight() * nights * qty;
            totalAmount += subtotal;
            listContainer.addView(buildSelectionBreakdownRow(room, qty, nights, subtotal));
        }

        tvRoomTypesCount.setText(getResources().getQuantityString(R.plurals.room_types_count_plain, typeCount, typeCount));
        tvTotalRooms.setText(getResources().getQuantityString(R.plurals.rooms_count_plain, totalQty, totalQty));
        tvTotalAmount.setText(String.format(Locale.US, getString(R.string.price_format), totalAmount));

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            int maxDialogHeightPx = (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, maxDialogHeightPx);
        }
    }

    private View buildSelectionBreakdownRow(Room room, int qty, long nights, double subtotal) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = Math.round(16 * density);
        row.setLayoutParams(rowParams);

        TextView tvName = new TextView(requireContext());
        tvName.setText(room.getName());
        tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.velocity_text_primary));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextSize(14f);
        row.addView(tvName);

        TextView tvMath = new TextView(requireContext());
        tvMath.setText(getResources().getQuantityString(R.plurals.rooms_count_plain, qty, qty)
                + " × " + String.format(Locale.US, getString(R.string.price_format), room.getPricePerNight())
                + " × " + getResources().getQuantityString(R.plurals.nights_count_plain, (int) nights, (int) nights));
        tvMath.setTextColor(ContextCompat.getColor(requireContext(), R.color.velocity_text_secondary));
        tvMath.setTextSize(12f);
        LinearLayout.LayoutParams mathParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mathParams.topMargin = Math.round(2 * density);
        tvMath.setLayoutParams(mathParams);
        row.addView(tvMath);

        TextView tvSubtotal = new TextView(requireContext());
        tvSubtotal.setText(getString(R.string.subtotal_label) + ": "
                + String.format(Locale.US, getString(R.string.price_format), subtotal));
        tvSubtotal.setTextColor(ContextCompat.getColor(requireContext(), R.color.velocity_red_primary));
        tvSubtotal.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSubtotal.setTextSize(13f);
        LinearLayout.LayoutParams subtotalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtotalParams.topMargin = Math.round(4 * density);
        tvSubtotal.setLayoutParams(subtotalParams);
        row.addView(tvSubtotal);

        return row;
    }

    /**
     * Builds one summary card for the Available Room List. The guest has two
     * equivalent ways to select this room type, and both write through the
     * same {@code stagedRooms}/{@code stagedQuantities} maps (the one shared
     * source of truth) via {@code applyQuantity} below, so either path stays
     * perfectly in sync with the other and with the footer:
     *  - Directly on the card: the +/- stepper wired below (no need to open
     *    Full Details first).
     *  - Tapping the card itself (image/name/facts/"View Full Details" row -
     *    anywhere that isn't the stepper buttons, since a clickable child
     *    like MaterialButton consumes its own touch and never bubbles the
     *    click up to cardContent's listener) opens
     *    RoomDetailsDialog#showForStaging(), seeded with the current staged
     *    quantity, whose own stepper calls back into the same applyQuantity.
     */
    private View buildAvailableRoomCard(Room room, Map<String, Room> stagedRooms,
                                         Map<String, Integer> stagedQuantities, Runnable updateFooter) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_available_room, null, false);

        TextView tvName = card.findViewById(R.id.roomName);
        TextView tvTypeAndCapacity = card.findViewById(R.id.roomTypeAndCapacity);
        TextView tvBedType = card.findViewById(R.id.roomBedType);
        TextView tvRoomSize = card.findViewById(R.id.roomSizeText);
        TextView tvPrice = card.findViewById(R.id.roomPrice);
        TextView tvAvailableBadge = card.findViewById(R.id.roomAvailableBadge);
        ImageView ivImage = card.findViewById(R.id.roomImage);
        ImageView ivTypeIcon = card.findViewById(R.id.roomTypeIcon);
        LinearLayout amenitiesRow = card.findViewById(R.id.amenitiesIconsRow);
        TextView tvQty = card.findViewById(R.id.tvQty);
        MaterialButton btnQtyMinus = card.findViewById(R.id.btnQtyMinus);
        MaterialButton btnQtyPlus = card.findViewById(R.id.btnQtyPlus);
        View layoutSelectedIndicator = card.findViewById(R.id.layoutSelectedIndicator);
        TextView tvSelectedIndicator = card.findViewById(R.id.tvSelectedIndicator);
        com.google.android.material.card.MaterialCardView cardRoot =
                (com.google.android.material.card.MaterialCardView) card;
        View cardContent = card.findViewById(R.id.availableRoomCardContent);

        int remainingBase = Math.max(0, room.getAvailableCount() - currentQtyForId(room.getId()));
        int maxQty = Math.max(1, remainingBase);

        tvName.setText(room.getName());
        tvTypeAndCapacity.setText(getString(R.string.room_type_capacity_format, room.getType(), room.getCapacity()));
        tvBedType.setText(room.getBedType() == null || room.getBedType().isEmpty()
                ? RoomVisuals.getBedType(requireContext(), room.getType(), room.getCapacity())
                : room.getBedType());
        tvRoomSize.setText(room.getRoomSize() == null || room.getRoomSize().isEmpty()
                ? getString(R.string.not_specified)
                : room.getRoomSize());
        tvPrice.setText(getString(R.string.price_format_per_night, room.getPricePerNight()));
        ivTypeIcon.setImageResource(RoomVisuals.getTypeIcon(room.getType()));

        int fallbackImage = RoomVisuals.getRoomImage(room.getType());
        if (room.getImageUrl() != null && !room.getImageUrl().isEmpty()) {
            Glide.with(this).load(room.getImageUrl()).placeholder(fallbackImage).error(fallbackImage).into(ivImage);
        } else if (room.getImageResId() != 0) {
            ivImage.setImageResource(room.getImageResId());
        } else {
            ivImage.setImageResource(fallbackImage);
        }

        bindAvailableRoomAmenities(amenitiesRow, room);

        // Selected state is never signaled by color alone - text ("N Rooms
        // Selected") + check icon, and the card's own border/background, all
        // change together.
        int strokeSelected = Math.round(2 * getResources().getDisplayMetrics().density);
        Runnable refreshCardUi = () -> {
            int qty = stagedQuantities.getOrDefault(room.getId(), 0);
            tvQty.setText(String.valueOf(qty));
            btnQtyMinus.setEnabled(qty > 0);
            btnQtyPlus.setEnabled(qty < maxQty);
            tvAvailableBadge.setText(getString(R.string.available_qty_format, Math.max(0, remainingBase - qty)));
            if (qty > 0) {
                layoutSelectedIndicator.setVisibility(View.VISIBLE);
                tvSelectedIndicator.setText(getResources().getQuantityString(R.plurals.rooms_staged_total_count, qty, qty));
                cardRoot.setStrokeWidth(strokeSelected);
                cardRoot.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.velocity_red_primary));
                // The card's own cardBackgroundColor sits behind cardContent's
                // opaque bg_glass_card_white drawable and would never actually
                // show through - tint that drawable directly instead.
                cardContent.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.velocity_red_bg_end)));
            } else {
                layoutSelectedIndicator.setVisibility(View.GONE);
                cardRoot.setStrokeWidth(0);
                cardContent.setBackgroundTintList(null);
            }
        };
        refreshCardUi.run();

        // The one mutation point both selection paths funnel through -
        // clamped to [0, maxQty], updates the shared staged maps, then
        // refreshes this card and the dialog footer together.
        java.util.function.IntConsumer applyQuantity = requested -> {
            int clamped = Math.max(0, Math.min(requested, maxQty));
            if (clamped > 0) {
                stagedRooms.put(room.getId(), room);
                stagedQuantities.put(room.getId(), clamped);
            } else {
                stagedRooms.remove(room.getId());
                stagedQuantities.remove(room.getId());
            }
            refreshCardUi.run();
            updateFooter.run();
        };

        btnQtyMinus.setOnClickListener(v -> {
            int current = stagedQuantities.getOrDefault(room.getId(), 0);
            if (current > 0) applyQuantity.accept(current - 1);
        });
        btnQtyPlus.setOnClickListener(v -> {
            int current = stagedQuantities.getOrDefault(room.getId(), 0);
            if (current < maxQty) {
                applyQuantity.accept(current + 1);
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_more_rooms_available_toast, room.getName()), Toast.LENGTH_SHORT).show();
            }
        });

        cardContent.setOnClickListener(v -> RoomDetailsDialog.showForStaging(
                requireActivity(), room, getState().nights(), remainingBase,
                stagedQuantities.getOrDefault(room.getId(), 0),
                (r, newQty) -> applyQuantity.accept(newQty),
                // Final safety-net refresh on close (Done/X/back/outside tap) -
                // the per-tap callback above already keeps things live while
                // the sheet is open, this just guarantees consistency either way.
                refreshCardUi));

        return card;
    }

    private void bindAvailableRoomAmenities(LinearLayout amenitiesRow, Room room) {
        amenitiesRow.removeAllViews();
        List<RoomAmenity> amenities = room.getAmenities();
        if (amenities == null) return;
        int maxIcons = 4;
        int shown = Math.min(amenities.size(), maxIcons);
        float density = getResources().getDisplayMetrics().density;
        int iconSize = Math.round(16 * density);
        int iconMarginEnd = Math.round(12 * density);
        ColorStateList grayTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.velocity_inactive_gray));

        for (int i = 0; i < shown; i++) {
            RoomAmenity amenity = amenities.get(i);
            ImageView icon = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);
            params.setMarginEnd(iconMarginEnd);
            icon.setLayoutParams(params);
            icon.setImageResource(RoomVisuals.getAmenityIcon(amenity.getName()));
            icon.setImageTintList(grayTint);
            icon.setContentDescription(amenity.getName());
            amenitiesRow.addView(icon);
        }
        int remaining = amenities.size() - shown;
        if (remaining > 0) {
            TextView more = new TextView(requireContext());
            more.setText(getString(R.string.amenities_more_format, remaining));
            more.setTextColor(ContextCompat.getColor(requireContext(), R.color.velocity_inactive_gray));
            more.setTextSize(12f);
            amenitiesRow.addView(more);
        }
    }

    private void addRoomsToSelection(Room room, int quantity) {
        for (int i = 0; i < quantity; i++) {
            getState().selectedRooms.add(room);
        }
        renderSelectedRooms();
        updateCapacityIndicator();
        Toast.makeText(requireContext(), getString(R.string.room_added_toast, room.getName()), Toast.LENGTH_SHORT).show();
    }

    private void renderSelectedRooms() {
        if (layoutSelectedRooms == null) return;
        layoutSelectedRooms.removeAllViews();

        Map<String, List<Room>> grouped = getState().selectedRoomsGroupedByType();

        for (Map.Entry<String, List<Room>> entry : grouped.entrySet()) {
            List<Room> group = entry.getValue();
            Room representative = group.get(0);
            int qty = group.size();

            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_selected_room, layoutSelectedRooms, false);
            row.setTag(entry.getKey());

            TextView tvName = row.findViewById(R.id.tvSelectedRoomName);
            TextView tvMeta = row.findViewById(R.id.tvSelectedRoomMeta);
            TextView tvQty = row.findViewById(R.id.tvSelectedRoomQty);
            TextView tvSubtotal = row.findViewById(R.id.tvSelectedRoomSubtotal);
            ImageView ivImage = row.findViewById(R.id.ivSelectedRoomImage);
            MaterialButton btnDecrease = row.findViewById(R.id.btnDecreaseQty);
            MaterialButton btnIncrease = row.findViewById(R.id.btnIncreaseQty);
            View btnRemove = row.findViewById(R.id.btnRemoveSelectedRoom);

            tvName.setText(representative.getName());
            tvMeta.setText(getString(R.string.capacity_persons_format, representative.getCapacity())
                    + " · " + String.format(Locale.US, getString(R.string.price_format_per_night), representative.getPricePerNight()));
            tvQty.setText(String.valueOf(qty));
            // Dates are already known by this step (step 1, before this one),
            // so this can show the real nightly-adjusted subtotal instead of
            // just rate x quantity - same formula Step 8's review uses.
            tvSubtotal.setText(String.format(Locale.US, getString(R.string.price_format),
                    representative.getPricePerNight() * getState().nights() * qty));

            int fallbackImage = RoomVisuals.getRoomImage(representative.getType());
            if (representative.getImageUrl() != null && !representative.getImageUrl().isEmpty()) {
                Glide.with(this).load(representative.getImageUrl()).placeholder(fallbackImage).error(fallbackImage).into(ivImage);
            } else if (representative.getImageResId() != 0) {
                ivImage.setImageResource(representative.getImageResId());
            } else {
                ivImage.setImageResource(fallbackImage);
            }

            btnDecrease.setOnClickListener(v -> adjustRoomQuantity(entry.getKey(), -1));
            btnIncrease.setOnClickListener(v -> adjustRoomQuantity(entry.getKey(), 1));
            btnRemove.setOnClickListener(v -> removeSelectedRoom(entry.getKey()));

            layoutSelectedRooms.addView(row);
        }

        layoutNoRoomsSelected.setVisibility(getState().selectedRooms.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void removeSelectedRoom(String roomId) {
        String removedName = null;
        for (Room r : getState().selectedRooms) {
            if (r.getId().equals(roomId)) {
                removedName = r.getName();
                break;
            }
        }
        getState().selectedRooms.removeIf(existing -> existing.getId().equals(roomId));

        renderSelectedRooms();
        updateCapacityIndicator();
        if (removedName != null) {
            Toast.makeText(requireContext(), getString(R.string.room_removed_toast, removedName), Toast.LENGTH_SHORT).show();
        }
    }

    private void adjustRoomQuantity(String roomId, int delta) {
        if (delta > 0) {
            Room fresh = null;
            for (Room r : allRooms) {
                if (r.getId().equals(roomId)) {
                    fresh = r;
                    break;
                }
            }
            // allRooms may not have loaded yet (e.g. the proactive refresh in
            // onViewCreated() hasn't returned) - fall back to the already-
            // selected Room's own availableCount snapshot instead of
            // pessimistically capping at the current quantity (which would
            // always block + regardless of real availability).
            Room toAdd = fresh;
            if (toAdd == null) {
                for (Room r : getState().selectedRooms) {
                    if (r.getId().equals(roomId)) {
                        toAdd = r;
                        break;
                    }
                }
            }
            if (toAdd == null) return;

            int cap = fresh != null ? fresh.getAvailableCount() : toAdd.getAvailableCount();
            if (currentQtyForId(roomId) >= cap) {
                Toast.makeText(requireContext(), getString(R.string.no_more_rooms_available_toast,
                        toAdd.getName()), Toast.LENGTH_SHORT).show();
                return;
            }
            getState().selectedRooms.add(toAdd);
        } else {
            List<Room> selectedRooms = getState().selectedRooms;
            for (int i = selectedRooms.size() - 1; i >= 0; i--) {
                if (selectedRooms.get(i).getId().equals(roomId)) {
                    selectedRooms.remove(i);
                    break;
                }
            }
        }

        renderSelectedRooms();
        updateCapacityIndicator();
    }

    private void updateCapacityIndicator() {
        BookingWizardState state = getState();
        if (state.selectedRooms.isEmpty()) {
            tvCapacityIndicator.setText(R.string.add_room_hint);
        } else {
            tvCapacityIndicator.setText(getResources().getQuantityString(
                    R.plurals.total_capacity_format, state.totalSelectedCapacity(), state.totalSelectedCapacity()));
        }
        if (state.selectedRooms.isEmpty()) {
            tvRoomsSelectedCount.setText(R.string.no_rooms_selected_yet);
        } else {
            tvRoomsSelectedCount.setText(getResources().getQuantityString(
                    R.plurals.rooms_selected_count, state.selectedRooms.size(), state.selectedRooms.size()));
        }
        updateNextButtonState();
    }
}
