package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OtpBoxesTest {

    private LinearLayout container;
    private OtpBoxes boxes;
    private String lastCode;
    private boolean lastComplete;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        container = new LinearLayout(context);
        boxes = new OtpBoxes(container, 6, (code, complete) -> {
            lastCode = code;
            lastComplete = complete;
        });
    }

    private EditText box(int i) {
        return (EditText) container.getChildAt(i);
    }

    @Test
    public void typingFillsBoxesInOrderAndReportsWhenComplete() {
        String code = "483920";
        for (int i = 0; i < 6; i++) {
            box(i).setText(String.valueOf(code.charAt(i)));
            assertEquals(code.substring(0, i + 1), boxes.getCode());
            assertEquals(i == 5, lastComplete);
        }
        assertEquals(code, lastCode);
    }

    @Test
    public void pastingAWholeCodeIntoTheFirstBoxSpreadsIt() {
        box(0).setText("123456");
        assertEquals("123456", boxes.getCode());
        assertTrue(lastComplete);
        for (int i = 0; i < 6; i++) assertEquals(String.valueOf(i + 1), box(i).getText().toString());
    }

    @Test
    public void pasteWithSpacesOrDashesKeepsOnlyTheDigits() {
        box(0).setText("123 456");
        assertEquals("123456", boxes.getCode());
    }

    @Test
    public void typingOverAFilledBoxKeepsTheNewestDigit() {
        box(0).setText("1");
        box(0).setText("17"); // what an EditText holds when a digit is typed after the old one
        assertEquals("7", box(0).getText().toString());
    }

    @Test
    public void backspaceOnAnEmptyBoxClearsAndFocusesThePreviousOne() {
        box(0).setText("1");
        box(1).setText("2");
        box(2).requestFocus();
        assertEquals("12", boxes.getCode());

        box(2).dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        assertEquals("1", boxes.getCode());
        assertFalse(lastComplete);
    }

    @Test
    public void nonDigitsAreIgnored() {
        box(0).setText("a");
        assertEquals("", boxes.getCode());
    }

    @Test
    public void clearEmptiesEveryBox() {
        box(0).setText("123456");
        boxes.clear();
        assertEquals("", boxes.getCode());
        assertTrue(boxes.isEmpty());
        assertSame(box(0), boxes.firstBox());
    }

    @Test
    public void emailIsMaskedKeepingTheFirstLetterAndTheDomain() {
        assertEquals("j***@gmail.com", OtpBoxes.maskEmail("juan.delacruz@gmail.com"));
        assertEquals("a***@example.com", OtpBoxes.maskEmail("  ana@example.com "));
        assertEquals("", OtpBoxes.maskEmail(null));
        assertEquals("nonsense", OtpBoxes.maskEmail("nonsense"));
    }
}
