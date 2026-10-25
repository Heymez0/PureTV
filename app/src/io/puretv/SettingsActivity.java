package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Categories share one lightweight page and retain the focused row when returning from a child. */
public class SettingsActivity extends Activity {
    private SettingsPage page;
    private SettingsPage.Data data;
    private AirPlayControl airplay;
    private SettingsEditors editors;
    private DreamView preview;
    private int category,generation,languageGeneration;
    private final int[] positions=new int[8];
    private boolean resumed;
    private boolean routingWelcome;
    private String appVersion="—";
    private ResourceStatus.Snapshot resources=new ResourceStatus.Snapshot();
    private String[] categories() {return new String[]{
        tr("Home screen"),tr("Appearance"),tr("Clock and weather"),tr("Content"),
        tr("Connections"),tr("Device and performance"),tr("Remote Buttons"),tr("System and about")};}
    private String tr(String text){return Ui.tr(text);}
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        if(routeWelcome())return;
        try{appVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        category=state==null?0:Math.max(0,Math.min(7,state.getInt("category",0)));
        airplay=new AirPlayControl(this,()->{if(page!=null&&category==4)page.changed();});
        editors=new SettingsEditors(this);
        if(state!=null){int[] saved=state.getIntArray("positions");if(saved!=null&&saved.length==positions.length)System.arraycopy(saved,0,positions,0,positions.length);}
        languageGeneration=Ui.generation;showCategory(category);
        if(state!=null)editors.restore(state);
    }
    @Override protected void onSaveInstanceState(Bundle out){
        if(page==null||editors==null){super.onSaveInstanceState(out);return;}
        if(!editors.opened())positions[category]=page.focusedIndex();
        out.putInt("category",category);out.putIntArray("positions",positions);editors.save(out);super.onSaveInstanceState(out);
    }
    private SettingsPage.Row action(String name,Runnable action){return new SettingsPage.Row(tr(name),d->action.run(),false);}
    private SettingsPage.Row adjust(String name,SettingsPage.Action action){return new SettingsPage.Row(tr(name),action,true);}
    private void editor(String section){positions[category]=page.focusedIndex();editors.open(section);}
    private void remotePicker(int key){positions[category]=page.focusedIndex();editors.openRemote(key);}
    private void open(Class<? extends Activity> target){startActivity(new Intent(this,target));}
    private void showCategory(int next) {showCategory(next,true);}
    private void showCategory(int next,boolean navigation) {
        if(page!=null&&!editors.opened())positions[category]=page.focusedIndex();
        editors.close();
        category=next;List<SettingsPage.Row> rows=new ArrayList<>();
        switch(category){
            case 0:
                rows.add(action("Home screen layout",()->editor("layout")));
                rows.add(action("Home screen apps",()->editor("shelf")));
                rows.add(action("Shortcuts",()->open(ShortcutSettingsActivity.class)));
                rows.add(action("Other apps",()->{HomeActivity.requestOtherApps();finish();}));
                SettingsPage.Row details=action("Detailed weather",()->Prefs.setDetailedWeather(!Prefs.detailedWeather()));
                details.hint=tr("Show or hide the forecast below shortcuts. Header weather stays unchanged.");rows.add(details);
                rows.add(action("Device mode",()->editor("device")));
                if(Prefs.television())rows.add(action("TV inputs",()->open(TvInputsActivity.class)));
                break;
            case 1:
                rows.add(adjust("Theme",d->{String[] modes={"auto","light","dark"};int i=java.util.Arrays.asList(modes).indexOf(Prefs.theme());Prefs.setTheme(modes[(i+d+3)%3]);}));
                rows.add(adjust("Sounds",d->Prefs.setSounds(!Prefs.sounds())));
                break;
            case 2:
                rows.add(adjust("Clock format",d->{String[] modes={"auto","12","24"};int i=java.util.Arrays.asList(modes).indexOf(Prefs.clockFormat());Prefs.setClockFormat(modes[(i+d+3)%3]);}));
                rows.add(action("City",()->editor("city")));
                rows.add(adjust("Temperature",d->{String[] units={"auto","C","F"};int i=java.util.Arrays.asList(units).indexOf(Prefs.temperature());Prefs.setTemperature(units[(i+d+3)%3]);}));
                rows.add(adjust("Weather information",d->Prefs.setWeather(!Prefs.weather())));
                rows.add(action("Refresh weather",()->{HomeData h=HomeData.get(this);h.settingsChanged();h.refreshIfStale();}));
                break;
            case 3:
                rows.add(action("Content apps",()->open(ContinueSettingsActivity.class)));
                rows.add(action("Restore hidden content",()->{Prefs.restoreHiddenContent();ContinueWatching.get(this).refresh(true);}));
                break;
            case 4:
                SettingsPage.Row airplayRow=action("AirPlay",()->airplay.toggle());
                airplayRow.hint=tr("Press OK to start or stop AirPlay. Left returns to categories.");rows.add(airplayRow);
                rows.add(action("AirPlay connection help",()->startActivity(new Intent(this,DeviceStatusActivity.class).putExtra("airplay_help",true))));
                SettingsPage.Row network=new SettingsPage.Row(tr("Network"),null,false);network.network=true;rows.add(network);
                rows.add(new SettingsPage.Row("IP",null,false));
                rows.add(action("Wi-Fi settings",()->SettingsLinks.open(this,Settings.ACTION_WIFI_SETTINGS)));
                rows.add(action("Bluetooth",()->SettingsLinks.openBluetooth(this)));
                break;
            case 5:
                rows.add(new SettingsPage.Row("RAM",null,false));rows.add(new SettingsPage.Row(tr("Storage"),null,false));
                rows.add(adjust("Lightweight mode",d->Prefs.setLightweight(!Prefs.lightweight())));
                rows.add(action("Refresh status",this::loadResources));
                break;
            case 6:
                if(RemoteKeys.supported()){
                    String[] buttons=editors.remoteLabels();
                    for(int i=0;i<buttons.length;i++){final int key=i;rows.add(new SettingsPage.Row(buttons[i],d->remotePicker(key),false));}
                }
                if(Prefs.television())rows.add(action("TV remote shortcuts",()->Prefs.setTvRemote(!Prefs.tvRemote())));
                rows.add(action("Remote button test",()->open(RemoteTestActivity.class)));
                break;
            default:
                rows.add(action("Default home screen",()->editor("home")));
                rows.add(action("Screensaver",this::showPreview));
                rows.add(action("TV Settings",()->SettingsLinks.open(this,Settings.ACTION_SETTINGS)));
                rows.add(action("Language",()->editor("language")));
                rows.add(new SettingsPage.Row(tr("Version"),null,false));
                rows.add(action("Status and notices",()->editor("status")));
                rows.add(action("Licences",()->new android.app.AlertDialog.Builder(this).setTitle(tr("Licences"))
                    .setMessage("Launcher: MIT\nAirPlay: GPL-3.0\n\n"+tr("Source code and licences are included in the update package.")).setPositiveButton("OK",null).show()));
                rows.add(action("Setup guide",()->WelcomeActivity.open(this)));
        }
        SettingsPage.Data nextData=new SettingsPage.Data(categories()[category],tr("Make yourself at home"),"settings",rows.toArray(new SettingsPage.Row[0]));
        nextData.hint=category==0?tr("Choose the layout, arrange shelves and show or hide the detailed forecast.")
            :category==3?tr("Only content shared by supported apps appears here. Hidden content stays hidden until you restore it.")
            :category==4?tr("AirPlay can start or stop here without opening the receiver. Your pairing preferences stay unchanged.")
            :category==5?tr("Available / total. Values refresh when you open this screen.")
            :category==6?tr("TV remote shortcuts work on the PureTV home screen only. Input opens sources, TV opens live TV, Menu opens quick settings. Power, volume, channels and voice stay with the system.")+"\n"+editors.remoteHint():tr("Right opens options. Back returns to categories.");
        installPage(nextData,positions[category],navigation);
        if(resumed){if(category==4)airplay.resume();else airplay.pause();if(category==5)loadResources();editors.resume();}
    }
    private void installPage(SettingsPage.Data next,int selected,boolean navigation){
        if(page!=null)page.pause();
        data=next;data.categories=categories();data.category=category;
        data.selectCategory=n->{if(n!=category||editors.opened())showCategory(n);};
        page=new SettingsPage(this,data,this::update);page.select(selected);page.navigationFocus(navigation);
        setContentView(page);if(resumed&&preview==null)page.resume();
    }
    void showEditor(SettingsPage.Data value,int selected){installPage(value,selected,false);}
    void finishEditor(){languageGeneration=Ui.generation;showCategory(category,false);}
    void refreshSettings(){if(resumed&&preview==null)page.changed();}
    boolean settingsVisible(){return resumed&&preview==null;}
    int settingsCategory(){return category;}
    private String on(boolean value){return tr(value?"On":"Off");}
    private void update(){
        if(editors.opened()){editors.update(data);return;}
        switch(category){
            case 0:data.rows[0].value=tr(HomeContentModes.label(Prefs.homeContentMode()));data.rows[4].value=on(Prefs.detailedWeather());
                data.rows[5].value=tr(DeviceProfiles.label(Prefs.deviceMode()));break;
            case 1:data.rows[0].value=tr(Prefs.theme().equals("auto")?"Automatic":Prefs.theme().equals("dark")?"Dark":"Light");
                data.rows[1].value=on(Prefs.sounds());break;
            case 2:
                data.rows[0].value=Prefs.clockFormat().equals("auto")?tr("Automatic"):Prefs.clockFormat()+"h";
                data.rows[1].value=Prefs.city();data.rows[2].value=Prefs.temperature().equals("auto")?tr("Automatic"):"°"+Prefs.temperature();
                data.rows[3].value=on(Prefs.weather());break;
            case 3:data.rows[1].value=Integer.toString(Prefs.hiddenContent().size());break;
            case 4:
                data.rows[0].value=airplay.value();NetworkStatus.Snapshot n=page.networkSnapshot();
                SettingsPage.Row row=data.rows[2];row.state=n.state;row.transport=n.transport;
                row.value=NetworkStatus.label(n);row.color=n.state==NetworkStatus.ONLINE?0xFF30D158:0xFFFF9F0A;
                data.rows[3].value=n.addresses.isEmpty()?"—":n.addresses;break;
            case 5:data.rows[0].value=ResourceStatus.value(this,resources.ramAvailable,resources.ramTotal);
                data.rows[1].value=ResourceStatus.value(this,resources.storageAvailable,resources.storageTotal);
                data.rows[2].value=on(Prefs.lightweight());break;
            case 6:
                if(RemoteKeys.supported())editors.updateRemote(data);
                if(Prefs.television())data.rows[RemoteKeys.supported()?Prefs.KEYS.length:0].value=on(Prefs.tvRemote());break;
            case 7:
                data.rows[0].value=editors.homeValue();data.rows[1].value=tr("Preview");
                data.rows[3].value=Prefs.language().equals("auto")?tr("Automatic"):Ui.languageName(Prefs.language());
                data.rows[4].value=appVersion;break;
            default:break;
        }
    }
    private void loadResources(){
        final int token=++generation;
        new Thread(()->{ResourceStatus.Snapshot value=ResourceStatus.read(getApplicationContext());
            runOnUiThread(()->{if(resumed&&category==5&&token==generation){resources=value;page.changed();}});
        },"settings-resources").start();
    }
    @Override protected void onResume(){
        super.onResume();if(routingWelcome||page==null)return;resumed=true;
        if(languageGeneration!=Ui.generation){languageGeneration=Ui.generation;showCategory(category);}
        page.resume();if(category==4)airplay.resume();if(category==5)loadResources();editors.resume();
        if(getIntent().getBooleanExtra("edit_shelf",false)){
            getIntent().removeExtra("edit_shelf");showCategory(0,false);page.select(1);editor("shelf");
        }
        if(getIntent().getBooleanExtra("screensaver",false)){
            getIntent().removeExtra("screensaver");showCategory(7,false);positions[7]=1;page.select(1);showPreview();
        }
        if(getIntent().getBooleanExtra("home_settings",false)){
            getIntent().removeExtra("home_settings");showCategory(0,false);
        }
    }
    private boolean routeWelcome(){
        if(!WelcomeStore.shouldShow(this))return false;
        routingWelcome=true;WelcomeActivity.open(this);finish();return true;
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);routeWelcome();}
    private void showPreview(){
        if(preview!=null)return;
        positions[category]=page.focusedIndex();page.pause();airplay.pause();editors.pause();
        preview=new DreamView(this);setContentView(preview);preview.start();
    }
    private void closePreview(boolean resume){
        if(preview==null)return;
        preview.stop();preview=null;setContentView(page);
        if(resume&&resumed){page.resume();editors.resume();}
    }
    @Override protected void onPause(){resumed=false;generation++;closePreview(false);if(airplay!=null)airplay.pause();if(editors!=null)editors.pause();if(page!=null)page.pause();super.onPause();}
    @Override protected void onDestroy(){closePreview(false);if(page!=null)page.pause();if(editors!=null)editors.destroy();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(page==null||editors==null)return super.dispatchKeyEvent(e);
        if(preview!=null){
            switch(e.getKeyCode()){
                case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:case KeyEvent.KEYCODE_BACK:
                    if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())closePreview(true);return true;
            }
        }
        if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&editors.opened()){
            if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())finishEditor();return true;
        }
        return page.onKey(e)||super.dispatchKeyEvent(e);
    }
}
