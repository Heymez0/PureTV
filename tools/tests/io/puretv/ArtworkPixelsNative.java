package io.puretv;
import android.graphics.*;
import java.util.*;
import java.util.zip.CRC32;

/** Separate Android VM: deterministic visual fingerprints, no network, app state or mode changes. */
public final class ArtworkPixelsNative {
    static String hash(Bitmap b){
        Bitmap original=b;if(b.getConfig()==Bitmap.Config.HARDWARE)b=b.copy(Bitmap.Config.ARGB_8888,false);
        CRC32 crc=new CRC32();int[] row=new int[b.getWidth()];byte[] bytes=new byte[row.length*4];
        for(int y=0;y<b.getHeight();y++){b.getPixels(row,0,row.length,0,y,row.length,1);int at=0;for(int pixel:row)for(int shift=0;shift<32;shift+=8)bytes[at++]=(byte)(pixel>>>shift);crc.update(bytes);}
        if(b!=original)b.recycle();return Long.toHexString(crc.getValue());
    }
    public static void main(String[] args)throws Exception{
        for(int n=0;n<3;n++){
            System.out.println("CASE "+n);
            int w=n==2?360:640,h=n==2?640:360;Bitmap src=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);int[] pixels=new int[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=((n==1?(x+y)%256:255)<<24)|((x*7+y*3)%256<<16)|((x+y*5)%256<<8)|(x*11+y)%256;
            src.setPixels(pixels,0,w,0,0,w,h);Backdrop b=Backdrop.make(src,true);
            System.out.println("PIXELS "+n+" wide="+hash(b.wide)+" tall="+hash(b.tall)+" light="+hash(b.lightImage)+" night="+hash(b.nightImage)+" bytes="+b.retainedBytes());
            b.wide.recycle();b.tall.recycle();b.lightImage.recycle();b.nightImage.recycle();System.gc();
        }
        Scene scene=new Scene();scene.setSize(1920,1080);int[] frost=new int[Backdrop.FW*Backdrop.FH],glass=new int[Backdrop.GW*Backdrop.GH];Arrays.fill(frost,0xff336699);Arrays.fill(glass,0xff997733);
        scene.setFrost(frost);scene.setGlassImages(glass,glass);Bitmap out=Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);RectF rect=new RectF(12,12,228,148);
        scene.glass(c,rect,24,0xff111115,.4f,0,0,0,1);scene.liquid(c,rect,24,0xffbbbbcc,.6f,0,2,1,.1f,.7f);
        long owned=0;for(String name:new String[]{"frostImage","glassA","glassB"}){java.lang.reflect.Field f=Scene.class.getDeclaredField(name);f.setAccessible(true);Bitmap b=(Bitmap)f.get(scene);if(b!=null)owned+=b.getAllocationByteCount();}
        System.out.println("SCENE pixels="+hash(out)+" owned="+owned);System.exit(0);
    }
}
