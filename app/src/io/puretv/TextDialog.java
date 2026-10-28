package io.puretv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import java.util.function.Consumer;

/**
 * One line of text in the settings popup style (the city search): the TV's keyboard types into a field drawn like the
 * PIN keypad's panel, and the keyboard's action key confirms. The panel sits in the upper part of the screen so the
 * keyboard doesn't cover it. Static canvas; the only view is the EditText the keyboard needs.
 */
final class TextDialog extends Dialog {
    private final String title, hint;
    private final int action, maxLength;
    private final Consumer<String> done;
    private EditText field;
    private boolean used;

    TextDialog(Activity activity, String title, String hint, int action, int maxLength, Consumer<String> done) {
        super(activity, android.R.style.Theme_Material_NoActionBar);
        Ui.init(activity);
        this.title = title; this.hint = hint; this.action = action; this.maxLength = maxLength; this.done = done;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setDimAmount(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        field = new EditText(getContext());
        field.setSingleLine(true);
        field.setBackground(null);
        field.setPadding(0, 0, 0, 0);
        field.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        field.setTypeface(Ui.medium);
        field.setHint(hint);
        field.setImeOptions(action);
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
        field.setOnEditorActionListener((v, id, event) -> { confirm(); return true; });
        setContentView(new Box());
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        field.requestFocus();
    }
    private void confirm() {
        if (used || !isShowing()) return;
        used = true;
        String text = field.getText().toString();
        dismiss();
        done.accept(text);
    }

    private final class Box extends ViewGroup {
        private final SettingsSurface surface = new SettingsSurface();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF panel = new RectF(), box = new RectF();
        private TextPaint heading;
        private String shownTitle = "";
        private float k;

        Box() {
            super(TextDialog.this.getContext());
            setWillNotDraw(false);
            addView(field);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            k = Math.min(width / 1920f, height / 1080f);
            surface.size(getContext(), width, height);
            float panelWidth = 760 * k;
            panel.set((width - panelWidth) / 2, 150 * k, (width + panelWidth) / 2, 150 * k + 196 * k);
            box.set(panel.left + 40 * k, panel.top + 92 * k, panel.right - 40 * k, panel.top + 156 * k);
            heading = new TextPaint(Ui.text(Ui.semibold, 28 * k, surface.ink));
            shownTitle = TextUtils.ellipsize(title, heading, panel.width() - 80 * k, TextUtils.TruncateAt.END).toString();
            rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(2 * k);
            field.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * k);
            field.setTextColor(surface.ink);
            field.setHintTextColor(surface.dim);
            field.measure(MeasureSpec.makeMeasureSpec((int) (box.width() - 40 * k), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) box.height(), MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int left = (int) (box.left + 20 * k), top = (int) box.top;
            field.layout(left, top, left + field.getMeasuredWidth(), top + field.getMeasuredHeight());
        }
        @Override protected void onDraw(Canvas c) {
            if (heading == null) return;
            c.drawColor(0x66000000);
            surface.popup(c, panel, 28 * k);
            c.drawText(shownTitle, panel.left + 40 * k, panel.top + 60 * k, heading);
            fill.setColor(surface.dark ? 0x14FFFFFF : 0x10000000);
            c.drawRoundRect(box, 16 * k, 16 * k, fill);
            rim.setColor(surface.dark ? 0xCCF5F5F7 : surface.focusFill);   // the field always has the focus
            c.drawRoundRect(box, 16 * k, 16 * k, rim);
        }
    }
}
