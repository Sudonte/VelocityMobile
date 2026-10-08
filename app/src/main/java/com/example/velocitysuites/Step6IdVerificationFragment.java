package com.example.velocitysuites;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 6: ID Verification for Discount. Lists every discount the System
 * Administrator currently offers (loaded from GET /discounts on every visit -
 * nothing hardcoded, so a discount the admin just created or activated shows
 * up the next time the guest opens this step; the server already returns
 * active discounts only and {@link Discount#activeOnly} re-checks). Tapping a
 * row opens that discount's full details with a "Select this discount"
 * button; "No discount" is always available, so the guest can continue even
 * when the list is empty or failed to load. Choosing a discount reveals the ID
 * upload, which is then required. In edit mode the previously chosen
 * discount and the ID already on file are pre-selected, and stay exactly as
 * they were unless the guest changes them.
 */
public class Step6IdVerificationFragment extends WizardStepFragment {

    private View rowNoDiscount;
    private View layoutLoading;
    private View layoutError;
    private TextView tvEmpty;
    private LinearLayout layoutList;
    private View layoutIdUpload;
    private TextView tvIdUploadStatus;
    private ImageView ivIdPreview;
    private MaterialButton btnUploadId;
    private MaterialButton btnRemoveId;

    private final List<Discount> loaded = new ArrayList<>();
    private boolean loading;
    private boolean loadFailed;
    private int loadGeneration;

    private ActivityResultLauncher<String> idPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        idPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            BookingWizardState state = getState();
            state.idCardImageUri = uri;
            state.removeIdCard = false;
            refreshUploadSection();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step6_id_verification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rowNoDiscount = view.findViewById(R.id.rowNoDiscount);
        layoutLoading = view.findViewById(R.id.layoutDiscountLoading);
        layoutError = view.findViewById(R.id.layoutDiscountError);
        tvEmpty = view.findViewById(R.id.tvDiscountEmpty);
        layoutList = view.findViewById(R.id.layoutDiscountList);
        layoutIdUpload = view.findViewById(R.id.layoutIdUpload);
        tvIdUploadStatus = view.findViewById(R.id.tvIdUploadStatus);
        ivIdPreview = view.findViewById(R.id.ivIdPreview);
        btnUploadId = view.findViewById(R.id.btnUploadId);
        btnRemoveId = view.findViewById(R.id.btnRemoveId);

        ((TextView) rowNoDiscount.findViewById(R.id.tvDiscountName)).setText(R.string.discount_none_title);
        ((TextView) rowNoDiscount.findViewById(R.id.tvDiscountShortDesc)).setText(R.string.discount_none_desc);
        rowNoDiscount.findViewById(R.id.tvDiscountValue).setVisibility(View.GONE);
        rowNoDiscount.setOnClickListener(v -> {
            getState().setDiscount(null);
            // Removing the discount also removes the stored ID in edit mode (a
            // discount-less reservation has no use for it) - applied only on a successful save.
            if (getState().idCardOnFile) getState().removeIdCard = true;
            renderList();
            refreshUploadSection();
        });

        view.findViewById(R.id.btnDiscountRetry).setOnClickListener(v -> loadDiscounts());
        btnUploadId.setOnClickListener(v -> idPickerLauncher.launch("image/*"));
        btnRemoveId.setOnClickListener(v -> {
            BookingWizardState state = getState();
            state.idCardImageUri = null;
            if (state.idCardOnFile) state.removeIdCard = true;
            refreshUploadSection();
        });

        renderList();
        refreshUploadSection();
        loadDiscounts();
    }

    @Override
    public void onDestroyView() {
        loadGeneration++; // drop any in-flight response for the old view
        super.onDestroyView();
    }

    private void loadDiscounts() {
        final int generation = ++loadGeneration;
        loadFailed = false;
        loading = true;
        renderList();
        RoomRepository.getInstance(requireContext()).refreshDiscounts(new RoomRepository.RepositoryCallback<List<Discount>>() {
            @Override
            public void onSuccess(List<Discount> result) {
                if (!isAdded() || getView() == null || generation != loadGeneration) return;
                loading = false;
                loaded.clear();
                loaded.addAll(Discount.activeOnly(result));
                adoptLegacySelection();
                renderList();
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || getView() == null || generation != loadGeneration) return;
                loading = false;
                loaded.clear();
                loadFailed = true;
                renderList();
            }
        });
    }

    /**
     * A reservation made before discounts had ids only remembers the discount's NAME. Once the
     * list is loaded, bind that selection to the real discount of the same name so its id is sent
     * (and it shows as selected) - without touching the guest's choice or the verification state.
     */
    private void adoptLegacySelection() {
        Discount selected = getState().discount;
        if (selected == null || selected.getId() != null) return;
        for (Discount d : loaded) {
            if (d.getName().equals(selected.getName())) {
                getState().discount = d;
                return;
            }
        }
    }

    private void showState(boolean loading, boolean error, boolean empty) {
        layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        layoutError.setVisibility(error ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    /** Rebuilds the discount rows and the loading/error/empty states from {@link #loaded} and the current selection. */
    private void renderList() {
        BookingWizardState state = getState();
        layoutList.removeAllViews();

        Discount selected = state.discount;
        markSelected(rowNoDiscount, selected == null);

        boolean selectedListed = false;
        for (Discount d : loaded) {
            boolean isSelected = selected != null && d.getId().equals(selected.getId());
            selectedListed |= isSelected;
            layoutList.addView(buildRow(d, isSelected, false));
        }
        // Edit mode: the discount chosen earlier may have been deactivated since. It stays selected
        // (unchanged data is never silently dropped) but is flagged.
        if (selected != null && !selectedListed && !loading) {
            layoutList.addView(buildRow(selected, true, true), 0);
        }

        if (loading) {
            showState(true, false, false);
        } else if (loadFailed) {
            showState(false, true, false);
        } else {
            showState(false, false, loaded.isEmpty());
        }
    }

    private View buildRow(Discount d, boolean isSelected, boolean unavailable) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_discount_option, layoutList, false);
        ((TextView) row.findViewById(R.id.tvDiscountName)).setText(d.getName());
        TextView desc = row.findViewById(R.id.tvDiscountShortDesc);
        desc.setText(d.getDescription() != null && !d.getDescription().trim().isEmpty() ? d.getDescription().trim() : getString(R.string.discount_no_description));
        TextView value = row.findViewById(R.id.tvDiscountValue);
        value.setText(d.getValueLabel());
        value.setVisibility(d.getDiscountType() == null ? View.GONE : View.VISIBLE);
        TextView note = row.findViewById(R.id.tvDiscountNote);
        note.setVisibility(unavailable ? View.VISIBLE : View.GONE);
        note.setText(R.string.discount_no_longer_available);
        markSelected(row, isSelected);
        row.setOnClickListener(v -> showDetails(d, unavailable));
        return row;
    }

    private void markSelected(View row, boolean selected) {
        row.setBackgroundResource(selected ? R.drawable.bg_flat_box_selected : R.drawable.bg_flat_box);
        row.findViewById(R.id.ivDiscountSelected).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void showDetails(Discount d, boolean unavailable) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_discount_details, null, false);
        ((TextView) content.findViewById(R.id.tvDetailTitle)).setText(d.getName());

        LinearLayout rows = content.findViewById(R.id.layoutDetailRows);
        addDetailRow(rows, getString(R.string.discount_detail_type),
                d.isPercentage() ? getString(R.string.discount_type_percentage) : getString(R.string.discount_type_fixed));
        addDetailRow(rows, getString(R.string.discount_detail_value), d.getValueLabel());
        addDetailRow(rows, getString(R.string.discount_detail_description),
                d.getDescription() != null && !d.getDescription().trim().isEmpty() ? d.getDescription().trim() : getString(R.string.discount_no_description));
        addDetailRow(rows, getString(R.string.discount_detail_status),
                unavailable ? getString(R.string.discount_no_longer_available) : getString(R.string.discount_status_active));
        String added = formatDate(d.getCreatedAt());
        if (added != null) addDetailRow(rows, getString(R.string.discount_detail_added), added);

        content.findViewById(R.id.btnDetailClose).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnDetailBack).setOnClickListener(v -> dialog.dismiss());
        MaterialButton select = content.findViewById(R.id.btnDetailSelect);
        select.setEnabled(!unavailable);
        select.setOnClickListener(v -> {
            BookingWizardState state = getState();
            boolean changed = state.discount == null || !d.getId().equals(state.discount.getId());
            state.setDiscount(d);
            if (changed && state.idCardOnFile) {
                // A different discount needs its own ID to be re-verified: the stored one is
                // kept until the guest picks a new one, so nothing is lost by just browsing.
                state.removeIdCard = false;
            }
            dialog.dismiss();
            renderList();
            refreshUploadSection();
        });
        dialog.setContentView(content);
        dialog.show();
    }

    private void addDetailRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        row.setLayoutParams(lp);

        TextView l = new TextView(requireContext());
        l.setText(label);
        l.setTextSize(12);
        l.setAllCaps(true);
        l.setTextColor(requireContext().getColor(R.color.velocity_text_secondary));
        TextView v = new TextView(requireContext());
        v.setText(value);
        v.setTextSize(15);
        v.setTextColor(requireContext().getColor(R.color.velocity_text_primary));
        v.setTextIsSelectable(true);
        row.addView(l);
        row.addView(v);
        parent.addView(row);
    }

    /** "2026-09-26T03:16:05.000000Z" -> "2026-09-26" (the date part only); null when absent. */
    @Nullable
    static String formatDate(@Nullable String iso) {
        if (iso == null || iso.length() < 10) return null;
        return iso.substring(0, 10);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Shows/hides the ID section for the chosen discount: a new image preview, the "on file" note, replace and remove. */
    private void refreshUploadSection() {
        BookingWizardState state = getState();
        if (state.discount == null) {
            layoutIdUpload.setVisibility(View.GONE);
            return;
        }
        layoutIdUpload.setVisibility(View.VISIBLE);
        boolean hasNew = state.idCardImageUri != null;
        boolean keepsStored = state.idCardOnFile && !state.removeIdCard && !hasNew;

        if (hasNew) {
            applyPreview(state.idCardImageUri);
            tvIdUploadStatus.setText(R.string.id_uploaded_status);
        } else {
            ivIdPreview.setVisibility(View.GONE);
            if (keepsStored) {
                tvIdUploadStatus.setText(R.string.discount_id_on_file);
            } else if (state.idCardOnFile) {
                tvIdUploadStatus.setText(R.string.discount_id_removed);
            } else {
                tvIdUploadStatus.setText(getString(R.string.discount_upload_for_format, state.discount.getName()));
            }
        }
        tvIdUploadStatus.setTextColor(requireContext().getColor(R.color.velocity_text_secondary));
        btnUploadId.setText((hasNew || keepsStored) ? R.string.discount_replace_id : R.string.upload_id_card);
        btnRemoveId.setVisibility((hasNew || keepsStored) ? View.VISIBLE : View.GONE);
    }

    private void applyPreview(Uri uri) {
        ivIdPreview.setVisibility(View.VISIBLE);
        Glide.with(requireContext()).load(uri).into(ivIdPreview);
    }

    @Override
    public String stepTitle() {
        return "ID Verification";
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        if (state.discountNeedsId()) {
            // Inline, next to the field it belongs to.
            tvIdUploadStatus.setText(R.string.error_id_required);
            tvIdUploadStatus.setTextColor(requireContext().getColor(R.color.velocity_red_primary));
            layoutIdUpload.requestFocus();
            return false;
        }
        return true;
    }
}
