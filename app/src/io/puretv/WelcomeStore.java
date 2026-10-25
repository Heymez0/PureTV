package io.puretv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import org.json.JSONObject;

/** Separate bookkeeping; never bumps layout preferences or changes the default launcher. */
final class WelcomeStore {
    private WelcomeStore(){}
    static int status(Context c){return c.getSharedPreferences("welcome",Context.MODE_PRIVATE).getInt("status",WelcomeFlow.NEW);}
    static void status(Context c,int value){c.getSharedPreferences("welcome",Context.MODE_PRIVATE).edit().putInt("status",value).apply();}
    static boolean active(Context c){
        try{
            ResolveInfo info=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
            return info!=null&&info.activityInfo!=null&&HomeMode.PKG.equals(info.activityInfo.packageName)
                    &&(HomeMode.PKG+".HomeActivity").equals(info.activityInfo.name);
        }catch(RuntimeException ignored){return false;}
    }
    static boolean shouldShow(Context c){
        int status=status(c);if(status==WelcomeFlow.DONE||status==WelcomeFlow.DEFERRED)return false;
        boolean active=active(c);JSONObject state=HomeMode.state(c);
        // Boot verification alone writes a fingerprint even on a new install. That is not a completed setup.
        boolean legacy=state.optLong("since",0)>0||state.has("originalHome")||state.has("adbAuthorized")
                ||state.optBoolean("homeReverted")||HomeMode.FREE.equals(state.optString("mode"));
        boolean healthy=active&&state.optString("error").isEmpty();
        boolean show=WelcomeFlow.shouldShow(status,healthy,legacy,!busyText(state,android.os.SystemClock.elapsedRealtime()).isEmpty());
        if(!show)status(c,active?WelcomeFlow.DONE:WelcomeFlow.DEFERRED);
        return show;
    }
    static String busyText(JSONObject state,long now){
        long at=state.optLong("busyAt");return now<at||now-at>10*60_000L?"":state.optString("busy");
    }
}
