package io.puretv;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** The settings backup's allowlist and checks (SettingsBackup). Pure: the regression tests use it without Android. */
final class BackupRules {
    private BackupRules() {}

    static final String[] STRINGS = {"language", "temperature", "theme", "clock_format", "device_mode", "city", "latitude",
            "longitude", "home_content_mode", "viewing_previous_mode", "shelf", "quick_shortcuts", "other_apps_order",
            "hidden_apps", "startup"};
    static final String[] BOOLEANS = {"lightweight", "tv_inputs", "inactive_inputs", "tv_remote", "sounds",
            "continue_watching", "viewing_information", "weather", "detailed_weather", "screensaver_photos",
            "auto_free_memory"};
    static final String SOURCES = "continue_hidden";
    private static final Pattern LANGUAGE = Pattern.compile("auto|[a-z]{2,3}(-[A-Za-z0-9]{2,8})?");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:\\-]{1,200}");

    /** Only allowed keys with valid values; anything else is dropped. */
    static Map<String, Object> clean(Map<String, ?> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in == null) return out;
        for (String key : BOOLEANS) if (in.get(key) instanceof Boolean) out.put(key, in.get(key));
        for (String key : STRINGS) {
            Object raw = in.get(key);
            String value = raw instanceof String ? clean(key, (String) raw) : null;
            if (value != null) out.put(key, value);
        }
        Object sources = in.get(SOURCES);
        if (sources instanceof Collection) {
            Set<String> pkgs = new LinkedHashSet<>();
            for (Object p : (Collection<?>) sources) {
                if (p instanceof String && WatchNextRules.packageName((String) p) && pkgs.size() < 200) pkgs.add((String) p);
            }
            out.put(SOURCES, pkgs);
        }
        return out;
    }

    private static String clean(String key, String v) {
        if (v.length() > 20000) return null;
        switch (key) {
            case "language": return LANGUAGE.matcher(v).matches() ? v : null;
            case "temperature": return v.equals("auto") || v.equals("C") || v.equals("F") ? v : null;
            case "theme": return v.equals("auto") || v.equals("light") || v.equals("dark") ? v : null;
            case "clock_format": return ClockFormat.normalize(v).equals(v) ? v : null;
            case "device_mode": return DeviceProfiles.normalize(v).equals(v) ? v : null;
            case "home_content_mode": case "viewing_previous_mode":
                return v.equals(HomeContentModes.CLOCK) || v.equals(HomeContentModes.RECENT) || v.equals(HomeContentModes.COMBINED) ? v : null;
            case "city": return v.length() <= 120 && !hasControl(v) ? v.trim() : null;
            case "latitude": return number(v, 90);
            case "longitude": return number(v, 180);
            case "shelf": return list(v, 64, true);
            case "quick_shortcuts": return ShortcutOrder.encode(java.util.Arrays.asList(v.split(",")));
            case "other_apps_order": return list(v, 200, false);
            case "hidden_apps": return String.join(",", FeatureRules.packages(v));
            case "startup": return FeatureRules.startup(v).equals(v) ? v : null;
            default: return null;
        }
    }

    private static boolean hasControl(String v) {
        for (int i = 0; i < v.length(); i++) if (Character.isISOControl(v.charAt(i))) return true;
        return false;
    }

    private static String number(String v, double limit) {
        try {
            double d = Double.parseDouble(v);
            return v.length() <= 24 && !Double.isNaN(d) && !Double.isInfinite(d) && Math.abs(d) <= limit ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Comma-separated package names (shelf) or app keys (other apps), deduplicated and bounded. */
    private static String list(String v, int max, boolean packages) {
        Set<String> out = new LinkedHashSet<>();
        for (String item : v.split(",")) {
            if (out.size() >= max) break;
            if (packages ? WatchNextRules.packageName(item) : KEY.matcher(item).matches()) out.add(item);
        }
        return String.join(",", out);
    }

    /** Remote buttons: only the given key codes, each with a package name or "" (Nothing). */
    static Map<Integer, String> cleanKeys(Map<String, ?> in, int[] allowed) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (in == null) return out;
        for (int code : allowed) {
            Object v = in.get(Integer.toString(code));
            if (v instanceof String && (((String) v).isEmpty() || WatchNextRules.packageName((String) v))) out.put(code, (String) v);
        }
        return out;
    }
}
