package io.puretv;

/** Main-thread request generations: one worker, no publication after an invalidation. */
final class RefreshGate {
    private long generation;
    private boolean loading;
    void invalidate(){generation++;}
    long begin(boolean force){
        if(force)invalidate();
        if(loading)return -1;
        loading=true;return generation;
    }
    boolean complete(long token){loading=false;return token==generation;}
}
