package io.puretv;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings changed on the TV itself (Home screen > Settings). config.properties only gives the first
 * values; anything the user changes here wins. Read from the :home process (home screen, screensaver, settings).
 */
final class Prefs {
    static int catalogVersion;
    private Prefs() {}

    /** Remote keys we handle, with the app each opens until the user picks another. Codes read from the TCL's log. */
    static final int KEY_NETFLIX = 4062, KEY_YOUTUBE = 190, KEY_PRIME = 4099, KEY_TCL_CHANNEL = 4020;
    /** Vestel's Netflix button: STEM_1, which the TV hands to its Netflix key receiver (dumpsys window policy). */
    static final int KEY_VESTEL_NETFLIX = KeyEvent.KEYCODE_STEM_1;
    static final int[] KEYS = RemoteKeys.VESTEL ? new int[] {KEY_VESTEL_NETFLIX}
            : new int[] {KEY_NETFLIX, KEY_YOUTUBE, KEY_PRIME, KEY_TCL_CHANNEL};

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;
    static volatile int contentVersion;

    private static Context app;
    private static volatile boolean lightweight;

    static void init(Context c) {
        app = c.getApplicationContext();
        lightweight = sp().getBoolean("lightweight", false);
        if (sp().contains("compact_home")) sp().edit().remove("compact_home").apply();
        if (sp().contains("weather_expanded")) sp().edit().remove("weather_expanded").apply();
        migrateKeys();   // before anything reads the button choices
        Ui.applyLanguage();   // the language picked on the TV, now that it can be read
    }

    static Context context() { return app; }

