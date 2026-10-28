package io.puretv;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure rules for downloads from PureTV's GitHub releases: the AirPlay receiver (AirPlayInstall) and updates
 * (AppUpdate). The regression tests use them without Android.
 */
final class InstallRules {
    private InstallRules() {}

    // MARK: - Updates

    /** The only address asked for the latest release (GitHub's REST API; no token, no redirect). */
    static final String LATEST_API = "https://api.github.com/repos/Heymez0/PureTV/releases/latest";
    private static final String RELEASES = "https://github.com/Heymez0/PureTV/releases/download/";
    static final String LAUNCHER_ASSET = "PureTV.apk", RECEIVER_ASSET = "PureTV-AirPlay.apk";
    static final String OFF = "off", NOTIFY = "notify", AUTO = "auto";
    static final String[] MODES = {NOTIFY, AUTO, OFF};

    static String mode(String v) {
        for (String m : MODES) if (m.equals(v)) return m;
        return NOTIFY;
    }

    /** A release asset address as GitHub lists it: this repository, a plain tag, exactly the expected file. */
    static String asset(String url, String name) {
        if (url == null || !url.startsWith(RELEASES) || !url.endsWith("/" + name)) return null;
        String tag = url.substring(RELEASES.length(), url.length() - name.length() - 1);
        return tag.matches("[A-Za-z0-9._-]{1,64}") ? url : null;
    }

    static boolean tag(String v) {
        return v != null && v.matches("[A-Za-z0-9._-]{1,64}");
    }

    /** Only a strictly newer build is ever installed: never the same one again, never a downgrade. */
    static boolean newer(long candidate, long installed) {
        return candidate > installed && installed >= 0;
    }

    static final String URL = "https://github.com/Heymez0/PureTV/releases/latest/download/PureTV-AirPlay.apk";
    static final long MAX_BYTES = 32L * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"));

    /** HTTPS to GitHub's release hosts only: no user info, no other port. */
    static boolean allowed(String value) {
        try {
            java.net.URL url = new java.net.URL(value);
            return "https".equals(url.getProtocol()) && url.getUserInfo() == null && (url.getPort() == -1 || url.getPort() == 443)
                    && HOSTS.contains(url.getHost().toLowerCase(Locale.ROOT));
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    /** A redirect target, resolved against the current address; null when it isn't allowed. */
    static String follow(String from, String location) {
        if (location == null) return null;
        try {
            String next = new java.net.URL(new java.net.URL(from), location).toString();
            return allowed(next) ? next : null;
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }
}
