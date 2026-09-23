package com.example.velocitysuites;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.AuthResponse;
import com.example.velocitysuites.network.dto.ProfileResponse;
import com.example.velocitysuites.network.dto.ProfileUpdateRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileManagementActivity extends BaseNavigationActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private static final int PERMISSION_REQUEST_CODE = 100;

    // 30-day rolling edit lock - applies to the profile picture and password
    // (still purely device-local/best-effort for those two), and now ALSO to
    // Personal Information / Contact & Address as a genuine rolling 30-day
    // cooldown enforced server-side (Api\ProfileController::update(), backed
    // by guests.profile_last_updated_at). The previous "permanent one-time"
    // design (guests.profile_edit_used) was never actually wired up anywhere
    // in the backend - confirmed via a full-codebase search - so it never
    // enforced anything; replaced outright rather than layered on top of.
    private static final long LOCK_DURATION_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int MIN_REGISTRATION_AGE = 18;
    private static final String KEY_PROFILE_PICTURE_UPDATED_AT = "profilePictureUpdatedAt";
    private static final String KEY_PASSWORD_UPDATED_AT = "passwordUpdatedAt";
    /** Cached mirrors of the server's profile_update block - see ProfileUpdateState.
     *  Only ever used to drive the button/status UI; the server independently
     *  re-validates the same 30-day window on every update() call regardless
     *  of what's cached here, so a stale/tampered local value can never grant
     *  an edit the server wouldn't otherwise allow. */
    private static final String KEY_PROFILE_CAN_UPDATE = "profileCanUpdate";
    private static final String KEY_PROFILE_LAST_UPDATED_AT = "profileLastUpdatedAt";
    private static final String KEY_PROFILE_NEXT_UPDATE_AT = "profileNextUpdateAt";

    private TextView userNameText, userEmailText, firstNameText, middleNameText, lastNameText, displayEmailText, userGenderText, userDobText, userAgeText, userMobileText;
    private TextView tvAccountCreated;
    private TextView userCountryText, userRegionText, userProvinceText, userCityText, userBarangayText, userStreetText, userZipText;
    private TextView profilePictureLockStatus, changePasswordHintText, profileEditLockStatus;
    private TextView profileUpdateStatusEligibleText, profileUpdateStatusLastUpdatedValue, profileUpdateStatusNextValue;
    private View profileUpdateStatusLockedGroup;
    private MaterialButton editProfileButton;
    private View updateProfilePictureButton, changePasswordButton;
    private ImageView profileImage;
    private SharedPreferences prefs;

    private com.google.android.material.switchmaterial.SwitchMaterial themeModeSwitch;
    private TextView themeModeStatusText;
    private ImageView themeLightIcon, themeDarkIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profilemanagement);
        setupGuestNavigation(R.id.nav_profile_management);

        prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        initializeViews();
        loadUserProfile();
        refreshLockUI();
        animateScreenContent();
        wireProfileActions();
        setupThemeToggle();
        fetchProfileFromServer();
    }

    /**
     * Wires the Appearance section's Light/Dark switch. Reflects the
     * currently-applied mode on load (correct even right after
     * AppCompatDelegate recreated this Activity from a toggle on this same
     * screen), and on change persists + applies the new mode via
     * ThemePreferences - AppCompatDelegate.setDefaultNightMode() recreates
     * every open AppCompatActivity (this one included) to pick up the
     * matching values-night/drawable-night resources, which is what makes
     * this a real, immediate, app-wide switch rather than a visual-only
     * toggle.
     */
    private void setupThemeToggle() {
        themeModeSwitch = findViewById(R.id.themeModeSwitch);
        themeModeStatusText = findViewById(R.id.themeModeStatusText);
        themeLightIcon = findViewById(R.id.themeLightIcon);
        themeDarkIcon = findViewById(R.id.themeDarkIcon);
        if (themeModeSwitch == null) return;

        boolean isDark = ThemePreferences.isDarkMode(this);
        themeModeSwitch.setChecked(isDark);
        updateThemeToggleUi(isDark);

        themeModeSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            updateThemeToggleUi(checked);
            ThemePreferences.setMode(this, checked ? ThemePreferences.MODE_DARK : ThemePreferences.MODE_LIGHT);
        });
    }

    /** Updates the status caption and light/dark icon emphasis to match the given state. */
    private void updateThemeToggleUi(boolean isDark) {
        if (themeModeStatusText != null) {
            themeModeStatusText.setText(isDark ? R.string.theme_dark_mode_active : R.string.theme_light_mode_active);
        }
        int activeColor = ContextCompat.getColor(this, R.color.velocity_red_primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.velocity_inactive_gray);
        if (themeLightIcon != null) themeLightIcon.setImageTintList(android.content.res.ColorStateList.valueOf(isDark ? inactiveColor : activeColor));
        if (themeDarkIcon != null) themeDarkIcon.setImageTintList(android.content.res.ColorStateList.valueOf(isDark ? activeColor : inactiveColor));
    }

    private void fetchProfileFromServer() {
        ApiClient.getService(this).getProfile().enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                    ProfileResponse body = response.body();
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("userFirstName", body.user.first_name);
                    editor.putString("userLastName", body.user.last_name);
                    editor.putString("userMiddleName", body.user.middle_name != null ? body.user.middle_name : "");
                    editor.putString("userEmail", body.user.email);
                    // Keep the pre-combined "userName" (read by Dashboard, PaymentReceiptActivity,
                    // etc.) in sync with the parts above - this fetch path previously updated
                    // first/middle/last but left userName holding whatever combined name was
                    // cached at last login/manual save, so a name changed through another
                    // channel (e.g. a receptionist-assisted edit) would show stale everywhere
                    // that reads userName until the guest next used this screen's own Save button.
                    editor.putString("userName", TextUtils.isEmpty(body.user.middle_name)
                            ? getString(R.string.full_name_format, body.user.first_name, body.user.last_name)
                            : getString(R.string.full_name_middle_format, body.user.first_name, body.user.middle_name, body.user.last_name));
                    // Server-authoritative account timestamps - never derived from the
                    // device clock, so "Account Created" stays accurate regardless of
                    // the guest's local time/timezone (see TimeUtils).
                    if (body.user.created_at != null) editor.putString("userCreatedAt", body.user.created_at);
                    if (body.user.updated_at != null) editor.putString("userUpdatedAt", body.user.updated_at);
                    if (body.guest != null) {
                        if (body.guest.mobile_number != null) editor.putString("userMobile", body.guest.mobile_number);
                        if (body.guest.gender != null) editor.putString("userGender", body.guest.gender);
                        if (body.guest.date_of_birth != null) editor.putString("userDob", formatDobForDisplay(body.guest.date_of_birth));
                        if (body.guest.age != null) editor.putString("userAge", String.valueOf(body.guest.age));
                        if (body.guest.country != null) editor.putString("userCountry", body.guest.country);
                        if (body.guest.region != null) editor.putString("userRegion", body.guest.region);
                        if (body.guest.province != null) editor.putString("userProvince", body.guest.province);
                        if (body.guest.city != null) editor.putString("userCity", body.guest.city);
                        if (body.guest.barangay != null) editor.putString("userBarangay", body.guest.barangay);
                        if (body.guest.street != null) editor.putString("userStreet", body.guest.street);
                        if (body.guest.zip_code != null) editor.putString("userZip", body.guest.zip_code);
                        editor.putString("profilePictureUrl", body.guest.profile_picture_url);
                    }
                    cacheProfileUpdateState(editor, body.profile_update);
                    editor.apply();
                    loadUserProfile();
                    refreshHeader();
                    refreshLockUI();
                }
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                // Keep showing the cached prefs values from the last login/update.
            }
        });
    }

    /**
     * The API sends the birth date as yyyy-MM-dd; the profile screen (and the
     * edit dialog's cached value) show it as a readable date. Unparseable
     * values are shown as-is rather than dropped.
     */
    private String formatDobForDisplay(String apiDate) {
        try {
            java.text.SimpleDateFormat apiFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US);
            java.util.Date parsed = apiFormat.parse(apiDate);
            return parsed != null ? displayFormat.format(parsed) : apiDate;
        } catch (java.text.ParseException e) {
            return apiDate;
        }
    }

    /**
     * Opens the DOB picker for the edit-profile dialog, always capped at today so a future
     * date can never be selected (mirrors RegistrationActivity.openDobPicker() exactly -
     * Age is derived from Date of Birth only, never the other way around). Reopens at the
     * previously chosen date when one exists; otherwise defaults to today.
     */
    private void openDobPicker(TextInputEditText editAge, TextInputEditText editDob) {
        Calendar initial = Calendar.getInstance();
        String existingDob = editDob.getText() != null ? editDob.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(existingDob)) {
            try {
                java.util.Date parsed = new SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(existingDob);
                if (parsed != null) initial.setTime(parsed);
            } catch (ParseException ignored) {
            }
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            editDob.setText(String.format(Locale.US, "%02d/%02d/%d", month + 1, dayOfMonth, year));
            editAge.setText(String.valueOf(calculateAge(year, month + 1, dayOfMonth)));
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private Integer parseValidAge(String value) {
        if (TextUtils.isEmpty(value)) return null;
        try {
            int age = Integer.parseInt(value.trim());
            return (age > 0 && age < 120) ? age : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int calculateAge(int birthYear, int birthMonth, int birthDay) {
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - birthYear;
        int todayMonth = today.get(Calendar.MONTH) + 1;
        int todayDay = today.get(Calendar.DAY_OF_MONTH);
        if (todayMonth < birthMonth || (todayMonth == birthMonth && todayDay < birthDay)) {
            age--;
        }
        return age;
    }

    private String mapGender(String displayGender) {
        if (displayGender == null) return null;
        switch (displayGender) {
            case "Male": return "male";
            case "Female": return "female";
            default: return null;
        }
    }

    /** Inverse of mapGender() - the API/prefs value is always lowercase ("male"/"female"),
     *  but every gender dropdown and display label in this app uses "Male"/"Female". */
    private String displayGender(String storedGender) {
        if (storedGender == null) return "";
        switch (storedGender.toLowerCase(Locale.ROOT)) {
            case "male": return "Male";
            case "female": return "Female";
            default: return storedGender;
        }
    }

    /** Converts the editDob text ("MM/dd/yyyy") into the API's expected "YYYY-MM-DD". */
    private String toApiDate(String mmDdYyyy) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return output.format(input.parse(mmDdYyyy));
        } catch (ParseException | NullPointerException e) {
            return mmDdYyyy;
        }
    }

    private void initializeViews() {
        userNameText = findViewById(R.id.userNameText);
        userEmailText = findViewById(R.id.userEmailText);
        firstNameText = findViewById(R.id.firstNameText);
        middleNameText = findViewById(R.id.middleNameText);
        lastNameText = findViewById(R.id.lastNameText);
        displayEmailText = findViewById(R.id.displayEmailText);
        userGenderText = findViewById(R.id.userGenderText);
        userDobText = findViewById(R.id.userDobText);
        userAgeText = findViewById(R.id.userAgeText);
        userMobileText = findViewById(R.id.userMobileText);
        userCountryText = findViewById(R.id.userCountryText);
        userRegionText = findViewById(R.id.userRegionText);
        userProvinceText = findViewById(R.id.userProvinceText);
        userCityText = findViewById(R.id.userCityText);
        userBarangayText = findViewById(R.id.userBarangayText);
        userStreetText = findViewById(R.id.userStreetText);
        userZipText = findViewById(R.id.userZipText);
        profileImage = findViewById(R.id.profileImage);
        tvAccountCreated = findViewById(R.id.tvAccountCreated);
        profilePictureLockStatus = findViewById(R.id.profilePictureLockStatus);
        changePasswordHintText = findViewById(R.id.changePasswordHintText);
        profileEditLockStatus = findViewById(R.id.profileEditLockStatus);
        profileUpdateStatusEligibleText = findViewById(R.id.profileUpdateStatusEligibleText);
        profileUpdateStatusLockedGroup = findViewById(R.id.profileUpdateStatusLockedGroup);
        profileUpdateStatusLastUpdatedValue = findViewById(R.id.profileUpdateStatusLastUpdatedValue);
        profileUpdateStatusNextValue = findViewById(R.id.profileUpdateStatusNextValue);
    }

    /** Client-side 30-day lock check: true while the section was updated less than 30 days ago. */
    private boolean isLocked(String key) {
        long last = prefs.getLong(key, 0);
        return last != 0 && (System.currentTimeMillis() - last) < LOCK_DURATION_MS;
    }

    /** Days left until the section unlocks again; 0 once it's unlocked. */
    private long remainingDays(String key) {
        long remainingMs = LOCK_DURATION_MS - (System.currentTimeMillis() - prefs.getLong(key, 0));
        if (remainingMs <= 0) return 0;
        return Math.max(1, (long) Math.ceil(remainingMs / (24.0 * 60 * 60 * 1000)));
    }

    private void markUpdated(String key) {
        prefs.edit().putLong(key, System.currentTimeMillis()).apply();
    }

    /**
     * Caches the server's Personal Information / Contact & Address cooldown
     * state (see ProfileUpdateState) so the UI can show it immediately on
     * next load without waiting on a fresh fetch. Purely a display cache -
     * Api\ProfileController::update() independently re-validates the same
     * 30-day window server-side on every call regardless of what's cached
     * here, so this can never be used to grant an edit the server wouldn't
     * otherwise allow. Does not call apply()/commit() itself - the caller
     * batches this into its own SharedPreferences.Editor transaction.
     */
    private void cacheProfileUpdateState(SharedPreferences.Editor editor, com.example.velocitysuites.network.dto.ProfileUpdateState state) {
        if (state == null) return;
        editor.putBoolean(KEY_PROFILE_CAN_UPDATE, state.can_update);
        editor.putString(KEY_PROFILE_LAST_UPDATED_AT, state.last_updated_at);
        editor.putString(KEY_PROFILE_NEXT_UPDATE_AT, state.next_update_at);
    }

    private void showLockedMessage(int messageRes, String key) {
        Toast.makeText(this, getString(messageRes) + " " + getString(R.string.days_remaining_format, remainingDays(key)), Toast.LENGTH_LONG).show();
    }

    /** Dims each section's edit control and shows/hides its lock caption based on its lock state. */
    private void refreshLockUI() {
        applySectionLock(KEY_PROFILE_PICTURE_UPDATED_AT, profilePictureLockStatus, updateProfilePictureButton);

        applyProfileCooldownLock();

        if (changePasswordHintText != null) {
            if (isLocked(KEY_PASSWORD_UPDATED_AT)) {
                changePasswordHintText.setText(getString(R.string.days_remaining_format, remainingDays(KEY_PASSWORD_UPDATED_AT)));
            } else {
                changePasswordHintText.setText(R.string.change_password_hint);
            }
        }
        if (changePasswordButton != null) {
            changePasswordButton.setAlpha(isLocked(KEY_PASSWORD_UPDATED_AT) ? 0.45f : 1f);
        }
    }

    /** 30-day rolling lock (profile picture) - still time-based, unlike the permanent one-time sections below. */
    private void applySectionLock(String key, TextView statusText, View actionButton) {
        boolean locked = isLocked(key);
        if (statusText != null) {
            if (locked) {
                statusText.setText(getString(R.string.days_remaining_format, remainingDays(key)));
                statusText.setVisibility(View.VISIBLE);
            } else {
                statusText.setVisibility(View.GONE);
            }
        }
        if (actionButton != null) {
            actionButton.setAlpha(locked ? 0.45f : 1f);
        }
    }

    /**
     * 30-day Personal Information / Contact & Address cooldown gate - a
     * rolling window, not a permanent one-time lock, so the button always
     * reads "Edit Profile" (just disabled while locked) rather than being
     * permanently relabeled. Shows the exact last-updated/next-eligible
     * dates rather than a vague "try again later" (see
     * R.string.profile_edit_locked_status). Viewing Personal Information/
     * Contact & Address data is never affected either way (see
     * loadUserProfile()) - only the Edit action itself is gated.
     */
    private void applyProfileCooldownLock() {
        if (editProfileButton == null) return;
        // Defaults to eligible until the server says otherwise - never guess
        // "locked" from a missing/stale local cache (e.g. a fresh install
        // before the first fetchProfileFromServer() response lands).
        boolean canUpdate = prefs.getBoolean(KEY_PROFILE_CAN_UPDATE, true);
        editProfileButton.setEnabled(canUpdate);
        editProfileButton.setAlpha(canUpdate ? 1f : 0.6f);
        editProfileButton.setText(R.string.edit_profile);
        editProfileButton.setIconResource(R.drawable.ic_edit);

        String lastUpdatedAt = prefs.getString(KEY_PROFILE_LAST_UPDATED_AT, null);
        String nextUpdateAt = prefs.getString(KEY_PROFILE_NEXT_UPDATE_AT, null);
        boolean showLockedDetail = !canUpdate && lastUpdatedAt != null && nextUpdateAt != null;

        if (profileEditLockStatus != null) {
            if (showLockedDetail) {
                profileEditLockStatus.setText(getString(R.string.profile_edit_locked_status,
                        TimeUtils.formatDate(lastUpdatedAt), TimeUtils.formatDate(nextUpdateAt)));
                profileEditLockStatus.setVisibility(View.VISIBLE);
            } else {
                profileEditLockStatus.setVisibility(View.GONE);
            }
        }

        // Dedicated Profile Update Status card - same underlying state as the
        // caption above, just given its own always-visible section (see
        // task spec section 9) instead of only appearing once locked.
        if (profileUpdateStatusEligibleText != null && profileUpdateStatusLockedGroup != null) {
            if (showLockedDetail) {
                profileUpdateStatusEligibleText.setVisibility(View.GONE);
                profileUpdateStatusLockedGroup.setVisibility(View.VISIBLE);
                if (profileUpdateStatusLastUpdatedValue != null) {
                    profileUpdateStatusLastUpdatedValue.setText(TimeUtils.formatDate(lastUpdatedAt));
                }
                if (profileUpdateStatusNextValue != null) {
                    profileUpdateStatusNextValue.setText(TimeUtils.formatDate(nextUpdateAt));
                }
            } else {
                profileUpdateStatusLockedGroup.setVisibility(View.GONE);
                profileUpdateStatusEligibleText.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadUserProfile() {
        // No hardcoded person here - if these are ever empty, that means
        // the real profile hasn't loaded yet (fetchProfileFromServer is
        // still in flight), not that we should show a fake account.
        String first = prefs.getString("userFirstName", "");
        String middle = prefs.getString("userMiddleName", "");
        String last = prefs.getString("userLastName", "");
        String email = prefs.getString("userEmail", "");
        String mobile = prefs.getString("userMobile", "");
        String gender = prefs.getString("userGender", "");
        String dob = prefs.getString("userDob", "");
        String age = prefs.getString("userAge", "");
        // Defaults to a placeholder address until the guest sets their own
        // via Edit - keeps the Address card from reading as "Not Available"
        // on a freshly registered account.
        String country = prefs.getString("userCountry", "Philippines");
        String region = prefs.getString("userRegion", "SOCCSKSARGEN");
        String province = prefs.getString("userProvince", "South Cotabato");
        String city = prefs.getString("userCity", "Surallah");
        String barangay = prefs.getString("userBarangay", "Brgy. Libertad");
        String street = prefs.getString("userStreet", "Magno Subdivision");
        String zip = prefs.getString("userZip", "9512");
        String profilePictureUrl = SessionManager.getProfilePictureUrl(this);

        if (userNameText != null) {
            // "Last, First Middle" - this screen's own Profile Summary display only
            // (see R.string.profile_full_name_format doc comment). Does not touch
            // the shared "userName" SharedPreferences cache, which stays in
            // "First Last" order for Dashboard/PaymentReceiptActivity/etc.
            String displayName = TextUtils.isEmpty(middle) ?
                    getString(R.string.profile_full_name_format_no_middle, last, first) :
                    getString(R.string.profile_full_name_format, last, first, middle);
            userNameText.setText(displayName);
        }

        if (userEmailText != null) userEmailText.setText(email);
        bindAccountTimestamps();
        if (firstNameText != null) firstNameText.setText(first);
        // Middle Name is genuinely optional, not "unavailable" data - a plain
        // dash reads better than "N/A" here (see task spec section 22).
        if (middleNameText != null) middleNameText.setText(TextUtils.isEmpty(middle) ? getString(R.string.value_empty_dash) : middle);
        if (lastNameText != null) lastNameText.setText(last);
        if (displayEmailText != null) displayEmailText.setText(email);
        if (userGenderText != null) userGenderText.setText(TextUtils.isEmpty(gender) ? getString(R.string.label_not_available) : displayGender(gender));
        if (userDobText != null) userDobText.setText(TextUtils.isEmpty(dob) ? getString(R.string.label_not_available) : dob);
        if (userAgeText != null) userAgeText.setText(TextUtils.isEmpty(age) ? getString(R.string.label_not_available) : getString(R.string.age_years_format, age));
        if (userMobileText != null) userMobileText.setText(mobile);
        if (userCountryText != null) userCountryText.setText(TextUtils.isEmpty(country) ? getString(R.string.label_not_available) : country);
        if (userRegionText != null) userRegionText.setText(TextUtils.isEmpty(region) ? getString(R.string.label_not_available) : region);
        if (userProvinceText != null) userProvinceText.setText(TextUtils.isEmpty(province) ? getString(R.string.label_not_available) : province);
        if (userCityText != null) userCityText.setText(TextUtils.isEmpty(city) ? getString(R.string.label_not_available) : city);
        if (userBarangayText != null) userBarangayText.setText(TextUtils.isEmpty(barangay) ? getString(R.string.label_not_available) : barangay);
        if (userStreetText != null) userStreetText.setText(TextUtils.isEmpty(street) ? getString(R.string.label_not_available) : street);
        if (userZipText != null) userZipText.setText(TextUtils.isEmpty(zip) ? getString(R.string.label_not_available) : zip);

        if (profileImage != null) {
            if (TextUtils.isEmpty(profilePictureUrl)) {
                // No uploaded picture yet - show the neutral default avatar.
                profileImage.setImageResource(R.drawable.img_profile_placeholder);
            } else {
                Glide.with(this)
                        .load(profilePictureUrl)
                        .circleCrop()
                        .placeholder(R.drawable.img_profile_placeholder)
                        .error(R.drawable.img_profile_placeholder)
                        .into(profileImage);
            }
        }
    }

    /**
     * "Member since <created_at>" (permanent, from the server's users.created_at -
     * never the device clock) with "Last updated <updated_at>" appended once the
     * account has actually been edited since creation. Hidden entirely on an
     * older cached session that predates these fields (fetchProfileFromServer()
     * hasn't populated them yet) rather than showing an empty/placeholder row.
     */
    private void bindAccountTimestamps() {
        if (tvAccountCreated == null) return;
        String createdAtRaw = prefs.getString("userCreatedAt", null);
        if (TextUtils.isEmpty(createdAtRaw)) {
            tvAccountCreated.setVisibility(View.GONE);
            return;
        }
        String createdDisplay = TimeUtils.formatDateTime(createdAtRaw);
        String updatedAtRaw = prefs.getString("userUpdatedAt", null);
        StringBuilder text = new StringBuilder(getString(R.string.member_since_format, createdDisplay));
        if (!TextUtils.isEmpty(updatedAtRaw) && !updatedAtRaw.equals(createdAtRaw)) {
            text.append("  •  ").append(getString(R.string.account_last_updated_format, TimeUtils.formatDateTime(updatedAtRaw)));
        }
        tvAccountCreated.setText(text.toString());
        tvAccountCreated.setVisibility(View.VISIBLE);
    }

    private void wireProfileActions() {
        editProfileButton = findViewById(R.id.editProfileButton);
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                String nextUpdateAt = prefs.getString(KEY_PROFILE_NEXT_UPDATE_AT, null);
                if (!prefs.getBoolean(KEY_PROFILE_CAN_UPDATE, true) && nextUpdateAt != null) {
                    Toast.makeText(this, getString(R.string.profile_edit_locked_message,
                            TimeUtils.formatDate(nextUpdateAt)), Toast.LENGTH_LONG).show();
                } else {
                    performEditProfileWizard();
                }
            });
        }

        if (profileImage != null) {
            profileImage.setOnClickListener(v -> handleProfilePictureTap());
        }

        changePasswordButton = findViewById(R.id.changePasswordButton);
        if (changePasswordButton != null) {
            changePasswordButton.setOnClickListener(v -> {
                if (isLocked(KEY_PASSWORD_UPDATED_AT)) {
                    showLockedMessage(R.string.locked_password, KEY_PASSWORD_UPDATED_AT);
                } else {
                    initiatePasswordReset();
                }
            });
        }

        updateProfilePictureButton = findViewById(R.id.updateProfilePictureButton);
        if (updateProfilePictureButton != null) {
            updateProfilePictureButton.setOnClickListener(v -> handleProfilePictureTap());
        }

        View logoutButton = findViewById(R.id.logoutButton);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.logout_confirm_title)
                    .setMessage(R.string.logout_confirm_message)
                    .setPositiveButton(R.string.logout_confirm_action, (d, w) -> logout())
                    .setNegativeButton(R.string.cancel_label, null)
                    .show());
        }

        View deactivateAccountButton = findViewById(R.id.deactivateAccountButton);
        if (deactivateAccountButton != null) {
            deactivateAccountButton.setOnClickListener(v -> showDeactivateAccountConfirmDialog());
        }
    }

    /**
     * Step 1 of 2 (see task spec sections 5-6) - a plain, non-destructive-sounding
     * confirmation explaining that deactivation is temporary/reversible and what
     * stays intact. Only on confirming here does showDeactivateAccountPasswordDialog()
     * (the actual password-gated submission) appear - a single accidental tap on the
     * card's button can never deactivate the account by itself.
     */
    private void showDeactivateAccountConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.deactivate_confirm_title)
                .setMessage(R.string.deactivate_confirm_message)
                .setPositiveButton(R.string.deactivate_confirm_action, (d, w) -> showDeactivateAccountPasswordDialog())
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    /**
     * Step 2 of 2 - requires the current password as a safety confirmation, same
     * pattern the old delete-account dialog used. Reversible (see
     * Api\ProfileController::deactivateAccount) - unlike the permanent-sounding
     * flow it replaces, so this never claims data will be deleted. On success the
     * guest is fully logged out (the server already revoked every token),
     * matching what happens on a normal logout - see BaseNavigationActivity#logout().
     */
    private void showDeactivateAccountPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_deactivate_account, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextInputEditText passwordField = dialogView.findViewById(R.id.deactivateAccountPassword);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmDeactivateAccount);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelDeactivateAccount);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String password = passwordField.getText() != null ? passwordField.getText().toString() : "";
            if (TextUtils.isEmpty(password)) {
                passwordField.setError(getString(R.string.current_password_error));
                return;
            }

            btnConfirm.setEnabled(false);
            ApiClient.getService(this).deactivateAccount(new com.example.velocitysuites.network.dto.DeactivateAccountRequest(password))
                    .enqueue(new Callback<ApiMessage>() {
                        @Override
                        public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                            btnConfirm.setEnabled(true);
                            if (!response.isSuccessful()) {
                                passwordField.setError(getString(R.string.deactivate_account_password_error));
                                return;
                            }
                            // The account is already deactivated server-side regardless of
                            // whether this screen is still visible - logout() itself is
                            // always safe to call (clears local state, navigates fresh to
                            // LoginActivity), so only the now-pointless dialog/Toast are guarded.
                            dismissSafely(dialog);
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(ProfileManagementActivity.this, R.string.deactivate_account_success, Toast.LENGTH_LONG).show();
                            }
                            logout();
                        }

                        @Override
                        public void onFailure(Call<ApiMessage> call, Throwable t) {
                            btnConfirm.setEnabled(true);
                            Toast.makeText(ProfileManagementActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }

    private void handleProfilePictureTap() {
        if (isLocked(KEY_PROFILE_PICTURE_UPDATED_AT)) {
            showLockedMessage(R.string.locked_profile_picture, KEY_PROFILE_PICTURE_UPDATED_AT);
        } else {
            selectProfilePicture();
        }
    }

    /**
     * The single "Edit Profile" action - one combined 4-step flow (Personal Information ->
     * Contact Information -> Address Information -> Review & Confirm) covering everything the
     * guest can edit, subject to the 30-day rolling cooldown (see guests.profile_last_updated_at).
     * Steps 1-3 only collect and validate input locally; nothing is saved to the server until
     * Confirm Update on the Review step succeeds (see submitCombinedProfile()) - closing the
     * dialog at any point before that, by any means, leaves the cooldown untouched.
     */
    private void performEditProfileWizard() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile_wizard, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        View step1Page = dialogView.findViewById(R.id.step1Page);
        View step2Page = dialogView.findViewById(R.id.step2Page);
        View step3Page = dialogView.findViewById(R.id.step3Page);
        View step4Page = dialogView.findViewById(R.id.step4Page);

        TextView stepText = dialogView.findViewById(R.id.stepText);
        TextView stepTitleText = dialogView.findViewById(R.id.stepTitleText);
        View stepProgressBar = dialogView.findViewById(R.id.stepProgressBar);
        View stepDot1 = dialogView.findViewById(R.id.stepDot1);
        View stepDot2 = dialogView.findViewById(R.id.stepDot2);
        View stepDot3 = dialogView.findViewById(R.id.stepDot3);
        View stepDot4 = dialogView.findViewById(R.id.stepDot4);

        // Step 1: Personal Information
        View ageValidationErrorContainer = dialogView.findViewById(R.id.profileAgeValidationErrorContainer);
        TextInputEditText editFirst = dialogView.findViewById(R.id.editFirstName);
        TextInputEditText editMiddle = dialogView.findViewById(R.id.editMiddleName);
        TextInputEditText editLast = dialogView.findViewById(R.id.editLastName);
        TextInputEditText editAge = dialogView.findViewById(R.id.editAge);
        AutoCompleteTextView editGender = dialogView.findViewById(R.id.editGender);
        TextInputEditText editDob = dialogView.findViewById(R.id.editDob);

        editFirst.setText(prefs.getString("userFirstName", ""));
        editMiddle.setText(prefs.getString("userMiddleName", ""));
        editLast.setText(prefs.getString("userLastName", ""));
        editAge.setText(prefs.getString("userAge", ""));
        editDob.setText(prefs.getString("userDob", ""));
        editGender.setText(displayGender(prefs.getString("userGender", "")), false);

        String[] genders = {"Male", "Female"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genders);
        editGender.setAdapter(genderAdapter);

        // Age is read-only, derived only from Date of Birth (see openDobPicker()) - no
        // manual entry, no reverse "type an age" flow.
        editDob.setOnClickListener(v -> openDobPicker(editAge, editDob));

        // Step 2: Contact Information
        TextInputEditText editEmail = dialogView.findViewById(R.id.editEmail);
        TextInputLayout editMobileLayout = dialogView.findViewById(R.id.editMobileLayout);
        TextInputEditText editMobile = dialogView.findViewById(R.id.editMobile);
        editEmail.setText(prefs.getString("userEmail", ""));
        editMobile.setText(prefs.getString("userMobile", ""));

        // Step 3: Address Information
        AddressHierarchyController addressController = AddressHierarchyController.attach(this, dialogView);
        AddressSelection saved = new AddressSelection();
        saved.country = prefs.getString("userCountry", "");
        saved.region = prefs.getString("userRegion", "");
        saved.province = prefs.getString("userProvince", "");
        saved.city = prefs.getString("userCity", "");
        saved.barangay = prefs.getString("userBarangay", "");
        saved.street = prefs.getString("userStreet", "");
        saved.zip = prefs.getString("userZip", "");
        if (!TextUtils.isEmpty(saved.country)) {
            addressController.populateFrom(saved);
        }
        applyCallingCodeHint(editMobileLayout, saved.country);
        addressController.setOnCountryChangeListener(isPhilippines ->
                applyCallingCodeHint(editMobileLayout, addressController.getSelectedCountry()));

        // Mobile masking (+63 9XX XXX XXXX) only makes sense for the Philippines' fixed-length
        // local format - other countries have too many different lengths/shapes to force into
        // one mask, so their input is left as typed. Reflects the country on file when the
        // dialog opens; PhoneNumberValidator still validates correctly against whatever
        // country ends up selected at Confirm Update regardless of whether the mask applied.
        if ("Philippines".equalsIgnoreCase(saved.country)) {
            editMobile.addTextChangedListener(new TextWatcher() {
                private boolean isUpdating = false;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isUpdating) return;
                    isUpdating = true;

                    String digits = s.toString().replaceAll("[^\\d]", "");

                    if (digits.startsWith("63")) {
                        digits = digits.substring(2);
                    } else if (digits.startsWith("0")) {
                        digits = digits.substring(1);
                    }

                    if (digits.length() > 10) {
                        digits = digits.substring(0, 10);
                    }

                    StringBuilder formatted = new StringBuilder();
                    if (digits.length() > 0) {
                        formatted.append("+63 ");
                        for (int i = 0; i < digits.length(); i++) {
                            if (i == 3 || i == 6) {
                                formatted.append(" ");
                            }
                            formatted.append(digits.charAt(i));
                        }
                    }

                    editMobile.setText(formatted.toString());
                    editMobile.setSelection(formatted.length());
                    isUpdating = false;
                }
            });
        }

        // Live 18+ check for Step 1, mirroring registration's gate.
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelEdit);
        MaterialButton btnDiscard = dialogView.findViewById(R.id.btnDiscardEdit);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveProfile);
        final int[] currentStep = {1};

        if (ageValidationErrorContainer != null) {
            Runnable updateAgeValidationUi = () -> {
                Integer age = parseValidAge(editAge.getText() != null ? editAge.getText().toString().trim() : "");
                boolean underage = age != null && age < MIN_REGISTRATION_AGE;
                ageValidationErrorContainer.setVisibility(underage ? View.VISIBLE : View.GONE);
                if (currentStep[0] == 1) btnSave.setEnabled(!underage);
            };
            editAge.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { updateAgeValidationUi.run(); }
            });
            updateAgeValidationUi.run();
        }

        // Dirty-tracking for the Cancel/Back discard-confirmation (spec: only nag when the
        // guest actually changed something) - attached AFTER every field above was pre-filled,
        // since programmatic setText() also fires TextWatcher.afterTextChanged().
        final boolean[] hasChanges = {false};
        TextWatcher dirtyWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { hasChanges[0] = true; }
        };
        View[] addrFields = {
                dialogView.findViewById(R.id.addrCountryField), dialogView.findViewById(R.id.addrRegionField),
                dialogView.findViewById(R.id.addrProvinceField), dialogView.findViewById(R.id.addrCityField),
                dialogView.findViewById(R.id.addrBarangayField), dialogView.findViewById(R.id.addrStreetField),
                dialogView.findViewById(R.id.addrZipField),
        };
        for (TextView tv : new TextView[]{editFirst, editMiddle, editLast, editDob, editGender, editEmail, editMobile}) {
            tv.addTextChangedListener(dirtyWatcher);
        }
        for (View v : addrFields) {
            if (v instanceof TextView) ((TextView) v).addTextChangedListener(dirtyWatcher);
        }

        Runnable doCancel = () -> {
            if (hasChanges[0]) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.discard_changes_title)
                        .setMessage(R.string.discard_changes_message)
                        .setPositiveButton(R.string.discard_changes_action, (d, w) -> dialog.dismiss())
                        .setNegativeButton(R.string.continue_editing_label, null)
                        .show();
            } else {
                dialog.dismiss();
            }
        };

        final Runnable[] renderStep = new Runnable[1];
        renderStep[0] = () -> {
            int step = currentStep[0];
            step1Page.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
            step2Page.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
            step3Page.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
            step4Page.setVisibility(step == 4 ? View.VISIBLE : View.GONE);

            stepText.setText(getString(R.string.registration_step_format, step, 4));
            int titleRes;
            switch (step) {
                case 2: titleRes = R.string.contact_info_title; break;
                case 3: titleRes = R.string.address_info_step_title; break;
                case 4: titleRes = R.string.review_confirm_step_title; break;
                default: titleRes = R.string.edit_profile_wizard_step1_title; break;
            }
            stepTitleText.setText(titleRes);

            float density = getResources().getDisplayMetrics().density;
            float trackWidth = 120 * density;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) stepProgressBar.getLayoutParams();
            params.width = (int) ((trackWidth / 4) * step);
            stepProgressBar.setLayoutParams(params);
            stepDot1.setBackgroundTintList(getColorStateList(R.color.velocity_red_primary));
            stepDot2.setBackgroundTintList(getColorStateList(step >= 2 ? R.color.velocity_red_primary : R.color.velocity_red_soft));
            stepDot3.setBackgroundTintList(getColorStateList(step >= 3 ? R.color.velocity_red_primary : R.color.velocity_red_soft));
            stepDot4.setBackgroundTintList(getColorStateList(step >= 4 ? R.color.velocity_red_primary : R.color.velocity_red_soft));

            btnDiscard.setVisibility(step == 4 ? View.VISIBLE : View.GONE);

            if (step == 1) {
                btnCancel.setText(R.string.cancel_label);
                btnCancel.setOnClickListener(v -> doCancel.run());
                btnSave.setText(R.string.next);
                btnSave.setOnClickListener(v -> {
                    if (!validateStep1(editFirst, editLast, editAge, editDob, ageValidationErrorContainer)) return;
                    currentStep[0] = 2;
                    renderStep[0].run();
                });
            } else if (step == 2) {
                btnCancel.setText(R.string.back_label);
                btnCancel.setOnClickListener(v -> { currentStep[0] = 1; renderStep[0].run(); });
                btnSave.setText(R.string.next);
                btnSave.setOnClickListener(v -> {
                    if (!validateContactStep(editEmail, editMobile, addressController)) return;
                    currentStep[0] = 3;
                    renderStep[0].run();
                });
            } else if (step == 3) {
                btnCancel.setText(R.string.back_label);
                btnCancel.setOnClickListener(v -> { currentStep[0] = 2; renderStep[0].run(); });
                btnSave.setText(R.string.next);
                btnSave.setOnClickListener(v -> {
                    if (!addressController.validate()) return;
                    populateReviewStep(dialogView, editFirst, editMiddle, editLast, editGender, editDob, editAge,
                            editEmail, editMobile, addressController);
                    currentStep[0] = 4;
                    renderStep[0].run();
                });
            } else {
                btnCancel.setText(R.string.back_label);
                btnCancel.setOnClickListener(v -> { currentStep[0] = 3; renderStep[0].run(); });
                btnDiscard.setText(R.string.cancel_label);
                btnDiscard.setOnClickListener(v -> doCancel.run());
                btnSave.setText(R.string.confirm_update_action);
                btnSave.setOnClickListener(v -> submitCombinedProfile(dialog, btnSave, editFirst, editMiddle, editLast,
                        editGender, editDob, editEmail, editMobile, addressController));
            }
        };
        renderStep[0].run();

        dialog.show();
    }

    private void applyCallingCodeHint(TextInputLayout mobileLayout, String country) {
        if (mobileLayout == null) return;
        mobileLayout.setHelperText(getString(R.string.mobile_calling_code_hint, PhoneNumberValidator.callingCodeHint(country)));
    }

    private boolean validateStep1(TextInputEditText first, TextInputEditText last, TextInputEditText age, TextInputEditText dob, View ageValidationErrorContainer) {
        if (TextUtils.isEmpty(first.getText())) { first.setError(getString(R.string.first_name_required)); first.requestFocus(); return false; }
        if (TextUtils.isEmpty(last.getText())) { last.setError(getString(R.string.last_name_required)); last.requestFocus(); return false; }
        if (TextUtils.isEmpty(dob.getText())) { dob.setError(getString(R.string.dob_required)); dob.requestFocus(); return false; }

        Integer ageValue = parseValidAge(age.getText().toString().trim());
        if (ageValue == null || ageValue < MIN_REGISTRATION_AGE) {
            // Age itself is read-only/derived - the error belongs on the field the
            // guest can actually act on.
            dob.setError(getString(R.string.age_validation_error));
            if (ageValidationErrorContainer != null) ageValidationErrorContainer.setVisibility(View.VISIBLE);
            dob.requestFocus();
            return false;
        }
        return true;
    }

    /**
     * Validates Email then Mobile against whichever country is currently selected/pre-filled
     * in the address cascade (Step 3, already attached/populated by the time Step 2 is
     * reached) - not necessarily the one on file, matching RegistrationActivity's own
     * first-invalid-field convention.
     */
    private boolean validateContactStep(TextInputEditText email, TextInputEditText mobile, AddressHierarchyController addressController) {
        String emailStr = email.getText() != null ? email.getText().toString().trim() : "";
        if (TextUtils.isEmpty(emailStr)) {
            email.setError(getString(R.string.email_required));
            email.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
            email.setError(getString(R.string.valid_email_required));
            email.requestFocus();
            return false;
        }

        String selectedCountry = addressController.getSelectedCountry();
        String mobileStr = mobile.getText() != null ? mobile.getText().toString().trim() : "";
        if (!PhoneNumberValidator.isValid(selectedCountry, mobileStr)) {
            mobile.setError(PhoneNumberValidator.errorMessage(this, selectedCountry));
            mobile.requestFocus();
            return false;
        }
        return true;
    }

    /** Fills the Review & Confirm step from the wizard's current field values (not prefs), so edits made in earlier steps show up correctly before the guest commits. */
    private void populateReviewStep(View dialogView, TextInputEditText editFirst, TextInputEditText editMiddle, TextInputEditText editLast,
                                     AutoCompleteTextView editGender, TextInputEditText editDob, TextInputEditText editAge,
                                     TextInputEditText editEmail, TextInputEditText editMobile, AddressHierarchyController addressController) {
        setReviewText(dialogView, R.id.reviewFirstName, editFirst.getText());
        String middleStr = editMiddle.getText() != null ? editMiddle.getText().toString().trim() : "";
        setReviewText(dialogView, R.id.reviewMiddleName, TextUtils.isEmpty(middleStr) ? getString(R.string.label_not_available) : middleStr);
        setReviewText(dialogView, R.id.reviewLastName, editLast.getText());
        setReviewText(dialogView, R.id.reviewGender, editGender.getText());
        setReviewText(dialogView, R.id.reviewDob, editDob.getText());
        String ageStr = editAge.getText() != null ? editAge.getText().toString().trim() : "";
        setReviewText(dialogView, R.id.reviewAge, TextUtils.isEmpty(ageStr) ? getString(R.string.label_not_available) : getString(R.string.age_years_format, ageStr));
        setReviewText(dialogView, R.id.reviewEmail, editEmail.getText());
        setReviewText(dialogView, R.id.reviewMobile, editMobile.getText());

        AddressSelection selection = addressController.getStructuredValues();
        setReviewText(dialogView, R.id.reviewCountry, selection.country);
        boolean isPhilippines = "Philippines".equalsIgnoreCase(selection.country);
        int addressRowsVisibility = isPhilippines ? View.VISIBLE : View.GONE;
        for (int id : new int[]{R.id.reviewRegionLabel, R.id.reviewRegion, R.id.reviewProvinceLabel, R.id.reviewProvince,
                R.id.reviewCityLabel, R.id.reviewCity, R.id.reviewBarangayLabel, R.id.reviewBarangay,
                R.id.reviewZipLabel, R.id.reviewZip, R.id.reviewStreetLabel, R.id.reviewStreet}) {
            View v = dialogView.findViewById(id);
            if (v != null) v.setVisibility(addressRowsVisibility);
        }
        if (isPhilippines) {
            setReviewText(dialogView, R.id.reviewRegion, selection.region);
            setReviewText(dialogView, R.id.reviewProvince, selection.province);
            setReviewText(dialogView, R.id.reviewCity, selection.city);
            setReviewText(dialogView, R.id.reviewBarangay, selection.barangay);
            setReviewText(dialogView, R.id.reviewZip, selection.zip);
            setReviewText(dialogView, R.id.reviewStreet, selection.street);
        }
    }

    private void setReviewText(View dialogView, int id, CharSequence text) {
        TextView tv = dialogView.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    /**
     * Extracts the server's real validation/rejection message (e.g. "Your profile information
     * has already been updated...") from an error response body, falling back to a generic
     * message when the body doesn't have the expected shape - same pattern LoginActivity
     * uses for its own error responses.
     */
    private String errorMessage(Response<?> response, String fallback) {
        if (response.errorBody() != null) {
            try {
                String body = response.errorBody().string();
                int idx = body.indexOf("\"message\":\"");
                if (idx != -1) {
                    int start = idx + 11;
                    int end = body.indexOf('"', start);
                    if (end != -1) return body.substring(start, end);
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    /**
     * Submits Personal Information, Contact Information, and Address Information together as
     * ONE request/transaction (see Api\ProfileController::update()) - this is the only point
     * in the whole wizard that writes anything to the server. The local profile-cooldown cache
     * (and every displayed field) is only ever updated from the server's own response
     * body here, never assumed from what was submitted, so the UI can never show a "saved"
     * state the server didn't actually confirm.
     */
    private void submitCombinedProfile(AlertDialog dialog, MaterialButton btnSave,
                                        TextInputEditText editFirst, TextInputEditText editMiddle, TextInputEditText editLast,
                                        AutoCompleteTextView editGender, TextInputEditText editDob,
                                        TextInputEditText editEmail, TextInputEditText editMobile, AddressHierarchyController addressController) {
        String firstStr = editFirst.getText().toString().trim();
        String middleStr = editMiddle.getText().toString().trim();
        String lastStr = editLast.getText().toString().trim();
        String genderStr = editGender.getText() != null ? editGender.getText().toString().trim() : "";
        String dobStr = editDob.getText() != null ? editDob.getText().toString().trim() : "";
        String emailStr = editEmail.getText().toString().trim();
        String mobileStr = editMobile.getText().toString().trim();
        AddressSelection selection = addressController.getStructuredValues();

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.first_name = firstStr;
        request.last_name = lastStr;
        request.middle_name = middleStr.isEmpty() ? null : middleStr;
        if (!genderStr.isEmpty()) request.gender = mapGender(genderStr);
        if (!dobStr.isEmpty()) request.date_of_birth = toApiDate(dobStr);
        request.email = emailStr;
        request.mobile_number = mobileStr;
        request.address = addressController.getComposedAddress();
        request.country = selection.country;
        request.region = selection.region;
        request.province = selection.province;
        request.city = selection.city;
        request.barangay = selection.barangay;
        request.street = selection.street;
        request.zip_code = selection.zip;

        btnSave.setEnabled(false);
        ApiClient.getService(this).updateProfile(request).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                btnSave.setEnabled(true);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(ProfileManagementActivity.this,
                            errorMessage(response, getString(R.string.profile_update_failed_generic)), Toast.LENGTH_LONG).show();
                    return;
                }

                ProfileResponse body = response.body();
                SharedPreferences.Editor editor = prefs.edit();
                if (body.user != null) {
                    editor.putString("userFirstName", body.user.first_name);
                    editor.putString("userMiddleName", body.user.middle_name != null ? body.user.middle_name : "");
                    editor.putString("userLastName", body.user.last_name);
                    if (body.user.email != null) editor.putString("userEmail", body.user.email);
                    // Server-authoritative "Last Updated" - reflects this actual save,
                    // not the moment the device happened to render the screen.
                    if (body.user.updated_at != null) editor.putString("userUpdatedAt", body.user.updated_at);
                    String fullName = TextUtils.isEmpty(body.user.middle_name) ?
                            getString(R.string.full_name_format, body.user.first_name, body.user.last_name) :
                            getString(R.string.full_name_middle_format, body.user.first_name, body.user.middle_name, body.user.last_name);
                    editor.putString("userName", fullName);
                }
                if (body.guest != null) {
                    if (body.guest.gender != null) editor.putString("userGender", body.guest.gender);
                    if (body.guest.date_of_birth != null) editor.putString("userDob", formatDobForDisplay(body.guest.date_of_birth));
                    if (body.guest.age != null) editor.putString("userAge", String.valueOf(body.guest.age));
                    if (body.guest.mobile_number != null) editor.putString("userMobile", body.guest.mobile_number);
                    if (body.guest.country != null) editor.putString("userCountry", body.guest.country);
                    if (body.guest.region != null) editor.putString("userRegion", body.guest.region);
                    if (body.guest.province != null) editor.putString("userProvince", body.guest.province);
                    if (body.guest.city != null) editor.putString("userCity", body.guest.city);
                    if (body.guest.barangay != null) editor.putString("userBarangay", body.guest.barangay);
                    if (body.guest.street != null) editor.putString("userStreet", body.guest.street);
                    if (body.guest.zip_code != null) editor.putString("userZip", body.guest.zip_code);
                }
                cacheProfileUpdateState(editor, body.profile_update);
                editor.apply();

                // The real save already happened server-side and the local prefs
                // write above already ran regardless - only the UI refresh below is
                // pointless (and unsafe) once the guest has left this screen.
                if (isFinishing() || isDestroyed()) return;
                loadUserProfile();
                refreshHeader();
                refreshLockUI();
                Toast.makeText(ProfileManagementActivity.this, R.string.profile_updated_full, Toast.LENGTH_SHORT).show();
                dismissSafely(dialog);
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                btnSave.setEnabled(true);
                Toast.makeText(ProfileManagementActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * OTP-gated instead of current-password-gated: sends a verification
     * code to the guest's registered email (reusing the same
     * forgotPassword/resetPassword endpoints the Login screen's Forgot
     * Password flow already uses - no new backend endpoint needed) and
     * only shows the new-password dialog once that send succeeds.
     */
    private void initiatePasswordReset() {
        String email = prefs.getString("userEmail", "");
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Couldn't determine your account email.", Toast.LENGTH_LONG).show();
            return;
        }

        if (changePasswordButton != null) changePasswordButton.setEnabled(false);
        ApiClient.getService(this).forgotPassword(new com.example.velocitysuites.network.dto.EmailRequest(email))
                .enqueue(new Callback<ApiMessage>() {
                    @Override
                    public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                        if (changePasswordButton != null) changePasswordButton.setEnabled(true);
                        if (!response.isSuccessful()) {
                            Toast.makeText(ProfileManagementActivity.this, R.string.reset_code_send_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(ProfileManagementActivity.this, getString(R.string.otp_sent_to, email), Toast.LENGTH_SHORT).show();
                        showChangePasswordOtpDialog(email);
                    }

                    @Override
                    public void onFailure(Call<ApiMessage> call, Throwable t) {
                        if (changePasswordButton != null) changePasswordButton.setEnabled(true);
                        Toast.makeText(ProfileManagementActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showChangePasswordOtpDialog(String email) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password_otp, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextInputEditText otpField = dialogView.findViewById(R.id.changePasswordOtp);
        TextInputEditText newPass = dialogView.findViewById(R.id.changePasswordNew);
        TextInputEditText confirmPass = dialogView.findViewById(R.id.changePasswordConfirmNew);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmChangePasswordOtp);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelChangePasswordOtp);
        View resendLink = dialogView.findViewById(R.id.resendChangePasswordOtpLink);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        resendLink.setOnClickListener(v -> ApiClient.getService(this).forgotPassword(new com.example.velocitysuites.network.dto.EmailRequest(email))
                .enqueue(new Callback<ApiMessage>() {
                    @Override
                    public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                        Toast.makeText(ProfileManagementActivity.this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<ApiMessage> call, Throwable t) {
                        Toast.makeText(ProfileManagementActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                    }
                }));

        btnConfirm.setOnClickListener(v -> {
            String otp = otpField.getText() != null ? otpField.getText().toString().trim() : "";
            String nPass = newPass.getText() != null ? newPass.getText().toString() : "";
            String cPass = confirmPass.getText() != null ? confirmPass.getText().toString() : "";

            if (otp.length() != 6) {
                otpField.setError(getString(R.string.invalid_otp));
                return;
            }
            if (nPass.length() < 8) {
                newPass.setError(getString(R.string.password_min_length));
                return;
            }
            if (!nPass.equals(cPass)) {
                confirmPass.setError(getString(R.string.passwords_do_not_match));
                return;
            }

            btnConfirm.setEnabled(false);
            ApiClient.getService(this).resetPassword(new com.example.velocitysuites.network.dto.ResetPasswordRequest(email, otp, nPass, cPass))
                    .enqueue(new Callback<AuthResponse>() {
                        @Override
                        public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                            btnConfirm.setEnabled(true);
                            if (!response.isSuccessful()) {
                                otpField.setError(getString(R.string.invalid_otp));
                                Toast.makeText(ProfileManagementActivity.this, R.string.otp_invalid_or_expired, Toast.LENGTH_LONG).show();
                                return;
                            }
                            // The password is already changed server-side regardless of
                            // whether this screen is still visible - only the now-pointless
                            // dialog/Toast are guarded; markUpdated()/logout() below still
                            // run unconditionally (real local state that must stay correct).
                            dismissSafely(dialog);
                            markUpdated(KEY_PASSWORD_UPDATED_AT);
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(ProfileManagementActivity.this, R.string.password_changed_success, Toast.LENGTH_LONG).show();
                            }
                            // resetPassword() revokes every prior token server-side
                            // and issues a new one (see AuthController::resetPassword),
                            // but this in-app change-password flow deliberately still
                            // logs the guest out rather than silently swapping the
                            // session's token, so they consciously log back in with
                            // the new password.
                            logout();
                        }

                        @Override
                        public void onFailure(Call<AuthResponse> call, Throwable t) {
                            btnConfirm.setEnabled(true);
                            Toast.makeText(ProfileManagementActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }

    private void selectProfilePicture() {
        String[] options = {getString(R.string.take_photo), getString(R.string.choose_from_gallery)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_image_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkPermissionAndCamera();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    private void checkPermissionAndCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                        confirmAndUploadProfilePicture(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == CAPTURE_IMAGE_REQUEST) {
                Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                if (imageBitmap != null) {
                    confirmAndUploadProfilePicture(imageBitmap);
                }
            }
        }
    }

    private void confirmAndUploadProfilePicture(Bitmap bitmap) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_update_profile_picture)
                .setPositiveButton(R.string.confirm_dialog_positive, (d, w) -> saveAndSetProfileImage(bitmap))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void saveAndSetProfileImage(Bitmap bitmap) {
        // Show it immediately for responsiveness, but the picture isn't
        // "saved" until the upload below succeeds - the server, not this
        // device, is the source of truth (so it follows the account
        // across reinstalls/devices instead of living only in local prefs).
        if (profileImage != null) profileImage.setImageBitmap(bitmap);

        File tempFile;
        try {
            tempFile = File.createTempFile("profile_", ".jpg", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to prepare image for upload", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody fileBody = RequestBody.create(tempFile, MediaType.parse("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("profile_picture", tempFile.getName(), fileBody);

        ApiClient.getService(this).updateProfilePicture(part).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                tempFile.delete();
                if (!response.isSuccessful() || response.body() == null || response.body().guest == null) {
                    Toast.makeText(ProfileManagementActivity.this, "Failed to upload photo. Please try again.", Toast.LENGTH_SHORT).show();
                    return;
                }

                SessionManager.setProfilePictureUrl(ProfileManagementActivity.this, response.body().guest.profile_picture_url);
                refreshHeader();
                markUpdated(KEY_PROFILE_PICTURE_UPDATED_AT);
                refreshLockUI();
                Toast.makeText(ProfileManagementActivity.this, R.string.profile_picture_updated, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                tempFile.delete();
                Toast.makeText(ProfileManagementActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
