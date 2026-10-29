package io.puretv;

import java.util.*;

/** Settings backup allowlist, receiver download rules and poster decode sampling: no device or network. */
final class FeatureRegression {
    private static void ok(boolean value){if(!value)throw new AssertionError();}

    private static void backup(){
        Map<String,Object> in=new HashMap<>();
        in.put("language","tr");in.put("theme","dark");in.put("temperature","C");in.put("clock_format","24");
        in.put("device_mode","tv");in.put("home_content_mode","combined");in.put("viewing_previous_mode","recent");
        in.put("city"," Istanbul ");in.put("latitude","41.0082");in.put("longitude","28.9784");
        in.put("shelf","com.netflix.ninja,com.netflix.ninja,bad pkg,com.google.android.youtube.tv");
        in.put("quick_shortcuts","airplay,unknown,status");in.put("other_apps_order","puretv-settings,com.spotify.tv.android");
        in.put("weather",true);in.put("sounds",false);in.put("lightweight","true");
        in.put("continue_hidden",new HashSet<>(Arrays.asList("com.netflix.ninja","not a package")));
        // Never accepted: HOME state, ADB/API keys, hidden-content hashes, anything unknown.
        in.put("mode","free");in.put("tmdb_key","x");in.put("continue_hidden_content",new HashSet<>(Collections.singleton("abc")));
        in.put("disabled","com.google.android.tvlauncher");
        Map<String,Object> out=BackupRules.clean(in);
        ok("tr".equals(out.get("language"))&&"dark".equals(out.get("theme"))&&"C".equals(out.get("temperature")));
        ok("24".equals(out.get("clock_format"))&&"tv".equals(out.get("device_mode"))&&"combined".equals(out.get("home_content_mode")));
        ok("Istanbul".equals(out.get("city"))&&"41.0082".equals(out.get("latitude")));
        ok("com.netflix.ninja,com.google.android.youtube.tv".equals(out.get("shelf")));
        ok("all,airplay,status".equals(out.get("quick_shortcuts")));
        ok("puretv-settings,com.spotify.tv.android".equals(out.get("other_apps_order")));
        ok(Boolean.TRUE.equals(out.get("weather"))&&Boolean.FALSE.equals(out.get("sounds"))&&!out.containsKey("lightweight"));
        ok(out.get("continue_hidden").equals(Collections.singleton("com.netflix.ninja")));
        for(String key:new String[]{"mode","tmdb_key","continue_hidden_content","disabled"})ok(!out.containsKey(key));
        Map<String,Object> bad=new HashMap<>();
        bad.put("language","../../etc");bad.put("theme","neon");bad.put("latitude","91");bad.put("longitude","NaN");
        bad.put("city","line\nbreak");bad.put("device_mode","phone");bad.put("home_content_mode","apps");bad.put("clock_format","13");
        ok(BackupRules.clean(bad).isEmpty());ok(BackupRules.clean(null).isEmpty());
        StringBuilder huge=new StringBuilder();for(int i=0;i<500;i++)huge.append("com.app.n").append(i).append(',');
        Map<String,Object> many=new HashMap<>();many.put("shelf",huge.toString());
        ok(((String)BackupRules.clean(many).get("shelf")).split(",").length==64);
        Map<String,Object> keys=new HashMap<>();keys.put("4062","com.netflix.ninja");keys.put("190","");keys.put("4099","rm -rf");keys.put("1","com.x.y");
        Map<Integer,String> cleanKeys=BackupRules.cleanKeys(keys,new int[]{4062,190,4099});
        ok(cleanKeys.size()==2&&"com.netflix.ninja".equals(cleanKeys.get(4062))&&"".equals(cleanKeys.get(190)));
    }

