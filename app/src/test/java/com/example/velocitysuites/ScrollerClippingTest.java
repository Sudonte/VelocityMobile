package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Guards against the bug that made the Payment screen's scrolling content paint over the guest header:
 * a scroller with {@code clipToPadding="false"} sitting directly in a parent with {@code clipChildren="false"}
 * is clipped by nothing, so whatever it scrolls draws outside its own bounds - over whatever is above it.
 * Plain JVM test over the real layout XML, so it covers every screen, not just the ones with a screen test.
 */
public class ScrollerClippingTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Set<String> SCROLLERS = new HashSet<>(Arrays.asList(
            "ScrollView", "NestedScrollView", "HorizontalScrollView", "RecyclerView", "ListView", "GridView"));

    /** Layout elements that break the rule: "file: <Scroller> (id) has clipToPadding=false inside a clipChildren=false <Parent>". */
    static List<String> overflowingScrollers(String fileName, Document document) {
        List<String> problems = new ArrayList<>();
        collect(fileName, document.getDocumentElement(), problems);
        return problems;
    }

    private static void collect(String fileName, Element element, List<String> problems) {
        String tag = element.getTagName();
        String simple = tag.substring(tag.lastIndexOf('.') + 1);
        Node parentNode = element.getParentNode();
        if (SCROLLERS.contains(simple)
                && "false".equals(element.getAttributeNS(ANDROID_NS, "clipToPadding"))
                && parentNode instanceof Element
                && "false".equals(((Element) parentNode).getAttributeNS(ANDROID_NS, "clipChildren"))) {
            problems.add(fileName + ": <" + simple + "> " + element.getAttributeNS(ANDROID_NS, "id")
                    + " has clipToPadding=false inside a clipChildren=false <" + ((Element) parentNode).getTagName() + ">");
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) collect(fileName, (Element) children.item(i), problems);
        }
    }

    private static Document parse(String xml) throws Exception {
        // a few resource files carry a UTF-8 byte-order mark, which the XML parser rejects before the root element
        if (xml.startsWith("﻿")) xml = xml.substring(1);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static File layoutDir() {
        for (String candidate : new String[]{"src/main/res", "app/src/main/res"}) {
            File res = new File(candidate);
            if (res.isDirectory()) return res;
        }
        throw new AssertionError("could not find src/main/res from " + new File(".").getAbsolutePath());
    }

    @Test
    public void detectsTheOriginalPaymentLayoutPattern() throws Exception {
        String broken = "<CoordinatorLayout xmlns:android=\"" + ANDROID_NS + "\" android:clipChildren=\"false\">"
                + "<AppBarLayout/>"
                + "<androidx.core.widget.NestedScrollView android:id=\"@+id/screenContent\" android:clipToPadding=\"false\"/>"
                + "</CoordinatorLayout>";
        List<String> problems = overflowingScrollers("payment.xml", parse(broken));
        assertEquals(problems.toString(), 1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("screenContent"));
    }

    @Test
    public void acceptsEitherSideOfThePatternOnItsOwn() throws Exception {
        String clippedByParent = "<FrameLayout xmlns:android=\"" + ANDROID_NS + "\">"
                + "<NestedScrollView android:clipToPadding=\"false\"/></FrameLayout>";
        String clippedByItself = "<FrameLayout xmlns:android=\"" + ANDROID_NS + "\" android:clipChildren=\"false\">"
                + "<NestedScrollView/></FrameLayout>";
        assertTrue(overflowingScrollers("a.xml", parse(clippedByParent)).isEmpty());
        assertTrue(overflowingScrollers("b.xml", parse(clippedByItself)).isEmpty());
    }

    /**
     * Every screen with the guest header (an AppBarLayout, or the shared guest_header include) - the screens where
     * overflowing content would land on the header. login/registration are deliberately not in this set: their
     * scroller fills the whole screen with nothing above it, so there is nothing for it to paint over.
     */
    @Test
    public void noGuestHeaderScreenLetsAScrollerOverflowItsBounds() throws Exception {
        File[] layoutDirs = layoutDir().listFiles((dir, name) -> name.equals("layout") || name.startsWith("layout-"));
        assertTrue("layout directories found", layoutDirs != null && layoutDirs.length > 0);
        List<String> problems = new ArrayList<>();
        int headerScreens = 0;
        for (File dir : layoutDirs) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
            if (files == null) continue;
            for (File file : files) {
                String xml = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (!xml.contains("AppBarLayout") && !xml.contains("@layout/guest_header")) continue;
                headerScreens++;
                problems.addAll(overflowingScrollers(dir.getName() + "/" + file.getName(), parse(xml)));
            }
        }
        assertTrue("the guest-header screens were actually found: " + headerScreens, headerScreens >= 8);
        assertTrue(problems.toString(), problems.isEmpty());
    }

    /** The screens reworked in this change must never reintroduce the pattern, header or not. */
    @Test
    public void reworkedScreensNeverLetAScrollerOverflowItsBounds() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String name : new String[]{"landing.xml", "payment.xml"}) {
            File file = new File(layoutDir(), "layout/" + name);
            assertTrue(file + " exists", file.isFile());
            problems.addAll(overflowingScrollers(name, parse(new String(
                    java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8))));
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }
}
