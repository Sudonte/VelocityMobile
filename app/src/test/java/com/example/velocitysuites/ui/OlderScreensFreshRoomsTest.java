package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The booking/reservation list screen and the start-up screen (MainActivity - its endless loading animation makes a launch test hang, so it is covered by the source check below) used to load rooms through the cacheable /rooms
 * endpoint, so a stale copy could be shown as available. They now use the same fresh, no-cache path as Landing,
 * Room Browsing and the booking wizard (pictures still go through Glide's normal URL cache).
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OlderScreensFreshRoomsTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(), caps);
    }

    @Test
    public void bookingAndReservationScreen_loadsRoomsFresh() {
        Robolectric.buildActivity(BookingAndReservationActivity.class,
                new Intent(app, BookingAndReservationActivity.class)).setup();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("rooms are read on the no-cache path", 1, api.count("getRoomsFresh"));
        assertEquals("never the cacheable one", 0, api.count("getRooms"));
    }

    /**
     * Only some of the screens' room loads run at launch (the date-aware ones run when a guest opens Add Room or
     * picks dates), so the source is checked too: neither screen may call the cacheable refreshRooms() at all.
     */
    @Test
    public void neitherScreenUsesTheCacheableRoomsCallAnywhere() throws Exception {
        for (String name : new String[]{"BookingAndReservationActivity.java", "MainActivity.java"}) {
            File file = new File("src/main/java/com/example/velocitysuites/" + name);
            if (!file.isFile()) file = new File("app/src/main/java/com/example/velocitysuites/" + name);
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertFalse(name + " must use refreshRoomsFresh()", source.contains(".refreshRooms("));
        }
    }
}
