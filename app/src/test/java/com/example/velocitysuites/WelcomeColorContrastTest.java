package com.example.velocitysuites;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Good contrast" on the Welcome screen, in BOTH themes: every text/background pair the screen uses reaches the
 * WCAG AA ratio for small text (4.5:1), and the icons reach 3:1. Reads the real values/ and values-night/
 * colors.xml (night only overrides some names - the rest fall back to the day value, exactly as on a device), so
 * swapping in a colour with no dark-mode override (the headline used to be red_dark, #7F1212 - 1.6:1 on the dark
 * background) fails here instead of on someone's phone.
 */
public class WelcomeColorContrastTest {

    private static final double AA_SMALL_TEXT = 4.5;
    private static final double AA_GRAPHIC = 3.0;

    private static Map<String, Integer> readColors(String relativePath) throws Exception {
        File file = new File(relativePath);
        assertTrue("missing " + file.getAbsolutePath(), file.exists());
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<color name=\"([a-z_0-9]+)\">#([0-9A-Fa-f]{6,8})</color>").matcher(xml);
        Map<String, Integer> colors = new HashMap<>();
        while (m.find()) {
            String hex = m.group(2);
            colors.put(m.group(1), (int) Long.parseLong(hex.length() == 8 ? hex.substring(2) : hex, 16));
        }
        return colors;
    }

    private static Map<String, Integer> theme(boolean night) throws Exception {
        Map<String, Integer> colors = readColors("src/main/res/values/colors.xml");
        if (night) colors.putAll(readColors("src/main/res/values-night/colors.xml"));
        return colors;
    }

    private static int color(Map<String, Integer> colors, String name) {
        Integer value = colors.get(name);
        assertTrue("colour " + name + " must be defined", value != null);
        return value;
    }

    private static void assertRatio(String what, Map<String, Integer> colors, String foreground, String background, double minimum) {
        double ratio = StatusColorContrastTest.contrast(color(colors, foreground), color(colors, background));
        assertTrue(String.format("%s: %s on %s is %.2f:1, below %.1f:1", what, foreground, background, ratio, minimum), ratio >= minimum);
    }

    private void assertWelcomeReadable(boolean night) throws Exception {
        Map<String, Integer> colors = theme(night);
        String theme = night ? "dark" : "light";
        // the page background is a gradient between these two
        for (String page : new String[]{"velocity_red_bg_start", "velocity_red_bg_end"}) {
            assertRatio(theme + " headline / value-point titles", colors, "velocity_text_primary", page, AA_SMALL_TEXT);
            assertRatio(theme + " supporting line / value-point descriptions", colors, "velocity_text_secondary", page, AA_SMALL_TEXT);
        }
        assertRatio(theme + " primary action label", colors, "white", "velocity_red_primary", AA_SMALL_TEXT);
        assertRatio(theme + " secondary action label", colors, "velocity_action_text", "velocity_surface_elevated", AA_SMALL_TEXT);
        assertRatio(theme + " value-point icons", colors, "velocity_action_text", "velocity_red_soft", AA_GRAPHIC);
        assertRatio(theme + " back arrow", colors, "velocity_red_primary", "white", AA_GRAPHIC);
    }

    @Test
    public void lightThemeWelcomeIsReadable() throws Exception {
        assertWelcomeReadable(false);
    }

    @Test
    public void darkThemeWelcomeIsReadable() throws Exception {
        assertWelcomeReadable(true);
    }
}
