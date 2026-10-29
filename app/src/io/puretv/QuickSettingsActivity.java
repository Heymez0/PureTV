package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/** A short-lived translucent activity, without overlay permission or a background service. */
public class QuickSettingsActivity extends Activity {
    private QuickSettingsView page;
    private AirPlayControl airplay;
    private boolean resumed,freeing;
    private int generation;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        airplay=new AirPlayControl(this,()->{if(page!=null)page.airplay(airplay.value(),airplay.running(),airplay.busy());});
        page=new QuickSettingsView(this,index->{
            switch(index){
                case 0:airplay.toggle();break;
                case 1:open(HomeEditActivity.class);break;
                case 2:open(DeviceStatusActivity.class);break;
                case 3:freeMemory();break;
                case 4:nextSleep();break;
                case 5:open(SettingsActivity.class);break;
            }
        },this::finish);
        setContentView(page);
    }
    private void freeMemory(){
        if(freeing)return;freeing=true;page.freeing(true);page.ramNote(Ui.tr("Freeing memory…"));
        final int token=generation;final android.content.Context context=getApplicationContext();
        new Thread(()->{
            String result=FreeMemory.result(FreeMemory.run(context));ResourceStatus.Snapshot snapshot=ResourceStatus.read(context);
            runOnUiThread(()->{freeing=false;page.freeing(false);if(!resumed||token!=generation)return;page.resources(snapshot);page.ramNote(result);page.announceForAccessibility(result);});
        },"quick-free-memory").start();
    }
    /** OK on Sleep timer: the next length (Off after the longest); the file and alarm work stay off the main thread. */
    private void nextSleep(){
        final android.content.Context context=getApplicationContext();final int token=generation;
        sleepIo.execute(()->{   // one at a time: quick presses step through the lengths in order
            if(SleepTimer.available(context))SleepTimer.set(context,SleepTimer.next(context));
            final String shown=sleepValue(context);
            runOnUiThread(()->{if(resumed&&token==generation){page.sleep(shown);page.announceForAccessibility(shown);}});
        });
    }
    private final java.util.concurrent.ExecutorService sleepIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override protected void onDestroy(){sleepIo.shutdown();super.onDestroy();}
    private static String sleepValue(android.content.Context c){
        if(!SleepTimer.available(c))return Ui.tr("Needs PureTV as home screen");
        int left=SleepTimer.minutesLeft(c);
        return left==0?Ui.tr("Off"):Ui.trf("{n} min left","n",left);
    }
    private void open(Class<? extends Activity> target){startActivity(new Intent(this,target));finish();}
    @Override protected void onResume(){
        super.onResume();resumed=true;airplay.resume();page.readAgain();
        final int token=++generation;
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            ResourceStatus.Snapshot snapshot=ResourceStatus.read(context);String sleep=sleepValue(context);
            runOnUiThread(()->{if(resumed&&token==generation){page.resources(snapshot);page.sleep(sleep);}});
        },"quick-status").start();
    }
    @Override protected void onPause(){resumed=false;generation++;airplay.pause();super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return page.onKey(e)||super.dispatchKeyEvent(e);}
}
