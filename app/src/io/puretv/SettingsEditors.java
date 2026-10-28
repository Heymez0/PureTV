package io.puretv;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Editors use the existing category page; no separate activity or rendering loop. */
final class SettingsEditors {
    private final SettingsActivity activity;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable modePoll=this::readMode;
    private String kind="",selectedShelf="",cityMessage="",drawn="";
    private int generation,remoteKey;
    private boolean alive=true,appsLoading,shelfLoading,cityLoading,modeLoading,modeReady,pendingRender,inputsLoading,live;
    private List<HomeApps.Item> inputs;
    private long pendingAt;
    private List<String[]> apps,shelfCandidates,cities=new ArrayList<>();
    private final List<String> shelfOrder=new ArrayList<>();
    private JSONObject mode=new JSONObject(),recommendations=new JSONObject();
    private boolean hasKey;
    private TextDialog cityDialog;

    SettingsEditors(SettingsActivity activity){this.activity=activity;context=activity.getApplicationContext();}
    private String tr(String text){return Ui.tr(text);}
    boolean opened(){return !kind.isEmpty();}
    void save(Bundle out){out.putString("editor",kind);out.putInt("remote_key",remoteKey);}
    void restore(Bundle state){remoteKey=Math.max(0,Math.min(Prefs.KEYS.length-1,state.getInt("remote_key",0)));open(state.getString("editor",""));}
    void openRemote(int key){remoteKey=key;open("remote");}
    void open(String next){
        if(!next.equals("device")&&!next.equals("layout")&&!next.equals("shelf")&&!next.equals("city")&&!next.equals("language")&&!next.equals("remote")&&!next.equals("home")&&!next.equals("status")
            &&!next.equals("hidden")&&!next.equals("startup")&&!next.equals("lock"))return;
        close();kind=next;
        if(kind.equals("shelf")){shelfCandidates=null;selectedShelf="";loadShelf();}
        if(kind.equals("city")){cities=new ArrayList<>();cityMessage="";cityLoading=false;}
        if(appList())loadApps();
        if(kind.equals("startup"))loadInputs();
        render();
        if(kind.equals("city")&&activity.settingsVisible())askCity();
        if(wantsMode())readMode();
    }
    void close(){generation++;kind="";drawn="";pendingRender=false;main.removeCallbacks(modePoll);dismissCity();}
    void pause(){main.removeCallbacks(modePoll);dismissCity();}
    void destroy(){alive=false;generation++;pause();}
    void resume(){
        if(pendingRender&&opened()){pendingRender=false;render();}
        if(activity.settingsCategory()==6&&RemoteKeys.supported()){loadApps();HomeMode.ensureRemoteService(context,0);}
        if(wantsMode())readMode();
    }
    private boolean wantsMode(){return alive&&activity.settingsVisible()&&activity.settingsCategory()==7
        &&(!opened()||kind.equals("home")||kind.equals("status"));}
    private SettingsPage.Row action(String label,Runnable task){return new SettingsPage.Row(label,d->task.run(),false);}
    private SettingsPage.Data data(String title,String hint,List<SettingsPage.Row> rows){
        SettingsPage.Data data=new SettingsPage.Data(title,tr("Back returns to this category."),"settings",rows.toArray(new SettingsPage.Row[0]));
        data.hint=hint;return data;
    }
    private void render(){
        if(!alive||!opened())return;
        switch(kind){
            case "device":
                List<SettingsPage.Row> profiles=new ArrayList<>();
                for(String profile:new String[]{DeviceProfiles.BOX,DeviceProfiles.TV}){
                    SettingsPage.Row row=action(tr(DeviceProfiles.label(profile)),()->{Prefs.setDeviceMode(profile);activity.finishEditor();});
                    row.value=profile.equals(Prefs.deviceMode())?tr("In use"):"";profiles.add(row);
                }
                activity.showEditor(data(tr("Device mode"),tr("TV mode adds physical inputs and TV controls. Box / stick mode keeps the current layout. Theme, apps and viewing preferences are preserved. This does not switch the default launcher."),profiles),Prefs.television()?1:0);break;
            case "layout":
                List<SettingsPage.Row> layouts=new ArrayList<>();
                for(String value:HomeContentModes.choices(Prefs.viewingInformation())){
                    SettingsPage.Row row=action(tr(HomeContentModes.label(value)),()->chooseLayout(value));
                    row.hint=tr(!Prefs.viewingInformation()?"TV viewing information is off. Enable it in Content to use viewing layouts.":value.equals(HomeContentModes.CLOCK)?"Show the clock without reading viewing content."
                        :value.equals(HomeContentModes.RECENT)?"Show viewing progress shared by your selected apps. Recommendations stay off."
                        :"Show Recently watched and Recommended tabs. Recommendations are read only in this layout.");
                    layouts.add(row);
                }
                int selected=HomeContentModes.CLOCK.equals(Prefs.homeContentMode())?0:HomeContentModes.COMBINED.equals(Prefs.homeContentMode())?2:1;
                activity.showEditor(data(tr("Home screen layout"),tr("Choose a layout with OK. App choices are in Content settings."),layouts),selected);break;
            case "shelf":renderShelf();break;
            case "city":renderCities();break;
            case "language":renderLanguages();break;
            case "remote":renderRemote();break;
            case "home":
                List<SettingsPage.Row> choices=new ArrayList<>();
                choices.add(action(tr("Custom home"),()->chooseHome(HomeMode.FREE)));
                choices.add(action(tr("Original"),()->chooseHome(HomeMode.ORIGINAL)));
                activity.showEditor(data(tr("Default home screen"),modeHint(),choices),freeActive()?0:1);break;
            case "status":renderStatus();break;
            case "hidden":renderHidden();break;
            case "startup":renderStartup();break;
            case "lock":renderLock();break;
        }
    }
    /** Editors that list the installed apps (loaded once in the background). */
    private boolean appList(){return kind.equals("remote")||kind.equals("hidden")||kind.equals("startup")||kind.equals("lock");}

