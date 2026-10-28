package io.puretv;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only Watch Next. No account access, provider writes, telemetry, service or persistent history cache. */
final class ContinueWatching {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    interface Listener { void onContinueWatching(); }
    static final class Program {
        final long id, at, position, duration;
        final int nextType;
        final boolean recommended;
        final String pkg, app, title, episode, art, intent, hiddenKey;
        Program(long id, long at, long position, long duration, int nextType, String pkg, String app,
                String title, String episode, String art, String intent) {
            this(id,at,position,duration,nextType,pkg,app,title,episode,art,intent,false);
        }
        Program(long id,long at,long position,long duration,int nextType,String pkg,String app,
                String title,String episode,String art,String intent,boolean recommended) {
            this.recommended=recommended;
            this.id=id; this.at=at; this.position=Math.max(0,position); this.duration=Math.max(0,duration);
            this.nextType=nextType; this.pkg=pkg; this.app=app; this.title=title; this.episode=episode;
            this.art=art; this.intent=intent;
            hiddenKey=HiddenContent.key(pkg,intent);

        }
        boolean same(Program other) {
            return other!=null && id==other.id && at==other.at && position==other.position && duration==other.duration
                && nextType==other.nextType && recommended==other.recommended && pkg.equals(other.pkg) && app.equals(other.app)
                && title.equals(other.title) && episode.equals(other.episode) && art.equals(other.art) && intent.equals(other.intent);
        }
        String meta() {
            String text=app;
            if (!episode.isEmpty() && !episode.equals(title)) text += " · " + (episode.matches("(?i)Episode [0-9]+") ? Ui.tr("Episódio ", "Episode ") + episode.substring(8) : episode);
            if (nextType == 1) text += " · " + Ui.tr("Próximo episódio", "Next episode");
            else if (duration > position && position > 0) text += " · " + ((duration-position-1)/60_000+1)
                    + " " + Ui.tr("min restantes", "min left");
            return text;
        }
    }
    static final class Source {
        final String pkg, label;
        final int count;
        Source(String pkg,String label,int count) {this.pkg=pkg;this.label=label;this.count=count;}
    }
    static final class Snapshot {
        final List<Program> programs,recommendations;
        final List<Source> sources;
        final boolean permission, failed;
        Snapshot(List<Program> programs,List<Source> sources,boolean permission,boolean failed) {
            this(programs,new ArrayList<>(),sources,permission,failed);
        }
        Snapshot(List<Program> programs,List<Program> recommendations,List<Source> sources,boolean permission,boolean failed) {
            this.recommendations=Collections.unmodifiableList(recommendations);
            this.programs=Collections.unmodifiableList(programs);this.sources=Collections.unmodifiableList(sources);
            this.permission=permission;this.failed=failed;
        }
        boolean sameData(Snapshot other) {
            if(permission!=other.permission || failed!=other.failed || programs.size()!=other.programs.size()
                    || recommendations.size()!=other.recommendations.size() || sources.size()!=other.sources.size())return false;
            for(int i=0;i<programs.size();i++)if(!programs.get(i).same(other.programs.get(i)))return false;
            for(int i=0;i<recommendations.size();i++)if(!recommendations.get(i).same(other.recommendations.get(i)))return false;
            for(int i=0;i<sources.size();i++) {
                Source a=sources.get(i),b=other.sources.get(i);
                if(a.count!=b.count || !a.pkg.equals(b.pkg) || !a.label.equals(b.label))return false;
            }
            return true;
        }
    }
    private static final Set<String> VIDEO = new HashSet<>(Arrays.asList(
            "com.netflix.ninja","com.google.android.youtube.tv","com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus","com.wbd.stream","com.hbo.hbonow","com.apple.atve.androidtv.appletv",
            "com.apple.atve.sony.appletv","org.jellyfin.androidtv","org.videolan.vlc","org.xbmc.kodi",
            "com.stremio.one","com.turkcell.ott","com.digiturk.beinconnect","com.digiturk.tod","com.beonetv.tod",
            "com.cbs.ott","com.hulu.livingroomplus","com.peacocktv.peacockandroid","com.globo.globotv",
            "com.crunchyroll.crunchyroid","in.startv.hotstar","ru.more.play","net.cme.voyo.cz.tvapp"));
    private static final String[] COLUMNS={"_id","package_name","title","episode_title","poster_art_uri",
            "thumbnail_uri","intent_uri","last_engagement_time_utc_millis","last_playback_position_millis",
            "duration_millis","watch_next_type","browsable","type"};
    private static ContinueWatching instance;
    static synchronized ContinueWatching get(Context c) {
        if(instance==null)instance=new ContinueWatching(c.getApplicationContext());return instance;
    }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService artIo=Executors.newSingleThreadExecutor();
    private final List<Listener> listeners=new ArrayList<>();
    static final long RETAIN_BYTES=16L*1024*1024;
    static final long DUAL_RETAIN_BYTES=32L*1024*1024;
    private final PosterCache<Backdrop> images=new PosterCache<>(6);
    private final Set<String> pending=new HashSet<>(), failedImages=new HashSet<>();
    private final PosterPlan<Program> wantedArt=new PosterPlan<>(p->p.art);
    private volatile Snapshot snapshot=new Snapshot(new ArrayList<>(),new ArrayList<>(),false,false);
    private boolean observing,observingRecommendations;
    private final RefreshGate loads=new RefreshGate();
    private int artGeneration;
    private boolean artActive;
    private long artLoads,artPublished,artDiscarded;
    private volatile int artVersion;
    private long loadedAt;
    private int loadedVersion=-1;
    private int loadedContentVersion=-1;
    private final Runnable reload=()->refresh(true);
    private void sourceChanged() {
        if(listeners.isEmpty())return;
        loads.invalidate();main.removeCallbacks(reload);main.postDelayed(reload,500);
    }
    private final ContentObserver observer=new ContentObserver(main) {
        @Override public void onChange(boolean selfChange){sourceChanged();}
    };
    private final ContentObserver recommendationObserver=new ContentObserver(main) {
        @Override public void onChange(boolean selfChange){sourceChanged();}
    };
    private ContinueWatching(Context c) {context=c;}
    /** Android kills the process when a runtime permission is revoked, so a grant seen once holds for this process. */
    private static volatile boolean granted;
    static boolean permitted(Context c) {
        if(granted)return true;
        boolean now=c.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED;   // an IPC on Android 9
        if(now)granted=true;
        return now;
    }
    Snapshot snapshot() {return snapshot;}
    void addListener(Listener l) {
        boolean first=listeners.isEmpty();if(!listeners.contains(l))listeners.add(l);refresh(first);
    }
    void removeListener(Listener l) {
        listeners.remove(l);
        if(listeners.isEmpty()) {stopObserving();main.removeCallbacks(reload);loads.invalidate();suspendArt();}
    }
    private void observe() {
        if(!Prefs.continueWatching() || !permitted(context) || listeners.isEmpty()){stopObserving();return;}
        if(!observing)try {
            context.getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI,true,observer);observing=true;
        }catch(RuntimeException ignored){}
        if(Prefs.appRecommendations() && !observingRecommendations)try {
            context.getContentResolver().registerContentObserver(TvContract.PreviewPrograms.CONTENT_URI,true,recommendationObserver);
            context.getContentResolver().registerContentObserver(TvContract.Channels.CONTENT_URI,true,recommendationObserver);
            observingRecommendations=true;
        }catch(RuntimeException ignored){unregister(recommendationObserver);}
        if(!Prefs.appRecommendations() && observingRecommendations){unregister(recommendationObserver);observingRecommendations=false;}
    }
    private void unregister(ContentObserver value){try{context.getContentResolver().unregisterContentObserver(value);}catch(RuntimeException ignored){}}
    private void stopObserving() {
        if(observing)unregister(observer);
        if(observingRecommendations)unregister(recommendationObserver);
        observing=observingRecommendations=false;
    }
    void refresh(boolean force) {
        observe();
        pruneArt();
        if(!Prefs.continueWatching() && !snapshot.programs.isEmpty() || !Prefs.appRecommendations() && !snapshot.recommendations.isEmpty())
            snapshot=new Snapshot(Prefs.continueWatching()?snapshot.programs:new ArrayList<>(),snapshot.sources,permitted(context),false);
        if(listeners.isEmpty())return;
        long interval=observing && (!Prefs.appRecommendations() || observingRecommendations) && !snapshot.failed?5*60_000L:30_000L;
        if(!force && loadedVersion==Prefs.version && loadedContentVersion==Prefs.contentVersion && snapshot.permission==permitted(context)
                && SystemClock.elapsedRealtime()-loadedAt<interval)return;
        final long token=loads.begin(force);if(token<0)return;main.removeCallbacks(reload);
        final int version=Prefs.version;
        final int contentVersion=Prefs.contentVersion;
        final boolean enabled=Prefs.continueWatching(),recommendations=Prefs.appRecommendations(),permission=permitted(context);
        final Set<String> hidden=Prefs.continueHidden();
        final Set<String> hiddenPrograms=Prefs.hiddenContent();
        io.execute(()->{
            Snapshot result;
            try {result=read(enabled,recommendations,permission,hidden,hiddenPrograms);}
            catch(Exception ignored) {result=new Snapshot(new ArrayList<>(),new ArrayList<>(),permission,true);}
            final Snapshot ready=result;
            main.post(()->{
                boolean current=loads.complete(token);
                if(listeners.isEmpty())return;
                if(!current || version!=Prefs.version || contentVersion!=Prefs.contentVersion || permission!=permitted(context)) {refresh(true);return;}
                loadedAt=SystemClock.elapsedRealtime();loadedVersion=version;loadedContentVersion=contentVersion;
                if(snapshot.sameData(ready))return;
                snapshot=ready;pruneArt();
                for(Listener l:new ArrayList<>(listeners))l.onContinueWatching();
            });
        });
    }
    /** Launchable apps for one read. A label loads the app's resources, so only shown or counted apps get one. */
    private static final class Installed {
        private final Map<String,ResolveInfo> apps=new LinkedHashMap<>();
        private final Map<String,String> labels=new HashMap<>();
        private final PackageManager pm;
        Installed(Context c) {
            pm=c.getPackageManager();
            for(Map.Entry<String,ResolveInfo> app:HomeApps.launchers(c).entrySet())
                if(WatchNextRules.packageName(app.getKey()))apps.put(app.getKey(),app.getValue());
        }
        boolean containsKey(String pkg) {return apps.containsKey(pkg);}
        Set<String> packages() {return apps.keySet();}
        String get(String pkg) {
            String label=labels.get(pkg);
            if(label==null){ResolveInfo app=apps.get(pkg);if(app==null)return null;label=String.valueOf(app.loadLabel(pm));labels.put(pkg,label);}
            return label;
        }
    }
    private Snapshot read(boolean enabled,boolean recommendations,boolean permission,Set<String> hidden,Set<String> hiddenPrograms) {
        Installed installed=new Installed(context);
        Map<String,Integer> counts=new HashMap<>();
        List<Program> programs=new ArrayList<>();
        Set<String> seen=new HashSet<>();boolean failed=false;
        if(enabled && permission)try(Cursor c=context.getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI,COLUMNS,null,null,"last_engagement_time_utc_millis DESC")) {
            if(c!=null)for(int scanned=0;scanned<WatchNextRules.SCAN_LIMIT && c.moveToNext();scanned++) {
                String pkg=value(c,"package_name",255);
                if(!installed.containsKey(pkg) || !WatchNextRules.packageName(pkg))continue;
                int type=(int)number(c,"watch_next_type",-1);
                if(!WatchNextRules.recent(type,number(c,"browsable",1)!=0,
                        number(c,"last_engagement_time_utc_millis",0),System.currentTimeMillis()))continue;
                // Only video programs. Music/playlist/station rows are not viewing history.
                long mediaType=number(c,"type",0);
                if(mediaType<0 || mediaType>=7)continue;
                counts.put(pkg,counts.getOrDefault(pkg,0)+1);
                if(hidden.contains(pkg) || programs.size()>=WatchNextRules.ITEM_LIMIT)continue;
                String title=value(c,"title",512),intent=value(c,"intent_uri",8192);
                if(title.isEmpty() || intent.isEmpty() || !seen.add(pkg+"|"+intent))continue;
                String art=value(c,"thumbnail_uri",8192);
                if(art.isEmpty())art=value(c,"poster_art_uri",8192);
                Program program=new Program(number(c,"_id",0),number(c,"last_engagement_time_utc_millis",0),
                        number(c,"last_playback_position_millis",0),number(c,"duration_millis",0),type,pkg,installed.get(pkg),
                        title,value(c,"episode_title",512),art,intent);
                if(!hiddenPrograms.contains(program.hiddenKey))programs.add(program);
            }
        } catch(SecurityException|IllegalArgumentException|android.database.SQLException ignored) {failed=true;}
        List<Program> previews=new ArrayList<>();
        if(recommendations && permission)try {previews=readPreviews(installed,hidden,hiddenPrograms,seen,counts);}
        catch(SecurityException|IllegalArgumentException|android.database.SQLException ignored) {failed=true;}
        List<Source> sources=new ArrayList<>();
        for(String pkg:installed.packages())if(VIDEO.contains(pkg) || counts.containsKey(pkg))
            sources.add(new Source(pkg,installed.get(pkg),counts.getOrDefault(pkg,0)));
        sources.sort((a,b)->a.label.compareToIgnoreCase(b.label));
        return new Snapshot(programs,previews,sources,permission,failed);
    }
    /** Read shared video previews for this launcher's selected apps. No provider writes or privileged permission.
     * Channel browsable belongs to the original launcher; it is commonly 0 while that launcher is disabled.
     * Respect our per-app choices, locked channels and individually hidden preview programs instead. */
    private List<Program> readPreviews(Installed installed,Set<String> hidden,Set<String> hiddenPrograms,
            Set<String> seen,Map<String,Integer> counts) {
        Set<String> channels=new HashSet<>();
        try(Cursor c=context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id","package_name","type","locked"},null,null,null)) {
            if(c!=null)for(int scanned=0;scanned<96 && c.moveToNext();scanned++) {
                String pkg=value(c,"package_name",255);
                if(installed.containsKey(pkg) && VIDEO.contains(pkg) && "TYPE_PREVIEW".equals(value(c,"type",64))
                        && number(c,"locked",0)==0)
                    channels.add(pkg+":"+number(c,"_id",-1));
            }
        }
        List<Program> result=new ArrayList<>();Map<String,Integer> perApp=new HashMap<>();
        if(channels.isEmpty())return result;
        try(Cursor c=context.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI,
                new String[]{"_id","package_name","channel_id","title","episode_title","poster_art_uri",
                    "thumbnail_uri","intent_uri","type","browsable","live"},null,null,"weight DESC")) {
            if(c!=null)for(int scanned=0;scanned<240 && result.size()<24 && c.moveToNext();scanned++) {
                String pkg=value(c,"package_name",255);long type=number(c,"type",-1);
                if(!installed.containsKey(pkg) || !WatchNextRules.packageName(pkg) || hidden.contains(pkg)
                        || !channels.contains(pkg+":"+number(c,"channel_id",-1)) || type<0 || type>5
                        || number(c,"browsable",1)==0 || number(c,"live",0)!=0 || perApp.getOrDefault(pkg,0)>=6)continue;
                String title=value(c,"title",512),intent=value(c,"intent_uri",8192);
                if(title.isEmpty() || intent.isEmpty() || !seen.add(pkg+"|"+intent))continue;
                String art=value(c,"thumbnail_uri",8192);if(art.isEmpty())art=value(c,"poster_art_uri",8192);
                Program program=new Program(number(c,"_id",0),0,0,0,-1,pkg,installed.get(pkg),title,
                        value(c,"episode_title",512),art,intent,true);
                if(hiddenPrograms.contains(program.hiddenKey))continue;
                result.add(program);perApp.put(pkg,perApp.getOrDefault(pkg,0)+1);
                counts.put(pkg,counts.getOrDefault(pkg,0)+1);
            }
        }
        return result;
    }
    private static String value(Cursor c,String key,int max) {int at=c.getColumnIndex(key);return at<0 || c.isNull(at)?"":WatchNextRules.text(c.getString(at),max);}
    private static long number(Cursor c,String key,long fallback) {int at=c.getColumnIndex(key);return at<0 || c.isNull(at)?fallback:c.getLong(at);}
    int artVersion(){return artVersion;}
    void resumeArt(){synchronized(images){artActive=true;failedImages.clear();}}
    void suspendArt(){
        synchronized(images){
            if(!artActive)return;
            artActive=false;artGeneration++;
            int before=images.size();images.retain(wantedArt.keys());
            // If a small retention budget is ever needed, keep the visible window last in the LRU.
            for(String art:wantedArt.activeKeys())images.get(art);
            images.trimTo(retainedLimit());
            if(images.size()!=before)artVersion++;
        }
    }
    private void pruneArt(){
        if(!Prefs.continueWatching()||!permitted(context)){setVisible(Collections.emptyList());trim();return;}
        Set<String> hidden=Prefs.continueHidden(),hiddenPrograms=Prefs.hiddenContent(),allowed=new HashSet<>();
        for(Program p:snapshot.programs)if(!hidden.contains(p.pkg)&&!hiddenPrograms.contains(p.hiddenKey))allowed.add(p.art);
        if(Prefs.appRecommendations())for(Program p:snapshot.recommendations)
            if(!hidden.contains(p.pkg)&&!hiddenPrograms.contains(p.hiddenKey))allowed.add(p.art);
        synchronized(images){
            int before=images.size();images.retain(allowed);
            wantedArt.retain(allowed);failedImages.retainAll(wantedArt.keys());
            if(images.size()!=before)artVersion++;
        }
    }
    void setVisible(List<Program> visible) {
        setWindows(visible,Collections.emptyList());
    }
    void setWindows(List<Program> visible,List<Program> standby){
        synchronized(images) {
            wantedArt.set(visible,standby);failedImages.retainAll(wantedArt.keys());
            int before=images.size();images.retain(wantedArt.keys());
            if(images.size()!=before)artVersion++;
        }
        prepareWanted();
    }
    private long retainedLimit(){return wantedArt.size()>3?DUAL_RETAIN_BYTES:RETAIN_BYTES;}
    private boolean currentArt(String key,int generation,Object slot){
        return artActive&&generation==artGeneration&&wantedArt.matches(key,slot);
    }
    /** Event-driven and serialized: the visible window is queued before the other tab. */
    private void prepareWanted(){
        List<Program> order;
        synchronized(images){if(!artActive)return;order=new ArrayList<>(wantedArt.values());}
        for(Program p:order)backdrop(p);
    }
    Backdrop backdrop(Program p) {
        if(p==null || p.art.isEmpty())return null;
        final int token;
        final Object slot;
        synchronized(images) {
            Backdrop ready=images.get(p.art);if(ready!=null)return ready;
            slot=wantedArt.token(p.art);
            if(!artActive || slot==null || pending.size()>=3 || pending.contains(p.art) || failedImages.contains(p.art))return null;
            pending.add(p.art);token=artGeneration;artLoads++;
        }
        artIo.execute(()->{
            boolean skipped;
            synchronized(images){skipped=!currentArt(p.art,token,slot);}
            Backdrop ready=null;Bitmap decoded=null;
            try{if(!skipped){
                decoded=decode(p.art);boolean wanted;
                synchronized(images){wanted=currentArt(p.art,token,slot);}
                if(decoded!=null && wanted && Prefs.continueAllowed(p.pkg) && (!p.recommended || Prefs.appRecommendations()))ready=Backdrop.make(decoded,true);
            }}catch(OutOfMemoryError lowMemory){
                synchronized(images){artActive=false;images.clear();artGeneration++;artVersion++;if(failedImages.size()>=24)failedImages.clear();failedImages.add(p.art);}
                AppLog.w("Poster omitted after memory pressure");
            }catch(Exception ignored){}
            finally{if(decoded!=null&&!decoded.isRecycled())decoded.recycle();} // Expired/private art never prevents opening a program.
            final Backdrop made=ready;final boolean wasSkipped=skipped;
            main.post(()->{
                synchronized(images) {
                    pending.remove(p.art);
                    boolean wanted=currentArt(p.art,token,slot);
                    if(made!=null && wanted && Prefs.continueAllowed(p.pkg) && (!p.recommended || Prefs.appRecommendations())) {
                        images.put(p.art,made,made.retainedBytes());artVersion++;artPublished++;
                    }else if(!wasSkipped && made==null && wanted){if(failedImages.size()>=24)failedImages.clear();failedImages.add(p.art);}
                    else artDiscarded++;
                }
                // A stale job may have occupied the last slot: let the visible window request its missing art.
                for(Listener l:new ArrayList<>(listeners))l.onContinueWatching();
                prepareWanted();
            });
        });
        return null;
    }
    private Bitmap decode(String value)throws Exception {
        Uri uri=Uri.parse(value);byte[] bytes;
        if("content".equals(uri.getScheme())) {
            try(InputStream in=context.getContentResolver().openInputStream(uri)) {bytes=bounded(in);}
        } else if("https".equals(uri.getScheme())) {
            URL url=new URL(value);HttpURLConnection connection=null;
            try {
                for(int redirect=0;;redirect++) {
                    if(!"https".equals(url.getProtocol()) || url.getUserInfo()!=null)throw new java.io.IOException();
                    connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(4000);connection.setReadTimeout(4000);
                    int code=connection.getResponseCode();
                    if(code>=300 && code<400 && redirect<3) {String location=connection.getHeaderField("Location");
                        if(location==null)throw new java.io.IOException();URL next=new URL(url,location);connection.disconnect();connection=null;url=next;continue;}
                    if(code!=200 || connection.getContentLengthLong()>4*1024*1024)throw new java.io.IOException();
                    try(InputStream in=connection.getInputStream()) {bytes=bounded(in);}break;
                }
            } finally {if(connection!=null)connection.disconnect();}
        } else return null;
        return decodeBytes(bytes);
    }
    /**
     * At most 1280 px on the long side. Measured on the Mi Stick: decoding 1920 px art at full size for a sharper wide card
     * doubled the poster's transient memory (16.5 MiB), so the slight upscale of such art is kept.
     */
    static Bitmap decodeBytes(byte[] bytes) {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0 || bounds.outHeight<=0 || (long)bounds.outWidth*bounds.outHeight>32_000_000L)return null;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>1280)opts.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
    }
    private static byte[] bounded(InputStream in)throws java.io.IOException{return BoundedInput.read(in,4*1024*1024);}
    void trim(){synchronized(images){if(!images.isEmpty())artVersion++;images.clear();failedImages.clear();artGeneration++;}}
    void dumpArt(java.io.PrintWriter out,String prefix){
        synchronized(images){out.println(prefix+"PosterCache: active="+artActive+" entries="+images.size()+" bytes="+images.bytes()
                +" retainedLimit="+retainedLimit()+" wanted="+wantedArt.size()+" pending="+pending.size()+" loads="+artLoads+" published="+artPublished+" discarded="+artDiscarded);}
    }
    static boolean open(Activity activity,Program p) {
        if(p==null || !permitted(activity) || !WatchNextRules.packageName(p.pkg) || !Prefs.continueAllowed(p.pkg) || p.recommended && !Prefs.appRecommendations())return false;
        if(AppLock.intercept(activity,p.pkg,p.app,()->open(activity,p)))return true;
        try {
            Intent parsed=Intent.parseUri(p.intent,Intent.URI_INTENT_SCHEME);
            Uri data=parsed.getData();String scheme=data==null?"":data.getScheme();
            if(data!=null && scheme!=null && !Arrays.asList("file","javascript","data").contains(scheme.toLowerCase(java.util.Locale.ROOT))) {
                Intent target=new Intent(Intent.ACTION_VIEW,data).setPackage(p.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // URI intents contain primitive extras only. Never inherit a component, selector, grant flags or action.
                Bundle extras=parsed.getExtras();
                if(extras!=null && extras.size()<=64)for(String key:extras.keySet()) {
                    Object v=extras.get(key);if(key.length()>128)continue;
                    if(v instanceof String && ((String)v).length()<=8192)target.putExtra(key,(String)v);
                    else if(v instanceof Integer)target.putExtra(key,(Integer)v);
                    else if(v instanceof Long)target.putExtra(key,(Long)v);
                    else if(v instanceof Boolean)target.putExtra(key,(Boolean)v);
                }
                ResolveInfo resolved=activity.getPackageManager().resolveActivity(target,0);
                if(resolved!=null && resolved.activityInfo!=null && resolved.activityInfo.exported
                        && p.pkg.equals(resolved.activityInfo.packageName)) {activity.startActivity(target);return true;}
            }
        } catch(Exception ignored) {}
        Intent launch=HomeApps.launchIntent(activity.getPackageManager(),p.pkg);
        if(launch==null)return false;
        try {activity.startActivity(launch);return true;}catch(android.content.ActivityNotFoundException|SecurityException ignored) {return false;}
    }
}
