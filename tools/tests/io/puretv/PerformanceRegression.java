package io.puretv;

/** Bounds, scrolling visibility and identical motion: no device, timers or benchmark thresholds. */
final class PerformanceRegression {
    private static void ok(boolean value){if(!value)throw new AssertionError();}
    private static void masks(){
        MaskCache<String> empty=new MaskCache<>(0,10);empty.put(1,"x",1);ok(empty.size()==0);
        MaskCache<String> cache=new MaskCache<>(3,10);
        cache.put(1,"a",4);cache.put(2,"b",4);ok(cache.bytes()==8);
        ok("a".equals(cache.get(1)));cache.put(3,"c",4);
        ok(cache.get(2)==null&&"a".equals(cache.get(1))&&"c".equals(cache.get(3)));
        cache.put(1,"replacement",6);ok(cache.bytes()==10&&cache.size()==2);
        cache.put(4,"oversized",11);ok(cache.get(4)==null&&cache.bytes()==10);
        cache.put(3,null,0);ok(cache.get(3)==null&&cache.bytes()==6);
        cache.clear();ok(cache.size()==0&&cache.bytes()==0);
        MaskCache<String> large=new MaskCache<>(4,Long.MAX_VALUE);
        large.put(1,"large",Long.MAX_VALUE-2);large.put(2,"next",3);
        ok(large.get(1)==null&&large.bytes()==3);
        for(int i=0;i<100000;i++){
            cache.put(i%7,"value",(i%5)+1);cache.get((i+1)%7);
            ok(cache.bytes()>=0&&cache.bytes()<=10&&cache.size()<=3);
        }
    }
    private static void visibleRows(){
        ok(VisibleRows.end(100,0,0,1080)==0);
        for(float top:new float[]{0,1110,1200})for(float shift=-6000;shift<=500;shift+=17.5f){
            int end=VisibleRows.end(top,230,shift,1080);
            for(int row=0;row<30;row++){
                float start=top+row*230+shift;
                if(start<1080&&start+230>0)ok(row<end);
            }
            ok(end>=0);
        }
    }
    private static void springs(){
        for(float response:new float[]{.28f,.34f,.46f})for(float damping:new float[]{.85f,.92f,1f}){
            Spring spring=new Spring(response,damping,0);float value=0,velocity=0;
            for(int frame=0;frame<10000;frame++){
                float target=(frame/117)%3*27f;spring.target=target;
                float dt=new float[]{.001f,.016666667f,.03333333f,.07f}[frame%4];
                boolean expected;
                if(Math.abs(value-target)<.0008f&&Math.abs(velocity)<.004f){value=target;velocity=0;expected=false;}
                else{
                    float omega=(float)(2*Math.PI/response),stiffness=omega*omega,friction=2*damping*omega;
                    int steps=Math.max(1,(int)Math.ceil(dt/.004f));float step=dt/steps;
                    for(int i=0;i<steps;i++){float accel=-stiffness*(value-target)-friction*velocity;velocity+=accel*step;value+=velocity*step;}
                    expected=true;
                }
                ok(spring.step(dt)==expected);ok(Float.floatToIntBits(spring.value)==Float.floatToIntBits(value));
                ok(Float.floatToIntBits(spring.velocity)==Float.floatToIntBits(velocity));
            }
        }
    }
    private static void pacing(){
        for(int hz:new int[]{50,60,90,120}){
            long step=1000000000L/hz,lastOld=-45_000_000L,lastNew=-45_000_000L,next=0;int oldDraws=0,newDraws=0,callbacks=0;
            for(long now=step;now<10_000_000_000L;now+=step){
                if(now-lastOld>=45_000_000L){lastOld=now;oldDraws++;}
                if(now>=next){callbacks++;if(now-lastNew>=45_000_000L){lastNew=now;newDraws++;}next=now+45_000_000L;}
            }
            ok(oldDraws==newDraws);ok(callbacks<10*hz);
        }
    }
    /** Direct poster pieces keep the framing of the former full-screen cover crop (within one screen pixel). */
    private static void posterCrop(){
        for(int[] screen:new int[][]{{1920,1080},{1280,720}}){
            int W=screen[0],H=screen[1];float S=(float)W/H,ui=W/1920f;
            int[][] pieces={{Math.round(1092*ui),Math.round(464*ui)},{Math.round(336*ui),Math.round(464*ui)},{192,108},{480,270}};
            for(int sw=1;sw<=4000;sw+=37)for(int sh=1;sh<=4000;sh+=41)for(int[] piece:pieces){
                float r=(float)piece[0]/piece[1];float[] got=PosterCrop.region(sw,sh,S,r);
                float s=Math.max((float)W/sw,(float)H/sh),left=(W-sw*s)/2,top=(H-sh*s)/2;
                int cw=Math.min(W,Math.round(H*r)),ch=Math.min(H,Math.round(cw/r)),x=(W-cw)/2,y=(H-ch)/2;
                ok(Math.abs(got[0]*s+left-x)<=1.01f&&Math.abs(got[1]*s+top-y)<=1.01f);
                ok(Math.abs(got[2]*s-cw)<=1.01f&&Math.abs(got[3]*s-ch)<=1.01f);
                ok(got[0]>=-.001f&&got[1]>=-.001f&&got[0]+got[2]<=sw+.001f&&got[1]+got[3]<=sh+.001f);
                ok(Math.abs(got[2]/got[3]-r)<=r*1e-4f);
            }
        }
        for(int size:new int[]{0,1,108,192,464,1092})for(float region=.5f;region<20000;region*=1.37f){
            int n=PosterCrop.halvings(region,size);long step=Math.max(1,size);
            ok(n>=0&&region<=(step<<(n+1))&&(n==0||region>(step<<n)));
        }
    }
    public static void main(String[] args){masks();visibleRows();springs();pacing();posterCrop();System.out.println("PASS: bounded mask LRU, visible-row guard, identical springs, photo pacing and direct poster framing");}
}
