package com.example.velocitysuites;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * An adapter for an exposed dropdown whose list must ALWAYS be the full list. A normal ArrayAdapter filters itself by the
 * text in its AutoCompleteTextView, so a dropdown that already shows "Bookings" would open with only "Bookings" in it -
 * the bug that left Transaction History's dropdown with a single option. This one's filter keeps every item whatever the
 * text says, so the list is complete when it first opens, after a selection and after coming back to the screen.
 */
final class NonFilteringArrayAdapter extends ArrayAdapter<String> {

    private final List<String> all;

    NonFilteringArrayAdapter(@NonNull Context context, @NonNull List<String> items) {
        super(context, android.R.layout.simple_dropdown_item_1line, items);
        this.all = items;
    }

    NonFilteringArrayAdapter(@NonNull Context context, @NonNull String[] items) {
        this(context, Arrays.asList(items));
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = all;
                results.count = all.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.count > 0) notifyDataSetChanged();
                else notifyDataSetInvalidated();
            }
        };
    }
}
