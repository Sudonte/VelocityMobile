package com.example.velocitysuites;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;

/**
 * Step 6: "Do you have a Senior Citizen or PWD ID?" - the Upload ID Card
 * section only appears (and is required) when Senior Citizen or PWD is
 * chosen. Ported from
 * BookingAndReservationActivity#setupIdentificationLogic()/idPickerLauncher.
 */
public class Step6IdVerificationFragment extends WizardStepFragment {

    private ChipGroup cgIdType;
    private MaterialButton btnUploadId;
    private MaterialCardView cardIdPreview;
    private ImageView ivIdPreview;
    private TextView tvIdUploadStatus;

    private ActivityResultLauncher<String> idPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        idPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            getState().idCardImageUri = uri;
            cardIdPreview.setVisibility(View.VISIBLE);
            ivIdPreview.setImageURI(uri);
            tvIdUploadStatus.setVisibility(View.VISIBLE);
            tvIdUploadStatus.setText(R.string.id_uploaded_status);
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

        cgIdType = view.findViewById(R.id.cgIdType);
        btnUploadId = view.findViewById(R.id.btnUploadId);
        cardIdPreview = view.findViewById(R.id.cardIdPreview);
        ivIdPreview = view.findViewById(R.id.ivIdPreview);
        tvIdUploadStatus = view.findViewById(R.id.tvIdUploadStatus);

        BookingWizardState state = getState();
        if ("Senior Citizen".equals(state.idCardType)) {
            view.findViewById(R.id.chipSenior).performClick();
        } else if ("PWD".equals(state.idCardType)) {
            view.findViewById(R.id.chipPwd).performClick();
        }
        if (state.idCardImageUri != null) {
            applyPreview(state.idCardImageUri);
        }

        cgIdType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || checkedIds.get(0) == R.id.chipNone) {
                setIdType("None");
                return;
            }
            int checkedId = checkedIds.get(0);
            setIdType(checkedId == R.id.chipSenior ? "Senior Citizen" : "PWD");
        });

        btnUploadId.setOnClickListener(v -> idPickerLauncher.launch("image/*"));
    }

    private void setIdType(String type) {
        BookingWizardState state = getState();
        state.idCardType = type;
        boolean needsId = !"None".equals(type);
        btnUploadId.setVisibility(needsId ? View.VISIBLE : View.GONE);
        if (!needsId) {
            state.idCardImageUri = null;
            cardIdPreview.setVisibility(View.GONE);
            tvIdUploadStatus.setVisibility(View.GONE);
        } else if (state.idCardImageUri != null) {
            applyPreview(state.idCardImageUri);
        } else {
            tvIdUploadStatus.setVisibility(View.VISIBLE);
            tvIdUploadStatus.setText(R.string.no_id_uploaded);
        }
    }

    private void applyPreview(Uri uri) {
        cardIdPreview.setVisibility(View.VISIBLE);
        ivIdPreview.setImageURI(uri);
        tvIdUploadStatus.setVisibility(View.VISIBLE);
        tvIdUploadStatus.setText(R.string.id_uploaded_status);
    }

    @Override
    public String stepTitle() {
        return "ID Verification";
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        boolean needsId = !"None".equals(state.idCardType);
        if (needsId && state.idCardImageUri == null) {
            Toast.makeText(requireContext(), R.string.error_id_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
