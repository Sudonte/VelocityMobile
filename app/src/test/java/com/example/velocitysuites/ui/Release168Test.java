package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PromoOffer;
import com.example.velocitysuites.ProfileManagementActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptImageViewerActivity;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAdapter;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.RoomDetailsDialog;
import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.PromoOfferDto;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * 1.68.0 on screen: the "1 promo" badge on a room card, the Promos section of Room Details, the Profile screen (no title, the photo with
 * its camera badge, the photo sheet, remove with a confirmation, full screen on tap, initials when there is no photo), and the photo preview
 * (Use photo / Retake) - at 360dp and 393dp, light and dark, normal and 2.0x fonts.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release168Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        ApiClient.setServiceForTests(api.proxy());
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "");
    }

    @After
    public void tearDown() {
        ApiClient.setServiceForTests(null);
        RuntimeEnvironment.setFontScale(1.0f);
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    private static PromoOffer promo(long id, String title) {
        PromoOfferDto d = new PromoOfferDto();
        d.id = id;
        d.title = title;
        d.description = "Stay two nights, save on every night.";
        d.label = "10% off the room rate";
        d.room_type_id = 1L;
        d.room_type_name = "Deluxe";
        d.valid_until = "2026-12-31";
        d.has_money_effect = true;
        d.rate_after = 3150.0;
        d.amenities = Collections.emptyList();
        return PromoOffer.listOf(Collections.singletonList(d)).get(0);
    }

    private static Room room(int promos) {
        List<RoomAmenity> amenities = new ArrayList<>();
        amenities.add(new RoomAmenity("Wi-Fi", "General", "", RoomAmenity.PRICING_COMPLIMENTARY, "0"));
        Room r = new Room("1", "Deluxe Room with City View", "Deluxe", 2, 3500.0, "About", 0, true, amenities, "1 Queen Bed", "24 sqm", "Quiet hours after 10 PM.");
        r.setAvailableCount(3);
        r.setRoomTypeId(1);
        List<PromoOffer> list = new ArrayList<>();
        for (int i = 0; i < promos; i++) list.add(promo(i + 1, "Promo " + (i + 1)));
        r.setPromos(list);
        return r;
    }

    // ================================================================ room card

    private View card(Room room, int widthDp, float fontScale) {
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.fontScale = fontScale;
        config.screenWidthDp = widthDp;
        Context ctx = new ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_VelocitySuites);
        FrameLayout parent = LayoutHarness.parentOfWidth(ctx, widthDp);
        RoomAdapter adapter = new RoomAdapter(Collections.singletonList(room), new RoomAdapter.OnRoomClickListener() {
            @Override public void onRoomClick(Room r) { }
            @Override public void onViewDetailsClick(Room r) { }
            @Override public void onBookNowClick(Room r) { }
        }, false, new HashMap<>());
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0));
        adapter.onBindViewHolder(holder, 0);
        LayoutHarness.layoutAtWidth(holder.itemView, ctx, widthDp);
        return holder.itemView;
    }

    @Test
    public void theCardSaysHowManyPromos_oneOrMore_andNothingWhenThereAreNone() {
        assertEquals("1 promo", ((TextView) card(room(1), 360, 1.0f).findViewById(R.id.roomPromoBadge)).getText().toString());
        assertEquals("2 promos", ((TextView) card(room(2), 360, 1.0f).findViewById(R.id.roomPromoBadge)).getText().toString());
        assertEquals(View.GONE, card(room(0), 360, 1.0f).findViewById(R.id.roomPromoBadge).getVisibility());
        assertEquals(View.VISIBLE, card(room(2), 360, 1.0f).findViewById(R.id.roomPromoBadge).getVisibility());
    }

    @Test
    public void thePromoBadge_sitsOnThePhoto_andNeverCoversTheAvailabilityChip_atEverySizeAndFont() {
        for (int width : new int[]{360, 393}) {
            for (float font : new float[]{1.0f, 1.3f, 2.0f}) {
                View card = card(room(2), width, font);
                Rect photo = LayoutHarness.boundsIn(card, card.findViewById(R.id.roomImageCard));
                Rect promo = LayoutHarness.boundsIn(card, card.findViewById(R.id.roomPromoBadge));
                Rect status = LayoutHarness.boundsIn(card, card.findViewById(R.id.roomStatusBadge));
                String tag = width + "dp x" + font;
                assertTrue(tag + " promo badge on the photo " + promo + " in " + photo, photo.contains(promo));
                assertTrue(tag + " availability chip on the photo", photo.contains(status));
                assertFalse(tag + " the two chips do not overlap: " + promo + " vs " + status, Rect.intersects(promo, status));
            }
        }
    }

    // ================================================================ room details

    private android.app.Dialog details(Room room) {
        androidx.appcompat.app.AppCompatActivity host = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        idle();
        RoomDetailsDialog.show(host, room, "VIEW_DETAILS", null, new RoomDetailsDialog.ActionListener() {
            @Override public void onBook(Room r) { }
            @Override public void onReserve(Room r) { }
        });
        idle();
        return ShadowDialog.getLatestDialog();
    }

    private static String allText(View v) {
        StringBuilder sb = new StringBuilder();
        collect(v, sb);
        return sb.toString();
    }

    private static void collect(View v, StringBuilder sb) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append(" | ");
        if (v instanceof android.view.ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) collect(((android.view.ViewGroup) v).getChildAt(i), sb);
        }
    }

    @Test
    public void roomDetails_listEachActivePromo_withTitleValueDescriptionAndValidUntil() {
        android.app.Dialog d = details(room(2));
        assertEquals(View.VISIBLE, d.findViewById(R.id.headPromos).getVisibility());
        assertEquals("Promos", ((TextView) d.findViewById(R.id.headPromos)).getText().toString());
        android.view.ViewGroup list = d.findViewById(R.id.layoutDetailPromos);
        assertEquals(2, list.getChildCount());
        String text = allText(list);
        assertTrue(text, text.contains("Promo 1") && text.contains("Promo 2"));
        assertTrue(text, text.contains("10% off the room rate"));
        assertTrue("what it does to THIS room's nightly rate: " + text, text.contains("₱3,500.00 → ₱3,150.00 per night (Promo)"));
        assertTrue(text, text.contains("Stay two nights, save on every night."));
        assertTrue(text, text.contains("Valid until Dec 31, 2026"));
        // the promos come before the overview, the rest of the details are still there
        View promos = d.findViewById(R.id.headPromos);
        View overview = d.findViewById(R.id.headOverview);
        android.view.ViewGroup column = (android.view.ViewGroup) promos.getParent();
        assertTrue(column.indexOfChild(promos) < column.indexOfChild(overview));
        assertNotNull(d.findViewById(R.id.headFacts));
        assertNotNull(d.findViewById(R.id.headAmenities));
        assertNotNull(d.findViewById(R.id.headPolicies));
    }

    @Test
    public void roomDetails_withoutPromos_leaveThePromosSectionOut() {
        android.app.Dialog d = details(room(0));
        assertEquals(View.GONE, d.findViewById(R.id.headPromos).getVisibility());
        assertEquals(View.GONE, d.findViewById(R.id.layoutDetailPromos).getVisibility());
        assertNotNull(d.findViewById(R.id.headFacts));
    }

    // ================================================================ profile

    private ProfileManagementActivity profile(String pictureUrl) {
        SessionManager.setProfilePictureUrl(app, pictureUrl);
        ProfileManagementActivity a = Robolectric.buildActivity(ProfileManagementActivity.class).setup().get();
        idle();
        settle();
        return a;
    }

    @Test
    public void theProfileHasNoTitleOrDescription_theCoverIsThere_andThePhotoOverlapsItsBottomEdge() {
        ProfileManagementActivity a = profile("");
        assertNull("no 'Profile Management' title", a.findViewById(R.id.screenTitle));
        assertNull("no description", a.findViewById(R.id.screenDescription));
        String all = allText(a.getWindow().getDecorView());
        assertFalse(all, all.contains("Profile Management"));

        assertNotNull(a.findViewById(R.id.profileCoverImage));
        View photo = a.findViewById(R.id.profileImageContainer);
        View cover = a.findViewById(R.id.profileCoverImage);
        assertTrue("the round photo sits over the cover's bottom edge", photo.getTop() < cover.getBottom() && photo.getBottom() > cover.getBottom());
        assertEquals("a camera badge, not a pencil", View.VISIBLE, a.findViewById(R.id.updateProfilePictureButton).getVisibility());
        String shownName = ((TextView) a.findViewById(R.id.userNameText)).getText().toString();
        assertTrue(shownName, shownName.contains("Ana") && shownName.contains("Reyes"));
        assertEquals("ana@example.com", ((TextView) a.findViewById(R.id.userEmailText)).getText().toString());
    }

    @Test
    public void withNoPhoto_theAvatarShowsTheInitials_andTapsOpenThePhotoSheet() {
        ProfileManagementActivity a = profile("");
        ImageView image = a.findViewById(R.id.profileImage);
        assertTrue("a drawable with the initials", image.getDrawable().getClass().getSimpleName().equals("InitialsDrawable"));

        image.performClick();
        idle();
        BottomSheetDialog sheet = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        assertTrue(sheet.isShowing());
        assertEquals("no photo, nothing to remove", View.GONE, sheet.findViewById(R.id.photoSheetRemove).getVisibility());
        assertNull("the viewer did not open", shadowOf(a).getNextStartedActivity());
    }

    @Test
    public void tappingThePhoto_opensItFullScreen_withPinchZoomAndAClose_andTheBadgeOpensTheSheet() {
        ProfileManagementActivity a = profile("https://example.test/storage/profile-pictures/me.jpg");
        a.findViewById(R.id.profileImage).performClick();
        Intent viewer = shadowOf(a).getNextStartedActivity();
        assertNotNull(viewer);
        assertEquals(ReceiptImageViewerActivity.class.getName(), viewer.getComponent().getClassName());
        ReceiptImageViewerActivity v = Robolectric.buildActivity(ReceiptImageViewerActivity.class, viewer).setup().get();
        assertEquals("pinch to zoom", "ZoomableImageView", v.findViewById(R.id.ivReceiptFull).getClass().getSimpleName());
        assertNotNull("a close button", v.findViewById(R.id.btnCloseReceiptViewer));
        v.findViewById(R.id.btnCloseReceiptViewer).performClick();
        assertTrue(v.isFinishing());

        a.findViewById(R.id.updateProfilePictureButton).performClick();
        idle();
        BottomSheetDialog sheet = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        assertEquals("Take a photo", ((TextView) sheet.findViewById(R.id.photoSheetTake)).getText().toString());
        assertEquals("Choose from gallery", ((TextView) sheet.findViewById(R.id.photoSheetGallery)).getText().toString());
        assertEquals("Remove photo", ((TextView) sheet.findViewById(R.id.photoSheetRemove)).getText().toString());
        assertEquals(View.VISIBLE, sheet.findViewById(R.id.photoSheetRemove).getVisibility());
        assertEquals("Cancel", ((TextView) sheet.findViewById(R.id.photoSheetCancel)).getText().toString());
    }

    @Test
    public void removingThePhoto_asksFirst_thenTheServerDropsIt_andTheInitialsComeBack() {
        ProfileManagementActivity a = profile("https://example.test/storage/profile-pictures/me.jpg");
        a.findViewById(R.id.updateProfilePictureButton).performClick();
        idle();
        ((BottomSheetDialog) ShadowDialog.getLatestDialog()).findViewById(R.id.photoSheetRemove).performClick();
        idle();

        AlertDialog confirm = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals("Remove your profile photo?", ((TextView) confirm.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
        assertEquals("nothing is sent before the guest agrees", 0, api.count("removeProfilePicture"));

        confirm.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertEquals("Cancel keeps the photo", 0, api.count("removeProfilePicture"));
        assertFalse(SessionManager.getProfilePictureUrl(app).isEmpty());

        a.findViewById(R.id.updateProfilePictureButton).performClick();
        idle();
        ((BottomSheetDialog) ShadowDialog.getLatestDialog()).findViewById(R.id.photoSheetRemove).performClick();
        idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(1, api.count("removeProfilePicture"));
        api.<ApiMessage>last("removeProfilePicture").succeed(new ApiMessage());
        idle();
        assertTrue("the picture is gone from this phone", SessionManager.getProfilePictureUrl(app).isEmpty());
        assertEquals("InitialsDrawable", ((ImageView) a.findViewById(R.id.profileImage)).getDrawable().getClass().getSimpleName());
    }

    @Test
    public void aFailedRemove_keepsThePhoto_andSaysSo() {
        ProfileManagementActivity a = profile("https://example.test/storage/profile-pictures/me.jpg");
        a.findViewById(R.id.updateProfilePictureButton).performClick();
        idle();
        ((BottomSheetDialog) ShadowDialog.getLatestDialog()).findViewById(R.id.photoSheetRemove).performClick();
        idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        api.<ApiMessage>last("removeProfilePicture").httpWithBody(500, "{\"message\":\"x\"}");
        idle();
        assertFalse("still has its photo", SessionManager.getProfilePictureUrl(app).isEmpty());
        assertEquals("Couldn't remove your photo. Please try again.", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void theProfile_isReadable_andTheSheetFits_at360And393_lightAndDark_at1xAnd2xFonts() {
        for (String q : CONFIGS) {
            for (float font : new float[]{1.0f, 2.0f}) {
                RuntimeEnvironment.setQualifiers("+" + q);
                RuntimeEnvironment.setFontScale(font);
                String tag = q + " x" + font;
                ProfileManagementActivity a = profile("https://example.test/me.jpg");
                assertEquals(tag + " the photo is shown", View.VISIBLE, a.findViewById(R.id.profileImageContainer).getVisibility());
                assertTrue(tag + " the name is shown", ((TextView) a.findViewById(R.id.userNameText)).getText().length() > 0);
                a.findViewById(R.id.updateProfilePictureButton).performClick();
                idle();
                BottomSheetDialog sheet = (BottomSheetDialog) ShadowDialog.getLatestDialog();
                for (int id : new int[]{R.id.photoSheetTake, R.id.photoSheetGallery, R.id.photoSheetRemove, R.id.photoSheetCancel}) {
                    View row = sheet.findViewById(id);
                    assertTrue(tag + " sheet row is at least 48dp tall to tap", row.getMinimumHeight() >= 0
                            && row.getLayoutParams().height >= (int) (48 * app.getResources().getDisplayMetrics().density) - 1);
                }
                sheet.dismiss();
            }
        }
    }

    // ================================================================ the preview before a photo is used

    @Test
    public void thePreview_showsTheGuidance_andRetakeOrUsePhotoDecide() {
        androidx.appcompat.app.AppCompatActivity host = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        idle();
        final List<String> events = new ArrayList<>();
        com.example.velocitysuites.PhotoPreviewDialog.show(host, Uri.parse("content://media/external/images/9"), R.string.id_photo_preview_title, R.string.id_photo_guidance,
                R.string.id_photo_use, R.string.id_photo_retake, () -> events.add("use"), () -> events.add("retake"));
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals("Check your ID photo", ((TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
        assertEquals("Make sure all four corners and the text are clear.", ((TextView) d.findViewById(R.id.photoPreviewGuidance)).getText().toString());
        assertNotNull(d.findViewById(R.id.photoPreviewImage));
        assertEquals("Use photo", d.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals("Retake", d.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        assertTrue("nothing is used before the guest decides", events.isEmpty());
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertEquals(Collections.singletonList("retake"), events);
    }
}
