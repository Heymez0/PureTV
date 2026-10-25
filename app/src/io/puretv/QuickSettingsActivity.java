package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/** A short-lived translucent activity, without overlay permission or a background service. */
public class QuickSettingsActivity extends Activity {
    private QuickSettingsView page;
    private AirPlayControl airplay;
    private boolean resumed;
    private int generation;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        airplay=new AirPlayControl(this,()->{if(page!=null)page.airplay(airplay.value(),airplay.running(),airplay.busy());});
        page=new QuickSettingsView(this,index->{
            switch(index){
                case 0:airplay.toggle();break;
                case 1:open(HomeEditActivity.class);break;
                case 2:open(DeviceStatusActivity.class);break;
                case 4:open(SettingsActivity.class);break;
            }
        },this::finish);
        setContentView(page);
    }
    private void open(Class<? extends Activity> target){startActivity(new Intent(this,target));finish();}
    @Override protected void onResume(){
        super.onResume();resumed=true;airplay.resume();page.readAgain();
        final int token=++generation;
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            ResourceStatus.Snapshot snapshot=ResourceStatus.read(context);
            runOnUiThread(()->{if(resumed&&token==generation)page.resources(snapshot);});
        },"quick-status").start();
    }
    @Override protected void onPause(){resumed=false;generation++;airplay.pause();super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return page.onKey(e)||super.dispatchKeyEvent(e);}
}
