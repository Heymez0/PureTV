package io.puretv;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The home screen, styled after the Apple TV app with the macOS 27 glass:
 *  - at the top left, the "For You" title and the tabs (Explore, Movies, Shows); at the top right, icon-only glass
 *    buttons (Live TV, HDMI Inputs, Settings), with the focused one's name below it, and device status;
 *  - three cards with the titles recommended for right now in the selected tab (Recs, from TMDB); the background is
 *    the first card's photo, heavily blurred and almost white (Backdrop); without one, a light gradient;
 *  - at the bottom, the glass shelf with the apps (icons in each brand's color, like the tvOS Dock; the focused one
 *    grows);
 *  - ↓ on the shelf: everything moves up and the bottom drawer appears (inputs, shortcuts, weather for the next hours
 *    and other apps); the background lightens (darkens at night). ↑ or Back closes it;
 *  - from sunset to sunrise, a night look: dark background, smoked glass and white text.
 * Measurements are in a 1920×1080 space (k = width / 1920). The screen is split into layers so the TV's weak GPU only
 * redraws what changed.
 */
final class HomeView extends ViewGroup implements Choreographer.FrameCallback, HomeData.Listener, Recs.Listener, ContinueWatching.Listener {
    // tvOS grid (Human Interface Guidelines): content inside the safe area, 80 px from the sides and 60 from top and
    // bottom; 40 px between items; 7 columns of 217 px (the apps). The shelf glass extends 36 px past the tiles (the
    // same padding on all four sides) and the tile corners are concentric with it (glass radius = radius + padding).
    private static final float MARGIN = 80, CONTENT_R = 1840;
    // Hero, anchored at the bottom (a two-line title grows upward).
    private static final float HERO_TITLE_W = 900;
    // Shelf: tiles with corners concentric with the glass (glass radius = tile radius + padding).
    // Shelf like the tvOS 27 Dock: 5:3 icons (210 × 126) in each brand's color, 48 apart, and the glass bar hugging
    // them with 40 of padding (the focused icon grows 20% without touching its edge).
    private static final float SHELF_PAD = 40, TILE_GAP = 48;
    private static final float TILE_RADIUS = 22, TILE_MAX_W = 270;
    // Tile focus (Apple TV style): it grows from the center and its shadow drops (it "rises" toward the viewer).
    private static final float FOCUS_GROW = 0.2f;
    // Corner capsule.
    // Corner capsule: the height and top offset of the tvOS tab bar (68 and 46).
    private static final float CAPSULE_TOP = 46, CAPSULE_H = 68, BUTTON = 56;
    // Bottom drawer (measured with it open; everything moves up by SHIFT).
    private static final float SHIFT = 560, ROW_TITLE = 518, ROW_TOP = 540, ROW_H = 126, CARDS_TOP = 708, CARDS_H = 300;
    // "All Apps" sheet.
    private static final int COLUMNS = 5, VISIBLE_ROWS = 3;
    private static final float GRID_W = 300, GRID_H = 169, GRID_GAP = 40, GRID_ROW = 239, GRID_TOP = 196, GRID_RADIUS = 22;
    private static final int SMOKE = 0xFF111113, INK = 0xFF1C1C1E, WHITE = 0xFFF5F5F7;
    private static final long TOAST_MS = 6000, FADE_MS = 800;
    /** Blur radii of the shadow and shadowDeep paints (Shadows needs them to draw the same blur on older TVs). */
    private static final float SHADOW_BLUR = 26, DEEP_BLUR = 30;
    private static final int SKY = 0, HERO = 1, STATUS = 2, PLATTER = 3, TILES = 4, DRAWER = 5, SHEET = 6, TOAST = 7, OTHER_LAYER = 8;
    private static final int Z_STATUS = 0, Z_HERO = 1, Z_SHELF = 2, Z_DRAWER = 3;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    // Light design (the L_ constants): title, tab bar, three recommendation cards and the shelf.
    // The tab bar is a 68 px capsule; the cards sit on the 5-column grid (the wide one spans 3).
    private static final float L_TABBAR_TOP = 158, L_TABBAR_H = 68, L_CARDS_TOP = 262, L_CARD_H = 442;
    private static final float L_TALL_W = 320, L_CARD_GAP = 40, L_BUTTON = 68, L_BUTTON_GAP = 20, L_BUTTON_CY = 103;
    private static final float L_PLATTER_TOP = 814, L_TILE_H = 126;
    private static final float L_DATE_BASE = 330, L_CLOCK_BASE = 540, L_WEATHER_BASE = 626, L_QUOTES_BASE = 694;
    private static final int L_INK = 0xFF1D1D1F, L_DIM = 0xFF6E6E73, L_FAINT = 0xFF8E8E93;
    private static final String[] TABS = {"Explorar", "Filmes", "Séries"};
    private static final String[] TABS_RU = {"Обзор", "Фильмы", "Сериалы"};
    private static final String[] TABS_EN = {"Explore", "Movies", "Shows"};
    private static final String[] TABS_ES = {"Explorar", "Películas", "Series"};
    private static final String[] TABS_DE = {"Entdecken", "Filme", "Serien"};
    private static final String[] TABS_TR = {"Keşfet", "Filmler", "Diziler"};
    private static final String[] TAB_KIND = {null, null, "movie", "tv"};
    private static final int Z_TOP = 4, Z_CARDS = 5, Z_OTHER = 6;

    private final Activity activity;
    private final Scene scene = new Scene();
    private final HomeData data;
    private final Recs recs;
    private final ContinueWatching continuing;
    private final boolean continueOn = Prefs.continueWatching(), recsOn = Prefs.appRecommendations();
    private static final int TOP_ACTION = HomeTabs.ACTION, TOP_COUNT = HomeTabs.COUNT;
    private final Click click;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences ui;
    private final TvInputManager tvInputs;
    private boolean inputsRegistered,inputLoading,inputDirty;
    private int inputGeneration,tvKeyArmed;
    private final Runnable inputReload=this::reloadInputRows;
    private final Spoken spoken = new Spoken(this, new Spoken.Source() {
        @Override public void items(List<Integer> out) { a11yItems(out); }
        @Override public String text(int id) { return a11yText(id); }
        @Override public boolean bounds(int id, Rect out) { return a11yBounds(id, out); }
        @Override public int focused() { return a11yFocused(); }
        @Override public void focus(int id) { a11yFocus(id); }
        @Override public void click(int id) { activate(); }
        @Override public boolean clickable(int id) { return (id - 1) / 1000 != WIDGETS; }
    });
    private final RectF a11yRect = new RectF();
    private final Layer sky, heroLayer, status, platter, tiles, drawerLayer, sheetLayer, toastLayer, otherLayer;
    private int zone = Z_SHELF;

    // Night look (like Apple's "Automatic" appearance): darker from sunset to sunrise, with a 2 s transition.
    private static final long NIGHT_MS = 2000;
    private boolean nightTarget;
    private float night, nightFrom;     // 0 = day, 1 = night
    private long nightAt;
    private final int[] glassNight = scene.imageEffects()?new int[Backdrop.GW * Backdrop.GH]:null, frostNight = scene.imageEffects()?new int[Backdrop.FW * Backdrop.FH]:null;
    private final int[] neutralGlassPx = scene.imageEffects()?new int[Backdrop.GW * Backdrop.GH]:null, neutralFrostPx = scene.imageEffects()?new int[Backdrop.FW * Backdrop.FH]:null;
    private boolean nightPair;          // the glass holds both photos (day and night), blended by `night`
    private Shader nightGradient;

    // At the top: tabs (0 to 2) and icon-only buttons (3 to 5); the three cards
    private int tab, topIndex, cardIndex;
    private int widgetIndex;    // the weather card can take focus
    private List<ContentCard> cards = new ArrayList<>();
    private int recentOffset,recommendationOffset;
    private List<ContinueWatching.Program> preparedRecent,preparedRecommendations;
    private int preparedTab=-1,preparedRecentOffset=-1,preparedRecommendationOffset=-1;
    private int renderedTab=-1,renderedLanguage=-1,cardsArtVersion=-1;
    private ContinueWatching.Snapshot filteredSnapshot;
    private int filteredPrefs=-1,filteredContent=-1;
    private boolean filteredPermission;
    private List<ContinueWatching.Program> filteredRecent=java.util.Collections.emptyList(),filteredRecommendations=java.util.Collections.emptyList();
    private static final class ContentCard {
        final Recs.Pick pick;
        final ContinueWatching.Program program;
        final String title,meta,app;
        String shownTitle="",shownMeta="";
        ContentCard(Recs.Pick p) {pick=p;program=null;title=p.title;meta=p.meta;app=Recs.serviceName(p);}
        ContentCard(ContinueWatching.Program p) {pick=null;program=p;title=p.title;meta=p.meta();app=p.app;}
    }
    private final Spring[] topFocus = new Spring[TOP_COUNT];
    private final RectF[] topRects = new RectF[TOP_COUNT];
    private final Spring[] cardFocus = new Spring[3];
    private final RectF[] cardRects = new RectF[3];
    private final List<HomeApps.Item> lightButtons = new ArrayList<>();
    private Paint lTab, lTabOn, lCardTitle, lCardMeta, lTip, lDate, lClock, lWeather;
    private Paint headerTime, headerText, headerGreeting;
    private boolean rebindingPosters;
    private final Rect headerGreetingBounds = new Rect();
    private final Paint headerLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF headerInfo = new RectF();
    private String headerShortDate = "", headerWeatherText = "", headerWeatherSymbol = "", headerGreetingText = "";
    private float headerTimeX, headerDateX, headerDividerX, headerWeatherX, headerSymbolX;
    private Shader lightGradient;

    // Shelf
    private List<HomeApps.Item> shelf = new ArrayList<>();
    private Spring[] tileFocus = new Spring[0];
    private RectF[] tileRects = new RectF[0];
    private final Map<Bitmap, Bitmap> crispLogos = new java.util.IdentityHashMap<>();
    private int crispWidth; // source identity + stable shelf geometry; no draw-time bitmap work
    /** Each app's tile like its Apple TV icon: brand colors (top, bottom) and logo (0 = as is, 1 = white, 2 = dark). */
    private static final Map<String, int[]> TILE_STYLE = new HashMap<>();
    static {
        TILE_STYLE.put("netflix", new int[] {0xFF0A0A0A, 0xFF000000, 0});
        TILE_STYLE.put("youtube", new int[] {0xFF1C1C1E, 0xFF09090A, 0});
        TILE_STYLE.put("globoplay", new int[] {0xFFFF8A1F, 0xFFFF2D5A, 1});
        TILE_STYLE.put("prime", new int[] {0xFF2AA8FF, 0xFF0A68D4, 1});
        TILE_STYLE.put("disney", new int[] {0xFF173584, 0xFF08143F, 0});
        TILE_STYLE.put("max", new int[] {0xFF5C30E8, 0xFF221684, 0});
        TILE_STYLE.put("appletv", new int[] {0xFF1C1C1E, 0xFF000000, 0});
    }
    private final android.graphics.PorterDuffColorFilter whiteLogo =
            new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
    private final RectF platterRect = new RectF();
    private int shelfIndex;

    // Moving apps on the shelf (as on Apple TV): hold OK on a tile, then ← → move it, OK drops it, Back cancels. The
    // lifted tile wiggles; the others slide to their new places (each slot's x offset is a spring that returns to 0).
    private static final long HOLD_MS = 500, WIGGLE_MS = 300;
    private static final float LIFT_GROW = 0.08f, WIGGLE_DEG = 1.5f;
    private boolean reordering, reorderAnim, okHeld, holdArmed;
    private int liftIndex = -1;
    private long reorderAt;
    private float wiggle;
    private Spring[] tileSlide = new Spring[0];
    private final Spring moveLift = new Spring(0.3f, 0.72f, 0);
    private List<HomeApps.Item> reorderOrigin;
    private final Runnable holdStart = () -> { if (this.okDown && this.holdArmed) showActions(); };
    private QuickActionDialog actions;

    // Corner capsule
    private List<HomeApps.Item> statusItems = new ArrayList<>();
    private Spring[] statusFocus = new Spring[0];
    private RectF[] statusRects = new RectF[0];
    private final RectF capsuleRect = new RectF();
    private float capsuleInfoW;
    private int statusIndex;

    // Hero and background
    private List<Recs.Pick> picks = new ArrayList<>();
    private int pickIndex, heroSlide = 1;
    private Hero hero, heroFrom;
    private long heroFadeAt, bgFadeAt;
    private final Spring heroFocus = new Spring(0.28f, 0.85f, 0);
    private final RectF heroButton = new RectF();
    private Backdrop bgFrom, bgTo;     // null = gradient (no photo)
    private final int[] frostMix = scene.imageEffects()?new int[Backdrop.FW * Backdrop.FH]:null;

    // Bottom drawer
    private List<HomeApps.Item> drawerItems = new ArrayList<>();
    private int drawerInputs;          // how many leading items are inputs (the rest are shortcuts)
    private Spring[] drawerFocus = new Spring[0];
    private RectF[] drawerRects = new RectF[0];
    private final RectF weatherCard = new RectF();
    private final Spring drawer = new Spring(0.46f, 0.92f, 0);
    private boolean drawerOpen;
    private int drawerIndex;

    private final Spring below = new Spring(0.34f, 1f, 0);
    private Spring[] otherFocus = new Spring[0];
    private int otherIndex, sectionMove, otherFirstRow, otherLastRow;
    private float sectionWiggle;
    private long sectionMoveAt;
    private final boolean weatherOn = Prefs.weather();
    private final boolean detailedWeatherOn = weatherOn && Prefs.detailedWeather();
    private String[] drawerLabels = new String[0];
    private final RectF weatherCacheBounds = new RectF();
    private String weatherCacheKey = "";
    private long weatherCacheStamp = Long.MIN_VALUE;
    private final Consumer<Canvas> weatherPainter = cc -> { drawerCard(cc, weatherCard); drawWeather(cc, weatherCard); };
    private List<HomeApps.Item> sectionOrigin;
    private String[] otherLabels = new String[0];
    private static final float OTHER_TOP = 1110;

    // "All Apps" sheet
    private List<HomeApps.Item> others = new ArrayList<>();
    private Spring[] gridFocus = new Spring[0];
    private String[] gridLabels;
    private final Spring sheet = new Spring(0.38f, 0.9f, 0);
    private final Spring scroll = new Spring(0.34f, 1f, 0);
    private final RectF focusedCell = new RectF();
    private boolean sheetOpen;
    private int gridIndex;

    // Toasts
    private final ArrayDeque<String> toasts = new ArrayDeque<>();
    private StaticLayout toast;
    private long toastAt;

    // Frames
    private boolean running, tilesDirty, statusDirty, heroDirty, drawerDirty, gridDirty, motionDirty = true;
    private boolean drawerWasMoving, idle;
    private long lastNanos, resumedAt, lastStep;
    private int w, h;
    private float k = 1;
    private String clockText = "", dateText = "", weatherLine = "";
    private long weatherLineAt = -1;
    private int weatherLinePrefs = -1;
    private String weatherLineLanguage = "";
    private long clockMinute = -1;
    private boolean okDown, backDown;

    private Paint labelPaint, metaPaint, clockPaint, buttonPaint, capsuleText, tipPaint;
    private Paint rowTitle, drawerLabel, cardTitle, cardText, cardDim, cardDimRight, bigTemp, hourPaint, hourTemp, hourRain;
    /** The chance of rain under an hour: a calm blue that reads on the light and the dark card. */
    private static final int RAIN_LIGHT = 0xFF1F6FB8, RAIN_DARK = 0xFF7DB8F0;
    private Paint pricePaint, sheetTitle;
    private TextPaint titlePaint, gridLabel, toastPaint;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowSoft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowDeep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shadows shadows = new Shadows();
    // A strong shader value would retain its own weak bitmap key and keep old artwork alive.
    private final Map<Bitmap, java.lang.ref.WeakReference<BitmapShader>> imageShaders = new WeakHashMap<>();
    private final Matrix imageMatrix = new Matrix();
    private final Paint imageFill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF tmp = new RectF(), cell = new RectF(), screen = new RectF();
    private final TextPaint fitPaint = new TextPaint();
    private final RectF gridCell = new RectF();
    private LinearGradient[] tileShaders = new LinearGradient[0];
    private LinearGradient tileGlow, cardSheen;
    private float tileShaderH, cardSheenH;
    private LinearGradient[] lightSheens = new LinearGradient[0], lightRims = new LinearGradient[0];
    private float[] lightShaderW = new float[0];
    private android.graphics.ColorFilter veilFilter;
    private float veilAmount = -1;
    private int veilColor;
    private HomeData.Weather textWeather;
    private int textPrefs = -1;
    private String textLanguage;
    private String weatherTitle, nowTemp, highLow, sunriseText, sunsetText, staleText;
    private final String[] hourText = new String[6], hourTempText = new String[6], hourRainText = {"", "", "", "", "", ""};

    /** What the hero shows: a recommendation or, without any, the date, the time and the weather. */
    private static final class Hero {
        String label = "", title = "", meta = "";
        boolean clock, button;
        int dots, dot;
        StaticLayout titleLayout;

        boolean sameText(Hero o) {
            return o != null && clock == o.clock && label.equals(o.label) && title.equals(o.title) && meta.equals(o.meta);
        }
    }

    /** One layer of the screen. It draws in full-screen coordinates: the glass needs each pixel's real position. */
    private final class Layer extends View {
        private final int kind;

        Layer(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        /**
         * The drawer and sheet fade whole layers. Without a hardware layer (Android 9 to 11 here), a fading view with
         * overlapping content is drawn into a full-size offscreen buffer on every frame: on a Mi Stick that saturated the
         * GPU while the drawer opened or closed (10-22 ms buffer swaps). Applying the alpha to each drawing instead only
         * lets a card's shadow show through its image for the few frames of a fade.
         */
        @Override public boolean hasOverlappingRendering() {
            return false;
        }

        @Override protected void onDraw(Canvas c) {
            if (labelPaint == null) return;
            c.translate(-getLeft(), -getTop());
            // The layer may be shifted (bottom drawer): the glass reads the background from where it really is. The
            // drawer and the top buttons are always drawn as if resting in place (they are only visible there and are
            // not redrawn while they move).
            scene.setOffset(0, kind == DRAWER || kind == STATUS ? 0 : getTranslationY());
            switch (kind) {
                case SKY: drawSky(c); break;
                case HERO: drawLightTop(c); break;
                case STATUS: drawLightButtons(c); break;
                case PLATTER: drawPlatter(c); break;
                case TILES: drawTiles(c); break;
                case DRAWER: drawDrawer(c); break;
                case SHEET: drawSheet(c); break;
                case OTHER_LAYER: drawOtherApps(c); break;
                default: drawToast(c); break;
            }
            scene.setOffset(0, 0);
        }
    }

