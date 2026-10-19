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

/** No screen, dialog, style or theme turns text into ALL CAPS or adds letter-spacing. Scans the resources and the Java sources. */
public class NoAllCapsOrLetterSpacingTest {

    private static final Pattern CAPS = Pattern.compile("(android:textAllCaps=\"true\"|name=\"android:textAllCaps\">\\s*true<|\\.setAllCaps\\(true\\))");
    private static final Pattern SPACING = Pattern.compile(
            "(android:letterSpacing=\"|name=\"android:letterSpacing\">|\\.setLetterSpacing\\()\\s*(-?[0-9]*\\.?[0-9]+)");

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
        return bad;
    }

    @Test
    public void noLayoutStyleOrCodeUsesAllCapsOrLetterSpacing() throws Exception {
        List<String> bad = offenders();
        assertTrue("found: " + bad, bad.isEmpty());
    }
}
