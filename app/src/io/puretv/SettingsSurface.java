package io.puretv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Static, inexpensive smoky surfaces shared by every settings page and the quick panel. */
final class SettingsSurface {
    private final Paint background=new Paint(),fill=new Paint(Paint.ANTI_ALIAS_FLAG),rim=new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean dark=true;
    int ink=0xFFF5F5F7,dim=0xFFACACB0,focusFill,focusInk,focusDim;
    private int width,height;private boolean ready;
    void size(Context c,int width,int height) {
        this.width=width;this.height=height;ready=false;refresh(c);
        rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(Math.max(1,width/1920f));
    }
    boolean refresh(Context c) {
        boolean next=HomeData.get(c).isNight(System.currentTimeMillis());
        if(ready&&dark==next)return false;
        dark=next;ready=true;
        ink=dark?0xFFF5F5F7:0xFF1D1D1F;dim=dark?0xFFA9A9AE:0xFF58585F;
        focusFill=dark?0xFFF5F5F7:0xFF28282D;
        focusInk=dark?0xFF1C1C1E:0xFFF5F5F7;
        focusDim=dark?0xFF505056:0xFFD4D4DC;
        background.setShader(new LinearGradient(0,0,width,height,
                dark?0xFF302A23:0xFFEAE5DD,dark?0xFF111113:0xFFF3F3F5,Shader.TileMode.CLAMP));
        return true;
    }
    void draw(Canvas c,int width,int height) { c.drawRect(0,0,width,height,background); }
    void panel(Canvas c,RectF r,float radius) {
        fill.setColor(dark?0xB9111113:0x9FFFFFFF);c.drawRoundRect(r,radius,radius,fill);
        rim.setColor(dark?0x33FFFFFF:0x26000000);c.drawRoundRect(r,radius,radius,rim);
    }
    void popup(Canvas c,RectF r,float radius) {
        fill.setColor(dark?0xF2141417:0xF2F5F3EF);c.drawRoundRect(r,radius,radius,fill);
        rim.setColor(dark?0x40FFFFFF:0x30000000);c.drawRoundRect(r,radius,radius,rim);
    }
}
