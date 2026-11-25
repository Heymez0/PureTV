package io.puretv;

final class WelcomeRegression {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static WelcomeFlow review(){WelcomeFlow flow=new WelcomeFlow();flow.next();ok(flow.step==WelcomeFlow.DEVICE);flow.next();ok(flow.step==WelcomeFlow.LAYOUT);flow.next();ok(flow.step==WelcomeFlow.LOOK);flow.next();ok(flow.step==WelcomeFlow.CITY);flow.next();ok(flow.step==WelcomeFlow.CONNECT);flow.next();ok(flow.step==WelcomeFlow.REVIEW);return flow;}
    public static void main(String[] args){
        for(int status=0;status<=3;status++)for(boolean active:new boolean[]{false,true})for(boolean legacy:new boolean[]{false,true})for(boolean busy:new boolean[]{false,true}){
            boolean show=WelcomeFlow.shouldShow(status,active,legacy,busy);
            if(status==WelcomeFlow.DONE||status==WelcomeFlow.DEFERRED)ok(!show);
            else if(status==WelcomeFlow.STARTED)ok(show==(!active||busy));
            else ok(show==(!active&&!legacy));
        }
        WelcomeFlow flow=new WelcomeFlow();ok(!flow.begin(0,false,false,""));flow.next();ok(!flow.begin(0,false,false,""));flow.back();ok(flow.step==0);
        flow=review();ok(flow.begin(100,false,false,"old"));for(int i=0;i<100;i++)ok(!flow.begin(101,false,false,"old"));
        flow.observe(500,false,false,"old");ok(flow.step==WelcomeFlow.WORKING&&flow.waiting);
        flow.observe(600,true,true,"");ok(flow.step==WelcomeFlow.WORKING); // Role assigned before cleanup completes is not completion.
        flow.observe(700,true,false,"");ok(flow.step==WelcomeFlow.READY&&!flow.waiting&&!flow.failed);
        flow=review();ok(flow.begin(100,false,false,"old"));flow.observe(200,false,true,"");flow.observe(300,false,false,"denied");ok(flow.failed&&!flow.waiting&&flow.step==WelcomeFlow.REVIEW);
        ok(flow.begin(400,false,false,"denied"));flow.observe(500,false,false,"new failure");ok(flow.failed&&flow.step==WelcomeFlow.REVIEW);
        flow=review();ok(flow.begin(100,false,false,""));flow.observe(10099,false,false,"");ok(flow.waiting);flow.observe(10100,false,false,"");ok(flow.failed&&!flow.waiting);
        flow=review();ok(!flow.begin(100,true,false,"")&&flow.step==WelcomeFlow.READY);
        flow=review();ok(flow.begin(100,false,false,""));flow.observe(200,true,false,"partial failure");ok(flow.failed&&flow.step==WelcomeFlow.REVIEW&&!flow.waiting);
        ok(flow.begin(300,true,false,"partial failure"));flow.observe(400,true,true,"");flow.observe(500,true,false,"");ok(flow.step==WelcomeFlow.READY&&!flow.failed);
        flow=review();ok(!flow.begin(100,false,true,""));
        flow=new WelcomeFlow();flow.observe(100,false,true,"");ok(flow.step==WelcomeFlow.WORKING);flow.observe(200,false,false,"failed");ok(flow.failed);
        flow=new WelcomeFlow();flow.restore(2);ok(flow.step==2);flow.restore(100);ok(flow.step==0);flow.restore(3);ok(flow.step==0);
        // The guide opened again after setup: Back walks READY → REVIEW → CONNECT → DEVICE → INTRO, and a poll that
        // still sees PureTV active must not throw the viewer forward to READY again.
        WelcomeFlow done=new WelcomeFlow();done.observe(0,true,false,"");ok(done.step==WelcomeFlow.READY);
        ok(done.back()&&done.step==WelcomeFlow.REVIEW);done.observe(500,true,false,"");ok(done.step==WelcomeFlow.REVIEW);
        ok(done.back()&&done.step==WelcomeFlow.CONNECT&&done.back()&&done.step==WelcomeFlow.CITY&&done.back()&&done.step==WelcomeFlow.LOOK&&done.back()&&done.step==WelcomeFlow.LAYOUT&&done.back()&&done.step==WelcomeFlow.DEVICE&&done.back()&&done.step==WelcomeFlow.INTRO&&!done.back());
        done.next();done.next();done.next();done.next();done.next();done.next();ok(done.step==WelcomeFlow.REVIEW&&!done.begin(900,true,false,"")&&done.step==WelcomeFlow.READY&&!done.browsing);
        flow.restore(WelcomeFlow.DEVICE);ok(flow.step==WelcomeFlow.DEVICE);flow.next();ok(flow.step==WelcomeFlow.LAYOUT);flow.next();ok(flow.step==WelcomeFlow.LOOK);flow.next();ok(flow.step==WelcomeFlow.CITY);flow.next();ok(flow.step==WelcomeFlow.CONNECT);ok(flow.back()&&flow.step==WelcomeFlow.CITY);ok(flow.back()&&flow.step==WelcomeFlow.LOOK);ok(flow.back()&&flow.step==WelcomeFlow.LAYOUT);ok(flow.back()&&flow.step==WelcomeFlow.DEVICE);ok(flow.back()&&flow.step==WelcomeFlow.INTRO);
        // Reinstall: HOME still opens PureTV (the TV's own home screen stayed off) but nothing is set up. Not "active",
        // so the guide shows and "Turn on PureTV" really runs the setup instead of jumping to READY.
        ok(!WelcomeFlow.setUp(true,false)&&!WelcomeFlow.setUp(false,true)&&WelcomeFlow.setUp(true,true));
        ok(WelcomeFlow.shouldShow(WelcomeFlow.NEW,WelcomeFlow.setUp(true,false),false,false));
        flow=review();ok(flow.begin(100,WelcomeFlow.setUp(true,false),false,"")&&flow.waiting);
        flow=new WelcomeFlow();flow.restore(WelcomeFlow.CITY);ok(flow.step==WelcomeFlow.CITY);
        flow=new WelcomeFlow();flow.restore(WelcomeFlow.LOOK);ok(flow.step==WelcomeFlow.LOOK);
        // Background photos: Automatic keeps one photo per day (and one per night), from the matching set.
        long day=86_400_000L;Scenes.Scene a=Scenes.pick(Scenes.AUTO,false,10*day+3_600_000L),b=Scenes.pick(Scenes.AUTO,false,10*day+7_200_000L);
        ok(a!=null&&a==b&&!a.night);ok(Scenes.pick(Scenes.AUTO,true,10*day).night);ok(Scenes.pick(Scenes.CLASSIC,false,0)==null);
        ok(Scenes.pick("castle",true,0).id.equals("castle"));ok(Scenes.normalize("nonsense").equals(Scenes.AUTO));
        ok(HomeTabs.up(HomeTabs.WEATHER)==HomeTabs.WEATHER&&HomeTabs.up(0)==HomeTabs.ACTION&&!HomeTabs.isTab(HomeTabs.WEATHER));
        ok(HomeTabs.next(HomeTabs.ACTION,-1,false,false,true)==HomeTabs.WEATHER&&HomeTabs.next(HomeTabs.ACTION,-1,false,false,false)==HomeTabs.ACTION);
        // The weather city's first guess comes from the TV's time zone; zones that aren't a place give nothing.
        ok(WelcomeFlow.zoneCity("Europe/Istanbul").equals("Istanbul"));ok(WelcomeFlow.zoneCity("America/Argentina/Buenos_Aires").equals("Buenos Aires"));
        ok(WelcomeFlow.zoneCity("America/Port-au-Prince").equals("Port-au-Prince"));
        for(String zone:new String[]{null,"","UTC","GMT","Etc/GMT+3","Etc/UTC","SystemV/EST5","Europe/","Zulu"})ok(WelcomeFlow.zoneCity(zone).isEmpty());
        System.out.println("PASS: background step, daily landscape choice and weather pill focus; weather city step and time-zone guess; reinstall without setup; fresh/legacy/deferred/completed routing; interrupted setup; explicit final-confirm gate; double activation blocked; role-before-busy completion; failure/retry; stale-result grace; timeout; restored pages.");
    }
}
