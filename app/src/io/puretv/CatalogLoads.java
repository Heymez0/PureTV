package io.puretv;

/** Coalesces refreshes and rejects results from before a package, preference or memory change. */
final class CatalogLoads {
    private boolean stale=true,loading;
    private int generation,prefs=-1,layout=-1;
    void invalidate(){stale=true;generation++;}
    int begin(int prefs,int layout){
        if(loading||!stale&&this.prefs==prefs&&this.layout==layout)return -1;
        this.prefs=prefs;this.layout=layout;loading=true;stale=false;return generation;
    }
    boolean complete(int token,int currentPrefs,int currentLayout){
        loading=false;
        if(token!=generation||currentPrefs!=prefs||currentLayout!=layout){stale=true;return false;}
        return true;
    }
    void failed(){loading=false;invalidate();}
}
