package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Internal guided entry. The final OK reuses the existing protected mode job. */
public class WelcomeActivity extends Activity {
    private final WelcomeFlow flow=new WelcomeFlow();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Runnable poll=this::readState;
    private WelcomeView page;
    private boolean visible,alive=true,loading,loaded,active,debug,airplayMissing;
    private int requests;
    private String busy="",error="";
    static void open(Activity from){from.startActivity(new Intent(from,WelcomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Watchdog.install(this);Ui.applyLanguage();Ui.init(this);
        if(WelcomeStore.status(this)==WelcomeFlow.NEW)WelcomeStore.status(this,WelcomeFlow.STARTED);
        if(state!=null)flow.restore(state.getInt("step",WelcomeFlow.INTRO));
        page=new WelcomeView(this,this::choose,this::back);setContentView(page);render();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("step",flow.step);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();visible=true;page.resume();readState();}
    @Override protected void onPause(){visible=false;main.removeCallbacks(poll);page.pause();super.onPause();}
    @Override protected void onDestroy(){alive=false;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    private void render(){page.bind(WelcomeView.content(flow,loaded,debug,busy,error,airplayMissing));}
    private void readState(){
        main.removeCallbacks(poll);if(!alive||!visible||loading)return;loading=true;
        final android.content.Context context=getApplicationContext();
        io.execute(()->{
            JSONObject state=HomeMode.state(context);boolean home=WelcomeStore.active(context);
            boolean enabled;
            try{enabled=Settings.Global.getInt(context.getContentResolver(),Settings.Global.ADB_ENABLED,0)!=0;}
            catch(RuntimeException unavailable){enabled=false;}
            final boolean debugEnabled=enabled,receiverMissing=AirPlayReceiver.themedVersion(context).isEmpty();
            main.post(()->{
                if(!alive)return;loading=false;loaded=true;active=home;debug=debugEnabled;airplayMissing=receiverMissing;
                busy=WelcomeStore.busyText(state,SystemClock.elapsedRealtime());error=state.optString("error");
                flow.observe(SystemClock.elapsedRealtime(),active,!busy.isEmpty(),error);
                if(flow.step==WelcomeFlow.READY)WelcomeStore.status(context,WelcomeFlow.DONE);
                if(!visible)return;render();if(flow.waiting||!busy.isEmpty())main.postDelayed(poll,500);
            });
        });
    }
    private void choose(int action){
        switch(action){
            case WelcomeView.NEXT:flow.next();render();break;
            case WelcomeView.BOX:case WelcomeView.TV:
                if(flow.step!=WelcomeFlow.DEVICE)return;
                Prefs.setDeviceMode(action==WelcomeView.TV?DeviceProfiles.TV:DeviceProfiles.BOX);flow.next();render();break;
            case WelcomeView.BACK:back();break;
            case WelcomeView.DEVELOPERS:
                if(!SettingsLinks.openDeveloperSettings(this)){
                    error=Ui.tr("Could not open TV settings. Open About on your TV to enable developer options.");render();
                }
                break;
            case WelcomeView.NETWORK:SettingsLinks.openNetworkSettings(this);break;
            case WelcomeView.ENABLE:
                if(!loaded)return;
                active=WelcomeStore.active(this);
                if(active&&busy.isEmpty()&&error.isEmpty()){flow.observe(SystemClock.elapsedRealtime(),true,false,"");WelcomeStore.status(this,WelcomeFlow.DONE);render();return;}
                if(!flow.begin(SystemClock.elapsedRealtime(),active,!busy.isEmpty(),error))return;
                WelcomeStore.status(this,WelcomeFlow.STARTED);render();
                try{requests++;HomeMode.request(getApplicationContext(),HomeMode.FREE);readState();}
                catch(RuntimeException failed){error=Ui.tr("Setup could not start. Please try again.");flow.observe(SystemClock.elapsedRealtime(),false,false,error);render();}
                break;
            case WelcomeView.LATER:WelcomeStore.status(this,WelcomeFlow.DEFERRED);openSettings();break;
            case WelcomeView.SETTINGS:openSettings();break;
            case WelcomeView.HOME:
                if(!WelcomeStore.active(this)){active=false;flow.step=WelcomeFlow.REVIEW;render();readState();return;}
                WelcomeStore.status(this,WelcomeFlow.DONE);
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));finish();break;
            case WelcomeView.CHECK:readState();break;
            case WelcomeView.AIRPLAY:startActivity(new Intent(this,AirPlayInstallActivity.class));break;
            case WelcomeView.LEAVE:finish();break;
        }
    }
    private void openSettings(){startActivity(new Intent(this,SettingsActivity.class).putExtra("home_settings",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
    private void back(){if(flow.back())render();else finish();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return page.onKey(event)||super.dispatchKeyEvent(event);}
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);out.println(prefix+"Welcome: step="+flow.step+" loaded="+loaded+" active="+active+" waiting="+flow.waiting+" status="+WelcomeStore.status(this)+" requests="+requests);
    }
}