    private static void install(){
        ok(InstallRules.allowed(InstallRules.URL));
        ok(InstallRules.allowed("https://objects.githubusercontent.com/github-production-release-asset/1/2?x=y"));
        ok(InstallRules.allowed("https://release-assets.githubusercontent.com/a/b"));
        for(String url:new String[]{"http://github.com/Heymez0/PureTV","https://evil.example/PureTV-AirPlay.apk",
                "https://github.com.evil.example/x","https://user@github.com/x","https://github.com:8443/x","file:///sdcard/x.apk",
                "javascript:alert(1)",""})ok(!InstallRules.allowed(url));
        ok("https://github.com/Heymez0/PureTV/releases/download/v1/PureTV-AirPlay.apk".equals(
                InstallRules.follow(InstallRules.URL,"/Heymez0/PureTV/releases/download/v1/PureTV-AirPlay.apk")));
        ok(InstallRules.follow(InstallRules.URL,"http://objects.githubusercontent.com/x")==null);
        ok(InstallRules.follow(InstallRules.URL,"https://example.com/x")==null&&InstallRules.follow(InstallRules.URL,null)==null);
        ok(InstallRules.MAX_REDIRECTS<=5&&InstallRules.MAX_BYTES<=32L*1024*1024);
        // Updates: only this repository's release assets, exactly the expected file, a plain tag.
        String good="https://github.com/Heymez0/PureTV/releases/download/v2/PureTV.apk";
        ok(good.equals(InstallRules.asset(good,InstallRules.LAUNCHER_ASSET)));
        ok(InstallRules.asset("https://github.com/Heymez0/PureTV/releases/download/v2.1-rc/PureTV-AirPlay.apk",InstallRules.RECEIVER_ASSET)!=null);
        for(String url:new String[]{"https://github.com/Other/PureTV/releases/download/v2/PureTV.apk",
                "https://github.com/Heymez0/PureTV/releases/download/v2/PureTV-AirPlay.apk",   // the receiver, not the launcher
                "https://github.com/Heymez0/PureTV/releases/download/../x/PureTV.apk",
                "https://github.com/Heymez0/PureTV/releases/download/a/b/PureTV.apk",
                "http://github.com/Heymez0/PureTV/releases/download/v2/PureTV.apk",
                "https://github.com/Heymez0/PureTV/releases/download//PureTV.apk",null})ok(InstallRules.asset(url,InstallRules.LAUNCHER_ASSET)==null);
        ok(InstallRules.LATEST_API.startsWith("https://api.github.com/repos/Heymez0/PureTV/"));
        ok(InstallRules.tag("v2")&&!InstallRules.tag("v2 beta")&&!InstallRules.tag("")&&!InstallRules.tag(null));
        // Only strictly newer builds; never the same one or a downgrade; not when nothing is installed.
        ok(InstallRules.newer(3,2)&&!InstallRules.newer(2,2)&&!InstallRules.newer(1,2)&&!InstallRules.newer(3,-1));
        ok(InstallRules.NOTIFY.equals(InstallRules.mode(null))&&InstallRules.NOTIFY.equals(InstallRules.mode("always"))
                &&InstallRules.AUTO.equals(InstallRules.mode("auto"))&&InstallRules.OFF.equals(InstallRules.mode("off")));
    }

