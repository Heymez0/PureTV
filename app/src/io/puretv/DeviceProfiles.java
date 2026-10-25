package io.puretv;

/** Presentation profile only; never changes Android's HOME role or system packages. */
final class DeviceProfiles {
    static final String BOX="box",TV="tv";
    private DeviceProfiles(){}
    static String normalize(String value){return TV.equals(value)?TV:BOX;}
    static String label(String value){return TV.equals(normalize(value))?"Television":"TV box / stick";}
}
