# Performance review

PureTV keeps the native home lightweight: Java/Canvas, no Android runtime libraries, event-driven refresh,
bounded artwork, no continuous RAM polling and no frame callback while the home is idle between clock updates.
Weather and app catalogues refresh when needed. Hidden-screen listeners stop.

## Final v1 changes

- Shelf logos are resized when the catalogue or shelf geometry changes, before drawing. Stable source images keep their prepared pixels.
- Other-app rows outside the viewport are omitted, with a conservative extra row to avoid popping during scrolling.
- Legacy shadow masks use a 32-entry / 1 MiB LRU rather than clearing the whole cache on overflow.
- Spring coefficients are computed once; regression tests require the same float trajectory as the preceding implementation.
- Photo screensaver callbacks follow its existing approximately 20 fps render cadence. Clock-only screensavers retain one-second drift updates.

The combined content home still prepares up to three recent and three recommended items. It keeps unchanged
artwork across tab changes, settings returns and screensaver returns. Clock-only mode does not request content artwork.
This review deliberately preserves that working set.

## Real-device comparison

Tested on the same Xiaomi Mi Stick, Android TV 9, 1920 x 1080, standard visual mode. A same-key update preserves
preferences and HOME recovery. The preceding APK was reinstalled and warmed up before three identical runs;
the candidate was then installed and given the same warm-up and three runs. Network and system settings were unchanged.

Numbers below are the median of three per-run median frame durations from Android `gfxinfo framestats`,
not a promise of constant frame rate. Lower is better. Device temperature, scheduling and clock boundaries affect samples.

| Transition | Preceding version | v1 candidate |
| --- | ---: | ---: |
| Home to shortcuts | 15.47 ms | 15.82 ms |
| Shortcuts to detailed weather | 29.54 ms | 27.20 ms |
| Weather to other apps | 7.29 ms | 7.80 ms |
| Warm home process PSS, four samples | 34.75 MiB | 34.57 MiB |

The weather transition improved by about 8% in this matched sample. Frames over 33.34 ms in that transition
fell from 71/177 to 54/177. The other transitions remain in a similar range; they are not claimed as improvements.
Warm memory is effectively unchanged. Earlier cold measurements varied substantially and are not mixed into this comparison.

Both versions retained four prepared images (one published recent item and three recommendations in this device's data),
12,349,440 bitmap bytes, with no pending loads at the end. This is not a forced six-poster measurement.
During the 20-second idle window the baseline rendered zero frames; the candidate rendered two at a clock-minute boundary.
There is no continuous idle animation. Process PSS excludes the separate AirPlay receiver and Android system processes.

A trial that hardware-cached the whole drawer increased weather-scroll cost and memory; it was rejected.
Older Mi Stick GPUs still miss some 60 Hz deadlines during the weather transition. Lightweight mode remains available.

## Reference review

The review considered static-surface caching and memory-trim policy in Clean Your TV, background preparation in Matinee,
and the shared launcher's drawing lifecycle. Those ideas were assessed against actual Mi Stick measurements;
no new library or copied implementation was introduced from the comparison repositories.

- [Clean Your TV](https://github.com/Gathin23/cleanyourtv): ClockScene, MemoryTrimPolicy, GlassView and HeroArt.
- [Matinee](https://github.com/Jarvis322/matinee): AppCatalog, LastPlayedPanel and CinematicBackground.

## Reproducing checks

Build Android, then run `python tools/tests/run.py` with the SDK/JDK arguments described in DEVELOPMENT.md.
`PerformanceRegression` tests zero/tiny/exact/oversized mask budgets, LRU replacement, visible-row bounds,
identical spring trajectories and photo pacing at 50/60/90/120 Hz. Existing poster tests cover repeated tab
reversals, lifecycle suspension, byte bounds and unchanged in-flight identities.

For hardware profiling, use the same visual mode, content, resolution, warm-up and key sequence in both APKs.
Compare several runs and keep raw device dumps local. Do not publish viewing history, IP addresses or account identifiers.
