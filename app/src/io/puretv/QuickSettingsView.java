package io.puretv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import java.util.List;
import java.util.function.IntConsumer;

/** A small, static popover below the home header. RAM is one snapshot; its Free up button (OK on the row) frees memory. */
final class QuickSettingsView extends View {
    private final SettingsSurface surface=new SettingsSurface();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel=new RectF(),box=new RectF(),pill=new RectF();
    private static final int ROWS=6,RAM=3,SLEEP=4;
    private final RectF[] rows={new RectF(),new RectF(),new RectF(),new RectF(),new RectF(),new RectF()};
    private final String[] labels={"AirPlay",Ui.tr("Edit home screen"),Ui.tr("Device status"),"RAM",Ui.tr("Sleep timer"),Ui.tr("All settings")};
    private final String[] symbols={"tv","shortcut_edit","tv_settings","memory","bedtime","settings"};
    private final String[] shown=new String[ROWS];
    private final IntConsumer action;
    private final Runnable close;
    private final Spoken spoken;
    private TextPaint text,detail,button;
    private final String freeLabel=Ui.tr("Free up");
    private String airplayValue="",ramValue="—",ramNote="",sleepValue="",shownAirplay="",shownRam="",shownSleep="";
    private boolean on,busy,armed,freeing;
    private float freeWidth;
    private float k,ramUsed=-1;
    private int focus;