    private static void features(){
        // Hidden and locked app lists: valid packages only, no repeats, bounded.
        ok(FeatureRules.packages("com.a.b,com.a.b,bad pkg,,com.c.d").equals(Arrays.asList("com.a.b","com.c.d")));
        ok(FeatureRules.packages(null).isEmpty());
        StringBuilder huge=new StringBuilder();for(int i=0;i<500;i++)huge.append("com.app.n").append(i).append(',');
        ok(FeatureRules.packages(huge.toString()).size()==FeatureRules.LIST_LIMIT);
        // What opens at start: only known forms.
        for(String v:new String[]{"","live","app:com.netflix.ninja","input:input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5",
                "input:input:com.tv/.Hdmi$Service/HW2"})ok(FeatureRules.startup(v).equals(v));
        for(String v:new String[]{"app:","app:rm -rf","input:a b","input:$(reboot)","input:x;y","shell","app:com.x;reboot",null})ok(FeatureRules.startup(v).isEmpty());
        // Once per start and only in its first minutes.
        ok(FeatureRules.launchAtStart("live",12,11,60_000));
        ok(!FeatureRules.launchAtStart("live",12,12,60_000));                       // this start already handled
        ok(!FeatureRules.launchAtStart("live",12,11,FeatureRules.STARTUP_WINDOW_MS)); // too long after the start
        ok(!FeatureRules.launchAtStart("",12,11,60_000)&&!FeatureRules.launchAtStart("live",0,0,60_000));
        // Lock hours: always, or evening until 7:00 the next morning.
        ok(FeatureRules.lockedAt("always",12*60)&&FeatureRules.lockedAt("bogus",12*60));
        ok(FeatureRules.lockedAt("21-7",21*60)&&FeatureRules.lockedAt("21-7",3*60)&&FeatureRules.lockedAt("21-7",6*60+59));
        ok(!FeatureRules.lockedAt("21-7",7*60)&&!FeatureRules.lockedAt("21-7",20*60+59)&&!FeatureRules.lockedAt("20-7",12*60));
        // PIN: digits only, stored salted and hashed, compared exactly; waiting after repeated mistakes.
        ok(FeatureRules.validPin("1234")&&FeatureRules.validPin("12345678"));
        for(String p:new String[]{"123","123456789","12a4","",null," 1234"})ok(!FeatureRules.validPin(p));
        String stored=FeatureRules.storePin("4821"),again=FeatureRules.storePin("4821");
        ok(!stored.contains("4821")&&!stored.equals(again));                      // a new salt each time
        ok(FeatureRules.pinMatches("4821",stored)&&FeatureRules.pinMatches("4821",again));
        ok(!FeatureRules.pinMatches("4822",stored)&&!FeatureRules.pinMatches("4821","")&&!FeatureRules.pinMatches("4821","nocolon"));
        ok(FeatureRules.waitLeft(FeatureRules.ATTEMPTS-1,1000,1000)==0);
        ok(FeatureRules.waitLeft(FeatureRules.ATTEMPTS,1000,1000)==FeatureRules.WAIT_MS);
        ok(FeatureRules.waitLeft(FeatureRules.ATTEMPTS,1000,1000+FeatureRules.WAIT_MS)==0);
        // Rain: the first hour at or over the threshold; unknown (-1) hours never count.
        ok(FeatureRules.firstRain(Arrays.asList(-1,10,29,30,80),FeatureRules.RAIN_SHOWN)==3);
        ok(FeatureRules.firstRain(Arrays.asList(-1,-1),FeatureRules.RAIN_SHOWN)==-1&&FeatureRules.firstRain(null,30)==-1);
        // Automatic memory cleanup: only below 15 % left, not again within 15 minutes, never on an unreadable figure.
        long gb=1024L*1024*1024,cool=FeatureRules.AUTO_FREE_COOLDOWN_MS;
        ok(FeatureRules.autoFree(gb/10,gb,cool)&&!FeatureRules.autoFree(gb*16/100,gb,cool)&&!FeatureRules.autoFree(gb/3,gb,cool));
        ok(!FeatureRules.autoFree(gb/10,gb,cool-1)&&!FeatureRules.autoFree(-1,gb,cool)&&!FeatureRules.autoFree(0,0,cool));
        // App compile catch-up: after an install or update, or a week later; never for a missing app.
        long week=FeatureRules.OPTIMIZE_EVERY_MS,now=10*week;
        ok(FeatureRules.optimizeDue(5,0,0,now)&&FeatureRules.optimizeDue(6,5,now-1,now)&&FeatureRules.optimizeDue(5,5,now-week,now));
        ok(!FeatureRules.optimizeDue(5,5,now-week+1,now)&&!FeatureRules.optimizeDue(0,0,0,now)&&FeatureRules.optimizeDue(5,5,now+1,now));
        // The backup carries hidden apps, the start choice and screensaver photos, but never the app lock.
        Map<String,Object> in=new HashMap<>();
        in.put("hidden_apps","com.a.b,bad pkg");in.put("startup","app:com.netflix.ninja");in.put("screensaver_photos",true);
        in.put("lock_pin","abc:def");in.put("lock_apps","com.a.b");in.put("lock_hours","21-7");
        Map<String,Object> out=BackupRules.clean(in);
        ok("com.a.b".equals(out.get("hidden_apps"))&&"app:com.netflix.ninja".equals(out.get("startup"))&&Boolean.TRUE.equals(out.get("screensaver_photos")));
        ok(!out.containsKey("lock_pin")&&!out.containsKey("lock_apps")&&!out.containsKey("lock_hours"));
        Map<String,Object> badStart=new HashMap<>();badStart.put("startup","input:$(reboot)");
        ok(!BackupRules.clean(badStart).containsKey("startup"));
    }

    public static void main(String[] args){
        backup();install();features();
        System.out.println("PASS: settings backup allowlist/bounds/no HOME or keys; receiver download hosts/redirects/limits; update assets/tags/newer-only/modes");
        System.out.println("PASS: hidden/locked app lists; start-up choice and once-per-start window; lock hours; salted PIN, waits; rain threshold; backup without lock");
    }
}
