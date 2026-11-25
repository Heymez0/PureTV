package io.puretv;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
 * Every settings page, laid out like a TV maker's settings: the categories on the left, the open category's rows in
 * the middle, and on the right a detail pane for the focused row (a picture of the result, what the setting does, its
 * choices and the keys that work now).
 *
 * The category column is wide while a category is being chosen and shrinks to its symbols once the focus is in the
 * rows, so the detail pane gets the room. Rows are grouped under small headings and carry a symbol; a row shows its
 * value under its name, a switch when it is simply on or off, or the value on the right when it only reports something.
 * A row with choices opens them in the detail pane: the arrows move through them (a picture rows shows each one), OK
 * applies the focused one and Back leaves them as they were.
 *
 * Pages without categories (AirPlay, updates, the remote test) use the same list and detail pane. Text is laid out when
 * something changes, never while drawing; the springs stop completely when settled.
 */
final class SettingsPage extends View implements Choreographer.FrameCallback {
    interface Action { void run(int direction); }
    /** A picture of what a setting (or one of its choices) does; drawn from bitmaps that are already loaded. */
    interface Preview { void draw(Canvas c, RectF box, boolean dark); }
    /** One answer of a choice row: its name, an optional line under it, and an optional picture. */
    /** "Settings › Category › …" for a page opened from Settings, each name translated. */
    static String crumb(String... names){
        StringBuilder b=new StringBuilder(Ui.tr("Settings"));for(String n:names)b.append(" › ").append(Ui.tr(n));return b.toString();
    }
    static final class Choice {
        final String label,sub; final Preview art; String shownLabel="",shownSub=""; float textScale=1;
        Choice(String label,String sub,Preview art){this.label=label;this.sub=sub==null?"":sub;this.art=art;}
    }
    static final class Row {
        final String label; String value="",shownValue="",shownLabel="",hint="";
        final Action action;
        /** On or off, drawn as a switch instead of the value's text; null for any other value. */
        Boolean toggle;
        boolean chosen;
        private String fittedValue;private float fittedWidth=Float.NaN;private Boolean fittedToggle;
        boolean network; int state=NetworkStatus.UNKNOWN; String transport=""; int color=Look.GOOD;
        /** A large picture in the detail pane while the row has the focus. */
        Preview preview;
        /** Works only in some situations right now (its hint says when): drawn dimmer, still usable. */
        boolean muted;
        /** The row's symbol (Symbols), or null. */
        String symbol;
        /** Starts a group: the heading drawn above this row. */
        String section;
        /** A short gold label in the detail pane: where or when the setting applies. */
        String note="";
        /** Answers picked in the detail pane, the one in use, and what picking one does. */
        Choice[] choices; int current; IntConsumer pick;
        private String shownSection="",shownDetailValue="";
        Row(String label,Action action){this.label=label;this.action=action;}
        /** An on/off value: its text (for TalkBack and narrow layouts) and the switch. */
        void set(boolean on){value=Ui.tr(on?"On":"Off");toggle=on;}
        /** One of a list of choices: the one in use carries the gold check. */
        void choose(boolean inUse){value=inUse?Ui.tr("In use"):"";chosen=inUse;}
        Row icon(String name){symbol=name;return this;}
        Row group(String heading){section=heading;return this;}
        Row value(String text){value=text;return this;}
    }
    static final class Data {
        final String title,subtitle,symbol; final Row[] rows; String hint="";
        /** The line under the page's name on a standalone page; null = "Close" on the Back key. */
        String footer;
        /** The subtitle and footer are an instruction to see at once (how to leave): drawn in the accent colour. */
        boolean urgent;
        /** Where this page sits, above its name ("Settings › Home screen"); empty = "Settings". */
        String crumb="";
        String[] categories,categorySymbols; int category; IntConsumer selectCategory;
        Data(String title,String subtitle,String symbol,Row... rows){this.title=title;this.subtitle=subtitle;this.symbol=symbol;this.rows=rows;}
    }

