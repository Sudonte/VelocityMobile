package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.TermsConsentView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import static org.robolectric.Shadows.shadowOf;

/** The shared view-then-agree block: locked until the Terms are opened, never auto-checked, state survives recreation. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TermsConsentViewTest {

    private TermsConsentView newView(Activity activity) {
        TermsConsentView view = new TermsConsentView(activity);
        view.setId(R.id.termsConsent);
        activity.setContentView(view);
        return view;
    }

    private Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.Theme_VelocitySuites);
        return activity;
    }

    @Test
    public void startsLockedWithReason_andShowsTheActionLabelInGuidance() {
        TermsConsentView view = newView(activity());
        view.setActionLabel("Confirm Booking");

        View box = view.findViewById(R.id.cbTermsAgree);
        assertFalse(box.isEnabled());
        assertFalse(view.isAccepted());
        assertEquals(View.VISIBLE, view.findViewById(R.id.tvTermsLockedHelper).getVisibility());
        assertEquals("3. Tap \"Confirm Booking\".", ((TextView) view.findViewById(R.id.tvTermsGuidanceStep3)).getText().toString());
    }

    @Test
    public void theBoxStaysLockedUntilTheGuestScrollsToTheEnd_andIsNeverAutoChecked() {
        Activity activity = activity();
        TermsConsentView view = newView(activity);
        boolean[] last = new boolean[2];
        view.setListener((viewed, accepted) -> { last[0] = viewed; last[1] = accepted; });

        view.findViewById(R.id.btnViewTerms).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue("the Terms dialog is shown", dialog != null && dialog.isShowing());
        assertFalse("opening alone does not unlock the box", view.findViewById(R.id.cbTermsAgree).isEnabled());
        assertFalse(view.isViewed());

        // the document is long: scroll to the very end
        NestedScrollView scroll = dialog.findViewById(R.id.termsScrollView);
        View content = scroll.getChildAt(0);
        assertTrue("the full Terms need scrolling here", content.getHeight() > scroll.getHeight());
        scroll.scrollTo(0, content.getHeight() - scroll.getHeight());
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(view.findViewById(R.id.cbTermsAgree).isEnabled());
        assertEquals(View.GONE, view.findViewById(R.id.tvTermsLockedHelper).getVisibility());
        assertTrue(view.isViewed());
        assertFalse("never auto-checked", view.isAccepted());
        assertTrue(last[0]);
        assertFalse(last[1]);

        // the dialog has a Done button and the 48-hour + privacy sections
        String body = ((TextView) dialog.findViewById(R.id.termsBodyText)).getText().toString();
        assertTrue(body.contains("48-Hour Advance Check-In Policy"));
        assertTrue(body.contains("RA 10173"));
        dialog.findViewById(R.id.termsDoneButton).performClick();
        assertFalse(dialog.isShowing());
    }

    @Test
    public void viewedAndAgreedSurviveRecreation() {
        Activity activity = activity();
        TermsConsentView view = newView(activity);
        view.findViewById(R.id.btnViewTerms).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        NestedScrollView scroll = ShadowDialog.getLatestDialog().findViewById(R.id.termsScrollView);
        scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
        shadowOf(Looper.getMainLooper()).idle();
        view.findViewById(R.id.cbTermsAgree).performClick();
        assertTrue(view.isAccepted());

        SparseArray<android.os.Parcelable> saved = new SparseArray<>();
        view.saveHierarchyState(saved);

        TermsConsentView recreated = newView(activity());
        recreated.restoreHierarchyState(saved);

        assertTrue(recreated.isViewed());
        assertTrue(recreated.isAccepted());
        assertTrue(recreated.findViewById(R.id.cbTermsAgree).isEnabled());
    }
}
