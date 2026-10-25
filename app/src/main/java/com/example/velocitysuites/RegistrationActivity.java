package com.example.velocitysuites;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.AuthResponse;
import com.example.velocitysuites.network.dto.EmailRequest;
import com.example.velocitysuites.network.dto.ProfileResponse;
import com.example.velocitysuites.network.dto.RegisterRequest;
import com.example.velocitysuites.network.dto.VerifyOtpRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Create account, in six short steps: 1 name, 2 about you (gender, date of birth, the age that follows from it), 3 contact (email, mobile),
 * 4 address, 5 password and photo, 6 review and terms. Then the verification screen (a six-box code) and the success screen. One compact
 * header on every screen (logo, "Create account", "Step N of 6 - Title", one progress bar) and Back / Next pinned at the bottom: Next is
 * always on screen, disabled with the reason above it until the step is valid.
 */
public class RegistrationActivity extends SafeActivity {

    /** Opens straight on the verification screen for an address whose sign-up was started but never verified (sent by Sign in). */
    public static final String EXTRA_VERIFY_EMAIL = "verify_email";

    static final int STEPS = 6;
    private static final int PHASE_FORM = 0, PHASE_VERIFY = 1, PHASE_SUCCESS = 2;
    private static final long RESEND_COOLDOWN_MS = 60_000L;
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DOB_SHOWN = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private static final String STATE_STEP = "currentStep";
    private static final String STATE_PHASE = "phase";
    private static final String STATE_DOB = "dob";
    private static final String STATE_VERIFY_EMAIL = "verifyEmail";
    private static final String STATE_VERIFY_ONLY = "verifyOnly";
    private static final String STATE_RESEND_UNTIL = "resendUntil";
    private static final String STATE_ADDR = "addr_";

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextInputEditText firstNameEdit, lastNameEdit, middleNameEdit, dobEdit, mobileEdit, emailEdit, passwordEdit, confirmPasswordEdit;
    private AutoCompleteTextView genderDropdown;
    private TextInputLayout mobileInputLayout, dobLayout, genderLayout;
    private final View[] stepLayouts = new View[STEPS + 1];
    private View otpLayout, successLayout, loginRedirect, bottomBar;
    private MaterialButton nextButton, signUpButton, previousButton;
    private TextView stepText, nextReason, ageText, ageErrorText, resendOtpLink, otpErrorText, otpVerifyingText, regSubmitError, otpDesc;
    private LinearProgressIndicator progressBar;
    private NestedScrollView scroll;
    private TermsConsentView termsConsent;
    private AddressHierarchyController addressController;
    private OtpBoxes otpBoxes;
    private ImageView regProfileImage;
    private TextView regPhotoStatusText;
    private View regRemovePhotoLink;
    private ProgressBar strengthBar;
    private TextView strengthText;
    private TextView[] rules;
    private android.os.CountDownTimer resendTimer;