    // MARK: - Hidden apps

    private void renderHidden(){
        List<SettingsPage.Row> rows=new ArrayList<>();
        for(String pkg:Prefs.hiddenApps()){
            SettingsPage.Row row=action(appName(pkg),()->{Prefs.setAppHidden(pkg,false);renderHidden();});
            row.value=tr("Show");rows.add(row);
        }
        if(rows.isEmpty())rows.add(new SettingsPage.Row(tr("No hidden apps."),null,false));
        activity.showEditor(data(tr("Hidden apps"),tr("OK shows the app in Other apps again. Hidden apps stay installed."),rows),lockFocus(rows));
    }

    // MARK: - On TV start

    private void loadInputs(){
        if(inputs!=null||inputsLoading||!Prefs.television())return;inputsLoading=true;
        new Thread(()->{
            List<HomeApps.Item> found;
            try{found=TvInputCatalog.hdmi(context,true);}catch(RuntimeException unavailable){found=new ArrayList<>();}
            boolean hasLive;try{hasLive=HomeApps.liveTv(context)!=null;}catch(RuntimeException ignored){hasLive=false;}
            final List<HomeApps.Item> result=found;final boolean available=hasLive;
            main.post(()->{
                if(!alive)return;inputs=result;live=available;inputsLoading=false;
                if(kind.equals("startup")){if(activity.settingsVisible())renderStartup();else pendingRender=true;}
                else if(activity.settingsCategory()==0)activity.refreshSettings();
            });
        },"settings-inputs").start();
    }
    String startupValue(){
        String choice=Prefs.startup();
        if(choice.equals(FeatureRules.HOME))return tr("Home screen");
        if(choice.equals(FeatureRules.LIVE))return tr("Live TV");
        if(choice.startsWith(FeatureRules.APP)){loadAppsQuietly();return appName(choice.substring(FeatureRules.APP.length()));}
        String key=choice.substring(FeatureRules.INPUT.length());
        if(inputs!=null)for(HomeApps.Item input:inputs)if(key.equals(input.key))return input.label;
        loadInputs();return tr("TV input");
    }
    private void loadAppsQuietly(){if(apps==null)loadApps();}
    private void renderStartup(){
        List<SettingsPage.Row> rows=new ArrayList<>();String choice=Prefs.startup();int selected=0;
        rows.add(startupRow(tr("Home screen"),FeatureRules.HOME,choice));
        if(Prefs.television()){
            if(live)rows.add(startupRow(tr("Live TV"),FeatureRules.LIVE,choice));
            if(inputs!=null)for(HomeApps.Item input:inputs)rows.add(startupRow(input.label,FeatureRules.INPUT+input.key,choice));
        }
        if(apps==null)rows.add(new SettingsPage.Row(tr("Checking…"),null,false));
        else for(String[] app:apps)if(!app[0].isEmpty())rows.add(startupRow(app[1],FeatureRules.APP+app[0],choice));
        for(int i=0;i<rows.size();i++)if(tr("In use").equals(rows.get(i).value)){selected=i;break;}
        activity.showEditor(data(tr("On TV start"),tr("Opens once, in the first minutes after the TV starts. Back returns to the home screen."),rows),selected);
    }
    private SettingsPage.Row startupRow(String label,String value,String choice){
        SettingsPage.Row row=action(label,()->{Prefs.setStartup(value);StartupLaunch.markCurrentStart(context);activity.finishEditor();});
        row.value=value.equals(choice)?tr("In use"):"";return row;
    }