    private static SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        sp().edit().putString(key, value).apply();
        version++;
    }

    private static void put(String key, boolean value) {
        sp().edit().putBoolean(key, value).apply();
        version++;
    }

    // MARK: - Language and units

    /** "auto", "pt", "en", "es", "de", "ru" or "tr". */
    static String language() {
        String v = app == null ? Config.LANGUAGE : sp().getString("language", Config.LANGUAGE);
        v = v.trim();
        return v.isEmpty() || v.equalsIgnoreCase("auto") ? "auto" : v;
    }

    static void setLanguage(String v) { put("language", v); Ui.applyLanguage(); }

    /** "auto", "C" or "F". */
    static String temperature() {
        String v = sp().getString("temperature", Config.TEMPERATURE).trim().toUpperCase(Locale.ROOT);
        return v.startsWith("F") ? "F" : v.startsWith("C") ? "C" : "auto";
    }

    static void setTemperature(String v) { put("temperature", v); }

    /** "auto" (dark from sunset to sunrise), "light" or "dark". */
    static String theme() {
        String v = sp().getString("theme", "auto");
        return v.equals("light") || v.equals("dark") ? v : "auto";
    }

    static void setTheme(String v) { put("theme", v); }

    /** Visual options only; no system settings, services or permissions are changed. */
    static String clockFormat() {
        return ClockFormat.normalize(app == null ? "auto" : sp().getString("clock_format", "auto"));
    }
    static void setClockFormat(String value) { put("clock_format", ClockFormat.normalize(value)); }
    static boolean lightweight() { return lightweight; }
    static void setLightweight(boolean value) {
        lightweight = value;
        put("lightweight", value);
    }

    static String deviceMode(){return DeviceProfiles.normalize(sp().getString("device_mode",DeviceProfiles.BOX));}
    static boolean television(){return DeviceProfiles.TV.equals(deviceMode());}
    static void setDeviceMode(String value){value=DeviceProfiles.normalize(value);if(!value.equals(deviceMode()))put("device_mode",value);}
    static boolean tvInputs(){return sp().getBoolean("tv_inputs",true);}
    static void setTvInputs(boolean value){if(value!=tvInputs())put("tv_inputs",value);}
    static boolean inactiveInputs(){return sp().getBoolean("inactive_inputs",true);}
    static void setInactiveInputs(boolean value){if(value!=inactiveInputs())put("inactive_inputs",value);}
    static boolean tvRemote(){return sp().getBoolean("tv_remote",true);}
    static void setTvRemote(boolean value){if(value!=tvRemote())put("tv_remote",value);}

    // MARK: - City

    static String city() { return sp().getString("city", Config.CITY).trim(); }
    static String latitude() { return sp().getString("latitude", Config.LATITUDE); }
    static String longitude() { return sp().getString("longitude", Config.LONGITUDE); }

    static void setCity(String name, String latitude, String longitude) {
        sp().edit().putString("city", name).putString("latitude", latitude).putString("longitude", longitude).apply();
        version++;
    }

    static boolean sounds() { return sp().getBoolean("sounds", true); }
    static void setSounds(boolean value) { put("sounds", value); }

    // Only local source/hide choices are saved; viewing history stays in Android TV's provider.
    static String homeContentMode() {
        return HomeContentModes.allowed(HomeContentModes.normalize(sp().getString("home_content_mode",null),sp().getBoolean("continue_watching",true)),viewingInformation());
    }
    static void setHomeContentMode(String value) {
        value=HomeContentModes.allowed(value,viewingInformation());
        sp().edit().putString("home_content_mode",value).putBoolean("continue_watching",!HomeContentModes.CLOCK.equals(value)).apply();
        version++;
    }
    static boolean viewingInformation(){return sp().getBoolean("viewing_information",true);}
    static void setViewingInformation(boolean enabled){
        if(enabled==viewingInformation())return;
        String mode=enabled?HomeContentModes.normalize(sp().getString("viewing_previous_mode",HomeContentModes.CLOCK),false):HomeContentModes.CLOCK;
        SharedPreferences.Editor edit=sp().edit();
        if(!enabled)edit.putString("viewing_previous_mode",homeContentMode());
        edit.putBoolean("viewing_information",enabled).putString("home_content_mode",mode)
            .putBoolean("continue_watching",!HomeContentModes.CLOCK.equals(mode)).apply();
        version++;
    }
    static boolean continueWatching() { return !HomeContentModes.CLOCK.equals(homeContentMode()); }
    static boolean appRecommendations() { return HomeContentModes.COMBINED.equals(homeContentMode()); }
    static void setContinueWatching(boolean value) { setHomeContentMode(value?HomeContentModes.RECENT:HomeContentModes.CLOCK); }
    static java.util.Set<String> continueHidden() {
        return new java.util.HashSet<>(sp().getStringSet("continue_hidden", java.util.Collections.emptySet()));
    }
    static boolean continueSelected(String pkg) { return !continueHidden().contains(pkg); }
    static boolean continueAllowed(String pkg) { return continueWatching() && continueSelected(pkg); }
    static void setContinueAllowed(String pkg, boolean allowed) {
        if (!WatchNextRules.packageName(pkg)) return;
        java.util.Set<String> hidden=continueHidden();
        if (allowed) hidden.remove(pkg); else hidden.add(pkg);
        sp().edit().putStringSet("continue_hidden", hidden).apply(); version++;
    }
    static java.util.Set<String> hiddenContent() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String key : sp().getStringSet("continue_hidden_content", java.util.Collections.emptySet())) {
            if (HiddenContent.valid(key) && out.size() < HiddenContent.LIMIT) out.add(key);
        }
        return out;
    }
    static boolean hideContent(String key) {
        if (!HiddenContent.valid(key)) return false;
        java.util.Set<String> hidden = hiddenContent();
        if (!hidden.contains(key) && hidden.size() >= HiddenContent.LIMIT) return false;
        hidden.add(key);
        sp().edit().putStringSet("continue_hidden_content", hidden).apply();
        contentVersion++;
        return true;
    }
    static void restoreHiddenContent() { sp().edit().remove("continue_hidden_content").apply(); contentVersion++; }

    // MARK: - Weather

    static boolean weather() { return sp().getBoolean("weather", true); }
    static void setWeather(boolean on) { put("weather", on); }
    static boolean detailedWeather() { return sp().getBoolean("detailed_weather", true); }
    static void setDetailedWeather(boolean value) { put("detailed_weather", value); }

    // MARK: - Shelf

    /** Packages on the home shelf, in order; null until the user edits it (then the built-in list applies). */
    static List<String> shelf() {
        String v = sp().getString("shelf", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setShelf(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("shelf", sb.toString());
    }

    // Separate from "shelf": these IDs only control the bottom shortcut row.
    static List<String> quickShortcuts() {
        return ShortcutOrder.read(sp().getString("quick_shortcuts", null));
    }
    static void setQuickShortcuts(List<String> ids) {
        // HomeView reloads this row on resume; do not rebuild the scene or refetch weather for a shortcut edit.
        sp().edit().putString("quick_shortcuts", ShortcutOrder.encode(ids)).apply();
        catalogVersion++;
    }

    static List<String> otherAppsOrder() {
        String raw = sp().getString("other_apps_order", "");
        List<String> out = new ArrayList<>();
        if (raw.length() <= 20000) for (String key : raw.split(",")) {
            if (!key.isEmpty() && key.length() <= 200 && !out.contains(key)) out.add(key);
            if (out.size() >= 200) break;
        }
        return out;
    }
    static void setOtherAppsOrder(List<String> keys) {
        sp().edit().putString("other_apps_order", String.join(",", keys)).apply();
        catalogVersion++;
    }

    // MARK: - Remote buttons

    private static String defaultApp(int keyCode) {
        switch (keyCode) {
            case KEY_NETFLIX: case KEY_VESTEL_NETFLIX: return "com.netflix.ninja";
            case KEY_YOUTUBE: return "com.google.android.youtube.tv";
            case KEY_PRIME: return "com.amazon.amazonvideo.livingroom";
            case KEY_TCL_CHANNEL: return "";
            default: return "";
        }
    }

    // The key service runs in its own small process (:keys), so that a busy home screen can never delay the TV's keys.
    // SharedPreferences are not shared between processes, so these few values live in a tiny file both processes read.
    // The service asks on every key: the values stay in memory and the file is read again only when it changes.
    private static File keysFile() {
        return new File(app.getFilesDir(), "remote-keys.txt");
    }

    private static final String[] keyApps = new String[KEYS.length];   // null = the default
    private static long keysStamp = Long.MIN_VALUE;

    /** A temporary file per process (two can start together after an update), synced before it replaces the old one. */
    private static boolean writeKeys(String text) {
        File tmp = new File(app.getFilesDir(), "remote-keys." + android.os.Process.myPid() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return tmp.renameTo(keysFile());
    }

    /** Choices saved before they moved to the file (in SharedPreferences) are copied there once. */
    private static void migrateKeys() {
        if (keysFile().exists()) return;
        SharedPreferences old = sp();
        boolean any = false;
        for (int key : KEYS) any |= old.contains("key" + key);
        if (!any) return;
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(old.getString("key" + key, defaultApp(key))).append('\n');
        writeKeys(sb.toString());
    }

    /** Package the key opens ("" = nothing). */
    static String keyApp(int keyCode) {
        synchronized (keyApps) {
            File f = keysFile();
            long stamp = f.lastModified() * 31 + f.length();   // 0 without the file: every key opens its default
            if (stamp != keysStamp) {
                keysStamp = stamp;
                java.util.Arrays.fill(keyApps, null);
                if (stamp != 0) {
                    try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            for (int i = 0; i < KEYS.length; i++) {
                                String prefix = KEYS[i] + "=";
                                if (line.startsWith(prefix)) keyApps[i] = line.substring(prefix.length());
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == keyCode && keyApps[i] != null) return keyApps[i];
            return defaultApp(keyCode);
        }
    }

    static void setKeyApp(int keyCode, String pkg) {
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(key == keyCode ? pkg : keyApp(key)).append('\n');
        if (writeKeys(sb.toString())) version++;
    }
}
