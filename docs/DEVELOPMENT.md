# Development

[Build](#build) · [Tests](#tests) · [Architecture](#architecture) · [Performance](#performance) · [Release](#release)

## Build

The Android launcher needs Python 3.10+, JDK 17+ and Android SDK command-line tools. The build uses only
Python's standard library and Android SDK tools; Gradle and pip packages are not needed for the launcher.
Accept the SDK licences through Android's tools.

```sh
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
# Set ANDROID_HOME to the SDK and JAVA_HOME to the JDK.
python build.py --config config.example.properties
python tools/tests/run.py
```

The same Python commands work on Windows. Override paths with `--java-home`, `--build-tools` and `--android-jar`.
The test runner accepts `--java-home` and `--android-jar` as well.

`config.example.properties` contains empty API keys. An ignored local `config.properties` may provide your location,
language and keys; changes made on the TV override those defaults. Use the explicit example config for public builds.
Any API key embedded in an APK is extractable. Never publish a personal configured APK.

### Signing

The default `PureTV-unsigned.apk` cannot be installed until signed. Keep one production key securely; updates of
this PureTV package require the same certificate. A production key is never generated automatically.

```sh
# Create private/ first. keytool prompts for passwords in your own terminal.
keytool -genkeypair -keystore private/puretv.keystore -alias puretv -keyalg RSA -keysize 3072 -validity 10000
# Set PURETV_STORE_PASSWORD and PURETV_KEY_PASSWORD in your own environment.
python build.py --config config.example.properties --keystore private/puretv.keystore --alias puretv
```

Keys/passwords stay out of Git and issues. CI only builds an empty-key unsigned APK; it cannot sign or publish releases.
The optional receiver and launcher must be signed with the **same certificate** for quick-panel control.
Do not clear application data or uninstall to bypass an update certificate mismatch.

`out/` is reset on each Android build to prevent stale classes from entering an APK. Java 8, min API 28 and target
API 33 are used. The namespace and application ID are `io.puretv`. Font files are stored without compression.
PureTV and incorporated MIT notices are bundled under `assets/licenses/`.

### Web packages

Python's standard library packages both web editions. `--generic` excludes local configuration and API credentials.

```sh
python webos/build.py --generic
python tizen/build.py --generic --sign YOUR_PROFILE
```

Omit `--sign` only to inspect an unsigned WGT. `--preview` writes files inside `out/webos` or `out/tizen`.
The optional GPL receiver has a separate [source/build guide](../tools/airplay/README.md).

### Configuration

Keep personal defaults in ignored `config.properties`; use `--config config.example.properties` for public Android builds.
TV preferences override build defaults. Generic web builds use the TV time zone to select a city.

| Setting | Purpose |
| --- | --- |
| `tmdb_key`, `tmdb_region` | Optional catalogue credentials and region |
| `watchmode_key` | Optional direct-title links |
| `latitude`, `longitude`, `city` | Weather location and label |
| `language`, `temperature` | Locale and C/F/automatic units |
| `hide_animation`, `hide_services` | Optional catalogue filters |
| `disable_assistant` | Optional Android Assistant disabling |
| `theme`, `hide_apps`, `shelf_apps` | LG/Samsung web defaults |

## Tests

Run after the Android build:

```sh
python tools/tests/run.py
python tools/tests/test_web_packages.py
node tools/tests/web_regression.js
python tools/audit_public.py
python tools/audit_branding.py
python tools/translate.py --check --allow-fallback
```

The eight JVM suites cover bounded/stale requests, immutable content snapshots, long settings navigation,
content controls, 1,600 poster-cache cycles, 3,200 dual-window cycles, direct poster framing, welcome flow, TV input/remote rules, locale formats, the settings-backup allowlist, the receiver download rules, and the hidden-app, start-up, app-lock (PIN, hours, waits) and rain rules (`FeatureRules`).
Locale tests verify percent placement for English, Turkish and French. Translation tests cover legacy two-argument calls, local menu wrappers, conditional labels, placeholders, date/plural syntax and core-label coverage across 24 catalogues. Configuration tests verify escaping and invalid-input rejection. No emulator or extra Java/Python library is needed.
Android-native test classes in `tools/tests/io/puretv/` require a device and are excluded from the desktop runner.
All generated Android/test files stay under `out/` and are excluded from source archives.

### Device checks

With USB/network debugging allowed for your computer, `tools/device_qa.py` repeats the device checks without installing
anything or changing HOME, settings or packages:

```sh
python tools/device_qa.py --serial TV_ADDRESS:5555 snapshot   # read-only summary
python tools/device_qa.py --serial TV_ADDRESS:5555 native     # after the build: native poster tests and timing
```

`native` runs `PosterNativeRegression`, `DualPosterNativeRegression` and `PosterBenchNative` with `app_process` from
`/data/local/tmp/puretv-qa` and always deletes that folder. The snapshot omits network names, addresses, accounts and
viewing history. `settings pull|push FILE` copies the settings backup in `Download` to or from the computer.

Web tests inspect generic IPK/WGT structure, manifests, resources, empty credentials and licences, then exercise mock
Luna/Tizen APIs without network/browser access. They do not establish hardware, renderer, HDMI or signing compatibility.
The source audits check known credential patterns, PureTV identities, presentation text and required notices; they cannot
prove that every possible secret or runtime issue is absent. Drawing/lifecycle, ADB, recovery and AirPlay need hardware QA.

## Architecture

- `HomeView`: Canvas layers, shared shelf geometry, focus and movement; a lower-cost drawing path for Android 9–12.
- `ContinueWatching` / `PosterPlan` / `PosterCache`: Android TV providers, immutable content snapshots and bounded prepared artwork.
- `HomeData`: Open-Meteo weather and city cache; expired data refreshes in the background.
- `Recs`: optional TMDB catalogue/screensaver sources, separate from app-published home recommendations.
- `SettingsPage`: measured rows and event-driven state. RAM/storage is read as a snapshot when the panel opens.
- `DreamView`: reusable recordings, plain clock, background photo preparation and one dimming filter.
- `HomeMode` / `ModeJob`: serialized mode changes in the main process; `Watchdog` is the recovery route.
- `RemoteKeyService`: supported key handling in its own `:keys` process, without heavy work in key events.
- `AirPlayControl`: checks the separate receiver's component, signature permission and certificate on every command.

Listeners/frame callbacks stop when a screen is hidden. Normal tab/app/settings/screensaver returns retain prepared
artwork; actual memory pressure may trim it. Budgets do not force visible posters to reload on ordinary navigation.
Raw debug output may contain private viewing data; publish sanitized measurement summaries only.

The launcher/test namespace is `io.puretv`. Android broadcasts, recovery keys, web local storage and platform manifests
use PureTV names. [Installation notes](INSTALL.md#identity-and-upgrades) explain the separate package identity.


The shared `web/` interface uses ES5/HTML/CSS. `web/tv.js` isolates LG Luna/local-list fallback from Samsung app/input APIs.
`tools/webapp.py` combines assets/translations for IPK/WGT packagers. Android ADB, Watchdog, content providers and
AirPlay control do not run on LG/Samsung. Artwork/provenance stays out of runtime packages; required notices are included.

## Performance

### Work limits

- Frame callbacks stop when animation settles; listeners/timers stop on hidden screens.
- The combined home prepares up to three recent items and three recommendations, limited by actual content count.
- Each poster's cards and blurred background are filtered straight from the decoded artwork; no full-screen
  intermediate bitmap is allocated per poster. Recent-content reads load app labels only for the apps they list.
- Artwork is decoded at most 1280 px on the long side. Decoding 1920 px art at full size for a sharper wide card was
  measured on the Mi Stick and rejected: it doubled the poster's transient memory.
- A granted viewing permission is remembered for the process (Android ends the process when it is revoked).
- Unchanged artwork survives normal tab/settings/app/screensaver returns. Real memory-pressure trimming, bounded
  requests and stale-response rejection remain active.
- Hidden artwork retention budgets are 16 MiB for a single tab and 32 MiB combined. These are not total RAM guarantees
  or a hard heap limit imposed on visible posters.
- Android 9–12 avoids preparing glass textures it does not draw. Text measurements/static backgrounds are reused.
- Home layers apply fade alpha per drawing (no offscreen buffer per frame on Android 9–11). Lightweight mode removes
  shadows and screensaver motion but keeps the full frame rate. Check with `dumpsys SurfaceFlinger --latency` while
  scrolling: on the Mi Stick the drawer and other apps scroll at 60 fps.
- Network state is event-driven. RAM/storage is a one-time panel snapshot.
- Weather/content requests run in the background and coalesce. Clock mode disables content listeners.

### Screensaver artwork

An unused 1/6-sized frosted copy of each screensaver photo was removed. For a 2112×1188 photo the persistent bitmap
saving is **278,784 bytes (272.25 KiB)**. Temporary blur arrays are also avoided; peak allocation savings were not measured.
The full photo, scrims and OLED dimming remain unchanged.

Old/new production paths were compared in an isolated Android helper at 2112×1188, 720×1280 and 300×300.
Photo/scrim pixels and brightness samples matched. Black background, grey text, font and 12-hour fit were checked.
The final comparison passed without a production crash.

### Historical device baseline

Mi Stick / Android 9, warm combined home, four prepared posters:

| Measurement | Result |
| --- | ---: |
| Three home-process PSS samples (KiB) | 30,954 / 30,906 / 30,571 |
| Median PSS | 30.18 MiB |
| Prepared artwork's calculated bitmap bytes | 12,349,440 |

PSS covers only `:home`, not the TV or separate receiver. Bitmap bytes must not be added to PSS.
These are historical measurements from the earlier package identity, not measurements of the freshly renamed build,
a before/after comparison or a RAM/FPS guarantee. Content, process history and shared memory change the result.

Earlier device checks covered tab, quick/full settings, other apps, light/dark themes, preview and actual Dream returns;
four prepared posters were retained. No production crash/ANR was seen. The earlier five JVM suites included 1,600 cache and
3,200 dual-window cycles. The renamed package's welcome, TV ADB authorization, HOME/Original recovery and receiver
control were later checked on the reference Mi Stick; see [release checks](RELEASE.md).

No new Android 13+ hardware, other manufacturers' inputs or OLED burn-in prevention is claimed; Screen Mirroring was
confirmed only for the owner's session on the reference Mi Stick.
GitHub Actions runs the desktop and package checks on pushes and pull requests; consult the repository Actions page for the current result.


## Release

1. Run the build, tests and both source audits. Review the staged diff before committing.
2. Commit with your own Git identity and push to your own PureTV repository. Publishing source does not automatically publish a downloadable Release.
3. Let the first real GitHub Actions run pass; local results are not a remote CI result.
4. Build with empty credentials and sign with your production key. Keep the same key for later updates and for the optional receiver.
5. Increase Android versionCode for future releases. The current version is v1.3 / code 5; web package versions are 1.0.0.
   In-app updates (`AppUpdate`) offer a release only when its `PureTV.apk` (and `PureTV-AirPlay.apk`) has a higher
   versionCode and the same signing certificate as the installed app; keep those asset names and a plain tag.
6. Complete first-launch, HOME/Original recovery, screensaver and receiver QA for the new package IDs before production release.
7. Distribute matching complete GPL receiver source with any receiver APK, plus all required component notices.

Do not publish personal configured APKs, keys, raw logs or local device records. Without its certificate, a WGT remains unsigned.
CI has contents:read permissions and full-SHA action pins; no production signing secret, automatic publication or Pages job is included.
See [contribution rules](../.github/CONTRIBUTING.md) and [licences](licenses/NOTICE.md).
