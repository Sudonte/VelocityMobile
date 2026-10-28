package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.dto.PromoInfoDto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** Every promo a transaction can use and the one the front desk applied at check-in (nothing is subtracted before that). */
public final class PromoInfo implements Serializable {
    public static final PromoInfo NONE = new PromoInfo(Collections.<PromoOffer>emptyList(), null, 0);

    public final List<PromoOffer> available;
    @Nullable public final String appliedTitle;
    public final double promoDiscount;

    public PromoInfo(@NonNull List<PromoOffer> available, @Nullable String appliedTitle, double promoDiscount) {
        this.available = available;
        this.appliedTitle = appliedTitle;
        this.promoDiscount = promoDiscount;
    }

    @NonNull
    public static PromoInfo from(@Nullable PromoInfoDto d) {
        if (d == null) return NONE;
        return new PromoInfo(PromoOffer.listOf(d.available), d.applied_title, Math.max(0, d.promo_discount));
    }

    public boolean isApplied() {
        return appliedTitle != null && !appliedTitle.isEmpty();
    }

    /** The promos still waiting for the desk (everything but the applied one). */
    @NonNull
    public List<PromoOffer> toBeVerified() {
        java.util.ArrayList<PromoOffer> out = new java.util.ArrayList<>();
        for (PromoOffer p : available) if (!p.applied) out.add(p);
        return out;
    }
}
