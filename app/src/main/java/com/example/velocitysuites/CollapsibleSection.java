package com.example.velocitysuites;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Collapse / expand control for one Dashboard section; the section starts collapsed. Collapsed never means hidden:
 * <ul>
 *   <li><b>List sections</b> ({@link #previewList}) keep their list on screen - collapsed shows the first
 *       {@link SectionPreview#PREVIEW_COUNT} items and a "Show all (N)" button, expanded shows every item and "Show less".
 *       The section only reports the change ({@code onChange}); the screen redraws its list from its own data.</li>
 *   <li><b>Other sections</b> ({@link #collapsedHides}) show a compact version (for example the first row of tiles)
 *       and reveal the rest when expanded.</li>
 *   <li>The plain form hides the whole body and shows a one-line summary instead (the Need help? card).</li>
 * </ul>
 * Tapping the header row or its chevron toggles it; the chevron turns over with a short animation. The choice is
 * remembered per section for as long as the app's preferences live, so it survives scrolling, rotation and a trip to
 * another screen.
 */
final class CollapsibleSection {

    private static final String PREFS = "VelocityPrefs";
    /** v2: the section now starts collapsed (v1 started expanded and meant "hide the body"). */
    private static final String KEY_PREFIX = "dashboard_section_v2_expanded_";
    private static final long TURN_MS = 180L;

    private final SharedPreferences prefs;
    private final String key;
    private final ImageView toggle;
    @Nullable private final TextView summary;
    private final View[] bodies;
    private boolean expanded;
    private CharSequence summaryText = "";
    private boolean previewMode;
    @Nullable private Runnable onChange;
    private View[] collapsedHides = new View[0];
    @Nullable private String title;
    private boolean animated;
    @Nullable private View header;

    /**
     * @param toggle  the chevron button (its parent row is made tappable too)
     * @param summary the one-line summary shown while collapsed (plain form only)
     * @param bodies  everything that is hidden while collapsed (plain form only)
     */
    CollapsibleSection(@NonNull android.content.Context context, @NonNull String key, @NonNull ImageView toggle,
                       @Nullable TextView summary, @NonNull View... bodies) {
        this(context, key, toggle, summary, false, bodies);
    }

    /** As above, but {@code startsExpanded} is what the section shows until the guest first changes it. */
    CollapsibleSection(@NonNull android.content.Context context, @NonNull String key, @NonNull ImageView toggle,
                       @Nullable TextView summary, boolean startsExpanded, @NonNull View... bodies) {
        this.prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        this.key = key;
        this.toggle = toggle;
        this.summary = summary;
        this.bodies = bodies;
        this.expanded = prefs.getBoolean(KEY_PREFIX + key, startsExpanded);
        View.OnClickListener flip = v -> setExpanded(!expanded);
        toggle.setOnClickListener(flip);
        if (toggle.getParent() instanceof View) {
            header = (View) toggle.getParent();
            header.setOnClickListener(flip);
        }
        apply(false);
    }

    /** A list section: the list stays visible, {@code onChange} redraws it (the preview or everything). */
    CollapsibleSection previewList(@Nullable Runnable onChange) {
        this.previewMode = true;
        this.onChange = onChange;
        apply(false);
        return this;
    }

    /** These views are shown only while expanded; the rest of the section stays visible when collapsed. */
    CollapsibleSection collapsedHides(@NonNull View... views) {
        this.previewMode = true;
        this.collapsedHides = views;
        apply(false);
        return this;
    }

    /** The section's name: the header and chevron then read "Collapse Your Next Stay" / "Expand Your Next Stay", and a change is announced. */
    CollapsibleSection named(@NonNull String name) {
        this.title = name;
        apply(false);
        return this;
    }

    /** Opens and closes the body with a short height animation (plain form only). */
    CollapsibleSection animated() {
        this.animated = true;
        return this;
    }

    /** Told after every change, whatever the form. */
    CollapsibleSection onChange(@Nullable Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    boolean isExpanded() {
        return expanded;
    }

    /** Sets the state and notifies the list (also what the "Show all / Show less" button calls). */
    void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        prefs.edit().putBoolean(KEY_PREFIX + key, expanded).apply();
        apply(true);
        if (onChange != null) onChange.run();
    }

    void toggle() {
        setExpanded(!expanded);
    }

    /** Sets the collapsed-state summary of the plain form; shown immediately if the section is collapsed. */
    void setSummary(@Nullable CharSequence text) {
        summaryText = text == null ? "" : text;
        if (previewMode || summary == null) return;
        summary.setText(summaryText);
        summary.setVisibility(!expanded && summaryText.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void apply(boolean animate) {
        if (previewMode) {
            for (View body : bodies) body.setVisibility(View.VISIBLE); // collapsed never means hidden
            for (View v : collapsedHides) v.setVisibility(expanded ? View.VISIBLE : View.GONE);
            if (summary != null) summary.setVisibility(View.GONE);
        } else {
            for (View body : bodies) {
                if (animated) SectionAnimator.set(body, expanded, animate);
                else body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            }
            if (summary != null) {
                summary.setText(summaryText);
                summary.setVisibility(!expanded && summaryText.length() > 0 ? View.VISIBLE : View.GONE);
            }
        }
        toggle.setImageResource(R.drawable.ic_expand_more);
        float target = expanded ? 180f : 0f;
        if (animate) toggle.animate().rotation(target).setDuration(TURN_MS).start();
        else toggle.setRotation(target);
        android.content.Context context = toggle.getContext();
        String description = title == null
                ? context.getString(expanded ? R.string.section_collapse : R.string.section_expand)
                : context.getString(expanded ? R.string.section_collapse_named : R.string.section_expand_named, title);
        toggle.setContentDescription(description);
        if (title != null && header != null) {
            header.setContentDescription(description);
            if (animate) {
                header.announceForAccessibility(context.getString(
                        expanded ? R.string.section_state_expanded_named : R.string.section_state_collapsed_named, title));
            }
        }
    }
}
