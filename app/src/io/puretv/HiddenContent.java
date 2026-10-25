package io.puretv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable local choices, without storing program titles or playback links. */
final class HiddenContent {
    static final int LIMIT = 128;
    private HiddenContent() {}
    static String key(String pkg, String intent) {
        if (!WatchNextRules.packageName(pkg) || intent == null || intent.isEmpty() || intent.length() > 8192) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((pkg + "\n" + intent).getBytes(StandardCharsets.UTF_8));
            char[] out = new char[hash.length * 2];
            String digits = "0123456789abcdef";
            for (int i = 0; i < hash.length; i++) {
                out[i * 2] = digits.charAt((hash[i] >>> 4) & 15);
                out[i * 2 + 1] = digits.charAt(hash[i] & 15);
            }
            return new String(out);
        } catch (java.security.NoSuchAlgorithmException e) { return ""; }
    }
    static boolean valid(String key) { return key != null && key.matches("[0-9a-f]{64}"); }
}
