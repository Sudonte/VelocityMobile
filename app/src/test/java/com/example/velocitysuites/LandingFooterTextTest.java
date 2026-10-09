package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The landing footer must read exactly "(c)2026 Velocity Suites Surallah. All rights reserved." - with the real
 * copyright sign (U+00A9). It used to show "A-circumflex + (c)": the sign had been saved in one text encoding and
 * read in another (UTF-8 bytes C2 A9 read as Latin-1 and saved again as UTF-8, i.e. C3 82 C2 A9).
 * <p>
 * Three guards: the exact text; the way it is stored (pure ASCII, so there is nothing for an encoding mix-up to
 * damage); and a scan of every resource and source file for that kind of damage anywhere else.
 * <p>
 * This file is deliberately pure ASCII too - every special character is built from its code point - so the guard
 * itself cannot be damaged by the very thing it guards against.
 */
public class LandingFooterTextTest {

    private static final char COPYRIGHT = (char) 0x00A9;
    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
    private static final char REPLACEMENT = (char) 0xFFFD;
    private static final char A_CIRCUMFLEX = (char) 0x00C2;
    private static final char A_TILDE = (char) 0x00C3;
    private static final char A_CIRCUMFLEX_LOWER = (char) 0x00E2;
    private static final char EURO = (char) 0x20AC;
    private static final char PESO = (char) 0x20B1;
    private static final char EM_DASH = (char) 0x2014;

    private static final String EXPECTED = COPYRIGHT + "2026 Velocity Suites Surallah. All rights reserved.";

    private static File srcDir() {
        for (String candidate : new String[]{"src", "app/src"}) {
            File dir = new File(candidate);
            if (dir.isDirectory()) return dir;
        }
        throw new AssertionError("could not find src/ from " + new File(".").getAbsolutePath());
    }

    private static String readUtf8(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return text.length() > 0 && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }

    @Test
    public void footerReadsExactly() throws Exception {
        File strings = new File(srcDir(), "main/res/values/strings.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(readUtf8(strings))));
        NodeList all = doc.getElementsByTagName("string");
        String value = null;
        int definitions = 0;
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if ("landing_footer_copyright".equals(e.getAttribute("name"))) {
                value = e.getTextContent();
                definitions++;
            }
        }
        assertEquals("defined exactly once", 1, definitions);
        assertEquals(EXPECTED, value);
    }

    @Test
    public void footerIsStoredAsPureAscii_soNoEncodingMixUpCanDamageIt() throws Exception {
        String xml = readUtf8(new File(srcDir(), "main/res/values/strings.xml"));
        for (String line : xml.split("\n")) {
            if (line.contains("name=\"landing_footer_copyright\"")) {
                for (int i = 0; i < line.length(); i++) {
                    assertTrue("non-ASCII character " + (int) line.charAt(i) + " in: " + line, line.charAt(i) < 0x80);
                }
                assertTrue("written as a character reference: " + line, line.contains("&#169;"));
                return;
            }
        }
        throw new AssertionError("landing_footer_copyright not found");
    }

    /**
     * Characters that only appear when UTF-8 text was decoded as a single-byte encoding: A-circumflex or A-tilde
     * followed by a Latin-1 symbol, a-circumflex followed by the euro sign, or the replacement character.
     */
    static List<String> mojibakeIn(String fileName, String text) {
        List<String> found = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                char next = i + 1 < line.length() ? line.charAt(i + 1) : 0;
                boolean bad = c == REPLACEMENT
                        || ((c == A_CIRCUMFLEX || c == A_TILDE) && next >= 0x80 && next <= 0xBF)
                        || (c == A_CIRCUMFLEX_LOWER && next == EURO);
                if (bad) {
                    found.add(fileName + ":" + (n + 1) + " code points " + (int) c + "," + (int) next);
                    break;
                }
            }
        }
        return found;
    }

    @Test
    public void theDetectorFindsTheOriginalBug_andIgnoresRealText() {
        String damaged = "" + A_CIRCUMFLEX + COPYRIGHT + " 2026 Velocity Suites Hotel";
        assertFalse(mojibakeIn("x", damaged).isEmpty());
        assertFalse(mojibakeIn("x", "price " + A_CIRCUMFLEX_LOWER + EURO + "99").isEmpty());
        assertFalse(mojibakeIn("x", "bad " + REPLACEMENT).isEmpty());
        assertTrue("a correct copyright sign, a peso sign and an em dash are fine",
                mojibakeIn("x", COPYRIGHT + "2026 " + PESO + "500 " + EM_DASH + " ok").isEmpty());
    }

    @Test
    public void noResourceOrSourceFileContainsTextDamagedByAnEncodingMixUp() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        Path root = srcDir().toPath();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = p.toString().replace('\\', '/');
                if (!(name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt"))) continue;
                problems.addAll(mojibakeIn(name, readUtf8(p.toFile())));
                checked++;
            }
        }
        assertTrue("files scanned: " + checked, checked > 200);
        assertTrue("text damaged by an encoding mix-up: " + problems, problems.isEmpty());
    }
}
