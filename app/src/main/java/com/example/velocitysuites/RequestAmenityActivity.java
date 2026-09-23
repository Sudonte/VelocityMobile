package com.example.velocitysuites;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.velocitysuites.network.dto.AmenityRequestDto;
import com.example.velocitysuites.network.dto.RequestableAmenityDto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;

/**
 * Post-booking Additional Amenity Request screen: the guest may request
 * more of a Paid/Additional amenity they already selected during this
 * reservation's original booking (see RequestableAmenityDto /
 * Api\AmenityRequestController::requestable()), and view the status of
 * requests already submitted. Requesting anything not originally selected
 * is rejected server-side, not just hidden here - see submitRequest().
 */
public class RequestAmenityActivity extends AppCompatActivity {

    public static final String EXTRA_RESERVATION_ID = "EXTRA_RESERVATION_ID";

    /** Soft UX guard against accidental multi-tap runaway quantities - not a business rule, just sane bounds on the stepper. */
    private static final int MAX_REQUEST_QTY = 20;

    private RoomRepository repository;
    private String reservationId;

    private CircularProgressIndicator progress;
    private View cardRequestable;
    private LinearLayout layoutRequestableContainer;
    private TextView tvEmptyState;
    private View cardHistory;
    private LinearLayout layoutHistoryContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.requestamenity);

        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        repository = RoomRepository.getInstance(this);

        findViewById(R.id.btnRequestAmenityBack).setOnClickListener(v -> finish());

        progress = findViewById(R.id.progressRequestAmenity);
        cardRequestable = findViewById(R.id.cardRequestableAmenities);
        layoutRequestableContainer = findViewById(R.id.layoutRequestableAmenitiesContainer);
        tvEmptyState = findViewById(R.id.tvRequestAmenityEmpty);
        cardHistory = findViewById(R.id.cardRequestHistory);
        layoutHistoryContainer = findViewById(R.id.layoutRequestHistoryContainer);

        if (reservationId == null || reservationId.isEmpty()) {
            Toast.makeText(this, getString(R.string.request_amenity_load_error, "missing reservation"), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadRequestable();
        loadHistory();
    }

    private void loadRequestable() {
        progress.setVisibility(View.VISIBLE);
        repository.fetchRequestableAmenities(reservationId, new RoomRepository.RepositoryCallback<List<RequestableAmenityDto>>() {
            @Override
            public void onSuccess(List<RequestableAmenityDto> items) {
                progress.setVisibility(View.GONE);
                renderRequestable(items);
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                Toast.makeText(RequestAmenityActivity.this, getString(R.string.request_amenity_load_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderRequestable(List<RequestableAmenityDto> items) {
        layoutRequestableContainer.removeAllViews();

        if (items == null || items.isEmpty()) {
            cardRequestable.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        cardRequestable.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < items.size(); i++) {
            RequestableAmenityDto item = items.get(i);
            View row = inflater.inflate(R.layout.item_requestable_amenity, layoutRequestableContainer, false);

            TextView nameLabel = row.findViewById(R.id.tvRequestableAmenityName);
            TextView summaryLabel = row.findViewById(R.id.tvRequestableAmenitySummary);
            TextView qtyLabel = row.findViewById(R.id.tvRequestableAmenityQty);
            MaterialButton btnMinus = row.findViewById(R.id.btnRequestableAmenityMinus);
            MaterialButton btnPlus = row.findViewById(R.id.btnRequestableAmenityPlus);
            MaterialButton btnSubmit = row.findViewById(R.id.btnRequestableAmenitySubmit);

            nameLabel.setText(item.category != null && !item.category.isEmpty()
                    ? item.amenity_name + " — " + item.category
                    : item.amenity_name);
            summaryLabel.setText(getString(R.string.request_amenity_summary_format,
                    item.price, item.original_quantity, item.already_requested_quantity));

            final int[] qty = {1};
            qtyLabel.setText(String.valueOf(qty[0]));

            btnMinus.setOnClickListener(v -> {
                if (qty[0] > 1) {
                    qty[0]--;
                    qtyLabel.setText(String.valueOf(qty[0]));
                }
            });
            btnPlus.setOnClickListener(v -> {
                if (qty[0] < MAX_REQUEST_QTY) {
                    qty[0]++;
                    qtyLabel.setText(String.valueOf(qty[0]));
                }
            });
            btnSubmit.setOnClickListener(v -> submitRequest(item, qty[0], btnSubmit));

            layoutRequestableContainer.addView(row);
        }
    }

    private void submitRequest(RequestableAmenityDto item, int quantity, MaterialButton btnSubmit) {
        btnSubmit.setEnabled(false);
        repository.submitAmenityRequest(reservationId, item.amenity_id, quantity, new RoomRepository.RepositoryCallback<AmenityRequestDto>() {
            @Override
            public void onSuccess(AmenityRequestDto result) {
                btnSubmit.setEnabled(true);
                Toast.makeText(RequestAmenityActivity.this, R.string.request_amenity_submit_success, Toast.LENGTH_LONG).show();
                loadRequestable();
                loadHistory();
            }

            @Override
            public void onError(String message) {
                btnSubmit.setEnabled(true);
                Toast.makeText(RequestAmenityActivity.this, getString(R.string.request_amenity_submit_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadHistory() {
        repository.fetchAmenityRequests(reservationId, new RoomRepository.RepositoryCallback<List<AmenityRequestDto>>() {
            @Override
            public void onSuccess(List<AmenityRequestDto> requests) {
                renderHistory(requests);
            }

            @Override
            public void onError(String message) {
                cardHistory.setVisibility(View.GONE);
            }
        });
    }

    private void renderHistory(List<AmenityRequestDto> requests) {
        layoutHistoryContainer.removeAllViews();

        if (requests == null || requests.isEmpty()) {
            cardHistory.setVisibility(View.GONE);
            return;
        }

        cardHistory.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AmenityRequestDto req : requests) {
            View row = inflater.inflate(R.layout.item_amenity_request_history, layoutHistoryContainer, false);

            TextView nameLabel = row.findViewById(R.id.tvHistoryRowName);
            TextView statusLabel = row.findViewById(R.id.tvHistoryRowStatus);
            TextView metaLabel = row.findViewById(R.id.tvHistoryRowMeta);

            nameLabel.setText(getString(R.string.request_amenity_history_name_format, req.amenity_name, req.quantity));
            String meta = getString(R.string.request_amenity_history_meta_format, req.getSubtotal(), formatRequestDate(req.created_at));
            if (req.category != null && !req.category.isEmpty()) {
                meta = req.category + "  •  " + meta;
            }
            metaLabel.setText(meta);

            applyStatusStyle(statusLabel, req.status);

            layoutHistoryContainer.addView(row);
        }
    }

    /**
     * The backend returns a UTC ISO-8601 timestamp - previously this just
     * substring'd the raw UTC date digits, which both dropped the time of
     * day and could show the wrong calendar day for a request made near
     * midnight Philippine time (e.g. 12:30 AM Sep 9 Manila is still Sep 8
     * in UTC). TimeUtils converts to Asia/Manila properly before formatting.
     */
    private String formatRequestDate(String isoDate) {
        return TimeUtils.formatDateTime(isoDate);
    }

    /** Mirrors the web Request Amenity module's status badge color convention (pending=orange, approved=blue, in_progress=brand red, completed=green, rejected=red-status). */
    private void applyStatusStyle(TextView label, String status) {
        int bgColor;
        int textColor;
        String displayText;
        if (status == null) status = "pending";

        switch (status) {
            case "approved":
                bgColor = R.color.velocity_blue_soft;
                textColor = R.color.velocity_blue_primary;
                displayText = "Approved";
                break;
            case "in_progress":
                bgColor = R.color.velocity_red_soft;
                textColor = R.color.velocity_red_primary;
                displayText = "In Progress";
                break;
            case "completed":
                bgColor = R.color.velocity_green_soft;
                textColor = R.color.velocity_green_dark;
                displayText = "Completed";
                break;
            case "rejected":
                bgColor = R.color.velocity_red_status_soft;
                textColor = R.color.velocity_red_status_dark;
                displayText = "Rejected";
                break;
            case "pending":
            default:
                bgColor = R.color.velocity_orange_soft;
                textColor = R.color.velocity_orange_primary;
                displayText = "Pending";
                break;
        }

        label.setText(displayText);
        label.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, bgColor)));
        label.setTextColor(ContextCompat.getColor(this, textColor));
    }
}
