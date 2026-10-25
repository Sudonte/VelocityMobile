package com.example.velocitysuites;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.AuthResponse;
import com.example.velocitysuites.network.dto.EmailRequest;
import com.example.velocitysuites.network.dto.ResetPasswordRequest;
import com.example.velocitysuites.network.dto.VerifyResetOtpRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Reset password, in three short steps under the same header as Sign in: 1) the email, 2) the 6-digit code (six boxes), 3) a new
 * password (the shared checklist). Asking for a code always gets the same answer ("If an account exists for that email, we've sent a
 * 6-digit code."), whether or not the address has an account. Saving the new password signs the guest in and opens the Dashboard.
 */
public class ResetPasswordActivity extends SafeActivity {

    /** The email to start with (Sign in passes the one that kept failing). */
    public static final String EXTRA_EMAIL = "reset_email";
    /** True when Sign in sent the guest here after three failed attempts: a line says why. */
    public static final String EXTRA_TROUBLE = "reset_trouble";

    private static final String STATE_STEP = "step";
    private static final String STATE_EMAIL = "email";
    private static final String STATE_CODE = "code";
    private static final String STATE_RESEND_LEFT = "resendLeft";
    private static final String STATE_BANNER = "banner";
    /** The server's own resend wait (PasswordResetService::RESEND_COOLDOWN_SECONDS). */
    private static final long RESEND_COOLDOWN_MS = 30_000L;

    private int step = 1;
    private String email = "";
    /** The code the server accepted in step 2; step 3 sends it again with the new password. */
    private String verifiedCode = "";
    private boolean requestInFlight;
    private boolean showBanner;
    private long resendUntil;
    private CountDownTimer resendTimer;

    private TextView heading, stepLine, banner, emailError, codeDesc, codeError, codeChecking, resendLink, saveError;
    private View step1, step2, step3;
    private TextInputLayout emailLayout;
    private TextInputEditText emailEdit, newPasswordEdit, confirmPasswordEdit;
    private MaterialButton sendCodeButton, saveButton;
    private PasswordRulesView rules;
    private OtpBoxes otpBoxes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reset_password);

        heading = findViewById(R.id.resetHeading);
        stepLine = findViewById(R.id.resetStepLine);
        banner = findViewById(R.id.resetBanner);
        step1 = findViewById(R.id.resetStep1);
        step2 = findViewById(R.id.resetStep2);
        step3 = findViewById(R.id.resetStep3);
        emailLayout = findViewById(R.id.resetEmailLayout);
        emailEdit = findViewById(R.id.resetEmailEditText);
        emailError = findViewById(R.id.resetEmailError);
        sendCodeButton = findViewById(R.id.sendCodeButton);
        codeDesc = findViewById(R.id.resetCodeDesc);
        codeError = findViewById(R.id.resetCodeError);
        codeChecking = findViewById(R.id.resetCodeChecking);
        resendLink = findViewById(R.id.resetResendLink);
        newPasswordEdit = findViewById(R.id.resetNewPasswordEditText);
        confirmPasswordEdit = findViewById(R.id.resetConfirmPasswordEditText);
        rules = findViewById(R.id.resetPasswordRules);
        saveError = findViewById(R.id.resetSaveError);
        saveButton = findViewById(R.id.saveAndSignInButton);

        showBanner = getIntent().getBooleanExtra(EXTRA_TROUBLE, false);
        String startEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        if (savedInstanceState != null) {
            step = savedInstanceState.getInt(STATE_STEP, 1);
            email = savedInstanceState.getString(STATE_EMAIL, "");
            verifiedCode = savedInstanceState.getString(STATE_CODE, "");
            resendUntil = SystemClock.elapsedRealtime() + savedInstanceState.getLong(STATE_RESEND_LEFT, 0L);
            showBanner = savedInstanceState.getBoolean(STATE_BANNER, showBanner);
            if (step == 3 && verifiedCode.length() != 6) step = 1; // nothing to save a password against
        } else if (startEmail != null) {
            email = startEmail.trim();
        }
        if (savedInstanceState == null || step == 1) emailEdit.setText(email);

        emailEdit.addTextChangedListener(new SimpleTextWatcher(() -> {
            emailLayout.setError(null);
            emailError.setVisibility(View.GONE);
        }));
        emailEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCode();
                return true;
            }
            return false;
        });
        sendCodeButton.setOnClickListener(v -> sendCode());
        findViewById(R.id.backToSignInLink).setOnClickListener(v -> finish());
        findViewById(R.id.btnBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        resendLink.setOnClickListener(v -> resendCode());
        findViewById(R.id.resetChangeEmailLink).setOnClickListener(v -> {
            cancelResend();
            step = 1;
            render();
        });
        rules.bind(newPasswordEdit, confirmPasswordEdit, () -> saveButton.setEnabled(!requestInFlight && rules.isSatisfied()));
        saveButton.setOnClickListener(v -> save());
        SimpleTextWatcher hideSaveError = new SimpleTextWatcher(() -> saveError.setVisibility(View.GONE));
        newPasswordEdit.addTextChangedListener(hideSaveError);
        confirmPasswordEdit.addTextChangedListener(hideSaveError);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (step == 1) {
                    finish();
                } else {
                    askToStop();
                }
            }
        });

        render();
    }

    // ---------------------------------------------------------------- the three steps

    private void render() {
        step1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        step2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        step3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        banner.setVisibility(showBanner && step == 1 ? View.VISIBLE : View.GONE);
        heading.setText(step == 1 ? R.string.reset_step1_title : step == 2 ? R.string.reset_step2_title : R.string.reset_step3_title);
        stepLine.setText(getString(R.string.reset_step_line, step,
                getString(step == 1 ? R.string.reset_step1_name : step == 2 ? R.string.reset_step2_name : R.string.reset_step3_name)));
        if (step == 2) {
            showCodeStep();
        } else if (step == 3) {
            saveError.setVisibility(View.GONE);
            saveButton.setEnabled(!requestInFlight && rules.isSatisfied());
            newPasswordEdit.requestFocus();
        } else {
            sendCodeButton.setEnabled(!requestInFlight);
        }
    }

    // ---------------------------------------------------------------- step 1: the email

    private void sendCode() {
        if (requestInFlight) return;
        String typed = textOf(emailEdit);
        if (TextUtils.isEmpty(typed)) {
            emailLayout.setError(getString(R.string.email_required));
            return;
        }
        if (!EmailValidator.isValid(typed)) {
            emailLayout.setError(getString(R.string.valid_email_required));
            return;
        }
        emailError.setVisibility(View.GONE);
        requestInFlight = true;
        sendCodeButton.setEnabled(false);
        ApiClient.getService(this).forgotPassword(new EmailRequest(typed)).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                requestInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                sendCodeButton.setEnabled(true);
                if (!response.isSuccessful()) {
                    // the wait-a-moment answer is the same for every address; anything else is a plain failure
                    showEmailError(response.code() == 429
                            ? ApiErrorParser.extractMessage(response, getString(R.string.reset_code_send_failed))
                            : getString(R.string.reset_code_send_failed));
                    return;
                }
                email = typed;
                verifiedCode = "";
                resendUntil = SystemClock.elapsedRealtime() + RESEND_COOLDOWN_MS;
                showBanner = false;
                step = 2;
                render();
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                requestInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                sendCodeButton.setEnabled(true);
                showEmailError(RoomRepository.networkErrorMessage(ResetPasswordActivity.this, t));
            }
        });
    }

    private void showEmailError(String message) {
        emailError.setText(message);
        emailError.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- step 2: the code

    private void showCodeStep() {
        codeDesc.setText(getString(R.string.reset_code_desc, OtpBoxes.maskEmail(email)));
        LinearLayout container = findViewById(R.id.resetOtpBoxes);
        otpBoxes = new OtpBoxes(container, 6, (code, complete) -> {
            codeError.setVisibility(View.GONE);
            otpBoxes.setError(false);
            if (complete) checkCode(code);
        });
        codeError.setVisibility(View.GONE);
        codeChecking.setVisibility(View.GONE);
        startResendCooldown(Math.max(0, resendUntil - SystemClock.elapsedRealtime()));
        otpBoxes.focusFirst();
    }

    private void checkCode(String code) {
        if (requestInFlight) return;
        requestInFlight = true;
        otpBoxes.setEnabled(false);
        codeChecking.setVisibility(View.VISIBLE);
        ApiClient.getService(this).verifyResetOtp(new VerifyResetOtpRequest(email, code)).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                requestInFlight = false;
                codeChecking.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    verifiedCode = code;
                    cancelResend();
                    step = 3;
                    render();
                    return;
                }
                otpBoxes.setEnabled(true);
                otpBoxes.clear(); // empty boxes for another try (clearing also hides any old message, so it comes first)
                otpBoxes.setError(true);
                codeError.setText(response.code() == 422 || response.code() == 400
                        ? getString(R.string.reset_code_wrong)
                        : ApiErrorParser.extractMessage(response, getString(R.string.reset_code_wrong)));
                codeError.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                requestInFlight = false;
                codeChecking.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                otpBoxes.setEnabled(true);
                codeError.setText(RoomRepository.networkErrorMessage(ResetPasswordActivity.this, t));
                codeError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void resendCode() {
        if (requestInFlight || SystemClock.elapsedRealtime() < resendUntil) return;
        resendLink.setEnabled(false);
        ApiClient.getService(this).forgotPassword(new EmailRequest(email)).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    resendUntil = SystemClock.elapsedRealtime() + RESEND_COOLDOWN_MS;
                    startResendCooldown(RESEND_COOLDOWN_MS);
                    otpBoxes.clear();
                    codeError.setVisibility(View.GONE);
                } else {
                    resendLink.setEnabled(true);
                    codeError.setText(response.code() == 429
                            ? ApiErrorParser.extractMessage(response, getString(R.string.reset_code_send_failed))
                            : getString(R.string.reset_code_send_failed));
                    codeError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                resendLink.setEnabled(true);
                codeError.setText(RoomRepository.networkErrorMessage(ResetPasswordActivity.this, t));
                codeError.setVisibility(View.VISIBLE);
            }
        });
    }

    /** "Resend code in 27s" while the wait lasts, "Resend code" once a new one can be asked for. */
    private void startResendCooldown(long millis) {
        cancelResend();
        if (millis <= 0) {
            resendLink.setText(R.string.reg_resend_code);
            resendLink.setEnabled(true);
            return;
        }
        resendLink.setEnabled(false);
        resendLink.setText(getString(R.string.resend_otp_countdown, (int) ((millis + 999) / 1000)));
        resendTimer = new CountDownTimer(millis, 1000L) {
            @Override
            public void onTick(long left) {
                resendLink.setText(getString(R.string.resend_otp_countdown, (int) ((left + 999) / 1000)));
            }

            @Override
            public void onFinish() {
                resendLink.setText(R.string.reg_resend_code);
                resendLink.setEnabled(true);
            }
        }.start();
    }

    private void cancelResend() {
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
    }

    // ---------------------------------------------------------------- step 3: the new password

    private void save() {
        if (requestInFlight || !rules.isSatisfied()) return;
        String password = rawOf(newPasswordEdit); // exactly as typed: spaces are part of a password
        String confirm = rawOf(confirmPasswordEdit);
        requestInFlight = true;
        saveButton.setEnabled(false);
        saveError.setVisibility(View.GONE);
        ApiClient.getService(this).resetPassword(new ResetPasswordRequest(email, verifiedCode, password, confirm)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                requestInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                AuthResponse auth = response.body();
                if (response.isSuccessful() && auth != null && auth.token != null && auth.user != null
                        && !"pending_deletion".equals(auth.user.account_status) && !Boolean.TRUE.equals(auth.reactivation_required)) {
                    signInAndOpenDashboard(auth);
                    return;
                }
                if (response.isSuccessful()) {
                    // the password IS changed, but this account needs its own step (restore / reactivate) that only Sign in has
                    FailedSignIns.clear(ResetPasswordActivity.this, email);
                    startActivity(new Intent(ResetPasswordActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_NOTICE, getString(R.string.reset_signin_instead)));
                    finish();
                    return;
                }
                saveButton.setEnabled(rules.isSatisfied());
                String message = ApiErrorParser.extractMessage(response, getString(R.string.reset_code_wrong));
                if (response.code() == 422 && message.toLowerCase(java.util.Locale.ROOT).contains("code")) {
                    // the code ran out while the password was being typed: back to step 2 for a new one
                    verifiedCode = "";
                    step = 2;
                    render();
                    otpBoxes.setError(true);
                    codeError.setText(R.string.reset_code_wrong);
                    codeError.setVisibility(View.VISIBLE);
                } else {
                    saveError.setText(message);
                    saveError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                requestInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                saveButton.setEnabled(rules.isSatisfied());
                saveError.setText(RoomRepository.networkErrorMessage(ResetPasswordActivity.this, t));
                saveError.setVisibility(View.VISIBLE);
            }
        });
    }

    /** The server signed this device in with the new password (and signed every other device out): straight to the Dashboard. */
    private void signInAndOpenDashboard(AuthResponse auth) {
        cancelResend();
        String fullName = GuestSession.save(this, auth);
        SessionManager.setRememberMe(this, true);
        FailedSignIns.clear(this, email);
        Intent intent = PendingRoomSelection.createPostAuthIntent(this, fullName);
        Feedback.later(getString(R.string.reset_success_snackbar)); // the Dashboard shows it as a Snackbar when it comes to the front
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------------------- leaving

    private void askToStop() {
        new SafeDialogs.Material(this)
                .setTitle(R.string.reset_stop_title)
                .setMessage(R.string.reset_stop_message)
                .setPositiveButton(R.string.reset_stop_keep, null)
                .setNegativeButton(R.string.reset_stop_leave, (dialog, which) -> finish())
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_STEP, step);
        out.putString(STATE_EMAIL, email);
        out.putString(STATE_CODE, verifiedCode);
        out.putLong(STATE_RESEND_LEFT, Math.max(0, resendUntil - SystemClock.elapsedRealtime()));
        out.putBoolean(STATE_BANNER, showBanner);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelResend();
    }

    private static String rawOf(@Nullable TextInputEditText edit) {
        return edit != null && edit.getText() != null ? edit.getText().toString() : "";
    }

    private static String textOf(@Nullable TextInputEditText edit) {
        return edit != null && edit.getText() != null ? edit.getText().toString().trim() : "";
    }
}
