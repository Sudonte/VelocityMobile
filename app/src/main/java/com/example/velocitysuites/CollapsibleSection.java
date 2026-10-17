package com.example.velocitysuites;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Collapse / expand control for one Dashboard section. Expanded shows the section's whole body; collapsed hides it and
 * shows a one-line summary (a count and the most relevant item) in its place. The choice is remembered per section.
 * Only the visibility of existing views changes - the section's data and its populate code are untouched, so a
 * collapsed section keeps refreshing and is complete the moment it is expanded again.
 */
final class CollapsibleSection {

    private static final String PREFS = "VelocityPrefs";
    private static final String KEY_PREFIX = "dashboard_section_expanded_";

    private final SharedPreferences prefs;
    private final String key;
    private final ImageView toggle;
    private final TextView summary;
    private final View[] bodies;
    private boolean expanded;
    private CharSequence summaryText = "";
    private Runnable onToggle;

    /**
     * @param toggle  the chevron button
     * @param summary the one-line summary shown while collapsed
     * @param bodies  everything that is hidden while collapsed (may be empty when the section re-renders itself, see setOnToggle)
     */
    CollapsibleSection(@NonNull android.content.Context context, @NonNull String key, @NonNull ImageView toggle,
                       @NonNull TextView summary, @NonNull View... bodies) {
        this.prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        this.key = key;
        this.toggle = toggle;
        this.summary = summary;
        this.bodies = bodies;
        this.expanded = prefs.getBoolean(KEY_PREFIX + key, true);
        toggle.setOnClickListener(v -> {
            expanded = !expanded;
            prefs.edit().putBoolean(KEY_PREFIX + this.key, expanded).apply();
            apply();
            if (onToggle != null) onToggle.run();
        });
        apply();
    }

    /** Called after the guest expands or collapses the section, for sections whose content depends on the state. */
    void setOnToggle(@Nullable Runnable onToggle) {
        this.onToggle = onToggle;
    }

    boolean isExpanded() {
        return expanded;
    }

    /** Sets the collapsed-state summary; shown immediately if the section is collapsed. */
    void setSummary(@Nullable CharSequence text) {
        summaryText = text == null ? "" : text;
        apply();
    }

    private void apply() {
        for (View body : bodies) body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        summary.setText(summaryText);
        summary.setVisibility(!expanded && summaryText.length() > 0 ? View.VISIBLE : View.GONE);
        toggle.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        toggle.setContentDescription(toggle.getContext().getString(expanded ? R.string.section_collapse : R.string.section_expand));
    }
}
