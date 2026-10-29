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
    private String receiverVersion="",backupStatus="",restoreStatus="";
    private long backupSaved;
    private boolean backupBusy,freeing;
    private String freeStatus="";
    private String[] categories() {return new String[]{
        tr("Home screen"),tr("Appearance"),tr("Clock and weather"),tr("Content"),
        tr("Connections"),tr("Device and performance"),tr("Remote Buttons"),tr("System and about")};}
    private String tr(String text){return Ui.tr(text);}
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        if(routeWelcome())return;
        ModeJob.scheduleUpdates(getApplicationContext());   // a fresh install has no daily check until now
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
                SettingsPage.Row hidden=action("Hidden apps",()->AppLock.confirm(this,()->editor("hidden")));
                hidden.hint=tr("Apps you hid from Other apps. OK shows an app again. Hold OK on an app in Other apps to hide it.");rows.add(hidden);
                SettingsPage.Row startup=action("On TV start",()->editor("startup"));
                startup.hint=tr("Open an app, Live TV or an input when the TV starts, instead of the home screen. Only right after a start; Back returns to the home screen.");rows.add(startup);
                SettingsPage.Row details=action("Detailed weather",()->Prefs.setDetailedWeather(!Prefs.detailedWeather()));
                details.hint=tr("Show or hide the forecast below shortcuts. Header weather stays unchanged.");rows.add(details);
                rows.add(action("Device mode",()->editor("device")));
                if(Prefs.television())rows.add(action("TV inputs",()->open(TvInputsActivity.class)));
                break;
            case 1:
                rows.add(adjust("Theme",d->{String[] modes={"auto","light","dark"};int i=java.util.Arrays.asList(modes).indexOf(Prefs.theme());Prefs.setTheme(modes[(i+d+3)%3]);}));
                rows.add(adjust("Sounds",d->Prefs.setSounds(!Prefs.sounds())));
                SettingsPage.Row photos=adjust("Screensaver photos",d->togglePhotos());
                photos.hint=tr("Your photos on this TV and its USB drives behind the screensaver clock (landscape photos, shuffled). They stay on the TV. Android asks for access to photos the first time.");
                rows.add(photos);
                SettingsPage.Row background=adjust("Background from content",d->Prefs.setContentBackground(!Prefs.contentBackground()));
                background.hint=tr("In the recent and recommended layouts the background takes its colours from the first card. Off keeps the theme's plain background.");
                rows.add(background);
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
                SettingsPage.Row receiver=action("AirPlay receiver",()->open(AirPlayInstallActivity.class));
                receiver.hint=tr("Install or update the optional receiver from PureTV's release. It runs only after you turn AirPlay on.");rows.add(receiver);
                break;
            case 5:
                rows.add(new SettingsPage.Row("RAM",null,false));rows.add(new SettingsPage.Row(tr("Storage"),null,false));
                rows.add(adjust("Lightweight mode",d->Prefs.setLightweight(!Prefs.lightweight())));
                rows.add(action("Refresh status",this::loadResources));
                SettingsPage.Row assistant=adjust("Voice assistant",d->{if(Prefs.setAssistantOff(!Prefs.assistantOff()))HomeMode.assistant(getApplicationContext());});
                assistant.hint=tr("Off turns Google Assistant off in custom home mode and frees its memory. The remote's microphone button stops working. Original turns it back on.");
                rows.add(assistant);
                SettingsPage.Row free=action("Free up memory",this::freeMemory);   // "Free memory" is the status metric's name
                free.hint=tr("Closes apps waiting in the background. The app on screen and AirPlay keep running; closed apps start again when you open them.");
                rows.add(free);
                SettingsPage.Row auto=adjust("Automatic memory cleanup",d->Prefs.setAutoFreeMemory(!Prefs.autoFreeMemory()));
                auto.hint=tr("When less than 15% of memory is left, returning to the home screen frees it, at most every 15 minutes. The app you just left starts again from the beginning.");
                rows.add(auto);
                SettingsPage.Row lock=action("App lock",()->AppLock.confirm(this,()->editor("lock")));
                lock.hint=tr("A PIN for chosen apps when they are opened from PureTV, always or in the evening. A simple household lock: apps opened another way (TV Settings, the remote's app buttons) are not locked.");
                rows.add(lock);
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
                rows.add(action("Default home screen",()->AppLock.confirm(this,()->editor("home"))));
                rows.add(action("Screensaver",this::showPreview));
                rows.add(action("TV Settings",()->SettingsLinks.open(this,Settings.ACTION_SETTINGS)));
                rows.add(action("Language",()->editor("language")));
                rows.add(new SettingsPage.Row(tr("Version"),null,false));
                rows.add(action("Status and notices",()->editor("status")));
                rows.add(action("Licences",()->Credits.show(this)));
                rows.add(action("Setup guide",()->WelcomeActivity.open(this)));
                SettingsPage.Row save=action("Back up settings",()->backup(false));
                save.hint=tr("Saves layout, shelves, shortcuts, content sources, city, theme and remote buttons. Viewing history and hidden content are not included. In custom home mode a copy also goes to Download/PureTV-settings.json.");
                rows.add(save);
                SettingsPage.Row load=action("Restore settings",()->backup(true));
                load.hint=tr("Replaces these settings with the backup. A newer copy in Download is used first.");
                rows.add(load);
                SettingsPage.Row updates=action("Updates",()->open(UpdateActivity.class));
                updates.hint=tr("Check PureTV's GitHub release, install a newer version, and choose daily notices or automatic installs.");
                rows.add(updates);
        }
        SettingsPage.Data nextData=new SettingsPage.Data(categories()[category],tr("Make yourself at home"),"settings",rows.toArray(new SettingsPage.Row[0]));
        nextData.hint=category==0?tr("Choose the layout, arrange shelves and show or hide the detailed forecast.")
            :category==3?tr("Only content shared by supported apps appears here. Hidden content stays hidden until you restore it.")
            :category==4?tr("AirPlay can start or stop here without opening the receiver. Your pairing preferences stay unchanged.")
            :category==5?tr("Available / total. Values refresh when you open this screen.")
            :category==6?tr("TV remote shortcuts work on the PureTV home screen only. Input opens sources, TV opens live TV, Menu opens quick settings. Power, volume, channels and voice stay with the system.")+"\n"+editors.remoteHint():tr("Right opens options. Back returns to categories.");
        if(category==4)receiverVersion=AirPlayReceiver.themedVersion(this);
        if(category==7)backupSaved=SettingsBackup.savedAt(this);
        installPage(nextData,positions[category],navigation);
        if(resumed){if(category==4)airplay.resume();else airplay.pause();if(category==5)loadResources();editors.resume();}
    }
    private String date(long at){
        return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT,Ui.locale()).format(new java.util.Date(at));
    }
    /** Back up or restore off the main thread (file and the TV's shell); the choices are applied on the main thread. */
    private void backup(boolean restore){
        if(backupBusy)return;backupBusy=true;if(restore)restoreStatus=tr("Working…");else backupStatus=tr("Working…");
        final android.content.Context app=getApplicationContext();
        new Thread(()->{
            String result;SettingsBackup.Loaded loaded=null;
            try{
                if(restore){loaded=SettingsBackup.load(app);result=Ui.trf("Restored from {date}","date",date(loaded.saved));}
                else result=SettingsBackup.save(app)?tr("Saved, with a copy in Download"):tr("Saved in PureTV");
            }catch(java.io.FileNotFoundException e){result=tr("No backup found");}
            catch(java.io.IOException|RuntimeException e){result=Ui.trf("Couldn't complete: {error}","error",String.valueOf(e.getMessage()));}
            final String text=result;final SettingsBackup.Loaded ready=loaded;
            runOnUiThread(()->{
                backupBusy=false;if(restore)restoreStatus=text;else backupStatus=text;
                if(ready!=null){Prefs.restore(ready.values,ready.keys);HomeData.get(this).settingsChanged();}
                if(isFinishing())return;
                // A restore on a new install brings viewing information back on without Android's grant: ask for it now.
                if(ready!=null&&Prefs.viewingInformation()&&!ContinueWatching.permitted(this))
                    requestPermissions(new String[]{ContinueWatching.PERMISSION},VIEWING_REQUEST);
                backupSaved=SettingsBackup.savedAt(this);
                if(languageGeneration!=Ui.generation){languageGeneration=Ui.generation;showCategory(category,false);}
                else if(page!=null&&category==7)page.changed();
            });
        },"settings-backup").start();
        if(page!=null)page.changed();
    }
    private void installPage(SettingsPage.Data next,int selected,boolean navigation){
        if(page!=null)page.pause();
        data=next;data.categories=categories();data.category=category;
        data.selectCategory=n->{if(n!=category||editors.opened())showCategory(n);};
        page=new SettingsPage(this,data,this::update);page.select(selected);page.navigationFocus(navigation);
        setContentView(page);if(resumed&&preview==null)page.resume();
    }
    void showEditor(SettingsPage.Data value,int selected){installPage(value,selected,false);}
    /** The focused row of the open editor (kept when a list is drawn again after a change). */
    int editorFocus(){return page==null||!editors.opened()?0:page.focusedIndex();}
    void finishEditor(){languageGeneration=Ui.generation;showCategory(category,false);}
    void refreshSettings(){if(resumed&&preview==null)page.changed();}
    boolean settingsVisible(){return resumed&&preview==null;}
    int settingsCategory(){return category;}
    private String on(boolean value){return tr(value?"On":"Off");}
    private void update(){
        if(editors.opened()){editors.update(data);return;}
        switch(category){
            case 0:data.rows[0].value=tr(HomeContentModes.label(Prefs.homeContentMode()));
                data.rows[4].value=Integer.toString(Prefs.hiddenApps().size());data.rows[5].value=editors.startupValue();
                data.rows[6].value=on(Prefs.detailedWeather());data.rows[7].value=tr(DeviceProfiles.label(Prefs.deviceMode()));break;
            case 1:data.rows[0].value=tr(Prefs.theme().equals("auto")?"Automatic":Prefs.theme().equals("dark")?"Dark":"Light");
                data.rows[1].value=on(Prefs.sounds());data.rows[2].value=on(Prefs.screensaverPhotos()&&LocalPhotos.permitted(this));
                data.rows[3].value=on(Prefs.contentBackground());break;
            case 2:
                data.rows[0].value=Prefs.clockFormat().equals("auto")?tr("Automatic"):Prefs.clockFormat()+"h";
                data.rows[1].value=Prefs.city();data.rows[2].value=Prefs.temperature().equals("auto")?tr("Automatic"):"°"+Prefs.temperature();
                data.rows[3].value=on(Prefs.weather());break;
            case 3:data.rows[1].value=Integer.toString(Prefs.hiddenContent().size());break;
            case 4:
                data.rows[0].value=airplay.value();NetworkStatus.Snapshot n=page.networkSnapshot();
                SettingsPage.Row row=data.rows[2];row.state=n.state;row.transport=n.transport;
                row.value=NetworkStatus.label(n);row.color=n.state==NetworkStatus.ONLINE?0xFF30D158:0xFFFF9F0A;
                data.rows[3].value=n.addresses.isEmpty()?"—":n.addresses;
                data.rows[6].value=receiverVersion.isEmpty()?tr("Not installed"):receiverVersion;break;
            case 5:data.rows[0].value=ResourceStatus.value(this,resources.ramAvailable,resources.ramTotal);
                data.rows[1].value=ResourceStatus.value(this,resources.storageAvailable,resources.storageTotal);
                data.rows[2].value=on(Prefs.lightweight());data.rows[4].value=on(!Prefs.assistantOff());data.rows[5].value=freeStatus;
                data.rows[6].value=on(Prefs.autoFreeMemory());data.rows[7].value=on(Prefs.lockSet());break;
            case 6:
                if(RemoteKeys.supported())editors.updateRemote(data);
                if(Prefs.television())data.rows[RemoteKeys.supported()?Prefs.KEYS.length:0].value=on(Prefs.tvRemote());break;
            case 7:
                data.rows[0].value=editors.homeValue();data.rows[1].value=tr("Preview");
                data.rows[3].value=Prefs.language().equals("auto")?tr("Automatic"):Ui.languageName(Prefs.language());
                data.rows[4].value=appVersion;
                data.rows[8].value=!backupStatus.isEmpty()?backupStatus:backupSaved==0?tr("Not saved yet"):date(backupSaved);
                data.rows[9].value=!restoreStatus.isEmpty()?restoreStatus:backupSaved==0?tr("No backup"):"";
                String mode=Prefs.updateMode();
                data.rows[10].value=tr(InstallRules.AUTO.equals(mode)?"Install automatically":InstallRules.OFF.equals(mode)?"Off":"Notify");break;
            default:break;
        }
    }
    private static final int PHOTOS_REQUEST=41,VIEWING_REQUEST=42;
    private void togglePhotos(){
        if(Prefs.screensaverPhotos()&&LocalPhotos.permitted(this)){Prefs.setScreensaverPhotos(false);return;}
        if(LocalPhotos.permitted(this)){Prefs.setScreensaverPhotos(true);return;}
        requestPermissions(new String[]{LocalPhotos.permission()},PHOTOS_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==VIEWING_REQUEST){ContinueWatching.get(this).refresh(true);return;}
        if(request!=PHOTOS_REQUEST)return;
        boolean granted=results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(granted)Prefs.setScreensaverPhotos(true);
        else android.widget.Toast.makeText(this,tr("Android didn't allow access to photos."),android.widget.Toast.LENGTH_SHORT).show();
        if(page!=null)page.changed();
    }
    private void freeMemory(){
        if(freeing)return;freeing=true;freeStatus=tr("Freeing memory…");page.changed();
        final android.content.Context app=getApplicationContext();
        new Thread(()->{
            String result=FreeMemory.result(FreeMemory.run(app));ResourceStatus.Snapshot value=ResourceStatus.read(app);
            runOnUiThread(()->{
                freeing=false;freeStatus=result;
                if(resumed&&category==5&&!editors.opened()){resources=value;page.changed();page.announceForAccessibility(result);}
            });
        },"settings-free-memory").start();
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
        if(category==4)receiverVersion=AirPlayReceiver.themedVersion(this);
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
