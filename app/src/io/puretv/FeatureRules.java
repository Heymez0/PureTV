package io.puretv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * The checks behind hidden apps, the start-up choice, the app lock and the rain warning. Pure: the regression tests use
 * it without Android, and every stored value is checked again when it is read.
 */
final class FeatureRules {
    private FeatureRules() {}

    // MARK: - Package lists (hidden and locked apps)

    static final int LIST_LIMIT = 200;

    /** Comma-separated package names, valid and without repeats, at most LIST_LIMIT. */
    static List<String> packages(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.length() > 20000) return out;
        for (String pkg : csv.split(",")) {
            if (out.size() >= LIST_LIMIT) break;
            if (WatchNextRules.packageName(pkg) && !out.contains(pkg)) out.add(pkg);
        }
        return out;
    }

    // MARK: - What opens when the TV starts

    static final String HOME = "", LIVE = "live", APP = "app:", INPUT = "input:";
    /** Only in the first minutes after a start: an app update or a crash restart later never opens anything. */
    static final long STARTUP_WINDOW_MS = 5 * 60_000L;

    static String startup(String v) {
        if (v == null) return HOME;
        if (v.equals(LIVE)) return v;
        if (v.startsWith(APP) && WatchNextRules.packageName(v.substring(APP.length()))) return v;
        // The input's catalogue key ("input:<TV input id>"); ids are component-like, nested classes included ($).
        if (v.startsWith(INPUT) && v.length() <= 200 && v.substring(INPUT.length()).matches("[A-Za-z0-9_.:/$\\-]+")) return v;
        return HOME;
    }

    /** Once per TV start, and only soon after it. */
    static boolean launchAtStart(String choice, int bootCount, int lastBoot, long sinceBootMs) {
        return !HOME.equals(startup(choice)) && bootCount > 0 && bootCount != lastBoot
                && sinceBootMs >= 0 && sinceBootMs < STARTUP_WINDOW_MS;
    }

    // MARK: - App lock

    static final String ALWAYS = "always";
    /** The evening hours a lock can be limited to; until 7:00 the next morning. */
    static final String[] HOURS = {ALWAYS, "20-7", "21-7", "22-7"};
    static final int ATTEMPTS = 5;
    static final long WAIT_MS = 30_000;

    static String hours(String v) {
        for (String h : HOURS) if (h.equals(v)) return h;
        return ALWAYS;
    }

    /** Whether a lock with these hours applies at this minute of the day (0–1439). */
    static boolean lockedAt(String hours, int minuteOfDay) {
        String h = hours(hours);
        if (h.equals(ALWAYS)) return true;
        int from = Integer.parseInt(h.substring(0, h.indexOf('-'))) * 60, until = 7 * 60;
        return minuteOfDay >= from || minuteOfDay < until;
    }

    static boolean validPin(String pin) {
        return pin != null && pin.matches("[0-9]{4,8}");
    }

    /** "salt:hash" (SHA-256 of salt and PIN): the PIN itself is never stored. */
    static String storePin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = hex(salt);
        return s + ":" + hex(sha256(s + ":" + pin));
    }

    static boolean pinMatches(String pin, String stored) {
        if (!validPin(pin) || stored == null) return false;
        int colon = stored.indexOf(':');
        if (colon <= 0) return false;
        String salt = stored.substring(0, colon);
        return MessageDigest.isEqual(hex(sha256(salt + ":" + pin)).getBytes(StandardCharsets.US_ASCII),
                stored.substring(colon + 1).getBytes(StandardCharsets.US_ASCII));
    }

    /** After ATTEMPTS wrong PINs in a row, WAIT_MS before the next try (milliseconds left, 0 = may try). */
    static long waitLeft(int failures, long lastFailAt, long now) {
        if (failures < ATTEMPTS || now < lastFailAt) return 0;
        return Math.max(0, lastFailAt + WAIT_MS - now);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return sb.toString();
    }

    // MARK: - Automatic memory cleanup

    /** Below this share of the TV's memory left, returning home frees memory. */
    static final int AUTO_FREE_PERCENT = 15;
    /** At most this often: a cleanup closes the app left a moment ago, which then starts from scratch. */
    static final long AUTO_FREE_COOLDOWN_MS = 15 * 60_000L;

    static boolean autoFree(long available, long total, long sinceLastMs) {
        return total > 0 && available >= 0 && available * 100 < total * AUTO_FREE_PERCENT
                && sinceLastMs >= AUTO_FREE_COOLDOWN_MS;
    }

    // MARK: - Rain warning

    /** An hour's chance of rain is shown from this percentage. */
    static final int RAIN_SHOWN = 30;

    /** The first of the next hours with at least `threshold` % chance of rain, or -1. */
    static int firstRain(List<Integer> chances, int threshold) {
        for (int i = 0; chances != null && i < chances.size(); i++) {
            Integer c = chances.get(i);
            if (c != null && c >= threshold && c <= 100) return i;
        }
        return -1;
    }
}