    // MARK: - App lock

    private void renderLock(){
        List<SettingsPage.Row> rows=new ArrayList<>();
        if(!Prefs.lockSet()){
            rows.add(action(tr("Set a PIN"),()->AppLock.choosePin(activity,this::lockChanged)));
            activity.showEditor(data(tr("App lock"),tr("Choose a PIN first, then the apps to lock. A simple household lock: apps opened another way (TV Settings, the remote's app buttons) are not locked."),rows),0);
            return;
        }
        SettingsPage.Row hours=new SettingsPage.Row(tr("Locked hours"),d->{
            String[] all=FeatureRules.HOURS;int i=java.util.Arrays.asList(all).indexOf(Prefs.lockHours());
            Prefs.setLockHours(all[(i+(d==0?1:d)+all.length)%all.length]);renderLock();
        },true);
        hours.value=AppLock.hoursLabel(Prefs.lockHours());rows.add(hours);
        rows.add(action(tr("Change PIN"),()->AppLock.choosePin(activity,this::lockChanged)));
        rows.add(action(tr("Turn off app lock"),()->{Prefs.clearLock();activity.finishEditor();}));
        if(apps==null)rows.add(new SettingsPage.Row(tr("Checking…"),null,false));
        else for(String[] app:apps){
            if(app[0].isEmpty())continue;final String pkg=app[0];
            SettingsPage.Row row=action(app[1],()->{Prefs.setAppLocked(pkg,!Prefs.lockedApps().contains(pkg));renderLock();});
            row.value=Prefs.lockedApps().contains(pkg)?tr("Locked"):"";rows.add(row);
        }
        activity.showEditor(data(tr("App lock"),tr("OK locks or unlocks an app. Locked apps ask for the PIN when opened from PureTV."),rows),lockFocus(rows));
    }
    /** After the PIN dialogs: drawn again only if the editor is still open and visible. */
    private void lockChanged(){if(alive&&kind.equals("lock")){if(activity.settingsVisible())renderLock();else pendingRender=true;}}
    /** Keeps the focus on the row just changed when the list is drawn again. */
    private int lockFocus(List<SettingsPage.Row> rows){
        int focus=kind.equals(drawn)?activity.editorFocus():0;drawn=kind;
        return Math.max(0,Math.min(rows.size()-1,focus));
    }
    void update(SettingsPage.Data data){
        if(kind.equals("layout")){
            String mode=Prefs.homeContentMode();String[] modes=HomeContentModes.choices(Prefs.viewingInformation());
            for(int i=0;i<modes.length;i++)data.rows[i].value=modes[i].equals(mode)?tr("In use"):"";
        }else if(kind.equals("home")){
            String current=mode.optString("mode",HomeMode.ORIGINAL);
            data.rows[0].value=modeReady&&freeActive()?tr("In use"):"";
            data.rows[1].value=modeReady&&HomeMode.ORIGINAL.equals(current)?tr("In use"):"";
            data.hint=modeHint();
        }else if(kind.equals("status"))updateStatus(data);
    }
    private void chooseLayout(String value){
        if(!value.equals(Prefs.homeContentMode())){
            Prefs.setHomeContentMode(value);
            ContinueWatching source=ContinueWatching.get(context);
            if(HomeContentModes.CLOCK.equals(value)){source.setVisible(java.util.Collections.emptyList());source.trim();}
            source.refresh(true);
        }
        activity.finishEditor();
    }

