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
    /** The weather city step: up to two places to choose, what the step says, and a search in progress. */
    private String[][] cities=new String[0][];
    private String cityStatus="";
    private boolean cityBusy,cityGuessed;
    private TextDialog cityDialog;
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
    @Override protected void onDestroy(){if(cityDialog!=null)cityDialog.dismiss();alive=false;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    /**
     * The AirPlay step. It comes up by itself once, when setup finishes and the carried receiver isn't installed;
     * Back from the last page shows it again (installed or not), so it is always there to find.
     */
    private boolean offerAirplay(){return flow.step==WelcomeFlow.READY&&airplayPage;}
    private void render(){
        if(flow.step==WelcomeFlow.CITY&&!cityGuessed)guessCity();
        page.bind(WelcomeView.content(flow,loaded,debug,busy,error,airplayMissing,offerAirplay(),airplayStatus,airplayBusy,cities,cityStatus,cityBusy));
    }
    /**
     * The first choices: the city already set (if any) and the one in the TV's time zone, found with the same search as
     * Settings → City. The search sends only the city's name.
     */
    private void guessCity(){
        cityGuessed=true;
        final java.util.List<String[]> found=new java.util.ArrayList<>();
        if(!Prefs.city().isEmpty())found.add(new String[]{Prefs.city()+"  ✓",Prefs.city(),Prefs.latitude(),Prefs.longitude()});   // the city in use, marked
        cities=found.toArray(new String[0][]);
        cityStatus=found.isEmpty()?Ui.tr("Choose your city."):"";
        final String zone=WelcomeFlow.zoneCity(java.util.TimeZone.getDefault().getID());
        if(zone.isEmpty()||zone.equalsIgnoreCase(Prefs.city()))return;
        cityBusy=true;
        io.execute(()->{
            String[] guess=null;
            try{java.util.List<String[]> r=HomeData.findCity(zone);if(!r.isEmpty())guess=r.get(0);}
            catch(Exception offline){AppLog.w("city guess: "+offline.getClass().getSimpleName());}
            final String[] place=guess;
            main.post(()->{
                if(!alive)return;cityBusy=false;
                if(place!=null&&(found.isEmpty()||!place[1].equalsIgnoreCase(found.get(0)[1]))){found.add(place);cityStatus=Ui.tr("Suggested from the TV's time zone.");}
                cities=found.toArray(new String[0][]);render();
            });
        });
    }
    private void searchCity(){
        if(cityBusy||cityDialog!=null||!visible)return;
        TextDialog dialog=new TextDialog(this,Ui.tr("City"),Ui.tr("Type the city's name"),android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,120,query->{
            final String name=query.trim();if(name.isEmpty())return;
            cityBusy=true;render();
            io.execute(()->{
                java.util.List<String[]> r;String message;
                try{r=HomeData.findCity(name);message=r.isEmpty()?Ui.tr("No city with that name."):Ui.tr("Choose your city.");}
                catch(Exception e){r=new java.util.ArrayList<>();message=Ui.tr("Could not search for a city. Try again.");}
                final java.util.List<String[]> result=r;final String text=message;
                main.post(()->{
                    if(!alive)return;cityBusy=false;cityStatus=text;
                    cities=result.subList(0,Math.min(2,result.size())).toArray(new String[0][]);render();
                });
            });
        });
        cityDialog=dialog;dialog.setOnDismissListener(d->{if(cityDialog==dialog)cityDialog=null;});dialog.show();
    }
    private void pickCity(int index){
        if(flow.step!=WelcomeFlow.CITY||index>=cities.length)return;
        String[] c=cities[index];
        if(!c[1].equals(Prefs.city())||!c[2].equals(Prefs.latitude())||!c[3].equals(Prefs.longitude())){
            Prefs.setCity(c[1],c[2],c[3]);HomeData.get(this).settingsChanged();
        }
        cityGuessed=false;   // coming back to this step lists the new city as the current one
        flow.next();render();
    }
    private static final int VIEWING_REQUEST=43;
    private void layout(String value){
        if(flow.step!=WelcomeFlow.LAYOUT)return;
        if(!HomeContentModes.CLOCK.equals(value))Prefs.setViewingInformation(true);
        Prefs.setHomeContentMode(value);
        // A layout with viewing content needs Android's TV listings permission: ask now, or its tabs stay empty.
        if(!HomeContentModes.CLOCK.equals(value)&&!ContinueWatching.permitted(this)){
            try{requestPermissions(new String[]{ContinueWatching.PERMISSION},VIEWING_REQUEST);return;}
            catch(RuntimeException unavailable){AppLog.w("viewing permission request: "+unavailable.getClass().getSimpleName());}
        }
        flow.next();render();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request!=VIEWING_REQUEST)return;
        // Refused: viewing information off, as in Settings (the home screen then shows the clock layout).
        if(!ContinueWatching.permitted(this))Prefs.setViewingInformation(false);
        ContinueWatching.get(this).refresh(true);
        if(flow.step==WelcomeFlow.LAYOUT){flow.next();render();}
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
            case WelcomeView.CITY_A:pickCity(0);break;
            case WelcomeView.CITY_B:pickCity(1);break;
            case WelcomeView.CITY_SEARCH:searchCity();break;
            case WelcomeView.CITY_SKIP:if(flow.step==WelcomeFlow.CITY){flow.next();render();}break;
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
                try{requests++;HomeMode.request(getApplicationContext(),HomeMode.FREE,true);readState();}
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
