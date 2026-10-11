package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;

/**
 * The one way into "Request Additional Amenities" from any screen (Payment Details, Booking Details, the Dashboard's
 * Next Stay): the same picker and API everywhere. The server decides whether the stay may request; a screen that asks
 * first ({@link #bindWithEligibility}) disables the button with the server's plain-language reason.
 */
final class AdditionalAmenitiesEntry {

    private AdditionalAmenitiesEntry() {
    }

    static Intent intent(@NonNull Context context, @NonNull Booking booking) {
        return new Intent(context, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, booking.isDirectBooking())
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, booking.getId());
    }

    /**
     * Shows the button disabled while the server is asked, then enabled, or disabled with {@code reason} filled in. If the
     * answer can't be loaded the button stays usable: the picker has its own error-with-Retry state.
     */
    static void bindWithEligibility(@NonNull Context context, @NonNull View button, @NonNull TextView reason, @NonNull Booking booking,
                                    @NonNull Runnable onClick) {
        button.setVisibility(View.VISIBLE);
        button.setEnabled(false);
        reason.setVisibility(View.GONE);
        button.setOnClickListener(v -> onClick.run());
        RoomRepository.getInstance(context).fetchAdditionalAmenities(booking.isDirectBooking(), booking.getId(),
                new RoomRepository.RepositoryCallback<AdditionalAmenityInfoDto>() {
                    @Override
                    public void onSuccess(AdditionalAmenityInfoDto info) {
                        button.setEnabled(info.can_request);
                        if (!info.can_request) {
                            reason.setText(info.reason != null ? info.reason : context.getString(R.string.additional_amenities_not_available));
                            reason.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        button.setEnabled(true);
                    }
                });
    }
}
