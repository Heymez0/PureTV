package io.puretv;

import java.text.NumberFormat;
import java.util.Locale;

/** Used for cached status labels, never for drawing each frame. */
final class LocaleFormats {
    private LocaleFormats() {}
    // SI byte units match Android's displayed sizes; only the numeric locale varies.
    static String bytes(long bytes, Locale locale) {
        String[] units = {"B", "kB", "MB", "GB", "TB", "PB", "EB"};
        double amount = Math.max(0, bytes);
        int unit = 0;
        while (amount > 900 && unit < units.length - 1) { amount /= 1000; unit++; }
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(false);
        int decimals = unit > 0 && amount < 10 ? 1 : 0;
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(amount) + " " + units[unit];
    }
    static String percent(double fraction, Locale locale) {
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(0);
        return format.format(Math.max(0, Math.min(1, fraction)));
    }
}