    private void loadShelf(){
        if(shelfLoading)return;shelfLoading=true;
        new Thread(()->{
            List<String[]> found=new ArrayList<>();List<String> selected=new ArrayList<>();
            try{
                found=HomeApps.shelfCandidates(context);
                for(String pkg:HomeApps.shelfPackages(context))if(HomeApps.launchIntent(context.getPackageManager(),pkg)!=null)selected.add(pkg);
            }catch(RuntimeException ignored){}
            final List<String[]> result=found;final List<String> order=selected;
            main.post(()->{
                if(!alive)return;shelfLoading=false;shelfCandidates=result;shelfOrder.clear();shelfOrder.addAll(order);
                if(kind.equals("shelf")){if(activity.settingsVisible())renderShelf();else pendingRender=true;}
            });
        },"settings-shelf").start();
    }
    private void renderShelf(){
        List<SettingsPage.Row> rows=new ArrayList<>();int selected=0;
        if(shelfCandidates==null)rows.add(new SettingsPage.Row(tr("Checking…"),null,false));
        else{
            List<String[]> sorted=new ArrayList<>();
            for(String pkg:shelfOrder)for(String[] app:shelfCandidates)if(pkg.equals(app[0])){sorted.add(app);break;}
            for(String[] app:shelfCandidates)if(!shelfOrder.contains(app[0]))sorted.add(app);
            for(String[] app:sorted){
                final String pkg=app[0];
                SettingsPage.Row row=new SettingsPage.Row(app[1],d->toggleShelf(pkg),true);
                int place=shelfOrder.indexOf(pkg);
                row.value=place<0?"—":Integer.toString(place+1);
                if(pkg.equals(selectedShelf))selected=rows.size();rows.add(row);
            }
            if(rows.isEmpty())rows.add(new SettingsPage.Row(tr("No apps found."),null,false));
        }
        activity.showEditor(data(tr("Home screen apps"),tr("OK adds or removes an app. To reorder, hold OK on the home shelf and choose Move on shelf."),rows),selected);
    }
    private void toggleShelf(String pkg){
        if(shelfOrder.contains(pkg))shelfOrder.remove(pkg);
        else if(shelfOrder.size()<HomeApps.MAX_SHELF)shelfOrder.add(pkg);
        else{activity.getWindow().getDecorView().announceForAccessibility(tr("The home shelf is full."));return;}
        selectedShelf=pkg;Prefs.setShelf(shelfOrder);renderShelf();
    }

    private void renderLanguages(){
        List<SettingsPage.Row> rows=new ArrayList<>();List<String> codes=new ArrayList<>();codes.add("auto");Collections.addAll(codes,Ui.languages());
        int selected=0;
        for(String code:codes){
            SettingsPage.Row row=action(code.equals("auto")?tr("Automatic"):Ui.languageName(code),()->{
                if(!code.equals(Prefs.language()))Prefs.setLanguage(code);activity.finishEditor();
            });
            if(code.equals(Prefs.language())){row.value=tr("In use");selected=rows.size();}rows.add(row);
        }
        activity.showEditor(data(tr("Language"),tr("Choose a language. Back keeps the current setting."),rows),selected);
    }
    private void renderCities(){
        List<SettingsPage.Row> rows=new ArrayList<>();
        for(String[] city:cities)rows.add(action(city[0],()->{
            if(!city[1].equals(Prefs.city())||!city[2].equals(Prefs.latitude())||!city[3].equals(Prefs.longitude())){
                Prefs.setCity(city[1],city[2],city[3]);HomeData.get(context).settingsChanged();
            }
            activity.finishEditor();
        }));
        if(rows.isEmpty()){
            SettingsPage.Row current=new SettingsPage.Row(tr("City"),null,false);current.value=Prefs.city();rows.add(current);
        }
        rows.add(action(tr("Search"),this::askCity));
        activity.showEditor(data(tr("City"),cityLoading?tr("Searching…"):cityMessage.isEmpty()?tr("Choose a city or search again. Back keeps the current city."):cityMessage,rows),0);
    }
    private void dismissCity(){if(cityDialog!=null){cityDialog.dismiss();cityDialog=null;}}
    private void askCity(){
        if(cityLoading||cityDialog!=null||!activity.settingsVisible())return;
        // The settings popup style; the keyboard's Search key searches, Back cancels.
        TextDialog dialog=new TextDialog(activity,tr("City"),tr("Type the city's name"),EditorInfo.IME_ACTION_SEARCH,120,this::searchCity);
        cityDialog=dialog;dialog.setOnDismissListener(d->{if(cityDialog==dialog)cityDialog=null;});dialog.show();
    }
    private void searchCity(String query){
        String name=query.trim();if(name.isEmpty()||cityLoading||!kind.equals("city"))return;
        cityLoading=true;cityMessage="";final int token=++generation;renderCities();
        new Thread(()->{
            List<String[]> found;String message;
            try{found=HomeData.findCity(name);message=found.isEmpty()?tr("No city with that name."):"";}
            catch(Exception e){found=new ArrayList<>();message=tr("Could not search for a city. Try again.");}
            final List<String[]> result=found;final String error=message;
            main.post(()->{
                if(!alive||token!=generation||!kind.equals("city"))return;
                cityLoading=false;cities=result;cityMessage=error;
                if(activity.settingsVisible())renderCities();else pendingRender=true;
            });
        },"settings-city").start();
    }

