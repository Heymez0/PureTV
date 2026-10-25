package io.puretv;

import android.app.Activity;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;

/** Event-driven diagnostic only. No saved key history, accessibility listener or remapping. */
public class RemoteTestActivity extends Activity {
    private SettingsPage page;private SettingsPage.Data data;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        String[] names={"Button","Key code","Scan code","Input device","Source","Home screen action"};
        SettingsPage.Row[] rows=new SettingsPage.Row[names.length];
        for(int i=0;i<names.length;i++){rows[i]=new SettingsPage.Row(Ui.tr(names[i]),null,false);rows[i].value="—";}
        data=new SettingsPage.Data(Ui.tr("Remote button test"),Ui.tr("Press a button. Back closes this test."),"settings",rows);
        data.hint=Ui.tr("Only events delivered to this screen can be shown. Power, Home, volume and voice may be handled by Android before the app sees them. No buttons are reassigned here.");
        page=new SettingsPage(this,data,()->{});setContentView(page);
    }
    @Override protected void onResume(){super.onResume();page.resume();}
    @Override protected void onPause(){page.pause();super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_BACK)return page.onKey(event)||super.dispatchKeyEvent(event);
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            data.rows[0].value=KeyEvent.keyCodeToString(key);data.rows[1].value=Integer.toString(key);data.rows[2].value=Integer.toString(event.getScanCode());
            InputDevice device=event.getDevice();data.rows[3].value=device==null?"—":TvInputRules.clean(device.getName());data.rows[4].value="0x"+Integer.toHexString(event.getSource());
            boolean controller=(event.getSource()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD||(event.getSource()&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;
            int action=TvRemoteRules.action(Prefs.television(),Prefs.tvRemote(),controller,key);
            data.rows[5].value=Ui.tr(action==TvRemoteRules.INPUTS?"TV inputs":action==TvRemoteRules.LIVE?"Live TV":action==TvRemoteRules.QUICK?"Quick settings":action>=TvRemoteRules.HDMI1?"HDMI":"System / unchanged");page.changed();
        }
        // Keep scrolling possible; other keys still go to Android rather than being swallowed by a test.
        return page.onKey(event)||super.dispatchKeyEvent(event);
    }
}
