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
    private boolean visible,alive=true,loading,loaded,active,debug,airplayMissing,bundled,airplayPage,airplaySeen,airplayBusy;
    private String airplayStatus="";
    private int requests;
    private String busy="",error="";
    static void open(Activity from){from.startActivity(new Intent(from,WelcomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Watchdog.install(this);Ui.applyLanguage();Ui.init(this);
        if(WelcomeStore.status(this)==WelcomeFlow.NEW)WelcomeStore.status(this,WelcomeFlow.STARTED);
        if(state!=null){flow.restore(state.getInt("step",WelcomeFlow.INTRO));airplaySeen=state.getBoolean("airplay_seen");}
        bundled=AirPlayInstall.bundled(this);
        page=new WelcomeView(this,this::choose,this::back);setContentView(page);render();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("step",flow.step);out.putBoolean("airplay_seen",airplaySeen);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();visible=true;page.resume();readState();}
    @Override protected void onPause(){visible=false;main.removeCallbacks(poll);page.pause();super.onPause();}
    @Override protected void onDestroy(){alive=false;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    /**
     * The AirPlay step. It comes up by itself once, when setup finishes and the carried receiver isn't installed;
     * Back from the last page shows it again (installed or not), so it is always there to find.
     */
    private boolean offerAirplay(){return flow.step==WelcomeFlow.READY&&airplayPage;}
    private void render(){page.bind(WelcomeView.content(flow,loaded,debug,busy,error,airplayMissing,offerAirplay(),airplayStatus,airplayBusy));}
    private void layout(String value){
        if(flow.step!=WelcomeFlow.LAYOUT)return;
        if(!HomeContentModes.CLOCK.equals(value))Prefs.setViewingInformation(true);
        Prefs.setHomeContentMode(value);flow.next();render();
    }
    /** Without a prompt where PureTV is already the home screen; otherwise (or if that fails) Android's own installer. */
    private void installAirplay(){
        if(airplayBusy)return;
        if(!HomeMode.custom(this)){startActivity(new Intent(this,AirPlayInstallActivity.class));return;}
        airplayBusy=true;airplayStatus=Ui.tr("Installing AirPlay…");render();
        final android.content.Context app=getApplicationContext();
        io.execute(()->{
            boolean done=false;
            try{java.io.File apk=AirPlayInstall.unpack(app);AirPlayInstall.verify(app,apk);done=AirPlayInstall.installQuietly(app,apk);}
            catch(java.io.IOException|RuntimeException failed){AppLog.w("receiver from the guide: "+failed.getMessage());}
            AirPlayInstall.clean(app);final boolean ok=done;
            main.post(()->{
                if(!alive)return;airplayBusy=false;airplayStatus="";
                if(ok){airplayMissing=false;airplayPage=false;}else if(visible)startActivity(new Intent(this,AirPlayInstallActivity.class));
                render();
            });
        });
    }
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
                if(flow.step==WelcomeFlow.READY&&!airplaySeen){airplaySeen=true;airplayPage=airplayMissing&&bundled;}
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
            case WelcomeView.LAYOUT_CLOCK:layout(HomeContentModes.CLOCK);break;
            case WelcomeView.LAYOUT_RECENT:layout(HomeContentModes.RECENT);break;
            case WelcomeView.LAYOUT_COMBINED:layout(HomeContentModes.COMBINED);break;
            case WelcomeView.AIRPLAY_NOW:installAirplay();break;
            case WelcomeView.AIRPLAY_SKIP:airplayPage=false;render();break;
            case WelcomeView.DEVELOPERS:
                if(!SettingsLinks.openDeveloperSettings(this)){
                    error=Ui.tr("Could not open TV settings. Open About on your TV to enable developer options.");render();
                }
                break;
            case WelcomeView.NETWORK:SettingsLinks.openNetworkSettings(this);break;
            case WelcomeView.ENABLE:
                if(!loaded)return;
                active=WelcomeStore.active(this);
                if(active&&busy.isEmpty()&&error.isEmpty()){flow.browsing=false;flow.observe(SystemClock.elapsedRealtime(),true,false,"");WelcomeStore.status(this,WelcomeFlow.DONE);render();return;}
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
    private void back(){
        if(airplayBusy)return;
        if(flow.step==WelcomeFlow.READY&&!airplayPage&&(bundled||!airplayMissing)){airplayPage=true;render();return;}   // last page → AirPlay
        airplayPage=false;
        if(flow.back())render();else finish();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){return page.onKey(event)||super.dispatchKeyEvent(event);}
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);out.println(prefix+"Welcome: step="+flow.step+" loaded="+loaded+" active="+active+" waiting="+flow.waiting+" status="+WelcomeStore.status(this)+" requests="+requests);
    }
}