    private int phase = PHASE_FORM;
    private int currentStep = 1;
    private boolean verifyOnly;
    private String verifyEmail = "";
    private long resendUntil;
    /** True while register / verify is in flight - a second tap does nothing. */
    private boolean requestInFlight;
    @Nullable
    private LocalDate dob;
    /** Picked now but only uploaded after the code is verified (the picture endpoint needs the account's token). */
    private Bitmap selectedProfileBitmap;
    private boolean continuing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration);
        bindViews();
        setupGender();
        dobEdit.setOnClickListener(v -> openDobPicker());
        addressController = AddressHierarchyController.attach(this, findViewById(R.id.step4Layout));
        addressController.setOnCountryChangeListener(isPh -> {
            mobileInputLayout.setHelperText(getString(isPh ? R.string.reg_mobile_helper : R.string.helper_mobile_format_intl));
            refreshNext();
        });
        setupValidation();
        setupPasswordRules();
        setupButtons();

        verifyOnly = getIntent().getStringExtra(EXTRA_VERIFY_EMAIL) != null;
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else if (verifyOnly) {
            verifyEmail = getIntent().getStringExtra(EXTRA_VERIFY_EMAIL);
            emailEdit.setText(verifyEmail);
            phase = PHASE_VERIFY;
            resendUntil = android.os.SystemClock.elapsedRealtime() + RESEND_COOLDOWN_MS;
        }
        render(false);
    }

    // ---------------------------------------------------------------- setup

    private void bindViews() {
        firstNameEdit = findViewById(R.id.firstNameEditText);
        lastNameEdit = findViewById(R.id.lastNameEditText);
        middleNameEdit = findViewById(R.id.middleNameEditText);
        dobEdit = findViewById(R.id.dobEditText);
        mobileEdit = findViewById(R.id.mobileEditText);
        mobileInputLayout = findViewById(R.id.mobileInputLayout);
        dobLayout = findViewById(R.id.dobLayout);
        genderLayout = findViewById(R.id.genderLayout);
        emailEdit = findViewById(R.id.emailEditText);
        passwordEdit = findViewById(R.id.passwordEditText);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEditText);
        genderDropdown = findViewById(R.id.genderDropdown);
        int[] ids = {0, R.id.step1Layout, R.id.step2Layout, R.id.step3Layout, R.id.step4Layout, R.id.step5Layout, R.id.step6Layout};
        for (int i = 1; i <= STEPS; i++) stepLayouts[i] = findViewById(ids[i]);
        otpLayout = findViewById(R.id.otpLayout);
        successLayout = findViewById(R.id.successLayout);
        loginRedirect = findViewById(R.id.loginRedirect);
        bottomBar = findViewById(R.id.bottomActionBar);
        nextButton = findViewById(R.id.nextButton);
        signUpButton = findViewById(R.id.signUpButton);
        previousButton = findViewById(R.id.btnPrevious);
        stepText = findViewById(R.id.stepText);
        nextReason = findViewById(R.id.nextReason);
        ageText = findViewById(R.id.ageText);
        ageErrorText = findViewById(R.id.ageValidationErrorText);
        resendOtpLink = findViewById(R.id.resendOtpLink);
        otpErrorText = findViewById(R.id.otpErrorText);
        otpVerifyingText = findViewById(R.id.otpVerifyingText);
        regSubmitError = findViewById(R.id.regSubmitError);
        otpDesc = findViewById(R.id.otpVerifyDescText);
        progressBar = findViewById(R.id.stepProgressBar);
        scroll = findViewById(R.id.regScroll);
        termsConsent = findViewById(R.id.termsConsent);
        regProfileImage = findViewById(R.id.regProfileImage);
        regPhotoStatusText = findViewById(R.id.regPhotoStatusText);
        regRemovePhotoLink = findViewById(R.id.regRemovePhotoLink);
        strengthBar = findViewById(R.id.passwordStrengthBar);
        strengthText = findViewById(R.id.passwordStrengthText);
        rules = new TextView[]{findViewById(R.id.ruleLength), findViewById(R.id.ruleLetter), findViewById(R.id.ruleNumber), findViewById(R.id.ruleMatch)};
    }

    private void setupGender() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{"Male", "Female"});
        genderDropdown.setAdapter(adapter);
        genderDropdown.setOnClickListener(v -> genderDropdown.showDropDown());
        genderDropdown.setOnItemClickListener((p, v, pos, id) -> {
            genderLayout.setError(null);
            refreshNext();
        });
    }

    private void setupButtons() {
        nextButton.setOnClickListener(v -> goNext());
        previousButton.setOnClickListener(v -> handleBack());
        signUpButton.setOnClickListener(v -> confirmAndRegister());
        findViewById(R.id.btnBack).setOnClickListener(v -> handleBack());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
        findViewById(R.id.signInLink).setOnClickListener(v -> confirmLeave(this::finish));
        findViewById(R.id.regSelectPhotoButton).setOnClickListener(v -> selectProfilePicture());
        regRemovePhotoLink.setOnClickListener(v -> clearSelectedProfilePicture());
        termsConsent.setActionLabel(getString(R.string.reg_create_account));
        termsConsent.setListener((viewed, accepted) -> refreshNext());
        resendOtpLink.setOnClickListener(v -> resendOtp());
        findViewById(R.id.changeEmailLink).setOnClickListener(v -> changeEmail());
        findViewById(R.id.successContinueButton).setOnClickListener(v -> continueIntoApp());
    }

    /** Inline errors appear when a field loses focus and clear while it is edited; every edit re-decides whether Next is enabled. */
    private void setupValidation() {
        TextInputEditText[] all = {firstNameEdit, lastNameEdit, middleNameEdit, mobileEdit, emailEdit, passwordEdit, confirmPasswordEdit};
        for (TextInputEditText field : all) {
            field.addTextChangedListener(new SimpleTextWatcher(() -> {
                TextInputLayout layout = layoutOf(field);
                if (layout != null) layout.setError(null);
                refreshNext();
            }));
            field.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) validateOnBlur(field);
            });
        }
        // the address controller owns these fields: watch them so Next follows the address as it is filled in
        int[] addressFields = {R.id.addrCountryField, R.id.addrRegionField, R.id.addrProvinceField, R.id.addrCityField, R.id.addrBarangayField,
                R.id.addrStreetField, R.id.addrZipField, R.id.addrTimezoneField};
        for (int id : addressFields) {
            TextView field = findViewById(id);
            if (field != null) field.addTextChangedListener(new SimpleTextWatcher(this::refreshNext));
        }
        TextView zip = findViewById(R.id.addrZipField);
        zip.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && zip.getText().length() > 0 && !zip.getText().toString().matches("\\d{4}")) {
                TextInputLayout layout = findViewById(R.id.addrZipLayout);
                layout.setError(getString(R.string.zip_invalid));
            }
        });
    }

    private void setupPasswordRules() {
        passwordEdit.addTextChangedListener(new SimpleTextWatcher(this::updatePasswordRules));
        confirmPasswordEdit.addTextChangedListener(new SimpleTextWatcher(this::updatePasswordRules));
        updatePasswordRules();
    }

    private void updatePasswordRules() {
        String p = textOf(passwordEdit, false);
        String c = textOf(confirmPasswordEdit, false);
        boolean[] met = {PasswordRules.hasMinLength(p), PasswordRules.hasLetter(p), PasswordRules.hasNumber(p), PasswordRules.matches(p, c)};
        int[] labels = {R.string.reg_rule_length, R.string.reg_rule_letter, R.string.reg_rule_number, R.string.reg_rule_match};
        for (int i = 0; i < rules.length; i++) {
            rules[i].setText((met[i] ? "✓ " : "○ ") + getString(labels[i]));
            rules[i].setTextColor(ContextCompat.getColor(this, met[i] ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
            rules[i].setTypeface(null, met[i] ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        int level = PasswordRules.strength(p);
        strengthBar.setProgress(level);
        int[] colors = {R.color.velocity_divider_hairline, R.color.velocity_action_text, R.color.velocity_orange_primary, R.color.velocity_gold_primary, R.color.velocity_green_primary};
        strengthBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, colors[level])));
        strengthBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.velocity_divider_hairline)));
        int[] words = {R.string.reg_strength_none, R.string.reg_strength_1, R.string.reg_strength_2, R.string.reg_strength_3, R.string.reg_strength_4};
        strengthText.setText(words[level]);
        refreshNext();
    }

    // ---------------------------------------------------------------- date of birth and age

    private LocalDate today() {
        return LocalDate.now(MANILA);
    }

    private void openDobPicker() {
        LocalDate start = dob != null ? dob : today().minusYears(AgeRules.MIN_AGE);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            dob = LocalDate.of(y, m + 1, d);
            updateDobUi();
            dobLayout.setError(null);
            refreshNext();
        }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth());
        // no date of birth in the future (the 18+ rule is explained under the field, so an under-age date can still be chosen and read)
        dialog.getDatePicker().setMaxDate(today().minusDays(1).atStartOfDay(MANILA).toInstant().toEpochMilli());
        SafeDialogs.show(dialog);
    }

    private void updateDobUi() {
        if (dob == null) {
            dobEdit.setText("");
            ageText.setText(R.string.reg_age_empty);
            ageErrorText.setVisibility(View.GONE);
            return;
        }
        dobEdit.setText(DOB_SHOWN.format(dob));
        int age = AgeRules.ageOn(dob, today());
        ageText.setText(getString(R.string.reg_age_format, age));
        ageErrorText.setVisibility(age < AgeRules.MIN_AGE ? View.VISIBLE : View.GONE);
    }

    /** What the date picker does, for tests (a system dialog cannot be driven from a unit test). */
    @androidx.annotation.VisibleForTesting
    public void setDateOfBirthForTest(@NonNull LocalDate date) {
        dob = date;
        updateDobUi();
        refreshNext();
    }

    /** Declares the address filled in (the live region/province/city lookups need the network, which a unit test does not have). */
    @androidx.annotation.VisibleForTesting
    public void markAddressCompleteForTest() {
        addressController.setCompleteForTest(true);
        refreshNext();
    }

    /** Opens the Terms and ticks the box (the Terms dialog must be scrolled to the end on a phone). */
    @androidx.annotation.VisibleForTesting
    public void acceptTermsForTest() {
        termsConsent.restore(true, true);
        refreshNext();
    }

    @androidx.annotation.VisibleForTesting
    public int currentStepForTest() {
        return phase == PHASE_FORM ? currentStep : 100 + phase;
    }

    // ---------------------------------------------------------------- step rules

    private String textOf(TextInputEditText et, boolean trim) {
        if (et == null || et.getText() == null) return "";
        return trim ? et.getText().toString().trim() : et.getText().toString();
    }

    private String textOf(TextInputEditText et) {
        return textOf(et, true);
    }

    private String gender() {
        return genderDropdown.getText() != null ? genderDropdown.getText().toString().trim() : "";
    }

    private boolean stepValid(int step) {
        switch (step) {
            case 1:
                return !textOf(firstNameEdit).isEmpty() && !textOf(lastNameEdit).isEmpty();
            case 2:
                return !gender().isEmpty() && dob != null && AgeRules.isAdult(dob, today());
            case 3:
                return EmailValidator.isValid(textOf(emailEdit)) && PhoneNumberValidator.isValid(addressController.getSelectedCountry(), textOf(mobileEdit));
            case 4:
                return addressController.isComplete();
            case 5:
                return PasswordRules.isValid(textOf(passwordEdit, false)) && PasswordRules.matches(textOf(passwordEdit, false), textOf(confirmPasswordEdit, false));
            case 6:
                return termsConsent.isViewed() && termsConsent.isAccepted();
            default:
                return true;
        }
    }

    @StringRes
    private int reasonFor(int step) {
        switch (step) {
            case 1: return R.string.reg_reason_1;
            case 2: return R.string.reg_reason_2;
            case 3: return R.string.reg_reason_3;
            case 4: return R.string.reg_reason_4;
            case 5: return R.string.reg_reason_5;
            default: return TermsGate.missingHintRes(termsConsent.isViewed(), termsConsent.isAccepted());
        }
    }

    /** Enables the pinned button for the current step, or says in plain words what is still missing. */
    private void refreshNext() {
        if (phase != PHASE_FORM || nextButton == null || termsConsent == null || addressController == null) return;
        boolean valid = stepValid(currentStep);
        MaterialButton primary = currentStep == STEPS ? signUpButton : nextButton;
        primary.setEnabled(valid && !requestInFlight);
        int reason = valid ? 0 : reasonFor(currentStep);
        nextReason.setVisibility(reason == 0 ? View.GONE : View.VISIBLE);
        if (reason != 0) nextReason.setText(reason);
    }

    private void validateOnBlur(TextInputEditText field) {
        TextInputLayout layout = layoutOf(field);
        if (layout == null) return;
        String error = null;
        if (field == firstNameEdit) {
            if (textOf(field).isEmpty()) error = getString(R.string.first_name_required);
        } else if (field == lastNameEdit) {
            if (textOf(field).isEmpty()) error = getString(R.string.last_name_required);
        } else if (field == emailEdit) {
            if (!EmailValidator.isValid(textOf(field))) error = getString(R.string.valid_email_required);
        } else if (field == mobileEdit) {
            String country = addressController.getSelectedCountry();
            if (!PhoneNumberValidator.isValid(country, textOf(field))) error = PhoneNumberValidator.errorMessage(this, country);
        } else if (field == passwordEdit) {
            if (!PasswordRules.isValid(textOf(field, false))) error = getString(R.string.password_min_length);
        } else if (field == confirmPasswordEdit) {
            if (!textOf(field, false).isEmpty() && !PasswordRules.matches(textOf(passwordEdit, false), textOf(field, false))) error = getString(R.string.passwords_do_not_match);
        }
        layout.setError(error);
    }

    @Nullable
    private TextInputLayout layoutOf(View field) {
        android.view.ViewParent parent = field.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) parent = parent.getParent();
        return (TextInputLayout) parent;
    }

    // ---------------------------------------------------------------- navigation

    private void goNext() {
        if (phase != PHASE_FORM || currentStep >= STEPS || !stepValid(currentStep)) return;
        showStep(currentStep + 1, true);
    }

    private void showStep(int step, boolean forward) {
        View from = stepLayouts[currentStep];
        currentStep = step;
        render(true);
        if (WelcomeEffects.enabled(this)) {
            View to = stepLayouts[currentStep];
            to.setAlpha(0f);
            to.setTranslationX((forward ? 24 : -24) * getResources().getDisplayMetrics().density);
            to.animate().alpha(1f).translationX(0f).setDuration(220L).start();
        }
        scroll.scrollTo(0, 0);
    }

    private void handleBack() {
        if (requestInFlight) return;
        if (phase == PHASE_SUCCESS) {
            continueIntoApp();
        } else if (phase == PHASE_VERIFY) {
            if (verifyOnly) {
                finish();
            } else {
                phase = PHASE_FORM;
                currentStep = STEPS;
                cancelResend();
                render(true);
            }
        } else if (currentStep > 1) {
            showStep(currentStep - 1, false);
        } else {
            confirmLeave(this::goToWelcome);
        }
    }

    /** Leaving with something typed asks first; with nothing typed it just leaves. */
    private void confirmLeave(Runnable leave) {
        if (!hasAnyInput()) {
            leave.run();
            return;
        }
        new SafeDialogs.Material(this)
                .setTitle(R.string.reg_discard_title)
                .setMessage(R.string.reg_discard_message)
                .setPositiveButton(R.string.reg_discard_yes, (d, w) -> leave.run())
                .setNegativeButton(R.string.reg_discard_no, null)
                .show();
    }

    private boolean hasAnyInput() {
        return !textOf(firstNameEdit).isEmpty() || !textOf(lastNameEdit).isEmpty() || !textOf(middleNameEdit).isEmpty() || !gender().isEmpty() || dob != null
                || !textOf(emailEdit).isEmpty() || !textOf(mobileEdit).isEmpty() || !textOf(passwordEdit, false).isEmpty() || selectedProfileBitmap != null;
    }

    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void changeEmail() {
        if (requestInFlight) return;
        phase = PHASE_FORM;
        currentStep = verifyOnly ? 1 : 3;
        verifyOnly = false;
        cancelResend();
        render(true);
        emailEdit.requestFocus();
    }

    // ---------------------------------------------------------------- drawing the current screen

    /** Shows the right screen (a step, the verification or the success screen) with its header line, progress and buttons. */
    private void render(boolean animateProgress) {
        for (int i = 1; i <= STEPS; i++) stepLayouts[i].setVisibility(phase == PHASE_FORM && i == currentStep ? View.VISIBLE : View.GONE);
        otpLayout.setVisibility(phase == PHASE_VERIFY ? View.VISIBLE : View.GONE);
        successLayout.setVisibility(phase == PHASE_SUCCESS ? View.VISIBLE : View.GONE);
        loginRedirect.setVisibility(phase == PHASE_FORM && currentStep == 1 ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(phase == PHASE_FORM ? View.VISIBLE : View.GONE);
        findViewById(R.id.bottomRule).setVisibility(phase == PHASE_FORM ? View.VISIBLE : View.GONE);
        boolean motion = animateProgress && WelcomeEffects.enabled(this);
        if (phase == PHASE_FORM) {
            int[] names = {0, R.string.reg_step_name_1, R.string.reg_step_name_2, R.string.reg_step_name_3, R.string.reg_step_name_4, R.string.reg_step_name_5, R.string.reg_step_name_6};
            stepText.setText(getString(R.string.wizard_step_x_of_y_title_format, currentStep, STEPS, getString(names[currentStep])));
            progressBar.setProgressCompat(Math.round(100f * currentStep / STEPS), motion);
            previousButton.setVisibility(currentStep > 1 ? View.VISIBLE : View.INVISIBLE);
            nextButton.setVisibility(currentStep < STEPS ? View.VISIBLE : View.GONE);
            signUpButton.setVisibility(currentStep == STEPS ? View.VISIBLE : View.GONE);
            ((ViewGroup.MarginLayoutParams) previousButton.getLayoutParams()).setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
            if (currentStep == 6) buildReview();
            updateDobUi();
            ((TextView) findViewById(R.id.regPhotoHint)).setText(R.string.reg_photo_hint);
            refreshNext();
        } else if (phase == PHASE_VERIFY) {
            stepText.setText(R.string.reg_step_verify);
            progressBar.setProgressCompat(100, motion);
            showVerify();
        } else {
            stepText.setText(R.string.reg_step_done);
            progressBar.setProgressCompat(100, motion);
        }
    }

    private void buildReview() {
        LinearLayout box = findViewById(R.id.reviewSummary);
        box.removeAllViews();
        addReviewSection(box, R.string.reg_review_name, 1, fullName());
        addReviewSection(box, R.string.reg_review_about, 2, gender() + " · " + (dob != null ? DOB_SHOWN.format(dob) + " · " + getString(R.string.reg_age_format, AgeRules.ageOn(dob, today())) : ""));
        addReviewSection(box, R.string.reg_review_contact, 3, textOf(emailEdit) + "\n" + textOf(mobileEdit));
        addReviewSection(box, R.string.reg_review_address, 4, addressController.getComposedAddress());
        addReviewSection(box, R.string.reg_review_password, 5, getString(R.string.reg_review_password_value,
                getString(selectedProfileBitmap != null ? R.string.reg_review_photo_yes : R.string.reg_review_photo_no)));
    }

    private String fullName() {
        StringBuilder sb = new StringBuilder(textOf(firstNameEdit));
        if (!textOf(middleNameEdit).isEmpty()) sb.append(' ').append(textOf(middleNameEdit));
        sb.append(' ').append(textOf(lastNameEdit));
        return sb.toString();
    }

    private void addReviewSection(LinearLayout box, @StringRes int title, int editStep, String value) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_flat_box);
        row.setPadding(Math.round(12 * d), Math.round(8 * d), Math.round(12 * d), Math.round(10 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8 * d);
        row.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_secondary));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(t);
        TextView edit = new TextView(this);
        edit.setText(R.string.reg_edit);
        edit.setTextSize(14f);
        edit.setTypeface(null, android.graphics.Typeface.BOLD);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setMinHeight(Math.round(48 * d));
        edit.setPadding(Math.round(8 * d), 0, 0, 0);
        edit.setTextColor(ContextCompat.getColor(this, R.color.velocity_action_text));
        edit.setContentDescription(getString(R.string.reg_edit) + " " + getString(title));
        edit.setOnClickListener(v -> {
            if (phase == PHASE_FORM) showStep(editStep, false);
        });
        head.addView(edit);
        row.addView(head);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14f);
        v.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_primary));
        row.addView(v);
        box.addView(row);
    }

    // ---------------------------------------------------------------- create the account

    private void confirmAndRegister() {
        if (requestInFlight || phase != PHASE_FORM || !stepValid(STEPS)) return;
        new SafeDialogs.Material(this)
                .setTitle(R.string.reg_confirm_title)
                .setMessage(getString(R.string.reg_confirm_message, textOf(emailEdit)))
                .setPositiveButton(R.string.reg_create_account, (d, w) -> register())
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void register() {
        if (requestInFlight) return;
        RegisterRequest request = new RegisterRequest();
        request.first_name = textOf(firstNameEdit);
        request.last_name = textOf(lastNameEdit);
        request.middle_name = textOf(middleNameEdit);
        request.email = textOf(emailEdit);
        request.password = textOf(passwordEdit, false);
        request.password_confirmation = textOf(confirmPasswordEdit, false);
        request.mobile_number = textOf(mobileEdit);
        request.address = addressController.getComposedAddress();
        AddressSelection address = addressController.getStructuredValues();
        request.country = address.country;
        request.region = address.region;
        request.province = address.province;
        request.city = address.city;
        request.barangay = address.barangay;
        request.street = address.street;
        request.zip_code = address.zip;
        request.timezone = addressController.getResolvedTimezone();
        request.otp_channel = "email";
        request.gender = mapGender(gender());
        request.date_of_birth = dob != null ? dob.toString() : "";
        request.age = dob != null ? AgeRules.ageOn(dob, today()) : 0;

        regSubmitError.setVisibility(View.GONE);
        setInFlight(true);
        signUpButton.setText(R.string.sending_code);
        ApiClient.getService(this).register(request).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                signUpButton.setText(R.string.reg_create_account);
                setInFlight(false);
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    verifyEmail = textOf(emailEdit);
                    phase = PHASE_VERIFY;
                    resendUntil = android.os.SystemClock.elapsedRealtime() + RESEND_COOLDOWN_MS;
                    render(true);
                } else {
                    showSubmitError(ApiErrorParser.extractMessage(response, getString(R.string.reg_submit_failed)));
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                signUpButton.setText(R.string.reg_create_account);
                setInFlight(false);
                if (isFinishing() || isDestroyed()) return;
                showSubmitError(RoomRepository.networkErrorMessage(RegistrationActivity.this, t));
            }
        });
    }

    private void showSubmitError(String message) {
        regSubmitError.setText(message);
        regSubmitError.setVisibility(View.VISIBLE);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setInFlight(boolean inFlight) {
        requestInFlight = inFlight;
        if (inFlight) signUpButton.setEnabled(false);
        else refreshNext();
    }

    private String mapGender(String shown) {
        if ("Male".equals(shown)) return "male";
        if ("Female".equals(shown)) return "female";
        return "other";
    }

    // ---------------------------------------------------------------- verify the code

    private void showVerify() {
        otpDesc.setText(getString(R.string.reg_verify_desc, OtpBoxes.maskEmail(verifyEmail)));
        LinearLayout container = findViewById(R.id.otpBoxesContainer);
        otpBoxes = new OtpBoxes(container, 6, (code, complete) -> {
            otpErrorText.setVisibility(View.GONE);
            otpBoxes.setError(false);
            if (complete) verifyOtp(code);
        });
        otpErrorText.setVisibility(View.GONE);
        otpVerifyingText.setVisibility(View.GONE);
        startResendCooldown(Math.max(0, resendUntil - android.os.SystemClock.elapsedRealtime()));
        otpBoxes.focusFirst();
    }

    private void verifyOtp(String code) {
        if (requestInFlight) return;
        setInFlight(true);
        otpBoxes.setEnabled(false);
        otpVerifyingText.setVisibility(View.VISIBLE);
        ApiClient.getService(this).verifyOtp(new VerifyOtpRequest(verifyEmail, code)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                requestInFlight = false;
                otpVerifyingText.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                    AuthResponse auth = response.body();
                    String fullName = auth.user.full_name != null ? auth.user.full_name : (auth.user.first_name + " " + auth.user.last_name);
                    String mobile = auth.user.guest != null && auth.user.guest.mobile_number != null ? auth.user.guest.mobile_number : textOf(mobileEdit);
                    String dobText = auth.user.guest != null && auth.user.guest.date_of_birth != null ? auth.user.guest.date_of_birth : (dob != null ? dob.toString() : "");
                    String genderText = auth.user.guest != null && auth.user.guest.gender != null ? auth.user.guest.gender : mapGender(gender());
                    SessionManager.saveSession(RegistrationActivity.this, auth.token, auth.user.id, auth.user.first_name, auth.user.last_name,
                            auth.user.middle_name, fullName, auth.user.email, mobile, genderText, dobText,
                            auth.user.guest != null ? auth.user.guest.profile_picture_url : null);
                    phase = PHASE_SUCCESS;
                    cancelResend();
                    render(true);
                } else {
                    otpBoxes.setEnabled(true);
                    otpBoxes.clear(); // empty boxes for another try (clearing also hides any old message, so it comes first)
                    otpBoxes.setError(true);
                    otpErrorText.setText(response.code() == 422 || response.code() == 400 ? getString(R.string.reg_code_wrong)
                            : ApiErrorParser.extractMessage(response, getString(R.string.reg_code_wrong)));
                    otpErrorText.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                requestInFlight = false;
                otpVerifyingText.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                otpBoxes.setEnabled(true);
                otpErrorText.setText(RoomRepository.networkErrorMessage(RegistrationActivity.this, t));
                otpErrorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void resendOtp() {
        if (requestInFlight || android.os.SystemClock.elapsedRealtime() < resendUntil) return;
        resendOtpLink.setEnabled(false);
        ApiClient.getService(this).resendOtp(new EmailRequest(verifyEmail)).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    resendUntil = android.os.SystemClock.elapsedRealtime() + RESEND_COOLDOWN_MS;
                    startResendCooldown(RESEND_COOLDOWN_MS);
                    otpErrorText.setVisibility(View.GONE);
                    otpBoxes.clear();
                } else {
                    resendOtpLink.setEnabled(true);
                    otpErrorText.setText(ApiErrorParser.extractMessage(response, getString(R.string.reg_submit_failed)));
                    otpErrorText.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                resendOtpLink.setEnabled(true);
                otpErrorText.setText(RoomRepository.networkErrorMessage(RegistrationActivity.this, t));
                otpErrorText.setVisibility(View.VISIBLE);
            }
        });
    }

    /** "Resend code in 57s" while locked, "Resend code" once it can be used again. */
    private void startResendCooldown(long millis) {
        cancelResend();
        if (millis <= 0) {
            resendOtpLink.setText(R.string.reg_resend_code);
            resendOtpLink.setEnabled(true);
            return;
        }
        resendOtpLink.setEnabled(false);
        resendOtpLink.setText(getString(R.string.resend_otp_countdown, (int) ((millis + 999) / 1000)));
        resendTimer = new android.os.CountDownTimer(millis, 1000L) {
            @Override
            public void onTick(long left) {
                resendOtpLink.setText(getString(R.string.resend_otp_countdown, (int) ((left + 999) / 1000)));
            }

            @Override
            public void onFinish() {
                resendOtpLink.setText(R.string.reg_resend_code);
                resendOtpLink.setEnabled(true);
            }
        }.start();
    }

    private void cancelResend() {
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
    }

    // ---------------------------------------------------------------- success

    private void continueIntoApp() {
        if (continuing) return;
        continuing = true;
        MaterialButton button = findViewById(R.id.successContinueButton);
        button.setEnabled(false);
        button.setText(R.string.reg_please_wait);
        String fullName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userName", "");
        uploadProfilePictureThenProceed(() -> {
            startActivity(PendingRoomSelection.createPostAuthIntent(RegistrationActivity.this, fullName));
            finish();
        });
    }

    // ---------------------------------------------------------------- profile picture

    private void selectProfilePicture() {
        String[] options = {getString(R.string.take_photo), getString(R.string.choose_from_gallery)};
        new SafeDialogs.Material(this)
                .setTitle(R.string.select_image_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) checkPermissionAndCamera();
                    else openGallery();
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
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST);
    }

    private void openGallery() {
        startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            else Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_IMAGE_REQUEST) {
            android.net.Uri selected = data.getData();
            if (selected != null) {
                BackgroundWork.run(this, () -> ImageDecoding.decode(getContentResolver(), selected, ImageDecoding.DEFAULT_MAX_EDGE), bitmap -> {
                    if (bitmap != null) setSelectedProfilePicture(bitmap);
                    else Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                });
            }
        } else if (requestCode == CAPTURE_IMAGE_REQUEST && data.getExtras() != null) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            if (bitmap != null) setSelectedProfilePicture(bitmap);
        }
    }

    private void setSelectedProfilePicture(Bitmap bitmap) {
        selectedProfileBitmap = bitmap;
        regProfileImage.setImageBitmap(bitmap);
        regPhotoStatusText.setText(R.string.profile_picture_selected);
        regRemovePhotoLink.setVisibility(View.VISIBLE);
    }

    private void clearSelectedProfilePicture() {
        selectedProfileBitmap = null;
        regProfileImage.setImageResource(R.drawable.img_profile_placeholder);
        regPhotoStatusText.setText(R.string.profile_picture_none_selected);
        regRemovePhotoLink.setVisibility(View.GONE);
    }

    /** Uploads the photo now that a real token exists; a failed upload never blocks the new account (it can be added later). */
    private void uploadProfilePictureThenProceed(Runnable proceed) {
        if (selectedProfileBitmap == null) {
            proceed.run();
            return;
        }
        final Bitmap toUpload = selectedProfileBitmap;
        BackgroundWork.run(this, () -> {
            File file = File.createTempFile("profile_", ".jpg", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(file)) {
                toUpload.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            return file;
        }, file -> {
            if (file == null) proceed.run();
            else uploadProfileFile(file, proceed);
        });
    }

    private void uploadProfileFile(File tempFile, Runnable proceed) {
        RequestBody fileBody = RequestBody.create(tempFile, MediaType.parse("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("profile_picture", tempFile.getName(), fileBody);
        ApiClient.getService(this).updateProfilePicture(part).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                tempFile.delete();
                if (response.isSuccessful() && response.body() != null && response.body().guest != null) {
                    SessionManager.setProfilePictureUrl(RegistrationActivity.this, response.body().guest.profile_picture_url);
                }
                proceed.run();
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                tempFile.delete();
                proceed.run();
            }
        });
    }

    // ---------------------------------------------------------------- rotation and process death

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_STEP, currentStep);
        out.putInt(STATE_PHASE, phase);
        out.putString(STATE_DOB, dob != null ? dob.toString() : null);
        out.putString(STATE_VERIFY_EMAIL, verifyEmail);
        out.putBoolean(STATE_VERIFY_ONLY, verifyOnly);
        out.putLong(STATE_RESEND_UNTIL, resendUntil);
        if (addressController != null) {
            AddressSelection a = addressController.getStructuredValues();
            out.putStringArray(STATE_ADDR, new String[]{a.country, a.region, a.province, a.city, a.barangay, a.street, a.zip, a.timezone});
        }
    }

    private void restoreState(Bundle in) {
        currentStep = Math.max(1, Math.min(STEPS, in.getInt(STATE_STEP, 1)));
        phase = in.getInt(STATE_PHASE, PHASE_FORM);
        String iso = in.getString(STATE_DOB);
        dob = iso != null ? LocalDate.parse(iso) : null;
        verifyEmail = in.getString(STATE_VERIFY_EMAIL, "");
        verifyOnly = in.getBoolean(STATE_VERIFY_ONLY, false);
        resendUntil = in.getLong(STATE_RESEND_UNTIL, 0L);
        String[] a = in.getStringArray(STATE_ADDR);
        if (a != null && a.length == 8) {
            AddressSelection saved = new AddressSelection();
            saved.country = a[0];
            saved.region = a[1];
            saved.province = a[2];
            saved.city = a[3];
            saved.barangay = a[4];
            saved.street = a[5];
            saved.zip = a[6];
            saved.timezone = a[7];
            addressController.populateFrom(saved);
        }
    }

    @Override
    protected void onDestroy() {
        cancelResend();
        if (addressController != null) addressController.dispose();
        super.onDestroy();
    }
}
