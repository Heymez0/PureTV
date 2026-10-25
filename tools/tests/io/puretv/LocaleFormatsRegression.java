package io.puretv;
import java.util.Locale;
final class LocaleFormatsRegression {
    public static void main(String[] args) {
        if (!LocaleFormats.percent(.68, Locale.US).equals("68%")) throw new AssertionError("English percent order");
        if (!LocaleFormats.percent(.68, new Locale("tr", "TR")).equals("%68")) throw new AssertionError("Turkish percent order");
        String french = LocaleFormats.percent(.68, Locale.FRANCE);
        if (!french.startsWith("68") || !french.endsWith("%")) throw new AssertionError("French percent");
        if (!LocaleFormats.percent(-1, Locale.US).equals("0%") || !LocaleFormats.percent(2, Locale.US).equals("100%")) throw new AssertionError("Snapshot clamp");
        if (!LocaleFormats.bytes(1000000000L, Locale.US).equals("1.0 GB")) throw new AssertionError("English bytes");
        if (!LocaleFormats.bytes(1000000000L, Locale.FRANCE).equals("1,0 GB")) throw new AssertionError("French bytes");
        if (!LocaleFormats.bytes(1000000000L, new Locale("tr", "TR")).equals("1,0 GB")) throw new AssertionError("Turkish bytes");
        if (!LocaleFormats.bytes(-1, Locale.US).equals("0 B") || !LocaleFormats.bytes(Long.MAX_VALUE, Locale.US).endsWith(" EB")) throw new AssertionError("Byte bounds");
        System.out.println("PASS: application-locale byte sizes, percent placement, localized spacing and bounded snapshot values");
    }
}
