package io.puretv;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

/** Caller-locked bounded working set. Byte trimming is only for hidden retention. */
final class PosterCache<T> {
    private static final class Entry<T> {
        final T value; final long bytes;
        Entry(T value,long bytes){this.value=value;this.bytes=Math.max(0,bytes);}
    }
    private final int capacity;
    private final LinkedHashMap<String,Entry<T>> entries=new LinkedHashMap<>(4,.75f,true);
    PosterCache(int capacity){this.capacity=Math.max(0,capacity);}
    T get(String key){Entry<T> entry=entries.get(key);return entry==null?null:entry.value;}
    void put(String key,T value,long bytes){
        entries.put(key,new Entry<>(value,bytes));
        while(entries.size()>capacity)removeOldest();
    }
    int size(){return entries.size();}
    boolean isEmpty(){return entries.isEmpty();}
    long bytes(){
        long total=0;
        for(Entry<T> entry:entries.values()){
            if(entry.bytes>Long.MAX_VALUE-total)return Long.MAX_VALUE;
            total+=entry.bytes;
        }
        return total;
    }
    void retain(Set<String> keys){
        Iterator<String> it=entries.keySet().iterator();
        while(it.hasNext())if(!keys.contains(it.next()))it.remove();
    }
    void trimTo(long budget){
        budget=Math.max(0,budget);
        while(!entries.isEmpty()&&bytes()>budget)removeOldest();
    }
    void clear(){entries.clear();}
    private void removeOldest(){Iterator<String> it=entries.keySet().iterator();if(it.hasNext()){it.next();it.remove();}}
}
