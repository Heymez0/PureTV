package io.puretv;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
/** Only fixed system settings actions chosen by our own UI. */
final class SettingsLinks {
    static boolean openTvControl(Activity a,String action){
        if(!Settings.ACTION_SETTINGS.equals(action)&&!Settings.ACTION_DISPLAY_SETTINGS.equals(action)
                &&!Settings.ACTION_SOUND_SETTINGS.equals(action)&&!android.media.tv.TvInputManager.ACTION_SETUP_INPUTS.equals(action))return false;
        return openSystemSettings(a,new String[]{action,Settings.ACTION_SETTINGS});
    }
    static boolean openDeveloperSettings(Activity a){
        return openSystemSettings(a,new String[]{Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,Settings.ACTION_DEVICE_INFO_SETTINGS,Settings.ACTION_SETTINGS});
    }
    static boolean openNetworkSettings(Activity a){
        return openSystemSettings(a,new String[]{Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_SETTINGS});
    }
    private static boolean openSystemSettings(Activity a,String[] actions){
        for(String action:actions){
            Intent intent=new Intent(action);
            ResolveInfo target=a.getPackageManager().resolveActivity(intent,android.content.pm.PackageManager.MATCH_SYSTEM_ONLY);
            if(target==null||target.activityInfo==null||!target.activityInfo.exported||!target.activityInfo.enabled
                    ||(target.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)continue;
            intent.setComponent(new android.content.ComponentName(target.activityInfo.packageName,target.activityInfo.name));
            try{a.startActivity(intent);return true;}catch(ActivityNotFoundException|SecurityException ignored){}
        }
        return false;
    }
    static boolean openBluetooth(Activity a){
        // Android TV 9 on Mi Stick has no handler for BLUETOOTH_SETTINGS.
        // Its system accessory screen exposes this fixed, package-scoped action.
        Intent tv=new Intent("com.google.android.intent.action.CONNECT_INPUT").setPackage("com.android.tv.settings");
        ResolveInfo target=a.getPackageManager().resolveActivity(tv,0);
        if(target!=null&&target.activityInfo!=null&&target.activityInfo.exported
                &&target.activityInfo.enabled&&(target.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0){
            try{a.startActivity(tv);return true;}catch(ActivityNotFoundException|SecurityException ignored){}
        }
        try{a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return true;}
        catch(ActivityNotFoundException|SecurityException ignored){
            android.widget.Toast.makeText(a,Ui.tr("Could not open Bluetooth settings."),android.widget.Toast.LENGTH_SHORT).show();return false;
        }
    }
    static boolean open(Activity a,String action){
        try{a.startActivity(new Intent(action));return true;}
        catch(ActivityNotFoundException|SecurityException e){
            if(Settings.ACTION_SETTINGS.equals(action))return false;
            try{a.startActivity(new Intent(Settings.ACTION_SETTINGS));return true;}
            catch(ActivityNotFoundException|SecurityException ignored){return false;}
        }
    }
}
