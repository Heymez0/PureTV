package io.puretv;
import java.util.*;

final class TvModeRegression {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static TvInputRules.Raw port(String id,String parent,String label,String custom,int state){return new TvInputRules.Raw(id,parent,label,custom,state);}
    public static void main(String[] args){
        for(String raw:new String[]{null,"box","stick","TV","obsolete","tv"})ok(DeviceProfiles.normalize(raw).equals("tv".equals(raw)?"tv":"box"));
        List<TvInputRules.Raw> raw=new ArrayList<>(Arrays.asList(
            port("port10",null,"HDMI 10","",2),port("port2",null,"HDMI 2","Oyun",1),port("port1",null,"HDMI 1","",2),
            port("cec-sleep","port1","Old device","",1),port("cec-on","port1","PS5","",0),
            port("orphan","missing","Ghost","",0),port("port2",null,"duplicate","",0)));
        List<TvInputRules.Port> all=TvInputRules.resolve(raw,true);ok(all.size()==3);ok(all.get(0).label.equals("PS5")&&all.get(0).state==0);
        ok(all.get(1).label.equals("Oyun")&&all.get(1).state==1);ok(all.get(2).number==10);ok(all.get(0).id.equals("port1"));
        List<TvInputRules.Port> active=TvInputRules.resolve(raw,false);ok(active.size()==1&&active.get(0).id.equals("port1"));
        raw.set(4,port("cec-on","port1","PS5","",2));ok(TvInputRules.resolve(raw,false).isEmpty());
        ok(TvInputRules.resolve(Collections.emptyList(),true).isEmpty());
        raw.clear();for(int i=0;i<1000;i++)raw.add(port("id"+i,null,"HDMI "+i,"",0));ok(TvInputRules.resolve(raw,true).size()==TvInputRules.MAX);
        raw=Arrays.asList(port("one",null,null,null,2),port("two",null,"HDMI 2","\nCustom\u0000",0));
        all=TvInputRules.resolve(raw,true);ok(all.get(0).label.equals("HDMI 1"));ok(all.get(1).label.equals("Custom"));
        ok(TvInputRules.clean(String.join("",Collections.nCopies(1000,"a"))).length()==80);
        for(int key=0;key<5000;key++){
            int action=TvRemoteRules.action(true,true,false,key);boolean known=key==178||key==170||key==82||key>=243&&key<=246;
            ok((action!=0)==known);ok(TvRemoteRules.action(false,true,false,key)==0);ok(TvRemoteRules.action(true,false,false,key)==0);ok(TvRemoteRules.action(true,true,true,key)==0);
        }
        System.out.println("PASS: explicit profile defaults; CEC dedup/active child/custom labels; numeric ordering; inactive/TCL standby filter; missing/empty inputs; bounded catalogue; foreground-only remote allowlist; box/off/gamepad pass-through.");
    }
}
