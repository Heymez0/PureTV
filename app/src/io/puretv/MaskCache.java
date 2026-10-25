package io.puretv;

/** Small primitive-key LRU for transient shadow masks; never owns or recycles a displayed bitmap. */
final class MaskCache<T> {
    private final long[] keys, used, weights;
    private final Object[] values;
    private final long budget;
    private long bytes, clock;
    private int size;
    MaskCache(int capacity, long budget) {
        int count = Math.max(0, capacity);
        keys = new long[count]; used = new long[count]; weights = new long[count]; values = new Object[count];
        this.budget = Math.max(0, budget);
    }
    @SuppressWarnings("unchecked") T get(long key) {
        for (int i=0;i<values.length;i++) if (values[i]!=null && keys[i]==key) {
            used[i]=++clock; return (T) values[i];
        }
        return null;
    }
    void put(long key, T value, long weight) {
        weight=Math.max(0,weight);
        for(int i=0;i<values.length;i++) if(values[i]!=null && keys[i]==key){remove(i);break;}
        if(value==null || values.length==0 || weight>budget)return;
        while(size==values.length || weight>budget-bytes)remove(oldest());
        for(int i=0;i<values.length;i++) if(values[i]==null){
            keys[i]=key;values[i]=value;weights[i]=weight;used[i]=++clock;bytes+=weight;size++;return;
        }
    }
    private int oldest(){int at=-1;for(int i=0;i<values.length;i++)if(values[i]!=null&&(at<0||used[i]<used[at]))at=i;return at;}
    private void remove(int i){bytes-=weights[i];values[i]=null;weights[i]=used[i]=0;size--;}
    long bytes(){return bytes;}
    int size(){return size;}
    void clear(){java.util.Arrays.fill(values,null);java.util.Arrays.fill(weights,0);java.util.Arrays.fill(used,0);bytes=clock=0;size=0;}
}
