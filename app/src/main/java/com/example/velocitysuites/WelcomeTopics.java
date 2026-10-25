package com.example.velocitysuites;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * What each Welcome card explains when it is tapped: a title, one or two sentences and numbered steps. Everything is read from the app's own
 * flows and policy texts (the wizard's step names, the five GCash steps, the 20%–50% rule, the three receipts, the cancel rule, the
 * hotel's contact details) - see strings.xml, welcome_topic_*.
 */
final class WelcomeTopics {

    static final int BOOKING = 0;
    static final int GCASH = 1;
    static final int TRACK = 2;
    static final int SUPPORT = 3;
    static final int COUNT = 4;

    final int id;
    @DrawableRes final int icon;
    @StringRes final int title;
    @StringRes final int intro;
    /** Pairs of (title, description) string resources, in order. */
    final int[][] steps;
    /** The front-desk topic also shows the hotel's phone and email (already in the app). */
    final boolean contact;

    private WelcomeTopics(int id, int icon, int title, int intro, boolean contact, int[]... steps) {
        this.id = id;
        this.icon = icon;
        this.title = title;
        this.intro = intro;
        this.contact = contact;
        this.steps = steps;
    }

    private static int[] s(int title, int desc) {
        return new int[]{title, desc};
    }

    @NonNull
    static WelcomeTopics of(int id) {
        switch (id) {
            case GCASH:
                return new WelcomeTopics(GCASH, R.drawable.ic_payment_card, R.string.welcome_point_pay_title, R.string.welcome_topic_gcash_intro, false,
                        s(R.string.welcome_topic_gcash_s1_title, R.string.welcome_topic_gcash_s1_desc),
                        s(R.string.welcome_topic_gcash_s2_title, R.string.welcome_topic_gcash_s2_desc),
                        s(R.string.welcome_topic_gcash_s3_title, R.string.welcome_topic_gcash_s3_desc),
                        s(R.string.welcome_topic_gcash_s4_title, R.string.welcome_topic_gcash_s4_desc),
                        s(R.string.welcome_topic_gcash_s5_title, R.string.welcome_topic_gcash_s5_desc));
            case TRACK:
                return new WelcomeTopics(TRACK, R.drawable.ic_transaction_history, R.string.welcome_point_track_title, R.string.welcome_topic_track_intro, false,
                        s(R.string.welcome_topic_track_s1_title, R.string.welcome_topic_track_s1_desc),
                        s(R.string.welcome_topic_track_s2_title, R.string.welcome_topic_track_s2_desc),
                        s(R.string.welcome_topic_track_s3_title, R.string.welcome_topic_track_s3_desc),
                        s(R.string.welcome_topic_track_s4_title, R.string.welcome_topic_track_s4_desc),
                        s(R.string.welcome_topic_track_s5_title, R.string.welcome_topic_track_s5_desc));
            case SUPPORT:
                return new WelcomeTopics(SUPPORT, R.drawable.ic_headset, R.string.welcome_point_notify_title, R.string.welcome_topic_support_intro, true,
                        s(R.string.welcome_topic_support_s1_title, R.string.welcome_topic_support_s1_desc),
                        s(R.string.welcome_topic_support_s2_title, R.string.welcome_topic_support_s2_desc),
                        s(R.string.welcome_topic_support_s3_title, R.string.welcome_topic_support_s3_desc));
            case BOOKING:
            default:
                return new WelcomeTopics(BOOKING, R.drawable.ic_bed, R.string.welcome_point_rooms_title, R.string.welcome_topic_booking_intro, false,
                        s(R.string.welcome_topic_booking_s1_title, R.string.welcome_topic_booking_s1_desc),
                        s(R.string.welcome_topic_booking_s2_title, R.string.welcome_topic_booking_s2_desc),
                        s(R.string.welcome_topic_booking_s3_title, R.string.welcome_topic_booking_s3_desc),
                        s(R.string.welcome_topic_booking_s4_title, R.string.welcome_topic_booking_s4_desc),
                        s(R.string.welcome_topic_booking_s5_title, R.string.welcome_topic_booking_s5_desc),
                        s(R.string.welcome_topic_booking_s6_title, R.string.welcome_topic_booking_s6_desc),
                        s(R.string.welcome_topic_booking_s7_title, R.string.welcome_topic_booking_s7_desc));
        }
    }
}
