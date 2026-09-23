package com.example.velocitysuites;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistrationActivity extends AppCompatActivity {
    private TextInputEditText firstNameEdit, lastNameEdit, middleNameEdit, ageEdit, dobEdit, mobileEdit, emailEdit, passwordEdit, confirmPasswordEdit, otpEdit;
    private AutoCompleteTextView genderDropdown;
    private View step1Layout, step2Layout, step3Layout, otpLayout;
    private MaterialButton nextButton, signUpButton;
    private TextView stepText, resendOtpLink;
    private View registrationLabel;
    private View stepProgressBar;
    private View stepDot1, stepDot2, stepDot3, stepDot4;
    private View headerSection, registrationCard, loginRedirect, stepIndicatorContainer;
    private Animation flipLeft, fadeInSlideUp;
    private View viewTermsButton;
    private MaterialCheckBox termsCheckBox;
    private TextView termsStatusText;
    private AddressHierarchyController addressController;
    private View ageValidationErrorContainer;
    private TextInputLayout mobileInputLayout;
    private ImageView regProfileImage;
    private View regSelectPhotoButton;
    private TextView regPhotoStatusText, regRemovePhotoLink;

    private static final int MIN_REGISTRATION_AGE = 18;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private int currentStep = 1;
    private boolean isOtpStep = false;
    private boolean termsViewed = false;
    /** Picked during registration but not uploaded until after OTP verification,
     *  since the profile-picture endpoint requires the account/token that only
     *  exists once verifyOtp() succeeds. Null means the guest skipped it. */
    private Bitmap selectedProfileBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration);

        initializeViews();
        setupGenderDropdown();
        setupDatePicker();
        addressController = AddressHierarchyController.attach(this, step3Layout);
        addressController.setOnCountryChangeListener(this::updateMobileHelperText);
        updateStepUI();
        startIntroAnimations();

        nextButton.setOnClickListener(v -> handleNextStep());
        signUpButton.setOnClickListener(v -> {
            if (isOtpStep) {
                verifyOtp();
            } else {
                handleRegistration();
            }
        });

        resendOtpLink.setOnClickListener(v -> resendOtp());

        viewTermsButton.setOnClickListener(v -> showTermsDialog());
        termsCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isOtpStep && currentStep == 3) signUpButton.setEnabled(isChecked);
        });

        regSelectPhotoButton.setOnClickListener(v -> selectProfilePicture());
        regRemovePhotoLink.setOnClickListener(v -> clearSelectedProfilePicture());

        findViewById(R.id.signInLink).setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // Single unified Back control for all 4 steps: floating button, and hardware/gesture
        // back, both funnel through handleBack() so Step 4->3->2->1->Welcome is consistent.
        findViewById(R.id.btnBack).setOnClickListener(v -> handleBack());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
    }

    private void initializeViews() {
        firstNameEdit = findViewById(R.id.firstNameEditText);
        lastNameEdit = findViewById(R.id.lastNameEditText);
        middleNameEdit = findViewById(R.id.middleNameEditText);
        ageEdit = findViewById(R.id.ageEditText);
        dobEdit = findViewById(R.id.dobEditText);
        mobileEdit = findViewById(R.id.mobileEditText);
        mobileInputLayout = findViewById(R.id.mobileInputLayout);
        emailEdit = findViewById(R.id.emailEditText);
        passwordEdit = findViewById(R.id.passwordEditText);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEditText);
        otpEdit = findViewById(R.id.otpEditText);
        genderDropdown = findViewById(R.id.genderDropdown);
        viewTermsButton = findViewById(R.id.viewTermsButton);
        termsCheckBox = findViewById(R.id.termsCheckBox);
        termsStatusText = findViewById(R.id.termsStatusText);
        ageValidationErrorContainer = findViewById(R.id.ageValidationErrorContainer);
        regProfileImage = findViewById(R.id.regProfileImage);
        regSelectPhotoButton = findViewById(R.id.regSelectPhotoButton);
        regPhotoStatusText = findViewById(R.id.regPhotoStatusText);
        regRemovePhotoLink = findViewById(R.id.regRemovePhotoLink);
        step1Layout = findViewById(R.id.step1Layout);
        step2Layout = findViewById(R.id.step2Layout);
        step3Layout = findViewById(R.id.step3Layout);
        otpLayout = findViewById(R.id.otpLayout);

        nextButton = findViewById(R.id.nextButton);
        signUpButton = findViewById(R.id.signUpButton);
        resendOtpLink = findViewById(R.id.resendOtpLink);

        stepText = findViewById(R.id.stepText);
        registrationLabel = findViewById(R.id.registrationLabel);
        stepProgressBar = findViewById(R.id.stepProgressBar);
        stepDot1 = findViewById(R.id.stepDot1);
        stepDot2 = findViewById(R.id.stepDot2);
        stepDot3 = findViewById(R.id.stepDot3);
        stepDot4 = findViewById(R.id.stepDot4);

        headerSection = findViewById(R.id.headerSection);
        registrationCard = findViewById(R.id.registrationCard);
        loginRedirect = findViewById(R.id.loginRedirect);
        stepIndicatorContainer = findViewById(R.id.stepIndicatorContainer);

        fadeInSlideUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up);
        flipLeft = AnimationUtils.loadAnimation(this, R.anim.flip_left);
    }

    private void setupGenderDropdown() {
        String[] genders = new String[]{"Male", "Female"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genders);
        genderDropdown.setAdapter(adapter);
        genderDropdown.setOnClickListener(v -> genderDropdown.showDropDown());
    }

    private void setupDatePicker() {
        dobEdit.setOnClickListener(v -> openDobPicker());

        // Live 18+ check: reacts to the age auto-filled whenever a DOB is picked, so the
        // Step 2 gate reflects the age the instant it changes. Age itself is read-only -
        // Date of Birth is the only source of truth for it.
        ageEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateAgeValidationUI();
            }
        });
    }

    /**
     * Shows/hides the "must be 18+" banner and, while on Step 2, disables the Next
     * button whenever the current age value is a valid but underage number. An
     * empty or unparsable age is left to the on-submit required-field check instead.
     */
    private void updateAgeValidationUI() {
        if (ageValidationErrorContainer == null) return;
        Integer age = parseValidAge(textOf(ageEdit));
        boolean underage = age != null && age < MIN_REGISTRATION_AGE;
        ageValidationErrorContainer.setVisibility(underage ? View.VISIBLE : View.GONE);
        if (currentStep == 2 && !isOtpStep && nextButton != null) {
            nextButton.setEnabled(!underage);
        }
    }

    /**
     * Opens the DOB picker, always capped at today so a future date can never be selected.
     * Reopens at the previously chosen date when one exists; otherwise defaults to today.
     */
    private void openDobPicker() {
        Calendar initial = Calendar.getInstance();
        String existingDob = textOf(dobEdit);
        if (!TextUtils.isEmpty(existingDob)) {
            try {
                Date parsed = new SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(existingDob);
                if (parsed != null) initial.setTime(parsed);
            } catch (ParseException ignored) {
            }
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    dobEdit.setText(String.format(Locale.US, "%02d/%02d/%d", monthOfYear + 1, dayOfMonth, year1));
                    ageEdit.setText(String.valueOf(calculateAge(year1, monthOfYear + 1, dayOfMonth)));
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

    /**
     * Shows the Terms and Agreement as a full-screen dialog. The agreement
     * checkbox stays disabled until the user scrolls the text to the bottom,
     * which is what actually flips {@link #termsViewed}.
     */
    private void showTermsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_terms_agreement, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        NestedScrollView scrollView = dialogView.findViewById(R.id.termsScrollView);
        TextView scrollHintText = dialogView.findViewById(R.id.termsScrollHintText);
        ImageView scrollHintIcon = dialogView.findViewById(R.id.termsScrollHintIcon);
        View closeButton = dialogView.findViewById(R.id.termsCloseButton);

        closeButton.setOnClickListener(v -> dialog.dismiss());

        Runnable markViewedIfAtBottom = () -> {
            View content = scrollView.getChildAt(0);
            if (content == null) return;
            boolean atBottom = scrollView.getScrollY() + scrollView.getHeight() >= content.getHeight() - 8;
            if (atBottom && !termsViewed) {
                termsViewed = true;
                termsCheckBox.setEnabled(true);
                termsStatusText.setText(R.string.terms_scroll_complete);
                scrollHintText.setText(R.string.terms_scroll_complete);
                scrollHintIcon.setImageResource(R.drawable.ic_check_circle);
            }
        };

        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> markViewedIfAtBottom.run());
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                markViewedIfAtBottom.run();
                scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /** Optional profile picture: same take-photo/choose-from-gallery chooser as
     *  {@link ProfileManagementActivity}, but the result is only held locally
     *  (uploaded post-verification, see {@link #verifyOtp()}). */
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
                android.net.Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                        setSelectedProfilePicture(bitmap);
                    } catch (IOException e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == CAPTURE_IMAGE_REQUEST && data.getExtras() != null) {
                Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                if (imageBitmap != null) setSelectedProfilePicture(imageBitmap);
            }
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

    /**
     * Keeps the mobile field's helper text in sync with the selected
     * country so the expected format is always visible, matching whichever
     * pattern validateFinalStep() will actually check.
     */
    private void updateMobileHelperText(boolean isPhilippines) {
        mobileInputLayout.setHelperText(getString(isPhilippines
                ? R.string.helper_mobile_format : R.string.helper_mobile_format_intl));
    }

    /**
     * Uploads the locally-picked photo now that a real bearer token exists
     * (post-OTP-verification), then proceeds regardless of outcome - a
     * failed picture upload shouldn't block account creation/login since the
     * photo was always optional and can be added later from Profile.
     */
    private void uploadProfilePictureThenProceed(Runnable proceed) {
        if (selectedProfileBitmap == null) {
            proceed.run();
            return;
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("profile_", ".jpg", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                selectedProfileBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
        } catch (IOException e) {
            proceed.run();
            return;
        }

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

    private void handleNextStep() {
        if (validateCurrentStep()) {
            animateStepOut(true, () -> {
                currentStep++;
                updateStepUI();
                animateStepIn(true);
            });
        }
    }

    /**
     * Single Back handler for the floating button, and hardware/gesture back: Step 4 (OTP) goes
     * to Step 3, Step 3->2->1 step back one at a time, and Step 1 exits to Welcome. All previously
     * entered fields are preserved since steps are just toggled visibility, never cleared.
     */
    private void handleBack() {
        if (isOtpStep) {
            animateStepOut(false, () -> {
                isOtpStep = false;
                currentStep = 3;
                updateStepUI();
                animateStepIn(false);
            });
        } else if (currentStep > 1) {
            animateStepOut(false, () -> {
                currentStep--;
                updateStepUI();
                animateStepIn(false);
            });
        } else {
            goToWelcome();
        }
    }

    private void updateStepUI() {
        // Reset visibility
        step1Layout.setVisibility(View.GONE);
        step2Layout.setVisibility(View.GONE);
        step3Layout.setVisibility(View.GONE);
        otpLayout.setVisibility(View.GONE);
        
        stepText.setText(getString(R.string.registration_step_format, currentStep, 4));

        // Update progress bar width proportionally (now for 4 steps)
        float density = getResources().getDisplayMetrics().density;
        float totalProgressWidth = 120 * density;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) stepProgressBar.getLayoutParams();
        params.width = (int) ((totalProgressWidth / 4) * currentStep);
        stepProgressBar.setLayoutParams(params);
        updateStepDots();

        if (isOtpStep) {
            otpLayout.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.GONE);
            signUpButton.setVisibility(View.VISIBLE);
            signUpButton.setText(R.string.verify_button);
            return;
        }

        switch (currentStep) {
            case 1:
                step1Layout.setVisibility(View.VISIBLE);
                nextButton.setVisibility(View.VISIBLE);
                nextButton.setEnabled(true);
                signUpButton.setVisibility(View.GONE);
                break;
            case 2:
                step2Layout.setVisibility(View.VISIBLE);
                nextButton.setVisibility(View.VISIBLE);
                signUpButton.setVisibility(View.GONE);
                updateAgeValidationUI();
                break;
            case 3:
                step3Layout.setVisibility(View.VISIBLE);
                nextButton.setVisibility(View.GONE);
                signUpButton.setVisibility(View.VISIBLE);
                signUpButton.setText(R.string.sign_up_now);
                signUpButton.setEnabled(termsCheckBox.isChecked());
                break;
        }
    }

    /** Fills in step dots up to (and including) the current step; the rest stay dim. */
    private void updateStepDots() {
        View[] dots = {stepDot1, stepDot2, stepDot3, stepDot4};
        int filledThrough = isOtpStep ? 4 : currentStep;
        for (int i = 0; i < dots.length; i++) {
            boolean filled = (i + 1) <= filledThrough;
            dots[i].setBackgroundTintList(getColorStateList(filled ? R.color.velocity_red_primary : R.color.velocity_red_soft));
        }
    }

    private void animateStepOut(boolean forward, Runnable onEnd) {
        View currentLayout = getCurrentStepLayout();
        float translationX = forward ? -100f : 100f;
        currentLayout.animate()
                .alpha(0f)
                .translationX(translationX)
                .setDuration(250)
                .withEndAction(() -> {
                    currentLayout.setTranslationX(0f);
                    currentLayout.setVisibility(View.GONE);
                    onEnd.run();
                })
                .start();
    }

    private void animateStepIn(boolean forward) {
        View currentLayout = getCurrentStepLayout();
        float startTranslationX = forward ? 100f : -100f;
        currentLayout.setVisibility(View.VISIBLE);
        currentLayout.setAlpha(0f);
        currentLayout.setTranslationX(startTranslationX);
        currentLayout.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .start();
    }

    private View getCurrentStepLayout() {
        if (isOtpStep) return otpLayout;
        switch (currentStep) {
            case 1: return step1Layout;
            case 2: return step2Layout;
            case 3: return step3Layout;
            default: return step1Layout;
        }
    }

    private boolean validateCurrentStep() {
        switch (currentStep) {
            case 1:
                if (isEmpty(firstNameEdit)) return showError(firstNameEdit, getString(R.string.first_name_required));
                if (isEmpty(lastNameEdit)) return showError(lastNameEdit, getString(R.string.last_name_required));
                return true;
            case 2:
                if (TextUtils.isEmpty(genderDropdown.getText())) {
                    genderDropdown.setError(getString(R.string.gender_required));
                    genderDropdown.requestFocus();
                    return false;
                }
                if (isEmpty(dobEdit)) return showError(dobEdit, getString(R.string.dob_required));

                Integer age = parseValidAge(textOf(ageEdit));
                if (age == null || age < MIN_REGISTRATION_AGE) {
                    ageValidationErrorContainer.setVisibility(View.VISIBLE);
                    return showError(dobEdit, getString(R.string.age_validation_error));
                }
                return true;
            case 3:
                return validateFinalStep();
            default:
                return true;
        }
    }

    private void handleRegistration() {
        if (!validateCurrentStep()) return;

        RegisterRequest request = new RegisterRequest();
        request.first_name = textOf(firstNameEdit);
        request.last_name = textOf(lastNameEdit);
        request.middle_name = textOf(middleNameEdit);
        request.email = textOf(emailEdit);
        request.password = textOf(passwordEdit);
        request.password_confirmation = textOf(confirmPasswordEdit);
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
        // Email is the only verification channel offered - see the
        // registration.xml OTP step (SMS/mobile delivery was never
        // configured server-side and has been removed from the UI).
        request.otp_channel = "email";
        request.gender = mapGender(genderDropdown.getText() != null ? genderDropdown.getText().toString() : "");
        request.date_of_birth = toApiDate(textOf(dobEdit));
        try {
            request.age = Integer.parseInt(textOf(ageEdit));
        } catch (NumberFormatException e) {
            request.age = 0;
        }

        signUpButton.setEnabled(false);
        ApiClient.getService(this).register(request).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                signUpButton.setEnabled(true);
                if (response.isSuccessful()) {
                    animateStepOut(true, () -> {
                        isOtpStep = true;
                        currentStep = 4;
                        updateStepUI();
                        animateStepIn(true);
                        Toast.makeText(RegistrationActivity.this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    String message = errorMessage(response);
                    Toast.makeText(RegistrationActivity.this, "Registration failed: " + message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                signUpButton.setEnabled(true);
                Toast.makeText(RegistrationActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifyOtp() {
        String otp = otpEdit.getText() != null ? otpEdit.getText().toString() : "";
        if (otp.length() != 6) {
            otpEdit.setError(getString(R.string.invalid_otp));
            return;
        }

        String email = textOf(emailEdit);
        signUpButton.setEnabled(false);
        ApiClient.getService(this).verifyOtp(new VerifyOtpRequest(email, otp)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                signUpButton.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    String fullName = auth.user.full_name != null ? auth.user.full_name
                            : (auth.user.first_name + " " + auth.user.last_name);
                    SessionManager.saveSession(RegistrationActivity.this, auth.token, auth.user.id,
                            auth.user.first_name, auth.user.last_name, auth.user.middle_name, fullName,
                            auth.user.email, textOf(mobileEdit), mapGender(genderDropdown.getText() != null ? genderDropdown.getText().toString() : ""),
                            textOf(dobEdit), auth.user.guest != null ? auth.user.guest.profile_picture_url : null);

                    Toast.makeText(RegistrationActivity.this, R.string.registration_success, Toast.LENGTH_LONG).show();

                    uploadProfilePictureThenProceed(() -> {
                        Intent intent = PendingRoomSelection.createPostAuthIntent(RegistrationActivity.this, fullName);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    otpEdit.setError(getString(R.string.invalid_otp));
                    Toast.makeText(RegistrationActivity.this, "Verification failed: " + errorMessage(response), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                signUpButton.setEnabled(true);
                Toast.makeText(RegistrationActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resendOtp() {
        ApiClient.getService(this).resendOtp(new EmailRequest(textOf(emailEdit))).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegistrationActivity.this, "A new OTP has been sent to your email.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RegistrationActivity.this, "Couldn't resend OTP: " + errorMessage(response), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                Toast.makeText(RegistrationActivity.this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private String textOf(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String mapGender(String displayGender) {
        if (displayGender == null) return "other";
        switch (displayGender) {
            case "Male": return "male";
            case "Female": return "female";
            default: return "other";
        }
    }

    /** Converts the dobEdit text ("MM/dd/yyyy") into the API's expected "YYYY-MM-DD". */
    private String toApiDate(String mmDdYyyy) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return output.format(input.parse(mmDdYyyy));
        } catch (ParseException | NullPointerException e) {
            return mmDdYyyy;
        }
    }

    private String errorMessage(Response<?> response) {
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
        return "please try again.";
    }

    private boolean validateFinalStep() {
        // Country drives the expected mobile format - see PhoneNumberValidator, shared with
        // ProfileManagementActivity and mirrored server-side by PhoneValidationService.
        String selectedCountry = addressController.getSelectedCountry();
        if (!PhoneNumberValidator.isValid(selectedCountry, textOf(mobileEdit)))
            return showError(mobileEdit, PhoneNumberValidator.errorMessage(this, selectedCountry));

        if (!addressController.validate())
            return false;

        if (!isValidEmail(emailEdit.getText()))
            return showError(emailEdit, getString(R.string.valid_email_required));
        
        if (isEmpty(passwordEdit) || (passwordEdit.getText() != null && passwordEdit.getText().length() < 8))
            return showError(passwordEdit, getString(R.string.password_min_length));
        
        if (passwordEdit.getText() != null && confirmPasswordEdit.getText() != null &&
            !passwordEdit.getText().toString().equals(confirmPasswordEdit.getText().toString())) {
            return showError(confirmPasswordEdit, getString(R.string.passwords_do_not_match));
        }

        // The signUpButton is already disabled until these are satisfied; these are
        // a defense-in-depth backup in case handleRegistration() is ever reached otherwise.
        if (!termsViewed) {
            Toast.makeText(this, R.string.msg_terms_not_viewed, Toast.LENGTH_LONG).show();
            return false;
        }
        if (!termsCheckBox.isChecked()) {
            Toast.makeText(this, R.string.msg_terms_not_checked, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private boolean isEmpty(TextInputEditText et) {
        return TextUtils.isEmpty(et.getText());
    }

    private boolean showError(TextInputEditText et, String message) {
        et.setError(message);
        et.requestFocus();
        return false;
    }

    private boolean isValidEmail(CharSequence target) {
        return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches()
                && target.toString().trim().toLowerCase(Locale.ROOT).endsWith("@gmail.com"));
    }

    /** Returns to the Welcome screen, clearing any Login/Registration
     *  activities stacked above it so back always lands on Welcome. */
    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void startIntroAnimations() {
        headerSection.setVisibility(View.VISIBLE);
        headerSection.startAnimation(flipLeft);

        registrationLabel.postDelayed(() -> {
            registrationLabel.setVisibility(View.VISIBLE);
            registrationLabel.startAnimation(flipLeft);
        }, 100);

        stepIndicatorContainer.postDelayed(() -> {
            stepIndicatorContainer.setVisibility(View.VISIBLE);
            stepIndicatorContainer.startAnimation(flipLeft);
        }, 200);

        registrationCard.postDelayed(() -> {
            registrationCard.setVisibility(View.VISIBLE);
            registrationCard.startAnimation(flipLeft);
        }, 300);

        loginRedirect.postDelayed(() -> {
            loginRedirect.setVisibility(View.VISIBLE);
            loginRedirect.startAnimation(flipLeft);
        }, 400);
    }
}
