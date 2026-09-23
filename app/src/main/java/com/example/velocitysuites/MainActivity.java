package com.example.velocitysuites;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Startup gate: introduction.xml is not just a cosmetic splash - it holds
 * navigation to LandingActivity until a real connectivity + backend check
 * succeeds (see startStartupCheck()). The entrance animation (fade-in, logo
 * zoom) is purely cosmetic and unrelated to that gate; it always plays the
 * same way regardless of network state.
 *
 * The 0-100% readout is a real, single-source-of-truth value (currentProgress
 * below), not decoration: it only ever advances while genuine startup work is
 * in flight (a phase for the local connectivity check, a phase for the
 * backend call), and it can only reach 100 once that work has actually
 * succeeded - see finishOnceResultReady(). A guest with an unusually slow
 * connection sees the bar hold just under 100 (never fake-completing) instead
 * of the screen looking frozen or the app lying about being ready.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Startup";
    private static final long LOGO_ZOOM_DURATION_MS = 3000L;

    // Progress checkpoints. Only two of these boundaries are gated by a real
    // async result (CONNECTIVITY_END on the local network check,
    // BACKEND_PHASE_CAP on the backend call via finishOnceResultReady()) -
    // INIT_END and CONNECTING_END are cosmetic sub-labels within that same
    // real wait, giving the spec's requested staged messaging without ever
    // claiming completion ahead of an actual result. See class doc.
    private static final int INIT_END = 8;
    private static final int CONNECTIVITY_END = 25;
    private static final int CONNECTING_END = 50;
    /** Soft cap for the backend-call cosmetic climb - never reaches 100 on its own. */
    private static final int BACKEND_PHASE_CAP = 92;
    private static final int ALMOST_READY_PROGRESS = 99;

    private static final long INIT_DURATION_MS = 350L;
    private static final long CONNECTIVITY_DURATION_MS = 450L;
    private static final long CONNECTING_DURATION_MS = 900L;
    private static final long LOADING_INFO_DURATION_MS = 1300L;
    private static final long ALMOST_READY_DURATION_MS = 200L;
    private static final long COMPLETE_DURATION_MS = 200L;
    private static final long READY_HOLD_MS = 400L;
    /** How often to re-check whether the real backend call has resolved yet, once the cosmetic climb is capped out waiting for it. */
    private static final long RESULT_POLL_INTERVAL_MS = 200L;

    private View loadingPanel;
    private View errorPanel;
    private TextView loadingLabel;
    private TextView loadingMessageText;
    private TextView loadingPercentText;
    private View loadingIndicatorDot;
    private ProgressBar introductionProgress;
    private TextView errorTitleText;
    private TextView errorDescriptionText;
    private MaterialButton btnIntroductionRetry;

    private final Handler startupHandler = new Handler(Looper.getMainLooper());

    /** Single source of truth for both the progress bar and the "%" label - see updateProgress(). */
    private int currentProgress = 0;
    /** Navigation must fire at most once - guards against the success callback somehow landing twice. */
    private boolean hasNavigated = false;
    /** Re-entrancy guard for the Retry button - a startup check already in flight is never duplicated. */
    private boolean isChecking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.introduction);

        View introductionRoot = findViewById(R.id.introductionRoot);
        View logoGroup = findViewById(R.id.logoGroup);
        loadingLabel = findViewById(R.id.loadingLabel);
        loadingMessageText = findViewById(R.id.loadingMessageText);
        loadingPercentText = findViewById(R.id.loadingPercentText);
        introductionProgress = findViewById(R.id.introductionProgress);
        loadingPanel = findViewById(R.id.loadingPanel);
        loadingIndicatorDot = findViewById(R.id.loadingIndicatorDot);
        errorPanel = findViewById(R.id.errorPanel);
        errorTitleText = findViewById(R.id.errorTitleText);
        errorDescriptionText = findViewById(R.id.errorDescriptionText);
        btnIntroductionRetry = findViewById(R.id.btnIntroductionRetry);

        introductionProgress.setIndeterminate(false);
        introductionProgress.setMax(100);

        btnIntroductionRetry.setOnClickListener(v -> startStartupCheck());

        // Edge-to-edge is enforced from targetSdk 35+; pad for system bars so the
        // logo and loading panel never sit under the status bar or gesture nav bar.
        ViewCompat.setOnApplyWindowInsetsListener(introductionRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return insets;
        });

        // Initial state: ensure views are ready for animation
        introductionRoot.setAlpha(0f);
        logoGroup.setVisibility(View.INVISIBLE);

        // 1. Start Background Fade In
        introductionRoot.animate()
                .alpha(1f)
                .setDuration(2000L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // 2. Start Logo Zoom
                        startLogoAnimation(logoGroup);
                    }
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Belt-and-suspenders: stop the result-polling chain from posting
        // further callbacks once this Activity is gone (openLanding()/
        // showError() already no-op safely via isChecking/hasNavigated, but
        // there's no reason to let a stray Handler message survive).
        startupHandler.removeCallbacksAndMessages(null);
    }

    private void startLogoAnimation(View logoGroup) {
        logoGroup.setVisibility(View.VISIBLE);
        Animation zoomInAnimation = AnimationUtils.loadAnimation(this, R.anim.logo_zoom_in);
        zoomInAnimation.setDuration(LOGO_ZOOM_DURATION_MS);
        zoomInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // 3. Real startup check begins only after the cosmetic entrance finishes.
                startStartupCheck();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        logoGroup.startAnimation(zoomInAnimation);
    }

    /**
     * The real startup gate, staged into several labeled progress segments
     * for a richer readout, but only ever gated by two genuine async results:
     *   0 -> INIT_END: purely cosmetic ("Starting Velocity Suites..." - local
     *   view setup, which does happen, just isn't literally metered).
     *   INIT_END -> CONNECTIVITY_END: gated by the real local connectivity
     *   check (NetworkUtils.isOnline) - fails straight to the error
     *   state, never continuing past this point on a bad result.
     *   CONNECTIVITY_END -> CONNECTING_END -> BACKEND_PHASE_CAP: cosmetic
     *   sub-labels ("Connecting..." then "Loading hotel information...")
     *   spanning the SAME single real backend call - RoomRepository.
     *   refreshRooms() hits the same public GET /rooms endpoint landing.xml
     *   needs first anyway, so it doubles as both the "backend reachable"
     *   probe and the "load required landing data" step rather than
     *   inventing a separate health endpoint.
     * The bar only ever completes past BACKEND_PHASE_CAP once the real
     * backend result is actually known to be a success - see
     * finishOnceResultReady(). Reaching the cap is not itself completion.
     */
    private void startStartupCheck() {
        if (isChecking) return;
        isChecking = true;

        btnIntroductionRetry.setEnabled(false);
        showLoadingPanel();
        revealLoadingView(loadingPanel);
        revealLoadingView(loadingIndicatorDot);
        revealLoadingView(loadingMessageText);
        revealLoadingView(loadingLabel);
        revealLoadingView(introductionProgress);
        revealLoadingView(loadingPercentText);
        startPulse(loadingIndicatorDot);

        updateProgress(0);
        loadingMessageText.setText(R.string.introduction_starting);

        animateProgress(0, INIT_END, INIT_DURATION_MS, () -> {
            loadingMessageText.setText(R.string.introduction_checking_connection);
            animateProgress(INIT_END, CONNECTIVITY_END, CONNECTIVITY_DURATION_MS, () -> {
                if (!NetworkUtils.isOnline(this)) {
                    isChecking = false;
                    showError(getString(R.string.introduction_error_no_internet),
                            getString(R.string.introduction_error_no_internet_desc));
                    return;
                }
                runBackendCheck();
            });
        });
    }

    private void runBackendCheck() {
        loadingMessageText.setText(R.string.introduction_connecting);

        // The real result is just recorded here - only finishOnceResultReady()
        // (driven by the cosmetic animation's own end callback / poll loop)
        // ever acts on it, so there's exactly one place that decides what
        // happens next regardless of which finishes first.
        boolean[] resultReady = {false};
        boolean[] resultSuccess = {false};
        String[] resultMessage = {null};

        RoomRepository.getInstance(this).refreshRooms(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                resultReady[0] = true;
                resultSuccess[0] = true;
            }

            @Override
            public void onError(String message) {
                resultReady[0] = true;
                resultSuccess[0] = false;
                resultMessage[0] = message;
            }
        });

        animateProgress(CONNECTIVITY_END, CONNECTING_END, CONNECTING_DURATION_MS, () -> {
            loadingMessageText.setText(R.string.introduction_loading_hotel_info);
            animateProgress(CONNECTING_END, BACKEND_PHASE_CAP, LOADING_INFO_DURATION_MS,
                    () -> finishOnceResultReady(resultReady, resultSuccess, resultMessage));
        });
    }

    private void finishOnceResultReady(boolean[] resultReady, boolean[] resultSuccess, String[] resultMessage) {
        if (!resultReady[0]) {
            // Cosmetic pacing finished before the real network call did (a
            // slow connection) - hold at the capped percentage and keep
            // checking rather than either stalling silently or faking 100.
            // Distinct wording from the success-path "Almost ready..." below:
            // this state can genuinely last several more seconds, so it
            // shouldn't imply imminent completion. OkHttp's own configured
            // timeouts (ApiClient) still bound the total wait, so this can't
            // poll forever.
            loadingMessageText.setText(R.string.introduction_slow_connection);
            startupHandler.postDelayed(() -> finishOnceResultReady(resultReady, resultSuccess, resultMessage), RESULT_POLL_INTERVAL_MS);
            return;
        }

        isChecking = false;
        if (resultSuccess[0]) {
            loadingMessageText.setText(R.string.introduction_almost_ready);
            animateProgress(currentProgress, ALMOST_READY_PROGRESS, ALMOST_READY_DURATION_MS, () ->
                    animateProgress(ALMOST_READY_PROGRESS, 100, COMPLETE_DURATION_MS, () -> {
                        loadingMessageText.setText(R.string.introduction_loading_complete);
                        startupHandler.postDelayed(this::openLanding, READY_HOLD_MS);
                    }));
        } else {
            // Technical detail logged only - the guest sees one honest,
            // non-technical message rather than a raw exception string.
            Log.e(TAG, "Startup backend check failed: " + resultMessage[0]);
            showError(getString(R.string.introduction_error_title),
                    getString(R.string.introduction_error_backend_desc));
        }
    }

    /** Animates currentProgress from..to over durationMs, keeping the bar and "%" label in lockstep, then runs onEnd. */
    private void animateProgress(int from, int to, long durationMs, Runnable onEnd) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(durationMs);
        animator.addUpdateListener(animation -> updateProgress((int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onEnd.run();
            }
        });
        animator.start();
    }

    /** The one place that writes both the progress bar and the "%" text - see the class doc on currentProgress. */
    private void updateProgress(int value) {
        currentProgress = Math.max(0, Math.min(100, value));
        introductionProgress.setProgress(currentProgress);
        loadingPercentText.setText(getString(R.string.introduction_progress_percent_format, currentProgress));
    }

    private void showLoadingPanel() {
        loadingPanel.setVisibility(View.VISIBLE);
        errorPanel.setVisibility(View.GONE);
    }

    private void showError(String title, String description) {
        loadingPanel.setVisibility(View.GONE);
        errorTitleText.setText(title);
        errorDescriptionText.setText(description);
        errorPanel.setVisibility(View.VISIBLE);
        btnIntroductionRetry.setEnabled(true);
    }

    private void openLanding() {
        if (hasNavigated) return;
        hasNavigated = true;
        Intent intent = new Intent(MainActivity.this, LandingActivity.class);
        startActivity(intent);
        finish(); // Prevents returning to splash
    }

    private void startPulse(View view) {
        view.animate()
                .alpha(0.3f)
                .setDuration(800)
                .withEndAction(() -> view.animate()
                        .alpha(1f)
                        .setDuration(800)
                        .withEndAction(() -> {
                            // Stop once the panel it belongs to is no longer
                            // shown (e.g. a check failed and errorPanel took
                            // over) instead of pulsing forever in the background.
                            if (loadingPanel.getVisibility() == View.VISIBLE) startPulse(view);
                        })
                        .start())
                .start();
    }

    private void revealLoadingView(View view) {
        view.setTranslationY(12f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450L)
                .start();
    }
}
