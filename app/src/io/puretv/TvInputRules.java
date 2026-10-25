package io.puretv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small immutable inventory; physical ports own CEC children, never the other way round. */
final class TvInputRules {
    static final int CONNECTED=0,STANDBY=1,DISCONNECTED=2,MAX=32;
    private static final Pattern NUMBER=Pattern.compile("(?i)HDMI\\s*[-:]?\\s*(\\d{1,2})(?!\\d)");
    static final class Raw {
        final String id,parent,label,custom;final int state;
        Raw(String id,String parent,String label,String custom,int state){
            this.id=id;this.parent=parent;this.label=clean(label);this.custom=clean(custom);this.state=state;
        }
    }
    static final class Port {
        final String id,label;final int number,state;
        Port(String id,String label,int number,int state){this.id=id;this.label=label;this.number=number;this.state=state;}
    }
    private TvInputRules(){}
    static String clean(String text){
        if(text==null)return "";StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length()&&out.length()<80;i++){char c=text.charAt(i);if(!Character.isISOControl(c))out.append(c);}
        return out.toString().trim();
    }
    static List<Port> resolve(List<Raw> raw,boolean inactive){
        Map<String,Raw> children=new HashMap<>();List<Raw> parents=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Raw r:raw){
            if(r==null||r.id==null||r.id.isEmpty()||r.id.length()>1024||!seen.add(r.id))continue;
            if(r.parent==null||r.parent.isEmpty()){if(parents.size()<MAX)parents.add(r);}
            else {Raw old=children.get(r.parent);if(old==null||r.state==CONNECTED&&old.state!=CONNECTED)children.put(r.parent,r);}
        }
        parents.sort(Comparator.comparing(r->r.id));List<Port> out=new ArrayList<>();
        for(int i=0;i<parents.size();i++){
            Raw p=parents.get(i),child=children.get(p.id);int state=p.state;
            if(child!=null&&child.state==CONNECTED)state=CONNECTED;
            if(!inactive&&state!=CONNECTED)continue;
            Matcher match=NUMBER.matcher(p.label);int number=match.find()?Integer.parseInt(match.group(1)):i+1;
            if(number==0)number=i+1;
            String label=!p.custom.isEmpty()?p.custom:child!=null&&!child.label.isEmpty()?child.label:"HDMI "+number;
            out.add(new Port(p.id,label,number,state));
        }
        out.sort(Comparator.comparingInt((Port p)->p.number).thenComparing(p->p.id));return out;
    }
}
