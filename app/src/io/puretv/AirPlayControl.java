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
    // Start server at boot as the receiver reports it: null until known; unsupported by receivers before 0.0.36-puretv.3
    private Boolean always;
    private boolean alwaysSupported=true;
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
    /** Keep AirPlay on (the receiver's Start server at boot): its value for the Connections row. */
    String alwaysValue() {
        if(Boolean.FALSE.equals(controlAvailable)) return "—";
        if(!alwaysSupported) return Ui.tr("Update the receiver");
        if(always==null) return Ui.tr("Checking…");
        return always?Ui.tr("On"):Ui.tr("Off");
    }
    /** Older receivers ignore the command (and would read as a failed connection), so it is sent only when supported. */
    void toggleAlways() {
        if(!visible || busy || always==null || !alwaysSupported)return;
        always=!always;busy=true;
        // "Keep AirPlay on" means on now too: start a stopped receiver (its reply also reports the new setting)
        boolean start=always && !running();
        if(start){requested=true;attempts=0;state="starting";}
        changed.run();
        send(always?"ALWAYS_ON":"ON_DEMAND",++generation);
        if(start)send("START",++generation);
    }
    void resume() { visible=true;busy=false;requested=null;controlAvailable=null;always=null;alwaysSupported=true;state="unknown";attempts=0;send("QUERY",++generation); }
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
            // A receiver force-stopped in TV Settings is "stopped": Android would drop the command until it is opened once.
            context.sendOrderedBroadcast(new Intent(ACTION+command).setComponent(COMPONENT)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),null,
                new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) {
                        if(!visible || token!=generation)return;
                        try {
                            if(getResultCode()!=Activity.RESULT_OK)throw new IllegalStateException();
                            JSONObject result=new JSONObject(getResultData());
                            state=result.optString("state","error");
                            if(!state.equals("running") && !state.equals("starting") && !state.equals("stopped")
                                    && !state.equals("error"))state="error";
                            alwaysSupported=result.has("always");
                            always=alwaysSupported?result.optBoolean("always"):null;
                        } catch(Exception e) {state="error";always=null;}
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
