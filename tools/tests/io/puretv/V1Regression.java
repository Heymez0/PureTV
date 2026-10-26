package io.puretv;
import java.io.*;
import java.util.*;
class V1Regression {
 static void ok(boolean v){if(!v)throw new AssertionError();}
 static void bounded()throws Exception{
  byte[] data=new byte[8193];Arrays.fill(data,(byte)9);
  ok(Arrays.equals(data,BoundedInput.read(new ByteArrayInputStream(data),8193)));
  ok(BoundedInput.read(new ByteArrayInputStream(new byte[0]),1).length==0);
  boolean rejected=false;try{BoundedInput.read(new ByteArrayInputStream(data),8192);}catch(IOException expected){rejected=true;}ok(rejected);
  InputStream lying=new InputStream(){int at;public int available(){return 0;}public int read(){return at++<100000?7:-1;}};
  rejected=false;try{BoundedInput.read(lying,100);}catch(IOException expected){rejected=true;}ok(rejected);
 }
 static void refreshes(){
  RefreshGate g=new RefreshGate();long a=g.begin(false);ok(a>=0);ok(g.begin(false)==-1);ok(g.complete(a));
  a=g.begin(false);g.invalidate();ok(g.begin(false)==-1);ok(!g.complete(a));long b=g.begin(false);ok(b>a);ok(g.complete(b));
  a=g.begin(false);for(int i=0;i<50;i++)ok(g.begin(true)==-1);ok(!g.complete(a));b=g.begin(false);ok(g.complete(b));
  a=g.begin(false);ok(g.complete(a)); // Stable reads are accepted after a burst has settled.
 }
 static ContinueWatching.Program p(String title,String episode,long position,int type,String app){
  return new ContinueWatching.Program(1,100,position,1000,type,"com.example.video",app,title,episode,"https://example.com/poster","example://play/1");
 }
 static ContinueWatching.Snapshot snap(ContinueWatching.Program p,int count,boolean permission,boolean failed){
  return new ContinueWatching.Snapshot(new ArrayList<>(Arrays.asList(p)),new ArrayList<>(),
   new ArrayList<>(Arrays.asList(new ContinueWatching.Source("com.example.video","Video",count))),permission,failed);
 }
 static void snapshots(){
  ContinueWatching.Program a=p("A:B","C",10,0,"Video"),same=p("A:B","C",10,0,"Video");
  ok(a.same(same));ok(snap(a,1,true,false).sameData(snap(same,1,true,false)));
  ok(!a.same(p("A","B:C",10,0,"Video"))); // Colon-delimited keys previously collided.
  ok(!a.same(p("A:B","C",11,0,"Video")));ok(!a.same(p("A:B","C",10,1,"Video")));
  ok(!a.same(p("A:B","C",10,0,"Renamed app")));
  ok(!snap(a,1,true,false).sameData(snap(a,2,true,false)));
  ok(!snap(a,1,true,false).sameData(snap(a,1,false,false)));
  ok(!snap(a,1,true,false).sameData(snap(a,1,true,true)));
  boolean immutable=false;try{snap(a,1,true,false).programs.add(a);}catch(UnsupportedOperationException expected){immutable=true;}ok(immutable);
 }
 static void shelves(){
  List<String> fixed=Arrays.asList("all","a","b","c");
  ok(!SectionOrder.move(fixed,0,1,1));ok(SectionOrder.move(fixed,1,3,1));ok(fixed.equals(Arrays.asList("all","b","c","a")));
  ok(SectionOrder.move(fixed,3,1,1));ok(fixed.equals(Arrays.asList("all","a","b","c")));
 }
 static void settings(){
  for(int count:new int[]{1,8,24,100}){
   int at=0;for(int i=1;i<count;i++){at=SettingsNavigation.next(at,1,count);ok(at==i);}
   ok(SettingsNavigation.next(at,1,count)==count-1);
   for(int i=count-2;i>=0;i--){at=SettingsNavigation.next(at,-1,count);ok(at==i);}
   ok(at==0);ok(SettingsNavigation.next(at,-1,count)==0);
  }
  ok(SettingsNavigation.next(0,1,0)==0);
  ok(Arrays.equals(HomeContentModes.choices(false),new String[]{"clock"}));
  ok(Arrays.equals(HomeContentModes.choices(true),new String[]{"clock","recent","combined"}));
  for(String mode:new String[]{"clock","recent","combined",null,"obsolete"})ok("clock".equals(HomeContentModes.allowed(mode,false)));
  for(String mode:HomeContentModes.choices(true))ok(mode.equals(HomeContentModes.allowed(mode,true)));
 }
 public static void main(String[] args)throws Exception{
  bounded();refreshes();snapshots();shelves();settings();
  ok(ClockFormat.minuteDelay(0)==60000);ok(ClockFormat.minuteDelay(1)==59999);ok(ClockFormat.minuteDelay(59999)==1);ok(ClockFormat.minuteDelay(-1)==1);
  for(String mode:new String[]{"clock","recent","combined"})ok(mode.equals(HomeContentModes.next(HomeContentModes.next(mode,1),-1)));
  System.out.println("PASS: bounded streams; stale-city/event generations; burst coalescing; metadata equality; immutable snapshots; shelf movement; long settings navigation; viewing master/layout gate.");
 }
}
