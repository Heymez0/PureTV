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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import java.util.List;
import java.util.function.IntConsumer;

/** Static native welcome surface: no images, backdrop decoding, animation loop or network work. */
final class WelcomeView extends View {
    static final int NEXT=1,LATER=2,DEVELOPERS=3,ENABLE=4,HOME=5,SETTINGS=6,BACK=7,CHECK=8,LEAVE=9,NETWORK=10;
    static final int BOX=11,TV=12,AIRPLAY=13;
    static final class Button {
        final int action;final String text;final boolean enabled;
        Button(int action,String text,boolean enabled){this.action=action;this.text=text;this.enabled=enabled;}
    }
    static final class Content {
        final int step;final String title,body,status;final Button[] buttons;
        Content(int step,String title,String body,String status,Button... buttons){this.step=step;this.title=title;this.body=body;this.status=status;this.buttons=buttons;}
        boolean same(Content other){
            if(other==null||step!=other.step||!title.equals(other.title)||!body.equals(other.body)||!status.equals(other.status)||buttons.length!=other.buttons.length)return false;
            for(int i=0;i<buttons.length;i++)if(buttons[i].action!=other.buttons[i].action||buttons[i].enabled!=other.buttons[i].enabled||!buttons[i].text.equals(other.buttons[i].text))return false;
            return true;
        }
    }
    private static String tr(String text){return Ui.tr(text);}
    private static Button button(int action,String text,boolean enabled){return new Button(action,tr(text),enabled);}
    static Content content(WelcomeFlow flow,boolean loaded,boolean debug,String busy,String error){return content(flow,loaded,debug,busy,error,false);}
    /** `airplay`: the optional receiver isn't installed, so the last page offers it (never required). */
    static Content content(WelcomeFlow flow,boolean loaded,boolean debug,String busy,String error,boolean airplay){
        switch(flow.step){
            case WelcomeFlow.DEVICE:return new Content(flow.step,tr("Where will you use PureTV?"),
                tr("Choose TV box / stick for a device connected to your TV. Choose Television when PureTV runs on the TV itself, with its own tuner and HDMI inputs. Your theme and app arrangement stay the same."),
                tr("You can change this later in Settings → Home screen → Device mode."),
                button(BOX,"TV box / stick",true),button(TV,"Television",true),button(LATER,"Set up later",true));
            case WelcomeFlow.CONNECT:return new Content(flow.step,tr("Get your TV ready"),
                tr("Open your TV's developer options and enable USB debugging. If developer options are hidden, open About and press Build seven times. Then return here with Back."),
                error.isEmpty()?tr(debug?"USB debugging is enabled.":"You can enable USB debugging in TV settings."):error,
                button(debug?NEXT:DEVELOPERS,debug?"Continue":"Open TV settings",true),
                button(debug?DEVELOPERS:NEXT,debug?"Open TV settings":"Continue",true),button(LATER,"Set up later",true));
            case WelcomeFlow.REVIEW:return new Content(flow.step,tr(flow.failed?"Let's try again":"Make PureTV your home"),
                flow.failed?(error.isEmpty()?tr("Setup could not finish. Check the connection and try again."):error)
                    :tr("PureTV will become your home screen and screensaver. Supported promotional components are disabled, not uninstalled. You can restore the original interface in Settings → System and about → Default home screen."),
                tr("Your home screen changes only after you choose Enable PureTV."),
                button(ENABLE,flow.failed?"Try again":"Enable PureTV",loaded),
                button(flow.failed&&networkError(error)?NETWORK:BACK,flow.failed&&networkError(error)?"Open network settings":"Connection settings",true),button(LATER,"Set up later",true));
            case WelcomeFlow.WORKING:return new Content(flow.step,tr("Setting up PureTV"),
                tr("If the TV asks Allow USB debugging, select Always allow from this computer and confirm. Setup continues on the TV; the new home screen opens when it is ready."),
                busy.isEmpty()?tr("Preparing setup…"):busy,button(CHECK,"Check progress",true),button(LEAVE,"Continue in the background",true));
            case WelcomeFlow.READY:return new Content(flow.step,tr("PureTV is ready"),
                tr("Press Home to return to your new interface. You can arrange your apps, choose viewing content and change the weather in Settings."),
                tr("You can return to the original interface whenever you want."),airplay
                    ?new Button[]{button(HOME,"Go to home screen",true),button(SETTINGS,"Personalize in settings",true),button(AIRPLAY,"Add AirPlay (optional)",true)}
                    :new Button[]{button(HOME,"Go to home screen",true),button(SETTINGS,"Personalize in settings",true)});
            default:return new Content(WelcomeFlow.INTRO,tr("Welcome to PureTV"),
                tr("Your apps, a calmer home screen and controls made for your remote. A few short steps will help you switch to your new interface."),
                loaded?tr("Use the arrow buttons to move and OK to choose."):tr("Checking…"),button(NEXT,"Let's begin",true),button(LATER,"Set up later",true));
        }
    }
    private static boolean networkError(String error){String text=error.toLowerCase(java.util.Locale.ROOT);return text.contains("wi-fi")||text.contains("wifi");}
    private final SettingsSurface surface=new SettingsSurface();
    private final IntConsumer action;private final Runnable back;private final Spoken spoken;
    private final RectF panel=new RectF(),box=new RectF();private final RectF[] rows={new RectF(),new RectF(),new RectF()};
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint brand,hero,caption,title,body,status,label,small;
    private StaticLayout heroLines,bodyLines,statusLines;
    private Content data;private int focus,width,height;private float k=1;private boolean armed,running;
    WelcomeView(Context c,IntConsumer action,Runnable back){
        super(c);this.action=action;this.back=back;setFocusable(true);
        spoken=new Spoken(this,new Spoken.Source(){
            @Override public void items(List<Integer> out){if(data!=null)for(int i=0;i<data.buttons.length;i++)out.add(i+1);}
            @Override public String text(int id){return valid(id)?data.buttons[id-1].text:"";}
            @Override public boolean bounds(int id,Rect out){if(!valid(id))return false;rows[id-1].round(out);return true;}
            @Override public int focused(){return focus+1;}
            @Override public void focus(int id){if(valid(id)){focus=id-1;invalidate();}}
            @Override public void click(int id){if(valid(id)&&data.buttons[id-1].enabled){armed=false;focus=id-1;activate();}}
            @Override public boolean clickable(int id){return valid(id)&&data.buttons[id-1].enabled;}
        });
    }
    private boolean valid(int id){return data!=null&&id>0&&id<=data.buttons.length;}
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return spoken;}
    void bind(Content next){
        if(next.same(data))return;boolean changedStep=data==null||data.step!=next.step;
        if(changedStep||focus>=next.buttons.length||data.buttons[focus].action!=next.buttons[focus].action)armed=false;
        data=next;
        if(changedStep)focus=0;focus=Math.min(focus,data.buttons.length-1);
        if(!data.buttons[focus].enabled)for(int i=0;i<data.buttons.length;i++)if(data.buttons[i].enabled){focus=i;break;}
        setContentDescription(data.title+". "+data.body+" "+data.status);rebuild();invalidate();if(running)spoken.readAgain();
    }
    void resume(){running=true;armed=false;rebuild();invalidate();spoken.readAgain();}
    void pause(){running=false;armed=false;}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        width=w;height=h;k=w/1920f;surface.size(getContext(),w,h);
        brand=new TextPaint(Ui.text(Ui.displaySemibold,74*k,surface.ink));hero=new TextPaint(Ui.text(Ui.displaySemibold,50*k,surface.ink));
        caption=new TextPaint(Ui.text(Ui.regular,28*k,surface.dim));title=new TextPaint(Ui.text(Ui.displaySemibold,40*k,surface.ink));
        body=new TextPaint(Ui.text(Ui.regular,28*k,surface.dim));status=new TextPaint(Ui.text(Ui.medium,24*k,surface.ink));
        label=new TextPaint(Ui.text(Ui.medium,28*k,surface.ink));small=new TextPaint(Ui.text(Ui.regular,22*k,surface.dim));
        panel.set(880*k,112*k,1810*k,968*k);for(int i=0;i<3;i++)rows[i].set(936*k,(665+i*88)*k,1754*k,(741+i*88)*k);
        line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(2*k);rebuild();
    }
    private StaticLayout lines(String text,TextPaint paint,int width,int max){
        return StaticLayout.Builder.obtain(text,0,text.length(),paint,Math.max(1,width)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(5*k,1).setMaxLines(max).setEllipsize(TextUtils.TruncateAt.END).build();
    }
    private void rebuild(){
        if(body==null||data==null)return;surface.refresh(getContext());
        brand.setColor(surface.ink);hero.setColor(surface.ink);caption.setColor(surface.dim);title.setColor(surface.ink);
        body.setColor(surface.dim);status.setColor(surface.ink);small.setColor(surface.dim);
        heroLines=lines(tr("A calmer home.\nMade for your TV."),hero,(int)(650*k),3);
        bodyLines=lines(data.body,body,(int)(818*k),7);statusLines=lines(data.status,status,(int)(818*k),3);
    }
    private void drawLines(Canvas c,StaticLayout text,float x,float y){if(text!=null){c.save();c.translate(x,y);text.draw(c);c.restore();}}
    @Override protected void onDraw(Canvas c){
        surface.draw(c,width,height);if(data==null||body==null)return;surface.panel(c,panel,34*k);
        fill.setColor(0xFFD2AB55);box.set(132*k,144*k,204*k,216*k);c.drawRoundRect(box,20*k,20*k,fill);
        Symbols.draw(getContext(),c,"tv",168*k,180*k,40*k,0xFF222225);c.drawText("PureTV",230*k,205*k,brand);
        drawLines(c,heroLines,132*k,306*k);
        c.drawText(tr("Simple. Familiar. Yours."),132*k,550*k,caption);
        String[] steps={tr("Welcome"),tr("Device mode"),tr("Connection"),tr("Start")};
        int current=data.step==WelcomeFlow.INTRO?0:data.step==WelcomeFlow.DEVICE?1:data.step==WelcomeFlow.CONNECT?2:3;
        for(int i=0;i<4;i++){
            float y=(700+i*66)*k;fill.setColor(i<=current?0xFFD2AB55:surface.dark?0xFF444247:0xFFD3CFC8);c.drawCircle(148*k,y,15*k,fill);
            label.setColor(i<=current?surface.ink:surface.dim);c.drawText(steps[i],184*k,y+Ui.halfCap(label),label);
        }
        c.drawText(tr("Quick setup"),936*k,180*k,small);c.drawText((current+1)+" / 4",1666*k,180*k,small);
        c.drawText(TextUtils.ellipsize(data.title,title,818*k,TextUtils.TruncateAt.END).toString(),936*k,252*k,title);
        drawLines(c,bodyLines,936*k,292*k);drawLines(c,statusLines,936*k,550*k);
        for(int i=0;i<data.buttons.length;i++){
            Button b=data.buttons[i];RectF r=rows[i];boolean on=i==focus&&b.enabled;
            fill.setColor(on?surface.focusFill:surface.dark?0xFF2B2A2B:0xFFE5E0D9);c.drawRoundRect(r,18*k,18*k,fill);
            label.setColor(on?surface.focusInk:b.enabled?surface.ink:surface.dim);
            String text=TextUtils.ellipsize(b.text,label,r.width()-56*k,TextUtils.TruncateAt.END).toString();
            c.drawText(text,r.centerX()-label.measureText(text)/2,r.centerY()+Ui.halfCap(label),label);
        }
        String hint=tr("OK selects · Back returns");c.drawText(hint,(width-small.measureText(hint))/2,1020*k,small);
    }
    private void activate(){if(data!=null&&data.buttons[focus].enabled)action.accept(data.buttons[focus].action);}
    boolean onKey(KeyEvent e){
        int key=e.getKeyCode();boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(down&&key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER)armed=false;
        switch(key){
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:
                if(down&&data!=null){int dir=key==KeyEvent.KEYCODE_DPAD_UP?-1:1,next=focus;
                    do{next+=dir;}while(next>=0&&next<data.buttons.length&&!data.buttons[next].enabled);
                    if(next>=0&&next<data.buttons.length)focus=next;invalidate();spoken.focusMoved();}return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:return true;
            case KeyEvent.KEYCODE_BACK:if(!down){armed=false;back.run();}return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if(down){if(e.getRepeatCount()==0)armed=true;}
                else{if(armed&&!e.isCanceled()&&spoken.keyClick())activate();armed=false;}return true;
            default:return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(data==null)return false;if(e.getAction()==MotionEvent.ACTION_UP)for(int i=0;i<data.buttons.length;i++)if(rows[i].contains(e.getX(),e.getY())&&data.buttons[i].enabled){armed=false;focus=i;invalidate();activate();return true;}
        return e.getAction()==MotionEvent.ACTION_DOWN;
    }
}
