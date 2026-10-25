package io.puretv;

/** Two content tabs in the existing header slots, followed by three actions. Hidden recommendation tabs never trap remote focus. */
final class HomeTabs {
    static final int ACTION = 4, COUNT = 7;
    private HomeTabs() {}
    static boolean visible(int index, boolean recent, boolean recommendations) {
        return index >= 0 && index < COUNT && (index >= ACTION || (index == 0 ? recent : index == 1 && recommendations));
    }
    static int first(boolean recent, boolean recommendations) { return recent ? 0 : recommendations ? 1 : ACTION; }
    static int up(int from) { return from>=0&&from<ACTION?ACTION:from; }
    static int next(int from, int direction, boolean recent, boolean recommendations) {
        int step=Integer.signum(direction);
        if(step==0)return from;
        for(int at=from+step;at>=0 && at<COUNT;at+=step)if(visible(at,recent,recommendations))return at;
        return from;
    }
}
