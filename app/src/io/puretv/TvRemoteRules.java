package io.puretv;

/** Foreground HOME actions only. Power, volume, channels, voice and controller buttons stay with Android. */
final class TvRemoteRules {
    static final int NONE=0,INPUTS=1,LIVE=2,QUICK=3,HDMI1=11;
    private TvRemoteRules(){}
    static int action(boolean tv,boolean enabled,boolean controller,int key){
        if(!tv||!enabled||controller)return NONE;
        if(key==178)return INPUTS; // KEYCODE_TV_INPUT
        if(key==170)return LIVE;   // KEYCODE_TV
        if(key==82)return QUICK;   // KEYCODE_MENU
        if(key>=243&&key<=246)return HDMI1+key-243;
        return NONE;
    }
}
