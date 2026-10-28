package io.puretv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeProvider;
import java.util.List;

/**
 * The app lock's PIN entry in the settings popup style: a remote-friendly 3×4 keypad (digits, delete, confirm) that
 * also takes the remote's number keys. Static canvas like QuickActionDialog; the digits are never drawn, only dots.
 * Confirm works from MIN digits (dimmed before).
 */
final class PinDialog extends Dialog {
    interface Entered { void pin(String value); }
    static final int MIN = 4, MAX = 8;
    private static final int DELETE = 9, ZERO = 10, CONFIRM = 11;
    private final String title;
    private final Entered entered;
    private final StringBuilder digits = new StringBuilder(MAX);
    private Pad view;
    private boolean done;

    PinDialog(Activity activity, String title, Entered entered) {
        super(activity, android.R.style.Theme_Material_NoActionBar);
        Ui.init(activity);
        this.title = title;
        this.entered = entered;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setDimAmount(0);
        view = new Pad();
        setContentView(view);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    @Override public void show() { super.show(); view.spoken.readAgain(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return view.onKey(event) || super.dispatchKeyEvent(event); }

    private void press(int key) {
        if (done || !isShowing()) return;
        if (key == CONFIRM) {
            // Every PIN has 4 to 8 digits: a shorter entry is never sent, so it can't count as a wrong try.
            if (digits.length() < MIN) return;
            done = true;
            dismiss();
            entered.pin(digits.toString());
            return;
        }
        if (key == DELETE) { if (digits.length() > 0) digits.setLength(digits.length() - 1); }
        else if (digits.length() < MAX) {
            digits.append(key == ZERO ? '0' : (char) ('1' + key));
            if (digits.length() == MAX) view.focus = CONFIRM;
        }
        view.invalidate();
    }
    private static String label(int key) {
        return key == DELETE ? Ui.tr("Delete digit") : key == CONFIRM ? Ui.tr("OK") : key == ZERO ? "0" : String.valueOf(key + 1);
    }

    private final class Pad extends View {
        private final SettingsSurface surface = new SettingsSurface();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
                line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF panel = new RectF(), key = new RectF();
        private final Path icon = new Path();
        private final Spoken spoken;
        private TextPaint heading, number;
        private String shownTitle = "";
        private float k, keyWidth, keyHeight, gap, padTop;
        int focus;
        private boolean armed;

        Pad() {
            super(PinDialog.this.getContext());
            setFocusable(true);
            setContentDescription(title);
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) { for (int i = 1; i <= 12; i++) out.add(i); }
                @Override public String text(int id) { return id > 0 && id <= 12 ? label(id - 1) : ""; }
                @Override public boolean bounds(int id, Rect out) {
                    if (id < 1 || id > 12) return false;
                    keyBounds(id - 1).round(out); return true;
                }
                @Override public int focused() { return focus + 1; }
                @Override public void focus(int id) { if (id > 0 && id <= 12) { focus = id - 1; invalidate(); } }
                @Override public void click(int id) { if (id > 0 && id <= 12) press(id - 1); }
            });
        }
        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return spoken; }
        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            k = Math.min(width / 1920f, height / 1080f);
            surface.size(getContext(), width, height);
            keyWidth = 132 * k; keyHeight = 80 * k; gap = 14 * k; padTop = 168 * k;
            float panelWidth = 3 * keyWidth + 2 * gap + 96 * k, panelHeight = padTop + 4 * keyHeight + 3 * gap + 44 * k;
            panel.set((width - panelWidth) / 2, (height - panelHeight) / 2, (width + panelWidth) / 2, (height + panelHeight) / 2);
            heading = new TextPaint(Ui.text(Ui.semibold, 28 * k, surface.ink));
            number = new TextPaint(Ui.text(Ui.medium, 34 * k, surface.ink));
            rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(2 * k);
            line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(3.5f * k);
            line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
            shownTitle = TextUtils.ellipsize(title, heading, panel.width() - 64 * k, TextUtils.TruncateAt.END).toString();
        }
        private RectF keyBounds(int index) {
            float left = panel.left + 48 * k + (index % 3) * (keyWidth + gap), top = panel.top + padTop + (index / 3) * (keyHeight + gap);
            key.set(left, top, left + keyWidth, top + keyHeight);
            return key;
        }
        @Override protected void onDraw(Canvas c) {
            if (heading == null) return;
            c.drawColor(0x66000000);
            surface.popup(c, panel, 28 * k);
            c.drawText(shownTitle, panel.centerX() - heading.measureText(shownTitle) / 2, panel.top + 64 * k, heading);
            int slots = Math.max(4, digits.length());
            float step = 34 * k, x = panel.centerX() - (slots - 1) * step / 2, y = panel.top + 116 * k;
            for (int i = 0; i < slots; i++) {
                if (i < digits.length()) { fill.setColor(surface.ink); c.drawCircle(x + i * step, y, 9 * k, fill); }
                else { rim.setColor(surface.dim); c.drawCircle(x + i * step, y, 8 * k, rim); }
            }
            for (int i = 0; i < 12; i++) {
                keyBounds(i);
                boolean focused = i == focus;
                // Focus filled like the settings rows: the one key the remote is on reads from across the room.
                fill.setColor(focused ? surface.focusFill : surface.dark ? 0x14FFFFFF : 0x10000000);
                c.drawRoundRect(key, 16 * k, 16 * k, fill);
                int ink = focused ? surface.focusInk : surface.ink;
                if (i == DELETE) drawDelete(c, ink);
                else if (i == CONFIRM) drawConfirm(c, digits.length() < MIN ? (focused ? surface.focusDim : surface.dim) : ink);
                else {
                    String s = label(i);
                    number.setColor(ink);
                    c.drawText(s, key.centerX() - number.measureText(s) / 2, key.centerY() + Ui.halfCap(number), number);
                }
            }
        }
        /** A backspace tag with a cross, drawn in the key's text colour. */
        private void drawDelete(Canvas c, int ink) {
            float cx = key.centerX(), cy = key.centerY(), w = 21 * k, h = 13 * k;
            icon.rewind();
            icon.moveTo(cx - w, cy); icon.lineTo(cx - w + 10 * k, cy - h); icon.lineTo(cx + w, cy - h);
            icon.lineTo(cx + w, cy + h); icon.lineTo(cx - w + 10 * k, cy + h); icon.close();
            float m = cx + 4 * k, d = 5.5f * k;
            icon.moveTo(m - d, cy - d); icon.lineTo(m + d, cy + d); icon.moveTo(m + d, cy - d); icon.lineTo(m - d, cy + d);
            line.setColor(ink); c.drawPath(icon, line);
        }
        private void drawConfirm(Canvas c, int ink) {
            float cx = key.centerX(), cy = key.centerY();
            icon.rewind();
            icon.moveTo(cx - 15 * k, cy + 1 * k); icon.lineTo(cx - 5 * k, cy + 11 * k); icon.lineTo(cx + 16 * k, cy - 11 * k);
            line.setColor(ink); c.drawPath(icon, line);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!panel.contains(event.getX(), event.getY())) cancel();
                else for (int i = 0; i < 12; i++) if (keyBounds(i).contains(event.getX(), event.getY())) { focus = i; press(i); break; }
            }
            return true;
        }
        boolean onKey(KeyEvent event) {
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            int code = event.getKeyCode();
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 || code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
                int digit = code <= KeyEvent.KEYCODE_9 ? code - KeyEvent.KEYCODE_0 : code - KeyEvent.KEYCODE_NUMPAD_0;
                if (down && event.getRepeatCount() == 0) press(digit == 0 ? ZERO : digit - 1);   // a held key types once
                return true;
            }
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) {
                        int row = focus / 3, column = focus % 3;
                        if (code == KeyEvent.KEYCODE_DPAD_UP) row = Math.max(0, row - 1);
                        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) row = Math.min(3, row + 1);
                        else if (code == KeyEvent.KEYCODE_DPAD_LEFT) column = Math.max(0, column - 1);
                        else column = Math.min(2, column + 1);
                        if (row * 3 + column != focus) { focus = row * 3 + column; invalidate(); spoken.focusMoved(); }
                    }
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    if (down) press(DELETE);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) { if (event.getRepeatCount() == 0) armed = true; }
                    else { if (armed && !event.isCanceled() && spoken.keyClick()) press(focus); armed = false; }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (!down) cancel();
                    return true;
                default: return false;
            }
        }
    }
}