    QuickSettingsView(Context context,IntConsumer action,Runnable close){
        super(context);this.action=action;this.close=close;setFocusable(true);
        setContentDescription(Ui.tr("Quick settings"));
        spoken=new Spoken(this,new Spoken.Source(){
            @Override public void items(List<Integer> out){for(int i=1;i<=ROWS;i++)out.add(i);}
            @Override public String text(int id){
                if(id<1||id>ROWS)return "";
                int i=id-1;
                return labels[i]+(i==0?", "+airplayValue:i==RAM?", "+ramValue+", "+(ramNote.isEmpty()?Ui.tr("Free up memory"):ramNote)
                        :i==SLEEP?", "+sleepValue:"");
            }
            @Override public boolean bounds(int id,Rect out){if(id<1||id>ROWS)return false;rows[id-1].round(out);return true;}
            @Override public int focused(){return focus+1;}
            @Override public void focus(int id){if(id>=1&&id<=ROWS){focus=id-1;cacheText();invalidate();}}
            @Override public void click(int id){if(id>=1&&id<=ROWS)action.accept(id-1);}
            @Override public boolean clickable(int id){return true;}
        });
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return spoken;}
    void readAgain(){spoken.readAgain();}
    void airplay(String value,boolean running,boolean changing){
        airplayValue=value;on=running;busy=changing;cacheText();invalidate();spoken.focusMoved();
    }
    void resources(ResourceStatus.Snapshot value){
        ramUsed=value.ramTotal>0&&value.ramAvailable>=0?Ui.clamp(1-value.ramAvailable/(float)value.ramTotal):-1;
        labels[RAM]=ramUsed<0?"RAM":"RAM "+LocaleFormats.percent(ramUsed,Ui.locale());
        ramValue=ResourceStatus.value(getContext(),value.ramAvailable,value.ramTotal);
        cacheText();invalidate();spoken.focusMoved();
    }
    /** The RAM row's second line: available memory, then the progress and the result of freeing it. */
    void ramNote(String note){ramNote=note;cacheText();invalidate();spoken.focusMoved();}
    /** While memory is being freed the Free up button is dimmed. */
    void freeing(boolean value){freeing=value;invalidate();}
    /** The sleep timer's state: "Off", "45 min left" or why it can't run here. */
    void sleep(String value){sleepValue=value;cacheText();invalidate();spoken.focusMoved();}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        k=Math.min(w/1920f,h/1080f);surface.size(getContext(),w,h);
        panel.set(w-600*k,156*k,w-80*k,680*k);
        float y=panel.top+20*k;
        for(int i=0;i<ROWS;i++){
            float height=(i==0?88:i==RAM?100:76)*k;
            rows[i].set(panel.left+16*k,y,panel.right-16*k,y+height);y+=height;
        }
        text=new TextPaint(Ui.text(Ui.semibold,26*k,surface.ink));
        detail=new TextPaint(Ui.text(Ui.regular,20*k,surface.dim));
        button=new TextPaint(Ui.text(Ui.semibold,19*k,surface.ink));freeWidth=button.measureText(freeLabel)+36*k;
        for(String symbol:symbols)Symbols.get(getContext(),symbol);
        cacheText();
    }
    private String fit(String value,TextPaint paint,float width){return TextUtils.ellipsize(value,paint,Math.max(0,width),TextUtils.TruncateAt.END).toString();}
    private void cacheText(){
        if(text==null)return;
        float beside=348*k-freeWidth-12*k;   // the RAM label leaves room for the Free up button on its line
        for(int i=0;i<ROWS;i++)shown[i]=fit(labels[i],text,i==0?310*k:i==RAM?beside:348*k);
        shownAirplay=fit(airplayValue,detail,310*k);
        shownSleep=fit(sleepValue,detail,394*k-text.measureText(shown[SLEEP])-24*k);
        shownRam=fit(ramNote.isEmpty()?ramValue:ramNote,detail,394*k);   // full width under it; the percentage stays in the label
    }
    @Override protected void onDraw(Canvas c){
        if(text==null)return;
        c.drawColor(0x40000000);surface.popup(c,panel,28*k);
        for(int i=0;i<ROWS;i++){
            RectF row=rows[i];boolean focused=i==focus;float f=focused?1:0;
            // The same focus as the settings rows: the bright surface, with everything on it in its dark ink.
            if(i==ROWS-1)surface.hairline(c,row.left+12*k,row.right-12*k,row.top-3*k,focused||focus==i-1?0:1);
            surface.focus(c,row,18*k,f);
            box.set(row.left+12*k,row.centerY()-25*k,row.left+62*k,row.centerY()+25*k);
            surface.well(c,box,14*k,f);
            Symbols.draw(getContext(),c,symbols[i],box.centerX(),box.centerY(),29*k,focused?surface.focusInk:surface.ink);
            float x=row.left+80*k;
            text.setColor(focused?surface.focusInk:surface.ink);detail.setColor(focused?surface.focusDim:surface.dim);
            if(i==0){
                c.drawText(shown[i],x,row.centerY()-7*k,text);
                c.drawText(shownAirplay,x,row.centerY()+20*k,detail);
                surface.toggle(c,row.right-14*k,row.centerY(),on,!busy,f);
            }else if(i==SLEEP){
                c.drawText(shown[i],x,row.centerY()+Ui.halfCap(text),text);
                c.drawText(shownSleep,row.right-22*k-detail.measureText(shownSleep),row.centerY()+Ui.halfCap(detail),detail);
            }else if(i==RAM){
                c.drawText(shown[i],x,row.top+28*k,text);
                c.drawText(shownRam,x,row.top+54*k,detail);
                // Free up: always shown, so the row says what OK does; lit like a focused control when the row is.
                boolean lit=focused&&!freeing;
                pill.set(row.right-14*k-freeWidth,row.top+6*k,row.right-14*k,row.top+37*k);   // on the label's line
                if(lit){fill.setColor(Look.GOLD);c.drawRoundRect(pill,15.5f*k,15.5f*k,fill);}
                else surface.well(c,pill,15.5f*k,f);
                button.setColor(lit?Look.ON_GOLD:freeing?focused?surface.focusDim:surface.dim:surface.ink);
                c.drawText(freeLabel,pill.left+18*k,pill.centerY()+Ui.halfCap(button),button);   // freeWidth = text + 2 × 18
                surface.meter(c,x,row.right-22*k,row.top+73*k,ramUsed,f);
            }else{
                c.drawText(shown[i],x,row.centerY()+Ui.halfCap(text),text);
                Symbols.draw(getContext(),c,"chevron_right",row.right-20*k,row.centerY(),20*k,focused?surface.focusDim:surface.dim);
            }
        }
    }
    boolean onKey(KeyEvent e){
        boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(down&&e.getKeyCode()!=KeyEvent.KEYCODE_DPAD_CENTER&&e.getKeyCode()!=KeyEvent.KEYCODE_ENTER
                &&e.getKeyCode()!=KeyEvent.KEYCODE_NUMPAD_ENTER)armed=false;
        switch(e.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:
                if(down){focus=Math.max(0,Math.min(ROWS-1,focus+(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?-1:1)));cacheText();invalidate();spoken.focusMoved();}return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if(down){if(e.getRepeatCount()==0)armed=true;}
                else{if(armed&&!e.isCanceled()&&spoken.keyClick())action.accept(focus);armed=false;}return true;
            case KeyEvent.KEYCODE_BACK:if(!down)close.run();return true;
            default:return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_UP){
            if(!panel.contains(e.getX(),e.getY()))close.run();
            else for(int i=0;i<ROWS;i++)if(rows[i].contains(e.getX(),e.getY())){focus=i;cacheText();invalidate();action.accept(i);break;}
        }
        return true;
    }
}
