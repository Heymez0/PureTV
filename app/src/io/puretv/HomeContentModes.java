package io.puretv;

/** The existing clock/recent choice also controls whether app previews may be read. */
final class HomeContentModes {
    static final String CLOCK="clock", RECENT="recent", COMBINED="combined";
    private HomeContentModes() {}
    static String normalize(String value,boolean legacyRecent) {
        return CLOCK.equals(value)||RECENT.equals(value)||COMBINED.equals(value)?value:legacyRecent?RECENT:CLOCK;
    }
    static String next(String value,int direction) {
        String[] modes={CLOCK,RECENT,COMBINED};int at=CLOCK.equals(value)?0:COMBINED.equals(value)?2:1;
        return modes[(at+(direction<0?2:1))%3];
    }
    static String label(String value) {
        return CLOCK.equals(value)?"Clock only":COMBINED.equals(value)?"Recent + recommended":"Recently watched only";
    }
    static String allowed(String value,boolean enabled){return enabled?normalize(value,true):CLOCK;}
    static String[] choices(boolean enabled){return enabled?new String[]{CLOCK,RECENT,COMBINED}:new String[]{CLOCK};}
}
