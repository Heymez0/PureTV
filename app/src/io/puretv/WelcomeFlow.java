package io.puretv;

/** UI-only setup state. The existing HomeMode remains the only activation implementation. */
final class WelcomeFlow {
    static final int INTRO=0,CONNECT=1,REVIEW=2,WORKING=3,READY=4;
    static final int DEVICE=5,LAYOUT=6;
    static final int NEW=0,STARTED=1,DEFERRED=2,DONE=3;
    int step=INTRO;
    boolean waiting,sawBusy,failed;
    /** Looking back through the steps of a setup that is already done (the guide opened again from Settings). */
    boolean browsing;
    private long requestedAt;
    private String previousError="";
    static boolean shouldShow(int status,boolean active,boolean legacy,boolean busy){
        if(status==DONE||status==DEFERRED)return false;
        return status==STARTED?!active||busy:!active&&!legacy;
    }
    void restore(int saved){step=saved==DEVICE||saved==LAYOUT||saved>=INTRO&&saved<=REVIEW?saved:INTRO;}
    void next(){if(step==INTRO)step=DEVICE;else if(step==DEVICE)step=LAYOUT;else if(step==LAYOUT)step=CONNECT;else if(step==CONNECT)step=REVIEW;failed=false;}
    boolean back(){
        if(step==READY){step=REVIEW;browsing=true;failed=false;return true;}
        if(step==DEVICE){step=INTRO;failed=false;return true;}
        if(step==LAYOUT){step=DEVICE;failed=false;return true;}
        if(step==CONNECT){step=LAYOUT;failed=false;return true;}
        if(step==REVIEW){step=CONNECT;failed=false;return true;}return false;
    }
    boolean begin(long now,boolean active,boolean busy,String error){
        if(step!=REVIEW||waiting||busy)return false;
        if(active&&error.isEmpty()){step=READY;browsing=false;return false;}
        waiting=true;sawBusy=false;failed=false;requestedAt=now;previousError=error;step=WORKING;return true;
    }
    void observe(long now,boolean active,boolean busy,String error){
        if(busy){if(!waiting){requestedAt=now;previousError=error;}waiting=true;sawBusy=true;step=WORKING;return;}
        if(active){waiting=false;failed=!error.isEmpty();if(failed)browsing=false;if(!browsing)step=failed?REVIEW:READY;return;}
        if(waiting&&(sawBusy||!error.equals(previousError)||now<requestedAt||now-requestedAt>=10_000)){
            waiting=false;failed=true;step=REVIEW;
        }
    }
}
