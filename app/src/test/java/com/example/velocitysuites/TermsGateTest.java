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
