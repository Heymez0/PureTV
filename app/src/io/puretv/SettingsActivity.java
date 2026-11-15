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
    /** The categories, in the order of categories() and CATEGORY_SYMBOLS. */
    private static final int HOME=0,LOOK=1,CLOCK=2,CONTENT=3,CONNECTIONS=4,DEVICE=5,REMOTE=6,SYSTEM=7;
    /** What each row of the open category shows; run on every update. */
    private final List<Runnable> values=new ArrayList<>();
    private int screensaverRow;
    private String freeStatus="";
    private String[] categories() {return new String[]{
        tr("Home screen"),tr("Appearance"),tr("Clock and weather"),tr("Content"),
        tr("Connections"),tr("Device and performance"),tr("Remote Buttons"),tr("System and about")};}
    /** One symbol per category, in the order of categories(). */
    private static final String[] CATEGORY_SYMBOLS={"home","sparkles","partly_cloudy_day","play","network","memory","remote","tv_settings"};
    private String tr(String text){return Ui.tr(text);}
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        if(routeWelcome())return;
        ModeJob.scheduleUpdates(getApplicationContext());   // a fresh install has no daily check until now
        try{appVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        category=state==null?0:Math.max(0,Math.min(7,state.getInt("category",0)));
        airplay=new AirPlayControl(this,()->{if(page!=null&&category==CONNECTIONS)page.changed();});
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
    private SettingsPage.Row action(String name,Runnable action){return new SettingsPage.Row(tr(name),d->action.run());}
    private SettingsPage.Row adjust(String name,SettingsPage.Action action){return new SettingsPage.Row(tr(name),action);}
    private static SettingsPage.Row info(String label){return new SettingsPage.Row(label,null);}
    /**
     * Adds a row to the category being built. `hint` explains it while it has the focus; `value` writes what it shows
     * and runs on every update, so a row carries its own value wherever it sits in the list.
     */
    private SettingsPage.Row add(List<SettingsPage.Row> rows,SettingsPage.Row row,String hint,java.util.function.Consumer<SettingsPage.Row> value){
        if(hint!=null)row.hint=hint;
        if(value!=null)values.add(()->value.accept(row));
        rows.add(row);return row;
    }
    private void editor(String section){positions[category]=page.focusedIndex();editors.open(section);}
    private void remotePicker(int key){positions[category]=page.focusedIndex();editors.openRemote(key);}
    private void open(Class<? extends Activity> target){startActivity(new Intent(this,target));}
    private void showCategory(int next) {showCategory(next,true);}
    private void showCategory(int next,boolean navigation) {
        if(page!=null&&!editors.opened())positions[category]=page.focusedIndex();
        editors.close();
        category=next;List<SettingsPage.Row> rows=new ArrayList<>();values.clear();
        switch(category){
            case HOME:   // what is on the home screen, and which apps
                add(rows,action("Home screen layout",()->editor("layout")),null,r->r.value=tr(HomeContentModes.label(Prefs.homeContentMode())));
                add(rows,action("Home screen apps",()->editor("shelf")),null,null);
                add(rows,action("Shortcuts",()->open(ShortcutSettingsActivity.class)),null,null);
                add(rows,action("Other apps",()->{HomeActivity.requestOtherApps();finish();}),null,null);
                add(rows,action("Hidden apps",()->AppLock.confirm(this,()->editor("hidden"))),
                    tr("Apps you hid from Other apps. OK shows an app again. Hold OK on an app in Other apps to hide it."),
                    r->r.value=Integer.toString(Prefs.hiddenApps().size()));
                add(rows,action("App lock",()->AppLock.confirm(this,()->editor("lock"))),
                    tr("A PIN for chosen apps when they are opened from PureTV, always or in the evening. A simple household lock: apps opened another way (TV Settings, the remote's app buttons) are not locked."),
                    r->r.set(Prefs.lockSet()));
                add(rows,action("On TV start",()->editor("startup")),
                    tr("Open an app, Live TV or an input when the TV starts, instead of the home screen. Only right after a start; Back returns to the home screen."),
                    r->r.value=editors.startupValue());
                if(Prefs.television())add(rows,action("TV inputs",()->open(TvInputsActivity.class)),null,null);
                break;
            case LOOK:   // how it looks and sounds, the screensaver included
                add(rows,adjust("Theme",d->{String[] modes={"auto","light","dark"};int i=java.util.Arrays.asList(modes).indexOf(Prefs.theme());Prefs.setTheme(modes[(i+d+3)%3]);}),
                    null,r->r.value=tr(Prefs.theme().equals("auto")?"Automatic":Prefs.theme().equals("dark")?"Dark":"Light"));
                add(rows,adjust("Background from content",d->Prefs.setContentBackground(!Prefs.contentBackground())),
                    tr("In the recent and recommended layouts the background takes its colours from the first card. Off keeps the theme's plain background."),
                    r->r.set(Prefs.contentBackground()));
                add(rows,adjust("Sounds",d->Prefs.setSounds(!Prefs.sounds())),null,r->r.set(Prefs.sounds()));
                screensaverRow=rows.size();
                add(rows,action("Screensaver",this::showPreview),
                    tr("OK shows the screensaver now. When it starts and when the TV turns off are set in TV Settings."),r->r.value=tr("Preview"));
                add(rows,adjust("Screensaver photos",d->togglePhotos()),
                    tr("Your photos on this TV and its USB drives behind the screensaver clock (landscape photos, shuffled). They stay on the TV. Android asks for access to photos the first time."),
                    r->r.set(Prefs.screensaverPhotos()&&LocalPhotos.permitted(this)));
                break;
            case CLOCK:  // the clock and everything about the weather
                add(rows,adjust("Clock format",d->{String[] modes={"auto","12","24"};int i=java.util.Arrays.asList(modes).indexOf(Prefs.clockFormat());Prefs.setClockFormat(modes[(i+d+3)%3]);}),
                    null,r->r.value=Prefs.clockFormat().equals("auto")?tr("Automatic"):Prefs.clockFormat()+"h");
                add(rows,adjust("Weather information",d->Prefs.setWeather(!Prefs.weather())),
                    tr("The temperature beside the clock at the top, and a small weather line under the clock on the clock layout. The forecast below the shortcuts is Detailed weather."),
                    r->r.set(Prefs.weather()));
                add(rows,action("Detailed weather",()->Prefs.setDetailedWeather(!Prefs.detailedWeather())),
                    tr("Show or hide the forecast below shortcuts."),r->r.set(Prefs.detailedWeather()));
                add(rows,action("City",()->editor("city")),null,r->r.value=Prefs.city());
                add(rows,adjust("Temperature",d->{String[] units={"auto","C","F"};int i=java.util.Arrays.asList(units).indexOf(Prefs.temperature());Prefs.setTemperature(units[(i+d+3)%3]);}),
                    null,r->r.value=Prefs.temperature().equals("auto")?tr("Automatic"):"°"+Prefs.temperature());
                add(rows,action("Refresh weather",()->{HomeData h=HomeData.get(this);h.settingsChanged();h.refreshIfStale();}),null,null);
                break;
            case CONTENT:
                add(rows,action("Content apps",()->open(ContinueSettingsActivity.class)),null,null);
                add(rows,action("Restore hidden content",()->{Prefs.restoreHiddenContent();ContinueWatching.get(this).refresh(true);}),
                    null,r->r.value=Integer.toString(Prefs.hiddenContent().size()));
                break;
            case CONNECTIONS:   // AirPlay first, together; then the network
                add(rows,action("AirPlay",()->airplay.toggle()),tr("Press OK to start or stop AirPlay. Left returns to categories."),
                    r->{r.value=airplay.value();r.toggle=airplay.switchState();});
                add(rows,adjust("Keep AirPlay on",d->airplay.toggleAlways()),
                    tr("On: AirPlay starts with the TV and stays on, keeping the processor awake. Off: it runs only after you turn it on and stops after 15 minutes without a sender."),
                    r->{r.value=airplay.alwaysValue();r.toggle=airplay.alwaysState();});
                add(rows,action("AirPlay receiver",()->open(AirPlayInstallActivity.class)),
                    tr("Install or update the optional receiver from PureTV's release. It runs only after you turn AirPlay on."),
                    r->r.value=receiverVersion.isEmpty()?tr("Not installed"):receiverVersion);
                add(rows,action("AirPlay connection help",()->startActivity(new Intent(this,DeviceStatusActivity.class).putExtra("airplay_help",true))),null,null);
                SettingsPage.Row network=info(tr("Network")),address=info("IP");network.network=true;
                add(rows,network,null,r->{
                    NetworkStatus.Snapshot n=page.networkSnapshot();r.state=n.state;r.transport=n.transport;
                    r.value=NetworkStatus.label(n);r.color=NetworkStatus.color(n);address.value=n.addresses.isEmpty()?"—":n.addresses;});
                add(rows,address,null,null);
                add(rows,action("Wi-Fi settings",()->SettingsLinks.open(this,Settings.ACTION_WIFI_SETTINGS)),null,null);
                add(rows,action("Bluetooth",()->SettingsLinks.openBluetooth(this)),null,null);
                break;
            case DEVICE:   // what this device is, then its memory
                add(rows,action("Device mode",()->editor("device")),
                    tr("TV box / stick for a device plugged into a TV. Television when PureTV runs on the TV itself; it adds the TV's inputs and remote shortcuts."),
                    r->r.value=tr(DeviceProfiles.label(Prefs.deviceMode())));
                add(rows,adjust("Lightweight mode",d->Prefs.setLightweight(!Prefs.lightweight())),
                    tr("For slower TVs: no shadows or glass effects, and screensaver photos stay still. Uses less memory and processor."),r->r.set(Prefs.lightweight()));
                add(rows,adjust("Voice assistant",d->{if(Prefs.setAssistantOff(!Prefs.assistantOff()))HomeMode.assistant(getApplicationContext());}),
                    tr("Off turns Google Assistant off and frees its memory (needs PureTV as the default home screen). The remote's microphone button then stops working; choosing Original as the default home screen turns it back on."),
                    r->r.set(!Prefs.assistantOff()));
                add(rows,info("RAM"),null,r->r.value=ResourceStatus.value(this,resources.ramAvailable,resources.ramTotal));
                add(rows,info(tr("Storage")),null,r->r.value=ResourceStatus.value(this,resources.storageAvailable,resources.storageTotal));
                add(rows,action("Free up memory",this::freeMemory),   // "Free memory" is the status metric's name
                    tr("Closes apps waiting in the background. The app on screen and AirPlay keep running; closed apps start again when you open them."),
                    r->r.value=freeStatus);
                add(rows,adjust("Automatic memory cleanup",d->Prefs.setAutoFreeMemory(!Prefs.autoFreeMemory())),
                    tr("When less than 15% of memory is left, returning to the home screen frees it, at most every 15 minutes. The app you just left starts again from the beginning."),
                    r->r.set(Prefs.autoFreeMemory()));
                add(rows,adjust("Optimise apps during the screensaver",d->Prefs.setOptimizeApps(!Prefs.optimizeApps())),
                    tr("Ten minutes into the screensaver, PureTV prepares the apps you installed to open and run faster, as Android itself would but rarely gets to on a TV. Pressing a key stops it. Needs PureTV as the default home screen."),
                    r->r.set(Prefs.optimizeApps()));
                break;
            case REMOTE:
                if(RemoteKeys.supported()){
                    String[] buttons=editors.remoteLabels();
                    for(int i=0;i<buttons.length;i++){final int key=i;rows.add(new SettingsPage.Row(buttons[i],d->remotePicker(key)));}
                    values.add(()->editors.updateRemote(data));   // the leading rows, one per remote button
                }
                if(Prefs.television())add(rows,action("TV remote shortcuts",()->Prefs.setTvRemote(!Prefs.tvRemote())),null,r->r.set(Prefs.tvRemote()));
                add(rows,action("Remote button test",()->open(RemoteTestActivity.class)),null,null);
                break;
            default:   // SYSTEM: what you change first, what you do now and then, what you only read last
                add(rows,action("Default home screen",()->AppLock.confirm(this,()->editor("home"))),
                    tr("Which home screen the TV opens. To go back to the TV's own home screen, choose TV's original home screen here; PureTV stays installed and you can return to it the same way. Several PureTV features need PureTV as the home screen and say so."),
                    r->r.value=editors.homeValue());
                add(rows,action("Language",()->editor("language")),null,
                    r->r.value=Prefs.language().equals("auto")?tr("Automatic"):Ui.languageName(Prefs.language()));
                add(rows,action("TV Settings",()->SettingsLinks.open(this,Settings.ACTION_SETTINGS)),null,null);
                add(rows,action("Updates",()->open(UpdateActivity.class)),
                    tr("Check PureTV's GitHub release, install a newer version, and choose daily notices or automatic installs."),
                    r->{String mode=Prefs.updateMode();r.value=tr(InstallRules.AUTO.equals(mode)?"Install automatically":InstallRules.OFF.equals(mode)?"Off":"Notify");});
                add(rows,action("Back up settings",()->backup(false)),
                    tr("Saves layout, shelves, shortcuts, content sources, city, theme and remote buttons. Viewing history and hidden content are not included. When PureTV is the default home screen, a copy also goes to Download/PureTV-settings.json."),
                    r->r.value=!backupStatus.isEmpty()?backupStatus:backupSaved==0?tr("Not saved yet"):date(backupSaved));
                add(rows,action("Restore settings",()->backup(true)),tr("Replaces these settings with the backup. A newer copy in Download is used first."),
                    r->r.value=!restoreStatus.isEmpty()?restoreStatus:backupSaved==0?tr("No backup"):"");
                add(rows,action("Setup guide",()->WelcomeActivity.open(this)),null,null);
                add(rows,action("Status and notices",()->editor("status")),
                    tr("What PureTV measured the last time the home screen was switched, and its recent notices. Useful when something doesn't work."),null);
                add(rows,info(tr("Version")),null,r->r.value=appVersion);
                add(rows,action("Licences",()->Credits.show(this)),null,null);
        }
        SettingsPage.Data nextData=new SettingsPage.Data(categories()[category],tr("Make yourself at home"),"settings",rows.toArray(new SettingsPage.Row[0]));
        nextData.hint=category==HOME?tr("Choose the layout, arrange the shelves, and hide or lock apps.")
            :category==CONTENT?tr("Only content shared by supported apps appears here. Hidden content stays hidden until you restore it.")
            :category==CONNECTIONS?tr("AirPlay can start or stop here without opening the receiver. Your pairing preferences stay unchanged.")
            :category==DEVICE?tr("Available / total. Values refresh when you open this screen.")
            :category==REMOTE?tr("TV remote shortcuts work on the PureTV home screen only. Input opens sources, TV opens live TV, Menu opens quick settings. Power, volume, channels and voice stay with the system.")+"\n"+editors.remoteHint():tr("Right opens options. Back returns to categories.");
        if(category==CONNECTIONS)receiverVersion=AirPlayReceiver.themedVersion(this);
        if(category==SYSTEM)backupSaved=SettingsBackup.savedAt(this);
        installPage(nextData,positions[category],navigation);
        if(resumed){if(category==CONNECTIONS)airplay.resume();else airplay.pause();if(category==DEVICE)loadResources();editors.resume();}
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
                else if(page!=null&&category==SYSTEM)page.changed();
            });
        },"settings-backup").start();
        if(page!=null)page.changed();
    }
    private void installPage(SettingsPage.Data next,int selected,boolean navigation){
        if(page!=null)page.pause();
        data=next;data.categories=categories();data.categorySymbols=CATEGORY_SYMBOLS;data.category=category;
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
    private void update(){
        if(editors.opened()){editors.update(data);return;}
        for(Runnable value:values)value.run();
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
                if(resumed&&category==DEVICE&&!editors.opened()){resources=value;page.changed();page.announceForAccessibility(result);}
            });
        },"settings-free-memory").start();
    }
    private void loadResources(){
        final int token=++generation;
        new Thread(()->{ResourceStatus.Snapshot value=ResourceStatus.read(getApplicationContext());
            runOnUiThread(()->{if(resumed&&category==DEVICE&&token==generation){resources=value;page.changed();}});
        },"settings-resources").start();
    }
    @Override protected void onResume(){
        super.onResume();if(routingWelcome||page==null)return;resumed=true;
        if(languageGeneration!=Ui.generation){languageGeneration=Ui.generation;showCategory(category);}
        if(category==CONNECTIONS)receiverVersion=AirPlayReceiver.themedVersion(this);
        page.resume();if(category==CONNECTIONS)airplay.resume();if(category==DEVICE)loadResources();editors.resume();
        if(getIntent().getBooleanExtra("edit_shelf",false)){
            getIntent().removeExtra("edit_shelf");showCategory(HOME,false);page.select(1);editor("shelf");
        }
        if(getIntent().getBooleanExtra("screensaver",false)){
            getIntent().removeExtra("screensaver");showCategory(LOOK,false);positions[LOOK]=screensaverRow;page.select(screensaverRow);showPreview();
        }
        if(getIntent().getBooleanExtra("home_settings",false)){
            getIntent().removeExtra("home_settings");showCategory(HOME,false);
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
