package com.example.velocitysuites;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** No screen, dialog, style, theme or string turns text into ALL CAPS or adds letter-spacing (except the allowed brand and receipt stamps below). */
public class NoAllCapsOrLetterSpacingTest {

    private static final Pattern CAPS = Pattern.compile("(android:textAllCaps=\"true\"|name=\"android:textAllCaps\">\\s*true<|\\.setAllCaps\\(true\\))");
    private static final Pattern SPACING = Pattern.compile(
            "(android:letterSpacing=\"|name=\"android:letterSpacing\">|\\.setLetterSpacing\\()\\s*(-?[0-9]*\\.?[0-9]+)");

    /**
     * The only strings that are ALL CAPS on purpose:
     *  - welcome_brand: the "VELOCITY SUITES" brand label, a wordmark rather than running text;
     *  - receipt_*: the receipt's printed stamp wording ("PAID IN FULL", "PARTIALLY PAID", "FULL PAYMENT" ...), which follows
     *    the hotel's printed receipts and must read the same on screen and on paper.
     * Everything else (buttons, roles, statuses, labels) is Title Case or sentence case.
     */
    private static boolean allowedAllCaps(String stringName) {
        return stringName.equals("welcome_brand") || stringName.startsWith("receipt_");
    }

    private static final Pattern STRING = Pattern.compile("<string name=\"([^\"]+)\"[^>]*>([^<]*)</string>");

    private static void collect(File dir, String suffix, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, suffix, out);
            else if (f.getName().endsWith(suffix)) out.add(f);
        }
    }

    private static List<String> offenders() throws Exception {
        File src = new File("src/main");
        if (!src.isDirectory()) src = new File("app/src/main");
        List<File> files = new ArrayList<>();
        collect(new File(src, "res"), ".xml", files);
        collect(new File(src, "java"), ".java", files);
        List<String> bad = new ArrayList<>();
        for (File f : files) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (CAPS.matcher(text).find()) bad.add(f.getName() + ": ALL CAPS");
            Matcher m = SPACING.matcher(text);
            while (m.find()) {
                if (Math.abs(Double.parseDouble(m.group(2))) > 0.0) bad.add(f.getName() + ": letter-spacing " + m.group(2));
            }
        }
        File strings = new File(src, "res/values/strings.xml");
        String xml = new String(Files.readAllBytes(strings.toPath()), StandardCharsets.UTF_8);
        Matcher sm = STRING.matcher(xml);
        while (sm.find()) {
            String letters = sm.group(2).replaceAll("[^A-Za-z]", "");
            if (letters.length() >= 4 && letters.equals(letters.toUpperCase()) && !allowedAllCaps(sm.group(1))) {
                bad.add("string " + sm.group(1) + " is ALL CAPS: " + sm.group(2));
            }
        }
        return bad;
    }

    @Test
    public void noLayoutStyleOrCodeUsesAllCapsOrLetterSpacing() throws Exception {
        List<String> bad = offenders();
        assertTrue("found: " + bad, bad.isEmpty());
    }
}
