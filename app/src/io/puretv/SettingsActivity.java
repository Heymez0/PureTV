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
    /** What the background and clock-weight previews show, prepared when a value changes (never while drawing). */
    private android.graphics.Bitmap previewPhoto,previewClassic,classicArt;private int previewTop=112;
    private void preparePreview(){
        long now=System.currentTimeMillis();boolean night=HomeData.get(this).isNight(now);
        Scenes.Scene s=Scenes.pick(Prefs.scene(),night,now);
        previewPhoto=s==null?null:Scenes.cachedThumb(this,s);previewTop=s==null?112:s.clockTop;
        classicArt=Ambient.get(night||"dark".equals(Prefs.theme()),Ambient.phase(now));
        previewClassic=s==null?classicArt:null;
    }
    private static String weightName(int weight){if(weight>=600)return Ui.tr("Bold");if(weight>=400)return Ui.tr("Regular");return Ui.tr("Thin");}
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
        // The background choices show every photo: their small copies are decoded here, off the main thread.
        final android.content.Context app=getApplicationContext();
        new Thread(()->{for(Scenes.Scene s:Scenes.ALL)Scenes.cachedThumb(app,s);runOnUiThread(()->{if(page!=null&&!isFinishing())page.changed();});},"settings-thumbs").start();
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
    /**
     * A clock-screen setting: with another layout it shows only while there is nothing to watch, so the row is dimmed
     * and its hint says how to see it always.
     */
    /** How many there are, as a row's value: nothing at all when there are none. */
    private static String count(int n){return n==0?"":Integer.toString(n);}
    private void clockOnly(SettingsPage.Row r,String hint){
        r.muted=!HomeContentModes.CLOCK.equals(Prefs.homeContentMode());
        r.note=r.muted?tr("Only on the clock screen"):null;
        r.hint=r.muted?hint+" "+tr("To see it always, set Home screen layout to Clock only."):hint;
    }
    /**
     * A row whose answers open in the detail pane (SettingsPage.Choice): `answers` builds them when the row is drawn
     * again, `current` says which one is in use and `pick` applies one.
     */
    private SettingsPage.Row choice(String name,java.util.function.Supplier<SettingsPage.Choice[]> answers,java.util.function.IntSupplier current,java.util.function.IntConsumer pick){
        SettingsPage.Row r=new SettingsPage.Row(tr(name),d->{});
        r.pick=i->{pick.accept(i);};
        values.add(()->{r.choices=answers.get();r.current=Math.max(0,current.getAsInt());});
        return r;
    }
    /** Automatic, each photo and Classic colours, each with a small picture of the home screen it gives. */
    private SettingsPage.Choice[] sceneChoices(){
        String[] all=Scenes.choices();SettingsPage.Choice[] out=new SettingsPage.Choice[all.length];
        long now=System.currentTimeMillis();boolean night=HomeData.get(this).isNight(now);
        for(int i=0;i<all.length;i++){
            final Scenes.Scene s=Scenes.pick(all[i],night,now);
            out[i]=new SettingsPage.Choice(Scenes.label(all[i]),s==null?"":s.author,(c,box,dark)->ScenePreview.draw(this,c,box,
                s==null?null:Scenes.peekThumb(s),s==null?classicArt:null,s==null?112:s.clockTop,Prefs.clockWeight(),dark,Ui.clock(System.currentTimeMillis())));
        }
        return out;
    }
    /** The three clock weights, each drawn on today's background. */
    private SettingsPage.Choice[] weightChoices(){
        SettingsPage.Choice[] out=new SettingsPage.Choice[Scenes.WEIGHTS.length];
        for(int i=0;i<out.length;i++){
            final int weight=Scenes.WEIGHTS[i];
            out[i]=new SettingsPage.Choice(weightName(weight),"",(c,box,dark)->ScenePreview.draw(this,c,box,previewPhoto,previewClassic,previewTop,
                weight,dark,Ui.clock(System.currentTimeMillis())));
        }
        return out;
    }
    private SettingsPage.Choice[] themeChoices(){return new SettingsPage.Choice[]{
        new SettingsPage.Choice(tr("Automatic"),tr("Light by day, dark after sunset"),null),
        new SettingsPage.Choice(tr("Light"),"",null),new SettingsPage.Choice(tr("Dark"),"",null)};}
    private SettingsPage.Choice[] clockChoices(){return new SettingsPage.Choice[]{
        new SettingsPage.Choice(tr("Automatic"),tr("Follows the TV"),null),
        new SettingsPage.Choice("12h","1:45 PM",null),new SettingsPage.Choice("24h","13:45",null)};}
    private SettingsPage.Choice[] unitChoices(){return new SettingsPage.Choice[]{
        new SettingsPage.Choice(tr("Automatic"),tr("From your region"),null),
        new SettingsPage.Choice("°C",tr("Celsius"),null),new SettingsPage.Choice("°F",tr("Fahrenheit"),null)};}
    private void editor(String section){positions[category]=page.focusedIndex();editors.open(section);}
    private void remotePicker(int key){positions[category]=page.focusedIndex();editors.openRemote(key);}
    private void open(Class<? extends Activity> target){startActivity(new Intent(this,target));}
    private void showCategory(int next) {showCategory(next,true);}
    private void showCategory(int next,boolean navigation) {
        if(page!=null&&!editors.opened())positions[category]=page.focusedIndex();
        editors.close();
        category=next;List<SettingsPage.Row> rows=new ArrayList<>();values.clear();
        switch(category){
            case HOME:   // everything the home screen shows, in one place: the layout, then the clock screen, then the apps
                add(rows,action("Home screen layout",()->editor("layout")),
                    tr("What the home screen shows: the clock alone, what you were watching, or that with recommendations."),
                    r->r.value=tr(HomeContentModes.label(Prefs.homeContentMode()))).icon("layout").group(tr("Layout"));
                SettingsPage.Row photo=add(rows,choice("Background photo",this::sceneChoices,()->java.util.Arrays.asList(Scenes.choices()).indexOf(Prefs.scene()),
                        i->{Prefs.setScene(Scenes.choices()[i]);preparePreview();}),null,
                    r->{r.value=Scenes.label(Prefs.scene());clockOnly(r,tr("A landscape behind the clock when the home screen has nothing to watch. Automatic shows a new photo every day and a night photo after sunset. OK changes it."));preparePreview();});
                photo.icon("photo").group(tr("Clock screen"));
                add(rows,choice("Clock weight",this::weightChoices,()->{int[] all=Scenes.WEIGHTS;for(int j=0;j<all.length;j++)if(all[j]==Prefs.clockWeight())return j;return 0;},
                        i->Prefs.setClockWeight(Scenes.WEIGHTS[i])),null,
                    r->{r.value=weightName(Prefs.clockWeight());clockOnly(r,tr("How bold the large clock is on a background photo. OK changes it."));preparePreview();}).icon("weight");
                add(rows,adjust("Weather information",d->Prefs.setWeather(!Prefs.weather())),
                    tr("The weather on the home screen: the temperature at the top and, on the clock screen, a weather button at the top left (a line under the clock with Classic colours). The forecast below the shortcuts is Detailed weather."),
                    r->r.set(Prefs.weather())).icon("partly_cloudy_day").group(tr("Weather"));
                add(rows,action("Detailed weather",()->Prefs.setDetailedWeather(!Prefs.detailedWeather())),
                    tr("Show or hide the forecast below shortcuts."),r->r.set(Prefs.detailedWeather())).icon("cloud");
                add(rows,action("Shortcuts",()->open(ShortcutSettingsActivity.class)),
                    tr("The buttons under the app shelf, such as All apps and AirPlay: choose which ones show."),null).icon("apps").group(tr("Apps"));
                add(rows,action("Hidden apps",()->AppLock.confirm(this,()->editor("hidden"))),
                    tr("Apps you hid from Other apps. OK shows an app again. Hold OK on an app in Other apps to hide it."),
                    r->r.value=count(Prefs.hiddenApps().size())).icon("hidden");
                add(rows,action("App lock",()->AppLock.confirm(this,()->editor("lock"))),
                    tr("A PIN for chosen apps when they are opened from PureTV, always or in the evening. A simple household lock: apps opened another way (TV Settings, the remote's app buttons) are not locked."),
                    r->r.set(Prefs.lockSet())).icon("lock");
                add(rows,action("On TV start",()->editor("startup")),
                    tr("Open an app, Live TV or an input when the TV starts, instead of the home screen. Only right after a start; Back returns to the home screen."),
                    r->r.value=editors.startupValue()).icon("power").group(tr("Start-up"));
                if(Prefs.television())add(rows,action("TV inputs",()->open(TvInputsActivity.class)),
                    tr("The TV's inputs, such as HDMI, and its picture, sound and channel controls. OK opens one."),null).icon("hdmi");
                break;
            case LOOK:   // how it looks and sounds, the screensaver included
                add(rows,choice("Theme",this::themeChoices,()->java.util.Arrays.asList("auto","light","dark").indexOf(Prefs.theme()),
                        i->Prefs.setTheme(new String[]{"auto","light","dark"}[i])),
                    tr("Automatic is light by day and dark after sunset in your city; Light and Dark always stay the same. OK changes it."),
                    r->r.value=tr(Prefs.theme().equals("auto")?"Automatic":Prefs.theme().equals("dark")?"Dark":"Light")).icon("palette").group(tr("Look"));
                add(rows,adjust("Background from content",d->Prefs.setContentBackground(!Prefs.contentBackground())),
                    tr("In the recent and recommended layouts the background takes its colours from the first card. Off keeps the theme's plain background."),
                    r->r.set(Prefs.contentBackground())).icon("sparkles");
                add(rows,adjust("Sounds",d->Prefs.setSounds(!Prefs.sounds())),
                    tr("Soft clicks as you move and choose on PureTV's screens."),r->r.set(Prefs.sounds())).icon("speaker").group(tr("Sound"));
                screensaverRow=rows.size();
                add(rows,action("Screensaver",this::showPreview),
                    tr("OK shows the screensaver now. When it starts and when the TV turns off are set in TV Settings."),r->r.value=tr("Preview")).icon("bedtime").group(tr("Screensaver"));
                add(rows,adjust("Screensaver photos",d->togglePhotos()),
                    tr("Your photos on this TV and its USB drives behind the screensaver clock (landscape photos, shuffled). They stay on the TV. Android asks for access to photos the first time."),
                    r->r.set(Prefs.screensaverPhotos()&&LocalPhotos.permitted(this))).icon("photo");
                break;
            case CLOCK:  // the clock and where the weather comes from
                add(rows,choice("Clock format",this::clockChoices,()->java.util.Arrays.asList("auto","12","24").indexOf(Prefs.clockFormat()),
                        i->Prefs.setClockFormat(new String[]{"auto","12","24"}[i])),
                    tr("12 or 24 hours. Automatic follows the TV's setting. OK changes it."),
                    r->r.value=Prefs.clockFormat().equals("auto")?tr("Automatic"):Prefs.clockFormat()+"h").icon("clock").group(tr("Clock"));
                add(rows,action("City",()->editor("city")),
                    tr("The city of the weather. It also sets when the home screen turns dark at sunset."),r->r.value=Prefs.city()).icon("pin").group(tr("Weather"));
                add(rows,choice("Temperature",this::unitChoices,()->java.util.Arrays.asList("auto","C","F").indexOf(Prefs.temperature()),
                        i->Prefs.setTemperature(new String[]{"auto","C","F"}[i])),
                    tr("Celsius or Fahrenheit. Automatic follows your region. OK changes it."),
                    r->r.value=Prefs.temperature().equals("auto")?tr("Automatic"):"°"+Prefs.temperature()).icon("thermo");
                add(rows,action("Refresh weather",()->{HomeData h=HomeData.get(this);h.settingsChanged();h.refreshIfStale();}),
                    tr("Gets the weather now instead of at the next update, every 30 minutes."),null).icon("sync");
                break;
            case CONTENT:
                add(rows,action("Content apps",()->open(ContinueSettingsActivity.class)),
                    tr("Which apps may show what you were watching and their recommendations on the home screen."),null).icon("apps");
                add(rows,action("Restore hidden content",()->{Prefs.restoreHiddenContent();ContinueWatching.get(this).refresh(true);}),
                    tr("Shows again everything you hid from Recently watched and Recommended."),
                    r->r.value=count(Prefs.hiddenContent().size())).icon("hidden");
                break;
            case CONNECTIONS:   // AirPlay first: everything about it is on its own page; then the network
                add(rows,action("AirPlay",()->open(AirPlayActivity.class)),
                    tr("Mirror an iPhone, iPad or Mac on this TV. OK opens AirPlay's page: turn it on, install or update the receiver."),
                    r->r.value=receiverVersion.isEmpty()?tr("Not installed"):airplay.value()).icon("tv").group(tr("AirPlay"));
                SettingsPage.Row network=info(tr("Network")),address=info("IP");network.network=true;
                network.hint=tr("This TV's connection: Wi-Fi or cable, and whether it reaches the internet.");
                address.hint=tr("This TV's address on your home network.");
                add(rows,network,null,r->{
                    NetworkStatus.Snapshot n=page.networkSnapshot();r.state=n.state;r.transport=n.transport;
                    r.value=NetworkStatus.label(n);r.color=NetworkStatus.color(n);address.value=n.addresses.isEmpty()?"—":n.addresses;}).group(tr("Network"));
                add(rows,address,null,null).icon("info");
                add(rows,action("Wi-Fi settings",()->SettingsLinks.open(this,Settings.ACTION_WIFI_SETTINGS)),
                    tr("Opens the TV's Wi-Fi settings to choose or change the network."),null).icon("wifi_settings");
                add(rows,action("Bluetooth",()->SettingsLinks.openBluetooth(this)),
                    tr("Opens the TV's Bluetooth settings to pair headphones, speakers or game controllers."),null).icon("bluetooth");
                break;
            case DEVICE:   // what this device is, then its memory
                add(rows,action("Device mode",()->editor("device")),
                    tr("TV box / stick for a device plugged into a TV. Television when PureTV runs on the TV itself; it adds the TV's inputs and remote shortcuts."),
                    r->r.value=tr(DeviceProfiles.label(Prefs.deviceMode()))).icon("screen").group(tr("Device"));
                add(rows,adjust("Lightweight mode",d->Prefs.setLightweight(!Prefs.lightweight())),
                    tr("For slower TVs: no shadows or glass effects, and screensaver photos stay still. Uses less memory and processor."),r->r.set(Prefs.lightweight())).icon("bolt");
                add(rows,adjust("Voice assistant",d->{if(Prefs.setAssistantOff(!Prefs.assistantOff()))HomeMode.assistant(getApplicationContext());}),
                    tr("Off turns Google Assistant off and frees its memory (needs PureTV as the default home screen). The remote's microphone button then stops working; choosing Original as the default home screen turns it back on."),
                    r->r.set(!Prefs.assistantOff())).icon("mic");
                add(rows,info("RAM"),tr("Available / total. Values refresh when you open this screen."),
                    r->r.value=ResourceStatus.value(this,resources.ramAvailable,resources.ramTotal)).icon("memory").group(tr("Memory"));
                add(rows,info(tr("Storage")),tr("Available / total. Values refresh when you open this screen."),
                    r->r.value=ResourceStatus.value(this,resources.storageAvailable,resources.storageTotal)).icon("storage");
                add(rows,action("Free up memory",this::freeMemory),   // "Free memory" is the status metric's name
                    tr("Closes apps waiting in the background. The app on screen and AirPlay keep running; closed apps start again when you open them."),
                    r->r.value=freeStatus).icon("sparkles");
                add(rows,adjust("Automatic memory cleanup",d->Prefs.setAutoFreeMemory(!Prefs.autoFreeMemory())),
                    tr("When less than 15% of memory is left, returning to the home screen frees it, at most every 15 minutes. The app you just left starts again from the beginning."),
                    r->r.set(Prefs.autoFreeMemory())).icon("sync");
                add(rows,adjust("Optimise apps during the screensaver",d->Prefs.setOptimizeApps(!Prefs.optimizeApps())),
                    tr("Ten minutes into the screensaver, PureTV prepares the apps you installed to open and run faster, as Android itself would but rarely gets to on a TV. Pressing a key stops it. Needs PureTV as the default home screen."),
                    r->r.set(Prefs.optimizeApps())).icon("bedtime");
                break;
            case REMOTE:
                if(RemoteKeys.supported()){
                    String[] buttons=editors.remoteLabels();
                    for(int i=0;i<buttons.length;i++){
                        final int key=i;SettingsPage.Row b=new SettingsPage.Row(buttons[i],d->remotePicker(key)).icon("remote");
                        if(i==0)b.group(tr("Buttons"));rows.add(b);
                    }
                    values.add(()->editors.updateRemote(data));   // the leading rows, one per remote button
                }
                if(Prefs.television())add(rows,action("TV remote shortcuts",()->Prefs.setTvRemote(!Prefs.tvRemote())),
                    tr("On the PureTV home screen: Input opens sources, TV opens live TV, Menu opens quick settings."),r->r.set(Prefs.tvRemote())).icon("tv").group(tr("Remote"));
                SettingsPage.Row test=add(rows,action("Remote button test",()->open(RemoteTestActivity.class)),
                    tr("Press any button to see what reaches PureTV, and give a button a job."),null).icon("gamepad");
                if(!Prefs.television()&&rows.size()>1)test.group(tr("Remote"));   // no heading over a lone row
                break;
            default:   // SYSTEM: what you change first, what you do now and then, what you only read last
                add(rows,action("Default home screen",()->AppLock.confirm(this,()->editor("home"))),
                    tr("Which home screen the TV opens. To go back to the TV's own home screen, choose TV's original home screen here; PureTV stays installed and you can return to it the same way. Several PureTV features need PureTV as the home screen and say so."),
                    r->r.value=editors.homeValue()).icon("home").group(tr("General"));
                add(rows,action("Language",()->editor("language")),tr("PureTV's language. Automatic follows the TV."),
                    r->r.value=Prefs.language().equals("auto")?tr("Automatic"):Ui.languageName(Prefs.language())).icon("globe");
                add(rows,action("TV Settings",()->SettingsLinks.open(this,Settings.ACTION_SETTINGS)),
                    tr("Opens the TV's own settings: picture, sound, network and more."),null).icon("tv_settings");
                add(rows,action("Updates",()->open(UpdateActivity.class)),
                    tr("Check PureTV's GitHub release, install a newer version, and choose daily notices or automatic installs."),
                    r->{String mode=Prefs.updateMode();r.value=tr(InstallRules.AUTO.equals(mode)?"Install automatically":InstallRules.OFF.equals(mode)?"Off":"Notify");}).icon("sync");
                add(rows,action("Back up settings",()->backup(false)),
                    tr("Saves layout, shelves, shortcuts, content sources, city, theme and remote buttons. Viewing history and hidden content are not included. When PureTV is the default home screen, a copy also goes to Download/PureTV-settings.json."),
                    r->r.value=!backupStatus.isEmpty()?backupStatus:backupSaved==0?tr("Not saved yet"):date(backupSaved)).icon("backup").group(tr("Backup"));
                add(rows,action("Restore settings",()->backup(true)),tr("Replaces these settings with the backup. A newer copy in Download is used first."),
                    r->r.value=!restoreStatus.isEmpty()?restoreStatus:backupSaved==0?tr("No backup"):"").icon("restore");
                add(rows,action("Setup guide",()->WelcomeActivity.open(this)),
                    tr("Goes through the first steps again: device, layout, background, city and AirPlay."),null).icon("guide").group(tr("Help"));
                add(rows,action("Status and notices",()->editor("status")),
                    tr("What PureTV measured the last time the home screen was switched, and its recent notices. Useful when something doesn't work."),null).icon("bell");
                add(rows,info(tr("Version")),tr("The PureTV version on this TV."),r->r.value=appVersion).icon("info").group(tr("About"));
                add(rows,action("Licences",()->Credits.show(this)),
                    tr("Credits and the licences of the font, symbols, photos and other parts PureTV uses."),null).icon("document");
        }
        SettingsPage.Data nextData=new SettingsPage.Data(categories()[category],"","settings",rows.toArray(new SettingsPage.Row[0]));
        nextData.hint=category==HOME?tr("Everything on the home screen: its layout, the clock screen's photo and weather, shortcuts, and which apps are hidden or locked.")
            :category==LOOK?tr("The theme, the interface sounds and the screensaver.")
            :category==CLOCK?tr("How the time is shown and the city of the weather.")
            :category==CONTENT?tr("Only content shared by supported apps appears here. Hidden content stays hidden until you restore it.")
            :category==CONNECTIONS?tr("AirPlay, this TV's network, Wi-Fi and Bluetooth.")
            :category==DEVICE?tr("What this device is, how light PureTV runs, and its memory.")
            :category==REMOTE?tr("TV remote shortcuts work on the PureTV home screen only. Input opens sources, TV opens live TV, Menu opens quick settings. Power, volume, channels and voice stay with the system.")+"\n"+editors.remoteHint()
            :tr("The default home screen, language, updates, backups and help.");
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
        if(editors.opened()){data.crumb=tr("Settings")+" › "+categories()[category];data.footer=categories()[category];}
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
    /** Extra from HomeMode: a switch the setup guide started has finished; show the guide again (its last steps). */
    static final String RESUME_GUIDE="resume_guide";
    private boolean routeWelcome(){
        boolean resume=getIntent().getBooleanExtra(RESUME_GUIDE,false);getIntent().removeExtra(RESUME_GUIDE);
        if(!resume&&!WelcomeStore.shouldShow(this))return false;
        routingWelcome=true;WelcomeActivity.open(this);finish();return true;
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(!routeWelcome()&&page!=null)startOver();}
    /*
     * Settings opens at its start every time it is opened again, as a TV's own settings do: the Home key (or any other way
     * out the user chose) leaves it, and the next visit starts at the first category with nothing open. Pages this
     * screen opens itself (Updates, AirPlay, TV Settings…) are not a way out, so they come back to where they were.
     */
    private boolean launching,leaving;
    @Override public void startActivityForResult(Intent intent,int code,Bundle options){launching=true;super.startActivityForResult(intent,code,options);}
    @Override protected void onUserLeaveHint(){if(!launching)leaving=true;super.onUserLeaveHint();}
    @Override protected void onStart(){super.onStart();launching=false;}
    @Override protected void onStop(){super.onStop();if(leaving&&!isFinishing()&&page!=null){leaving=false;startOver();}}
    private void startOver(){
        closePreview(false);showCategory(0,true);java.util.Arrays.fill(positions,0);page.select(0);
    }
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
