package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

/**
 * DiffUtil over two parallel lists of strings: an id per row (is it the same row?) and a content key per row (does it
 * look the same?). Lets an adapter that keeps a plain mutable list report exactly which rows were inserted, removed or
 * changed, instead of telling RecyclerView that everything changed - rows whose key is unchanged are not rebound.
 */
final class ListDiff {

    private ListDiff() {
    }

    static DiffUtil.DiffResult diff(@NonNull List<String> oldIds, @NonNull List<String> oldKeys,
                                    @NonNull List<String> newIds, @NonNull List<String> newKeys) {
        return DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldIds.size(); }
            @Override public int getNewListSize() { return newIds.size(); }
            @Override public boolean areItemsTheSame(int o, int n) { return oldIds.get(o).equals(newIds.get(n)); }
            @Override public boolean areContentsTheSame(int o, int n) { return oldKeys.get(o).equals(newKeys.get(n)); }
        }, false);
    }

    /** Joins the given values into one content key; null becomes the empty string. */
    static String key(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) sb.append(part == null ? "" : part).append('\u001f');
        return sb.toString();
    }
}
