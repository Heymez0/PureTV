package io.puretv;

/**
 * Two content tabs in the existing header slots, followed by three actions. Hidden recommendation tabs never trap remote
 * focus. Slot 3 is the weather pill at the top left of the landscape clock (Scenes): there only with a photo behind the
 * clock and the weather on.
 */
final class HomeTabs {
    static final int ACTION = 4, COUNT = 7, WEATHER = 3;
    private HomeTabs() {}
    static boolean visible(int index, boolean recent, boolean recommendations) { return visible(index, recent, recommendations, false); }
    static boolean visible(int index, boolean recent, boolean recommendations, boolean weather) {
        if (index == WEATHER) return weather;
        return index >= 0 && index < COUNT && (index >= ACTION || (index == 0 ? recent : index == 1 && recommendations));
    }
    static boolean isTab(int index) { return index >= 0 && index < ACTION && index != WEATHER; }
    static int first(boolean recent, boolean recommendations) { return recent ? 0 : recommendations ? 1 : ACTION; }
    /** Up from a tab goes to the buttons; the weather pill already sits on their row. */
    static int up(int from) { return isTab(from) ? ACTION : from; }
    static int next(int from, int direction, boolean recent, boolean recommendations) { return next(from, direction, recent, recommendations, false); }
    static int next(int from, int direction, boolean recent, boolean recommendations, boolean weather) {
        int step=Integer.signum(direction);
        if(step==0)return from;
        for(int at=from+step;at>=0 && at<COUNT;at+=step)if(visible(at,recent,recommendations,weather))return at;
        return from;
    }
}
