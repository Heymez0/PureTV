package io.puretv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weather, sunrise and sunset (Open-Meteo, every 30 min), cached
 * so the screen opens with numbers. Only fetched while the home screen or the screensaver is visible (the screen asks).
 * No key is required. Location comes from config.properties.
 */
final class HomeData {
    private static final String WEATHER_QUERY = "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
            + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability&timezone=auto&forecast_days=2";

    private static String weatherPlace() {
        return Prefs.latitude() + "," + Prefs.longitude();
    }

    private static String weatherUrl() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prefs.latitude() + "&longitude=" + Prefs.longitude() + WEATHER_QUERY;
    }
    private static final long WEATHER_EVERY = 30 * 60_000L;
    interface Listener { void onData(); }

    /** City changed: fetch the local forecast again. */
    void settingsChanged() {
        weatherLoads.invalidate();weatherAt=0;weatherFailedAt=0;
        String place=weatherPlace();
        if(!place.equals(weatherFor)){weatherFor=place;weather=new Weather();}
    }

    static final class Weather {
        double temp = Double.NaN, max = Double.NaN, min = Double.NaN;
        int code = -1;
        boolean isDay = true;
        long sunrise, sunset;   // today's, in epoch ms
        // Next hours (starting at the next full hour): time, temperature, code and day/night.
        final List<Long> hours = new ArrayList<>();
        final List<Double> hourTemp = new ArrayList<>();
        final List<Integer> hourCode = new ArrayList<>();
        final List<Boolean> hourDay = new ArrayList<>();
        final List<Integer> hourRain = new ArrayList<>();   // chance of rain in %, or -1 (a cached forecast without it)
    }

    private static HomeData instance;

    static synchronized HomeData get(Context c) {
        if (instance == null) instance = new HomeData(c.getApplicationContext());
        return instance;
    }

    volatile long weatherAt;
    volatile Weather weather = new Weather();

    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final RefreshGate weatherLoads=new RefreshGate();
    private String weatherFor=weatherPlace();
    private volatile long weatherFailedAt;

    private HomeData(Context c) {
        cache = c.getSharedPreferences("homedata", Context.MODE_PRIVATE);
        try {
            String w = cache.getString("weather", null);
            // The cached forecast is for the city it was fetched for; another city means fetch again.
            if (w != null && weatherPlace().equals(cache.getString("weatherPlace", ""))) {
                weather = parseWeather(w);
                weatherAt = cache.getLong("weatherAt", 0);
            }
        } catch (Exception ignored) {}
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** Fetches whatever is stale. The screen calls this when it appears and every minute while it's visible. */
    void refreshIfStale() {
        long now=System.currentTimeMillis();
        if((now-weatherAt<=WEATHER_EVERY && now>=weatherAt) || failedRecently(weatherFailedAt,now))return;
        final long token=weatherLoads.begin(false);if(token<0)return;
        final String place=weatherPlace(),url=weatherUrl();
        io.execute(()->{
            String body=null;Weather parsed=null;String error="";
            try{body=get(url);parsed=parseWeather(body);}
            catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final String response=body,failure=error;final Weather ready=parsed;
            main.post(()->{
                boolean current=weatherLoads.complete(token) && place.equals(weatherPlace());
                if(!current){if(!listeners.isEmpty())refreshIfStale();return;}
                if(ready==null){weatherFailedAt=System.currentTimeMillis();AppLog.w("weather: "+failure);return;}
                weather=ready;weatherFor=place;weatherAt=System.currentTimeMillis();weatherFailedAt=0;
                cache.edit().putString("weather",response).putLong("weatherAt",weatherAt).putString("weatherPlace",place).apply();
                notifyListeners();
            });
        });
    }

    private static boolean failedRecently(long at, long now) {
        return at != 0 && now >= at && now - at < 2 * 60_000L;
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onData(); });
    }



    private static Weather parseWeather(String body) throws Exception {
        JSONObject o = new JSONObject(body);
        TimeZone zone = TimeZone.getTimeZone(o.optString("timezone", TimeZone.getDefault().getID()));
        Weather w = new Weather();
        JSONObject current = o.getJSONObject("current");
        w.temp = current.getDouble("temperature_2m");
        w.code = current.getInt("weather_code");
        w.isDay = current.optInt("is_day", 1) == 1;
        JSONObject daily = o.getJSONObject("daily");
        w.max = daily.getJSONArray("temperature_2m_max").getDouble(0);
        w.min = daily.getJSONArray("temperature_2m_min").getDouble(0);
        w.sunrise = localTime(daily.getJSONArray("sunrise").getString(0), zone);
        w.sunset = localTime(daily.getJSONArray("sunset").getString(0), zone);
        JSONObject hourly = o.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray codes = hourly.getJSONArray("weather_code");
            JSONArray days = hourly.optJSONArray("is_day");
            JSONArray rain = hourly.optJSONArray("precipitation_probability");
            long now = System.currentTimeMillis();
            for (int i = 0; i < times.length() && w.hours.size() < 6; i++) {
                // Open-Meteo sends null for an hour it has no value for: skip that hour, keep the forecast.
                if (temps.isNull(i) || codes.isNull(i)) continue;
                long at = localTime(times.getString(i), zone);
                if (at <= now) continue;
                w.hours.add(at);
                w.hourTemp.add(temps.getDouble(i));
                w.hourCode.add(codes.getInt(i));
                w.hourDay.add(days == null || days.optInt(i, 1) == 1);
                w.hourRain.add(rain == null || rain.isNull(i) ? -1 : Math.max(-1, Math.min(100, rain.optInt(i, -1))));
            }
        }
        return w;
    }

    private static long localTime(String iso, TimeZone zone) throws ParseException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(zone);
        return f.parse(iso).getTime();
    }

    static List<String[]> findCity(String name) throws Exception {
        JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language=" + Ui.language
                + "&name=" + URLEncoder.encode(name.trim(), "UTF-8")));
        JSONArray results = o.optJSONArray("results");
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();   // places with the same name, state and country read the same
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String city = r.optString("name", "");
            if (city.isEmpty() || !r.has("latitude") || !r.has("longitude")) continue;
            StringBuilder label = new StringBuilder(city);
            for (String part : new String[] {r.optString("admin1", ""), r.optString("country", "")}) {
                if (!part.isEmpty() && !part.equals(city)) label.append(", ").append(part);
            }
            if (!seen.add(label.toString())) continue;
            out.add(new String[] {label.toString(), city,
                    String.format(Locale.US, "%.4f", r.getDouble("latitude")), String.format(Locale.US, "%.4f", r.getDouble("longitude"))});
        }
        return out;
    }

    private static String get(String value) throws IOException {
        URL url=new URL(value);HttpURLConnection connection=null;
        try {
            for(int redirect=0;;redirect++) {
                if(!"https".equals(url.getProtocol()) || url.getUserInfo()!=null)throw new IOException("Invalid weather URL");
                connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000);connection.setReadTimeout(6000);connection.setRequestProperty("User-Agent","PureTV");
                int code=connection.getResponseCode();
                if(code>=300 && code<400 && redirect<3) {
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Missing redirect");
                    URL next=new URL(url,location);connection.disconnect();connection=null;url=next;continue;
                }
                if(code!=200)throw new IOException("HTTP "+code);
                if(connection.getContentLengthLong()>1024*1024)throw new IOException("Weather response exceeds size limit");
                try(InputStream in=connection.getInputStream()) {
                    return new String(BoundedInput.read(in,1024*1024),StandardCharsets.UTF_8);
                }
            }
        } finally {if(connection!=null)connection.disconnect();}
    }

    /**
     * Night at your location: from sunset to sunrise (like Apple's "Automatic" appearance: the light design darkens).
     * Uses the last weather fetched (it moves ~1 min a day); without data, 6:00 and 18:00.
     */
    boolean isNight(long now) {
        String theme = Prefs.theme();
        if (theme.equals("dark")) return true;
        if (theme.equals("light")) return false;
        long t = Shaders.timeOfDay(now, 0);
        long rise = Shaders.timeOfDay(weather.sunrise, 6 * 60 * 60_000L);
        long set = Shaders.timeOfDay(weather.sunset, 18 * 60 * 60_000L);
        return t < rise || t >= set;
    }

    // MARK: - Formatting

    private static Locale locale() {
        return Ui.locale();
    }







    /** The weather in words, from Open-Meteo's WMO code. */
    static String highLow(Weather wx, String space) {
        return Ui.trf("H:{max}", "max", degrees(wx.max)) + space + Ui.trf("L:{min}", "min", degrees(wx.min));
    }

    static String condition(int code, boolean day) {
        if (code < 0) return "";
        if (code == 0) return day ? Ui.tr("Clear") : Ui.tr("night|Clear");
        if (code == 1) return day ? Ui.tr("Mostly Sunny") : Ui.tr("Mostly Clear");
        if (code == 2) return Ui.tr("Partly Cloudy");
        if (code == 3) return Ui.tr("Cloudy");
        if (code == 45 || code == 48) return Ui.tr("Fog");
        if (code >= 51 && code <= 57) return Ui.tr("Drizzle");
        if (code == 65 || code == 67 || code == 82) return Ui.tr("Heavy Rain");
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Ui.tr("Snow");
        if (code >= 95) return Ui.tr("Thunderstorms");
        return Ui.tr("Rain");
    }



    /** Celsius or Fahrenheit (config.properties "temperature": C, F, or auto = Fahrenheit in the US). */
    static String degrees(double v) {
        if (Double.isNaN(v)) return "—";
        return Math.round(fahrenheit() ? v * 9 / 5 + 32 : v) + "°";
    }

    private static boolean fahrenheit() {
        String unit = Prefs.temperature();
        if (unit.equals("F")) return true;
        if (unit.equals("C")) return false;
        return "US".equals(Locale.getDefault().getCountry());
    }



    static String hour(long at) {
        if (!"auto".equals(Prefs.clockFormat()) || Ui.tr) {
            return Ui.once("hour|" + Prefs.clockFormat(), at, () -> new SimpleDateFormat(ClockFormat.hour(Prefs.clockFormat(), Ui.en), Ui.locale()).format(new java.util.Date(at)));
        }
        return Ui.once("hour", at, () -> Ui.en ? new SimpleDateFormat("h a", Locale.US).format(new java.util.Date(at))
                : new SimpleDateFormat(Ui.es || Ui.ru ? "HH:mm" : Ui.de ? "HH 'Uhr'" : "HH'h'", Ui.locale()).format(new java.util.Date(at)));
    }

    /** How long ago the data was fetched, if it's stale (no internet): "12 min ago". */
    static String staleness(long at, long every) {
        if (at == 0) return "";
        long age = System.currentTimeMillis() - at;
        if (age < every * 2) return "";
        long minutes = age / 60_000;
        if (Ui.tr) return minutes < 60 ? minutes + " dk önce" : (minutes / 60) + " sa önce";
        if (Ui.en) return minutes < 60 ? minutes + " min ago" : (minutes / 60) + " h ago";
        if (Ui.ru) return minutes < 60 ? minutes + " мин назад" : (minutes / 60) + " ч назад";
        if (Ui.es) return minutes < 60 ? "hace " + minutes + " min" : "hace " + (minutes / 60) + " h";
        if (Ui.de) return minutes < 60 ? "vor " + minutes + " Min." : "vor " + (minutes / 60) + " Std.";
        return minutes < 60 ? "há " + minutes + " min" : "há " + (minutes / 60) + " h";
    }
}
