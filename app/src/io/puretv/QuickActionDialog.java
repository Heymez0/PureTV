package io.puretv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
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

/** Static modal canvas: no animation loop, blur surface, receiver or background worker. */
final class QuickActionDialog extends Dialog {
    static final class Option {
        final String label;
        final Runnable action;
        Option(String label, Runnable action) { this.label = label; this.action = action; }
    }
    private final String title;
    private final List<Option> options;
    private final RectF anchor;
    private Menu view;
    private boolean choosing;

    QuickActionDialog(Activity activity, String title, List<Option> options, RectF anchor) {
        super(activity, android.R.style.Theme_Material_NoActionBar);
        this.title = title;
        this.options = options;
        this.anchor = new RectF(anchor);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setDimAmount(0);
        view = new Menu();
        setContentView(view);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    @Override public void show() { super.show(); view.spoken.readAgain(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return view.onKey(event) || super.dispatchKeyEvent(event); }
    private void choose(int index) {
        if (choosing || !isShowing()) return;
        choosing = true;
        dismiss();
        options.get(index).action.run();
    }
    private final class Menu extends View {
        private final SettingsSurface surface = new SettingsSurface();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF panel = new RectF(), row = new RectF();
        private final Spoken spoken;
        private TextPaint heading, text;
        private String shownTitle = "";
        private String[] shownLabels;
        private float k;
        private int focus;
        private boolean armed;

        Menu() {
            super(QuickActionDialog.this.getContext());
            setFocusable(true);
            setContentDescription(title);
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) { for (int i = 1; i <= options.size(); i++) out.add(i); }
                @Override public String text(int id) { return id > 0 && id <= options.size() ? options.get(id - 1).label : ""; }
                @Override public boolean bounds(int id, Rect out) {
                    if (id < 1 || id > options.size()) return false;
                    rowBounds(id - 1).round(out); return true;
                }
                @Override public int focused() { return focus + 1; }
                @Override public void focus(int id) { if (id > 0 && id <= options.size()) { focus = id - 1; invalidate(); } }
                @Override public void click(int id) { if (id > 0 && id <= options.size()) choose(id - 1); }
            });
        }
        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return spoken; }
        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            k = Math.min(width / 1920f, height / 1080f);
            surface.size(getContext(),width,height);
            float panelHeight = (72 + options.size() * 64) * k, panelWidth=480*k, edge=32*k, gap=18*k;
            int[] location=new int[2];getLocationOnScreen(location);
            float cx=anchor.centerX()-location[0], above=anchor.top-location[1], below=anchor.bottom-location[1];
            float x=Math.max(edge,Math.min(width-edge-panelWidth,cx-panelWidth/2));
            float y=below+gap;
            if(y+panelHeight>height-edge)y=above-gap-panelHeight;
            y=Math.max(edge,Math.min(height-edge-panelHeight,y));
            panel.set(x,y,x+panelWidth,y+panelHeight);
            heading = new TextPaint(Ui.text(Ui.semibold, 25 * k, surface.dim));
            text = new TextPaint(Ui.text(Ui.medium, 26 * k, surface.ink));
            rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(2*k);
            shownTitle = TextUtils.ellipsize(title, heading, panel.width() - 48 * k, TextUtils.TruncateAt.END).toString();
            shownLabels = new String[options.size()];
            for (int i = 0; i < options.size(); i++) shownLabels[i] = TextUtils.ellipsize(
                    options.get(i).label, text, panel.width() - 64 * k, TextUtils.TruncateAt.END).toString();
        }
        private RectF rowBounds(int index) {
            float top = panel.top + (60 + index * 64) * k;
            row.set(panel.left + 12 * k, top, panel.right - 12 * k, top + 60 * k);
            return row;
        }
        @Override protected void onDraw(Canvas c) {
            if (heading == null) return;
            c.drawColor(0x28000000);
            surface.popup(c,panel,24*k);
            c.drawText(shownTitle, panel.left + 24 * k, panel.top + 37 * k, heading);
            fill.setColor(surface.dark?0x26FFFFFF:0x20000000);
            c.drawRect(panel.left+24*k,panel.top+50*k,panel.right-24*k,panel.top+51*k,fill);
            for (int i = 0; i < options.size(); i++) {
                rowBounds(i);
                if (i == focus) {
                    fill.setColor(surface.dark?0xFF303034:surface.focusFill);c.drawRoundRect(row,14*k,14*k,fill);
                    rim.setColor(surface.dark?0xCCF5F5F7:surface.focusFill);c.drawRoundRect(row,14*k,14*k,rim);
                }
                text.setColor(i==focus&&!surface.dark?surface.focusInk:surface.ink);
                c.drawText(shownLabels[i], row.left + 20 * k, row.centerY() + Ui.halfCap(text), text);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_UP){
                if(!panel.contains(event.getX(),event.getY()))cancel();
                else for(int i=0;i<options.size();i++)if(rowBounds(i).contains(event.getX(),event.getY())){choose(i);break;}
            }
            return true;
        }
        boolean onKey(KeyEvent event) {
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) {
                        focus = Math.max(0, Math.min(options.size() - 1, focus
                                + (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
                        invalidate(); spoken.focusMoved();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) { if (event.getRepeatCount() == 0) armed = true; }
                    else { if (armed && !event.isCanceled() && spoken.keyClick()) choose(focus); armed = false; }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (!down) cancel();
                    return true;
                default: return false;
            }
        }
    }
}
