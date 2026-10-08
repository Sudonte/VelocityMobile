package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TermsGateTest {

    @Test
    public void checkboxUnlocksOnlyAfterTermsAreOpened() {
        assertFalse(TermsGate.isCheckboxEnabled(false));
        assertTrue(TermsGate.isCheckboxEnabled(true));
    }

    @Test
    public void endOfDocumentIsReachedByScrollingOrWhenItFitsOnScreen() {
        assertFalse("not laid out yet", TermsGate.hasReachedEnd(0, 0, 0));
        assertFalse("long document, at the top", TermsGate.hasReachedEnd(0, 800, 5000));
        assertFalse("long document, scrolled partway", TermsGate.hasReachedEnd(2000, 800, 5000));
        assertFalse("just short of the bottom", TermsGate.hasReachedEnd(4100, 800, 5000));
        assertTrue("at the bottom", TermsGate.hasReachedEnd(4200, 800, 5000));
        assertTrue("within rounding of the bottom", TermsGate.hasReachedEnd(4195, 800, 5000));
        assertTrue("short document fits on screen", TermsGate.hasReachedEnd(0, 800, 600));
        assertTrue("exactly fits", TermsGate.hasReachedEnd(0, 800, 800));
    }

    @Test
    public void mainActionNeedsBothOpenedAndChecked() {
        assertFalse(TermsGate.isActionEnabled(false, false));
        assertFalse("checked without opening is never enough", TermsGate.isActionEnabled(false, true));
        assertFalse(TermsGate.isActionEnabled(true, false));
        assertTrue(TermsGate.isActionEnabled(true, true));
    }

    @Test
    public void helperLineSaysWhatIsStillMissing() {
        assertEquals(R.string.terms_missing_open, TermsGate.missingHintRes(false, false));
        assertEquals(R.string.terms_missing_open, TermsGate.missingHintRes(false, true));
        assertEquals(R.string.terms_missing_check, TermsGate.missingHintRes(true, false));
        assertEquals(0, TermsGate.missingHintRes(true, true));
    }

    @Test
    public void numberedSectionTitlesAreHeadingsButNumberedSentencesAreNot() {
        assertTrue(TermsContent.isSubheading("1. Check-In Window"));
        assertTrue(TermsContent.isSubheading("12. Non-Refundable Policy"));
        assertFalse(TermsContent.isSubheading("1. You agree that all personal information provided is accurate."));
        assertFalse(TermsContent.isSubheading("Check-In Window"));
        assertFalse(TermsContent.isSubheading(""));
    }
}