    private final CatalogLoads catalogLoads = new CatalogLoads();
    private boolean destroyed, packagesRegistered;
    private static int lastPackageSequence;
    private int packageSequence=lastPackageSequence;
    private final android.content.BroadcastReceiver packageReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(intent.getData()!=null && context.getPackageName().equals(intent.getData().getSchemeSpecificPart()))return;
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            HomeApps.trimCaches();reloadSoon();
        }
    };
    private final Runnable reloadSoon = this::reload;

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String inputId, int state) { inputsChanged(); }
        @Override public void onInputAdded(String inputId) { inputsChanged(); }
        @Override public void onInputRemoved(String inputId) { inputsChanged(); }
        @Override public void onInputUpdated(String inputId) { inputsChanged(); }
    };

    private void inputsChanged(){
        inputGeneration++;inputDirty=true;handler.removeCallbacks(inputReload);
        if(running)handler.postDelayed(inputReload,350);
    }
    private void reloadInputRows(){
        if(!running||destroyed||tvInputs==null||inputLoading)return;
        inputLoading=true;inputDirty=false;final int token=inputGeneration;
        final Context c=getContext().getApplicationContext();
        LOADER.execute(()->{
            final List<HomeApps.Item> d;
            try{d=drawerList(c);}catch(RuntimeException unavailable){handler.post(()->{inputLoading=false;AppLog.w("TV input refresh unavailable");});return;}
            handler.post(()->{
                inputLoading=false;if(destroyed||!running)return;
                if(token!=inputGeneration||inputDirty){inputsChanged();return;}
                if(reordering||sectionMove!=0){inputDirty=true;return;}
                setDrawerItems(d);wake();
            });
        });
    }

    private void reloadSoon() {
        catalogLoads.invalidate();
        if(!running)return;
        handler.removeCallbacks(reloadSoon);
        handler.postDelayed(reloadSoon, 500);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            wake();
            data.refreshIfStale();
            if(continueOn)continuing.refresh(false);
            checkNotices();
            updatePalette();
            checkNight(true);
            updatePicks();      // three new ones every hour
            handler.postDelayed(this, 15_000);
        }
    };

    HomeView(Activity a) {
        super(a);
        activity = a;
        data = HomeData.get(a);
        recs = Recs.get(a);
        continuing = ContinueWatching.get(a);
        click = new Click(a);
        ui = a.getSharedPreferences("home-ui", Context.MODE_PRIVATE);
        tvInputs = Prefs.television() && Prefs.tvInputs() ? (TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE) : null;
        setFocusable(true);
        sky = new Layer(a, SKY);
        heroLayer = new Layer(a, HERO);
        status = new Layer(a, STATUS);
        platter = new Layer(a, PLATTER);
        tiles = new Layer(a, TILES);
        drawerLayer = new Layer(a, DRAWER);
        otherLayer = new Layer(a, OTHER_LAYER);
        sheetLayer = new Layer(a, SHEET);
        toastLayer = new Layer(a, TOAST);
        // Hardware layers on the GPU: only the one that changed is redrawn; moving, fading and scaling stay cheap.
        // Not on Android 9 to 11: on older GPUs (a Mi Box 3's Mali-450) redrawing and compositing a big layer on every
        // frame cost more than drawing straight to the screen (measured: focus on the picks 33 → 12 ms a frame, the
        // drawer 19 → 17 ms).
        for (View v : new View[] {heroLayer, status, platter, tiles, drawerLayer, sheetLayer}) v.setLayerType(Ui.LEGACY ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        for (View v : new View[] {sky, heroLayer, status, platter, tiles, drawerLayer, otherLayer, sheetLayer, toastLayer}) {
            v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);   // TalkBack reads the items (Spoken)
            addView(v);
        }
        setContentDescription(Ui.tr("Início", "Home Screen"));
        drawerLayer.setVisibility(INVISIBLE);
        otherLayer.setVisibility(INVISIBLE);
        for (int i = 0; i < TOP_COUNT; i++) {
            topFocus[i] = new Spring(0.26f, 0.85f, 0);
            topRects[i] = new RectF();
        }
        for (int i = 0; i < 3; i++) {
            cardFocus[i] = new Spring(0.32f, 0.78f, 0);
            cardRects[i] = new RectF();
        }
        makeLightButtons();
        recs.setLight(true);
        checkNight(false);
        sheetLayer.setVisibility(INVISIBLE);
        scene.setMotion(0.6f, 0f);
        updatePalette();
        // First time: right away (a cold start opens with the apps already there); missing logos come from reload().
        setShelf(HomeApps.shelf(a, false));
        setStatusItems(statusList(a));
        setDrawerItems(HomeApps.drawerShortcuts(a)); // Physical inputs arrive from the background loader.
        android.content.IntentFilter packages=new android.content.IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addDataScheme("package");
        Context app=a.getApplicationContext();
        if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(packageReceiver,packages,Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(packageReceiver,packages);
        packagesRegistered=true;
    }

    // MARK: - Lifecycle (called by HomeActivity)

    void resume() {
        if (running) return;
        running = true;
        // Rebind the visible window to the retained prepared images on return.
        renderedTab=-1;cardsArtVersion=-1;preparedTab=-1;
        checkNight(false);
        resumedAt = SystemClock.uptimeMillis();
        lastNanos = 0;
        lastStep = 0;
        data.addListener(this);
        if(continueOn){continuing.resumeArt();continuing.addListener(this);}
        else {continuing.setVisible(java.util.Collections.emptyList());continuing.trim();}
        data.refreshIfStale();
        tvKeyArmed=0;
        if(tvInputs!=null){try{tvInputs.registerCallback(inputCallback,handler);inputsRegistered=true;}catch(RuntimeException ignored){}inputsChanged();}
        handler.postDelayed(tick, 15_000);
        checkNotices();
        try {
            android.content.pm.ChangedPackages changes=getContext().getPackageManager().getChangedPackages(packageSequence);
            if(changes!=null) {
                if(packageSequence!=0 && !changes.getPackageNames().isEmpty()){catalogLoads.invalidate();HomeApps.trimCaches();}
                packageSequence=changes.getSequenceNumber();lastPackageSequence=packageSequence;
            }
        } catch(RuntimeException ignored) {}
        reload();
        updatePalette();
        rebindingPosters=true;
        try{updatePicks();}finally{rebindingPosters=false;}
        if (sheetOpen) closeSheet();
        sheet.snap(sheet.target);
        motionDirty = true;
        invalidateAll();
        idle = false;
        Choreographer.getInstance().postFrameCallback(this);
        spoken.readAgain();
    }

    void pause() {
        if (!running) return;
        running = false;
        if (actions != null) { actions.dismiss(); actions = null; }
        cancelSectionMove();
        cancelReorder(true);
        handler.removeCallbacks(holdStart);
        okHeld = holdArmed = false;
        Choreographer.getInstance().removeFrameCallback(this);
        handler.removeCallbacks(tick);
        handler.removeCallbacks(reloadSoon);
        data.removeListener(this);
        recs.removeListener(this);
        continuing.suspendArt();
        continuing.removeListener(this);
        // No view-owned old backdrop may bypass the source's hidden byte budget.
        bgFrom=bgTo=null;bgFadeAt=0;
        tvKeyArmed=0;inputGeneration++;handler.removeCallbacks(inputReload);
        if(inputsRegistered){try{tvInputs.unregisterCallback(inputCallback);}catch(RuntimeException ignored){}inputsRegistered=false;}
    }

    void destroy() {
        pause();destroyed=true;catalogLoads.invalidate();
        if(packagesRegistered){getContext().getApplicationContext().unregisterReceiver(packageReceiver);packagesRegistered=false;}
        handler.removeCallbacksAndMessages(null);weatherSnapshot.clear();shadows.trim();scene.trimImages();
        crispLogos.clear();imageShaders.clear();bgFrom=bgTo=null;hero=heroFrom=null;
        filteredSnapshot=null;filteredRecent=filteredRecommendations=java.util.Collections.emptyList();cards.clear();
        preparedRecent=preparedRecommendations=null;
        click.release();
    }

    /** Starting state (Home pressed while the home screen is showing): first app focused, drawer and sheet closed. */
    void reset(boolean animated,boolean resetContent) {
        wake();
        if (actions != null) { actions.dismiss(); actions = null; }
        cancelSectionMove();
        below.snap(0);
        cancelReorder(!animated);
        zone = Z_SHELF;
        shelfIndex = 0;
        sheetOpen = false;
        drawerOpen = false;
        if (animated) {
            sheet.target = 0;
            drawer.target = 0;
        } else {
            sheet.snap(0);
            drawer.snap(0);
        }
        if (pickIndex != 0 && !picks.isEmpty()) {
            pickIndex = 0;
            updatePicks();
        }
        // HOME from another app preserves its last content window; a second HOME resets it.
        if (resetContent && tab != firstTab()) selectTab(firstTab());
        if(running&&continueOn)continuing.resumeArt();
        retarget(!animated);
        motionDirty = true;
    }

    void saveContentState(android.os.Bundle state){
        state.putInt("content_tab",tab);state.putInt("recent_offset",recentOffset);state.putInt("recommended_offset",recommendationOffset);
    }
    void restoreContentState(android.os.Bundle state){
        if(state==null)return;
        tab=state.getInt("content_tab",0)==1?1:0;
        recentOffset=Math.max(0,state.getInt("recent_offset",0));
        recommendationOffset=Math.max(0,state.getInt("recommended_offset",0));renderedTab=-1;
    }

    /** The three corner buttons, with their names in the current language. */
    private void makeLightButtons() {
        lightButtons.clear();
        if (!Prefs.television()) {
            lightButtons.add(HomeApps.tvSettings());
            lightButtons.add(QuickShortcuts.item(getContext(), "status"));
        } else {
            lightButtons.add(HomeApps.action(HomeApps.LIVE_TV, "live", Ui.tr("TV aberta", "Live TV"), "live_tv"));
            lightButtons.add(HomeApps.action(HomeApps.INPUTS, "inputs", Ui.tr("Entradas HDMI", "HDMI Inputs"), "hdmi"));
        }
        lightButtons.add(HomeApps.action(HomeApps.QUICK_SETTINGS, "settings", Ui.tr("Configurações", "Settings"), "settings"));
    }

    private static String[] translatedTabs;
    private static int tabsGeneration = -1;
    private static String[] tabs() {
        if (translatedTabs == null || tabsGeneration != Ui.generation) {
            translatedTabs = new String[] {Ui.tr("Assistidos recentemente", "Recently watched"), Ui.tr("Recommended"), Ui.tr("Movies"), Ui.tr("Shows")}; tabsGeneration = Ui.generation;
        }
        return translatedTabs;
    }

    @Override public void onData() {
        wake();
        updatePalette();
        checkNight(true);
        layoutStatus();
        heroDirty = statusDirty = drawerDirty = true;
        platter.invalidate();
    }

    @Override public void onContinueWatching() { wake(); updateCards(); }

    @Override public void onRecs() {
        wake();
        updatePicks();
    }

    private void invalidateAll() {
        for (View v : new View[] {sky, heroLayer, status, platter, tiles, drawerLayer, otherLayer}) v.invalidate();
    }

    // MARK: - Size and layers

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        float kk = width / 1920f;
        measure(sky, width, height);
        measure(heroLayer, width, Math.round(790 * kk));
        measure(status, width, Math.round(210 * kk));
        // Sized to what they draw (the glass and its shadow; the grown tiles and their shadows): smaller layers are
        // cheaper for the GPU to composite on every frame of the drawer animation.
        measure(platter, width, Math.round(320 * kk));
        measure(tiles, width, Math.round(270 * kk));
        measure(drawerLayer, width, Math.round(600 * kk));
        int rows = Math.max(1, Math.min(5, (others.size() + COLUMNS - 1) / COLUMNS - otherFirstRow));
        measure(otherLayer, width, Math.round((rows * GRID_ROW + 110) * kk));
        measure(sheetLayer, width, height);
        measure(toastLayer, Math.round(1000 * kk), Math.round(220 * kk));
        setMeasuredDimension(width, height);
    }

    private static void measure(View v, int width, int height) {
        v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l, height = b - t;
        float kk = width / 1920f;
        sky.layout(0, 0, width, height);
        place(heroLayer, 0, 0);
        place(status, width - status.getMeasuredWidth(), 0);
        place(platter, 0, Math.round(750 * kk));
        place(tiles, 0, Math.round(790 * kk));
        place(drawerLayer, 0, Math.round(470 * kk));
        place(otherLayer, 0, Math.round((otherTop() + otherFirstRow * GRID_ROW - 72) * kk));
        sheetLayer.layout(0, 0, width, height);
        sheetLayer.setPivotX(width / 2f);
        sheetLayer.setPivotY(height / 2f);
        place(toastLayer, (width - toastLayer.getMeasuredWidth()) / 2, 0);
    }

    private static void place(View v, int x, int y) {
        v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        screen.set(0, 0, w, h);
        scene.setSize(w, h);
        makePaints();
        gridLabels = null;
        cacheOtherLabels();
        clockMinute = -1;
        updateClock();
        layoutShelf();
        layoutStatus();
        layoutDrawer();
        layoutLight();
        hero = null;
        heroFrom = null;
        updatePicks();
        motionDirty = true;
        wake();
    }

    private void makePaints() {
        labelPaint = Ui.text(Ui.medium, 25 * k, 0xC7FFFFFF);
        labelPaint.setLetterSpacing(0.1f);
        titlePaint = new TextPaint(Ui.text(Ui.displayMedium, 76 * k, 0xFFFFFFFF));
        titlePaint.setLetterSpacing(0.005f);
        metaPaint = Ui.text(Ui.medium, 29 * k, 0xD1FFFFFF);
        metaPaint.setLetterSpacing(0.014f);
        clockPaint = Ui.numbers(Ui.thin, 176 * k, 0xFFFFFFFF);
        clockPaint.setLetterSpacing(-0.01f);
        for (Paint p : new Paint[] {labelPaint, titlePaint, metaPaint, clockPaint}) p.setShadowLayer(22 * k, 0, 2 * k, 0x59000000);
        lTab = Ui.text(Ui.medium, 30 * k, L_DIM);
        lTabOn = Ui.text(Ui.semibold, 30 * k, L_INK);
        lCardTitle = Ui.text(Ui.semibold, 31 * k, L_INK);
        lCardTitle.setLetterSpacing(0.012f);
        lCardMeta = Ui.text(Ui.medium, 25 * k, L_DIM);

        lTip = Ui.text(Ui.semibold, 25 * k, L_INK);
        lTip.setTextAlign(Paint.Align.CENTER);
        lDate = Ui.text(Ui.medium, 34 * k, L_DIM);
        lClock = Ui.numbers(Ui.semibold, 200 * k, L_INK);
        lClock.setLetterSpacing(-0.01f);
        lWeather = Ui.text(Ui.semibold, 34 * k, L_DIM);
        headerTime = Ui.numbers(Ui.semibold, 38 * k, L_INK);
        headerText = Ui.text(Ui.medium, 25 * k, L_DIM);
        headerGreeting = Ui.text(Ui.medium, 26 * k, L_INK);
        headerLine.setStyle(Paint.Style.STROKE);
        headerLine.setStrokeWidth(1.2f * k);

        lightGradient = null;
        buttonPaint = Ui.text(Ui.semibold, 29 * k, INK);
        capsuleText = Ui.numbers(Ui.medium, 29 * k, 0xF2FFFFFF);
        tipPaint = Ui.text(Ui.semibold, 25 * k, 0xF2FFFFFF);
        tipPaint.setTextAlign(Paint.Align.CENTER);
        tipPaint.setShadowLayer(16 * k, 0, 1 * k, 0x99000000);



        rowTitle = Ui.text(Ui.semibold, 25 * k, 0xB3FFFFFF);
        drawerLabel = Ui.text(Ui.medium, 23 * k, 0xEBFFFFFF);
        drawerLabel.setTextAlign(Paint.Align.CENTER);
        cardTitle = Ui.text(Ui.semibold, 25 * k, 0xB3FFFFFF);
        cardText = Ui.text(Ui.medium, 29 * k, 0xF2FFFFFF);
        cardDim = Ui.numbers(Ui.medium, 23 * k, 0xADFFFFFF);
        cardDimRight = Ui.text(Ui.medium, 21 * k, 0x99FFFFFF);
        cardDimRight.setTextAlign(Paint.Align.RIGHT);
        bigTemp = Ui.numbers(Ui.light, 100 * k, 0xFFFFFFFF);
        hourPaint = Ui.numbers(Ui.medium, 22 * k, 0xADFFFFFF);
        hourPaint.setTextAlign(Paint.Align.CENTER);
        hourTemp = Ui.numbers(Ui.medium, 26 * k, 0xF2FFFFFF);
        hourTemp.setTextAlign(Paint.Align.CENTER);
        hourRain = Ui.numbers(Ui.semibold, 19 * k, RAIN_DARK);
        hourRain.setTextAlign(Paint.Align.CENTER);


        sheetTitle = Ui.text(Ui.displaySemibold, 40 * k, 0xFFFFFFFF);
        sheetTitle.setTextAlign(Paint.Align.CENTER);
        gridLabel = new TextPaint(Ui.text(Ui.medium, 24 * k, 0x99FFFFFF));
        gridLabel.setTextAlign(Paint.Align.CENTER);
        toastPaint = new TextPaint(Ui.text(Ui.medium, 26 * k, 0xF2FFFFFF));
        shadow.setMaskFilter(new BlurMaskFilter(SHADOW_BLUR * k, BlurMaskFilter.Blur.NORMAL));
        shadowSoft.setMaskFilter(new BlurMaskFilter(12 * k, BlurMaskFilter.Blur.NORMAL));
        shadowDeep.setMaskFilter(new BlurMaskFilter(DEEP_BLUR * k, BlurMaskFilter.Blur.NORMAL));
        halo.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * k);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2.5f * k);
    }

    // MARK: - Frames: 60 per second while something moves; nothing is redrawn when still

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (frameTimeNanos - lastNanos) / 1e9f);
        lastNanos = frameTimeNanos;
        long up = SystemClock.uptimeMillis();
        // The weather card shows nothing that changes by the minute: a minute in this key re-rendered it in software
        // and re-uploaded the ~3 MB texture on the next drawer opening (a heavy first frame on the Mi Stick).
        long stamp = data.weatherAt ^ ((long) Prefs.version << 32) ^ Float.floatToIntBits(dark()) ^ Ui.generation;
        if (stamp != weatherCacheStamp) { weatherCacheStamp = stamp; weatherCacheKey = Long.toString(stamp); }
        boolean sectionMoving = sectionMove != 0;
        if (sectionMoving) {
            sectionWiggle = MoveFeedback.angle(up - sectionMoveAt);
            if (sectionMove == Z_DRAWER) drawerDirty = true;
            else gridDirty = true;
        }

        boolean tilesMoving = step(tileFocus, dt);
        if (reorderAnim) tilesMoving |= stepReorder(dt, up);
        boolean statusMoving = step(statusFocus, dt);
        boolean heroMoving = heroFocus.step(dt);
        boolean topMoving = step(topFocus, dt), cardsMoving = step(cardFocus, dt);
        if (topMoving) status.invalidate();
        heroMoving |= topMoving | cardsMoving;
        boolean drawerItemsMoving = step(drawerFocus, dt);
        boolean gridMoving = step(gridFocus, dt) | scroll.step(dt);
        boolean belowMoving = below.step(dt);
        int first = Math.max(0, (int) Math.floor((below.value - otherTop() - GRID_ROW) / GRID_ROW));
        if (first != otherFirstRow) { otherFirstRow = first; requestLayout(); gridDirty = true; }
        float otherTranslation=(1-drawer.value)*SHIFT-below.value;
        int last=VisibleRows.end(otherTop(),GRID_ROW,otherTranslation,h/k);
        if(last!=otherLastRow){otherLastRow=last;gridDirty=true;}
        boolean otherMoving = step(otherFocus, dt);
        boolean drawerMoving = drawer.step(dt);
        boolean sheetMoving = sheet.step(dt);
        boolean toastFading = tickToast(up);
        boolean heroFading = tickHeroFade(up);
        boolean bgFading = tickBackgroundFade(up);
        boolean nightMoving = tickNight(up);

        if (drawerMoving || belowMoving || sheetMoving || motionDirty) updateMotion();
        // Opening and closing the drawer only moves the cached layers: redrawing the glass on every frame made the TV
        // drop frames (measured: ~50 ms of GPU per frame). The app tiles have no glass; the top buttons fade out as
        // they rise; the drawer is drawn resting in place; the glass shelf is redrawn once the drawer stops, so the
        // glass shows the background where it ended up.
        if (tilesMoving || tilesDirty) tiles.invalidate();
        if (statusMoving || statusDirty || bgFading) status.invalidate();
        if (heroMoving || heroDirty || heroFading) heroLayer.invalidate();
        if (drawerItemsMoving || drawerDirty || bgFading) drawerLayer.invalidate();
        if (otherMoving || gridDirty || bgFading) otherLayer.invalidate();
        if (gridMoving || gridDirty) sheetLayer.invalidate();
        if (drawerMoving || bgFading) sky.invalidate();
        if (bgFading || (drawerWasMoving && !drawerMoving)) platter.invalidate();
        drawerWasMoving = drawerMoving;
        tilesDirty = statusDirty = heroDirty = drawerDirty = gridDirty = motionDirty = false;
        if (updateClock()) {
            layoutStatus();
            status.invalidate();
            if (hero != null && hero.clock) {
                refreshHero(false);
                heroLayer.invalidate();
            }
            if (!picksOn() || emptyContinue()) heroLayer.invalidate();
        }

        // Busy: something is moving, or the sheet or the drawer is open.
        boolean busy = sectionMoving || belowMoving || otherMoving || tilesMoving || statusMoving || heroMoving || drawerItemsMoving || gridMoving || drawerMoving
                || sheetMoving || toastFading || heroFading || bgFading || nightMoving || toast != null || !toasts.isEmpty();
        idle = !busy;
        // Every vsync while moving, lightweight mode included: its former 30 ms pacing halved the frame rate (measured on
        // the Mi Stick: exactly 30 fps, felt as judder), while the same device draws shelf focus at 60 fps without it.
        if (busy) Choreographer.getInstance().postFrameCallback(this);
        else Choreographer.getInstance().postFrameCallbackDelayed(this,ClockFormat.minuteDelay(System.currentTimeMillis()));
    }

    private void wake() {
        if (!running || !idle) return;
        idle = false;
        lastNanos = 0;
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    private static boolean step(Spring[] springs, float dt) {
        boolean moving = false;
        for (Spring s : springs) moving |= s.step(dt);
        return moving;
    }

    /** Drawer and sheet: the layers' positions and opacity (without redrawing their content). */
    private void updateMotion() {
        float d = drawer.value, dc = Ui.clamp(d), s = Ui.clamp(sheet.value);
        float home = 1 - s, top = home * (1 - smoothstep(0, 0.32f, dc));
        float shift = SHIFT * k;
        float lift = -d * shift - below.value * k;
        show(heroLayer, top, lift);
        show(status, top, lift);
        show(platter, home, lift);
        show(tiles, home, lift);
        float da = home * smoothstep(0.1f, 0.6f, dc);
        drawerLayer.setTranslationY((1 - d) * shift - below.value * k);
        otherLayer.setTranslationY((1 - d) * shift - below.value * k);
        otherLayer.setAlpha(da);
        otherLayer.setVisibility(da > 0.002f ? VISIBLE : INVISIBLE);
        drawerLayer.setAlpha(da);
        drawerLayer.setVisibility(da > 0.002f ? VISIBLE : INVISIBLE);
        sheetLayer.setVisibility(s > 0.001f || sheetOpen ? VISIBLE : INVISIBLE);
        sheetLayer.setAlpha(s);
        float scale = 1.04f - 0.04f * s;
        sheetLayer.setScaleX(scale);
        sheetLayer.setScaleY(scale);
    }

    private static void show(View v, float alpha, float translationY) {
        v.setAlpha(alpha);
        v.setTranslationY(translationY);
        v.setVisibility(alpha > 0.002f ? VISIBLE : INVISIBLE);
    }

    private boolean updateClock() {
        long now = System.currentTimeMillis();
        long minute = now / 60_000;
        if (minute == clockMinute || clockPaint == null) return false;
        clockMinute = minute;
        clockText = Ui.clock(now);
        dateText = Ui.date(now);
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(Ui.locale(), "EEE d MMM");
        headerShortDate = new java.text.SimpleDateFormat(pattern, Ui.locale()).format(new java.util.Date(now));
        java.util.Calendar local = java.util.Calendar.getInstance();
        local.setTimeInMillis(now);
        int hour = local.get(java.util.Calendar.HOUR_OF_DAY);
        String greeting = hour >= 5 && hour < 12 ? Ui.tr("Bom dia", "Good morning")
                : hour < 18 && hour >= 12 ? Ui.tr("Boa tarde", "Good afternoon")
                : hour >= 18 && hour < 22 ? Ui.tr("Boa noite", "Good evening") : Ui.tr("Boa noite", "Good night");
        fitPaint.set(headerGreeting);
        headerGreetingText = TextUtils.ellipsize(greeting, fitPaint, 348 * k, TextUtils.TruncateAt.END).toString();
        layoutHeaderActions();
        layoutHeaderInfo();
        return true;
    }

    private void updatePalette() {
        HomeData.Weather wx = data.weather;
        Shaders.sky(scene.palette, System.currentTimeMillis(), wx.sunrise, wx.sunset);
        scene.applyPalette();
    }

    // MARK: - Recommendations: hero and background

    private Recs.Pick currentPick() {
        return picks.isEmpty() ? null : picks.get(Math.min(pickIndex, picks.size() - 1));
    }

    /** This hour's picks: the cards and the background image (called when Recs notifies and every 15 s). */
    private void updatePicks() {
        if (labelPaint == null) return;
        updateCards();
    }

    private static boolean samePicks(List<Recs.Pick> a, List<Recs.Pick> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!Recs.key(a.get(i)).equals(Recs.key(b.get(i)))) return false;
        return true;
    }

    /**
     * Rebuilds the hero. With `animate`, a different title slides in while the previous one slides out; details that
     * arrive later (genre, runtime) just appear.
     */
    private void refreshHero(boolean animate) {
        Hero next = makeHero();
        if (next.sameText(hero) && hero.dots == next.dots && hero.dot == next.dot && hero.button == next.button) return;
        boolean other = hero != null && (hero.clock != next.clock || !hero.title.equals(next.title));
        if (animate && other) {
            heroFrom = hero;
            heroFadeAt = SystemClock.uptimeMillis();
        }
        boolean layoutChanged = hero == null || hero.clock != next.clock;
        hero = next;
        heroDirty = true;
        if (layoutChanged) layoutStatus();
    }

    private Hero makeHero() {
        Hero h = new Hero();
        Recs.Pick p = currentPick();
        if (p == null) {
            h.clock = true;
            h.label = Ui.date(System.currentTimeMillis()).toUpperCase(Ui.locale());
            return h;
        }
        h.label = Recs.periodLabel();
        h.title = p.title == null ? "" : p.title;
        h.meta = p.meta == null ? "" : p.meta;
        h.button = p.pkg != null;
        h.dots = picks.size();
        h.dot = pickIndex;
        h.titleLayout = StaticLayout.Builder.obtain(h.title, 0, h.title.length(), titlePaint, Math.max(1, Math.round(HERO_TITLE_W * k)))
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0, 0.94f)
                .setIncludePad(false)
                .build();
        return h;
    }

    /** ←/→ on the hero: the next (or previous) of this hour's picks. */
    private boolean browse(int dir) {
        if (picks.size() < 2) return false;
        int next = pickIndex;
        for (int i = 0; i < picks.size(); i++) {
            next = (next + dir + picks.size()) % picks.size();
            if (zone != Z_HERO || picks.get(next).pkg != null) break;
        }
        if (next == pickIndex) return false;
        pickIndex = next;
        heroSlide = dir;
        refreshHero(true);
        Backdrop b = recs.backdrop(currentPick());
        if (b != null) showBackdrop(b);
        spoken.focusMoved();
        return true;
    }

    /** Swaps the background with a cross-fade (from one image to another, from the gradient to an image, or back). */
    private void showBackdrop(Backdrop b) {
        if (b == bgTo) {if(rebindingPosters&&b==null)neutralGlass();return;}
        long up = SystemClock.uptimeMillis();
        // In the middle of another swap, start from whichever is more visible.
        if (bgFadeAt == 0 || fadeEase(bgFadeAt, up) >= 0.5f) bgFrom = bgTo;
        bgTo = b;
        bgFadeAt = rebindingPosters?0:up;
        if(rebindingPosters)bgFrom=null;
        nightPair = false;
        if (!scene.imageEffects() || b == null || glassOf(b) == null) return;
        if (bgFrom == null || glassOf(bgFrom) == null) {
            scene.setFrost(frostOf(b));
            scene.setGlassImage(glassOf(b));
        } else {
            scene.setGlassImages(glassOf(bgFrom), glassOf(b));
        }
    }

    /** What the glass shows: the photo lightened (by day), darkened (at night) or blended in between. */
    private int[] glassOf(Backdrop b) {
        if(!scene.imageEffects())return null;
        if (b.nightGlass == null || night <= 0.001f) return b.lightGlass;
        if (night >= 0.999f) return b.nightGlass;
        // During the transition, the blend (only for the current background; a photo fading in uses the nearer side).
        if (b != bgTo) return night < 0.5f ? b.lightGlass : b.nightGlass;
        for (int i = 0; i < glassNight.length; i++) glassNight[i] = mix(b.lightGlass[i], b.nightGlass[i], night);
        return glassNight;
    }

    /** No photo: the glass reads the background's own gray (not Scene's default aurora, which isn't on screen). */
    private void neutralGlass() {
        if(!scene.imageEffects()){nightPair=false;return;}
        int color = blend(0xFFEBEBF0, 0xFF141416, night);
        java.util.Arrays.fill(neutralFrostPx, color);
        java.util.Arrays.fill(neutralGlassPx, color);
        scene.setFrost(neutralFrostPx);
        scene.setGlassImage(neutralGlassPx);
        nightPair = false;
    }

    /** What the frosted glass shows (drawer, toasts): the blurred photo, darkened at night. */
    private int[] frostOf(Backdrop b) {
        if(!scene.imageEffects())return b.frost;
        if (b.nightFrost == null || night <= 0.001f) return b.frost;
        if (night >= 0.999f) return b.nightFrost;
        if (b != bgTo) return night < 0.5f ? b.frost : b.nightFrost;
        for (int i = 0; i < frostNight.length; i++) frostNight[i] = mix(b.frost[i], b.nightFrost[i], night);
        return frostNight;
    }

    // MARK: - Night look

    /** Day or night right now (from the sun at your location); `animate`: with the transition. */
    private void checkNight(boolean animate) {
        boolean want = data.isNight(System.currentTimeMillis());
        if (want == nightTarget) return;
        nightTarget = want;
        if (animate && running) {
            nightFrom = night;
            nightAt = SystemClock.uptimeMillis();
        } else {
            night = want ? 1 : 0;
            nightAt = 0;
            nightChanged();
        }
    }

    /** Advances the day ⇄ night transition; true while it runs. */
    private boolean tickNight(long up) {
        if (nightAt == 0) return false;
        float t = Ui.clamp((up - nightAt) / NIGHT_MS), to = nightTarget ? 1 : 0;
        night = nightFrom + (to - nightFrom) * t * t * (3 - 2 * t);
        if (t >= 1) {
            night = to;
            nightAt = 0;
        }
        nightChanged();
        return true;
    }

    /** Redraws for the current night value (the glass now reads the lightened, blended or darkened photo). */
    private void nightChanged() {
        if (bgFadeAt == 0 && bgTo != null && bgTo.lightGlass != null) {
            scene.setFrost(frostOf(bgTo));
            if (nightAt != 0 && bgTo.nightGlass != null) {
                if (!nightPair) {
                    scene.setGlassImages(bgTo.lightGlass, bgTo.nightGlass);
                    nightPair = true;
                }
                scene.setGlassMix(night);
            } else {
                scene.setGlassImage(glassOf(bgTo));
                nightPair = false;
            }
        } else if (bgFadeAt == 0 && bgTo == null) {
            neutralGlass();
        }
        heroDirty = statusDirty = tilesDirty = drawerDirty = gridDirty = true;
        sky.invalidate();
        platter.invalidate();
    }

    /** How dark it is: 0 = day, 1 = night (in between during the transition). */
    private float dark() {
        return night;
    }

    /** The day color or the night color, depending on the night value (blended during the transition). */
    private int tone(int onLight, int onDark) {
        return blend(onLight, onDark, dark());
    }

    private float tone(float onLight, float onDark) {
        return onLight + (onDark - onLight) * dark();
    }

    /** Text colors for the current night value (dark by day, white at night). */
    private void applyTone() {
        lTab.setColor(tone(L_DIM, 0xFF8E8E93));
        lTabOn.setColor(tone(L_INK, WHITE));
        lCardTitle.setColor(tone(L_INK, WHITE));
        lCardMeta.setColor(tone(L_DIM, 0xFF98989D));

        lTip.setColor(tone(L_INK, WHITE));
        lDate.setColor(tone(L_DIM, 0xFF98989D));
        lClock.setColor(tone(L_INK, WHITE));
        lWeather.setColor(tone(L_DIM, 0xFF98989D));
        headerTime.setColor(tone(L_INK, WHITE));
        headerText.setColor(tone(0xFF5B5D64, 0xFFD0D1D6));
        headerGreeting.setColor(tone(L_INK, WHITE));
        headerLine.setColor(tone(0x26000000, 0x33FFFFFF));

    }

    private boolean tickBackgroundFade(long up) {
        if (bgFadeAt == 0) return false;
        float e = fadeEase(bgFadeAt, up);
        if (scene.imageEffects() && bgTo != null && bgFrom != null && glassOf(bgFrom) != null && glassOf(bgTo) != null) {
            if (drawerLayer.getVisibility() == VISIBLE || toast != null) {
                int[] from = frostOf(bgFrom), to = frostOf(bgTo);
                for (int i = 0; i < frostMix.length; i++) frostMix[i] = mix(from[i], to[i], e);
                scene.setFrost(frostMix);
            }
            scene.setGlassMix(e);
        }
        if (e >= 1) {
            bgFadeAt = 0;
            bgFrom = null;
            if (bgTo == null) {
                neutralGlass();
            } else {
                scene.setFrost(frostOf(bgTo));
                if (glassOf(bgTo) != null) scene.setGlassImage(glassOf(bgTo));
            }
        }
        return true;
    }

    private boolean tickHeroFade(long up) {
        if (heroFadeAt == 0) return false;
        if (fadeEase(heroFadeAt, up) >= 1) {
            heroFadeAt = 0;
            heroFrom = null;
        }
        return true;
    }

    private float fadeEase(long start, long up) {
        return fadeEase(start, up, FADE_MS);
    }

    private static float fadeEase(long start, long up, float duration) {
        float t = Ui.clamp((up - start) / duration);
        return t * t * (3 - 2 * t);
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // MARK: - Background

    private void drawSky(Canvas c) {
        drawLightSky(c);
    }

    /** Blends a color on top (the drawer's veil) as part of drawing the image, without another layer. */
    private android.graphics.ColorFilter veil(float amount, int color) {
        if (amount <= 0.002f) return null;
        amount = Math.round(amount * 255) / 255f;
        if (amount == veilAmount && color == veilColor) return veilFilter;
        veilAmount = amount;
        veilColor = color;
        float keep = 1 - amount;
        return veilFilter = new android.graphics.ColorMatrixColorFilter(new float[] {
            keep, 0, 0, 0, ((color >> 16) & 255) * amount,
            0, keep, 0, 0, ((color >> 8) & 255) * amount,
            0, 0, keep, 0, (color & 255) * amount,
            0, 0, 0, 1, 0});
    }

    private void drawImage(Canvas c, Bitmap image, float alpha) {
        if (image == null || alpha <= 0.004f) return;
        imagePaint.setAlpha(Math.round(255 * Math.min(1, alpha)));
        c.drawBitmap(image, null, screen, imagePaint);
    }

    // MARK: - Hero

    private static void text(Canvas c, String s, float x, float y, Paint p, int color, float alpha) {
        if (s == null || s.isEmpty()) return;
        p.setColor(Ui.alpha(color, alpha));
        c.drawText(s, x, y, p);
    }

    // MARK: - Corner capsule: time and weather (when the hero shows a recommendation) and icon-only buttons

    /** Live TV, the inputs with a device on, All Apps and Settings. */
    private static List<HomeApps.Item> statusList(Context c) {
        List<HomeApps.Item> list = new ArrayList<>();
        list.addAll(HomeApps.shortcuts(c));
        list.add(HomeApps.settings());
        return list;
    }

    private void setStatusItems(List<HomeApps.Item> list) {
        boolean same = list.size() == statusItems.size();
        for (int i = 0; same && i < list.size(); i++) same = list.get(i).key.equals(statusItems.get(i).key);
        String focusedKey = statusIndex < statusItems.size() ? statusItems.get(statusIndex).key : null;
        statusItems = list;
        if (!same || statusFocus.length != list.size()) {
            statusFocus = new Spring[list.size()];
            statusRects = new RectF[list.size()];
            int index = Math.min(statusIndex, Math.max(0, list.size() - 1));
            for (int i = 0; i < statusFocus.length; i++) {
                statusFocus[i] = new Spring(0.26f, 0.85f, 0);
                statusRects[i] = new RectF();
                if (list.get(i).key.equals(focusedKey)) index = i;   // a device came on: keep focus on the same button
            }
            statusIndex = index;
            if (zone == Z_STATUS && list.isEmpty()) zone = Z_SHELF;
            aim(statusFocus, zone == Z_STATUS ? statusIndex : -1, true);   // only the capsule's springs
            statusDirty = true;
        }
        layoutStatus();
    }

    private boolean showInfo() {
        return hero != null && !hero.clock;
    }

    private void layoutStatus() {
        if (capsuleText == null) return;
        layoutHeaderInfo();
        int n = statusItems.size();
        float b = BUTTON * k, gap = 4 * k, pad = 6 * k;
        float info = 0;
        if (showInfo()) {
            info = 26 * k + capsuleText.measureText(clockText);
            HomeData.Weather wx = data.weather;
            if (!Double.isNaN(wx.temp) && weatherOn) {
                info += 22 * k + Symbols.width(getContext(), Symbols.weather(wx.code, wx.isDay), 24 * k) + 8 * k
                        + capsuleText.measureText(HomeData.degrees(wx.temp));
            }
            info += 22 * k;
        }
        capsuleInfoW = info;
        float buttons = n * b + Math.max(0, n - 1) * gap;
        float right = CONTENT_R * k, top = CAPSULE_TOP * k;
        float left = right - pad - buttons - (info > 0 ? info + 12 * k : pad);
        capsuleRect.set(left, top, right, top + CAPSULE_H * k);
        float x = right - pad - buttons, cy = capsuleRect.centerY();
        for (int i = 0; i < n; i++) {
            statusRects[i].set(x, cy - b / 2, x + b, cy + b / 2);
            x += b + gap;
        }
        statusDirty = true;
    }

    // MARK: - Shelf

    private void setShelf(List<HomeApps.Item> list) {
        wake();
        cancelSectionMove();
        cancelReorder(true);    // the list changed under the move (an app installed or removed)
        tileShaders = new LinearGradient[0];
        boolean same = list.size() == shelf.size();
        for (int i = 0; same && i < list.size(); i++) same = list.get(i).key.equals(shelf.get(i).key);
        shelf = list;
        if (!same || tileFocus.length != list.size()) {
            tileFocus = new Spring[list.size()];
            tileSlide = new Spring[list.size()];
            tileRects = new RectF[list.size()];
            for (int i = 0; i < tileFocus.length; i++) {
                tileFocus[i] = new Spring(0.34f, 0.74f, 0);
                tileSlide[i] = new Spring(0.32f, 0.86f, 0);
                tileRects[i] = new RectF();
            }
            shelfIndex = Math.min(shelfIndex, Math.max(0, list.size() - 1));
            retarget(true);
            layoutShelf();
        }
        prepareShelfLogos();
        tilesDirty = true;
        platter.invalidate();
    }

    private void layoutShelf() {
        int n = shelf.size();
        float pad = SHELF_PAD * k, gap = TILE_GAP * k;
        float full = (CONTENT_R - MARGIN) * k;
        // Tiles in the TV banners' own 16:9, so an app's art is never stretched; never taller than L_TILE_H. The glass
        // keeps its bottom edge, so with more apps it only gets lower.
        float tw = Math.min(L_TILE_H * 16f / 9 * k, (full - Math.max(0, n - 1) * gap) / Math.max(1, n));
        float th = tw * 9f / 16;
        float width = n * tw + Math.max(0, n - 1) * gap, left = (w - width) / 2f;
        float top = (L_PLATTER_TOP + L_TILE_H) * k - th;
        platterRect.set(left - pad, top, left + width + pad, top + th + 2 * pad);
        float x = left, y = top + pad;
        for (int i = 0; i < n; i++) {
            tileRects[i].set(x, y, x + tw, y + th);
            x += tw + gap;
        }
        int pixelWidth=Math.round(tw);
        if(pixelWidth!=crispWidth){crispLogos.clear();crispWidth=pixelWidth;}
        prepareShelfLogos();
    }

    /** Shelf layer: the glass bar and its shadow (it only changes with the background). */
    private void drawPlatter(Canvas c) {
        if (shelf.isEmpty()) return;
        float radius = (TILE_RADIUS + SHELF_PAD) * k;
        tmp.set(platterRect);
        tmp.offset(0, 18 * k);
        shadow.setColor(tone(0x1F000000, 0x52000000));
        shadows.draw(c, tmp, radius, SHADOW_BLUR * k, shadow);
        drawShelfSurface(c, platterRect, radius);
    }

    /** One material for the shelf and header; older TVs reuse the existing translucent fallback. */
    private void drawShelfSurface(Canvas c, RectF bounds, float radius) {
        // White glass by day; smoked glass at night.
        scene.liquid(c, bounds, radius, tone(0xFFFFFFFF, SMOKE), tone(0.42f, 0.12f), tone(0.03f, 0.08f),
                tone(30, 34) * k, tone(22, 28) * k, tone(0.65f, 0.95f), 1);
    }

    private void drawTiles(Canvas c) {
        // The focused one last: it grows over its neighbors without being covered by them.
        int top = -1;
        float most = 0.02f;
        for (int i = 0; i < shelf.size(); i++) {
            if (tileFocus[i].value > most) {
                most = tileFocus[i].value;
                top = i;
            }
        }
        if (liftIndex >= 0 && liftIndex < shelf.size() && moveLift.value > 0.02f) top = liftIndex;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < shelf.size(); i++) {
                if ((pass == 0) == (i == top)) continue;
                // Moving apps: the tile slides from where it was and the lifted one wiggles (only then: no extra work
                // otherwise).
                float dx = i < tileSlide.length ? tileSlide[i].value : 0;
                float angle = i == liftIndex ? wiggle * moveLift.value : 0;
                if (dx == 0 && angle == 0) {
                    drawShelfTile(c, i);
                    continue;
                }
                c.save();
                c.translate(dx, 0);
                c.rotate(angle, tileRects[i].centerX(), tileRects[i].centerY());
                drawShelfTile(c, i);
                c.restore();
            }
        }
    }

    /**
     * App icon like the ones in the tvOS 27 Dock: the brand color edge to edge (a soft gradient) with the logo.
     * Focused: it grows 20% from the center, with a deep shadow (it "rises" toward the viewer) and a subtle glow at the
     * top. No border and no reflection, as Apple does it.
     */
    private void drawShelfTile(Canvas c, int i) {
        HomeApps.Item it = shelf.get(i);
        // Lifted to be moved: a little bigger than focused, with a deeper shadow.
        float l = i == liftIndex ? Math.max(0, moveLift.value) : 0;
        float f = tileFocus[i].value, fc = Ui.clamp(f), s = 1 + FOCUS_GROW * f + LIFT_GROW * l;
        RectF base = tileRects[i];
        float bw = base.width(), bh = base.height(), r0 = TILE_RADIUS * k;
        float cx = base.centerX(), cy = base.centerY();
        cell.set(cx - bw * s / 2, cy - bh * s / 2, cx + bw * s / 2, cy + bh * s / 2);
        float r = r0 * s;
        // Shadow at the resting icon size, enlarged by the transform (so the blur stays cached): short at rest, deep
        // and lower when focused.
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
        tmp.offset(0, (4 + 14 * f + 6 * l) * k);
        shadowDeep.setColor(Ui.alpha(0xFF000000, tone(0.14f, 0.3f) + tone(0.18f, 0.3f) * fc + 0.14f * Ui.clamp(l)));
        shadows.draw(c, tmp, r0, DEEP_BLUR * k, shadowDeep);
        c.restore();
        int[] style = TILE_STYLE.get(it.key);
        if (it.logo != null && style != null) {
            if (tileShaders.length != shelf.size() || tileShaderH != bh) {
                tileShaders = new LinearGradient[shelf.size()];
                tileShaderH = bh;
                tileGlow = new LinearGradient(0, -bh / 2, 0, 0, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            }
            if (tileShaders[i] == null) {
                tileShaders[i] = new LinearGradient(0, -bh / 2, 0, bh / 2, style[0], style[1], Shader.TileMode.CLAMP);
            }
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
            tileFill.setShader(tileShaders[i]);
            tileFill.setAlpha(255);
            c.drawRoundRect(tmp, r0, r0, tileFill);
            if (fc > 0.01f) {
                tileFill.setShader(tileGlow);
                tileFill.setAlpha(Math.round(255 * fc));
                c.drawRoundRect(tmp, r0, r0, tileFill);
                tileFill.setAlpha(255);
            }
            tileFill.setShader(null);
            c.restore();
            drawLogo(c, it, cell, s, style[2]);
        } else if (it.logo != null) {
            tileFill.setShader(null);
            tileFill.setColor(0xFF161618);
            c.drawRoundRect(cell, r, r, tileFill);
            drawLogo(c, it, cell, s, 0);
        } else {
            if (it.tile != null) roundImage(c, it.tile, cell, r);
        }
        lightFocusRing(c,cell,r,fc);
    }

    /** Two crisp strokes keep focus visible over both pale artwork and dark logos. */
    private void lightFocusRing(Canvas c,RectF bounds,float radius,float focus){
        float amount=Ui.clamp(focus)*(1-dark());if(amount<=.01f)return;
        rim.setShader(null);rim.setColor(Ui.alpha(0xFF242428,amount));rim.setStrokeWidth(4*k);
        tmp.set(bounds);tmp.inset(-4*k,-4*k);c.drawRoundRect(tmp,radius+4*k,radius+4*k,rim);
        rim.setColor(Ui.alpha(WHITE,.95f*amount));rim.setStrokeWidth(2*k);c.drawRoundRect(bounds,radius,radius,rim);
        rim.setStrokeWidth(2.5f*k);
    }

    /**
     * `image` stretched over `cell`, with corners of radius `r`. Android 12+ clips to the rounded shape; on Android 9 to
     * 11 that anti-aliased clip costs the GPU a mask on every frame of a focus animation (measured on a Mi Box 3: most of
     * a 49 ms frame), so there the image is the shader of a rounded rectangle instead, which looks the same.
     */
    private void roundImage(Canvas c, Bitmap image, RectF cell, float r) {
        roundImage(c,image,cell,r,null);
    }
    private void roundImage(Canvas c, Bitmap image, RectF cell, float r,BitmapShader preparedShader) {
        if (!Ui.LEGACY) {
            clip.reset();
            clip.addRoundRect(cell, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawBitmap(image, null, cell, bitmapPaint);
            c.restore();
            return;
        }
        BitmapShader shader=preparedShader;
        java.lang.ref.WeakReference<BitmapShader> cachedShader=shader==null?imageShaders.get(image):null;
        if(shader==null&&cachedShader!=null)shader=cachedShader.get();
        if (shader == null) {
            shader = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            imageShaders.put(image, new java.lang.ref.WeakReference<>(shader));
        }
        imageMatrix.setScale(cell.width() / image.getWidth(), cell.height() / image.getHeight());
        imageMatrix.postTranslate(cell.left, cell.top);
        shader.setLocalMatrix(imageMatrix);
        imageFill.setShader(shader);
        c.drawRoundRect(cell, r, r, imageFill);
        imageFill.setShader(null);
    }

    /**
     * Logo at its "optical" size (all with the same amount of ink, plus each one's own adjustment, never past 80% × 62%
     * of the tile), centered and already filtered down to the focused tile size: when focused it is drawn pixel for
     * pixel, without resampling.
     */
    private void drawLogo(Canvas c, HomeApps.Item it, RectF box, float s, int mode) {
        Bitmap logo = crispLogo(it, mode == 2 && it.logoDark != null);
        if (logo == null) return;
        float scale = s / (1 + FOCUS_GROW);
        float dw = logo.getWidth() * scale, dh = logo.getHeight() * scale;
        float left = box.centerX() - dw / 2, top = box.centerY() - dh / 2;
        if (scale > 0.998f) {
            left = Math.round(left);
            top = Math.round(top);
        }
        tmp.set(left, top, left + dw, top + dh);
        bitmapPaint.setColorFilter(mode == 1 ? whiteLogo : null);
        c.drawBitmap(logo, null, tmp, bitmapPaint);
        bitmapPaint.setColorFilter(null);
    }

    private Bitmap crispLogo(HomeApps.Item it, boolean dark) {
        Bitmap source=dark?it.logoDark:it.logo;
        Bitmap cached=crispLogos.get(source);
        return cached==null?source:cached;
    }

    /** Called after catalogue/geometry changes, never by onDraw. Existing source identities retain prepared pixels. */
    private void prepareShelfLogos() {
        if(tileRects.length==0 || tileRects[0].width()<=0)return;
        java.util.Set<Bitmap> wanted=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Bitmap,Boolean>());
        for(HomeApps.Item item:shelf){
            if(item.logo!=null){wanted.add(item.logo);prepareLogo(item,item.logo);}
            if(item.logoDark!=null){wanted.add(item.logoDark);prepareLogo(item,item.logoDark);}
        }
        crispLogos.keySet().retainAll(wanted);
    }
    private void prepareLogo(HomeApps.Item item,Bitmap source){
        if(crispLogos.containsKey(source))return;
        float bw=tileRects[0].width()*(1+FOCUS_GROW),bh=tileRects[0].height()*(1+FOCUS_GROW);
        float lw=source.getWidth(),lh=source.getHeight();
        float scale=(float)Math.sqrt(bw*bh*.085f/(lw*lh*Math.max(.08f,item.logoInk)))*item.logoScale;
        scale=Math.min(scale,Math.min(bw*.76f/lw,bh*.58f/lh));
        crispLogos.put(source,downscale(source,Math.round(lw*scale),Math.round(lh*scale)));
    }

    /** Halves the size while it can (a 2×2 average, no aliasing), then finishes at the exact size. */
    private static Bitmap downscale(Bitmap src, int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        Bitmap b = src;
        while (b.getWidth() / 2 >= w && b.getHeight() / 2 >= h) {
            Bitmap half = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            if (b != src) b.recycle();
            b = half;
        }
        Bitmap out = Bitmap.createScaledBitmap(b, w, h, true);
        if (b != src && b != out) b.recycle();
        return out;
    }

    // MARK: - Bottom drawer

    private static List<HomeApps.Item> drawerList(Context c) {
        List<HomeApps.Item> list = HomeApps.allInputs(c);
        list.addAll(HomeApps.drawerShortcuts(c));
        return list;
    }

    private void setDrawerItems(List<HomeApps.Item> list) {
        int inputs = 0;
        while (inputs < list.size() && (list.get(inputs).kind == HomeApps.INPUT || list.get(inputs).kind == HomeApps.LIVE_TV || list.get(inputs).kind == HomeApps.TV_INPUT_SETTINGS)) inputs++;
        String focused=drawerIndex<drawerItems.size()?drawerItems.get(drawerIndex).key:null;
        boolean same = list.size() == drawerItems.size();
        for (int i = 0; same && i < list.size(); i++) {
            same = list.get(i).key.equals(drawerItems.get(i).key) && list.get(i).connected == drawerItems.get(i).connected
                && list.get(i).label.equals(drawerItems.get(i).label) && java.util.Objects.equals(list.get(i).symbol,drawerItems.get(i).symbol);
        }
        drawerItems = list;
        drawerInputs = inputs;
        if(focused!=null)for(int i=0;i<list.size();i++)if(focused.equals(list.get(i).key)){drawerIndex=i;break;}
        drawerIndex=Math.min(drawerIndex,Math.max(0,list.size()-1));
        if (drawerFocus.length != list.size()) {
            drawerFocus = new Spring[list.size()];
            drawerRects = new RectF[list.size()];
            for (int i = 0; i < drawerFocus.length; i++) {
                drawerFocus[i] = new Spring(0.28f, 0.85f, 0);
                drawerRects[i] = new RectF();
            }
            drawerIndex = Math.min(drawerIndex, Math.max(0, list.size() - 1));
            retarget(true);
        }
        aim(drawerFocus,zone==Z_DRAWER?drawerIndex:-1,true);
        layoutDrawer();
        if (!same) drawerDirty = true;
    }

    private void layoutDrawer() {
        int n = drawerItems.size();
        float left = MARGIN * k, right = CONTENT_R * k;
        float gap = 18 * k, groupGap = 56 * k;
        int groups = drawerInputs > 0 && drawerInputs < n ? 2 : 1;
        float tw = Math.min(230 * k, (right - left - Math.max(0, n - groups) * gap - (groups - 1) * groupGap) / Math.max(1, n));
        float x = left, top = ROW_TOP * k;
        for (int i = 0; i < n; i++) {
            if (i == drawerInputs && i > 0) x += groupGap - gap;
            drawerRects[i].set(x, top, x + tw, top + ROW_H * k);
            x += tw + gap;
        }
        weatherCard.set(left, CARDS_TOP * k, right, (CARDS_TOP + CARDS_H) * k);
        weatherCacheBounds.set(weatherCard); weatherCacheBounds.inset(-50 * k, -50 * k);
        if (drawerLabel != null) {
            drawerLabels = new String[n]; fitPaint.set(drawerLabel);
            for (int i = 0; i < n; i++) drawerLabels[i] = TextUtils.ellipsize(drawerItems.get(i).label,
                    fitPaint, drawerRects[i].width() - 14 * k, TextUtils.TruncateAt.END).toString();
        }
        drawerDirty = true;
    }

    private void openDrawer() {
        if (drawerItems.isEmpty()) return;
        drawerOpen = true;
        drawer.target = 1;
        zone = Z_DRAWER;
        drawerIndex = shelf.isEmpty() ? 0 : nearest(drawerRects, tileRects[shelfIndex].centerX());
        for (int i = 0; i < drawerFocus.length; i++) drawerFocus[i].snap(i == drawerIndex ? 1 : 0);
        drawerDirty = true;
    }

    private void closeDrawer() {
        below.target = 0;
        drawerOpen = false;
        drawer.target = 0;
        zone = Z_SHELF;
        for (Spring f : drawerFocus) f.snap(0);   // the drawer goes away: its focus doesn't need to animate
        if (!shelf.isEmpty() && drawerIndex < drawerRects.length) shelfIndex = nearest(tileRects, drawerRects[drawerIndex].centerX());
    }

    /** Color for the drawer's text and symbols: dark by day, white at night. */
    private int drawerInk(float alpha) {
        return Ui.alpha(tone(L_INK, 0xFFFFFFFF), alpha);
    }

    /** Glass for a drawer card or button: white by day, smoked at night (both during the transition). */
    private void drawerGlass(Canvas c, RectF r, float radius, float lightTint, int shadowColor, float drop) {
        float dk = dark();
        if (dk < 0.999f) {
            tmp.set(r);
            tmp.offset(0, drop);
            shadow.setColor(Ui.alpha(shadowColor, 1 - dk));
            shadows.draw(c, tmp, radius, SHADOW_BLUR * k, shadow);
            scene.glass(c, r, radius, 0xFFFFFFFF, lightTint, 0, 0, 0.02f, 1, 1 - dk);
        }
        if (dk > 0.001f) scene.glass(c, r, radius, SMOKE, 0.4f, 0, 0, 0.02f, 1, dk);
    }

    private void drawerCard(Canvas c, RectF r) {
        drawerGlass(c, r, 30 * k, 0.72f, 0x1A000000, 10 * k);
    }

    private void drawDrawer(Canvas c) {
        if (drawerItems.isEmpty()) return;
        rowTitle.setColor(tone(L_DIM, 0xB3FFFFFF));
        cardTitle.setColor(tone(L_DIM, 0xB3FFFFFF));
        cardText.setColor(drawerInk(0.95f));
        cardDim.setColor(tone(L_DIM, 0xADFFFFFF));
        cardDimRight.setColor(tone(L_FAINT, 0x99FFFFFF));
        bigTemp.setColor(drawerInk(1));
        hourPaint.setColor(tone(L_DIM, 0xADFFFFFF));
        hourTemp.setColor(drawerInk(0.95f));
        hourRain.setColor(tone(RAIN_LIGHT, RAIN_DARK));

        float titleBase = ROW_TITLE * k;
        if (drawerInputs > 0) c.drawText(Ui.tr("Entradas", "Inputs"), drawerRects[0].left + 4 * k, titleBase, rowTitle);
        if (drawerInputs < drawerItems.size()) c.drawText(Ui.tr("Atalhos", "Shortcuts"), drawerRects[drawerInputs].left + 4 * k, titleBase, rowTitle);
        int top = -1;
        for (int i = 0; i < drawerItems.size(); i++) if (drawerFocus[i].value > 0.02f && (top < 0 || drawerFocus[i].value > drawerFocus[top].value)) top = i;
        for (int i = 0; i < drawerItems.size(); i++) if (i != top) drawDrawerTile(c, i);
        if (top >= 0) drawDrawerTile(c, top);
        if (detailedWeatherOn) {
            drawWeatherCard(c, weatherCard);
            if (zone == WIDGETS) {
                rim.setShader(null); rim.setColor(drawerInk(0.65f));
                c.drawRoundRect(weatherCard, 30 * k, 30 * k, rim);
            }
        }
    }

    /** Square drawer button: symbol and name; an input with no device is dimmed. Focused: white, with dark text. */
    private void drawDrawerTile(Canvas c, int i) {
        HomeApps.Item it = drawerItems.get(i);
        float f = Ui.clamp(drawerFocus[i].value), s = 1 + 0.07f * f;
        RectF base = drawerRects[i];
        float cx = base.centerX(), cy = base.centerY();
        boolean moving = sectionMove == Z_DRAWER && i == drawerIndex;
        if (moving) { c.save(); c.rotate(sectionWiggle, cx, cy); }
        cell.set(cx - base.width() * s / 2, cy - base.height() * s / 2, cx + base.width() * s / 2, cy + base.height() * s / 2);
        float r = 20 * k * s;
        if (f > 0.01f) {
            tmp.set(cell);
            tmp.offset(0, 12 * k * f);
            shadow.setColor(Ui.alpha(0xFF000000, 0.45f * f));
            shadows.draw(c, tmp, r, SHADOW_BLUR * k, shadow);
        }
        if (f < 0.99f) drawerGlass(c, cell, r, 0.7f, 0x14000000, 6 * k);
        if (f > 0.01f) {
            fill.setShader(null);
            fill.setColor(Ui.alpha(WHITE, f));
            c.drawRoundRect(cell, r, r, fill);
        }
        boolean off = it.kind == HomeApps.INPUT && !it.connected;
        int ink = blend(tone(off ? 0xFF8E8E93 : L_INK, off ? 0x6BFFFFFF : 0xF2FFFFFF), INK, f);
        Symbols.draw(getContext(), c, it.symbol, cx, cell.top + cell.height() * 0.4f, 34 * k * s, ink);
        drawerLabel.setColor(ink);
        drawerLabel.setTextSize(23 * k * s);
        String label = i < drawerLabels.length ? drawerLabels[i] : it.label;
        c.drawText(label, cx, cell.top + cell.height() * 0.8f, drawerLabel);
        drawerLabel.setTextSize(23 * k);
        if (moving) c.restore();
    }

    /** Weather for your location: now (large), the next 6 hours, and sunrise and sunset. */
    private void drawWeatherCard(Canvas c, RectF r) {
        if (Ui.LEGACY) weatherSnapshot.draw(c, weatherCacheBounds, weatherCacheKey, weatherPainter);
        else { drawerCard(c, r); drawWeather(c, r); }
    }

    private void drawWeather(Canvas c, RectF r) {
        refreshDrawerText();
        float x = r.left + 34 * k;
        c.drawText(weatherTitle, x, r.top + 54 * k, cardTitle);
        HomeData.Weather wx = textWeather;
        if (Double.isNaN(wx.temp)) {
            c.drawText(Ui.tr("Sem dados do clima agora.", "No weather data right now."), x, r.top + 120 * k, cardDim);
            return;
        }
        String symbol = Symbols.weather(wx.code, wx.isDay);
        float sw = Symbols.width(getContext(), symbol, 52 * k);
        Symbols.draw(getContext(), c, symbol, x + sw / 2, r.top + 132 * k, 52 * k, drawerInk(0.95f));
        c.drawText(nowTemp, x + sw + 18 * k - Ui.leftBearing(bigTemp, nowTemp), r.top + 170 * k, bigTemp);
        c.drawText(HomeData.condition(wx.code, wx.isDay), x, r.top + 220 * k, cardText);
        c.drawText(highLow, x, r.top + 258 * k, cardDim);
        float split = r.left + 392 * k;
        fill.setShader(null);
        fill.setColor(tone(0x1A000000, 0x24FFFFFF));
        c.drawRoundRect(split - k, r.top + 34 * k, split + k, r.bottom - 34 * k, k, k, fill);
        float hx = split + 30 * k, hw = (r.right - 30 * k - hx) / 6;
        for (int i = 0; i < wx.hours.size() && i < 6; i++) {
            float hc = hx + hw * (i + 0.5f);
            c.drawText(hourText[i], hc, r.top + 96 * k, hourPaint);
            Symbols.draw(getContext(), c, Symbols.weather(wx.hourCode.get(i), wx.hourDay.get(i)), hc, r.top + 142 * k, 30 * k, drawerInk(0.9f));
            c.drawText(hourTempText[i], hc, r.top + 196 * k, hourTemp);
            if (!hourRainText[i].isEmpty()) c.drawText(hourRainText[i], hc, r.top + 224 * k, hourRain);
        }
        float sx = hx, base = r.top + 258 * k;
        if (wx.sunrise != 0) sx = sunItem(c, "sunrise", sunriseText, sx, base) + 36 * k;
        if (wx.sunset != 0) sunItem(c, "sunset", sunsetText, sx, base);
        cardDimRight.setTextSize(15*k);
        c.drawText("Weather: Open-Meteo · CC BY 4.0",r.right-30*k,r.bottom-12*k,cardDimRight);
        cardDimRight.setTextSize(21*k);
    }

    private void refreshDrawerText() {
        HomeData.Weather wx = data.weather;
        boolean settings = Prefs.version != textPrefs || !Ui.language.equals(textLanguage);
        textPrefs = Prefs.version;
        textLanguage = Ui.language;
        if (wx != textWeather || settings) {
            textWeather = wx;
            weatherTitle = Prefs.city().isEmpty() ? Ui.tr("Clima", "Weather") : Prefs.city();
            nowTemp = HomeData.degrees(wx.temp);
            highLow = HomeData.highLow(wx, "   ");
            for (int i = 0; i < wx.hours.size() && i < 6; i++) {
                hourText[i] = HomeData.hour(wx.hours.get(i));
                hourTempText[i] = HomeData.degrees(wx.hourTemp.get(i));
                int rain = i < wx.hourRain.size() ? wx.hourRain.get(i) : -1;
                hourRainText[i] = rain >= FeatureRules.RAIN_SHOWN ? LocaleFormats.percent(rain / 100.0, Ui.locale()) : "";
            }
            sunriseText = Ui.tr("Nascer ", "Sunrise ") + Ui.time(wx.sunrise);
            sunsetText = Ui.tr("Pôr do sol ", "Sunset ") + Ui.time(wx.sunset);
        }
    }

    private float sunItem(Canvas c, String symbol, String label, float x, float base) {
        float size = 22 * k, sw = Symbols.width(getContext(), symbol, size);
        Symbols.draw(getContext(), c, symbol, x + sw / 2, base - Ui.halfCap(cardDim), size, tone(L_DIM, 0xB3FFFFFF));
        c.drawText(label, x + sw + 10 * k, base, cardDim);
        return x + sw + 10 * k + cardDim.measureText(label);
    }

    /**
     * Android 9 to 11: keep the complete weather card in one bitmap while shortcut focus or position changes.
     * Its key changes with the weather, preferences, language, minute and palette.
     */
    private static final class Snapshot {
        private Bitmap bitmap;
        private String key = "";
        void clear() { bitmap=null;key=""; }

        void draw(Canvas c, RectF r, String key, Consumer<Canvas> content) {
            int left = Math.round(r.left), top = Math.round(r.top);
            int w = Math.max(1, Math.round(r.right) - left), h = Math.max(1, Math.round(r.bottom) - top);
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                this.key = "";
            }
            if (!key.equals(this.key)) {
                bitmap.eraseColor(0);
                Canvas cc = new Canvas(bitmap);
                cc.translate(-left, -top);
                content.accept(cc);
                this.key = key;
            }
            c.drawBitmap(bitmap, left, top, null);
        }
    }

    private final Snapshot weatherSnapshot = new Snapshot();



    // MARK: - Focus

    /** Sets the springs' targets from the current focus (snap: no animation). */
    private void retarget(boolean snap) {
        // Only the group whose focus changed is redrawn (moving along the shelf doesn't redraw the hero or capsule).
        boolean tilesChanged = aim(tileFocus, !sheetOpen && zone == Z_SHELF ? shelfIndex : -1, snap);
        boolean statusChanged = aim(statusFocus, !sheetOpen && zone == Z_STATUS ? statusIndex : -1, snap);
        boolean drawerChanged = aim(drawerFocus, !sheetOpen && zone == Z_DRAWER ? drawerIndex : -1, snap);
        boolean heroChanged = heroFocus.target != (!sheetOpen && zone == Z_HERO ? 1 : 0);
        heroFocus.target = !sheetOpen && zone == Z_HERO ? 1 : 0;
        if (snap) heroFocus.snap(heroFocus.target);
        boolean topChanged = aim(topFocus, !sheetOpen && zone == Z_TOP ? topIndex : -1, snap);
        heroChanged |= aim(cardFocus, !sheetOpen && zone == Z_CARDS ? cardIndex : -1, snap) | topChanged;
        statusChanged |= topChanged;
        tilesDirty |= snap || tilesChanged;
        statusDirty |= snap || statusChanged;
        heroDirty |= snap || heroChanged;
        drawerDirty |= snap || drawerChanged;
        otherDirty(snap);
        spoken.focusMoved();
    }

    private void otherDirty(boolean snap) {
        aim(otherFocus, !sheetOpen && zone == Z_OTHER ? otherIndex : -1, snap);
        if (zone == Z_OTHER) {
            float top = otherTop() + (otherIndex / COLUMNS) * GRID_ROW;
            below.target = Math.max(0, top - 300);
        } else if (zone == WIDGETS) below.target = 220;
        else below.target = 0;
        if (snap) below.snap(below.target);
        gridDirty = drawerDirty = true;
    }

    /** Aims a group's springs (only `focused` at 1); returns true if any target changed. */
    private static boolean aim(Spring[] springs, int focused, boolean snap) {
        boolean changed = false;
        for (int i = 0; i < springs.length; i++) {
            float target = i == focused ? 1 : 0;
            changed |= springs[i].target != target;
            springs[i].target = target;
            if (snap) springs[i].snap(target);
        }
        return changed;
    }

    // MARK: - "All Apps" sheet

    private void setOthers(List<HomeApps.Item> list) {
        others = list;
        requestLayout();
        otherFocus = new Spring[list.size()];
        for (int i = 0; i < otherFocus.length; i++) otherFocus[i] = new Spring(0.3f, 0.82f, 0);
        otherIndex = Math.min(otherIndex, Math.max(0, list.size() - 1));
        cacheOtherLabels();
        otherDirty(true);
        gridFocus = new Spring[list.size()];
        for (int i = 0; i < gridFocus.length; i++) gridFocus[i] = new Spring(0.3f, 0.82f, 0);
        gridLabels = null;
        gridIndex = Math.min(gridIndex, Math.max(0, list.size() - 1));
        retargetGrid(true);
        gridDirty = true;
    }

    private void openSheet() {
        sheetOpen = true;
        gridIndex = 0;
        scroll.snap(0);
        retargetGrid(true);
        retarget(false);
        sheet.target = 1;
        gridDirty = true;
    }

    private void closeSheet() {
        sheetOpen = false;
        sheet.target = 0;
        retarget(false);
    }

    private void retargetGrid(boolean snap) {
        for (int i = 0; i < gridFocus.length; i++) {
            gridFocus[i].target = sheetOpen && i == gridIndex ? 1 : 0;
            if (snap) gridFocus[i].snap(gridFocus[i].target);
        }
        int rows = Math.max(1, (others.size() + COLUMNS - 1) / COLUMNS);
        int visible = Math.min(rows, VISIBLE_ROWS);
        int r = gridIndex / COLUMNS;
        float target = scroll.target;
        if (r < target) target = r;
        else if (r > target + visible - 1) target = r - visible + 1;
        scroll.target = Math.max(0, Math.min(rows - visible, target));
        if (snap) scroll.snap(scroll.target);
        spoken.focusMoved();
    }

    private void drawSheet(Canvas c) {
        c.drawColor(0xA8000000);
        float shift = scroll.value * GRID_ROW * k;
        c.drawText(Ui.tr("Todos os Apps", "All Apps"), w / 2f, 124 * k - shift, sheetTitle);
        int n = others.size();
        gridLabels = otherLabels;
        float gridWidth = COLUMNS * GRID_W + (COLUMNS - 1) * GRID_GAP;
        float left = (1920 - gridWidth) / 2 * k;
        float gridTop = GRID_TOP * k;
        for (int i = 0; i < n; i++) {
            int r = i / COLUMNS, col = i % COLUMNS;
            float y = gridTop + r * GRID_ROW * k - shift;
            if (y > h || y + GRID_ROW * k < 0) continue;
            float x = left + col * (GRID_W + GRID_GAP) * k;
            gridCell.set(x, y, x + GRID_W * k, y + GRID_H * k);
            float f = gridFocus[i].value;
            boolean moving = sectionMove == Z_OTHER && i == gridIndex;
            if (moving) { c.save(); c.rotate(sectionWiggle, gridCell.centerX(), gridCell.centerY()); }
            drawCard(c, others.get(i).tile, gridCell, GRID_RADIUS * k, f, 0.08f);
            if (sheetOpen && i == gridIndex) focusedCell.set(cell);
            gridLabel.setColor(blend(0x99FFFFFF, 0xFFFFFFFF, Ui.clamp(f)));
            c.drawText(gridLabels[i], gridCell.centerX(), gridCell.bottom + (40 + 6 * f) * k, gridLabel);
            if (moving) c.restore();
        }
    }

    /** 16:9 sheet card. Focused (the shelf's subtle focus): it grows, with a shadow, a soft sheen and a thin border. */
    private void drawCard(Canvas c, Bitmap image, RectF base, float radius, float f, float grow) {
        float s = 1 + grow * f;
        float cx = base.centerX(), cy = base.centerY();
        cell.set(cx - base.width() * s / 2, cy - base.height() * s / 2, cx + base.width() * s / 2, cy + base.height() * s / 2);
        float r = radius * s;
        float fc = Ui.clamp(f);
        if (f > 0.01f) {
            tmp.set(cell);
            tmp.offset(0, 16 * k * f);
            shadow.setColor(Ui.alpha(0xFF000000, 0.55f * fc));
            shadows.draw(c, tmp, r, SHADOW_BLUR * k, shadow);
        }
        if (image != null) roundImage(c, image, cell, r);
        if (f > 0.01f) {
            float bh = base.height();
            if (cardSheen == null || cardSheenH != bh) {
                cardSheen = new LinearGradient(0, -bh / 2, 0, 0, 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
                cardSheenH = bh;
            }
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            tmp.set(-base.width() / 2, -bh / 2, base.width() / 2, bh / 2);
            sheen.setShader(cardSheen);
            sheen.setAlpha(Math.round(255 * fc));
            c.drawRoundRect(tmp, radius, radius, sheen);
            sheen.setAlpha(255);
            c.restore();
        }
        if (fc < 0.99f) {
            veil.setColor(Ui.alpha(0xFF000000, 0.08f * (1 - fc)));
            c.drawRoundRect(cell, r, r, veil);
        }
        lightFocusRing(c,cell,r,fc);
        border.setColor(0x14FFFFFF);
        c.drawRoundRect(cell, r, r, border);
        if (f > 0.01f) {
            rim.setShader(null);
            rim.setColor(Ui.alpha(0xFFFFFFFF, 0.4f * fc));
            rim.setStrokeWidth(1.5f * k);
            c.drawRoundRect(cell, r, r, rim);
            rim.setStrokeWidth(2.5f * k);
        }
    }

    // MARK: - Toasts

    void showToast(String text) {
        toasts.add(text);
        announceForAccessibility(text);
        wake();
    }

    /** Advances the toast queue; returns true while a toast fades in or out (animation). */
    private boolean tickToast(long up) {
        if (toast != null && up - toastAt > TOAST_MS) {
            toast = null;
            toastLayer.invalidate();
        }
        if (toast == null && toastPaint != null) {
            String next = toasts.poll();
            if (next != null) {
                toast = layout(next);
                toastAt = up;
            }
        }
        if (toast == null) return false;
        long age = up - toastAt;
        boolean fading = age < 350 || age > TOAST_MS - 450;
        if (fading) toastLayer.invalidate();
        return fading;
    }

    private void drawToast(Canvas c) {
        if (toast == null) return;
        float age = (SystemClock.uptimeMillis() - toastAt) / 1000f;
        float a = Math.min(Ui.clamp(age / 0.3f), Ui.clamp((TOAST_MS / 1000f - age) / 0.4f));
        if (a <= 0) return;
        float padX = 28 * k, padY = 18 * k, icon = 26 * k, iconW = Symbols.width(getContext(), "bell", icon);
        float tw = toast.getWidth() + iconW + 16 * k, th = toast.getHeight();
        float toastTop = 64 * k - (1 - a) * 16 * k;
        tmp.set((w - tw) / 2f - padX, toastTop, (w + tw) / 2f + padX, toastTop + th + 2 * padY);
        int save = c.saveLayerAlpha(tmp.left - 4, tmp.top - 4, tmp.right + 4, tmp.bottom + 4, Math.round(255 * a));
        scene.glass(c, tmp, Math.min(tmp.height() / 2, 32 * k), SMOKE, 0.55f, 0, 0, 0.02f, 1);
        Symbols.draw(getContext(), c, "bell", tmp.left + padX + iconW / 2, tmp.centerY(), icon, 0xEBFFFFFF);
        c.translate(tmp.left + padX + iconW + 16 * k, tmp.top + padY);
        toast.draw(c);
        c.restoreToCount(save);
    }

    private StaticLayout layout(String text) {
        int max = Math.round(720 * k);
        int width = (int) Math.min(max, Math.ceil(Layout.getDesiredWidth(text, toastPaint)));
        return StaticLayout.Builder.obtain(text, 0, text.length(), toastPaint, Math.max(1, width))
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private volatile long noticeStamp = Long.MIN_VALUE;   // the state file last read for notices (LOADER thread)

    /**
     * New notices from the main process (mode switches, the check after the TV boots): those from the last 24 h.
     * The 15 s tick reads the shared state file only after the main process has replaced it.
     */
    private void checkNotices() {
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            long stamp = HomeMode.stateStamp(c);
            if (stamp == noticeStamp) return;
            noticeStamp = stamp;
            JSONArray list = HomeMode.state(c).optJSONArray("notices");
            if (list == null) return;
            long seen = ui.getLong("seenNotice", 0), newest = seen;
            long cutoff = System.currentTimeMillis() - 24 * 3600_000L;
            final List<String> fresh = new ArrayList<>();
            for (int i = list.length() - 1; i >= 0; i--) {   // the list comes newest first
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                long at = o.optLong("at");
                if (at <= seen || at < cutoff) continue;
                fresh.add(o.optString("text"));
                newest = Math.max(newest, at);
            }
            if (newest > seen) ui.edit().putLong("seenNotice", newest).apply();
            if (!fresh.isEmpty()) handler.post(() -> {if(running&&!destroyed)for(String t:fresh)showToast(t);});
        });
    }

    // MARK: - Lists (in the background, so returning to the home screen isn't delayed)

    private void reload() {
        if(destroyed)return;
        final int token=catalogLoads.begin(Prefs.version,Prefs.catalogVersion);
        if(token<0)return;
        final Context c=getContext().getApplicationContext();
        LOADER.execute(()->{
            try {
                final List<HomeApps.Item> s=HomeApps.shelf(c,true), st=statusList(c),d=drawerList(c),o=HomeApps.others(c);
                handler.post(()->{
                    if(destroyed)return;
                    if(!catalogLoads.complete(token,Prefs.version,Prefs.catalogVersion)) {if(running)reload();return;}
                    if(reordering||sectionMove!=0) {catalogLoads.invalidate();return;}
                    setShelf(s);setStatusItems(st);setDrawerItems(d);if(!sheetOpen)setOthers(o);
                });
            }catch(RuntimeException error){handler.post(()->{catalogLoads.failed();AppLog.w("Home catalogue refresh failed");});}
        });
    }

    /** Release our hidden art on real memory pressure, never on the ordinary trip into an app. */
    void trimHiddenMemory() {
        if(running || destroyed)return;
        catalogLoads.invalidate();
        for(List<HomeApps.Item> list:java.util.Arrays.asList(shelf,others,drawerItems,statusItems))
            for(HomeApps.Item item:list){item.tile=null;item.logo=null;item.logoDark=null;}
        crispLogos.clear();imageShaders.clear();weatherSnapshot.clear();shadows.trim();
        cards=new ArrayList<>();renderedTab=-1;cardsArtVersion=-1;filteredSnapshot=null;
        filteredRecent=filteredRecommendations=java.util.Collections.emptyList();
        preparedRecent=preparedRecommendations=null;preparedTab=-1;
        bgFrom=bgTo=null;hero=heroFrom=null;bgFadeAt=heroFadeAt=0;
        scene.trimImages();HomeApps.trimCaches();recs.trimHidden();
        continuing.setVisible(java.util.Collections.emptyList());continuing.trim();
    }

    /** A low-memory signal must not trigger a decode/evict loop on the next frame. */
    void trimPosterMemory(){
        continuing.suspendArt();continuing.trim();
        bgFrom=bgTo=null;bgFadeAt=0;scene.trimImages();imageShaders.clear();cardsArtVersion=-1;heroDirty=true;
        if(running){invalidateAll();wake();}
    }

    void dumpTv(java.io.PrintWriter out,String prefix){
        out.println(prefix+"DeviceProfile: mode="+Prefs.deviceMode()+" inputs="+drawerInputs+" callback="+inputsRegistered+" pending="+inputLoading+" remote="+Prefs.tvRemote());
    }

    // MARK: - Keys: only the arrows, OK and Back (system keys pass through to the system)

    boolean onKey(KeyEvent e) {
        wake();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if(tvRemoteKey(e))return true;
        if (sectionMove != 0) return sectionMoveKey(e, down);
        if (reordering) return reorderKey(e, down);
        int code = e.getKeyCode();
        if (down && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                && code != KeyEvent.KEYCODE_NUMPAD_ENTER) {
            holdArmed = false;      // another key while OK is held: no move mode
            handler.removeCallbacks(holdStart);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Opens on release, and only if the press happened here: the release of an OK pressed inside another
                // app opens nothing.
                // Held on a shelf app (~0.5 s): move mode. A timer, since not every remote repeats a held key, or the
                // long-press repeat, whichever comes first; the release after a hold opens nothing.
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        okDown = true;
                        okHeld = false;
                        handler.removeCallbacks(holdStart);
                        holdArmed = canShowActions();
                        if (holdArmed) handler.postDelayed(holdStart, HOLD_MS);
                    } else if (okDown && holdArmed && e.isLongPress()) {
                        showActions();
                    }
                } else {
                    holdArmed = false;
                    handler.removeCallbacks(holdStart);
                    if (okDown && !okHeld && !e.isCanceled() && spoken.keyClick()) activate();
                    okDown = okHeld = false;
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) {
                    if (e.getRepeatCount() == 0) backDown = true;
                } else {
                    if (backDown && !e.isCanceled()) back();
                    backDown = false;
                }
                return true;
            default:
                return false;
        }
    }

    private void back() {
        if (sheetOpen) {
            closeSheet();
        } else if (zone == Z_OTHER) {
            zone = detailedWeatherOn ? WIDGETS : Z_DRAWER; retarget(false);
        } else if (zone == Z_DRAWER || zone == WIDGETS) {
            closeDrawer();
            retarget(false);
        } else if (zone != Z_SHELF) {
            zone = Z_SHELF;
            retarget(false);
        } else {
            return;
        }
        click.move();
    }

    private void move(int dx, int dy) {
        if (sheetOpen) {
            moveGrid(dx, dy);
            return;
        }
        if (zone == Z_OTHER) { moveOther(dx, dy); return; }
        if (moveLight(dx, dy)) return;
        switch (zone) {
            case Z_SHELF:
                if (dx != 0) {
                    int next = Math.max(0, Math.min(shelf.size() - 1, shelfIndex + dx));
                    if (next == shelfIndex) return;
                    shelfIndex = next;
                } else if (dy < 0) {
                    if (hero != null && hero.button) {
                        zone = Z_HERO;
                    } else if (!statusItems.isEmpty()) {
                        zone = Z_STATUS;
                        statusIndex = shelf.isEmpty() ? 0 : nearest(statusRects, tileRects[shelfIndex].centerX());
                    } else {
                        return;
                    }
                } else {
                    if (drawerItems.isEmpty()) return;
                    openDrawer();
                }
                break;
            case Z_HERO:
                if (dx != 0) {
                    if (!browse(dx)) return;
                } else if (dy < 0) {
                    if (statusItems.isEmpty()) return;
                    zone = Z_STATUS;
                    statusIndex = 0;
                } else {
                    zone = Z_SHELF;
                }
                break;
            case Z_STATUS:
                if (dx != 0) {
                    int next = Math.max(0, Math.min(statusItems.size() - 1, statusIndex + dx));
                    if (next == statusIndex) return;
                    statusIndex = next;
                } else if (dy > 0) {
                    if (hero != null && hero.button) {
                        zone = Z_HERO;
                    } else {
                        zone = Z_SHELF;
                        if (!shelf.isEmpty()) shelfIndex = nearest(tileRects, statusRects[statusIndex].centerX());
                    }
                } else {
                    return;
                }
                break;
            case WIDGETS:
                if (dy < 0) zone = Z_DRAWER;
                else if (dy > 0 && !others.isEmpty()) zone = Z_OTHER;
                else return;
                break;
            default:
                if (dx != 0) {
                    int next = Math.max(0, Math.min(drawerItems.size() - 1, drawerIndex + dx));
                    if (next == drawerIndex) return;
                    drawerIndex = next;
                } else if (dy < 0) {
                    closeDrawer();
                } else if (detailedWeatherOn) {
                    // Only with TalkBack: the widgets are there to be read, and this is how it gets to them.
                    zone = WIDGETS;
                    widgetIndex = 0;
                } else if (!others.isEmpty()) {
                    zone = Z_OTHER;
                } else {
                    return;
                }
                break;
        }
        retarget(false);
        click.move();
    }

    private static int nearest(RectF[] rects, float x) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < rects.length; i++) {
            float d = Math.abs(rects[i].centerX() - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private void moveGrid(int dx, int dy) {
        int n = others.size();
        if (n == 0) return;
        int r = gridIndex / COLUMNS, col = gridIndex % COLUMNS;
        int rows = (n + COLUMNS - 1) / COLUMNS;
        int next = gridIndex;
        if (dx != 0) {
            int candidate = gridIndex + dx;
            if (candidate >= 0 && candidate < n && candidate / COLUMNS == r) next = candidate;
        } else {
            int targetRow = r + dy;
            if (targetRow >= 0 && targetRow < rows) next = Math.min(n - 1, targetRow * COLUMNS + col);
        }
        if (next == gridIndex) return;
        gridIndex = next;
        retargetGrid(false);
        click.move();
        gridDirty = true;
    }

    private void activate() {
        wake();
        if (reordering) {   // TalkBack's click while moving an app: drop it
            dropReorder();
            return;
        }
        HomeApps.Item it;
        RectF r;
        if (!sheetOpen && zone == WIDGETS) return;
        if (sheetOpen) {
            if (others.isEmpty()) return;
            it = others.get(gridIndex);
            r = focusedCell;
        } else if (zone == Z_OTHER) {
            if (others.isEmpty()) return;
            it = others.get(otherIndex);
            otherRect(otherIndex, focusedCell);
            r = focusedCell;
        } else if (zone == Z_HERO) {
            Recs.Pick p = currentPick();
            if (p == null || p.pkg == null) return;
            it = HomeApps.app(p.pkg, Recs.serviceName(p), p.link);
            r = heroButton;
        } else if (zone == Z_STATUS) {
            if (statusItems.isEmpty()) return;
            it = statusItems.get(statusIndex);
            r = statusRects[statusIndex];
        } else if (zone == Z_DRAWER) {
            if (drawerItems.isEmpty()) return;
            it = drawerItems.get(drawerIndex);
            r = drawerRects[drawerIndex];
        } else if (zone == Z_TOP) {
            if (topIndex < TOP_ACTION) {
                selectTab(topIndex);
                return;
            }
            it = lightButtons.get(topIndex - TOP_ACTION);
            r = topRects[topIndex];
        } else if (zone == Z_CARDS) {
            ContentCard card=cardIndex<cards.size()?cards.get(cardIndex):null;
            if(card==null)return;
            if(card.program!=null) {
                click.open();
                if(!ContinueWatching.open(activity,card.program))showToast(Ui.tr("Não deu para abrir ","Couldn't open ")+card.app+".");
                return;
            }
            Recs.Pick p=card.pick;
            if(p==null || p.pkg==null)return;
            it=HomeApps.app(p.pkg,Recs.serviceName(p),p.link);
            r=cardRects[cardIndex];
        } else {
            if (shelf.isEmpty()) return;
            it = shelf.get(shelfIndex);
            r = tileRects[shelfIndex];
        }
        click.open();
        if (it.kind == HomeApps.ALL_APPS) {
            openSheet();
            return;
        }
        if (it.kind == HomeApps.INPUTS) {
            openTvInputs();
            return;
        }
        boolean opened = HomeApps.open(activity, it, this, Math.round(r.left), Math.round(r.top),
                Math.round(r.width()), Math.round(r.height()));
        if (!opened) showToast(Ui.tr("Não deu para abrir ", "Couldn't open ") + it.label + ".");
    }

    // MARK: - Long-press actions (shelf movement below remains its own operation)

    private void openTvInputs(){
        if(drawerInputs==0){activity.startActivity(new Intent(activity,TvInputsActivity.class));return;}
        sheetOpen=false;sheet.target=0;openDrawer();drawerIndex=0;retarget(false);
    }
    private boolean tvRemoteKey(KeyEvent e){
        int source=e.getSource();boolean controller=(source&android.view.InputDevice.SOURCE_GAMEPAD)==android.view.InputDevice.SOURCE_GAMEPAD
            ||(source&android.view.InputDevice.SOURCE_JOYSTICK)==android.view.InputDevice.SOURCE_JOYSTICK;
        int action=TvRemoteRules.action(Prefs.television(),Prefs.tvRemote(),controller,e.getKeyCode());
        if(action==TvRemoteRules.NONE){if(e.getAction()==KeyEvent.ACTION_DOWN)tvKeyArmed=0;return false;}
        if(reordering||sectionMove!=0)return false;
        if(e.getAction()==KeyEvent.ACTION_DOWN){if(e.getRepeatCount()==0)tvKeyArmed=action;return true;}
        if(e.getAction()!=KeyEvent.ACTION_UP)return true;
        boolean choose=tvKeyArmed==action&&!e.isCanceled();tvKeyArmed=0;if(!choose)return true;
        handler.removeCallbacks(holdStart);holdArmed=okDown=okHeld=false;
        if(action==TvRemoteRules.INPUTS){openTvInputs();return true;}
        if(action==TvRemoteRules.QUICK){activity.startActivity(new Intent(activity,QuickSettingsActivity.class));return true;}
        HomeApps.Item item=null;
        if(action==TvRemoteRules.LIVE)item=HomeApps.action(HomeApps.LIVE_TV,"live",Ui.tr("Live TV"),"tv");
        else for(HomeApps.Item port:drawerItems)if(port.kind==HomeApps.INPUT&&Integer.toString(action-TvRemoteRules.HDMI1+1).equals(port.number)){item=port;break;}
        if(item==null||!HomeApps.open(activity,item,this,0,0,getWidth(),getHeight()))showToast(Ui.tr("The TV could not open this source."));
        return true;
    }

    private HomeApps.Item actionItem() {
        if (sheetOpen) return gridIndex < others.size() ? others.get(gridIndex) : null;
        if (zone == Z_OTHER) return otherIndex < others.size() ? others.get(otherIndex) : null;
        if (zone == Z_SHELF) return shelfIndex < shelf.size() ? shelf.get(shelfIndex) : null;
        if (zone == Z_DRAWER) return drawerIndex < drawerItems.size() ? drawerItems.get(drawerIndex) : null;
        return null;
    }
    private boolean canShowActions() {
        if (!sheetOpen && zone == Z_CARDS && cardIndex < cards.size())
            return cards.get(cardIndex).program != null;
        HomeApps.Item item = actionItem();
        return item != null && item.kind != HomeApps.ALL_APPS && (item.kind == HomeApps.APP || zone == Z_DRAWER && item.kind != HomeApps.EDIT_SHORTCUTS);
    }
    private void showActions() {
        handler.removeCallbacks(holdStart);
        holdArmed = false;
        if (!canShowActions() || actions != null && actions.isShowing()) return;
        okDown = false;
        okHeld = true;
        List<QuickActionDialog.Option> options = new ArrayList<>();
        if (!sheetOpen && zone == Z_CARDS) {
            ContinueWatching.Program program = cards.get(cardIndex).program;
            options.add(new QuickActionDialog.Option(program.recommended?Ui.tr("Abrir","Open"):Ui.tr("Continue assistindo", "Continue watching"), () -> {
                if (!ContinueWatching.open(activity, program)) showToast(Ui.tr("Não deu para abrir ", "Couldn't open ") + program.app + ".");
            }));
            options.add(new QuickActionDialog.Option(Ui.tr("Ocultar este conteúdo", "Hide this content"), () -> {
                if (!Prefs.hideContent(program.hiddenKey)) {
                    showToast(Ui.tr("Restaure os conteúdos ocultos antes de ocultar mais.", "Restore hidden content before hiding more."));
                    return;
                }
                wake(); updateCards(); retarget(false);
                continuing.refresh(true);
                showToast(Ui.tr("Content hidden. Restore it in Content settings."));
            }));
            showActionMenu(program.title, options);
            return;
        }
        HomeApps.Item item = actionItem();
        if (zone == Z_OTHER) otherRect(otherIndex, focusedCell);
        final RectF origin = new RectF(sheetOpen || zone == Z_OTHER ? focusedCell
                : zone == Z_DRAWER ? drawerRects[drawerIndex] : tileRects[shelfIndex]);
        if (!sheetOpen && zone == Z_DRAWER) origin.offset(0, -below.value * k);
        options.add(new QuickActionDialog.Option(Ui.tr("Abrir", "Open"), () -> {
            click.open();
            if (item.kind == HomeApps.ALL_APPS) { openSheet(); return; }
            if (!HomeApps.open(activity, item, this, Math.round(origin.left), Math.round(origin.top),
                    Math.round(origin.width()), Math.round(origin.height())))
                showToast(Ui.tr("Não deu para abrir ", "Couldn't open ") + item.label + ".");
        }));
        if (item.kind == HomeApps.APP && item.pkg != null) {
            options.add(new QuickActionDialog.Option(Ui.tr("Informações do app", "App info"), () -> {
                try { activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + item.pkg))); }
                catch (ActivityNotFoundException | SecurityException e) { showToast(Ui.tr("Não deu para abrir ", "Couldn't open ") + item.label + "."); }
            }));
        }
        String id = QuickShortcuts.id(item);
        if (id != null) {
            boolean selected = Prefs.quickShortcuts().contains(id);
            options.add(new QuickActionDialog.Option(selected ? Ui.tr("Remover dos atalhos", "Remove from shortcuts")
                    : Ui.tr("Adicionar aos atalhos", "Add to shortcuts"), () -> {
                List<String> ids = Prefs.quickShortcuts();
                if (ShortcutOrder.toggle(ids, id)) { Prefs.setQuickShortcuts(ids); reload(); }
                else showToast(Ui.tr("Limite de 6 atalhos. Remova um antes de adicionar outro.",
                        "Limit of 6 shortcuts. Remove one before adding another."));
            }));
        }
        if (canReorder()) options.add(new QuickActionDialog.Option(Ui.tr("Mover na prateleira", "Move on shelf"), () -> {
            for (int i = 0; i < shelf.size(); i++) if (item.pkg.equals(shelf.get(i).pkg)) {
                shelfIndex = i; startReorder(); break;
            }
        }));
        if (zone == Z_DRAWER && QuickShortcuts.id(item) != null || (zone == Z_OTHER || sheetOpen) && others.size() > 1) {
            options.add(new QuickActionDialog.Option(Ui.tr("Mover na prateleira", "Move on shelf"), this::startSectionMove));
        }
        if (item.kind == HomeApps.APP && item.pkg != null) addAppOptions(item, options);
        showActionMenu(item.label, options);
    }

    /**
     * Hide (Other apps only: the shelf has its own editor), Uninstall (apps the person installed, with Android's
     * confirmation) and Force stop (custom home mode). With the app lock on, the PIN is asked first.
     */
    private void addAppOptions(HomeApps.Item item, List<QuickActionDialog.Option> options) {
        final Context app = getContext().getApplicationContext();
        if (zone == Z_OTHER || sheetOpen) options.add(new QuickActionDialog.Option(Ui.tr("Hide app"), () -> AppLock.confirm(activity, () -> {
            Prefs.setAppHidden(item.pkg, true);
            catalogLoads.invalidate(); reload();
            showToast(Ui.tr("App hidden. Show it again in Settings → Home screen → Hidden apps."));
        })));
        if (AppActions.canUninstall(app, item.pkg)) options.add(new QuickActionDialog.Option(Ui.tr("Uninstall"), () -> AppLock.confirm(activity, () -> {
            if (!AppActions.uninstall(activity, item.pkg)) showToast(Ui.tr("Não deu para abrir ", "Couldn't open ") + item.label + ".");
        })));
        if (AppActions.canForceStop(app, item.pkg)) options.add(new QuickActionDialog.Option(Ui.tr("Force stop"), () -> LOADER.execute(() -> {
            boolean stopped = AppActions.forceStop(app, item.pkg);
            handler.post(() -> { if (!destroyed) showToast(stopped ? Ui.trf("{app} was stopped.", "app", item.label) : Ui.tr("Couldn't stop the app.")); });
        })));
    }

    private RectF actionAnchor() {
        RectF origin=new RectF();
        float grow=0;
        if(sheetOpen)origin.set(focusedCell);
        else if(zone==Z_OTHER){otherRect(otherIndex,origin);origin.offset(0,(1-drawer.value)*SHIFT*k);grow=.08f*Ui.clamp(otherFocus[otherIndex].value);}
        else if(zone==Z_SHELF){origin.set(tileRects[shelfIndex]);origin.offset(0,tiles.getTranslationY());grow=FOCUS_GROW*Ui.clamp(tileFocus[shelfIndex].value);}
        else if(zone==Z_DRAWER){origin.set(drawerRects[drawerIndex]);origin.offset(0,drawerLayer.getTranslationY());grow=.07f*Ui.clamp(drawerFocus[drawerIndex].value);}
        else if(zone==Z_CARDS){origin.set(cardRects[cardIndex]);origin.offset(0,heroLayer.getTranslationY());grow=.04f;}
        origin.inset(-origin.width()*grow/2,-origin.height()*grow/2);
        int[] location=new int[2];getLocationOnScreen(location);origin.offset(location[0],location[1]);
        return origin;
    }

    private void showActionMenu(String title, List<QuickActionDialog.Option> options) {
        QuickActionDialog menu = new QuickActionDialog(activity, title, options, actionAnchor());
        actions = menu;
        menu.setOnDismissListener(dialog -> {
            if (actions == menu) actions = null;
            okDown = okHeld = false;
        });
        menu.show();
    }

    // MARK: - Independent drawer and other-app movement

    private float otherTop() { return detailedWeatherOn ? OTHER_TOP : CARDS_TOP + 65; }

    private void cacheOtherLabels() {
        if (gridLabel == null) return;
        otherLabels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) otherLabels[i] = TextUtils.ellipsize(
                others.get(i).label, gridLabel, (GRID_W - 10) * k, TextUtils.TruncateAt.END).toString();
    }

    private void otherBaseRect(int index, RectF out) {
        float width = COLUMNS * GRID_W + (COLUMNS - 1) * GRID_GAP;
        float x = ((1920 - width) / 2 + index % COLUMNS * (GRID_W + GRID_GAP)) * k;
        float y = (otherTop() + index / COLUMNS * GRID_ROW) * k;
        out.set(x, y, x + GRID_W * k, y + GRID_H * k);
    }

    private void otherRect(int index, RectF out) { otherBaseRect(index, out); out.offset(0, -below.value * k); }

    private void drawOtherApps(Canvas c) {
        if (others.isEmpty() || otherLabels.length != others.size()) return;
        rowTitle.setColor(tone(L_DIM, 0xB3FFFFFF));
        if (otherFirstRow == 0) c.drawText(Ui.tr("Outros apps", "Other apps"), MARGIN * k, (otherTop() - 48) * k, rowTitle);
        int start = otherFirstRow * COLUMNS, end = Math.min(others.size(), Math.min(otherLastRow,otherFirstRow+5) * COLUMNS);
        for (int i = start; i < end; i++) if (i != otherIndex) drawOtherApp(c, i);
        if (otherIndex >= start && otherIndex < end) drawOtherApp(c, otherIndex);
    }

    private void drawOtherApp(Canvas c, int i) {
        otherBaseRect(i, gridCell);
        boolean moving = sectionMove == Z_OTHER && i == otherIndex;
        if (moving) { c.save(); c.rotate(sectionWiggle, gridCell.centerX(), gridCell.centerY()); }
        float f = otherFocus[i].value;
        drawCard(c, others.get(i).tile, gridCell, GRID_RADIUS * k, f, 0.08f);
        gridLabel.setColor(blend(tone(L_DIM, 0x99FFFFFF), tone(L_INK, WHITE), Ui.clamp(f)));
        c.drawText(otherLabels[i], gridCell.centerX(), gridCell.bottom + (40 + 6 * f) * k, gridLabel);
        if (moving) c.restore();
    }

    void browseOtherApps() {
        drawerOpen = true;
        drawer.target = 1;
        zone = Z_OTHER;
        otherIndex = 0;
        retarget(false);
    }

    private void moveOther(int dx, int dy) {
        if (others.isEmpty()) { zone = Z_DRAWER; retarget(false); return; }
        int next = otherIndex;
        if (dy < 0 && otherIndex < COLUMNS) {
            zone = detailedWeatherOn ? WIDGETS : Z_DRAWER;
        } else if (dx != 0) {
            int candidate = otherIndex + dx;
            if (candidate >= 0 && candidate < others.size() && candidate / COLUMNS == otherIndex / COLUMNS) next = candidate;
        } else if (dy != 0) {
            int row = otherIndex / COLUMNS + dy;
            if (row >= 0 && row * COLUMNS < others.size()) next = Math.min(others.size() - 1, row * COLUMNS + otherIndex % COLUMNS);
        }
        otherIndex = next;
        retarget(false);
        click.move();
    }

    private void startSectionMove() {
        if (reordering || sectionMove != 0) return;
        HomeApps.Item it = actionItem();
        if (it == null || it.kind == HomeApps.ALL_APPS) return;
        sectionMove = zone == Z_DRAWER ? Z_DRAWER : Z_OTHER;
        sectionMoveAt = SystemClock.uptimeMillis();
        sectionOrigin = new ArrayList<>(sectionMove == Z_DRAWER ? drawerItems : others);
        okDown = holdArmed = false;
        handler.removeCallbacks(holdStart);
        showToast(Ui.tr("Setas para mover · OK confirma · Voltar cancela", "Arrows to move · OK confirms · Back cancels"));
        wake();
    }

    private boolean sectionMoveKey(KeyEvent event, boolean down) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (down && event.getRepeatCount() == 0) { finishSectionMove(); okDown = false; }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (down && event.getRepeatCount() == 0) backDown = true;
            else if (!down) { if (backDown && !event.isCanceled()) cancelSectionMove(); backDown = false; }
            return true;
        }
        int delta = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : code == KeyEvent.KEYCODE_DPAD_UP ? -COLUMNS : code == KeyEvent.KEYCODE_DPAD_DOWN ? COLUMNS : 0;
        if (delta == 0) return false;
        if (!down) return true;
        List<HomeApps.Item> list = sectionMove == Z_DRAWER ? drawerItems : others;
        int at = sectionMove == Z_DRAWER ? drawerIndex : sheetOpen ? gridIndex : otherIndex;
        if (sectionMove == Z_DRAWER && Math.abs(delta) != 1) return true;
        int next = at + delta;
        if (next < 0 || next >= list.size() || sectionMove == Z_DRAWER &&
                (list.get(next).kind == HomeApps.ALL_APPS || QuickShortcuts.id(list.get(next)) == null)) return true;
        // Remove/insert preserves the intervening apps' relative order even across grid rows.
        if (!SectionOrder.move(list, at, next, sectionMove == Z_DRAWER ? drawerInputs + 1 : 0)) return true;
        Spring[] focus = sectionMove == Z_DRAWER ? drawerFocus : sheetOpen ? gridFocus : otherFocus;
        SectionOrder.move(java.util.Arrays.asList(focus), at, next, 0);
        if (sectionMove == Z_DRAWER) {
            drawerIndex = next;
            layoutDrawer();
        } else {
            if (sheetOpen) gridIndex = next; else otherIndex = next;
            cacheOtherLabels();
            retargetGrid(false);
        }
        retarget(false);
        click.move();
        return true;
    }

    private void finishSectionMove() {
        if (sectionMove == Z_DRAWER) {
            List<String> ids = new ArrayList<>();
            for (HomeApps.Item item : drawerItems) if (ShortcutOrder.valid(item.key)) ids.add(item.key);
            Prefs.setQuickShortcuts(ids);
        } else if (sectionMove == Z_OTHER) {
            List<String> keys = new ArrayList<>();
            for (HomeApps.Item item : others) keys.add(item.key);
            Prefs.setOtherAppsOrder(keys);
        }
        sectionMove = 0;
        sectionWiggle = 0;
        sectionOrigin = null;
        retarget(false);
        click.open();
        if(inputDirty)inputsChanged();
    }

    private void cancelSectionMove() {
        if (sectionOrigin == null) return;
        if (sectionMove == Z_DRAWER) {
            String key = drawerItems.get(drawerIndex).key;
            drawerItems = sectionOrigin;
            for (int i = 0; i < drawerItems.size(); i++) if (key.equals(drawerItems.get(i).key)) drawerIndex = i;
            layoutDrawer();
        } else {
            HomeApps.Item item = others.get(sheetOpen ? gridIndex : otherIndex);
            others = sectionOrigin;
            if (sheetOpen) gridIndex = others.indexOf(item); else otherIndex = others.indexOf(item);
            cacheOtherLabels();
            retargetGrid(true);
        }
        sectionMove = 0;
        sectionWiggle = 0;
        sectionOrigin = null;
        retarget(false);
        if(inputDirty)inputsChanged();
    }

    // MARK: - Moving apps on the shelf

    private boolean canReorder() {
        if (sheetOpen || zone != Z_SHELF || shelfIndex >= shelf.size() || shelf.size() < 2) return false;
        HomeApps.Item it = shelf.get(shelfIndex);
        return it.kind == HomeApps.APP && it.pkg != null;
    }

    private void startReorder() {
        handler.removeCallbacks(holdStart);
        holdArmed = false;
        if (reordering || !canReorder()) return;
        wake();
        reordering = reorderAnim = okHeld = true;
        reorderOrigin = new ArrayList<>(shelf);
        liftIndex = shelfIndex;
        reorderAt = SystemClock.uptimeMillis();
        moveLift.target = 1;
        click.open();
        showToast(Ui.tr("← → para mover · OK para soltar · Voltar para cancelar",
                "← → to move · OK to drop · Back to cancel"));
        retarget(false);    // TalkBack reads "Moving <app>, position i of n"
    }

    /** Keys while moving an app: nothing else on the home screen gets them (system keys still go to the system). */
    private boolean reorderKey(KeyEvent e, boolean down) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) shiftReorder(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // The press that lifted the app is still held: its repeats and release do nothing; a new press drops it.
                if (down && e.getRepeatCount() == 0) {
                    okDown = okHeld = false;
                    dropReorder();
                } else if (!down) {
                    okDown = okHeld = false;
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) {
                    if (e.getRepeatCount() == 0) backDown = true;
                } else {
                    if (backDown && !e.isCanceled()) {
                        cancelReorder(false);
                        click.move();
                    }
                    backDown = false;
                }
                return true;
            default:
                return false;
        }
    }

    /** Swaps the lifted app with its neighbor; both slide from where they are to their new slots. */
    private void shiftReorder(int dx) {
        int i = shelfIndex, j = i + dx;
        if (j < 0 || j >= shelf.size()) return;
        HomeApps.Item it = shelf.get(i);
        shelf.set(i, shelf.get(j));
        shelf.set(j, it);
        Spring f = tileFocus[i];
        tileFocus[i] = tileFocus[j];
        tileFocus[j] = f;
        Spring s = tileSlide[i];
        tileSlide[i] = tileSlide[j];
        tileSlide[j] = s;
        float d = tileRects[i].left - tileRects[j].left;
        tileSlide[j].value += d;
        tileSlide[i].value -= d;
        if (tileShaders.length == shelf.size()) {
            LinearGradient g = tileShaders[i];
            tileShaders[i] = tileShaders[j];
            tileShaders[j] = g;
        }
        shelfIndex = liftIndex = j;
        reorderAnim = true;
        wake();
        retarget(false);
        click.move();
    }

    /** OK: drops the app where it is and saves the shelf's new order. */
    private void dropReorder() {
        if (!reordering) return;
        reordering = false;
        moveLift.target = 0;
        boolean changed = false;
        for (int i = 0; i < shelf.size() && !changed; i++) changed = shelf.get(i) != reorderOrigin.get(i);
        reorderOrigin = null;
        click.open();
        retarget(false);
        if(inputDirty)inputsChanged();
        if (!changed) return;
        final List<String> order = new ArrayList<>();
        for (HomeApps.Item it : shelf) order.add(it.pkg);
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            // The saved list may hold more than the shelf shows (apps not installed, past the limit): the shown ones
            // take their new order in the same slots, the others stay where they were.
            List<String> all = HomeApps.shelfPackages(c), out = new ArrayList<>(all);
            int next = 0;
            for (int s = 0; s < out.size() && next < order.size(); s++) {
                if (order.contains(out.get(s))) out.set(s, order.get(next++));
            }
            if (next < order.size()) {
                out = new ArrayList<>(order);
                for (String p : all) if (!order.contains(p)) out.add(p);
            }
            final List<String> save = out;
            handler.post(() -> Prefs.setShelf(save));
        });
    }

    /** Back, or leaving the home screen: the original order comes back (sliding, unless `snap`). */
    private void cancelReorder(boolean snap) {
        if (!reordering && !reorderAnim) return;
        if (reordering) {
            reordering = false;
            moveLift.target = 0;
            int n = shelf.size();
            if (reorderOrigin != null && reorderOrigin.size() == n) {
                HomeApps.Item lifted = shelf.get(shelfIndex);
                Spring[] focus = new Spring[n], slide = new Spring[n];
                LinearGradient[] shaders = tileShaders.length == n ? new LinearGradient[n] : tileShaders;
                for (int a = 0; a < n; a++) {
                    int b = shelf.indexOf(reorderOrigin.get(a));
                    focus[a] = tileFocus[b];
                    slide[a] = tileSlide[b];
                    slide[a].value += tileRects[b].left - tileRects[a].left;
                    if (shaders != tileShaders) shaders[a] = tileShaders[b];
                }
                shelf = reorderOrigin;
                tileFocus = focus;
                tileSlide = slide;
                tileShaders = shaders;
                shelfIndex = liftIndex = shelf.indexOf(lifted);
            }
            reorderOrigin = null;
            retarget(snap);
            if(inputDirty)inputsChanged();
        }
        if (snap) {
            moveLift.snap(0);
            for (Spring s : tileSlide) s.snap(0);
            wiggle = 0;
            liftIndex = -1;
            reorderAnim = false;
            tilesDirty = true;
        } else {
            wake();
        }
    }

    /** One frame of the move: the slides, the lift and the wiggle. Returns true while any of them moves. */
    private boolean stepReorder(float dt, long up) {
        boolean moving = step(tileSlide, dt) | moveLift.step(dt);
        if (reordering || moveLift.value > 0.001f) {
            wiggle = WIGGLE_DEG * (float) Math.sin((up - reorderAt) * (2 * Math.PI / WIGGLE_MS));
            moving = true;
        } else {
            wiggle = 0;
        }
        reorderAnim = moving;
        return moving;
    }

    // MARK: - TalkBack: every reachable item as a virtual node (Spoken). Id = group * 1000 + index + 1.

    /** Accessibility groups for app grid and weather. */
    private static final int GRID = 9, WIDGETS = 8;

    private static int a11yId(int group, int index) {
        return group * 1000 + index + 1;
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return spoken;
    }

    private void a11yItems(List<Integer> out) {
        if (sheetOpen) {
            for (int i = 0; i < others.size(); i++) out.add(a11yId(GRID, i));
            return;
        }
        for (int i = 0; i < TOP_COUNT; i++) if (visibleTop(i)) out.add(a11yId(Z_TOP, i));
        for (int i = 0; i < cardCount(); i++) out.add(a11yId(Z_CARDS, i));
        for (int i = 0; i < shelf.size(); i++) out.add(a11yId(Z_SHELF, i));
        for (int i = 0; i < drawerItems.size(); i++) out.add(a11yId(Z_DRAWER, i));
        if (drawerOpen) for (int i = 0; i < others.size(); i++) out.add(a11yId(Z_OTHER, i));
        if (drawerOpen && detailedWeatherOn) out.add(a11yId(WIDGETS, 0));
    }

    private String a11yText(int id) {
        int group = (id - 1) / 1000, i = (id - 1) % 1000;
        switch (group) {
            case Z_OTHER:
            case GRID:
                if (i >= others.size()) return "";
                return label(others, i) + ", " + (i + 1) + Ui.tr(" de ", " of ") + others.size();
            case Z_TOP:
                if (i >= TOP_ACTION) return label(lightButtons, i - TOP_ACTION);
                return tabs()[i];
            case Z_CARDS: return i < cards.size() ? cards.get(i).title+", "+cards.get(i).meta : "";
            case Z_SHELF:
                if (reordering && i == shelfIndex) {
                    return Ui.tr("Movendo ", "Moving ", "Moviendo ") + label(shelf, i)
                            + Ui.tr(", posição ", ", position ", ", posición ") + (i + 1) + Ui.tr(" de ", " of ") + shelf.size();
                }
                return label(shelf, i);
            case Z_DRAWER: return label(drawerItems, i);
            case WIDGETS: return weatherText();
            default: return "";
        }
    }

    /**
     * Where the item is now. Items off screen (the closed drawer, the sheet's hidden rows, the top while the drawer is
     * open) get a thin strip at that edge, so TalkBack's arrows can still reach them; going there brings them in.
     */
    private boolean a11yBounds(int id, Rect out) {
        int group = (id - 1) / 1000, i = (id - 1) % 1000;
        float dy;
        switch (group) {
            case Z_OTHER:
                if (!drawerOpen || i >= others.size()) return false;
                otherRect(i, a11yRect); dy = 0; break;
            case GRID: {
                if (!sheetOpen || i >= others.size()) return false;
                float gridWidth = COLUMNS * GRID_W + (COLUMNS - 1) * GRID_GAP;
                float x = ((1920 - gridWidth) / 2 + (i % COLUMNS) * (GRID_W + GRID_GAP)) * k;
                float y = (GRID_TOP + (i / COLUMNS - scroll.target) * GRID_ROW) * k;
                a11yRect.set(x, y, x + GRID_W * k, y + GRID_H * k);
                dy = 0;
                break;
            }
            case Z_TOP:
                if (i >= TOP_COUNT || !visibleTop(i)) return false;
                a11yRect.set(topRects[i]);
                dy = (i < TOP_ACTION ? heroLayer : status).getTranslationY();
                break;
            case Z_CARDS:
                if (i >= cardCount()) return false;
                a11yRect.set(cardRects[i]);
                dy = heroLayer.getTranslationY();
                break;
            case Z_SHELF:
                if (i >= tileRects.length) return false;
                a11yRect.set(tileRects[i]);
                dy = tiles.getTranslationY();
                break;
            case Z_DRAWER:
                if (i >= drawerRects.length) return false;
                a11yRect.set(drawerRects[i]);
                dy = drawerOpen ? -below.target * k : h;
                break;
            case WIDGETS:
                if (!drawerOpen || !detailedWeatherOn) return false;
                a11yRect.set(weatherCard);
                dy = -below.target * k;
                break;
            default:
                return false;
        }
        a11yRect.offset(0, dy);
        float edge = 24 * k;
        if (a11yRect.top >= h - edge) a11yRect.set(a11yRect.left, h - edge, a11yRect.right, h);
        else if (a11yRect.bottom <= edge) a11yRect.set(a11yRect.left, 0, a11yRect.right, edge);
        a11yRect.round(out);
        return out.width() > 0 && out.height() > 0;
    }

    private int a11yFocused() {
        if (sheetOpen) return others.isEmpty() ? -1 : a11yId(GRID, gridIndex);
        switch (zone) {
            case Z_TOP: return a11yId(Z_TOP, topIndex);
            case Z_CARDS: return cardIndex < cardCount() ? a11yId(Z_CARDS, cardIndex) : -1;
            case Z_SHELF: return shelf.isEmpty() ? -1 : a11yId(Z_SHELF, shelfIndex);
            case Z_DRAWER: return drawerItems.isEmpty() ? -1 : a11yId(Z_DRAWER, drawerIndex);
            case Z_OTHER: return others.isEmpty() ? -1 : a11yId(Z_OTHER, otherIndex);
            case WIDGETS: return a11yId(WIDGETS, widgetIndex);
            default: return -1;
        }
    }

    /** Visible weather accessibility card. */
    private List<Integer> widgets() {
        List<Integer> out = new ArrayList<>();
        if (!drawerOpen) return out;
        if (detailedWeatherOn) out.add(0);
        return out;
    }

    /** A spoken-feedback service (TalkBack) is on. */
    private boolean talkBack() {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled()
                && !am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isEmpty();
    }

    /** TalkBack moved to an item: the remote's focus goes there (opening or closing the drawer on the way). */
    private void a11yFocus(int id) {
        wake();
        int group = (id - 1) / 1000, i = (id - 1) % 1000;
        if (group == GRID) {
            if (!sheetOpen || i >= others.size()) return;
            gridIndex = i;
            retargetGrid(false);
            gridDirty = true;
            click.move();
            return;
        }
        if (sheetOpen || reordering || sectionMove != 0) return;
        switch (group) {
            case Z_OTHER:
                if (i >= others.size()) return;
                if (!drawerOpen) openDrawer();
                otherIndex = i;
                break;
            case Z_DRAWER:
                if (i >= drawerItems.size()) return;
                if (!drawerOpen) openDrawer();
                drawerIndex = i;
                break;
            case WIDGETS:
                if (!widgets().contains(i)) return;
                widgetIndex = i;
                break;
            case Z_TOP:
                if (i >= TOP_COUNT || !visibleTop(i)) return;
                if (drawerOpen) closeDrawer();
                topIndex = i;
                if (i < TOP_ACTION) selectTab(i);
                break;
            case Z_CARDS:
                if (i >= cardCount()) return;
                if (drawerOpen) closeDrawer();
                cardIndex = i;
                break;
            case Z_SHELF:
                if (i >= shelf.size()) return;
                if (drawerOpen) closeDrawer();
                shelfIndex = i;
                break;
            default:
                return;
        }
        zone = group;
        retarget(false);
        click.move();
    }

    private static String label(List<HomeApps.Item> list, int i) {
        return i >= 0 && i < list.size() && list.get(i).label != null ? list.get(i).label : "";
    }

    /** A recommendation: title, details and the app that plays it. */
    private static String pickText(Recs.Pick p) {
        if (p == null) return "";
        StringBuilder s = new StringBuilder(p.title == null ? "" : p.title);
        if (p.meta != null && !p.meta.isEmpty()) s.append(", ").append(p.meta);
        String app = Recs.serviceName(p);
        if (app != null && !app.isEmpty()) s.append(". App: ").append(app);
        return s.toString();
    }

    /** The weather card as TalkBack reads it: what it shows, in the same words and units. */
    private String weatherText() {
        StringBuilder s = new StringBuilder(Ui.tr("Detailed weather"));
        s.append(" ").append(Prefs.city().isEmpty() ? Ui.tr("Clima", "Weather") : Prefs.city());
        HomeData.Weather wx = data.weather;
        if (Double.isNaN(wx.temp)) return s.append(". ").append(Ui.tr("Sem dados do clima agora.", "No weather data right now.")).toString();
        s.append(". ").append(HomeData.degrees(wx.temp)).append(", ").append(HomeData.condition(wx.code, wx.isDay))
                .append(Ui.tr(". Máxima ", ". High ")).append(HomeData.degrees(wx.max))
                .append(Ui.tr(", mínima ", ", low ")).append(HomeData.degrees(wx.min)).append('.');
        int hours = Math.min(6, wx.hours.size());
        if (hours > 0) s.append(Ui.tr(" Próximas horas: ", " Next hours: "));
        for (int i = 0; i < hours; i++) {
            s.append(HomeData.hour(wx.hours.get(i))).append(' ').append(HomeData.degrees(wx.hourTemp.get(i))).append(i < hours - 1 ? ", " : ".");
        }
        int rain = FeatureRules.firstRain(wx.hourRain.subList(0, Math.min(hours, wx.hourRain.size())), FeatureRules.RAIN_SHOWN);
        if (rain >= 0) s.append(' ').append(Ui.trf("Chance of rain at {hour}: {percent}.", "hour", HomeData.hour(wx.hours.get(rain)),
                "percent", LocaleFormats.percent(wx.hourRain.get(rain) / 100.0, Ui.locale())));
        if (wx.sunrise != 0) s.append(Ui.tr(" Nascer do sol ", " Sunrise ")).append(Ui.time(wx.sunrise)).append('.');
        if (wx.sunset != 0) s.append(Ui.tr(" Pôr do sol ", " Sunset ")).append(Ui.time(wx.sunset)).append('.');
        return s.toString();
    }



    // MARK: - Light design: title, tabs, cards and icon-only buttons

    private void layoutLight() {
        if (lTab == null) return;
        // Text tabs; the focus pill extends 26 px past the text (the first tab's text starts at the margin).
        float pad = 26 * k, tabTop = L_TABBAR_TOP * k, tabBottom = (L_TABBAR_TOP + L_TABBAR_H) * k;
        float x = MARGIN * k - pad;
        for (int i = 0; i < TOP_ACTION; i++) {
            if (!visibleTop(i)) { topRects[i].setEmpty(); continue; }
            float tw = Math.max(lTabOn.measureText(tabs()[i]), lTab.measureText(tabs()[i]));
            topRects[i].set(x, tabTop, x + tw + 2 * pad, tabBottom);
            x = topRects[i].right + 4 * k;
        }
        layoutHeaderActions();
        float top = L_CARDS_TOP * k, ch = L_CARD_H * k, g = L_CARD_GAP * k, left = MARGIN * k, tall = L_TALL_W * k;
        float wide = (CONTENT_R - MARGIN) * k - 2 * tall - 2 * g;
        cardRects[0].set(left, top, left + wide, top + ch);
        cardRects[1].set(cardRects[0].right + g, top, cardRects[0].right + g + tall, top + ch);
        cardRects[2].set(cardRects[1].right + g, top, cardRects[1].right + g + tall, top + ch);
        for(int i=0;i<cards.size()&&i<cardRects.length;i++){
            ContentCard card=cards.get(i);float width=cardRects[i].width();
            fitPaint.set(lCardTitle);card.shownTitle=TextUtils.ellipsize(card.title,fitPaint,width,TextUtils.TruncateAt.END).toString();
            fitPaint.set(lCardMeta);card.shownMeta=card.meta==null?"":TextUtils.ellipsize(card.meta,fitPaint,width,TextUtils.TruncateAt.END).toString();
        }
        layoutHeaderInfo();
        heroDirty = statusDirty = true;
    }

    private void layoutHeaderActions() {
        if(headerGreeting==null)return;
        // Include accents and descenders; measure only when the header layout changes.
        headerGreeting.getTextBounds(headerGreetingText,0,headerGreetingText.length(),headerGreetingBounds);
        float b = L_BUTTON * k, right = CONTENT_R * k, cy = L_BUTTON_CY * k;
        for (int i = 2; i >= 0; i--) {
            float width = i == 2 ? Math.max(236*k,headerGreetingBounds.width()+(48+27+L_BUTTON/2)*k) : b;
            topRects[TOP_ACTION + i].set(right - width, cy - b / 2, right, cy + b / 2);
            right -= width + 18 * k;
        }
    }

    /** The three cards of the current tab (Explore, Movies, Shows) and the background taken from the first one. */
    private int firstTab() { return 0; }
    private boolean recentTopVisible() { return continueOn && (recsOn || !emptyContinue()); }
    private boolean visibleTop(int index) { return HomeTabs.visible(index,recentTopVisible(),recsOn); }
    private int firstTop() { return HomeTabs.first(recentTopVisible(),recsOn); }
    private int nextTop(int from, int direction) {
        return HomeTabs.next(from,direction,recentTopVisible(),recsOn);
    }
    private boolean emptyContinue() { return continueOn && tab == 0 && cards.isEmpty(); }
    private int cardCount() { return Math.min(3,cards.size()); }
    private Backdrop cardBackdrop(ContentCard card) {
        return card == null || card.program == null ? null : continuing.backdrop(card.program);
    }
    private void updateCards() {
        if(!HomeTabs.visible(tab,continueOn,recsOn) || tab>=TOP_ACTION)tab=firstTab();
        List<ContinueWatching.Program> recent=recentPrograms(),recommended=recommendedPrograms();
        recentOffset=Math.max(0,Math.min(recentOffset,Math.max(0,recent.size()-3)));
        recommendationOffset=Math.max(0,Math.min(recommendationOffset,Math.max(0,recommended.size()-3)));
        List<ContinueWatching.Program> programs=tab==0?recent:recommended;
        int offset=tab==0?recentOffset:recommendationOffset;
        int count=Math.min(3,programs.size()-offset);
        boolean changed=renderedTab!=tab || renderedLanguage!=Ui.generation || cards.size()!=count;
        if(!changed)for(int i=0;i<count;i++)if(!programs.get(offset+i).same(cards.get(i).program)){changed=true;break;}
        if(changed) {
            List<ContentCard> next=new ArrayList<>(count);
            for(int i=0;i<count;i++)next.add(new ContentCard(programs.get(offset+i)));
            cards=next;renderedTab=tab;renderedLanguage=Ui.generation;
            cardIndex=Math.min(cardIndex,Math.max(0,count-1));layoutLight();
            if(zone==Z_TOP && !visibleTop(topIndex)){topIndex=firstTop();retarget(false);}
            if(zone==Z_CARDS && count==0){zone=Z_SHELF;retarget(false);}
        }
        if(preparedTab!=tab || preparedRecent!=recent || preparedRecommendations!=recommended
                || preparedRecentOffset!=recentOffset || preparedRecommendationOffset!=recommendationOffset){
            List<ContinueWatching.Program> recentWindow=recent.subList(recentOffset,Math.min(recent.size(),recentOffset+3));
            List<ContinueWatching.Program> recommendedWindow=recommended.subList(recommendationOffset,Math.min(recommended.size(),recommendationOffset+3));
            continuing.setWindows(tab==0?recentWindow:recommendedWindow,tab==0?recommendedWindow:recentWindow);
            preparedTab=tab;preparedRecent=recent;preparedRecommendations=recommended;
            preparedRecentOffset=recentOffset;preparedRecommendationOffset=recommendationOffset;
        }
        // Images arrive separately from metadata. Polls with identical data never rebuild card text/layout.
        for(ContentCard card:cards)cardBackdrop(card);
        boolean artChanged=cardsArtVersion!=continuing.artVersion();cardsArtVersion=continuing.artVersion();
        // Appearance → Background from content off: the theme's plain background, as in the clock layout.
        boolean follow=Prefs.contentBackground();
        Backdrop background=cards.isEmpty() || !follow?null:cardBackdrop(cards.get(0));
        if(cards.isEmpty() || background!=null || rebindingPosters || !follow)showBackdrop(background);
        if(changed || artChanged)heroDirty=true;
        if(changed)spoken.focusMoved();
    }

    private List<ContinueWatching.Program> recentPrograms(){return sharedPrograms(false);}
    private List<ContinueWatching.Program> recommendedPrograms(){return sharedPrograms(true);}
    private List<ContinueWatching.Program> sharedPrograms(boolean recommendations) {
        if(!continueOn || recommendations&&!recsOn)return java.util.Collections.emptyList();
        ContinueWatching.Snapshot snapshot=continuing.snapshot();boolean permission=ContinueWatching.permitted(getContext());
        if(snapshot!=filteredSnapshot || filteredPrefs!=Prefs.version || filteredContent!=Prefs.contentVersion || filteredPermission!=permission) {
            filteredSnapshot=snapshot;filteredPrefs=Prefs.version;filteredContent=Prefs.contentVersion;filteredPermission=permission;
            filteredRecent=new ArrayList<>();filteredRecommendations=new ArrayList<>();
            if(permission) {
                java.util.Set<String> hidden=Prefs.continueHidden(),hiddenPrograms=Prefs.hiddenContent();
                for(ContinueWatching.Program p:snapshot.programs)
                    if(!hidden.contains(p.pkg)&&!hiddenPrograms.contains(p.hiddenKey))filteredRecent.add(p);
                if(recsOn)for(ContinueWatching.Program p:snapshot.recommendations)
                    if(!hidden.contains(p.pkg)&&!hiddenPrograms.contains(p.hiddenKey))filteredRecommendations.add(p);
            }
        }
        return recommendations?filteredRecommendations:filteredRecent;
    }

    private void selectTab(int index) {
        if (index == tab) return;
        tab = index;
        updateCards();
        heroDirty = true;
    }

    /**
     * Background: the first card's photo, heavily blurred and almost white; without it, a light gradient. At night, the
     * same photo almost black (or a dark gradient), drawn on top during the transition.
     */
    private void drawLightSky(Canvas c) {
        long up = SystemClock.uptimeMillis();
        float e = bgFadeAt == 0 ? 1 : fadeEase(bgFadeAt, up);
        boolean fading = bgFadeAt != 0;
        // Drawer open: the background lightens (by day) or darkens (at night), as a filter on the drawing itself.
        android.graphics.ColorFilter v = veil(tone(0.32f, 0.42f) * Ui.clamp(drawer.value), blend(0xFFFFFFFF, 0xFF000000, night));
        if (bgTo == null || (fading && bgFrom == null)) {
            if (lightGradient == null) lightGradient = new LinearGradient(0, 0, 0, h, 0xFFF5F5F7, 0xFFE2E2E7, Shader.TileMode.CLAMP);
            if (nightGradient == null) nightGradient = new LinearGradient(0, 0, 0, h, 0xFF1C1C1E, 0xFF0B0B0D, Shader.TileMode.CLAMP);
            fill.setColorFilter(v);
            fill.setColor(0xFF000000);
            if (night < 0.999f) {
                fill.setShader(lightGradient);
                c.drawRect(screen, fill);
            }
            if (night > 0.001f) {
                fill.setShader(nightGradient);
                fill.setAlpha(Math.round(255 * night));
                c.drawRect(screen, fill);
                fill.setAlpha(255);
            }
            fill.setShader(null);
            fill.setColorFilter(null);
        }
        imagePaint.setColorFilter(v);
        if (fading && bgFrom != null) drawLightImage(c, bgFrom, bgTo == null ? 1 - e : 1);
        if (bgTo != null) drawLightImage(c, bgTo, fading ? e : 1);
        imagePaint.setColorFilter(null);
    }

    private void drawLightImage(Canvas c, Backdrop b, float alpha) {
        if (night < 0.999f || b.nightImage == null) drawImage(c, b.lightImage, alpha);
        if (night > 0.001f) drawImage(c, b.nightImage, alpha * night);
    }

    private void drawLightTop(Canvas c) {
        applyTone();
        if (!picksOn()) {
            drawClock(c);
            return;
        }
        // Text tabs (the selected one in bold, in the main text color); with focus on the tabs, the selected one gets
        // the white pill with a shadow and grows a little (the tvOS focus).
        float focus = Ui.clamp(topFocus[tab].value);
        for (int i = 0; i < TOP_ACTION; i++) {
            if (!visibleTop(i)) continue;
            RectF r = topRects[i];
            if (i == tab && focus > 0.01f) {
                float s = 1 + 0.05f * focus;
                cell.set(r.centerX() - r.width() * s / 2, r.centerY() - r.height() * s / 2,
                        r.centerX() + r.width() * s / 2, r.centerY() + r.height() * s / 2);
                float rr = cell.height() / 2;
                if (focus > 0.01f) {
                    tmp.set(cell);
                    tmp.offset(0, 8 * k * focus);
                    shadow.setColor(Ui.alpha(0xFF000000, tone(0.22f, 0.4f) * focus));
                    shadows.draw(c, tmp, rr, SHADOW_BLUR * k, shadow);
                }
                fill.setShader(null);
                fill.setColor(Ui.alpha(tone(L_INK,WHITE), focus));
                c.drawRoundRect(cell, rr, rr, fill);
            }
            Paint p = i == tab ? lTabOn : lTab;
            if (i == tab) lTabOn.setColor(blend(tone(L_INK, WHITE), tone(WHITE,L_INK), focus));
            c.drawText(tabs()[i], r.centerX() - p.measureText(tabs()[i]) / 2, r.centerY() + Ui.halfCap(p), p);
        }
        if(cards.isEmpty()) {
            if(tab==0)drawClock(c);
            else {
                c.drawText(Ui.tr("No shared recommendations yet."),MARGIN*k,L_DATE_BASE*k,lCardTitle);
                c.drawText(Ui.tr("Open a supported app, then return Home."),MARGIN*k,(L_DATE_BASE+48)*k,lCardMeta);
            }
            return;
        }
        int visible=cards.size();
        int top = -1;
        for (int i = 0; i < visible; i++) if (cardFocus[i].value > 0.02f && (top < 0 || cardFocus[i].value > cardFocus[top].value)) top = i;
        for (int i = 0; i < visible; i++) if (i != top) drawLightCard(c, i);
        if (top >= 0) drawLightCard(c, top);
    }

    private boolean picksOn() {
        return continueOn || recsOn;
    }

    private void drawClock(Canvas c) {
        float x = MARGIN * k;
        c.drawText(dateText, x - Ui.leftBearing(lDate, dateText), L_DATE_BASE * k, lDate);
        c.drawText(clockText, x - Ui.leftBearing(lClock, clockText), L_CLOCK_BASE * k, lClock);
        HomeData.Weather wx = data.weather;
        if (!weatherOn || Double.isNaN(wx.temp)) return;
        if (data.weatherAt != weatherLineAt || Prefs.version != weatherLinePrefs || !Ui.language.equals(weatherLineLanguage)) {
            weatherLineAt = data.weatherAt;
            weatherLinePrefs = Prefs.version;
            weatherLineLanguage = Ui.language;
            weatherLine = HomeData.degrees(wx.temp) + "  " + HomeData.condition(wx.code, wx.isDay) + "   "
                    + (Ui.tr ? "En yüksek " + HomeData.degrees(wx.max) + "  En düşük " + HomeData.degrees(wx.min)
                    : Ui.ru ? "Макс. " + HomeData.degrees(wx.max) + "  Мин. " + HomeData.degrees(wx.min)
                    : Ui.en ? "H:" + HomeData.degrees(wx.max) + "  L:" + HomeData.degrees(wx.min)
                    : Ui.de ? "H:" + HomeData.degrees(wx.max) + "  T:" + HomeData.degrees(wx.min)
                    : "Máx. " + HomeData.degrees(wx.max) + "  Mín. " + HomeData.degrees(wx.min));
        }
        float size = 40 * k, base = L_WEATHER_BASE * k;
        String symbol = Symbols.weather(wx.code, wx.isDay);
        float sw = Symbols.width(getContext(), symbol, size);
        Symbols.draw(getContext(), c, symbol, x + sw / 2, base - Ui.halfCap(lWeather), size, lWeather.getColor());
        c.drawText(weatherLine, x + sw + 16 * k, base, lWeather);
    }

    /**
     * Recommendation card: the title's photo (already at the focused card size, so it stays sharp), rounded corners and
     * a soft shadow; when focused, it grows 5%, rises and lights up its border. Below it, the title (and on the large
     * card, genre · runtime · service).
     */
    private void drawLightCard(Canvas c, int i) {
        RectF base = cardRects[i];
        ContentCard p = i < cards.size() ? cards.get(i) : null;
        float v = cardFocus[i].value, f = Ui.clamp(v), s = 1 + 0.05f * v;
        float bw = base.width(), bh = base.height(), r0 = 18 * k;
        float cx = base.centerX(), cy = base.centerY() - 6 * k * v;
        cell.set(cx - bw * s / 2, cy - bh * s / 2, cx + bw * s / 2, cy + bh * s / 2);
        float r = r0 * s;
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
        tmp.offset(0, (10 + 14 * f) * k);
        shadowDeep.setColor(Ui.alpha(0xFF000000, tone(0.14f, 0.32f) + tone(0.2f, 0.28f) * f));
        shadows.draw(c, tmp, r0, DEEP_BLUR * k, shadowDeep);
        c.restore();
        Backdrop b = cardBackdrop(p);
        Bitmap img = b == null ? null : (i == 0 ? b.wide : b.tall);
        if (img != null) {
            roundImage(c, img, cell, r,i==0?b.wideShader:b.tallShader);
        } else {
            fill.setShader(null);
            fill.setColor(tone(0xFFDADADF, 0xFF2C2C2E));
            c.drawRoundRect(cell, r, r, fill);
        }
        if (f > 0.01f) {
            if (lightSheens.length != cardRects.length) {
                lightSheens = new LinearGradient[cardRects.length];
                lightRims = new LinearGradient[cardRects.length];
                lightShaderW = new float[cardRects.length];
            }
            if (lightSheens[i] == null || lightShaderW[i] != bw) {
                lightSheens[i] = new LinearGradient(-bw / 2, -bh / 2, 0, bh / 2, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
                lightRims[i] = new LinearGradient(0, -bh / 2, 0, bh / 2, 0xE6FFFFFF, 0x59FFFFFF, Shader.TileMode.CLAMP);
                lightShaderW[i] = bw;
            }
            int alpha = Math.round(255 * f);
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
            sheen.setShader(lightSheens[i]);
            sheen.setAlpha(alpha);
            c.drawRoundRect(tmp, r0, r0, sheen);
            sheen.setAlpha(255);
            rim.setShader(lightRims[i]);
            rim.setAlpha(alpha);
            rim.setStrokeWidth(2.5f * k / s);
            c.drawRoundRect(tmp, r0, r0, rim);
            rim.setStrokeWidth(2.5f * k);
            rim.setAlpha(255);
            c.restore();
        }
        lightFocusRing(c,cell,r,f);
        if (p == null) return;
        if (p.program != null && p.program.position > 0 && p.program.duration > 0) {
            float left=cell.left+16*k, right=cell.right-16*k, bottom=cell.bottom-14*k;
            fill.setShader(null);fill.setColor(0x66FFFFFF);c.drawRoundRect(left,bottom,right,bottom+4*k,2*k,2*k,fill);
            fill.setColor(0xFFFFFFFF);c.drawRoundRect(left,bottom,left+(right-left)*WatchNextRules.progress(p.program.position,p.program.duration),bottom+4*k,2*k,2*k,fill);
        }
        c.drawText(p.shownTitle, base.left, base.bottom + 46 * k, lCardTitle);
        if (i == 0 && p.meta != null && !p.meta.isEmpty()) {
            c.drawText(p.shownMeta, base.left, base.bottom + 80 * k, lCardMeta);
        }
    }

    /** Cached status geometry, rebuilt only when the minute, language or weather changes. */
    private void layoutHeaderInfo() {
        if (headerTime == null) return;
        float x = (MARGIN + 24) * k;
        headerTimeX = x;
        x += headerTime.measureText(clockText) + 22 * k;
        headerDateX = x;
        x += headerText.measureText(headerShortDate) + 24 * k;
        headerWeatherText = "";
        HomeData.Weather wx = data.weather;
        if (weatherOn && !Double.isNaN(wx.temp)) {
            headerDividerX = x;
            x += 24 * k;
            headerWeatherSymbol = Symbols.weather(wx.code, wx.isDay);
            headerSymbolX = x + 13 * k;
            x += 38 * k;
            headerWeatherX = x;
            String city = Prefs.city();
            String text = HomeData.degrees(wx.temp) + (city.isEmpty() ? "" : " · " + city);
            float actionLeft = topRects[TOP_ACTION].isEmpty() ? 1396 * k : topRects[TOP_ACTION].left;
            float right = Math.min((MARGIN + 1000) * k, actionLeft - 48 * k);
            fitPaint.set(headerText);
            headerWeatherText = TextUtils.ellipsize(text, fitPaint, Math.max(40 * k, right - x - 24 * k),
                    TextUtils.TruncateAt.END).toString();
            x += headerText.measureText(headerWeatherText) + 24 * k;
        }
        headerInfo.set(MARGIN * k, (L_BUTTON_CY - L_BUTTON / 2) * k, x,
                (L_BUTTON_CY + L_BUTTON / 2) * k);
    }

    /** The shelf's subtle glass material, with the existing header layout and actions. */
    private void drawLightButtons(Canvas c) {
        applyTone();
        fill.setShader(null);
        drawShelfSurface(c, headerInfo, 34 * k);
        float cy = L_BUTTON_CY * k;
        c.drawText(clockText, headerTimeX, cy + Ui.halfCap(headerTime), headerTime);
        c.drawText(headerShortDate, headerDateX, cy + Ui.halfCap(headerText), headerText);
        if (!headerWeatherText.isEmpty()) {
            c.drawLine(headerDividerX, cy - 15 * k, headerDividerX, cy + 15 * k, headerLine);
            Symbols.draw(getContext(), c, headerWeatherSymbol, headerSymbolX, cy, 25 * k, headerText.getColor());
            c.drawText(headerWeatherText, headerWeatherX, cy + Ui.halfCap(headerText), headerText);
        }
        for (int i = 0; i < 3; i++) {
            RectF r = topRects[TOP_ACTION + i];
            float f = Ui.clamp(topFocus[TOP_ACTION + i].value), scale = 1 + 0.06f * f;
            float hw = r.width() * scale / 2, hh = r.height() * scale / 2;
            cell.set(r.centerX() - hw, r.centerY() - hh, r.centerX() + hw, r.centerY() + hh);
            drawShelfSurface(c, cell, hh);
            if (f > 0.01f) {
                fill.setColor(Ui.alpha(tone(L_INK,WHITE), f));
                c.drawRoundRect(cell, hh, hh, fill);
            }
            if (i == 2) {
                float badge = cell.right - hh;
                float badgeRadius = 27 * k * scale;
                fill.setColor(0xFFCCA03C);
                c.drawCircle(badge, cell.centerY(), badgeRadius, fill);
                Symbols.draw(getContext(), c, lightButtons.get(i).symbol, badge, cell.centerY(), 27 * k * scale, L_INK);
                headerGreeting.setColor(blend(tone(L_INK, WHITE), tone(WHITE,L_INK), f));
                float textLeft = cell.left + 24 * k * scale;
                float textRight = badge - badgeRadius - 24 * k * scale;
                // Center visible glyphs in their own slot; text, badge and pill grow together.
                c.save();
                c.translate((textLeft+textRight)/2,cell.centerY());
                c.scale(scale,scale);
                c.drawText(headerGreetingText,-headerGreetingBounds.exactCenterX(),
                        -headerGreetingBounds.exactCenterY(),headerGreeting);
                c.restore();
            } else Symbols.draw(getContext(), c, lightButtons.get(i).symbol, cell.centerX(), cell.centerY(),
                    27 * k * scale, blend(tone(L_INK, WHITE), tone(WHITE,L_INK), f));
            if (f > 0.01f) {
                String label = lightButtons.get(i).label;
                float half = lTip.measureText(label) / 2;
                float x = Math.min(r.centerX(), CONTENT_R * k - half);
                lTip.setAlpha(Math.round(255 * f));
                c.drawText(label, x, r.bottom + (36 + 4 * f) * k, lTip);
            }
        }
    }

    private boolean moveLight(int dx, int dy) {
        switch (zone) {
            case Z_SHELF:
                if (dy >= 0) return false;
                if (shelf.isEmpty()) { zone = Z_TOP; topIndex = firstTop(); break; }
                if (cardCount()==0) {
                    zone = Z_TOP;
                    topIndex = firstTop();
                } else {
                    zone = Z_CARDS;
                    cardIndex = nearest(cardRects, cardCount(), tileRects[shelfIndex].centerX());
                }
                break;
            case Z_CARDS:
                if (dx != 0) {
                    int offset=tab==0?recentOffset:recommendationOffset;
                    int total=tab==0?recentPrograms().size():recommendedPrograms().size();
                    if((dx>0 && cardIndex==cardCount()-1 && offset+cards.size()<total) || (dx<0 && cardIndex==0 && offset>0)) {
                        if(tab==0)recentOffset+=dx;else recommendationOffset+=dx;
                        updateCards();retarget(false);click.move();return true;
                    }
                    int next = Math.max(0, Math.min(cardCount() - 1, cardIndex + dx));
                    if (next == cardIndex) return true;
                    cardIndex = next;
                } else if (dy < 0) {
                    zone = Z_TOP;
                    topIndex = tab;     // as on Apple TV: moving up to the tabs focuses the selected tab
                } else {
                    zone = Z_SHELF;
                    if (!shelf.isEmpty()) shelfIndex = nearest(tileRects, tileRects.length, cardRects[cardIndex].centerX());
                }
                break;
            case Z_TOP:
                if (dx != 0) {
                    int next = nextTop(topIndex, dx);
                    if (next == topIndex) return true;
                    topIndex = next;
                    if (topIndex < TOP_ACTION) selectTab(topIndex);   // focusing a tab selects it
                } else if (dy > 0) {
                    if (cardCount()==0) {
                        zone = Z_SHELF;
                    } else {
                        zone = Z_CARDS;
                        cardIndex = nearest(cardRects, cardCount(), topRects[topIndex].centerX());
                    }
                } else {
                    int next=HomeTabs.up(topIndex);
                    if(next==topIndex)return true;
                    topIndex=next;
                }
                break;
            default:
                return false;
        }
        retarget(false);
        click.move();
        return true;
    }

    private static int nearest(RectF[] rects, int count, float x) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < Math.min(count, rects.length); i++) {
            float d = Math.abs(rects[i].centerX() - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    // MARK: - Helpers

    private static float smoothstep(float e0, float e1, float x) {
        float t = Ui.clamp((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    private static int blend(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
