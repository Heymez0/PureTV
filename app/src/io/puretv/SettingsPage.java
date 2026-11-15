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

/**
 * Every settings page: the categories on the left (each with its symbol; the open one carries the gold mark) and the
 * rows of the open category on the right. A row shows its value as text, or as a switch when it is simply on or off;
 * a row that only reports something (RAM, the IP address) is drawn quieter: its value is what matters.
 * Static surfaces; the focus animation stops completely when settled.
 */
final class SettingsPage extends View implements Choreographer.FrameCallback {
    interface Action { void run(int direction); }
    static final class Row {
        final String label; String value="",shownValue="",shownLabel="",hint="";
        final Action action;
        /** On or off, drawn as a switch instead of the value's text; null for any other value. */
        Boolean toggle;
        boolean chosen;
        private String fittedValue;private float fittedWidth=Float.NaN;private Boolean fittedToggle;
        boolean network; int state=NetworkStatus.UNKNOWN; String transport=""; int color=Look.GOOD;
        Row(String label,Action action){this.label=label;this.action=action;}
        /** An on/off value: its text (for TalkBack and narrow layouts) and the switch. */
        void set(boolean on){value=Ui.tr(on?"On":"Off");toggle=on;}
        /** One of a list of choices: the one in use carries the gold check. */
        void choose(boolean inUse){value=inUse?Ui.tr("In use"):"";chosen=inUse;}
    }
    static final class Data {
        final String title,subtitle,symbol; final Row[] rows; String hint="";
        /** The line under the page's name on a standalone page; null = "Back to close". */
        String footer;
        boolean compact; String[] categories,categorySymbols; int category; IntConsumer selectCategory;
        Data(String title,String subtitle,String symbol,Row... rows){this.title=title;this.subtitle=subtitle;this.symbol=symbol;this.rows=rows;}
    }
    private static final int VISIBLE=7,NAV=1000;
    private static final float ROW_PITCH=82,ROW_H=74,ROW_RADIUS=18,NAV_PITCH=78,NAV_H=66;
    private final SettingsSurface surface=new SettingsSurface();
    private final Data data; private final Runnable update;
    private final Spring[] focusMotion; private final Spring navMotion; private final Spoken spoken;
    private final RectF row=new RectF(),arc=new RectF(),panel=new RectF(),navBox=new RectF(),mark=new RectF();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),icon=new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint title,label,value,hintPaint,navText; private Paint subtitle,brand;
    private StaticLayout hint; private String hintText="",position="",footer="";
    private String[] shownCategories=new String[0];
    private float k=1; private int focus,scroll,width,height; private long lastFrame;
    private boolean running,armed,scheduled,navFocus; private NetworkStatus.Watch network;
    SettingsPage(Context context,Data data,Runnable update){
        super(context);this.data=data;this.update=update;
        for(int i=0;i<data.rows.length;i++)if(data.rows[i].action!=null){focus=i;break;}
        navFocus=data.categories!=null;focusMotion=new Spring[data.rows.length];
        for(int i=0;i<focusMotion.length;i++)focusMotion[i]=new Spring(.28f,.85f,!navFocus&&i==focus?1:0);
        navMotion=new Spring(.28f,.85f,navFocus?1:0);
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
    void navigationFocus(boolean nav){navFocus=nav&&data.categories!=null;aim(true);changed();}
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
        title.setColor(surface.ink);subtitle.setColor(surface.dim);hintPaint.setColor(surface.dim);brand.setColor(surface.dim);
        navText.setColor(surface.ink);label.setColor(surface.ink);value.setColor(surface.dim);
        hint=null;
    }
    private void aim(boolean snap){
        for(int i=0;i<focusMotion.length;i++){focusMotion[i].target=!navFocus&&i==focus?1:0;if(snap)focusMotion[i].snap(focusMotion[i].target);}
        navMotion.target=navFocus?1:0;if(snap)navMotion.snap(navMotion.target);
    }
    private void wake(){if(running&&!scheduled){scheduled=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}}
    private float left(){return data.compact?1110:data.categories!=null?670:850;}
    private float right(){return 1824;}
    private float top(){return data.compact?244:226;}
    /** Where the title starts: after the category's symbol when the categories are shown. */
    private float titleLeft(){return left()+(data.categories!=null?98:24);}
    private RectF rowBounds(int at){float y=(top()+(at-scroll)*ROW_PITCH)*k;row.set(left()*k,y,right()*k,y+ROW_H*k);return row;}
    private RectF navBounds(int at){float y=(210+at*NAV_PITCH)*k;navBox.set(100*k,y,590*k,y+NAV_H*k);return navBox;}
    private String symbol(int category){
        String[] s=data.categorySymbols;return s!=null&&category>=0&&category<s.length?s[category]:data.symbol;
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        width=w;height=h;k=w/1920f;surface.size(getContext(),w,h);
        title=new TextPaint(Ui.text(Ui.displaySemibold,38*k,surface.ink));
        label=new TextPaint(Ui.text(Ui.medium,29*k,surface.ink));
        value=new TextPaint(Ui.text(Ui.regular,26*k,surface.dim));value.setTextAlign(Paint.Align.RIGHT);
        subtitle=Ui.text(Ui.regular,24*k,surface.dim);
        hintPaint=new TextPaint(Ui.text(Ui.regular,23*k,surface.dim));
        navText=new TextPaint(Ui.text(Ui.medium,27*k,surface.ink));
        brand=Ui.text(Ui.semibold,21*k,surface.dim);brand.setLetterSpacing(.14f);
        icon.setStyle(Paint.Style.STROKE);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeWidth(2.8f*k);
        footer=data.footer!=null?data.footer:Ui.tr("Voltar para fechar","Back to close");
        if(data.categories!=null){
            shownCategories=new String[data.categories.length];
            for(int i=0;i<shownCategories.length;i++)shownCategories[i]=TextUtils.ellipsize(data.categories[i],navText,
                (data.categorySymbols!=null?390:440)*k,TextUtils.TruncateAt.END).toString();
        }
        rebuildText();
    }
    private void rebuildText(){
        if(value==null)return;followFocus();
        for(int i=scroll;i<Math.min(data.rows.length,scroll+VISIBLE);i++){
            Row r=data.rows[i];float width=(right()-left()-100-(r.network?54:0))*k;
            if(width==r.fittedWidth&&r.value.equals(r.fittedValue)&&r.toggle==r.fittedToggle)continue;
            r.fittedWidth=width;r.fittedValue=r.value;r.fittedToggle=r.toggle;
            float room=width;
            if(r.toggle!=null)room-=surface.toggleWidth()+26*k;
            else if(!r.value.isEmpty())room-=Math.min(400*k,value.measureText(r.value))+26*k;
            r.shownLabel=TextUtils.ellipsize(r.label,label,Math.max(150*k,room),TextUtils.TruncateAt.END).toString();
            float available=Math.max(100*k,width-label.measureText(r.shownLabel));
            r.shownValue=r.toggle!=null?"":TextUtils.ellipsize(r.value,value,available,TextUtils.TruncateAt.END).toString();
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
        float dt=lastFrame==0?1/60f:Math.min(.05f,(nanos-lastFrame)/1e9f);lastFrame=nanos;boolean moving=navMotion.step(dt);
        for(int i=Math.max(0,scroll-1);i<Math.min(focusMotion.length,scroll+VISIBLE+1);i++)moving|=focusMotion[i].step(dt);
        if(moving){invalidate();scheduled=true;Choreographer.getInstance().postFrameCallback(this);}   // full rate, as on Home
    }
    @Override protected void onDraw(Canvas c){
        if(title==null)return;
        if(data.compact)c.drawColor(0x66000000);else surface.draw(c,width,height);
        int shown=Math.min(data.rows.length,VISIBLE);
        float listBottom=(top()+shown*ROW_PITCH)*k;
        panel.set((left()-32)*k,98*k,(right()+32)*k,Math.min(height-36*k,listBottom+58*k+(hint==null?0:hint.getHeight())));
        if(data.compact)surface.popup(c,panel,34*k);else surface.panel(c,panel,34*k);
        if(data.categories!=null)drawCategories(c);
        else if(!data.compact){
            float cx=400*k,cy=380*k;
            mark.set(cx-108*k,cy-108*k,cx+108*k,cy+108*k);surface.well(c,mark,108*k,0);
            Symbols.draw(getContext(),c,data.symbol,cx,cy,96*k,accent());
            // Centred under the symbol, in the middle of the space left of the panel.
            String name=Ui.tr("Ajustes","Settings");
            c.drawText(name,cx-title.measureText(name)/2,565*k,title);
            c.drawText(footer,cx-subtitle.measureText(footer)/2,612*k,subtitle);
        }
        if(data.categories!=null){
            float cx=(left()+50)*k,cy=170*k;
            fill.setColor(Ui.alpha(Look.GOLD,surface.dark?.16f:.24f));c.drawCircle(cx,cy,30*k,fill);
            Symbols.draw(getContext(),c,symbol(data.category),cx,cy,27*k,accent());
        }
        c.drawText(data.title,titleLeft()*k,160*k,title);
        c.drawText(data.subtitle,titleLeft()*k,199*k,subtitle);
        int end=Math.min(data.rows.length,scroll+VISIBLE);
        for(int i=scroll;i<end;i++){
            Row r=data.rows[i];rowBounds(i);float f=Ui.clamp(focusMotion[i].value);
            // A hairline in the gap below each row; it fades beside the focus, which needs no line.
            if(i<end-1)surface.hairline(c,row.left+26*k,row.right-26*k,row.bottom+3.5f*k,1-Math.max(f,Ui.clamp(focusMotion[i+1].value)));
            surface.focus(c,row,ROW_RADIUS*k,f);
            float x=row.left+26*k;if(r.network){networkIcon(c,x+15*k,row.centerY(),r);x+=54*k;}
            boolean info=r.action==null;
            label.setColor(Look.mix(info?surface.dim:surface.ink,surface.focusInk,f));
            value.setColor(Look.mix(info?surface.ink:surface.dim,surface.focusDim,f));
            float base=row.centerY()+Ui.halfCap(label);c.drawText(r.shownLabel,x,base,label);
            if(r.toggle!=null)surface.toggle(c,row.right-26*k,row.centerY(),r.toggle,true,f);
            else if(r.chosen)Symbols.draw(getContext(),c,"check",row.right-44*k,row.centerY(),30*k,Look.mix(accent(),surface.focusInk,f));
            else c.drawText(r.shownValue,row.right-26*k,base,value);
        }
        if(data.rows.length>VISIBLE){
            // Where the visible rows are in the list.
            float trackTop=top()*k,trackBottom=listBottom-(ROW_PITCH-ROW_H)*k,x=(right()+12)*k,span=trackBottom-trackTop;
            fill.setColor(surface.line);c.drawRoundRect(x,trackTop,x+4*k,trackBottom,2*k,2*k,fill);
            float from=trackTop+span*scroll/data.rows.length,to=trackTop+span*(scroll+VISIBLE)/data.rows.length;
            fill.setColor(Ui.alpha(surface.dim,.75f));c.drawRoundRect(x,from,x+4*k,to,2*k,2*k,fill);
        }
        if(hint!=null){c.save();c.translate((left()+26)*k,listBottom+28*k);hint.draw(c);c.restore();}
        if(!position.isEmpty()){value.setColor(surface.dim);c.drawText(position,(right()-25)*k,199*k,value);}
    }
    /** Gold reads on the dark surfaces; on the light ones its deeper shade does. */
    private int accent(){return surface.dark?Look.GOLD:Look.GOLD_DEEP;}
    private void drawCategories(Canvas c){
        panel.set(78*k,98*k,612*k,height-60*k);surface.panel(c,panel,34*k);
        c.drawText(Ui.tr("Ajustes","Settings"),112*k,162*k,title);
        float nav=Ui.clamp(navMotion.value);boolean symbols=data.categorySymbols!=null;
        for(int i=0;i<data.categories.length;i++){
            navBounds(i);boolean open=i==data.category;float f=open?nav:0;
            if(open){
                // The open category: the focus while choosing a category; a quiet fill and the gold mark while in its rows.
                if(f<.99f){
                    surface.well(c,navBox,ROW_RADIUS*k,0);
                    mark.set(navBox.left,navBox.centerY()-15*k,navBox.left+5*k,navBox.centerY()+15*k);
                    fill.setColor(Ui.alpha(accent(),1-f));c.drawRoundRect(mark,2.5f*k,2.5f*k,fill);
                }
                surface.focus(c,navBox,ROW_RADIUS*k,f);
            }
            int ink=Look.mix(open?surface.ink:surface.dim,surface.focusInk,f);
            float x=navBox.left+24*k;
            if(symbols){Symbols.draw(getContext(),c,symbol(i),x+15*k,navBox.centerY(),25*k,ink);x+=52*k;}
            navText.setColor(Look.mix(surface.ink,surface.focusInk,f));
            c.drawText(i<shownCategories.length?shownCategories[i]:data.categories[i],x,navBox.centerY()+Ui.halfCap(navText),navText);
        }
        // The wordmark at the foot of the categories, when they leave room for it.
        float base=height-60*k-40*k;
        if((210+data.categories.length*NAV_PITCH)*k<base-40*k){
            fill.setColor(accent());c.drawCircle(118*k,base-8*k,5*k,fill);
            c.drawText("PURETV",134*k,base,brand);
        }
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
