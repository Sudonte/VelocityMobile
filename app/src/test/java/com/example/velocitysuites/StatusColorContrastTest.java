package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
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
 * "Text must have readable contrast": every payment-status badge color pair, in BOTH themes, must reach the
 * WCAG AA ratio for small text (4.5:1). Reads the real values/ and values-night/ colors.xml, so changing a
 * color to something unreadable fails here rather than on someone's phone.
 */
public class StatusColorContrastTest {

    private static final String[] STATUSES = {"pending", "paid", "partial", "cancelled", "rejected"};
    private static final double AA_SMALL_TEXT = 4.5;

    private static Map<String, Integer> readColors(String relativePath) throws Exception {
        // Gradle runs unit tests with the module directory (app/) as the working directory.
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

    private static double luminance(int rgb) {
        double r = channel((rgb >> 16) & 0xFF);
        double g = channel((rgb >> 8) & 0xFF);
        double b = channel(rgb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private void assertReadable(String theme, Map<String, Integer> colors) {
        for (String status : STATUSES) {
            Integer bg = colors.get("status_" + status + "_bg");
            Integer fg = colors.get("status_" + status + "_fg");
            assertTrue(theme + ": status_" + status + " colors must be defined", bg != null && fg != null);
            double ratio = contrast(bg, fg);
            assertTrue(String.format("%s: %s badge contrast %.2f:1 is below %.1f:1", theme, status, ratio, AA_SMALL_TEXT),
                    ratio >= AA_SMALL_TEXT);
        }
    }

    @Test
    public void lightThemeBadgesAreReadable() throws Exception {
        assertReadable("light", readColors("src/main/res/values/colors.xml"));
    }

    @Test
    public void darkThemeBadgesAreReadable() throws Exception {
        assertReadable("dark", readColors("src/main/res/values-night/colors.xml"));
    }

    @Test
    public void everyStatusHasItsOwnHue_notJustItsOwnName() throws Exception {
        Map<String, Integer> light = readColors("src/main/res/values/colors.xml");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (String status : STATUSES) seen.add(light.get("status_" + status + "_fg"));
        assertEquals("each status needs a visibly different text color", STATUSES.length, seen.size());
    }

    @Test
    public void contrastMathMatchesKnownReferences() {
        assertEquals(21.0, contrast(0x000000, 0xFFFFFF), 0.01);   // black on white
        assertEquals(1.0, contrast(0x777777, 0x777777), 0.001);   // identical
    }
}
