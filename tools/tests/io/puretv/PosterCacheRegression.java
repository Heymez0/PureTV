package io.puretv;

import java.util.Arrays;
import java.util.HashSet;

/** Exercises the production working set with budgets smaller than one image. */
final class PosterCacheRegression {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static void fill(PosterCache<Object> cache,Object[] images){
        for(int i=0;i<images.length;i++)cache.put("p"+i,images[i],4_000_000);
    }
    public static void main(String[] args){
        Object[] images={new Object(),new Object(),new Object()};
        for(long budget:new long[]{-1,0,1,3_999_999,4_000_000,8_000_000,12_000_000,16_777_216}){
            PosterCache<Object> cache=new PosterCache<>(3);
            for(int trip=0;trip<100;trip++){
                fill(cache,images);
                // A hidden retention budget never caps the visible working set.
                ok(cache.size()==3&&cache.bytes()==12_000_000);
                for(int i=0;i<3;i++)ok(cache.get("p"+i)==images[i]);
                cache.trimTo(budget);
                ok(cache.bytes()<=Math.max(0,budget));
                int expected=(int)Math.min(3,Math.max(0,budget)/4_000_000);
                ok(cache.size()==expected);
                // Missing entries refill normally on return, without per-frame eviction.
                fill(cache,images);ok(cache.size()==3);
                for(int i=0;i<3;i++)ok(cache.get("p"+i)==images[i]);
            }
        }
        PosterCache<Object> cache=new PosterCache<>(3);fill(cache,images);
        cache.get("p0");cache.put("new",images[0],2);
        ok(cache.size()==3&&cache.get("p1")==null&&cache.get("p0")==images[0]);
        cache.retain(new HashSet<>(Arrays.asList("p0","new")));ok(cache.size()==2&&cache.bytes()==4_000_002);
        cache.put("p0",images[1],1);ok(cache.bytes()==3&&cache.get("p0")==images[1]);
        cache.trimTo(1);ok(cache.size()==1&&cache.get("p0")==images[1]);
        cache.clear();cache.put("oversized",images[0],17_000_000);cache.trimTo(16_777_216);ok(cache.isEmpty());
        cache.put("a",images[0],Long.MAX_VALUE);cache.put("b",images[1],Long.MAX_VALUE);ok(cache.bytes()==Long.MAX_VALUE);
        cache.trimTo(Long.MAX_VALUE-1);ok(cache.isEmpty());
        cache.put("negative",images[0],-100);ok(cache.bytes()==0);cache.clear();ok(cache.isEmpty());
        PosterCache<Object> zero=new PosterCache<>(0);zero.put("a",images[0],1);ok(zero.isEmpty());
        Object[] six={new Object(),new Object(),new Object(),new Object(),new Object(),new Object()};
        for(long budget:new long[]{-1,0,1,3_999_999,4_000_000,16_777_216,24_000_000,33_554_432}){
            PosterCache<Object> dual=new PosterCache<>(6);
            for(int trip=0;trip<100;trip++){
                fill(dual,six);ok(dual.size()==6&&dual.bytes()==24_000_000);
                dual.trimTo(budget);ok(dual.size()==Math.min(6,Math.max(0,budget)/4_000_000)&&dual.bytes()<=Math.max(0,budget));
                fill(dual,six);for(int i=0;i<6;i++)ok(dual.get("p"+i)==six[i]);
            }
        }
        System.out.println("PASS: 1,600 three/six-poster suspend/refill cycles; zero/tiny/exact/oversized byte budgets; visible working set; LRU; privacy subset; replacement; overflow; zero capacity.");
    }
}
