package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * The custom home screen (Android's "Home" role in custom home mode). It runs in the :home process: if it crashes,
 * the safety net in the main process keeps working. On the 3rd crash within 5 min, the Watchdog gives the home screen
 * back to the TV's original one.
 */
public class HomeActivity extends Activity {
    private static boolean browseRequested;
    static void requestOtherApps() { browseRequested = true; }
    private HomeView home;
    private boolean homeFocused;
    private String weatherPlace = Prefs.latitude()+","+Prefs.longitude();
    private int prefsVersion = Prefs.version;   // settings changed while we were away: start over

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        if (Watchdog.crashedTooOften(this)) {
            // Don't try to draw again: only show a notice while the main process restores the original home screen.
            HomeMode.requestRevertHome(this);
            TextView t = new TextView(this);
            t.setText(Ui.tr("O início teve um problema.\nVoltando para o Início original da TV…",
                    "The home screen ran into a problem.\nGoing back to the TV's original home screen…"));
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(Color.rgb(7, 11, 30));
            setContentView(t);
            return;
        }
        Ui.init(this);
        home = new HomeView(this);
        home.restoreContentState(savedInstanceState);
        setContentView(home);
        if (getIntent().getBooleanExtra("browse_other_apps", false)) home.browseOtherApps();
        getWindow().setBackgroundDrawable(null);
    }

    @Override protected void onSaveInstanceState(Bundle state){
        if(home!=null)home.saveContentState(state);super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefsVersion != Prefs.version) {
            prefsVersion = Prefs.version;
            String place=Prefs.latitude()+","+Prefs.longitude();
            if(!weatherPlace.equals(place)) {weatherPlace=place;HomeData.get(this).settingsChanged();}
            recreate();   // a new language or city: draw everything again
            return;
        }
        if (home != null) {
            home.resume();
            if (browseRequested) { browseRequested = false; home.browseOtherApps(); }
        }
    }

    @Override protected void onPause() {
        if (home != null) home.pause();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);homeFocused=focused;
    }

    /** Home button pressed on the home screen: back to the entry state (focus on the first app, sheet closed). */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Android pauses even an already-visible Home before delivering a new intent.
        boolean alreadyHome=homeFocused&&(intent.getFlags()&Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)==0;
        if (home != null) home.reset(true,alreadyHome);
        if (home != null && intent.getBooleanExtra("browse_other_apps", false)) home.browseOtherApps();
    }

    @Override protected void onDestroy() {
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        boolean pressure=(level>=TRIM_MEMORY_RUNNING_LOW&&level<TRIM_MEMORY_UI_HIDDEN)||level>=TRIM_MEMORY_MODERATE;
        if(pressure&&home!=null){home.trimPosterMemory();home.trimHiddenMemory();}
    }

    @Override public void onLowMemory(){
        super.onLowMemory();if(home!=null){home.trimPosterMemory();home.trimHiddenMemory();}
    }

    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);ContinueWatching.get(this).dumpArt(out,prefix);
        if(home!=null)home.dumpTv(out,prefix);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (RemoteKeys.onKey(this, event)) return true;
        if (home != null && home.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

}
