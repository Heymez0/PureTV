package io.puretv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Local viewing-data master switch and independent per-app source choices. */
public class ContinueSettingsActivity extends Activity implements ContinueWatching.Listener {
    private SettingsPage page;
    private ContinueWatching source;
    private boolean resumed,pendingEnable;
    private static final int ACCESS = 41;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);pendingEnable=state!=null&&state.getBoolean("pending_enable",false);source=ContinueWatching.get(this);rebuild();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("pending_enable",pendingEnable);super.onSaveInstanceState(state);}
    @Override protected void onResume() {super.onResume();resumed=true;source.addListener(this);page.resume();}
    @Override protected void onPause() {resumed=false;page.pause();source.removeListener(this);super.onPause();}
    @Override public void onContinueWatching() {rebuild();}
    private void toggleViewing() {
        // On without the grant (e.g. after a restore on a new install): OK asks for it instead of turning viewing off.
        if(Prefs.viewingInformation()&&ContinueWatching.permitted(this)){setViewing(false);return;}
        if(!ContinueWatching.permitted(this)){
            pendingEnable=true;requestPermissions(new String[]{ContinueWatching.PERMISSION},ACCESS);
        }else setViewing(true);
    }
    private void setViewing(boolean enabled){
        Prefs.setViewingInformation(enabled);
        source.setVisible(java.util.Collections.emptyList());source.trim();source.refresh(true);rebuild();
    }
    private void rebuild() {
        int focus=page==null?0:page.focusedIndex();if(page!=null)page.pause();
        ContinueWatching.Snapshot snapshot=source.snapshot();
        List<SettingsPage.Row> rows=new ArrayList<>();
        SettingsPage.Row master=new SettingsPage.Row(Ui.tr("TV viewing information"),d->toggleViewing(),false);
        master.hint=Ui.tr("When off, only the clock layout is available. App choices are kept. OK enables or disables viewing information.");
        rows.add(master);
        for(ContinueWatching.Source app:snapshot.sources)rows.add(new SettingsPage.Row(app.label,d->{
            Prefs.setContinueAllowed(app.pkg,!Prefs.continueSelected(app.pkg));source.refresh(true);
        },true));
        SettingsPage.Data data=new SettingsPage.Data(Ui.tr("Content apps"),
                Ui.tr("Recently watched and recommendations"),"tv",rows.toArray(new SettingsPage.Row[0]));
        data.hint=Ui.tr("Escolha quais aplicativos aparecem no início. Só aparecem conteúdos que o aplicativo compartilha com a TV.",
                "Choose which apps appear on Home. Only content shared by the app with the TV is shown.")+"\n\n"
                +Ui.tr("Sem conteúdo: abra o aplicativo, assista a algo e volte ao início. Alguns aplicativos não compartilham esses dados.",
                "No content: open the app, watch something and return Home. Some apps do not share this information.");
        page=new SettingsPage(this,data,()->{
            boolean viewing=Prefs.viewingInformation();java.util.Set<String> hidden=Prefs.continueHidden();
            data.rows[0].value=!viewing?Ui.tr("Off"):ContinueWatching.permitted(this)?Ui.tr("On"):Ui.tr("On · permission required");
            boolean hiddenByClock=!Prefs.continueWatching();
            for(int i=0;i<snapshot.sources.size();i++) {
                ContinueWatching.Source app=snapshot.sources.get(i);
                data.rows[i+1].value=hidden.contains(app.pkg)?Ui.tr("Desligado","Off")
                        :!viewing?Ui.tr("On · viewing information is off")
                        :hiddenByClock?Ui.tr("On · hidden in clock layout")
                        :app.count>0?Ui.tr("Ligado","On")+" · "+app.count+" "+Ui.tr("itens","items"):Ui.tr("Ligado · sem conteúdo","On · no content yet");
            }
        });
        page.select(Math.min(focus,rows.size()-1));setContentView(page);if(resumed)page.resume();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==ACCESS) {
            if(pendingEnable&&ContinueWatching.permitted(this))setViewing(true);
            else if(pendingEnable&&Prefs.viewingInformation())setViewing(false);   // refused: off, so OK can still switch it
            pendingEnable=false;source.refresh(true);rebuild();
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {return page.onKey(event)||super.dispatchKeyEvent(event);}
}