    String[] remoteLabels(){return RemoteKeys.VESTEL?new String[]{Ui.tr("Botão Netflix","Netflix button")}:new String[]{
        Ui.tr("Botão Netflix","Netflix button"),Ui.tr("Botão YouTube","YouTube button"),
        Ui.tr("Botão Prime Video","Prime Video button"),Ui.tr("Botão TCL Channel","TCL Channel button")};}
    String remoteHint(){
        return RemoteKeys.VESTEL?Ui.tr("YouTube, Prime Video e Google Play: a própria TV controla esses botões.","YouTube, Prime Video and Google Play: the TV itself controls those buttons.")
            :RemoteKeys.supported()?Ui.tr("Disney e Globoplay: a própria TV controla esses botões.","Disney and Globoplay: the TV itself controls those buttons.")
            :Ui.tr("App buttons are managed by your TV firmware. Use Remote button test to check events delivered to PureTV.");
    }
    private String appName(String pkg){
        if(pkg.isEmpty())return tr("Nothing");if(apps==null)return tr("Checking…");
        for(String[] app:apps)if(pkg.equals(app[0]))return app[1];return tr("Not installed");
    }
    void updateRemote(SettingsPage.Data data){for(int i=0;i<data.rows.length&&i<Prefs.KEYS.length;i++)data.rows[i].value=appName(Prefs.keyApp(Prefs.KEYS[i]));}
    private void loadApps(){
        if(apps!=null||appsLoading)return;appsLoading=true;
        new Thread(()->{
            List<String[]> found=new ArrayList<>();
            try{
                for(String[] app:HomeApps.launchable(context))if(!app[0].equals(context.getPackageName()))found.add(app);
                Collections.sort(found,(a,b)->a[1].toLowerCase(Locale.ROOT).compareTo(b[1].toLowerCase(Locale.ROOT)));
            }catch(RuntimeException ignored){}
            found.add(0,new String[]{"",tr("Nothing")});final List<String[]> result=found;
            main.post(()->{
                if(!alive)return;apps=result;appsLoading=false;
                if(appList()){if(activity.settingsVisible())render();else pendingRender=true;}
                else if(activity.settingsCategory()==6||activity.settingsCategory()==0)activity.refreshSettings();
            });
        },"settings-remote-apps").start();
    }
    private void renderRemote(){
        List<SettingsPage.Row> rows=new ArrayList<>();int selected=0;
        if(apps==null)rows.add(new SettingsPage.Row(tr("Checking…"),null,false));
        else for(String[] app:apps){
            final String pkg=app[0];SettingsPage.Row row=action(app[1],()->{
                if(!pkg.equals(Prefs.keyApp(Prefs.KEYS[remoteKey])))Prefs.setKeyApp(Prefs.KEYS[remoteKey],pkg);activity.finishEditor();
            });
            if(pkg.equals(Prefs.keyApp(Prefs.KEYS[remoteKey]))){row.value=tr("In use");selected=rows.size();}rows.add(row);
        }
        activity.showEditor(data(remoteLabels()[remoteKey],remoteHint(),rows),selected);
    }

