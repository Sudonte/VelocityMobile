package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What Request Additional Amenities offers: the catalog the server sent, cleaned up. Inactive amenities are dropped and
 * each amenity appears ONCE - entries that share an id are collapsed to the first (a duplicated row in the response used
 * to show the same amenity twice). Two entries with different ids but the same name are both kept, because they are
 * different amenities as far as the server is concerned; {@link #sameNameDifferentId} lists them so that can be reported
 * as possible duplicate data. Pure rules, so they are unit-testable.
 */
final class AmenityCatalog {

    private AmenityCatalog() {
    }

    @NonNull
    static List<AddOnAmenity> cleaned(@Nullable List<AddOnAmenity> items) {
        Map<String, AddOnAmenity> byId = new LinkedHashMap<>();
        if (items != null) {
            for (AddOnAmenity amenity : items) {
                if (amenity == null || !amenity.isActive()) continue;
                byId.putIfAbsent(amenity.getId(), amenity);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** Names that appear under more than one amenity id in this catalog (candidates for duplicate data on the server). */
    @NonNull
    static List<String> sameNameDifferentId(@NonNull List<AddOnAmenity> catalog) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (AddOnAmenity amenity : catalog) {
            String name = amenity.getName() == null ? "" : amenity.getName().trim().toLowerCase(Locale.US);
            if (name.isEmpty()) continue;
            if (!seen.add(name) && !duplicates.contains(amenity.getName().trim())) duplicates.add(amenity.getName().trim());
        }
        return duplicates;
    }

    /** The description to show, or null when there is none worth showing (empty, or a placeholder such as "N/A"). */
    @Nullable
    static String descriptionOf(@NonNull AddOnAmenity amenity) {
        String d = amenity.getDescription();
        return TransactionDetailSections.isShowable(d) ? d.trim() : null;
    }
}