    private static final int NAV=1000;
    // The 1920×1080 layout: panels 60 from the top and bottom; the category column grows from RAIL_MIN to RAIL_MAX.
    private static final float EDGE=60,RAIL_X=50,RAIL_MIN=112,RAIL_MAX=520,GAP=24,LIST_W=760,LIST_W_ALONE=860,RIGHT=1870;
    private static final float ROW_H=80,ROW_PITCH=88,SECTION_H=46,ROW_RADIUS=18,NAV_TOP=166,NAV_PITCH=78,NAV_H=66,PAD=44;
    private final SettingsSurface surface=new SettingsSurface();
    private final Data data; private final Runnable update;
    private final Spring[] focusMotion; private final Spring navMotion,scrollMotion,pickMotion; private final Spoken spoken;
    private final RectF row=new RectF(),arc=new RectF(),box=new RectF(),navBox=new RectF(),mark=new RectF(),card=new RectF();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),icon=new Paint(Paint.ANTI_ALIAS_FLAG),shade=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final LinearGradient shadeUnit=new LinearGradient(0,0,0,1,0x00000000,0xB0000000,Shader.TileMode.CLAMP);
    private final android.graphics.Matrix shadeMatrix=new android.graphics.Matrix();
    private TextPaint valueBig,title,label,value,small,section,hintPaint,navText,keyCap,keyText,detailTitle,choiceText,crumbPaint,brand,chip;
    private StaticLayout detailHint,navHint,titleLayout; private int hintLines; private float choiceSize; private String hintText="",navHintText="",position="",footer="";
    private String[] shownCategories=new String[0];
    private float[] rowTop=new float[0]; private float contentHeight;
    private float k=1; private int focus,width,height; private long lastFrame;
    private boolean running,armed,scheduled,navFocus,picking,icons; private int pickAt;
    private NetworkStatus.Watch network;

    SettingsPage(Context context,Data data,Runnable update){
        super(context);this.data=data;this.update=update;
        for(int i=0;i<data.rows.length;i++)if(data.rows[i].action!=null){focus=i;break;}
        navFocus=data.categories!=null;focusMotion=new Spring[data.rows.length];
        for(int i=0;i<focusMotion.length;i++)focusMotion[i]=new Spring(.28f,.85f,!navFocus&&i==focus?1:0);
        navMotion=new Spring(.34f,.9f,navFocus?1:0);scrollMotion=new Spring(.3f,.95f,0);pickMotion=new Spring(.26f,.85f,0);
        for(Row r:data.rows)if(r.symbol!=null||r.network){icons=true;break;}
        setFocusable(true);setContentDescription(data.title);
        spoken=new Spoken(this,new Spoken.Source(){
            @Override public void items(List<Integer> out){
                if(data.categories!=null)for(int i=0;i<data.categories.length;i++)out.add(NAV+i);
                for(int i=0;i<data.rows.length;i++)if(visible(i))out.add(i+1);
            }
            @Override public String text(int id){
                if(id>=NAV)return data.categories!=null&&id-NAV<data.categories.length?data.categories[id-NAV]:"";
                if(id<1||id>data.rows.length)return "";Row r=data.rows[id-1];
                if(picking&&id-1==focus&&r.choices!=null&&pickAt<r.choices.length)return r.label+", "+r.choices[pickAt].label;
                return r.label+(r.value.isEmpty()?"":", "+r.value)+(r.hint.isEmpty()?"":". "+r.hint);
            }
            @Override public boolean bounds(int id,Rect out){
                if(id>=NAV){if(data.categories==null||id-NAV>=data.categories.length)return false;navBounds(id-NAV).round(out);return true;}
                if(id<1||id>data.rows.length||!visible(id-1))return false;rowBounds(id-1).round(out);return true;
            }
            @Override public int focused(){return navFocus?NAV+data.category:focus+1;}
            @Override public void focus(int id){
                if(id>=NAV){chooseCategory(id-NAV);return;}
                if(id>0&&id<=data.rows.length){navFocus=false;picking=false;focus=id-1;changed();}
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
    void select(int index){focus=Math.max(0,Math.min(index,data.rows.length-1));picking=false;layoutRows();aim(true);invalidate();}
    void navigationFocus(boolean nav){navFocus=nav&&data.categories!=null;picking=false;aim(true);changed();}
    void resume(){if(running)return;running=true;lastFrame=0;if(network!=null)network.start();changed();spoken.readAgain();}
    void pause(){
        running=false;armed=false;if(network!=null)network.stop();
        Choreographer.getInstance().removeFrameCallback(this);scheduled=false;
    }
    void changed(){if(!running)return;update.run();syncTheme();rebuildText();aim(false);invalidate();wake();}
    private void syncTheme(){
        if(title==null||!surface.refresh(getContext()))return;
        paints();detailHint=null;navHint=null;titleLayout=null;
    }
    private void aim(boolean snap){
        for(int i=0;i<focusMotion.length;i++){focusMotion[i].target=!navFocus&&i==focus?1:0;if(snap)focusMotion[i].snap(focusMotion[i].target);}
        navMotion.target=navFocus?1:0;if(snap)navMotion.snap(navMotion.target);
        scrollMotion.target=scrollTarget();if(snap)scrollMotion.snap(scrollMotion.target);
        pickMotion.target=picking?1:0;if(snap)pickMotion.snap(pickMotion.target);
    }
    private void wake(){if(running&&!scheduled){scheduled=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}}

    // MARK: - Geometry (1920×1080 units, times k)

    private boolean rail(){return data.categories!=null;}
    private float bottom(){return height/k-EDGE;}
    private float railWidth(){return RAIL_MIN+(RAIL_MAX-RAIL_MIN)*Ui.clamp(navMotion.value);}
    private float listLeft(){return rail()?RAIL_X+railWidth()+GAP:EDGE;}
    private float listWidth(){return rail()?LIST_W:LIST_W_ALONE;}
    private float listRight(){return listLeft()+listWidth();}
    private float detailLeft(){return listRight()+GAP;}
    /** The rows' window: under the page's name, to the panel's foot. */
    private float viewTop(){return data.subtitle.isEmpty()?236:262;}
    private float viewBottom(){return bottom()-22;}
    private boolean visible(int at){
        if(at<0||at>=rowTop.length)return false;
        float y=viewTop()+rowTop[at]-scrollMotion.value;return y+ROW_H>viewTop()&&y<viewBottom();
    }
    private RectF rowBounds(int at){
        float y=(viewTop()+rowTop[at]-scrollMotion.value)*k;
        row.set((listLeft()+18)*k,y,(listRight()-18)*k,y+ROW_H*k);return row;
    }
    private RectF navBounds(int at){
        float y=(NAV_TOP+at*NAV_PITCH)*k;navBox.set((RAIL_X+20)*k,y,(RAIL_X+railWidth()-20)*k,y+NAV_H*k);return navBox;
    }
    private String symbol(int category){
        String[] s=data.categorySymbols;return s!=null&&category>=0&&category<s.length?s[category]:data.symbol;
    }
    /** Where each row starts in the scrolling list: rows 88 apart, plus a heading's height where a group starts. */
    private void layoutRows(){
        if(rowTop.length!=data.rows.length)rowTop=new float[data.rows.length];
        float y=0;
        for(int i=0;i<data.rows.length;i++){
            Row r=data.rows[i];if(r.section!=null&&!r.section.isEmpty())y+=SECTION_H;
            rowTop[i]=y;y+=ROW_PITCH;
        }
        contentHeight=y;
        position=data.rows.length>7?(focus+1)+" / "+data.rows.length:"";
    }
    /** Keeps the focused row (and the heading of its group) inside the window. */
    private float scrollTarget(){
        if(rowTop.length==0||focus>=rowTop.length)return 0;
        float view=viewBottom()-viewTop(),max=Math.max(0,contentHeight-ROW_PITCH+ROW_H-view);
        Row r=data.rows[focus];float top=rowTop[focus]-(r.section!=null&&!r.section.isEmpty()?SECTION_H:0),end=rowTop[focus]+ROW_H;
        float s=scrollMotion.target;
        if(top-s<0)s=top;
        if(end-s>view-20)s=end-view+20;
        if(focus==0)s=0;
        s=Math.max(0,Math.min(max,s));
        // Settle on the start of a row (or of its heading), never with a sliver of the row above showing at the top;
        // at the end of the list that may leave a little room below the last row.
        if(s>0)for(int i=0;i<rowTop.length;i++){
            Row it=data.rows[i];float b=rowTop[i]-(it.section!=null&&!it.section.isEmpty()?SECTION_H:0);
            if(b>=s-1){s=Math.min(b,top);break;}
        }
        return s;
    }

    // MARK: - Text, laid out when something changes

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        width=w;height=h;k=w/1920f;surface.size(getContext(),w,h);
        paints();
        footer=data.footer!=null?data.footer:Ui.tr("Close");
        if(data.categories!=null){
            shownCategories=new String[data.categories.length];
            for(int i=0;i<shownCategories.length;i++)shownCategories[i]=TextUtils.ellipsize(data.categories[i],navText,
                (RAIL_MAX-132)*k,TextUtils.TruncateAt.END).toString();
        }
        for(Row r:data.rows)r.fittedWidth=Float.NaN;
        layoutRows();aim(true);rebuildText();
    }
    private void paints(){
        title=new TextPaint(Ui.text(Ui.displaySemibold,40*k,surface.ink));
        detailTitle=new TextPaint(Ui.text(Ui.displaySemibold,36*k,surface.ink));
        label=new TextPaint(Ui.text(Ui.medium,28*k,surface.ink));
        value=new TextPaint(Ui.text(Ui.regular,26*k,surface.dim));value.setTextAlign(Paint.Align.RIGHT);
        small=new TextPaint(Ui.text(Ui.regular,21*k,surface.dim));
        valueBig=new TextPaint(Ui.text(Ui.medium,28*k,accent()));
        section=new TextPaint(Ui.text(Ui.semibold,18*k,surface.dim));section.setLetterSpacing(.14f);
        hintPaint=new TextPaint(Ui.text(Ui.regular,25*k,surface.dim));
        navText=new TextPaint(Ui.text(Ui.medium,26*k,surface.ink));
        crumbPaint=new TextPaint(Ui.text(Ui.medium,22*k,surface.dim));
        keyCap=new TextPaint(Ui.text(Ui.semibold,17*k,surface.ink));keyCap.setTextAlign(Paint.Align.CENTER);
        keyText=new TextPaint(Ui.text(Ui.regular,20*k,surface.dim));
        choiceSize=20*k;choiceText=new TextPaint(Ui.text(Ui.semibold,choiceSize,0xFFFFFFFF));
        chip=new TextPaint(Ui.text(Ui.semibold,19*k,accent()));
        brand=new TextPaint(Ui.text(Ui.semibold,21*k,surface.dim));brand.setLetterSpacing(.14f);
        icon.setStyle(Paint.Style.STROKE);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeWidth(2.8f*k);
    }
    /** The detail pane's inner width in the 1920 space (with the categories shrunk). */
    private float detailWidth(){return RIGHT-(rail()?RAIL_X+RAIL_MIN+GAP+LIST_W+GAP:EDGE+LIST_W_ALONE+GAP)-2*PAD;}
    private void rebuildText(){
        if(value==null)return;layoutRows();
        float rowRoom=(listWidth()-36-44-(icons?66:0))*k;
        for(Row r:data.rows){
            if(rowRoom==r.fittedWidth&&r.value.equals(r.fittedValue)&&r.toggle==r.fittedToggle)continue;
            r.fittedWidth=rowRoom;r.fittedValue=r.value;r.fittedToggle=r.toggle;
            float right=r.toggle!=null?surface.toggleWidth()+20*k:r.chosen||r.action!=null?44*k:0;
            if(info(r)&&!r.value.isEmpty())right=Math.min(320*k,value.measureText(r.value))+20*k;
            r.shownLabel=TextUtils.ellipsize(r.label,label,Math.max(140*k,rowRoom-right),TextUtils.TruncateAt.END).toString();
            r.shownValue=info(r)?TextUtils.ellipsize(r.value,value,Math.max(100*k,rowRoom-label.measureText(r.shownLabel)-20*k),TextUtils.TruncateAt.END).toString()
                :TextUtils.ellipsize(r.value,small,Math.max(100*k,rowRoom-right),TextUtils.TruncateAt.END).toString();
            String heading=r.section==null?"":r.section.toUpperCase(Ui.locale());
            r.shownSection=TextUtils.ellipsize(heading,section,(listWidth()-80)*k,TextUtils.TruncateAt.END).toString();
        }
        float w=detailWidth()*k;
        if(data.rows.length>0){
            Row r=data.rows[focus];
            String text=!r.hint.isEmpty()?r.hint:data.hint;
            int lines=r.choices!=null?4:r.preview!=null?4:9;
            if(detailHint==null||!hintText.equals(text)||detailHint.getWidth()!=(int)w||hintLines!=lines){
                hintText=text;hintLines=lines;detailHint=layout(text,hintPaint,(int)w,lines);
            }
            titleLayout=layout(r.label,detailTitle,(int)w,2);
            r.shownDetailValue=TextUtils.ellipsize(r.value,valueBig,w,TextUtils.TruncateAt.END).toString();
            if(r.choices!=null){
                int cols=columns(r);float cw=hasArt(r)?(w-(cols-1)*18*k)/cols-24*k:w-110*k;
                for(Choice ch:r.choices){
                    // A long name on a card first gets up to a fifth smaller, so it fits whole before it is shortened.
                    ch.textScale=hasArt(r)?Math.max(.8f,Math.min(1,Math.max(60*k,cw)/Math.max(1,choiceText.measureText(ch.label)))):1;
                    choiceText.setTextSize(choiceSize*ch.textScale);
                    ch.shownLabel=TextUtils.ellipsize(ch.label,hasArt(r)?choiceText:label,Math.max(60*k,cw),TextUtils.TruncateAt.END).toString();
                    choiceText.setTextSize(choiceSize);
                    ch.shownSub=TextUtils.ellipsize(ch.sub,small,Math.max(60*k,w-140*k),TextUtils.TruncateAt.END).toString();
                }
                if(r.current<0||r.current>=r.choices.length)r.current=0;
                if(pickAt>=r.choices.length)pickAt=r.current;
            }
        }
        if(rail()){
            float nw=(RIGHT-(RAIL_X+RAIL_MAX+GAP+LIST_W+GAP)-2*PAD)*k;
            String text=data.hint==null?"":data.hint;
            if(navHint==null||!navHintText.equals(text)||navHint.getWidth()!=(int)Math.max(1,nw)){navHintText=text;navHint=layout(text,hintPaint,(int)Math.max(1,nw),12);}
        }
    }
    private static StaticLayout layout(String text,TextPaint paint,int width,int lines){
        return StaticLayout.Builder.obtain(text,0,text.length(),paint,Math.max(1,width)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).setLineSpacing(paint.getTextSize()*.3f,1).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
    }
    private static boolean info(Row r){return r.action==null;}
    private static boolean hasArt(Row r){
        if(r.choices==null)return false;for(Choice c:r.choices)if(c.art!=null)return true;return false;
    }
    private static int columns(Row r){return !hasArt(r)?1:r.choices.length<=3?r.choices.length:4;}

    // MARK: - Drawing

    @Override public void doFrame(long nanos){
        scheduled=false;if(!running)return;
        float dt=lastFrame==0?1/60f:Math.min(.05f,(nanos-lastFrame)/1e9f);lastFrame=nanos;
        boolean moving=navMotion.step(dt);moving|=scrollMotion.step(dt);moving|=pickMotion.step(dt);
        for(int i=0;i<focusMotion.length;i++)moving|=focusMotion[i].step(dt);
        if(moving){invalidate();scheduled=true;Choreographer.getInstance().postFrameCallback(this);}   // full rate, as on Home
    }
    @Override protected void onDraw(Canvas c){
        if(title==null)return;
        surface.draw(c,width,height);
        if(rail())drawCategories(c);
        drawList(c);
        drawDetail(c);
    }
    /** Gold reads on the dark surfaces; on the light ones its deeper shade does. */
    private int accent(){return surface.dark?Look.GOLD:Look.GOLD_DEEP;}

    private void drawCategories(Canvas c){
        float e=Ui.clamp(navMotion.value),w=railWidth();
        box.set(RAIL_X*k,EDGE*k,(RAIL_X+w)*k,bottom()*k);surface.panel(c,box,34*k);
        c.save();c.clipRect(box);
        if(e>.02f){title.setAlpha(Math.round(255*e));c.drawText(Ui.tr("Ajustes","Settings"),(RAIL_X+36)*k,128*k,title);title.setAlpha(255);}
        for(int i=0;i<data.categories.length;i++){
            navBounds(i);boolean open=i==data.category;float f=open&&navFocus?Ui.clamp(navMotion.value):0;
            if(open){
                if(!navFocus||f<.99f){
                    surface.well(c,navBox,ROW_RADIUS*k,0);
                    mark.set(navBox.left-14*k,navBox.centerY()-16*k,navBox.left-10*k,navBox.centerY()+16*k);
                    fill.setColor(accent());c.drawRoundRect(mark,2*k,2*k,fill);
                }
                surface.focus(c,navBox,ROW_RADIUS*k,f);
            }
            int ink=Look.mix(open?accent():surface.dim,surface.focusInk,f);
            // The symbol slides from the middle of the narrow column to the start of the wide one.
            float sx=(RAIL_X+RAIL_MIN/2)*k+(navBox.left+38*k-(RAIL_X+RAIL_MIN/2)*k)*e;
            Symbols.draw(getContext(),c,symbol(i),sx,navBox.centerY(),26*k,ink);
            if(e>.02f){
                navText.setColor(Look.mix(surface.ink,surface.focusInk,f));navText.setAlpha(Math.round(255*e));
                c.drawText(i<shownCategories.length?shownCategories[i]:data.categories[i],navBox.left+76*k,navBox.centerY()+Ui.halfCap(navText),navText);
                navText.setAlpha(255);
            }
        }
        float base=bottom()*k-40*k;
        if(e>.02f&&(NAV_TOP+data.categories.length*NAV_PITCH)*k<base-40*k){
            fill.setColor(accent());fill.setAlpha(Math.round(255*e));c.drawCircle((RAIL_X+40)*k,base-8*k,5*k,fill);fill.setAlpha(255);
            brand.setAlpha(Math.round(255*e));c.drawText("PURETV",(RAIL_X+56)*k,base,brand);brand.setAlpha(255);
        }
        c.restore();
    }

    private void drawList(Canvas c){
        float l=listLeft(),r=listRight();
        box.set(l*k,EDGE*k,r*k,bottom()*k);surface.panel(c,box,34*k);
        // Where this page sits, then its name beside its symbol.
        String crumb=data.crumb==null||data.crumb.isEmpty()?Ui.tr("Ajustes","Settings"):data.crumb;
        crumbPaint.setColor(surface.dim);c.drawText(crumb,(l+30)*k,116*k,crumbPaint);
        if(!position.isEmpty()){value.setColor(surface.dim);value.setTextSize(22*k);c.drawText(position,(r-30)*k,116*k,value);value.setTextSize(26*k);}
        float cx=(l+60)*k,cy=164*k;
        fill.setColor(Ui.alpha(Look.GOLD,surface.dark?.18f:.26f));mark.set(cx-30*k,cy-30*k,cx+30*k,cy+30*k);c.drawRoundRect(mark,18*k,18*k,fill);
        Symbols.draw(getContext(),c,rail()?symbol(data.category):data.symbol,cx,cy,27*k,accent());
        c.drawText(TextUtils.ellipsize(data.title,title,(listWidth()-130)*k,TextUtils.TruncateAt.END).toString(),(l+104)*k,cy+Ui.halfCap(title),title);
        if(!data.subtitle.isEmpty()){
            small.setColor(data.urgent?accent():surface.dim);small.setFakeBoldText(data.urgent);
            c.drawText(TextUtils.ellipsize(data.subtitle,small,(listWidth()-80)*k,TextUtils.TruncateAt.END).toString(),(l+30)*k,228*k,small);
            small.setColor(surface.dim);small.setFakeBoldText(false);
        }
        // The rows, in a window that scrolls.
        float top=viewTop(),end=viewBottom();
        c.save();c.clipRect((l+4)*k,(top-6)*k,(r-4)*k,end*k);
        for(int i=0;i<data.rows.length;i++){
            Row it=data.rows[i];float y=top+rowTop[i]-scrollMotion.value;
            if(y>end||y+ROW_H<top-SECTION_H)continue;
            if(!it.shownSection.isEmpty()){section.setColor(surface.dim);c.drawText(it.shownSection,(l+40)*k,(y-14)*k,section);}
            if(y+ROW_H<top||y>end)continue;
            drawRow(c,it,i);
        }
        c.restore();
        if(contentHeight>end-top+ROW_PITCH-ROW_H){
            float trackTop=top*k,trackBottom=end*k,x=(r-12)*k,span=trackBottom-trackTop,total=contentHeight-ROW_PITCH+ROW_H;
            fill.setColor(surface.line);c.drawRoundRect(x,trackTop,x+4*k,trackBottom,2*k,2*k,fill);
            float from=trackTop+span*scrollMotion.value/total,to=trackTop+span*Math.min(1,(scrollMotion.value+end-top)/total);
            fill.setColor(Ui.alpha(surface.dim,.75f));c.drawRoundRect(x,from,x+4*k,to,2*k,2*k,fill);
        }
    }
    private void drawRow(Canvas c,Row r,int i){
        rowBounds(i);float f=Ui.clamp(focusMotion[i].value)*(picking&&i==focus?.55f:1);
        surface.focus(c,row,ROW_RADIUS*k,f);
        boolean quiet=info(r)||r.muted;
        float x=row.left+22*k;
        if(icons){
            mark.set(x,row.centerY()-24*k,x+48*k,row.centerY()+24*k);
            fill.setColor(Look.mix(Ui.alpha(surface.ink,surface.dark?.08f:.06f),Ui.alpha(surface.focusInk,.08f),f));c.drawRoundRect(mark,14*k,14*k,fill);
            int ink=Look.mix(quiet?surface.dim:surface.ink,surface.focusInk,f);
            if(r.network)networkIcon(c,mark.centerX(),mark.centerY()+2*k,r);
            else if(r.symbol!=null)Symbols.draw(getContext(),c,r.symbol,mark.centerX(),mark.centerY(),22*k,ink);
            x+=66*k;
        }
        label.setColor(Look.mix(quiet?surface.dim:surface.ink,surface.focusInk,f));
        boolean twoLines=!info(r)&&r.toggle==null&&!r.chosen&&!r.shownValue.isEmpty();
        if(twoLines){
            c.drawText(r.shownLabel,x,row.centerY()-4*k,label);
            small.setColor(Look.mix(surface.dim,surface.focusDim,f));c.drawText(r.shownValue,x,row.centerY()+26*k,small);
        }else c.drawText(r.shownLabel,x,row.centerY()+Ui.halfCap(label),label);
        float right=row.right-22*k;
        if(r.toggle!=null)surface.toggle(c,right,row.centerY(),r.toggle,true,f);
        else if(r.chosen)Symbols.draw(getContext(),c,"check",right-16*k,row.centerY(),28*k,Look.mix(accent(),surface.focusInk,f));
        else if(info(r)){value.setColor(Look.mix(surface.ink,surface.focusDim,f));c.drawText(r.shownValue,right,row.centerY()+Ui.halfCap(value),value);}
        else Symbols.draw(getContext(),c,"chevron_right",right-12*k,row.centerY(),24*k,Look.mix(surface.dim,surface.focusDim,f));
    }

    private void drawDetail(Canvas c){
        float l=detailLeft();if(l>RIGHT-120)return;
        box.set(l*k,EDGE*k,RIGHT*k,bottom()*k);surface.panel(c,box,34*k);
        c.save();clip.reset();clip.addRoundRect(box,34*k,34*k,Path.Direction.CW);c.clipPath(clip);
        float e=rail()?Ui.clamp(navMotion.value):0;
        // An offscreen layer only while the row's detail and the category's summary cross-fade; settled, none.
        if(e<.98f&&data.rows.length>0){
            boolean fade=e>.02f;if(fade)c.saveLayerAlpha(box,Math.round(255*(1-e)));
            drawRowDetail(c,l+PAD);if(fade)c.restore();
        }
        if(e>.02f){
            boolean fade=e<.98f;if(fade)c.saveLayerAlpha(box,Math.round(255*e));
            drawCategoryDetail(c,l+PAD);if(fade)c.restore();
        }
        c.restore();
    }
    /** While a category is being chosen: its symbol, its name and what it holds. */
    private void drawCategoryDetail(Canvas c,float x){
        float cx=(x+56)*k,cy=(EDGE+PAD+56)*k;
        fill.setColor(Ui.alpha(Look.GOLD,surface.dark?.16f:.24f));c.drawCircle(cx,cy,56*k,fill);
        Symbols.draw(getContext(),c,symbol(data.category),cx,cy,48*k,accent());
        String name=data.categories!=null&&data.category<data.categories.length?data.categories[data.category]:data.title;
        detailTitle.setColor(surface.ink);
        c.drawText(TextUtils.ellipsize(name,detailTitle,(RIGHT-x-PAD)*k,TextUtils.TruncateAt.END).toString(),x*k,(EDGE+PAD+168)*k,detailTitle);
        if(navHint!=null){hintPaint.setColor(surface.dim);c.save();c.translate(x*k,(EDGE+PAD+196)*k);navHint.draw(c);c.restore();}
        keys(c,x,new String[]{"▲ ▼",Ui.tr("Browse"),"OK",Ui.tr("Open")});
    }
    private void drawRowDetail(Canvas c,float x){
        Row r=data.rows[focus];float w=detailWidth(),y=EDGE+PAD;
        if(r.choices!=null&&hasArt(r)){
            y=drawHeading(c,r,x,y);
            // A large picture of the focused (or chosen) answer, then every answer as a card.
            int cols=columns(r),count=r.choices.length,rows=(count+cols-1)/cols;
            float cw=(w-(cols-1)*18)/cols,ch=cw*9/16,cardsH=rows*ch+(rows-1)*18;
            float keysTop=bottom()-PAD-40,space=keysTop-26-(y+26)-cardsH-28;
            float ph=Math.min(w*9/16,space),pw=ph*16/9;
            int shown=picking?pickAt:r.current;
            if(ph>80){
                box.set(x*k,(y+26)*k,(x+pw)*k,(y+26+ph)*k);
                Preview art=r.choices[shown].art;if(art!=null)art.draw(c,box,surface.dark);
                y+=26+ph;
            }
            y+=28;
            for(int i=0;i<count;i++){
                float cx=x+(i%cols)*(cw+18),cy=y+(i/cols)*(ch+18);
                boolean focused=picking&&i==pickAt;float grow=focused?6*Ui.clamp(pickMotion.value):0;
                card.set((cx-grow)*k,(cy-grow*9/16)*k,(cx+cw+grow)*k,(cy+ch+grow*9/16)*k);
                drawCard(c,r.choices[i],card,i==r.current,focused);
            }
        }else if(r.choices!=null){
            y=drawHeading(c,r,x,y);y+=26;
            float h=r.choices.length>4?66:74,pitch=h+10;
            for(int i=0;i<r.choices.length;i++){
                Choice ch=r.choices[i];box.set(x*k,(y+i*pitch)*k,(x+w)*k,(y+i*pitch+h)*k);
                boolean focused=picking&&i==pickAt;float f=focused?Ui.clamp(pickMotion.value):0;
                if(!focused)surface.well(c,box,16*k,0);
                surface.focus(c,box,16*k,f);
                label.setColor(Look.mix(surface.ink,surface.focusInk,f));
                float ty=box.centerY()+(ch.shownSub.isEmpty()?Ui.halfCap(label):-2*k);
                c.drawText(ch.shownLabel,box.left+24*k,ty,label);
                if(!ch.shownSub.isEmpty()){small.setColor(Look.mix(surface.dim,surface.focusDim,f));c.drawText(ch.shownSub,box.left+24*k,box.centerY()+24*k,small);}
                if(i==r.current)Symbols.draw(getContext(),c,"check",box.right-38*k,box.centerY(),28*k,Look.mix(accent(),surface.focusInk,f));
            }
        }else if(r.preview!=null){
            float pw=w,ph=pw*9/16;
            box.set(x*k,y*k,(x+pw)*k,(y+ph)*k);r.preview.draw(c,box,surface.dark);
            drawHeading(c,r,x,y+ph+12);
        }else{
            // No picture: the row's symbol large, its name, its value, and what it does.
            float cx=(x+56)*k,cy=(y+56)*k;
            fill.setColor(Ui.alpha(Look.GOLD,surface.dark?.16f:.24f));c.drawCircle(cx,cy,56*k,fill);
            if(r.network)networkIcon(c,cx,cy+4*k,r);
            else Symbols.draw(getContext(),c,r.symbol!=null?r.symbol:rail()?symbol(data.category):data.symbol,cx,cy,46*k,accent());
            y+=132;
            if(!r.value.isEmpty()&&r.toggle==null&&!r.chosen){
                detailTitle.setColor(surface.ink);
                c.save();c.translate(x*k,y*k);titleLayout.draw(c);c.restore();y+=titleLayout.getHeight()/k+14;
                valueBig.setColor(accent());c.drawText(r.shownDetailValue,x*k,(y+26)*k,valueBig);
                y+=44;
                if(detailHint!=null){hintPaint.setColor(surface.dim);c.save();c.translate(x*k,(y+8)*k);detailHint.draw(c);c.restore();}
                drawNote(c,r,x,y+8+(detailHint==null?0:detailHint.getHeight()/k)+22);
            }else drawHeading(c,r,x,y-26);
        }
        keys(c,x,rowKeys(r));
    }
    /** The row's name, what it does, and its note; returns where the next part starts. */
    private float drawHeading(Canvas c,Row r,float x,float y){
        detailTitle.setColor(surface.ink);
        c.save();c.translate(x*k,(y+26)*k);titleLayout.draw(c);c.restore();
        y+=26+titleLayout.getHeight()/k+12;
        if(detailHint!=null&&!hintText.isEmpty()){
            hintPaint.setColor(surface.dim);c.save();c.translate(x*k,y*k);detailHint.draw(c);c.restore();
            y+=detailHint.getHeight()/k;
        }
        return drawNote(c,r,x,y+16);
    }
    private float drawNote(Canvas c,Row r,float x,float y){
        if(r.note==null||r.note.isEmpty())return y-16;
        String text=TextUtils.ellipsize(r.note,chip,(detailWidth()-40)*k,TextUtils.TruncateAt.END).toString();
        float tw=chip.measureText(text);
        box.set(x*k,y*k,x*k+tw+32*k,(y+36)*k);
        fill.setColor(Ui.alpha(Look.GOLD,surface.dark?.16f:.22f));c.drawRoundRect(box,18*k,18*k,fill);
        chip.setColor(accent());c.drawText(text,box.left+16*k,box.centerY()+Ui.halfCap(chip),chip);
        return y+36;
    }
    private void drawCard(Canvas c,Choice ch,RectF b,boolean inUse,boolean focused){
        c.save();clip.reset();clip.addRoundRect(b,16*k,16*k,Path.Direction.CW);c.clipPath(clip);
        if(ch.art!=null)ch.art.draw(c,b,surface.dark);else{fill.setColor(surface.well);c.drawRect(b,fill);}
        // A shade under the name so it reads on any picture.
        shadeMatrix.setScale(1,60*k);shadeMatrix.postTranslate(0,b.bottom-60*k);shadeUnit.setLocalMatrix(shadeMatrix);shade.setShader(shadeUnit);
        c.drawRect(b.left,b.bottom-60*k,b.right,b.bottom,shade);
        choiceText.setTextSize(choiceSize*ch.textScale);c.drawText(ch.shownLabel,b.left+14*k,b.bottom-14*k,choiceText);choiceText.setTextSize(choiceSize);
        c.restore();
        if(inUse){
            float cx=b.right-24*k,cy=b.top+24*k;fill.setColor(Look.GOLD);c.drawCircle(cx,cy,17*k,fill);
            Symbols.draw(getContext(),c,"check",cx,cy,20*k,0xFF1B1710);
        }
        if(focused){
            icon.setColor(0xFFF5F5F7);icon.setStrokeWidth(4*k);c.drawRoundRect(b,16*k,16*k,icon);icon.setStrokeWidth(2.8f*k);
        }
    }
    private String[] rowKeys(Row r){
        if(picking)return new String[]{hasArt(r)?"◀ ▶":"▲ ▼",Ui.tr("Browse"),"OK",Ui.tr("Apply"),Ui.tr("Back"),Ui.tr("Cancel")};
        String back=rail()?Ui.tr("Categories"):footer;
        String backKey=rail()?"◀":Ui.tr("Back");
        if(info(r))return new String[]{backKey,back};
        String ok=r.choices!=null?Ui.tr("Choose"):r.toggle!=null?Ui.tr("Change"):Ui.tr("Open");
        return new String[]{"OK",ok,backKey,back};
    }
    /** The keys that work now, as key caps with their meaning, along the pane's foot. */
    private void keys(Canvas c,float x,String[] pairs){
        float y=bottom()-PAD-34,at=x*k;
        for(int i=0;i+1<pairs.length;i+=2){
            if(pairs[i+1]==null||pairs[i+1].isEmpty())continue;
            float cw=Math.max(40*k,keyCap.measureText(pairs[i])+20*k);
            box.set(at,y*k,at+cw,(y+34)*k);fill.setColor(Ui.alpha(surface.ink,surface.dark?.12f:.08f));c.drawRoundRect(box,9*k,9*k,fill);
            keyCap.setColor(surface.ink);c.drawText(pairs[i],box.centerX(),box.centerY()+Ui.halfCap(keyCap),keyCap);
            keyText.setColor(data.urgent&&!rail()&&i==pairs.length-2?accent():surface.dim);
            c.drawText(pairs[i+1],box.right+10*k,box.centerY()+Ui.halfCap(keyText),keyText);
            at=box.right+10*k+keyText.measureText(pairs[i+1])+30*k;
        }
    }
    private void networkIcon(Canvas c,float x,float y,Row r){
        icon.setColor(r.color);
        if("Ethernet".equals(r.transport)){
            arc.set(x-14*k,y-13*k,x+14*k,y+7*k);c.drawRoundRect(arc,3*k,3*k,icon);
            c.drawLine(x,y+7*k,x,y+14*k,icon);c.drawLine(x-9*k,y+14*k,x+9*k,y+14*k,icon);
        }else{
            for(int i=0;i<3;i++){float radius=(7+i*6.5f)*k;arc.set(x-radius,y+10*k-radius,x+radius,y+10*k+radius);c.drawArc(arc,225,90,false,icon);}
            fill.setColor(r.color);c.drawCircle(x,y+9*k,2.2f*k,fill);
        }
        if(r.state==NetworkStatus.OFFLINE||r.state==NetworkStatus.UNKNOWN)c.drawLine(x-16*k,y-16*k,x+16*k,y+16*k,icon);
    }

    // MARK: - Keys

    private void activate(int direction){
        if(data.rows.length==0)return;Row r=data.rows[focus];if(r.action==null)return;
        if(r.choices!=null&&r.pick!=null){picking=true;pickAt=r.current;aim(false);invalidate();wake();spoken.focusMoved();return;}
        r.action.run(direction);changed();spoken.focusMoved();
    }
    private void chooseCategory(int index){
        if(data.categories==null||index<0||index>=data.categories.length)return;
        if(data.selectCategory!=null)data.selectCategory.accept(index);
    }
    /** The arrows inside the choices: across the cards (and down a row of them), or down the list. */
    private void movePick(int code){
        Row r=data.rows[focus];int n=r.choices.length,cols=columns(r),at=pickAt;
        if(cols==1){if(code==KeyEvent.KEYCODE_DPAD_UP)at--;else if(code==KeyEvent.KEYCODE_DPAD_DOWN)at++;}
        else if(code==KeyEvent.KEYCODE_DPAD_LEFT)at--;else if(code==KeyEvent.KEYCODE_DPAD_RIGHT)at++;
        else if(code==KeyEvent.KEYCODE_DPAD_UP)at-=cols;else if(code==KeyEvent.KEYCODE_DPAD_DOWN)at+=cols;
        at=Math.max(0,Math.min(n-1,at));
        if(at!=pickAt){pickAt=at;pickMotion.snap(0);pickMotion.target=1;invalidate();wake();spoken.focusMoved();}
    }
    boolean onKey(KeyEvent e){
        boolean down=e.getAction()==KeyEvent.ACTION_DOWN;int code=e.getKeyCode();
        if(down&&(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN
                ||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_BACK))armed=false;
        if(picking){
            Row r=data.rows[focus];
            switch(code){
                case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if(down)movePick(code);return true;
                case KeyEvent.KEYCODE_BACK:
                    if(!down){picking=false;aim(false);invalidate();wake();spoken.focusMoved();}return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if(down){if(e.getRepeatCount()==0)armed=true;}
                    else{
                        if(armed&&!e.isCanceled()&&spoken.keyClick()){picking=false;if(r.pick!=null)r.pick.accept(pickAt);changed();spoken.focusMoved();}
                        armed=false;
                    }
                    return true;
                default:return false;
            }
        }
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
                // Right opens a row's choices, as OK does.
                if(code==KeyEvent.KEYCODE_DPAD_RIGHT&&down&&data.rows.length>0&&data.rows[focus].choices!=null)activate(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if(down){if(e.getRepeatCount()==0)armed=true;}
                else{if(armed&&!e.isCanceled()&&spoken.keyClick())activate(1);armed=false;}return true;
            default:return false;
        }
    }
}
