package io.puretv;

/** Every settings row can be read and reached again, whether or not it has an action. */
final class SettingsNavigation {
    private SettingsNavigation() {}
    static int next(int index,int direction,int count) {
        return count<1?0:Math.max(0,Math.min(count-1,index+Integer.signum(direction)));
    }
}
