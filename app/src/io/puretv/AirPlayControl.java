package io.puretv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/** Queries only while a page is visible. Commands and replies are restricted to our signed receiver. */
final class AirPlayControl {
    static final String PERMISSION = "io.puretv.airplay.permission.CONTROL";
    private static final String ACTION = "io.puretv.airplay.control.";
    private static final ComponentName COMPONENT = new ComponentName(AirPlayReceiver.THEMED,
            "io.github.jqssun.airplay.LauncherControlReceiver");
    private final Context context;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible, busy;
    private String state = "unknown";
    private int generation;
    private int attempts;
    private Boolean requested;
    private Boolean controlAvailable;
    AirPlayControl(Context c, Runnable changed) { context=c.getApplicationContext(); this.changed=changed; }
    boolean running() { return "running".equals(state); }
    boolean busy() { return busy; }
    boolean available() {
        try {
            PackageManager pm=context.getPackageManager();
            ActivityInfo info=pm.getReceiverInfo(COMPONENT,0);
            return info.enabled && PERMISSION.equals(info.permission)
                    && pm.checkSignatures(context.getPackageName(),AirPlayReceiver.THEMED)==PackageManager.SIGNATURE_MATCH;
        } catch(PackageManager.NameNotFoundException | SecurityException e) { return false; }
    }
    String value() {
        if(Boolean.FALSE.equals(controlAvailable)) return Ui.tr("Controle indisponível", "Control unavailable");
        if("starting".equals(state))return Ui.tr("Iniciando…", "Starting…");
        if("stopping".equals(state))return Ui.tr("Parando…", "Stopping…");
        if("error".equals(state))return Ui.tr("Falha ao conectar", "Connection failed");
        if("unknown".equals(state))return Ui.tr("Verificando…", "Checking…");
        return running()?Ui.tr("Ligado","On"):Ui.tr("Desligado","Off");
    }
    void resume() { visible=true;busy=false;requested=null;controlAvailable=null;state="unknown";attempts=0;send("QUERY",++generation); }
    void pause() { visible=false;generation++;handler.removeCallbacksAndMessages(null); }
    void toggle() {
        if(!visible || busy || !available() || "unknown".equals(state))return;
        requested=!running();attempts=0;busy=true;state=requested?"starting":"stopping";changed.run();
        send(requested?"START":"STOP",++generation);
    }
    private void send(String command, int token) {
        controlAvailable=available(); // Every command still verifies the receiver and its signature afresh.
        if(!controlAvailable) {state="error";busy=false;if(visible)changed.run();return;}
        try {
            context.sendOrderedBroadcast(new Intent(ACTION+command).setComponent(COMPONENT),null,
                new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) {
                        if(!visible || token!=generation)return;
                        try {
                            if(getResultCode()!=Activity.RESULT_OK)throw new IllegalStateException();
                            JSONObject result=new JSONObject(getResultData());
                            state=result.optString("state","error");
                            if(!state.equals("running") && !state.equals("starting") && !state.equals("stopped")
                                    && !state.equals("error"))state="error";
                        } catch(Exception e) {state="error";}
                        boolean waiting=requested!=null && !"error".equals(state) && running()!=requested;
                        busy=waiting;
                        changed.run();
                        if(waiting && ++attempts<5)handler.postDelayed(()->send("QUERY",token),500);
                        else {busy=false;requested=null;changed.run();}
                    }
                },handler,Activity.RESULT_CANCELED,null,null);
        } catch(RuntimeException e) {state="error";busy=false;requested=null;if(visible)changed.run();}
    }
}
