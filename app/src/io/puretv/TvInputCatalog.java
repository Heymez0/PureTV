package io.puretv;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import java.util.ArrayList;
import java.util.List;

/** Read-only platform inventory. No tuning sessions, preview video, polling or port placeholders. */
final class TvInputCatalog {
    private TvInputCatalog(){}
    static List<HomeApps.Item> hdmi(Context c,boolean inactive){
        List<TvInputRules.Raw> raw=new ArrayList<>();
        TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        if(manager!=null)try{
            List<TvInputInfo> inputs=manager.getTvInputList();
            for(int i=0;i<inputs.size()&&i<256;i++){
                TvInputInfo info=inputs.get(i);if(info.getType()!=TvInputInfo.TYPE_HDMI)continue;
                String label="",custom="";int state=TvInputRules.DISCONNECTED;
                try{CharSequence text=info.loadLabel(c);label=text==null?"":TvInputRules.clean(text.toString());}catch(RuntimeException ignored){}
                try{CharSequence text=info.loadCustomLabel(c);custom=text==null?"":text.toString();}catch(RuntimeException ignored){}
                try{state=manager.getInputState(info.getId());}catch(RuntimeException ignored){}
                raw.add(new TvInputRules.Raw(info.getId(),info.getParentId(),label,custom,state));
            }
        }catch(RuntimeException ignored){}
        List<HomeApps.Item> out=new ArrayList<>();
        for(TvInputRules.Port port:TvInputRules.resolve(raw,inactive)){
            HomeApps.Item item=HomeApps.action(HomeApps.INPUT,"input:"+port.id,port.label,HomeApps.deviceSymbol(port.label));
            item.inputId=port.id;item.number=Integer.toString(port.number);item.connected=port.state==TvInputRules.CONNECTED;
            out.add(item);
        }
        return out;
    }
}
