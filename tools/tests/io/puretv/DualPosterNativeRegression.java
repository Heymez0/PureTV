package io.puretv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Isolated Android VM: native pixels/shaders and the production source lifecycle, without providers/network. */
public final class DualPosterNativeRegression {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static Object get(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static ContinueWatching.Program p(int i){return new ContinueWatching.Program(i,1,0,1,0,"test.video","Test","Test","","test://poster/"+i,"test://play/"+i);}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Constructor<ContinueWatching> ctor=ContinueWatching.class.getDeclaredConstructor(android.content.Context.class);ctor.setAccessible(true);
        final ContinueWatching source=ctor.newInstance((Object)null);
        final PosterCache<Backdrop> cache=(PosterCache<Backdrop>)get(source,"images");
        final PosterPlan<ContinueWatching.Program> plan=(PosterPlan<ContinueWatching.Program>)get(source,"wantedArt");
        final ExecutorService io=(ExecutorService)get(source,"artIo");final CountDownLatch block=new CountDownLatch(1);
        io.execute(()->{try{block.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        final List<ContinueWatching.Program> recent=Arrays.asList(p(0),p(1),p(2)),recommended=Arrays.asList(p(3),p(4),p(5));
        source.setWindows(recent,recommended);source.resumeArt();
        for(ContinueWatching.Program program:recent)ok(source.backdrop(program)==null);
        Object first=plan.token(recent.get(0).art);int gen=(Integer)get(source,"artGeneration");
        source.setWindows(recommended,recent);ok(plan.matches(recent.get(0).art,first)&&(Integer)get(source,"artGeneration")==gen);
        ok(((Set<?>)get(source,"pending")).size()==3&&(Long)get(source,"artLoads")==3L);
        source.suspendArt();block.countDown();
        new Handler().postDelayed(()->{
            try{
                ok(((Set<?>)get(source,"pending")).isEmpty());ok((Long)get(source,"artPublished")==0L&&(Long)get(source,"artDiscarded")==3L);
                source.setWindows(recent,recommended);
                List<ContinueWatching.Program> all=new ArrayList<>(recent);all.addAll(recommended);
                List<Backdrop> images=new ArrayList<>();List<BitmapShader> shaders=new ArrayList<>();long bytes=0;
                for(int i=0;i<6;i++){
                    Bitmap raw=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);raw.eraseColor(0xff123456+i*0x10101);
                    Backdrop image=Backdrop.make(raw,true);images.add(image);bytes+=image.retainedBytes();
                    ok(image.wide!=null&&image.tall!=null&&image.wideShader!=null&&image.tallShader!=null);
                    shaders.add(image.wideShader);shaders.add(image.tallShader);cache.put(all.get(i).art,image,image.retainedBytes());
                }
                ok(cache.size()==6&&bytes==cache.bytes()&&bytes<=ContinueWatching.DUAL_RETAIN_BYTES);
                System.gc(); // This separate test VM verifies strong shader ownership; no GC requested in the launcher.
                for(int trip=0;trip<50;trip++){
                    source.resumeArt();source.setWindows(recommended,recent);source.setWindows(recent,recommended);
                    source.suspendArt();ok(cache.size()==6&&cache.bytes()==bytes);
                    for(int i=0;i<6;i++){
                        ok(source.backdrop(all.get(i))==images.get(i));ok(images.get(i).wideShader==shaders.get(2*i)&&images.get(i).tallShader==shaders.get(2*i+1));
                        ok(!images.get(i).wide.isRecycled()&&!images.get(i).tall.isRecycled());
                    }
                }
                ok((Long)get(source,"artLoads")==3L);
                source.setWindows(recent.subList(0,1),recommended);ok(cache.size()==4&&plan.size()==4);
                source.setVisible(recent.subList(0,1));ok(cache.size()==1&&plan.size()==1);
                source.trim();ok(cache.size()==0&&source.backdrop(recent.get(0))==null&&(Long)get(source,"artLoads")==3L);
                source.setVisible(Collections.emptyList());ok(plan.size()==0);
                System.out.println("PASS: six real wide+tall posters; retainedBytes="+bytes+"; twelve strong shaders survive GC; 50 dual-tab/suspend cycles reuse every image; stable in-flight identities; stale hidden work rejected; actual 1+3 and recent-only counts; pressure/clock clear.");
                System.exit(0);
            }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        },1000);
        Looper.loop();
    }
}
