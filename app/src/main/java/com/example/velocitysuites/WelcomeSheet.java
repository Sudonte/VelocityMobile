package com.example.velocitysuites;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * The bottom sheet one Welcome card opens: icon and title with a close ×, a short explanation, numbered steps (fading in one after the other),
 * then "Create an account" and "Sign in". Only one sheet is ever open; Back closes it before the screen.
 */
final class WelcomeSheet {

    private WelcomeSheet() {
    }

    interface Listener {
        void onCreateAccount();

        void onSignIn();

        void onDismissed();
    }

    /** The sheet showing now, if any (a second tap on a card while it is open does nothing). */
    private static java.lang.ref.WeakReference<BottomSheetDialog> current = new java.lang.ref.WeakReference<>(null);

    /** The screen that sheet belongs to: a sheet left over from a screen that is gone does not count. */
    private static java.lang.ref.WeakReference<Activity> owner = new java.lang.ref.WeakReference<>(null);

    static boolean isShowing(@NonNull Activity activity) {
        Activity o = owner.get();
        BottomSheetDialog shown = current.get();
        return shown != null && shown.isShowing() && o == activity && !activity.isFinishing() && !activity.isDestroyed();
    }

    @android.annotation.SuppressLint("InflateParams") // a dialog has no parent to inflate against
    static void show(@NonNull Activity activity, int topicId, @NonNull Listener listener) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (isShowing(activity)) return;
        WelcomeTopics topic = WelcomeTopics.of(topicId);
        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_welcome_topic, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(view);
        View surface = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (surface != null) surface.setBackgroundResource(android.R.color.transparent); // our own rounded surface is the sheet
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        current = new java.lang.ref.WeakReference<>(dialog);
        owner = new java.lang.ref.WeakReference<>(activity);
        dialog.setOnDismissListener(d -> {
            if (current.get() == dialog) current = new java.lang.ref.WeakReference<>(null);
            listener.onDismissed();
        });

        ((android.widget.ImageView) view.findViewById(R.id.welcomeSheetIcon)).setImageResource(topic.icon);
        ((TextView) view.findViewById(R.id.welcomeSheetTitle)).setText(topic.title);
        ((TextView) view.findViewById(R.id.welcomeSheetIntro)).setText(topic.intro);
        view.findViewById(R.id.welcomeSheetClose).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.welcomeSheetCreate).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onCreateAccount();
        });
        view.findViewById(R.id.welcomeSheetSignIn).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onSignIn();
        });
        android.text.SpannableString line = new android.text.SpannableString(activity.getString(R.string.welcome_sheet_sign_in));
        String link = activity.getString(R.string.welcome_sign_in_link);
        int at = line.toString().lastIndexOf(link);
        if (at >= 0) {
            line.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, at + link.length(), 0);
            line.setSpan(new android.text.style.ForegroundColorSpan(activity.getColor(R.color.velocity_action_text)), at, at + link.length(), 0);
        }
        ((TextView) view.findViewById(R.id.welcomeSheetSignIn)).setText(line);

        LinearLayout steps = view.findViewById(R.id.welcomeSheetSteps);
        boolean motion = WelcomeEffects.enabled(activity);
        for (int i = 0; i < topic.steps.length; i++) {
            View step = LayoutInflater.from(activity).inflate(R.layout.item_welcome_step, steps, false);
            ((TextView) step.findViewById(R.id.stepNumber)).setText(String.valueOf(i + 1));
            ((TextView) step.findViewById(R.id.stepTitle)).setText(topic.steps[i][0]);
            ((TextView) step.findViewById(R.id.stepDesc)).setText(topic.steps[i][1]);
            steps.addView(step);
            if (motion) {
                step.setAlpha(0f);
                step.setTranslationY(8 * activity.getResources().getDisplayMetrics().density);
                step.animate().alpha(1f).translationY(0f).setStartDelay(120L + i * 60L).setDuration(WelcomeEffects.STEP_MS).start();
            }
        }
        if (topic.contact) addContact(activity, view.findViewById(R.id.welcomeSheetContact));

        // the body never grows past about 60% of the screen: more than that scrolls inside the sheet
        ((MaxHeightNestedScrollView) view.findViewById(R.id.welcomeSheetScroll)).setMaxHeightPx((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6f));
        SafeDialogs.show(dialog);
    }

    /** The hotel's own phone and email, as already shown on the Welcome/landing screens (never invented here). */
    private static void addContact(Activity activity, LinearLayout box) {
        box.setVisibility(View.VISIBLE);
        String phone = activity.getString(R.string.landing_hotel_phone);
        String email = activity.getString(R.string.landing_hotel_email);
        box.addView(link(activity, activity.getString(R.string.welcome_topic_support_call, phone), R.drawable.ic_phone, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))));
        box.addView(link(activity, activity.getString(R.string.welcome_topic_support_email, email), R.drawable.ic_email,
                new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email)).putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.support_email_subject))));
    }

    private static View link(Activity activity, String text, int icon, Intent intent) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(ContextCompat.getColor(activity, R.color.velocity_action_text));
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        tv.setCompoundDrawablePadding(Math.round(12 * activity.getResources().getDisplayMetrics().density));
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(tv, android.content.res.ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.velocity_red_primary)));
        tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tv.setMinHeight(Math.round(48 * activity.getResources().getDisplayMetrics().density));
        tv.setPadding(Math.round(6 * activity.getResources().getDisplayMetrics().density), 0, 0, 0);
        tv.setBackgroundResource(selectable(activity));
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setOnClickListener(v -> {
            try {
                activity.startActivity(intent);
            } catch (RuntimeException ignored) {
                // no phone or mail app: the number and address are still on screen
            }
        });
        return tv;
    }

    private static int selectable(Activity activity) {
        android.util.TypedValue tv = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }
}
