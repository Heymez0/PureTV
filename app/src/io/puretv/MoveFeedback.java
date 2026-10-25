package io.puretv;

/** Same small, allocation-free rotation as the main shelf, only while an item is being moved. */
final class MoveFeedback {
    private MoveFeedback() {}
    static float angle(long elapsedMillis) {
        return 1.5f * (float) Math.sin(elapsedMillis * (2 * Math.PI / 300));
    }
}
