package com.example.velocitysuites;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.AuthResponse;
import com.example.velocitysuites.network.dto.EmailRequest;
import com.example.velocitysuites.network.dto.LoginRequest;
import com.example.velocitysuites.network.dto.ProfileResponse;
import java.util.Locale;
import com.example.velocitysuites.network.dto.ReactivateResendRequest;
import com.example.velocitysuites.network.dto.ReactivateVerifyRequest;
import com.example.velocitysuites.network.dto.ResetPasswordRequest;
import com.example.velocitysuites.network.dto.VerifyResetOtpRequest;
import android.os.CountDownTimer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private TextInputEditText forgotEmailEditText, loginOtpEditText, newPasswordEditText, confirmNewPasswordEditText;
    private TextInputLayout forgotEmailLayout, loginOtpLayout, newPasswordLayout, confirmNewPasswordLayout;
    private View forgotEmailLabel, otpLabel, newPasswordLabel, confirmPasswordLabel;
    private View loginCard, forgotPasswordCard, signupRedirect, headerSection, loginLabel;
    private TextView forgotPasswordDesc, forgotPasswordTitle;
    private TextView resendOtpLink;
    private MaterialButton forgotActionButton;
    private MaterialCheckBox rememberMeCheckbox;
    private Animation flipLeft, fadeInSlideUp;

    private boolean isOtpSent = false;
    private boolean isOtpVerified = false;

    // Account-reactivation OTP flow (see handleLogin()'s reactivation_required
    // branch) - a deliberately separate card/state from the forgot-password
    // flow above, since it's triggered by login() itself rather than a user
    // action, and needs no email/new-password steps, only the OTP.
    private View reactivationCard;
    private TextView reactivationDesc, reactivationResendLink;
    private TextInputLayout reactivationOtpLayout;
    private TextInputEditText reactivationOtpEditText;
    private MaterialButton reactivationActionButton;
    private String pendingReactivationToken;
    private CountDownTimer resendCooldownTimer;

    // Built-in demo account so the app is usable without a live backend.
    private static final String DEFAULT_EMAIL = "guest@gmail.com";
    private static final String DEFAULT_PASSWORD = "password123";
    private static final String DEFAULT_FIRST_NAME = "John Paul";
    private static final String DEFAULT_MIDDLE_NAME = "Abe";
    private static final String DEFAULT_LAST_NAME = "Ombid";
    private static final String DEFAULT_AGE = "21";
    private static final String DEFAULT_DOB = "Nov 01, 2004";
    private static final String DEFAULT_MOBILE = "093564255534";
    private static final String DEFAULT_GENDER = "Male";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // A still-valid (not yet 24h-expired) Remember Me session is only a
        // LOCAL claim (see SessionManager#hasValidRememberedSession()'s own
        // doc) - it says nothing about whether the token it's built on is
        // still actually valid/authorized server-side. A token can stop
        // being valid without this device ever finding out (revoked,
        // password changed, account deactivated) - and, separately, this
        // exact local state (token + Remember Me flags) could in principle
        // have arrived on this device via something other than a real login
        // here (see SessionManager's own class doc on why that state is kept
        // out of Android backup/device-transfer). Either way, a locally-held
        // token must be confirmed against the backend before this activity
        // ever lets it into the dashboard - see validateRememberedSession().
        if (SessionManager.hasValidRememberedSession(this)) {
            validateRememberedSession();
            return;
        }

        showLoginForm();
    }

    /**
     * The one and only gate that turns a locally-held Remember Me session
     * into an actual dashboard entry. A lightweight authenticated GET
     * (guest/profile - already used elsewhere, no new backend endpoint) that
     * only ever succeeds if the backend still considers this exact token
     * valid: expired/invalid/revoked all come back non-2xx (or the call
     * fails outright), and both cases fall back to showLoginForm() with the
     * stale local session cleared - never a silent dashboard entry on local
     * state alone.
     */
    private void validateRememberedSession() {
        ApiClient.getService(this).getProfile().enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call,
                                    Response<ProfileResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    enterAppFromRememberedSession();
                } else {
                    SessionManager.clear(LoginActivity.this);
                    showLoginForm();
                }
            }

            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                // A network failure here is NOT proof the token is invalid -
                // but silently entering the dashboard on local state alone is
                // exactly the risk this whole gate exists to close, so the
                // safe default is the same as an explicit rejection: show
                // the login form rather than assume. The guest can simply
                // retry once connectivity returns; nothing was lost, since a
                // real session (if this token is still good) resumes on the
                // very next successful login attempt or app reopen.
                if (isFinishing() || isDestroyed()) return;
                showLoginForm();
            }
        });
    }

    private void enterAppFromRememberedSession() {
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String userName = prefs.getString("userName", "");
        Intent intent = PendingRoomSelection.createPostAuthIntent(this, userName);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();
    }

    private void showLoginForm() {
        setContentView(R.layout.login);

        initializeViews();
        startIntroAnimations();

        findViewById(R.id.signInButton).setOnClickListener(v -> handleLogin());
        findViewById(R.id.signUpLink).setOnClickListener(v -> {
            startActivity(new Intent(this, RegistrationActivity.class));
            // Using system defaults if custom animations are missing or to ensure smooth transition
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> goToWelcome());

        findViewById(R.id.forgotPasswordText).setOnClickListener(v -> showForgotPasswordFlow());
        findViewById(R.id.backToLoginLink).setOnClickListener(v -> showLoginFlow());
        forgotActionButton.setOnClickListener(v -> handleForgotAction());
        resendOtpLink.setOnClickListener(v -> handleResendOtp());

        reactivationActionButton.setOnClickListener(v -> handleVerifyReactivation());
        reactivationResendLink.setOnClickListener(v -> handleResendReactivationOtp());
        findViewById(R.id.backToLoginFromReactivationLink).setOnClickListener(v -> backToLoginFromReactivation());
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        
        forgotEmailEditText = findViewById(R.id.forgotEmailEditText);
        loginOtpEditText = findViewById(R.id.loginOtpEditText);
        newPasswordEditText = findViewById(R.id.newPasswordEditText);
        confirmNewPasswordEditText = findViewById(R.id.confirmNewPasswordEditText);
        
        forgotEmailLayout = findViewById(R.id.forgotEmailLayout);
        loginOtpLayout = findViewById(R.id.loginOtpLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmNewPasswordLayout = findViewById(R.id.confirmNewPasswordLayout);

        forgotEmailLabel = findViewById(R.id.forgotEmailLabel);
        otpLabel = findViewById(R.id.otpLabel);
        newPasswordLabel = findViewById(R.id.newPasswordLabel);
        confirmPasswordLabel = findViewById(R.id.confirmPasswordLabel);
        
        loginCard = findViewById(R.id.loginCard);
        forgotPasswordCard = findViewById(R.id.forgotPasswordCard);
        signupRedirect = findViewById(R.id.signupRedirect);
        headerSection = findViewById(R.id.headerSection);
        loginLabel = findViewById(R.id.loginLabel);
        
        forgotPasswordTitle = findViewById(R.id.forgotPasswordTitle);
        forgotPasswordDesc = findViewById(R.id.forgotPasswordDesc);
        forgotActionButton = findViewById(R.id.forgotActionButton);
        resendOtpLink = findViewById(R.id.resendOtpLink);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);

        reactivationCard = findViewById(R.id.reactivationCard);
        reactivationDesc = findViewById(R.id.reactivationDesc);
        reactivationOtpLayout = findViewById(R.id.reactivationOtpLayout);
        reactivationOtpEditText = findViewById(R.id.reactivationOtpEditText);
        reactivationActionButton = findViewById(R.id.reactivationActionButton);
        reactivationResendLink = findViewById(R.id.reactivationResendLink);

        fadeInSlideUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up);
        flipLeft = AnimationUtils.loadAnimation(this, R.anim.flip_left);
    }

    private void handleLogin() {
        if (emailEditText == null || passwordEditText == null) return;

        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString().trim() : "";

        if (!validateInput(email, password)) return;

        boolean isDefaultAccount = email.equalsIgnoreCase(DEFAULT_EMAIL) && password.equals(DEFAULT_PASSWORD);

        findViewById(R.id.signInButton).setEnabled(false);

        // Always authenticate against the real backend first - even for the
        // built-in demo credentials - so a session created here carries a
        // real Bearer token. A locally-fabricated token (e.g. "demo-token")
        // is accepted by no server endpoint, so anything that actually
        // writes data (creating a booking/reservation, updating the
        // profile, submitting a payment) would fail with 401
        // "Unauthenticated" even though the guest appears logged in.
        LoginRequest request = new LoginRequest(email, password, Build.MODEL);
        ApiClient.getService(this).login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                findViewById(R.id.signInButton).setEnabled(true);
                if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().reactivation_required)) {
                    // Correct credentials, but the account is deactivated (see
                    // Api\AuthController::login()'s ACCOUNT_DEACTIVATED branch) -
                    // no token was issued; the guest must verify an emailed OTP
                    // before a real session exists (see showReactivationFlow()).
                    showReactivationFlow(response.body());
                } else if (response.isSuccessful() && response.body() != null) {
                    successfulLogin(response.body(), R.string.welcome_back);
                } else if (response.code() == 423) {
                    Toast.makeText(LoginActivity.this, R.string.failed_attempts_warning, Toast.LENGTH_LONG).show();
                    showForgotPasswordFlow();
                } else if (response.code() == 403) {
                    // Wrong password never reaches here (that's a 401) -
                    // 403 means the credentials were correct but the
                    // account isn't a guest account (see AuthController::
                    // login's role check), so show the server's actual
                    // reason instead of a misleading "invalid credentials".
                    Toast.makeText(LoginActivity.this, errorMessage(response), Toast.LENGTH_LONG).show();
                } else if (isDefaultAccount) {
                    // Demo account rejected by the server (e.g. not seeded
                    // in this environment) - fall back to the fully local
                    // demo session so the UI is still browsable, same as
                    // the no-connectivity fallback below.
                    loginWithDefaultAccount();
                } else {
                    Toast.makeText(LoginActivity.this, R.string.invalid_credentials, Toast.LENGTH_SHORT).show();
                    passwordEditText.setError(getString(R.string.incorrect_password));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                findViewById(R.id.signInButton).setEnabled(true);
                if (isDefaultAccount) {
                    // No backend reachable - fall back to the fully local
                    // demo session so the demo account still works offline.
                    loginWithDefaultAccount();
                    return;
                }
                Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void successfulLogin(AuthResponse auth, @androidx.annotation.StringRes int messageResId) {
        String firstName = auth.user.first_name != null ? auth.user.first_name : "";
        String lastName = auth.user.last_name != null ? auth.user.last_name : "";
        String fullName = auth.user.full_name != null ? auth.user.full_name : (firstName + " " + lastName).trim();
        String mobile = auth.user.guest != null ? auth.user.guest.mobile_number : null;
        String gender = auth.user.guest != null ? auth.user.guest.gender : null;
        String dob = auth.user.guest != null ? auth.user.guest.date_of_birth : null;
        String pictureUrl = auth.user.guest != null ? auth.user.guest.profile_picture_url : null;

        SessionManager.saveSession(this, auth.token, auth.user.id, firstName, lastName, auth.user.middle_name,
                fullName, auth.user.email, mobile, gender, dob, pictureUrl);

        // Persistent login only if "Remember Me" is checked; otherwise the
        // token still lives in prefs for this process/app-open, but there's
        // no Remember Me session for LoginActivity to auto-skip past on the
        // next cold start. saveSession() above already reset any prior
        // Remember Me state, so this call is what (re)establishes it.
        boolean isRemembered = rememberMeCheckbox != null && rememberMeCheckbox.isChecked();
        SessionManager.setRememberMe(this, isRemembered);

        // The account was soft-deleted (see ProfileManagementActivity's
        // Delete Account action) but is still inside its 30-day restore
        // window - the server still issues a token for this login
        // specifically so the guest can reach this prompt instead of the
        // normal dashboard flow.
        if ("pending_deletion".equals(auth.user.account_status)) {
            showRestoreAccountPrompt(auth.user.restore_deadline, fullName);
            return;
        }

        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
        Intent intent = PendingRoomSelection.createPostAuthIntent(this, fullName);
        startActivity(intent);
        finish();
    }

    /**
     * Offers to reactivate a still-restorable account. Declining clears the
     * session that successfulLogin() already saved, so the guest lands back
     * at a logged-out state rather than silently keeping a live token for
     * an account still scheduled for deletion.
     */
    private void showRestoreAccountPrompt(String restoreDeadlineIso, String fullName) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_account_title)
                .setMessage(getString(R.string.restore_account_message, formatRestoreDeadline(restoreDeadlineIso)))
                .setCancelable(false)
                .setPositiveButton(R.string.restore_account_button, (dialog, which) -> restoreAccount(fullName))
                .setNegativeButton(R.string.no_label, (dialog, which) -> {
                    getSharedPreferences("VelocityPrefs", MODE_PRIVATE).edit().clear().apply();
                    Toast.makeText(this, R.string.restore_account_declined, Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void restoreAccount(String fullName) {
        ApiClient.getService(this).restoreAccount().enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (!response.isSuccessful()) {
                    getSharedPreferences("VelocityPrefs", MODE_PRIVATE).edit().clear().apply();
                    Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(LoginActivity.this, R.string.restore_account_success, Toast.LENGTH_LONG).show();
                Intent intent = PendingRoomSelection.createPostAuthIntent(LoginActivity.this, fullName);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                getSharedPreferences("VelocityPrefs", MODE_PRIVATE).edit().clear().apply();
                Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Best-effort "Month DD, YYYY" formatting of the ISO-8601 restore_deadline; falls back to the raw string if parsing fails. */
    private String formatRestoreDeadline(String isoDate) {
        if (isoDate == null) return "";
        try {
            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            java.text.SimpleDateFormat output = new java.text.SimpleDateFormat("MMMM dd, yyyy", Locale.US);
            return output.format(input.parse(isoDate));
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Switches from the login card to the reactivation card and starts the
     * resend cooldown. Never touches SessionManager/prefs here - no session
     * exists yet, and won't until handleVerifyReactivation() succeeds.
     */
    private void showReactivationFlow(AuthResponse body) {
        pendingReactivationToken = body.reactivation_token;

        transitionCards(loginCard, reactivationCard);
        reactivationDesc.setText(getString(R.string.reactivate_account_desc, body.masked_email));
        if (reactivationOtpEditText != null) reactivationOtpEditText.setText("");
        startResendCooldown(60);
    }

    /** Returns to the plain login form without authenticating - the account stays deactivated (see task spec section 27). */
    private void backToLoginFromReactivation() {
        pendingReactivationToken = null;
        cancelResendCooldown();
        transitionCards(reactivationCard, loginCard);
    }

    private void handleVerifyReactivation() {
        if (reactivationOtpEditText == null || pendingReactivationToken == null) return;
        String otp = reactivationOtpEditText.getText() != null ? reactivationOtpEditText.getText().toString() : "";
        if (otp.length() != 6) {
            reactivationOtpLayout.setError(getString(R.string.invalid_otp));
            return;
        }
        reactivationOtpLayout.setError(null);

        reactivationActionButton.setEnabled(false);
        ApiClient.getService(this)
                .reactivateVerify(new ReactivateVerifyRequest(pendingReactivationToken, otp, Build.MODEL))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        reactivationActionButton.setEnabled(true);
                        if (!response.isSuccessful() || response.body() == null || response.body().token == null) {
                            reactivationOtpLayout.setError(getString(R.string.invalid_otp));
                            Toast.makeText(LoginActivity.this, errorMessage(response), Toast.LENGTH_LONG).show();
                            return;
                        }
                        // Real session established server-side only now that OTP
                        // verification succeeded - cancelResendCooldown() first so
                        // no stray callback fires after this screen navigates away.
                        cancelResendCooldown();
                        pendingReactivationToken = null;
                        successfulLogin(response.body(), R.string.reactivation_success);
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        reactivationActionButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleResendReactivationOtp() {
        if (pendingReactivationToken == null) return;

        reactivationResendLink.setEnabled(false);
        ApiClient.getService(this).reactivateResend(new ReactivateResendRequest(pendingReactivationToken))
                .enqueue(new Callback<ApiMessage>() {
                    @Override
                    public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                        if (!response.isSuccessful()) {
                            // 429 (throttled) is expected if this races the
                            // cooldown's own end - either way, resume counting
                            // down rather than leaving the link permanently
                            // disabled with no explanation.
                            startResendCooldown(60);
                            return;
                        }
                        if (reactivationOtpEditText != null) reactivationOtpEditText.setText("");
                        Toast.makeText(LoginActivity.this, R.string.otp_resent, Toast.LENGTH_SHORT).show();
                        startResendCooldown(60);
                    }

                    @Override
                    public void onFailure(Call<ApiMessage> call, Throwable t) {
                        reactivationResendLink.setEnabled(true);
                        Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Informational-only client-side countdown (see task spec section 22) -
     * the backend independently enforces its own resend cooldown regardless
     * of what this timer shows; a modified/replayed resend request still
     * gets throttled server-side either way.
     */
    private void startResendCooldown(int seconds) {
        cancelResendCooldown();
        reactivationResendLink.setEnabled(false);
        resendCooldownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                reactivationResendLink.setText(getString(R.string.resend_otp_countdown, (millisUntilFinished / 1000) + 1));
            }

            @Override
            public void onFinish() {
                reactivationResendLink.setText(R.string.resend_otp);
                reactivationResendLink.setEnabled(true);
            }
        }.start();
    }

    private void cancelResendCooldown() {
        if (resendCooldownTimer != null) {
            resendCooldownTimer.cancel();
            resendCooldownTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelResendCooldown();
    }

    /**
     * Fallback-only local session for the built-in demo profile, used when
     * the real backend can't authenticate it (see the callers in
     * {@link #handleLogin}). Since this never talks to the server, the
     * resulting "demo-token" is not a valid Bearer token - screens that
     * only read locally cached data still work, but any real server write
     * (booking/reservation creation, profile edits, payments) will be
     * rejected with 401 Unauthenticated.
     */
    private void loginWithDefaultAccount() {
        String fullName = DEFAULT_FIRST_NAME + " " + DEFAULT_MIDDLE_NAME + " " + DEFAULT_LAST_NAME;

        SessionManager.saveSession(this, "demo-token", 0L, DEFAULT_FIRST_NAME, DEFAULT_LAST_NAME,
                DEFAULT_MIDDLE_NAME, fullName, DEFAULT_EMAIL, DEFAULT_MOBILE, DEFAULT_GENDER, DEFAULT_DOB, null);
        getSharedPreferences("VelocityPrefs", MODE_PRIVATE).edit().putString("userAge", DEFAULT_AGE).apply();

        boolean isRemembered = rememberMeCheckbox != null && rememberMeCheckbox.isChecked();
        SessionManager.setRememberMe(this, isRemembered);

        Toast.makeText(this, R.string.welcome_back, Toast.LENGTH_SHORT).show();
        Intent intent = PendingRoomSelection.createPostAuthIntent(this, fullName);
        startActivity(intent);
        finish();
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
        return getString(R.string.invalid_credentials);
    }

    private boolean validateInput(String email, String password) {
        boolean isValid = true;
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError(getString(R.string.email_required));
            isValid = false;
        } else if (!isGmailAddress(email)) {
            emailEditText.setError(getString(R.string.valid_email_required));
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError(getString(R.string.password_required));
            isValid = false;
        }
        return isValid;
    }

    private boolean isGmailAddress(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && email.trim().toLowerCase(Locale.ROOT).endsWith("@gmail.com");
    }

    private void showForgotPasswordFlow() {
        transitionCards(loginCard, forgotPasswordCard);
        resetForgotFields();
    }

    private void showLoginFlow() {
        transitionCards(forgotPasswordCard, loginCard);
    }

    private void transitionCards(View fromCard, View toCard) {
        fromCard.animate().alpha(0f).translationY(50f).setDuration(250).withEndAction(() -> {
            fromCard.setVisibility(View.GONE);
            signupRedirect.setVisibility(toCard == loginCard ? View.VISIBLE : View.GONE);
            toCard.setVisibility(View.VISIBLE);
            toCard.setAlpha(0f);
            toCard.setTranslationY(50f);
            toCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }).start();
    }

    private void handleForgotAction() {
        if (!isOtpSent) {
            if (forgotEmailEditText == null) return;
            String email = forgotEmailEditText.getText() != null ? forgotEmailEditText.getText().toString().trim() : "";
            if (TextUtils.isEmpty(email)) {
                forgotEmailEditText.setError(getString(R.string.email_required));
                return;
            }
            if (!isGmailAddress(email)) {
                forgotEmailEditText.setError(getString(R.string.valid_email_required));
                return;
            }

            forgotActionButton.setEnabled(false);
            ApiClient.getService(this).forgotPassword(new EmailRequest(email)).enqueue(new Callback<ApiMessage>() {
                @Override
                public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                    forgotActionButton.setEnabled(true);
                    if (!response.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, R.string.reset_code_send_failed, Toast.LENGTH_LONG).show();
                        return;
                    }

                    isOtpSent = true;
                    Toast.makeText(LoginActivity.this, R.string.otp_sent, Toast.LENGTH_SHORT).show();

                    forgotEmailLayout.setVisibility(View.GONE);
                    forgotEmailLabel.setVisibility(View.GONE);
                    loginOtpLayout.setVisibility(View.VISIBLE);
                    otpLabel.setVisibility(View.VISIBLE);
                    resendOtpLink.setVisibility(View.VISIBLE);
                    forgotPasswordDesc.setText(getString(R.string.otp_sent_to, email));
                    forgotActionButton.setText(R.string.verify_otp_button);
                }

                @Override
                public void onFailure(Call<ApiMessage> call, Throwable t) {
                    forgotActionButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                }
            });
        } else if (!isOtpVerified) {
            if (loginOtpEditText == null) return;
            String otp = loginOtpEditText.getText() != null ? loginOtpEditText.getText().toString() : "";
            if (otp.length() != 6) {
                loginOtpEditText.setError(getString(R.string.invalid_otp));
                return;
            }
            String email = forgotEmailEditText.getText() != null ? forgotEmailEditText.getText().toString().trim() : "";

            forgotActionButton.setEnabled(false);
            ApiClient.getService(this).verifyResetOtp(new VerifyResetOtpRequest(email, otp)).enqueue(new Callback<ApiMessage>() {
                @Override
                public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                    forgotActionButton.setEnabled(true);
                    if (!response.isSuccessful()) {
                        loginOtpEditText.setError(getString(R.string.invalid_otp));
                        Toast.makeText(LoginActivity.this, R.string.otp_invalid_or_expired, Toast.LENGTH_LONG).show();
                        return;
                    }

                    isOtpVerified = true;
                    Toast.makeText(LoginActivity.this, R.string.otp_verified, Toast.LENGTH_SHORT).show();
                    loginOtpLayout.setVisibility(View.GONE);
                    otpLabel.setVisibility(View.GONE);
                    resendOtpLink.setVisibility(View.GONE);
                    newPasswordLayout.setVisibility(View.VISIBLE);
                    newPasswordLabel.setVisibility(View.VISIBLE);
                    confirmNewPasswordLayout.setVisibility(View.VISIBLE);
                    confirmPasswordLabel.setVisibility(View.VISIBLE);
                    forgotPasswordTitle.setText(R.string.reset_password_title);
                    forgotPasswordDesc.setText(R.string.set_new_password_instruction);
                    forgotActionButton.setText(R.string.reset_password_button);
                }

                @Override
                public void onFailure(Call<ApiMessage> call, Throwable t) {
                    forgotActionButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            if (newPasswordEditText == null || confirmNewPasswordEditText == null) return;
            String newPass = newPasswordEditText.getText() != null ? newPasswordEditText.getText().toString() : "";
            String confirmPass = confirmNewPasswordEditText.getText() != null ? confirmNewPasswordEditText.getText().toString() : "";

            if (!validateNewPassword(newPass, confirmPass)) return;

            String email = forgotEmailEditText.getText() != null ? forgotEmailEditText.getText().toString().trim() : "";
            String otp = loginOtpEditText.getText() != null ? loginOtpEditText.getText().toString() : "";

            forgotActionButton.setEnabled(false);
            ApiClient.getService(this).resetPassword(new ResetPasswordRequest(email, otp, newPass, confirmPass))
                    .enqueue(new Callback<AuthResponse>() {
                        @Override
                        public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                            forgotActionButton.setEnabled(true);
                            if (!response.isSuccessful() || response.body() == null) {
                                loginOtpEditText.setError(getString(R.string.invalid_otp));
                                Toast.makeText(LoginActivity.this, R.string.otp_invalid_or_expired, Toast.LENGTH_LONG).show();
                                return;
                            }

                            isOtpSent = false;
                            isOtpVerified = false;
                            successfulLogin(response.body(), R.string.password_reset_success);
                        }

                        @Override
                        public void onFailure(Call<AuthResponse> call, Throwable t) {
                            forgotActionButton.setEnabled(true);
                            Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private boolean validateNewPassword(String newPass, String confirmPass) {
        if (newPass.length() < 8) {
            newPasswordEditText.setError(getString(R.string.password_min_length));
            return false;
        }
        if (!newPass.equals(confirmPass)) {
            confirmNewPasswordEditText.setError(getString(R.string.passwords_do_not_match));
            return false;
        }
        return true;
    }

    private void handleResendOtp() {
        if (forgotEmailEditText == null) return;
        String email = forgotEmailEditText.getText() != null ? forgotEmailEditText.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) return;

        resendOtpLink.setEnabled(false);
        ApiClient.getService(this).forgotPassword(new EmailRequest(email)).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                resendOtpLink.setEnabled(true);
                if (!response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, R.string.reset_code_send_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                if (loginOtpEditText != null) loginOtpEditText.setText("");
                Toast.makeText(LoginActivity.this, R.string.otp_resent, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                resendOtpLink.setEnabled(true);
                Toast.makeText(LoginActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resetForgotFields() {
        isOtpSent = false;
        isOtpVerified = false;
        forgotEmailLayout.setVisibility(View.VISIBLE);
        forgotEmailLabel.setVisibility(View.VISIBLE);
        loginOtpLayout.setVisibility(View.GONE);
        otpLabel.setVisibility(View.GONE);
        resendOtpLink.setVisibility(View.GONE);
        newPasswordLayout.setVisibility(View.GONE);
        newPasswordLabel.setVisibility(View.GONE);
        confirmNewPasswordLayout.setVisibility(View.GONE);
        confirmPasswordLabel.setVisibility(View.GONE);
        forgotPasswordTitle.setText(R.string.reset_password_title);
        forgotPasswordDesc.setText(R.string.forgot_password_instruction);
        forgotActionButton.setText(R.string.send_otp_button);
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

        loginLabel.postDelayed(() -> {
            loginLabel.setVisibility(View.VISIBLE);
            loginLabel.startAnimation(flipLeft);
        }, 200);

        loginCard.postDelayed(() -> {
            loginCard.setVisibility(View.VISIBLE);
            loginCard.startAnimation(flipLeft);
        }, 400);

        signupRedirect.postDelayed(() -> {
            signupRedirect.setVisibility(View.VISIBLE);
            signupRedirect.startAnimation(flipLeft);
        }, 600);
    }
}
