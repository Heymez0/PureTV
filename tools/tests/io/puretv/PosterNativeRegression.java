package io.puretv;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** Isolated app_process test; no provider, preferences, network or product test controls. */
public final class PosterNativeRegression {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static Object get(Object target,String name)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    static ContinueWatching.Program program(int i){
        return new ContinueWatching.Program(i,1,0,1,0,"test.video","Test","Test","","test://poster/"+i,"test://play/"+i);
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Constructor<ContinueWatching> ctor=ContinueWatching.class.getDeclaredConstructor(android.content.Context.class);ctor.setAccessible(true);
        final ContinueWatching source=ctor.newInstance((Object)null);
        final PosterCache<Backdrop> cache=(PosterCache<Backdrop>)get(source,"images");
        final ExecutorService artIo=(ExecutorService)get(source,"artIo");
        final CountDownLatch block=new CountDownLatch(1);
        artIo.execute(()->{try{block.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        List<ContinueWatching.Program> programs=Arrays.asList(program(0),program(1),program(2));
        source.setVisible(programs);source.resumeArt();
        for(ContinueWatching.Program p:programs)ok(source.backdrop(p)==null);
        ok(((Set<?>)get(source,"pending")).size()==3);
        source.suspendArt();int generation=(Integer)get(source,"artGeneration");
        for(int i=3;i<9;i++)ok(source.backdrop(program(i))==null);
        ok((Long)get(source,"artLoads")==3L);
        ok((Integer)get(source,"artGeneration")==generation);
        block.countDown();
        new Handler().postDelayed(()->{
            try{
                ok(((Set<?>)get(source,"pending")).isEmpty());
                ok((Long)get(source,"artPublished")==0L&&(Long)get(source,"artDiscarded")==3L);
                Bitmap raw=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);raw.eraseColor(0xff325678);
                Backdrop poster=Backdrop.make(raw,true);
                ok(poster.wide!=null&&poster.tall!=null&&poster.wide.getWidth()==Backdrop.WIDE_W&&poster.tall.getWidth()==Backdrop.TALL_W);
                long bytes=poster.retainedBytes();ok(bytes>0&&bytes*3<=ContinueWatching.RETAIN_BYTES);
                for(ContinueWatching.Program p:programs)cache.put(p.art,poster,bytes);
                for(int trip=0;trip<50;trip++){
                    source.suspendArt();ok(cache.size()==3&&cache.bytes()==bytes*3);
                    for(ContinueWatching.Program p:programs)ok(source.backdrop(p)==poster);
                    source.resumeArt();for(ContinueWatching.Program p:programs)ok(source.backdrop(p)==poster);
                }
                ok((Long)get(source,"artLoads")==3L);
                source.setVisible(Collections.singletonList(programs.get(0)));ok(cache.size()==1);
                source.suspendArt();source.trim();ok(cache.isEmpty());ok(source.backdrop(programs.get(0))==null);
                ok((Long)get(source,"artLoads")==3L);
                System.out.println("PASS: native wide+tall generation; bytesPerPoster="+bytes+"; three fit retained budget; 50 cache reuse cycles; hidden loads blocked; 3 stale jobs rejected; pressure clear.");
                System.exit(0);
            }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        },1000);
        Looper.loop();
    }
}
