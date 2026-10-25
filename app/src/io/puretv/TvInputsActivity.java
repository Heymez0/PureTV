package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Short-lived, read-only source inventory. Callbacks and worker live only as long as this page. */
public class TvInputsActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable refresh=this::load;
    private SettingsPage page;private TvInputManager manager;
    private boolean alive=true,visible,loading,dirty,registered;private int generation;
    private List<HomeApps.Item> inputs=Collections.emptyList();private boolean live;
    private final TvInputManager.TvInputCallback callback=new TvInputManager.TvInputCallback(){
        @Override public void onInputAdded(String id){changed();}
        @Override public void onInputRemoved(String id){changed();}
        @Override public void onInputUpdated(String id){changed();}
        @Override public void onInputStateChanged(String id,int state){changed();}
    };
    private String tr(String value){return Ui.tr(value);}
    private SettingsPage.Row action(String label,Runnable task){return new SettingsPage.Row(tr(label),d->task.run(),false);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        manager=(TvInputManager)getSystemService(TV_INPUT_SERVICE);render(0);
    }
    private void render(int selected){
        if(page!=null)page.pause();List<SettingsPage.Row> rows=new ArrayList<>();
        if(Prefs.television()){
            SettingsPage.Row show=action("Show inputs on home screen",()->{Prefs.setTvInputs(!Prefs.tvInputs());page.changed();});
            show.value=tr(Prefs.tvInputs()?"On":"Off");rows.add(show);
            SettingsPage.Row inactive=action("Show inactive HDMI inputs",()->{Prefs.setInactiveInputs(!Prefs.inactiveInputs());page.changed();});
            inactive.value=tr(Prefs.inactiveInputs()?"On":"Off");rows.add(inactive);
            if(live)rows.add(action("Live TV",()->open(HomeApps.action(HomeApps.LIVE_TV,"live",tr("Live TV"),"tv"))));
            for(HomeApps.Item input:inputs){
                SettingsPage.Row row=new SettingsPage.Row(input.label,d->open(input),false);
                row.value=tr(input.connected?"Connected":"No active signal");rows.add(row);
            }
            if(inputs.isEmpty()&&!live)rows.add(new SettingsPage.Row(tr(loading?"Checking…":"No physical TV inputs were reported by this device."),null,false));
            rows.add(action("Refresh inputs",this::changed));
            rows.add(action("Channel setup",()->system(TvInputManager.ACTION_SETUP_INPUTS)));
            rows.add(action("Picture settings",()->system(Settings.ACTION_DISPLAY_SETTINGS)));
            rows.add(action("Sound settings",()->system(Settings.ACTION_SOUND_SETTINGS)));
        }else rows.add(new SettingsPage.Row(tr("Select Television in Home screen → Device mode to use TV inputs."),null,false));
        rows.add(action("TV Settings",()->system(Settings.ACTION_SETTINGS)));
        SettingsPage.Data data=new SettingsPage.Data(tr("TV inputs"),tr("Sources and TV controls"),"hdmi",rows.toArray(new SettingsPage.Row[0]));
        data.hint=tr("Only inputs reported by the device appear. A TV box cannot access the television's HDMI ports. Picture, sound and channels open the TV's own settings.");
        page=new SettingsPage(this,data,()->{
            if(Prefs.television()){data.rows[0].value=tr(Prefs.tvInputs()?"On":"Off");data.rows[1].value=tr(Prefs.inactiveInputs()?"On":"Off");}
        });page.select(selected);setContentView(page);if(visible)page.resume();
    }
    private void open(HomeApps.Item item){
        if(!HomeApps.open(this,item,page,0,0,page.getWidth(),page.getHeight()))
            android.widget.Toast.makeText(this,tr("The TV could not open this source."),android.widget.Toast.LENGTH_SHORT).show();
    }
    private void system(String action){if(!SettingsLinks.openTvControl(this,action))android.widget.Toast.makeText(this,tr("Could not open TV settings."),android.widget.Toast.LENGTH_SHORT).show();}
    private void changed(){
        generation++;dirty=true;main.removeCallbacks(refresh);if(visible)main.postDelayed(refresh,250);
    }
    private void load(){
        if(!alive||!visible||loading||!Prefs.television())return;loading=true;dirty=false;final int token=generation;
        final android.content.Context context=getApplicationContext();
        worker.execute(()->{
            List<HomeApps.Item> found;
            try{found=TvInputCatalog.hdmi(context,true);}catch(RuntimeException unavailable){found=Collections.emptyList();}
            final List<HomeApps.Item> result=found;boolean hasLive=false;
            try{hasLive=HomeApps.liveTv(context)!=null;}catch(RuntimeException ignored){}
            final boolean available=hasLive;
            main.post(()->{
                if(!alive)return;loading=false;if(!visible)return;
                if(token!=generation||dirty){load();return;}
                // Preserve source identity when a cable is plugged in above the focused row.
                int index=page.focusedIndex(),start=live?3:2;String focused=index>=start&&index<start+inputs.size()?inputs.get(index-start).key:null;
                inputs=result;live=available;
                if(focused!=null)for(int i=0;i<inputs.size();i++)if(focused.equals(inputs.get(i).key))index=(live?3:2)+i;
                render(index);
            });
        });
    }
    @Override protected void onResume(){
        super.onResume();visible=true;page.resume();
        if(Prefs.television()&&manager!=null)try{manager.registerCallback(callback,main);registered=true;}catch(RuntimeException ignored){}
        changed();
    }
    @Override protected void onPause(){
        visible=false;generation++;main.removeCallbacks(refresh);page.pause();
        if(registered){try{manager.unregisterCallback(callback);}catch(RuntimeException ignored){}registered=false;}super.onPause();
    }
    @Override protected void onDestroy(){alive=false;main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return page.onKey(event)||super.dispatchKeyEvent(event);}
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);out.println(prefix+"TV inputs: profile="+Prefs.deviceMode()+" ports="+inputs.size()+" live="+live+" callback="+registered+" loading="+loading+" show="+Prefs.tvInputs()+" inactive="+Prefs.inactiveInputs());
    }
}
