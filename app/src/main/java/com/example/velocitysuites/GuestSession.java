package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.AuthResponse;

/** Saves the session a successful sign-in (or a completed password reset) returns. One place, so both paths store the same things. */
final class GuestSession {

    private GuestSession() {
    }

    /** Stores the token and the guest's details on this device and returns the guest's full name. */
    @NonNull
    static String save(@NonNull Context context, @NonNull AuthResponse auth) {
        String firstName = auth.user.first_name != null ? auth.user.first_name : "";
        String lastName = auth.user.last_name != null ? auth.user.last_name : "";
        String fullName = auth.user.full_name != null ? auth.user.full_name : (firstName + " " + lastName).trim();
        String mobile = auth.user.guest != null ? auth.user.guest.mobile_number : null;
        String gender = auth.user.guest != null ? auth.user.guest.gender : null;
        String dob = auth.user.guest != null ? auth.user.guest.date_of_birth : null;
        String pictureUrl = auth.user.guest != null ? auth.user.guest.profile_picture_url : null;

        SessionManager.saveSession(context, auth.token, auth.user.id, firstName, lastName, auth.user.middle_name,
                fullName, auth.user.email, mobile, gender, dob, pictureUrl);
        if (auth.user.guest != null && auth.user.guest.address != null) {
            // Shown in the Guest Information card of the booking/reservation details.
            context.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().putString("userAddress", auth.user.guest.address).apply();
        }
        return fullName;
    }
}
