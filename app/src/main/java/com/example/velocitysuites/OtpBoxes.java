package com.example.velocitysuites;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A row of single-digit boxes for a one-time code: typing a digit moves to the next box, Backspace
 * on an empty box clears and moves back, and pasting a whole code fills the boxes from the one
 * pasted into. {@link #getCode()} is the digits entered so far.
 */
final class OtpBoxes {

    interface Listener {
        /** Called whenever the entered digits change (including when cleared). */
        void onCodeChanged(@NonNull String code, boolean complete);
    }

    private final EditText[] boxes;
    private final Listener listener;
    private boolean updating;

    OtpBoxes(@NonNull LinearLayout container, int length, @Nullable Listener listener) {
        this.listener = listener;
        this.boxes = new EditText[length];
        Context context = container.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        container.removeAllViews();
        for (int i = 0; i < length; i++) {
            EditText box = new BackspaceAwareEditText(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (52 * density), 1f);
            if (i > 0) lp.setMarginStart((int) (6 * density));
            box.setLayoutParams(lp);
            box.setGravity(Gravity.CENTER);
            box.setInputType(InputType.TYPE_CLASS_NUMBER);
            box.setImeOptions(i == length - 1 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
            box.setTextSize(20);
            box.setTypeface(box.getTypeface(), android.graphics.Typeface.BOLD);
            box.setTextColor(context.getColor(R.color.velocity_text_primary));
            box.setBackgroundResource(R.drawable.bg_flat_box);
            box.setPadding(0, 0, 0, 0);
            box.setSelectAllOnFocus(true);
            box.setCursorVisible(false);
            box.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
            box.setContentDescription(context.getString(R.string.otp_box_desc, i + 1, length));
            final int index = i;
            box.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(Editable s) {
                    if (updating) return;
                    onBoxChanged(index, s.toString());
                }
            });
            box.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && box.getText().length() == 0 && index > 0) {
                    EditText previous = boxes[index - 1];
                    updating = true;
                    previous.setText("");
                    updating = false;
                    previous.requestFocus();
                    notifyChanged();
                    return true;
                }
                return false;
            });
            boxes[i] = box;
            container.addView(box);
        }
    }

    /**
     * Many soft keyboards never send KEYCODE_DEL for an EMPTY field - they call deleteSurroundingText()
     * on the input connection instead - so the key listener alone misses Backspace-to-previous-box.
     * This turns that call (and a real DEL key) on an empty box into the same DEL key event.
     */
    private static final class BackspaceAwareEditText extends androidx.appcompat.widget.AppCompatEditText {
        BackspaceAwareEditText(Context context) {
            super(context);
        }

        @Override
        public android.view.inputmethod.InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            android.view.inputmethod.InputConnection base = super.onCreateInputConnection(outAttrs);
            if (base == null) return null;
            return new android.view.inputmethod.InputConnectionWrapper(base, true) {
                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    if (getText().length() == 0 && beforeLength > 0) {
                        return dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                && dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
                    }
                    return super.deleteSurroundingText(beforeLength, afterLength);
                }
            };
        }
    }

    private void onBoxChanged(int index, String raw) {
        String digits = raw.replaceAll("\\D", "");
        updating = true;
        try {
            if (digits.isEmpty()) {
                boxes[index].setText("");
            } else if (digits.length() == 1) {
                boxes[index].setText(digits);
                focusAfter(index);
            } else if (digits.length() == 2 && raw.length() == 2) {
                // Typed over a filled box: the newest digit wins.
                boxes[index].setText(digits.substring(1));
                focusAfter(index);
            } else {
                // A pasted code: spread it from this box onward.
                int pos = index;
                for (int i = 0; i < digits.length() && pos < boxes.length; i++, pos++) {
                    boxes[pos].setText(String.valueOf(digits.charAt(i)));
                }
                focusAfter(Math.min(pos, boxes.length) - 1);
            }
        } finally {
            updating = false;
        }
        notifyChanged();
    }

    private void focusAfter(int index) {
        if (index + 1 < boxes.length) {
            boxes[index + 1].requestFocus();
        } else {
            boxes[index].setSelection(boxes[index].getText().length());
        }
    }

    private void notifyChanged() {
        if (listener != null) listener.onCodeChanged(getCode(), getCode().length() == boxes.length);
    }

    @NonNull
    String getCode() {
        StringBuilder code = new StringBuilder();
        for (EditText box : boxes) code.append(box.getText());
        return code.toString();
    }

    void clear() {
        updating = true;
        for (EditText box : boxes) box.setText("");
        updating = false;
        boxes[0].requestFocus();
        notifyChanged();
    }

    void setError(boolean error) {
        int background = error ? R.drawable.bg_flat_box_selected : R.drawable.bg_flat_box;
        for (EditText box : boxes) box.setBackgroundResource(background);
    }

    void setEnabled(boolean enabled) {
        for (EditText box : boxes) box.setEnabled(enabled);
    }

    void focusFirst() {
        boxes[0].requestFocus();
    }

    EditText firstBox() {
        return boxes[0];
    }

    boolean isEmpty() {
        return getCode().isEmpty();
    }

    /** "j***@gmail.com": the first letter of the local part, then asterisks, then the full domain. */
    @NonNull
    static String maskEmail(@Nullable String email) {
        if (email == null) return "";
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) return trimmed;
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }
}
