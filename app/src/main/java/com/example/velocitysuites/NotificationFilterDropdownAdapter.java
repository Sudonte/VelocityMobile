package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

/**
 * Renders the "Filter by status" combo box's dropdown popup - one row per
 * filter option, each showing its category icon (NotificationCategoryPresenter,
 * the same mapping the notification cards and Transaction History use) and a
 * checkmark on whichever option is currently selected. The underlying
 * ArrayAdapter data is the plain option label text (used only for
 * MaterialAutoCompleteTextView's own internal bookkeeping) - NotificationActivity
 * sets the box's own displayed text explicitly after a selection, so this
 * adapter's job is purely rendering the popup rows, never the closed box itself.
 * <p>
 * Never filterable/editable - NotificationActivity's dropdown has
 * android:inputType="none" (the guest picks, never types), so getFilter()'s
 * default no-op behavior (ArrayAdapter re-shows the full list regardless of any
 * text) is exactly what's wanted here, not overridden.
 */
public class NotificationFilterDropdownAdapter extends ArrayAdapter<String> {

    /** Position-matched with the label array - each row's "All"/"Unread"/Notification.TYPE_... key, for its icon lookup. */
    private final String[] filterKeys;
    @Nullable
    private String selectedKey;

    public NotificationFilterDropdownAdapter(@NonNull Context context, String[] labels, String[] filterKeys) {
        super(context, R.layout.item_notification_filter_dropdown, labels);
        this.filterKeys = filterKeys;
    }

    public void setSelectedKey(@Nullable String selectedKey) {
        this.selectedKey = selectedKey;
        notifyDataSetChanged();
    }

    /** Replaces every row's label (e.g. to refresh unread-count suffixes) without touching filterKeys/selection - position-matched with the array this adapter was constructed with, so the length must stay the same. */
    public void updateLabels(String[] newLabels) {
        clear();
        addAll(newLabels);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView != null ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.item_notification_filter_dropdown, parent, false);

        MaterialCardView iconContainer = view.findViewById(R.id.iconContainerFilterOption);
        ImageView icon = view.findViewById(R.id.ivFilterOptionIcon);
        TextView label = view.findViewById(R.id.tvFilterOptionLabel);
        ImageView check = view.findViewById(R.id.ivFilterOptionCheck);

        label.setText(getItem(position));

        String key = filterKeys[position];
        // "All"/"Unread" are views, not real notification categories (see the
        // task's own distinction) - they get the same generic bell look every
        // other unrecognized type already falls back to in
        // NotificationCategoryPresenter, rather than a fabricated category icon.
        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(key);
        icon.setImageResource(category.iconRes);
        iconContainer.setCardBackgroundColor(getContext().getColor(category.bgColorRes));
        icon.setColorFilter(getContext().getColor(category.fgColorRes));

        boolean isSelected = key.equals(selectedKey);
        check.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        label.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        return view;
    }
}
