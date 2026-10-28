package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.dto.PromoOfferDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A promo from the admin Promotional module, as a guest sees it: title, what it does to the nightly rate, which room type it is for and
 * when it ends. {@link #estimatedSaving} is only a note until the front desk verifies the promo at check-in - it is never subtracted.
 */
public final class PromoOffer implements Serializable {
    public final long id;
    public final String title;
    public final String description;
    /** "10% off the room rate" / "₱500.00 off per night"; empty for a promo with no discount value. */
    public final String label;
    /** Null = every room type. */
    @Nullable public final String roomTypeName;
    @Nullable public final Long roomTypeId;
    /** yyyy-MM-dd; null when unknown. */
    @Nullable public final String validUntil;
    public final boolean hasMoneyEffect;
    public final double estimatedSaving;
    /** The nightly rate with the promo (a room list only); 0 when unknown. */
    public final double rateAfter;
    public final boolean applied;
    public final List<String> amenities;

    private PromoOffer(PromoOfferDto d) {
        this.id = d.id;
        this.title = d.title != null ? d.title : "";
        this.description = d.description != null ? d.description : "";
        this.label = d.label != null ? d.label : "";
        this.roomTypeName = d.room_type_name;
        this.roomTypeId = d.room_type_id;
        this.validUntil = d.valid_until;
        this.hasMoneyEffect = d.has_money_effect;
        this.estimatedSaving = Math.max(0, d.estimated_saving);
        this.rateAfter = d.rate_after != null ? d.rate_after : 0;
        this.applied = d.applied;
        this.amenities = d.amenities != null ? Collections.unmodifiableList(new ArrayList<>(d.amenities)) : Collections.<String>emptyList();
    }

    @NonNull
    public static List<PromoOffer> listOf(@Nullable List<PromoOfferDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return Collections.emptyList();
        List<PromoOffer> out = new ArrayList<>();
        for (PromoOfferDto d : dtos) if (d != null && d.title != null) out.add(new PromoOffer(d));
        return Collections.unmodifiableList(out);
    }

    /** For the wizard: a promo as the room list sent it, with its estimated saving on the guest's own rooms. */
    @NonNull
    public PromoOffer withSaving(double saving) {
        return new PromoOffer(this, saving);
    }

    private PromoOffer(PromoOffer o, double saving) {
        this.id = o.id;
        this.title = o.title;
        this.description = o.description;
        this.label = o.label;
        this.roomTypeName = o.roomTypeName;
        this.roomTypeId = o.roomTypeId;
        this.validUntil = o.validUntil;
        this.hasMoneyEffect = o.hasMoneyEffect;
        this.estimatedSaving = saving;
        this.rateAfter = o.rateAfter;
        this.applied = o.applied;
        this.amenities = o.amenities;
    }

    /** True when this promo covers the room type (a promo with no room type covers every type). */
    public boolean covers(@Nullable Long typeId) {
        return roomTypeId == null || (typeId != null && roomTypeId.equals(typeId));
    }
}
