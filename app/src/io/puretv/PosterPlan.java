package io.puretv;

import java.util.*;
import java.util.function.Function;

/** Two bounded windows. A retained URI keeps its request identity when tabs switch. Caller-locked. */
final class PosterPlan<T> {
    private final Function<T,String> keyOf;
    private final LinkedHashMap<String,T> order=new LinkedHashMap<>();
    private final Map<String,Object> tokens=new HashMap<>();
    private final Set<String> active=new HashSet<>();
    private final Set<String> keys=Collections.unmodifiableSet(order.keySet());
    private final Collection<T> values=Collections.unmodifiableCollection(order.values());
    PosterPlan(Function<T,String> keyOf){this.keyOf=keyOf;}
    void set(List<T> visible,List<T> standby){
        LinkedHashMap<String,T> next=new LinkedHashMap<>();append(next,visible);
        active.clear();active.addAll(next.keySet());append(next,standby);
        tokens.keySet().retainAll(next.keySet());
        for(String key:next.keySet())if(!tokens.containsKey(key))tokens.put(key,new Object());
        order.clear();order.putAll(next);
    }
    private void append(Map<String,T> into,List<T> window){
        for(int i=0;i<Math.min(3,window.size());i++){
            T value=window.get(i);String key=keyOf.apply(value);
            if(key!=null&&!key.isEmpty()&&!into.containsKey(key))into.put(key,value);
        }
    }
    Set<String> keys(){return keys;}
    Collection<T> values(){return values;}
    Set<String> activeKeys(){return active;}
    int size(){return order.size();}
    Object token(String key){return tokens.get(key);}
    boolean matches(String key,Object token){return token!=null&&tokens.get(key)==token;}
    void retain(Set<String> allowed){
        order.keySet().retainAll(allowed);tokens.keySet().retainAll(allowed);active.retainAll(allowed);
    }
}
