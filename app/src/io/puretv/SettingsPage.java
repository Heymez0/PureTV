package io.puretv;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import java.util.List;
import java.util.function.IntConsumer;

/** Static settings surfaces; focus animation stops completely when settled. */
final class SettingsPage extends View implements Choreographer.FrameCallback {
    interface Action { void run(int direction); }
    static final class Row {
        final String label; String value="",shownValue="",shownLabel="",hint="";
        final Action action;
        private String fittedValue;private float fittedWidth=Float.NaN;
        boolean network; int state=NetworkStatus.UNKNOWN; String transport=""; int color=0xFF30D158;
        Row(String label,Action action,boolean choice){this.label=label;this.action=action;}
    }
    static final class Data {
        final String title,subtitle,symbol; final Row[] rows; String hint="";
        boolean compact; String[] categories; int category; IntConsumer selectCategory;
        Data(String title,String subtitle,String symbol,Row... rows){this.title=title;this.subtitle=subtitle;this.symbol=symbol;this.rows=rows;}
    }
    private static final int VISIBLE=7,NAV=1000;
    private final SettingsSurface surface=new SettingsSurface();
    private final Data data; private final Runnable update;
    private final Spring[] focusMotion; private final Spoken spoken;
    private final RectF row=new RectF(),arc=new RectF(),panel=new RectF(),navBox=new RectF();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),icon=new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint title,label,value,hintPaint,navText; private Paint subtitle;
    private StaticLayout hint; private String hintText="",position="",footer="";
    private float k=1; private int focus,scroll,width,height; private long lastFrame;
    private boolean running,armed,scheduled,navFocus; private NetworkStatus.Watch network;
    SettingsPage(Context context,Data data,Runnable update){
        super(context);this.data=data;this.update=update;
        for(int i=0;i<data.rows.length;i++)if(data.rows[i].action!=null){focus=i;break;}
        navFocus=data.categories!=null;focusMotion=new Spring[data.rows.length];
        for(int i=0;i<focusMotion.length;i++)focusMotion[i]=new Spring(.28f,.85f,!navFocus&&i==focus?1:0);
        setFocusable(true);setContentDescription(data.title);
        spoken=new Spoken(this,new Spoken.Source(){
            @Override public void items(List<Integer> out){
                if(data.categories!=null)for(int i=0;i<data.categories.length;i++)out.add(NAV+i);
                for(int i=scroll;i<Math.min(data.rows.length,scroll+VISIBLE);i++)out.add(i+1);
            }
            @Override public String text(int id){
                if(id>=NAV)return data.categories!=null&&id-NAV<data.categories.length?data.categories[id-NAV]:"";
                if(id<1||id>data.rows.length)return "";Row r=data.rows[id-1];return r.label+(r.value.isEmpty()?"":", "+r.value);
            }
            @Override public boolean bounds(int id,Rect out){
                if(id>=NAV){if(data.categories==null||id-NAV>=data.categories.length)return false;navBounds(id-NAV).round(out);return true;}
                if(id<=scroll||id>Math.min(data.rows.length,scroll+VISIBLE))return false;rowBounds(id-1).round(out);return true;
            }
            @Override public int focused(){return navFocus?NAV+data.category:focus+1;}
            @Override public void focus(int id){
                if(id>=NAV){chooseCategory(id-NAV);return;}
                if(id>0&&id<=data.rows.length){navFocus=false;focus=id-1;changed();}
            }
            @Override public void click(int id){
                if(id>=NAV){chooseCategory(id-NAV);return;}
                if(id>0&&id<=data.rows.length){navFocus=false;focus=id-1;activate(1);}
            }
            @Override public boolean clickable(int id){return id>=NAV||id>0&&id<=data.rows.length&&data.rows[id-1].action!=null;}
        });
        for(Row r:data.rows)if(r.network){network=new NetworkStatus.Watch(context,this::changed);break;}
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return spoken;}
    int focusedIndex(){return focus;}
    NetworkStatus.Snapshot networkSnapshot(){return network==null?NetworkStatus.read(getContext()):network.snapshot();}
    void select(int index){focus=Math.max(0,Math.min(index,data.rows.length-1));followFocus();aim(true);invalidate();}
    void navigationFocus(boolean nav){navFocus=nav&&data.categories!=null;changed();}
    private void followFocus(){
        if(focus<scroll)scroll=focus;if(focus>=scroll+VISIBLE)scroll=focus-VISIBLE+1;
        position=data.rows.length>VISIBLE?(focus+1)+" / "+data.rows.length:"";
    }
    void resume(){if(running)return;running=true;lastFrame=0;if(network!=null)network.start();changed();spoken.readAgain();}
    void pause(){
        running=false;armed=false;if(network!=null)network.stop();
        Choreographer.getInstance().removeFrameCallback(this);scheduled=false;
    }
    void changed(){if(!running)return;update.run();syncTheme();rebuildText();aim(false);invalidate();wake();}
    private void syncTheme(){
        if(title==null||!surface.refresh(getContext()))return;
        title.setColor(surface.ink);subtitle.setColor(surface.dim);hintPaint.setColor(surface.dim);
        navText.setColor(surface.ink);label.setColor(surface.ink);value.setColor(surface.dim);
        hint=null;
    }
    private void aim(boolean snap){
        for(int i=0;i<focusMotion.length;i++){focusMotion[i].target=!navFocus&&i==focus?1:0;if(snap)focusMotion[i].snap(focusMotion[i].target);}
    }
    private void wake(){if(running&&!scheduled){scheduled=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}}
    private float left(){return data.compact?1110:data.categories!=null?670:850;}
    private float right(){return 1824;}
    private float top(){return data.compact?244:226;}
    private RectF rowBounds(int at){float y=(top()+(at-scroll)*82)*k;row.set(left()*k,y,right()*k,y+74*k);return row;}
    private RectF navBounds(int at){float y=(210+at*78)*k;navBox.set(100*k,y,590*k,y+66*k);return navBox;}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        width=w;height=h;k=w/1920f;surface.size(getContext(),w,h);
        title=new TextPaint(Ui.text(Ui.displaySemibold,38*k,surface.ink));
        label=new TextPaint(Ui.text(Ui.medium,29*k,surface.ink));
        value=new TextPaint(Ui.text(Ui.regular,26*k,surface.dim));value.setTextAlign(Paint.Align.RIGHT);
        subtitle=Ui.text(Ui.regular,24*k,surface.dim);
        hintPaint=new TextPaint(Ui.text(Ui.regular,23*k,surface.dim));
        navText=new TextPaint(Ui.text(Ui.medium,27*k,surface.ink));
        icon.setStyle(Paint.Style.STROKE);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeWidth(2.8f*k);
        footer=Ui.tr("Voltar para fechar","Back to close");rebuildText();
    }
    private void rebuildText(){
        if(value==null)return;followFocus();
        for(int i=scroll;i<Math.min(data.rows.length,scroll+VISIBLE);i++){
            Row r=data.rows[i];float width=(right()-left()-100-(r.network?54:0))*k;
            if(width==r.fittedWidth&&r.value.equals(r.fittedValue))continue;
            r.fittedWidth=width;r.fittedValue=r.value;
            float room=width;
            if(!r.value.isEmpty())room-=Math.min(400*k,value.measureText(r.value))+26*k;
            r.shownLabel=TextUtils.ellipsize(r.label,label,Math.max(150*k,room),TextUtils.TruncateAt.END).toString();
            float available=Math.max(100*k,(right()-left()-100-(r.network?54:0))*k-label.measureText(r.shownLabel));
            r.shownValue=TextUtils.ellipsize(r.value,value,available,TextUtils.TruncateAt.END).toString();
        }
        String text=data.rows.length>0&&!data.rows[focus].hint.isEmpty()?data.rows[focus].hint:data.hint;
        int room=Math.max(1,Math.round((right()-left()-60)*k));
        if(hint==null||!hintText.equals(text)||hint.getWidth()!=room){
            hintText=text;hint=StaticLayout.Builder.obtain(text,0,text.length(),hintPaint,room)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(7*k,1)
                .setMaxLines(data.compact?3:7).setEllipsize(TextUtils.TruncateAt.END).build();
        }
    }
    @Override public void doFrame(long nanos){
        scheduled=false;if(!running)return;
        float dt=lastFrame==0?1/60f:Math.min(.05f,(nanos-lastFrame)/1e9f);lastFrame=nanos;boolean moving=false;
        for(int i=Math.max(0,scroll-1);i<Math.min(focusMotion.length,scroll+VISIBLE+1);i++)moving|=focusMotion[i].step(dt);
        if(moving){invalidate();scheduled=true;Choreographer.getInstance().postFrameCallback(this);}   // full rate, as on Home
    }
    @Override protected void onDraw(Canvas c){
        if(title==null)return;
        if(data.compact)c.drawColor(0x66000000);else surface.draw(c,width,height);
        panel.set((left()-32)*k,98*k,(right()+32)*k,Math.min(height-36*k,
            (top()+Math.min(data.rows.length,VISIBLE)*82+58)*k+(hint==null?0:hint.getHeight())));
        surface.panel(c,panel,34*k);
        if(data.categories!=null){
            panel.set(78*k,98*k,612*k,height-60*k);surface.panel(c,panel,34*k);
            c.drawText(Ui.tr("Ajustes","Settings"),112*k,162*k,title);
            for(int i=0;i<data.categories.length;i++){
                navBounds(i);
                if(i==data.category){fill.setColor(navFocus?surface.focusFill:surface.dark?0x22FFFFFF:0x20000000);c.drawRoundRect(navBox,18*k,18*k,fill);}
                navText.setColor(i==data.category&&navFocus?surface.focusInk:surface.ink);
                c.drawText(data.categories[i],navBox.left+24*k,navBox.centerY()+Ui.halfCap(navText),navText);
            }
        }else if(!data.compact){
            Symbols.draw(getContext(),c,data.symbol,400*k,380*k,110*k,surface.dim);
            c.drawText(Ui.tr("Ajustes","Settings"),130*k,565*k,title);
            c.drawText(footer,130*k,612*k,subtitle);
        }
        c.drawText(data.title,(left()+24)*k,160*k,title);
        c.drawText(data.subtitle,(left()+24)*k,199*k,subtitle);
        for(int i=scroll;i<Math.min(data.rows.length,scroll+VISIBLE);i++){
            Row r=data.rows[i];rowBounds(i);float f=Ui.clamp(focusMotion[i].value);
            if(f>.01f){fill.setColor(Ui.alpha(surface.focusFill,f));c.drawRoundRect(row,18*k,18*k,fill);}
            float x=row.left+26*k;if(r.network){networkIcon(c,x+15*k,row.centerY(),r);x+=54*k;}
            label.setColor(mix(surface.ink,surface.focusInk,f));value.setColor(mix(surface.dim,surface.focusDim,f));
            float base=row.centerY()+Ui.halfCap(label);c.drawText(r.shownLabel,x,base,label);c.drawText(r.shownValue,row.right-26*k,base,value);
        }
        if(hint!=null){c.save();c.translate((left()+26)*k,(top()+Math.min(data.rows.length,VISIBLE)*82+28)*k);hint.draw(c);c.restore();}
        if(!position.isEmpty()){value.setColor(surface.dim);c.drawText(position,(right()-25)*k,199*k,value);}
    }
    private void networkIcon(Canvas c,float x,float y,Row r){
        icon.setColor(r.color);
        if("Ethernet".equals(r.transport)){
            arc.set(x-20*k,y-18*k,x+20*k,y+10*k);c.drawRoundRect(arc,3*k,3*k,icon);
            c.drawLine(x,y+10*k,x,y+20*k,icon);c.drawLine(x-12*k,y+20*k,x+12*k,y+20*k,icon);
        }else{
            for(int i=0;i<3;i++){float radius=(10+i*9)*k;arc.set(x-radius,y+14*k-radius,x+radius,y+14*k+radius);c.drawArc(arc,225,90,false,icon);}
            fill.setColor(r.color);c.drawCircle(x,y+12*k,2.5f*k,fill);
        }
        if(r.state==NetworkStatus.OFFLINE||r.state==NetworkStatus.UNKNOWN)c.drawLine(x-22*k,y-22*k,x+22*k,y+22*k,icon);
    }
    private static int mix(int a,int b,float f){
        int result=0;for(int shift=0;shift<=24;shift+=8)result|=Math.round((a>>>shift&255)+((b>>>shift&255)-(a>>>shift&255))*f)<<shift;return result;
    }
    private void activate(int direction){
        if(data.rows.length==0)return;Row r=data.rows[focus];if(r.action==null)return;r.action.run(direction);changed();spoken.focusMoved();
    }
    private void chooseCategory(int index){
        if(data.categories==null||index<0||index>=data.categories.length)return;
        if(data.selectCategory!=null)data.selectCategory.accept(index);
    }
    boolean onKey(KeyEvent e){
        boolean down=e.getAction()==KeyEvent.ACTION_DOWN;int code=e.getKeyCode();
        if(down&&(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN
                ||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_BACK))armed=false;
        if(code==KeyEvent.KEYCODE_BACK&&data.categories!=null&&!navFocus){
            if(!down){navFocus=true;changed();spoken.focusMoved();}return true;
        }
        if(navFocus){
            if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){
                if(down)chooseCategory(Math.max(0,Math.min(data.categories.length-1,data.category+(code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1))));return true;
            }
            if(code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER){
                if(!down){navFocus=false;changed();spoken.focusMoved();}return true;
            }
            if(code==KeyEvent.KEYCODE_DPAD_LEFT)return true;
        }
        switch(code){
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:
                if(down){int step=code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1;
                    focus=SettingsNavigation.next(focus,step,data.rows.length);
                    changed();spoken.focusMoved();}return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:
                if(code==KeyEvent.KEYCODE_DPAD_LEFT&&data.categories!=null){
                    if(down){navFocus=true;changed();spoken.focusMoved();}return true;}
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if(down){if(e.getRepeatCount()==0)armed=true;}
                else{if(armed&&!e.isCanceled()&&spoken.keyClick())activate(1);armed=false;}return true;
            default:return false;
        }
    }
}
