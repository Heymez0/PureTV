package io.puretv;
import java.util.*;

final class PosterPlanRegression {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static List<String> list(String... values){return Arrays.asList(values);}
    public static void main(String[] args){
        PosterPlan<String> plan=new PosterPlan<>(value->value);
        for(int recent=0;recent<=3;recent++)for(int recommended=0;recommended<=3;recommended++){
            List<String> a=list("a","b","c").subList(0,recent),b=list("d","e","f").subList(0,recommended);
            plan.set(a,b);ok(plan.size()==recent+recommended);
            Map<String,Object> tokens=new HashMap<>();for(String key:plan.keys())tokens.put(key,plan.token(key));
            for(int trip=0;trip<100;trip++){
                plan.set(b,a);ok(plan.size()==recent+recommended);for(String key:plan.keys())ok(plan.matches(key,tokens.get(key)));
                plan.set(a,b);ok(plan.size()==recent+recommended);for(String key:plan.keys())ok(plan.matches(key,tokens.get(key)));
            }
        }
        plan.set(list("a","b","c","extra"),list("d","e","f","extra2"));ok(plan.size()==6&&!plan.keys().contains("extra"));
        ok(new ArrayList<>(plan.values()).equals(list("a","b","c","d","e","f")));
        Object a=plan.token("a"),b=plan.token("b");plan.set(list("d","e","f"),list("b","c"));ok(!plan.matches("a",a)&&plan.matches("b",b));
        plan.set(list("a","b","c"),list("d","e","f"));ok(!plan.matches("a",a)&&plan.matches("b",b));
        plan.set(list("a","b","c"),list("a","b","d"));ok(plan.size()==4);
        plan.retain(new HashSet<>(list("a","d")));ok(plan.size()==2&&!plan.matches("b",b));
        plan.set(list("","a","a","b"),list("a","d"));ok(plan.size()==2); // The window has three slots, not three distinct nonblank URIs.
        boolean immutable=false;try{plan.keys().clear();}catch(UnsupportedOperationException expected){immutable=true;}ok(immutable);
        plan.set(Collections.emptyList(),Collections.emptyList());ok(plan.size()==0&&plan.token("a")==null);
        System.out.println("PASS: 3,200 tab reversals; 0..3 actual slots per tab; max six; stable in-flight identities; active priority; removed/re-added URI rejection; deduplication; privacy subset; clock empty.");
    }
}
