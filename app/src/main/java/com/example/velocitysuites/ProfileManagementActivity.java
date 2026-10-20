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
import android.view.ViewGroup;
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
import com.example.velocitysuites.network.dto.RequestEmailChangeRequest;
import com.example.velocitysuites.network.dto.ConfirmEmailChangeRequest;
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

    /** onCreate() already fetched the profile, so the very first onResume() must not fetch it a second time. */
    private boolean firstResume = true;

    @Override
    protected void onResume() {
        super.onResume();
        // Back on this screen (from another screen or another app): pick up anything that changed meanwhile - a
        // name or number corrected at the front desk, a profile-update lock that has since lifted.
        if (firstResume) {
            firstResume = false;
        } else {
            fetchProfileFromServer();
        }
    }

    /**
     * The 30s beat (started/stopped with the screen by BaseNavigationActivity): quietly re-read the profile. It only
     * re-applies server values to the read-only page - the edit form is a separate dialog with its own copy, so
     * nothing a guest is typing can be overwritten - and a failure shows nothing. The base class's badge check is kept.
     */
    @Override
    protected void onVisiblePoll() {
        super.onVisiblePoll();
        fetchProfileFromServer();
    }

    private void fetchProfileFromServer() {
        ApiClient.getService(this).getProfile().enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                    // A beat's request can still be in flight when the guest leaves; the page it would repaint (and
                    // the Glide load that starts) must not be touched once this screen is gone. (An expired session
                    // below is still acted on either way.)
                    if (isFinishing() || isDestroyed()) return;
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
                        if (body.guest.address != null) editor.putString("userAddress", body.guest.address);
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
                } else if (response.code() == 401 || response.code() == 419) {
                    // Unlike RoomRepository-backed screens, this Activity calls
                    // ApiClient directly - without this, a dead/expired token here
                    // just left the guest stuck on stale cached data forever, with
                    // no path back to the login screen (see RoomRepository's own
                    // forceSessionExpiredLogout() doc for the full rationale).
                    RoomRepository.forceSessionExpiredLogout(ProfileManagementActivity.this);
                }
                // Any other non-2xx: keep showing the cached prefs values from the
                // last login/update, same as onFailure() below.
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
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
        java.util.Date parsed = parseDob(apiDate);
        return parsed != null ? new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(parsed) : apiDate;
    }

    /**
     * Parses a birth date in any format this app has stored or received: the API's yyyy-MM-dd
     * (possibly with a trailing time/zone part, which is ignored - only the calendar date matters,
     * so no timezone conversion can shift it), the wizard's MM/dd/yyyy, or the profile screen's
     * "MMM dd, yyyy" cache. Returns null when nothing matches.
     */
    static java.util.Date parseDob(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.matches("^\\d{4}-\\d{2}-\\d{2}.*")) v = v.substring(0, 10);
        for (String pattern : new String[]{"yyyy-MM-dd", "MM/dd/yyyy", "MMM dd, yyyy", "MMM d, yyyy"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            try {
                java.util.Date parsed = format.parse(v);
                if (parsed != null) return parsed;
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    /** The saved date of birth as the API's yyyy-MM-dd, or "" when none is saved/parseable. */
    private String savedDobIso() {
        java.util.Date parsed = parseDob(prefs.getString("userDob", ""));
        return parsed != null ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed) : "";
    }

    /** The saved date of birth as the wizard's MM/dd/yyyy field text, or "" when none is saved. */
    private String savedDobForWizard() {
        java.util.Date parsed = parseDob(prefs.getString("userDob", ""));
        return parsed != null ? new SimpleDateFormat("MM/dd/yyyy", Locale.US).format(parsed) : "";
    }

    /**
     * Opens the DOB picker for the edit-profile dialog, capped at yesterday so neither today
     * nor any future date can ever be selected as a Date of Birth (mirrors
     * RegistrationActivity.openDobPicker() exactly - Age is derived from Date of Birth only,
     * never the other way around). Reopens at the previously chosen date when one exists;
     * otherwise anchors on MIN_REGISTRATION_AGE years ago instead of today, so the guest
     * isn't forced to spin the picker back several decades from the current date.
     */
    private void openDobPicker(TextInputEditText editAge, TextInputEditText editDob) {
        Calendar initial = Calendar.getInstance();
        String existingDob = editDob.getText() != null ? editDob.getText().toString().trim() : "";
        java.util.Date existing = parseDob(existingDob);
        if (existing != null) {
            initial.setTime(existing);
        } else {
            initial.add(Calendar.YEAR, -MIN_REGISTRATION_AGE);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            editDob.setText(String.format(Locale.US, "%02d/%02d/%d", month + 1, dayOfMonth, year));
            editAge.setText(String.valueOf(calculateAge(year, month + 1, dayOfMonth)));
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));

        Calendar maxDob = Calendar.getInstance();
        maxDob.add(Calendar.DAY_OF_YEAR, -1);
        datePickerDialog.getDatePicker().setMaxDate(maxDob.getTimeInMillis());
        SafeDialogs.show(datePickerDialog);
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

    /** Converts the editDob text into the API's expected "YYYY-MM-DD"; null if it can't be read (never sends garbage). */
    private String toApiDate(String dobText) {
        java.util.Date parsed = parseDob(dobText);
        return parsed != null ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed) : null;
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
        Toast.makeText(this, getString(messageRes) + " " + getResources().getQuantityString(R.plurals.days_remaining_format, (int) remainingDays(key), remainingDays(key)), Toast.LENGTH_LONG).show();
    }

    /** Dims each section's edit control and shows/hides its lock caption based on its lock state. */
    private void refreshLockUI() {
        applySectionLock(KEY_PROFILE_PICTURE_UPDATED_AT, profilePictureLockStatus, updateProfilePictureButton);

        applyProfileCooldownLock();

        if (changePasswordHintText != null) {
            if (isLocked(KEY_PASSWORD_UPDATED_AT)) {
                changePasswordHintText.setText(getResources().getQuantityString(R.plurals.days_remaining_format, (int) remainingDays(KEY_PASSWORD_UPDATED_AT), remainingDays(KEY_PASSWORD_UPDATED_AT)));
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
                statusText.setText(getResources().getQuantityString(R.plurals.days_remaining_format, (int) remainingDays(key), remainingDays(key)));
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
        bindProfileHeaderDetails(mobile, gender, dob);
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
     * The header's mobile line (the guest ID stands in when there is no number), the profile
     * completeness line ("Profile complete" or what is still missing) and "Last updated: <date>".
     * Completeness looks only at what the guest has actually saved - never at the placeholder
     * address shown on the cards below.
     */
    private void bindProfileHeaderDetails(String mobile, String gender, String dob) {
        TextView mobileView = findViewById(R.id.headerMobileText);
        TextView statusView = findViewById(R.id.headerStatusText);
        TextView updatedView = findViewById(R.id.headerLastUpdatedText);

        if (mobileView != null) {
            long userId = prefs.getLong("userId", -1L);
            if (!TextUtils.isEmpty(mobile)) {
                mobileView.setText(mobile);
                mobileView.setVisibility(View.VISIBLE);
            } else if (userId > 0) {
                mobileView.setText(getString(R.string.profile_header_guest_id_format, userId));
                mobileView.setVisibility(View.VISIBLE);
            } else {
                mobileView.setVisibility(View.GONE);
            }
        }

        if (statusView != null) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (TextUtils.isEmpty(mobile)) missing.add(getString(R.string.pc_profile_missing_mobile));
            if (TextUtils.isEmpty(gender)) missing.add(getString(R.string.pc_profile_missing_gender));
            if (TextUtils.isEmpty(dob)) missing.add(getString(R.string.profile_missing_dob));
            if (TextUtils.isEmpty(prefs.getString("userCity", "")) || TextUtils.isEmpty(prefs.getString("userStreet", ""))) {
                missing.add(getString(R.string.profile_missing_address));
            }
            statusView.setText(missing.isEmpty()
                    ? getString(R.string.profile_status_complete)
                    : getString(R.string.profile_status_incomplete_format, TextUtils.join(", ", missing)));
        }

        if (updatedView != null) {
            String updatedAt = prefs.getString(KEY_PROFILE_LAST_UPDATED_AT, null);
            if (TextUtils.isEmpty(updatedAt)) updatedAt = prefs.getString("userUpdatedAt", null);
            if (TextUtils.isEmpty(updatedAt)) {
                updatedView.setVisibility(View.GONE);
            } else {
                updatedView.setText(getString(R.string.profile_header_last_updated_format, TimeUtils.formatDate(updatedAt)));
                updatedView.setVisibility(View.VISIBLE);
            }
        }
    }

    /** The cover is the Landing screen's background, decoded at roughly screen size instead of full resolution. */
    private void loadProfileCover() {
        ImageView cover = findViewById(R.id.profileCoverImage);
        if (cover == null) return;
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int heightPx = (int) (180 * getResources().getDisplayMetrics().density);
        Glide.with(this).load(R.drawable.bg_landing_banner_luxury).override(widthPx, heightPx).centerCrop().into(cover);
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
        String createdDisplay = TimeUtils.formatDate(createdAtRaw); // a date is enough for "member since"
        StringBuilder text = new StringBuilder(getString(R.string.member_since_format, createdDisplay));
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

        loadProfileCover();
        updateProfilePictureButton = findViewById(R.id.updateProfilePictureButton);
        if (updateProfilePictureButton != null) {
            updateProfilePictureButton.setOnClickListener(v -> handleProfilePictureTap());
        }

        setupCollapsibleSection(R.id.personalChevron, R.id.personalBody);
        setupCollapsibleSection(R.id.contactChevron, R.id.contactBody);
        setupCollapsibleSection(R.id.addressChevron, R.id.addressBody);

        View logoutButton = findViewById(R.id.logoutButton);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> new SafeDialogs.Material(this)
                    .setTitle(R.string.logout_confirm_title)
                    .setMessage(R.string.logout_confirm_message)
                    .setPositiveButton(R.string.logout_confirm_action, (d, w) -> logout())
                    .setNegativeButton(R.string.cancel_label, null)
                    .show());
        }
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
        AlertDialog dialog = new SafeDialogs.Material(this)
                .setView(dialogView)
                .setCancelable(false)
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
        // The cached userDob is a display string ("Sep 09, 1990"); the field and the picker work in
        // MM/dd/yyyy, so convert - setting the raw cache here is what used to make the picker
        // fail to parse it and fall back to today.
        editDob.setText(savedDobForWizard());
        String savedAge = prefs.getString("userAge", "");
        java.util.Date savedDobDate = parseDob(editDob.getText().toString());
        if (TextUtils.isEmpty(savedAge) && savedDobDate != null) {
            Calendar dobCal = Calendar.getInstance();
            dobCal.setTime(savedDobDate);
            savedAge = String.valueOf(calculateAge(dobCal.get(Calendar.YEAR), dobCal.get(Calendar.MONTH) + 1, dobCal.get(Calendar.DAY_OF_MONTH)));
        }
        editAge.setText(savedAge);
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
        // one mask, so their input is left as typed. Checks the LIVE Step-3 selection on every
        // keystroke (not just the country on file when the dialog opened) so the mask correctly
        // turns on/off if the guest changes country mid-edit, instead of staying stuck on
        // whichever country was selected at dialog-open. The mask only ever affects what's
        // shown/typed here - validateContactStep()/submitCombinedProfile() strip these spaces
        // back out via PhoneNumberValidator.stripFormatting() before validating or submitting,
        // since PhoneNumberValidator's PH pattern requires the unspaced canonical form.
        editMobile.addTextChangedListener(new TextWatcher() {
            private boolean isUpdating = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                if (!"Philippines".equalsIgnoreCase(addressController.getSelectedCountry())) return;
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

        // Unsaved-changes check for Back/Cancel: compares the live fields with the saved profile
        // (rather than flagging any TextWatcher callback), so the async address cascade filling in
        // the saved address, or typing a value back to what it was, never counts as a change.
        java.util.function.BooleanSupplier hasChanges = () -> {
            String currentEmail = prefs.getString("userEmail", "");
            if (!editEmail.getText().toString().trim().equalsIgnoreCase(currentEmail)) return true;
            return profileFieldsDiffer(editFirst, editMiddle, editLast, editGender, editDob, editMobile,
                    addressController.getStructuredValues(), true);
        };

        Runnable doCancel = () -> {
            if (hasChanges.getAsBoolean()) {
                new SafeDialogs.Material(this)
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

        dialog.setOnDismissListener(d -> addressController.dispose());
        // Hardware/gesture Back: step back through the wizard, and ask before discarding on the
        // first step. Tapping outside never closes it (setCancelable(false)).
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != android.view.KeyEvent.KEYCODE_BACK || event.getAction() != android.view.KeyEvent.ACTION_UP) return false;
            if (currentStep[0] > 1) {
                currentStep[0]--;
                renderStep[0].run();
            } else {
                doCancel.run();
            }
            return true;
        });
        SafeDialogs.show(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    /**
     * True when any editable profile field (everything except email, which has its own OTP flow)
     * differs from what is saved. With {@code ignoreUnloadedAddress}, an address part that is
     * still empty (the cascade is still loading the saved value) is not treated as a change.
     */
    private boolean profileFieldsDiffer(TextInputEditText editFirst, TextInputEditText editMiddle, TextInputEditText editLast,
                                        AutoCompleteTextView editGender, TextInputEditText editDob, TextInputEditText editMobile,
                                        AddressSelection sel, boolean ignoreUnloadedAddress) {
        if (!editFirst.getText().toString().trim().equals(prefs.getString("userFirstName", "").trim())) return true;
        if (!editMiddle.getText().toString().trim().equals(prefs.getString("userMiddleName", "").trim())) return true;
        if (!editLast.getText().toString().trim().equals(prefs.getString("userLastName", "").trim())) return true;
        if (!editGender.getText().toString().trim().equalsIgnoreCase(displayGender(prefs.getString("userGender", "")))) return true;
        String dobIso = toApiDate(editDob.getText().toString());
        if (dobIso != null && !dobIso.equals(savedDobIso())) return true;
        String mobile = PhoneNumberValidator.stripFormatting(editMobile.getText().toString().trim());
        if (!mobile.equals(PhoneNumberValidator.stripFormatting(prefs.getString("userMobile", "").trim()))) return true;
        String[][] pairs = {
                {sel.country, prefs.getString("userCountry", "")}, {sel.region, prefs.getString("userRegion", "")},
                {sel.province, prefs.getString("userProvince", "")}, {sel.city, prefs.getString("userCity", "")},
                {sel.barangay, prefs.getString("userBarangay", "")}, {sel.street, prefs.getString("userStreet", "")},
                {sel.zip, prefs.getString("userZip", "")}};
        for (String[] pair : pairs) {
            String now = pair[0] == null ? "" : pair[0].trim();
            String saved = pair[1] == null ? "" : pair[1].trim();
            if (ignoreUnloadedAddress && now.isEmpty()) continue;
            if (!now.equals(saved)) return true;
        }
        return false;
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
        // Strip the Philippines display mask's spaces ("+63 917 123 4567") before
        // validating - PhoneNumberValidator's PH pattern requires the unspaced
        // canonical form and would otherwise reject every masked PH number, making
        // it impossible to ever save a changed mobile number (see stripFormatting()).
        String mobileStr = mobile.getText() != null ? PhoneNumberValidator.stripFormatting(mobile.getText().toString().trim()) : "";
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
        return ApiErrorParser.extractMessage(response, fallback);
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
        // Same normalization as validateContactStep() - the server must receive the
        // canonical unspaced form, matching what's already stored for every other guest.
        String mobileStr = PhoneNumberValidator.stripFormatting(editMobile.getText().toString().trim());
        AddressSelection selection = addressController.getStructuredValues();

        // Email is handled entirely separately below via the OTP-gated flow -
        // never sent through this generic update, which the server no longer
        // accepts email changes on anyway (see RequestEmailChangeRequest).
        String currentEmail = prefs.getString("userEmail", "");
        boolean emailChanged = !emailStr.equalsIgnoreCase(currentEmail);

        // Nothing but (maybe) the email changed: don't send a profile update at all - the server
        // counts every accepted update against the 30-day cooldown, so a no-op save would burn it.
        if (!profileFieldsDiffer(editFirst, editMiddle, editLast, editGender, editDob, editMobile, selection, false)) {
            if (emailChanged) {
                dismissSafely(dialog);
                showConfirmPasswordForEmailChangeDialog(emailStr);
            } else {
                Toast.makeText(this, R.string.profile_no_changes, Toast.LENGTH_SHORT).show();
                dismissSafely(dialog);
            }
            return;
        }

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.first_name = firstStr;
        request.last_name = lastStr;
        request.middle_name = middleStr.isEmpty() ? null : middleStr;
        if (!genderStr.isEmpty()) request.gender = mapGender(genderStr);
        // Unchanged date -> the exact saved value; otherwise the picked date. Never today, never garbage.
        String dobIso = toApiDate(dobStr);
        if (dobIso != null) request.date_of_birth = dobIso;
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
                if (isFinishing() || isDestroyed()) return;
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

                if (emailChanged) {
                    // Everything else just saved successfully - email is
                    // handled as its own OTP-gated step from here. Those
                    // other saved fields are never rolled back even if the
                    // guest cancels the email step that follows.
                    dismissSafely(dialog);
                    showConfirmPasswordForEmailChangeDialog(emailStr);
                } else {
                    Toast.makeText(ProfileManagementActivity.this, R.string.profile_updated_full, Toast.LENGTH_SHORT).show();
                    dismissSafely(dialog);
                }
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                btnSave.setEnabled(true);
                Toast.makeText(ProfileManagementActivity.this, RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Step 1 of the email-change flow - re-confirms the current password
     * before anything about the new address is even sent (defense against a
     * stolen/left-open session trying to silently redirect account
     * recovery), matching Api\ProfileController::requestEmailChange()'s own
     * server-side re-check. The password only ever lives in this field/local
     * request for the one call it's needed for - never SharedPreferences,
     * never logged, and not kept around to support a "resend" shortcut (see
     * the OTP dialog's resend link, which re-opens this screen instead of
     * silently reusing an old value).
     */
    private void showConfirmPasswordForEmailChangeDialog(String newEmail) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_password_email_change, null);
        AlertDialog dialog = new SafeDialogs.Material(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextInputEditText passwordField = dialogView.findViewById(R.id.confirmEmailChangePassword);
        MaterialButton btnContinue = dialogView.findViewById(R.id.btnContinueConfirmEmailChangePassword);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelConfirmEmailChangePassword);

        btnCancel.setOnClickListener(v -> {
            if (passwordField.getText() != null) passwordField.getText().clear();
            dialog.dismiss();
        });

        btnContinue.setOnClickListener(v -> {
            String password = passwordField.getText() != null ? passwordField.getText().toString() : "";
            if (TextUtils.isEmpty(password)) {
                passwordField.setError(getString(R.string.password_required));
                return;
            }

            btnContinue.setEnabled(false);
            ApiClient.getService(this).requestEmailChange(new RequestEmailChangeRequest(newEmail, password))
                    .enqueue(new Callback<ApiMessage>() {
                        @Override
                        public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                            if (isFinishing() || isDestroyed()) return;
                            btnContinue.setEnabled(true);
                            // Never held longer than this one request, success or not.
                            if (passwordField.getText() != null) passwordField.getText().clear();

                            if (!response.isSuccessful()) {
                                Toast.makeText(ProfileManagementActivity.this,
                                        errorMessage(response, getString(R.string.incorrect_password)), Toast.LENGTH_LONG).show();
                                return;
                            }

                            dismissSafely(dialog);
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(ProfileManagementActivity.this, getString(R.string.otp_sent_to, newEmail), Toast.LENGTH_SHORT).show();
                                showVerifyNewEmailOtpDialog(newEmail);
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiMessage> call, Throwable t) {
                            if (isFinishing() || isDestroyed()) return;
                            btnContinue.setEnabled(true);
                            if (passwordField.getText() != null) passwordField.getText().clear();
                            Toast.makeText(ProfileManagementActivity.this, RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        SafeDialogs.show(dialog);
    }

    /**
     * Step 2 - the new email only actually becomes the registered address
     * once this succeeds; cancelling here leaves the account exactly as it
     * was (old email still active, still the login/recovery address). The
     * profile is re-fetched from the server afterward rather than trusting
     * a local edit, so a killed/recreated Activity can never show an
     * unconfirmed email as if it were already verified.
     */
    private void showVerifyNewEmailOtpDialog(String newEmail) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_verify_new_email_otp, null);
        AlertDialog dialog = new SafeDialogs.Material(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView descText = dialogView.findViewById(R.id.verifyNewEmailDesc);
        if (descText != null) descText.setText(getString(R.string.otp_sent_to, newEmail));

        TextInputEditText otpField = dialogView.findViewById(R.id.verifyNewEmailOtp);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmVerifyNewEmailOtp);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelVerifyNewEmailOtp);
        TextView resendLink = dialogView.findViewById(R.id.resendVerifyNewEmailOtpLink);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Resend is locked for 30s after each code is sent, with a visible countdown.
        resendLink.setEnabled(false);
        android.os.CountDownTimer resendTimer = new android.os.CountDownTimer(30_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                resendLink.setText(getString(R.string.resend_otp_countdown, (int) ((millisUntilFinished + 999) / 1000)));
            }

            @Override
            public void onFinish() {
                resendLink.setText(R.string.resend_otp);
                resendLink.setEnabled(true);
            }
        };
        resendTimer.start();
        dialog.setOnDismissListener(d -> resendTimer.cancel());

        // Resending means calling requestEmailChange() again, which needs the
        // password again too - rather than holding it in memory just to
        // support this link, restart the previous step honestly.
        resendLink.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(ProfileManagementActivity.this, R.string.resend_code_reenter_password, Toast.LENGTH_LONG).show();
            showConfirmPasswordForEmailChangeDialog(newEmail);
        });

        btnConfirm.setOnClickListener(v -> {
            String otp = otpField.getText() != null ? otpField.getText().toString().trim() : "";
            if (otp.length() != 6) {
                otpField.setError(getString(R.string.invalid_otp));
                return;
            }

            btnConfirm.setEnabled(false);
            ApiClient.getService(this).confirmEmailChange(new ConfirmEmailChangeRequest(otp))
                    .enqueue(new Callback<ApiMessage>() {
                        @Override
                        public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                            if (isFinishing() || isDestroyed()) return;
                            btnConfirm.setEnabled(true);
                            if (!response.isSuccessful()) {
                                String msg = errorMessage(response, getString(R.string.otp_invalid_or_expired));
                                otpField.setError(msg);
                                Toast.makeText(ProfileManagementActivity.this, msg, Toast.LENGTH_LONG).show();
                                return;
                            }

                            dismissSafely(dialog);
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(ProfileManagementActivity.this, R.string.email_updated_success, Toast.LENGTH_LONG).show();
                            }
                            // Authoritative refresh from the server - never just
                            // paint the locally-typed address on as if confirmed.
                            fetchProfileFromServer();
                        }

                        @Override
                        public void onFailure(Call<ApiMessage> call, Throwable t) {
                            if (isFinishing() || isDestroyed()) return;
                            btnConfirm.setEnabled(true);
                            Toast.makeText(ProfileManagementActivity.this, RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        SafeDialogs.show(dialog);
    }

    /**
     * OTP-gated instead of current-password-gated: opens the three-step Change Password dialog, whose first step
     * sends a verification code to the guest's registered email (the same forgot-password endpoint the Login
     * screen's Forgot Password flow uses). The password itself is changed
     * through guest/password/change, which needs the token the server
     * issues once the code is verified.
     */
    private void initiatePasswordReset() {
        String email = prefs.getString("userEmail", "");
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Couldn't determine your account email.", Toast.LENGTH_LONG).show();
            return;
        }
        showChangePasswordOtpDialog(email);
    }

    /**
     * Change Password, one dialog with three mutually exclusive steps. Step 1 asks to send the code; step 2 verifies the emailed
     * code with the server, which answers with a short-lived single-use token; only then is step 2
     * (the password fields) shown, and the password change is sent together with that token, so
     * the server itself refuses a change that skipped verification. The token lives only in this
     * method's locals: closing the dialog at any point discards it and the next attempt starts
     * again at step 1.
     */
    private void showChangePasswordOtpDialog(String email) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password_otp, null);
        AlertDialog dialog = new SafeDialogs.Material(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        View stepRequest = dialogView.findViewById(R.id.changePasswordStepRequest);
        TextView requestDesc = dialogView.findViewById(R.id.changePasswordRequestDesc);
        TextView requestError = dialogView.findViewById(R.id.changePasswordRequestError);
        View seg2 = dialogView.findViewById(R.id.changePasswordSeg2);
        View seg3 = dialogView.findViewById(R.id.changePasswordSeg3);
        TextView labelVerify = dialogView.findViewById(R.id.changePasswordLabelVerify);
        TextView labelNew = dialogView.findViewById(R.id.changePasswordLabelNew);
        TextView labelDone = dialogView.findViewById(R.id.changePasswordLabelDone);
        View stepDone = dialogView.findViewById(R.id.changePasswordStepDone);
        com.google.android.material.progressindicator.LinearProgressIndicator strengthBar = dialogView.findViewById(R.id.changePasswordStrengthBar);
        TextView strengthText = dialogView.findViewById(R.id.changePasswordStrengthText);
        View stepOtp = dialogView.findViewById(R.id.changePasswordStepOtp);
        View stepPassword = dialogView.findViewById(R.id.changePasswordStepPassword);
        TextView stepText = dialogView.findViewById(R.id.changePasswordStepText);
        TextView otpDesc = dialogView.findViewById(R.id.changePasswordOtpDesc);
        TextView otpError = dialogView.findViewById(R.id.changePasswordOtpError);
        TextView resendLink = dialogView.findViewById(R.id.resendChangePasswordOtpLink);
        TextInputEditText newPass = dialogView.findViewById(R.id.changePasswordNew);
        TextInputEditText confirmPass = dialogView.findViewById(R.id.changePasswordConfirmNew);
        TextInputLayout newPassLayout = dialogView.findViewById(R.id.changePasswordNewLayout);
        TextInputLayout confirmPassLayout = dialogView.findViewById(R.id.changePasswordConfirmNewLayout);
        TextView ruleLength = dialogView.findViewById(R.id.changePasswordRuleLength);
        TextView ruleMatch = dialogView.findViewById(R.id.changePasswordRuleMatch);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmChangePasswordOtp);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelChangePasswordOtp);

        otpDesc.setText(getString(R.string.change_password_otp_sent_desc, OtpBoxes.maskEmail(email)));
        requestDesc.setText(getString(R.string.change_password_request_desc, OtpBoxes.maskEmail(email)));

        // null until the SERVER has accepted the code; step 2 is only reachable when it is set.
        final String[] resetToken = {null};
        final boolean[] busy = {false};
        final OtpBoxes[] otpBoxes = new OtpBoxes[1];
        final int[] step = {1}; // 1 request the code, 2 enter it (both "Verify"), 3 new password, 4 done
        final boolean[] changed = {false};
        final int onColor = getColor(R.color.velocity_red_primary);
        final int offColor = getColor(R.color.velocity_red_soft);

        // One place that shows a step: its section, the Verify / New password / Done indicator, and the main button's label.
        Runnable showStep = () -> {
            stepRequest.setVisibility(step[0] == 1 ? View.VISIBLE : View.GONE);
            stepOtp.setVisibility(step[0] == 2 ? View.VISIBLE : View.GONE);
            stepPassword.setVisibility(step[0] == 3 ? View.VISIBLE : View.GONE);
            stepDone.setVisibility(step[0] == 4 ? View.VISIBLE : View.GONE);
            int stage = step[0] <= 2 ? 1 : step[0] == 3 ? 2 : 3;
            stepText.setText(step[0] == 4 ? getString(R.string.change_password_done_title)
                    : getString(R.string.registration_step_format, stage, 3));
            seg2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(stage >= 2 ? onColor : offColor));
            seg3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(stage >= 3 ? onColor : offColor));
            labelVerify.setTextColor(getColor(stage == 1 ? R.color.velocity_red_primary : R.color.velocity_text_secondary));
            labelNew.setTextColor(getColor(stage == 2 ? R.color.velocity_red_primary : R.color.velocity_text_secondary));
            labelDone.setTextColor(getColor(stage == 3 ? R.color.velocity_red_primary : R.color.velocity_text_secondary));
            for (TextView l : new TextView[]{labelVerify, labelNew, labelDone}) {
                l.setTypeface(null, l.getCurrentTextColor() == onColor ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
            btnConfirm.setText(step[0] == 1 ? R.string.change_password_send_code
                    : step[0] == 2 ? R.string.verify_otp_button
                    : step[0] == 3 ? R.string.reset_password_button : R.string.change_password_done_button);
            btnCancel.setVisibility(step[0] == 4 ? View.GONE : View.VISIBLE);
        };

        Runnable verifyCode = () -> {
            if (busy[0] || resetToken[0] != null) return;
            String code = otpBoxes[0].getCode();
            if (code.length() != 6) {
                showOtpError(otpError, otpBoxes[0], getString(R.string.invalid_otp));
                return;
            }
            busy[0] = true;
            btnConfirm.setEnabled(false);
            ApiClient.getService(this).verifyResetOtpForToken(new com.example.velocitysuites.network.dto.VerifyResetOtpRequest(email, code))
                    .enqueue(new Callback<com.example.velocitysuites.network.dto.ResetTokenResponse>() {
                        @Override
                        public void onResponse(Call<com.example.velocitysuites.network.dto.ResetTokenResponse> call,
                                               Response<com.example.velocitysuites.network.dto.ResetTokenResponse> response) {
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            busy[0] = false;
                            btnConfirm.setEnabled(true);
                            com.example.velocitysuites.network.dto.ResetTokenResponse body = response.body();
                            if (!response.isSuccessful() || body == null || TextUtils.isEmpty(body.reset_token)) {
                                String msg = response.code() == 429
                                        ? getString(R.string.otp_too_many_attempts)
                                        : (response.isSuccessful() ? getString(R.string.otp_invalid_or_expired)
                                        : errorMessage(response, getString(R.string.otp_invalid_or_expired)));
                                otpBoxes[0].clear();
                                showOtpError(otpError, otpBoxes[0], msg);
                                return;
                            }
                            resetToken[0] = body.reset_token;
                            Toast.makeText(ProfileManagementActivity.this, R.string.otp_verified, Toast.LENGTH_SHORT).show();
                            step[0] = 3;
                            showStep.run();
                            newPass.requestFocus();
                        }

                        @Override
                        public void onFailure(Call<com.example.velocitysuites.network.dto.ResetTokenResponse> call, Throwable t) {
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            busy[0] = false;
                            btnConfirm.setEnabled(true);
                            showOtpError(otpError, otpBoxes[0], RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t));
                        }
                    });
        };

        otpBoxes[0] = new OtpBoxes(dialogView.findViewById(R.id.changePasswordOtpBoxes), 6, (code, complete) -> {
            otpError.setVisibility(View.GONE);
            otpBoxes[0].setError(false);
            if (complete) verifyCode.run();
        });

        showStep.run();

        // ---- resend with a 30s countdown (same cadence as the email-change flow; the server enforces it too) ----
        final android.os.CountDownTimer[] resendTimer = {null};
        Runnable startResendCountdown = () -> {
            if (resendTimer[0] != null) resendTimer[0].cancel();
            resendLink.setEnabled(false);
            resendTimer[0] = new android.os.CountDownTimer(30_000, 1_000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    resendLink.setText(getString(R.string.resend_otp_countdown, (int) ((millisUntilFinished + 999) / 1000)));
                }

                @Override
                public void onFinish() {
                    resendLink.setText(R.string.resend_otp);
                    resendLink.setEnabled(true);
                }
            }.start();
        };

        // ---- step 1: send the code. A 429 means one was sent moments ago (still valid), so it counts as sent. ----
        Runnable sendCode = () -> {
            if (busy[0]) return;
            busy[0] = true;
            btnConfirm.setEnabled(false);
            requestError.setVisibility(View.GONE);
            ApiClient.getService(this).forgotPassword(new com.example.velocitysuites.network.dto.EmailRequest(email))
                    .enqueue(new Callback<ApiMessage>() {
                        @Override
                        public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            busy[0] = false;
                            btnConfirm.setEnabled(true);
                            if (!response.isSuccessful() && response.code() != 429) {
                                requestError.setText(errorMessage(response, getString(R.string.reset_code_send_failed)));
                                requestError.setVisibility(View.VISIBLE);
                                return;
                            }
                            Toast.makeText(ProfileManagementActivity.this, getString(R.string.otp_sent_to, OtpBoxes.maskEmail(email)), Toast.LENGTH_SHORT).show();
                            step[0] = 2;
                            showStep.run();
                            startResendCountdown.run();
                            otpBoxes[0].focusFirst();
                        }

                        @Override
                        public void onFailure(Call<ApiMessage> call, Throwable t) {
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            busy[0] = false;
                            btnConfirm.setEnabled(true);
                            requestError.setText(RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t));
                            requestError.setVisibility(View.VISIBLE);
                        }
                    });
        };

        resendLink.setOnClickListener(v -> {
            resendLink.setEnabled(false);
            ApiClient.getService(this).forgotPassword(new com.example.velocitysuites.network.dto.EmailRequest(email))
                    .enqueue(new Callback<ApiMessage>() {
                        @Override
                        public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            if (response.isSuccessful()) {
                                Toast.makeText(ProfileManagementActivity.this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                                otpBoxes[0].clear();
                                otpError.setVisibility(View.GONE);
                            } else {
                                showOtpError(otpError, otpBoxes[0], errorMessage(response, getString(R.string.reset_code_send_failed)));
                            }
                            startResendCountdown.run();
                        }

                        @Override
                        public void onFailure(Call<ApiMessage> call, Throwable t) {
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            resendLink.setEnabled(true);
                            showOtpError(otpError, otpBoxes[0], RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t));
                        }
                    });
        });

        // ---- step 2: live rule hints (they mirror the server: at least 8 characters, and a matching confirmation) ----
        Runnable updateRules = () -> {
            String p = newPass.getText() != null ? newPass.getText().toString() : "";
            String c = confirmPass.getText() != null ? confirmPass.getText().toString() : "";
            updateStrength(strengthBar, strengthText, p);
            setRuleState(ruleLength, R.string.password_rule_length, p.length() >= 8);
            setRuleState(ruleMatch, R.string.password_rule_match, !p.isEmpty() && p.equals(c));
        };
        TextWatcher rulesWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                newPassLayout.setError(null);
                confirmPassLayout.setError(null);
                updateRules.run();
            }
        };
        newPass.addTextChangedListener(rulesWatcher);
        confirmPass.addTextChangedListener(rulesWatcher);
        updateRules.run();

        Runnable submitPassword = () -> {
            if (busy[0] || resetToken[0] == null) return;
            String nPass = newPass.getText() != null ? newPass.getText().toString() : "";
            String cPass = confirmPass.getText() != null ? confirmPass.getText().toString() : "";
            if (nPass.length() < 8) {
                newPassLayout.setError(getString(R.string.password_min_length));
                newPass.requestFocus();
                return;
            }
            if (!nPass.equals(cPass)) {
                confirmPassLayout.setError(getString(R.string.passwords_do_not_match));
                confirmPass.requestFocus();
                return;
            }

            busy[0] = true;
            btnConfirm.setEnabled(false);
            ApiClient.getService(this).changePasswordWithToken(
                            new com.example.velocitysuites.network.dto.ChangePasswordTokenRequest(resetToken[0], nPass, cPass))
                    .enqueue(new Callback<ApiMessage>() {
                        @Override
                        public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                            busy[0] = false;
                            if (response.isSuccessful()) {
                                // The password is already changed server-side regardless of whether this
                                // screen is still visible - markUpdated()/logout() always run.
                                resetToken[0] = null;
                                markUpdated(KEY_PASSWORD_UPDATED_AT);
                                changed[0] = true;
                                // The server revokes every API token (this device and all others) on a password
                                // change, so the app signs the guest out to log back in with the new password -
                                // when they leave the success screen (button, back, or the dialog closing).
                                if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                                    logout();
                                    return;
                                }
                                newPass.setText("");
                                confirmPass.setText("");
                                busy[0] = false;
                                btnConfirm.setEnabled(true);
                                step[0] = 4;
                                showStep.run();
                                return;
                            }
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            btnConfirm.setEnabled(true);
                            String msg = errorMessage(response, getString(R.string.password_change_failed));
                            if (response.code() == 422 && msg.toLowerCase(Locale.ROOT).contains("verification")) {
                                // The server no longer accepts the verification (expired or already used): start over.
                                resetToken[0] = null;
                                step[0] = 2;
                                showStep.run();
                                newPass.setText("");
                                confirmPass.setText("");
                                otpBoxes[0].clear();
                                showOtpError(otpError, otpBoxes[0], msg);
                            } else {
                                newPassLayout.setError(msg);
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiMessage> call, Throwable t) {
                            busy[0] = false;
                            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                            btnConfirm.setEnabled(true);
                            Toast.makeText(ProfileManagementActivity.this, RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t), Toast.LENGTH_LONG).show();
                        }
                    });
        };
        confirmPass.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submitPassword.run();
                return true;
            }
            return false;
        });

        btnConfirm.setOnClickListener(v -> {
            if (step[0] == 4) dialog.dismiss();
            else if (step[0] == 1) sendCode.run();
            else if (step[0] == 2) verifyCode.run();
            else submitPassword.run();
        });

        // ---- leaving: ask first if anything was entered; a verified code is always discarded ----
        Runnable requestClose = () -> {
            if (step[0] == 4) {
                dialog.dismiss();
                return;
            }
            boolean hasInput = resetToken[0] != null || !otpBoxes[0].isEmpty()
                    || (newPass.getText() != null && newPass.getText().length() > 0)
                    || (confirmPass.getText() != null && confirmPass.getText().length() > 0);
            if (!hasInput) {
                dialog.dismiss();
                return;
            }
            new SafeDialogs.Material(this)
                    .setTitle(R.string.discard_changes_title)
                    .setMessage(R.string.change_password_discard_message)
                    .setPositiveButton(R.string.discard_changes_action, (d, w) -> dialog.dismiss())
                    .setNegativeButton(R.string.continue_editing_label, null)
                    .show();
        };
        btnCancel.setOnClickListener(v -> requestClose.run());
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != android.view.KeyEvent.KEYCODE_BACK || event.getAction() != android.view.KeyEvent.ACTION_UP) return false;
            requestClose.run();
            return true;
        });
        dialog.setOnDismissListener(d -> {
            if (resendTimer[0] != null) resendTimer[0].cancel();
            resetToken[0] = null;
            if (changed[0]) logout();
        });

        SafeDialogs.show(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void showOtpError(TextView errorView, OtpBoxes boxes, String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
        boxes.setError(true);
    }

    /** 0 (empty) to 4: length 8+, mixed case, a digit, a symbol - a hint shown next to the server's own rule (8+ characters). */
    static int passwordStrength(String p) {
        if (p == null || p.isEmpty()) return 0;
        int score = 0;
        if (p.length() >= 8) score++;
        if (!p.equals(p.toLowerCase(Locale.ROOT)) && !p.equals(p.toUpperCase(Locale.ROOT))) score++;
        if (p.matches(".*\\d.*")) score++;
        if (p.matches(".*[^A-Za-z0-9].*")) score++;
        if (p.length() < 8) score = Math.min(score, 1);
        score = Math.max(score, 1); // anything typed is at least "Weak"
        return score;
    }

    private void updateStrength(com.google.android.material.progressindicator.LinearProgressIndicator bar, TextView text, String password) {
        int score = passwordStrength(password);
        bar.setProgress(score);
        text.setText(score == 0 ? R.string.password_strength_none : score == 1 ? R.string.password_strength_weak
                : score == 2 ? R.string.password_strength_fair : score == 3 ? R.string.password_strength_good : R.string.password_strength_strong);
    }

    private void setRuleState(TextView rule, int textRes, boolean met) {
        rule.setText(getString(met ? R.string.password_rule_met_format : R.string.password_rule_unmet_format, getString(textRes)));
        rule.setTextColor(getColor(met ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
    }

    private void selectProfilePicture() {
        String[] options = {getString(R.string.take_photo), getString(R.string.choose_from_gallery)};
        new SafeDialogs.Material(this)
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
                    // Decoding the picked photo reads a file: off the main thread.
                    BackgroundWork.run(this, () -> ImageDecoding.decode(getContentResolver(), selectedImage, ImageDecoding.DEFAULT_MAX_EDGE), bitmap -> {
                        if (bitmap != null) {
                            confirmAndUploadProfilePicture(bitmap);
                        } else {
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else if (requestCode == CAPTURE_IMAGE_REQUEST && data.getExtras() != null) {
                Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                if (imageBitmap != null) {
                    confirmAndUploadProfilePicture(imageBitmap);
                }
            }
        }
    }

    private void confirmAndUploadProfilePicture(Bitmap bitmap) {
        new SafeDialogs.Material(this)
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

        BackgroundWork.run(this, () -> writeProfileJpeg(bitmap), file -> {
            if (file == null) {
                Toast.makeText(this, "Failed to prepare image for upload", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadProfileFile(file);
        });
    }

    private File writeProfileJpeg(Bitmap bitmap) throws IOException {
        File tempFile = File.createTempFile("profile_", ".jpg", getCacheDir());
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        return tempFile;
    }

    private void uploadProfileFile(File tempFile) {
        RequestBody fileBody = RequestBody.create(tempFile, MediaType.parse("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("profile_picture", tempFile.getName(), fileBody);

        ApiClient.getService(this).updateProfilePicture(part).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                tempFile.delete();
                if (isFinishing() || isDestroyed()) return;
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
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ProfileManagementActivity.this, RoomRepository.networkErrorMessage(ProfileManagementActivity.this, t), Toast.LENGTH_LONG).show();
            }
        });
    }
    /**
     * Personal / Contact / Address details stay hidden until the guest taps the section header
     * (the row the chevron sits in); tapping again collapses it. The body views stay in the
     * hierarchy, so the rest of this class keeps binding to them unchanged.
     */
    private void setupCollapsibleSection(int chevronId, int bodyId) {
        View chevron = findViewById(chevronId);
        View body = findViewById(bodyId);
        if (chevron == null || body == null || !(chevron.getParent() instanceof ViewGroup)) return;
        ViewGroup header = (ViewGroup) chevron.getParent();
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> {
            boolean expand = body.getVisibility() != View.VISIBLE;
            ViewGroup card = (ViewGroup) body.getParent();
            android.transition.TransitionManager.beginDelayedTransition(card, new android.transition.AutoTransition().setDuration(200));
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(expand ? 180f : 0f).setDuration(200).start();
        });
    }
}
