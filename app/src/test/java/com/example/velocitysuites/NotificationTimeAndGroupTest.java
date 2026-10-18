package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@RunWith(AndroidJUnit4.class)
@org.robolectric.annotation.Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class NotificationTimeAndGroupTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();
    private static final ZoneId MNL = ZoneId.of("Asia/Manila");

    private static long at(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, MNL).toInstant().toEpochMilli();
    }

    @Test
    public void timeText_isShortAndNormalCase() {
        long now = at(2026, 10, 19, 12, 0);
        assertEquals("Just now", NotificationTimeText.of(ctx, now - 20_000, now, null));
        assertEquals("4m ago", NotificationTimeText.of(ctx, now - 4 * 60_000, now, null));
        assertEquals("3h ago", NotificationTimeText.of(ctx, now - 3 * 3_600_000L, now, null));
        assertEquals("Yesterday", NotificationTimeText.of(ctx, at(2026, 10, 18, 9, 0), now, null));
        assertEquals("Oct 17", NotificationTimeText.of(ctx, at(2026, 10, 17, 9, 0), now, null));
        assertEquals("Oct 18, 2025", NotificationTimeText.of(ctx, at(2025, 10, 18, 9, 0), now, null));
        assertEquals("fallback", NotificationTimeText.of(ctx, 0, now, "fallback"));
    }

    @Test
    public void yesterday_isTheManilaCalendarDay() {
        long now = at(2026, 10, 19, 1, 0);
        assertEquals("Yesterday", NotificationTimeText.of(ctx, at(2026, 10, 18, 0, 30) , now, null));
    }

    @Test
    public void unreadBadgeText_capsAt99Plus() {
        assertEquals("99", NotificationRowBinder.badgeText(ctx, 99));
        assertEquals("99+", NotificationRowBinder.badgeText(ctx, 100));
        assertEquals("99+", NotificationRowBinder.badgeText(ctx, 200));
    }

    @Test
    public void showAllText_capsAbove99() {
        assertEquals("Show all (4)", SectionPreview.showAllText(ctx, 4));
        assertEquals("Show all (99+)", SectionPreview.showAllText(ctx, 200));
    }

    @Test
    public void amenityNotifications_haveTheirOwnIconAndChip_butStayUnderTheirRealCategory() {
        Notification n = new Notification("1", "Amenity Request Approved", "m", "x", Notification.TYPE_BOOKING, false, "5");
        assertEquals(R.drawable.ic_amenity_concierge, NotificationCategoryPresenter.resolve(n).iconRes);
        assertEquals(R.string.notif_category_amenity, NotificationCategoryPresenter.resolve(n).labelRes);
        Notification promo = new Notification("2", "Amenities sale", "m", "x", Notification.TYPE_PROMOTION, false, null);
        assertEquals("a promotion is never an amenity request", R.drawable.ic_star, NotificationCategoryPresenter.resolve(promo).iconRes);
    }
}