    private String busyText(){
        long at=mode.optLong("busyAt"),now=SystemClock.elapsedRealtime();
        return now<at||now-at>10*60_000L?"":mode.optString("busy");
    }
    private boolean busy(){return !busyText().isEmpty()||pendingAt!=0&&SystemClock.uptimeMillis()-pendingAt<10_000;}
    private boolean freeActive(){return HomeMode.FREE.equals(mode.optString("mode"))&&!mode.optBoolean("homeReverted");}
    String homeValue(){return !modeReady?tr("Checking…"):busy()?busyText().isEmpty()?tr("Starting…"):busyText():tr(freeActive()?"Custom home":"Original");}
    private String modeHint(){
        if(!modeReady)return tr("Checking…");
        String text=Ui.tr("A troca leva uns segundos e a tela pisca nas medições.","Switching takes a few seconds; the screen blinks while it measures.");
        if(busy())return homeValue()+"\n\n"+text;
        String error=mode.optString("error");return error.isEmpty()?text:error+"\n\n"+text;
    }
    private void chooseHome(String wanted){
        if(!modeReady||busy())return;
        boolean already=HomeMode.FREE.equals(wanted)?freeActive():HomeMode.ORIGINAL.equals(mode.optString("mode",HomeMode.ORIGINAL));
        if(already)return;
        HomeMode.request(context,wanted);pendingAt=SystemClock.uptimeMillis();activity.refreshSettings();readMode();
    }
    private void readMode(){
        if(modeLoading||!wantsMode())return;modeLoading=true;main.removeCallbacks(modePoll);
        new Thread(()->{
            JSONObject state=HomeMode.state(context),recs=Recs.status(context);boolean key=Recs.hasKey(context);
            main.post(()->{
                if(!alive)return;modeLoading=false;boolean wasBusy=busy();mode=state;recommendations=recs;hasKey=key;modeReady=true;
                if(!busyText().isEmpty())pendingAt=0;
                if(!wantsMode())return;
                activity.refreshSettings();
                if(wasBusy&&!busy())activity.getWindow().getDecorView().announceForAccessibility(homeValue());
                if(busy())main.postDelayed(modePoll,500);
            });
        },"settings-mode-state").start();
    }
    private String metric(JSONObject object,String name){return object==null||!object.has(name)?"—":object.optLong(name)+(name.endsWith("MB")?" MB":" ms");}
    private void renderStatus(){
        List<SettingsPage.Row> rows=new ArrayList<>();
        for(String label:new String[]{"Default home screen","Home button","Free memory","Home screen memory","Recommendations","Notices","Last error"})
            rows.add(new SettingsPage.Row(tr(label),null,false));
        activity.showEditor(data(tr("Status and notices"),tr("Stored measurements only. Opening this screen does not run a benchmark."),rows),0);
    }
    private void updateStatus(SettingsPage.Data data){
        data.rows[0].value=homeValue();JSONObject metrics=mode.optJSONObject("metrics");
        JSONObject original=metrics==null?null:metrics.optJSONObject(HomeMode.ORIGINAL),custom=metrics==null?null:metrics.optJSONObject(HomeMode.FREE);
        String[] names={"homeMs","memAvailableMB","homePssMB"};
        for(int i=0;i<3;i++){
            data.rows[i+1].value=metric(original,names[i])+" / "+metric(custom,names[i]);
            data.rows[i+1].hint=tr("Original")+" / "+tr("Custom home")+"\n"+tr("Stored measurements only. Opening this screen does not run a benchmark.");
        }
        String recsError=recommendations.optString("error");
        data.rows[4].value=!hasKey?tr("Not configured"):!recsError.isEmpty()?tr("Error"):recommendations.optInt("count")+" "+tr("titles");
        data.rows[4].hint=(recsError.isEmpty()?"":recsError+"\n\n")+Ui.tr("Usa a API do TMDB, sem endosso ou certificação do TMDB.","This product uses the TMDB API but is not endorsed or certified by TMDB.");
        JSONArray notices=mode.optJSONArray("notices");StringBuilder lines=new StringBuilder();
        for(int i=0;notices!=null&&i<Math.min(2,notices.length());i++){
            JSONObject item=notices.optJSONObject(i);if(item==null)continue;
            if(lines.length()>0)lines.append("\n\n");lines.append(item.optString("text"));
        }
        data.rows[5].value=notices==null||notices.length()==0?tr("None yet."):Integer.toString(notices.length());
        data.rows[5].hint=lines.length()==0?tr("None yet."):lines.toString();
        String error=mode.optString("error");data.rows[6].value=error.isEmpty()?tr("None yet."):tr("Error");data.rows[6].hint=error;
    }
}
