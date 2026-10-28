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
 * The theme tokens every guest screen is built from stay readable in BOTH modes: body text, secondary text and the red
 * "action" text (receipt highlights, balances) on the three surfaces they sit on (card, screen, soft chip), and white
 * text on the brand red. Reads the real values/ and values-night/ colors.xml (night falls back to light for a name it
 * does not override, exactly like Android).
 */
public class ThemeTokenContrastTest {

    private static final double AA_SMALL = 4.5;

    private static Map<String, Integer> read(String path) throws Exception {
        File f = new File(path);
        assertTrue("missing " + f.getAbsolutePath(), f.exists());
        String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<color name=\"([a-z_0-9]+)\">#([0-9A-Fa-f]{6,8})</color>").matcher(xml);
        Map<String, Integer> out = new HashMap<>();
        while (m.find()) {
            String hex = m.group(2);
            out.put(m.group(1), (int) Long.parseLong(hex.length() == 8 ? hex.substring(2) : hex, 16));
        }
        return out;
    }

    private static Map<String, Integer> theme(boolean night) throws Exception {
        Map<String, Integer> colors = new HashMap<>(read("src/main/res/values/colors.xml"));
        if (night) colors.putAll(read("src/main/res/values-night/colors.xml"));
        return colors;
    }

    private static void check(String mode, Map<String, Integer> c, String fg, String bg) {
        assertTrue(mode + ": " + fg + " is not defined", c.containsKey(fg));
        assertTrue(mode + ": " + bg + " is not defined", c.containsKey(bg));
        double ratio = StatusColorContrastTest.contrast(c.get(fg), c.get(bg));
        assertTrue(String.format("%s: %s on %s is only %.2f:1", mode, fg, bg, ratio), ratio >= AA_SMALL);
    }

    private void checkMode(String mode, boolean night) throws Exception {
        Map<String, Integer> c = theme(night);
        for (String surface : new String[]{"velocity_card_surface", "velocity_red_bg_end", "velocity_red_soft", "velocity_surface_elevated"}) {
            check(mode, c, "velocity_text_primary", surface);
            check(mode, c, "velocity_text_secondary", surface);
            check(mode, c, "velocity_action_text", surface);
            check(mode, c, "velocity_success_text", surface); // the green of "Nothing to pay"
        }
        check(mode, c, "white", "velocity_red_primary"); // button text on the brand red
    }

    @Test
    public void lightModeTextIsReadableOnEverySurface() throws Exception {
        checkMode("light", false);
    }

    @Test
    public void darkModeTextIsReadableOnEverySurface() throws Exception {
        checkMode("dark", true);
    }

    @Test
    public void theCardSurfaceTokenFlipsBetweenModes() throws Exception {
        assertTrue(theme(false).get("velocity_card_surface") != theme(true).get("velocity_card_surface").intValue());
    }
}
