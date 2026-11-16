# Changelog

## v1.4.1

A fix release. Built, covered by the regression suite and checked on a Mi Stick (Android TV 9): two complete fresh
installs through the welcome guide (with the viewing permission allowed and refused, the ADB prompt cancelled and then
accepted, AirPlay installed and skipped), reinstalling over a configured TV, and Original mode restoring the factory
home screen. The remote's Home and apps buttons inside Remote button test, and All Apps rising from the apps button,
need a physical remote and were not checked there.

- **Reinstalling no longer half-works:** after an uninstall the TV's own home screen stays off, so Home already opened
  PureTV and the welcome guide treated setup as finished: it opened on its last page and Enable PureTV did nothing.
  PureTV counts as set up only when its own state says so too (`WelcomeFlow.setUp`).
- **Original mode after a reinstall:** the new install did not recognise the TV's own home screen and rewrote the
  record of apps it had turned off without it, so Original left the TV with no home screen. Apps an earlier install
  turned off and that are still off now stay on the record and are turned back on (`HomeRecord`).
- **The guide keeps its last steps:** enabling PureTV measured the home screen and then opened it over the guide, so
  the AirPlay step never showed. A switch the guide asked for now returns to it.
- **Weather city in the guide:** without a city the weather came from the build's default coordinates. A new step
  suggests the city in the TV's time zone, keeps the current one, and searches by name (only the name is sent).
- **Viewing permission in the guide:** choosing a layout with viewing content now asks for Android's TV listings
  permission; refusing it leaves the clock layout, as in Settings.
- **AirPlay:** its rows moved to one page, Settings → Connections → AirPlay. The receiver page no longer reinstalls the
  same or an older receiver ("already installed and up to date").
- **Remote button test:** Home, the apps button and Settings are shown in the test instead of acting (while PureTV is
  the default home screen; the key service lets go of them within 10 seconds if the test stops), and the way to leave
  is shown once, in gold. All Apps opened by the apps button rises like the shortcut's.
- **Settings:** Home screen apps and Other apps were removed from Settings → Home screen; a long press on a home
  screen app still adds, removes and moves it, and Other apps stays in the bottom drawer. Hints now stop inside their
  panel instead of running past it.
- **Text:** the guide warns that the screen may switch back and forth during setup. Turkish wording was reviewed (one
  informal voice, a wrong "Let's try again"), ADB error messages are translated, and new text is in all 24 languages.

## v1.4

Built, covered by the regression suite and checked on a Mi Stick (Android TV 9) in the dark and the light theme:
settings, home (content and clock layouts), quick panel and bottom drawer. Not yet seen there: the weather line of the
clock layout, the PIN keypad, the text field and the welcome guide. The screenshots in `docs/media` still show the
previous look.

- **One look, from the icon:** obsidian surfaces, paper-white text and a single gold accent for what is on, selected
  or in progress (`Look`). The focus is the same bright surface with a soft shadow on every screen; the quick panel
  and the long-press menu used a different one from the settings.
- **A backdrop that follows the day:** without a title's photo, the home screen, the settings and the welcome guide sit
  on a deep surface lit by a warm and a cool light whose colours change through morning, day, evening and night
  (`Ambient`). It is computed once into a small dithered bitmap and stretched, so it costs one textured rectangle and
  shows no banding. On Android 13+ the glass reads this backdrop.
- **Home screen:** the clock layout shows a small tracked date, the time large and light, and the weather on one line
  with the city; the header no longer repeats the time, date and weather beside it. The open content tab carries a
  gold mark, watch progress is gold, the focused tile takes a bright ring at night too, and black tiles get a hairline
  of light on the smoked shelf.
- **Glass on older TVs (Android 9 to 12):** panels are lit from above (a faint sheen and a rim brighter at the top)
  instead of a flat fill with an even outline. Lightweight mode keeps the plain panels.
- **Settings:** each category has its symbol and the open one a gold mark; on/off values are switches and the choice in
  use a gold check; rows that only report something (RAM, IP, version) are quieter; long lists show where you are.
- **Settings, regrouped:** rows now sit where you would look for them. Detailed weather is with the rest of the
  weather; the screensaver and its photos are together under Appearance; App lock is next to Hidden apps under Home
  screen; Device mode is under Device and performance; the AirPlay rows are together, before the network ones; System
  and about starts with what you change and ends with what you only read. The redundant Refresh status row is gone
  (the values refresh when the screen opens and after freeing memory).
- **AirPlay inside PureTV.apk:** a release build carries the signed receiver as a compressed asset
  (`build.py --receiver`), so Connections → AirPlay receiver installs it without a download; Android still asks once
  to confirm. A build without it, or a carried copy that fails the package and certificate check, downloads from the
  release as before. The receiver stays a separate GPL-3.0 app. Receiver `0.0.36-puretv.4` (on the Mi Stick: screens and start/stop checked, return to the home screen after
  mirroring confirmed by the device owner; the in-app install was not tried there)
  leaves for the home screen 0.6 s after sharing ends, from the engine's own events instead of a polled connection
  count, wears the current look (gold switches, lit panels, two-light backdrop) and is smaller.
- **The remote's apps button works:** Android sends that button only to the TV's own launcher, which is off in custom
  home mode, so it did nothing. PureTV's key service now answers it with All Apps (Xiaomi remotes and any remote that
  reports the key; custom home mode). Confirmed by the device owner on a Mi Stick remote on 2026-11-15.
- **Remote button test, and jobs for buttons:** Back no longer leaves the test (hold it for two seconds; Home still
  leaves). A pressed button can be given a job there (All Apps, quick settings, settings or an app) and nothing
  changes until Save changes. OK, Back, the arrows, Home, power and volume can never be reassigned.
- **Setup guide:** a step to choose the home screen layout with a small preview of each, an AirPlay step when setup
  finishes (installed without a prompt in custom home mode, from the copy inside PureTV), and the USB debugging step
  says why it is asked for. Its buttons were English outside Turkish and are now translated.
- **Other apps:** an app added to the home screen is no longer listed there too.
- **Header:** a hairline separates the weather from the date.
- **All Apps shows everything:** the home screen's apps, then the other apps, then the apps you hid, dimmed and named
  so. The long-press menu adds an app to the home screen or removes it, and hides or shows it; Force stop is gone.
  PureTV's own AirPlay receiver is never listed as an app (it opens from Connections); no other app is treated so.
- **Screensaver:** light numerals, a tracked uppercase date and a dimmed gold dot and label, still on black.
- **Setup guide from Settings:** Back walks the steps back before it closes, also after setup is done.
- **Default home screen:** the choices are PureTV home screen and TV's original home screen, and the hint says how to
  go back to the TV's own.
- **No white flash:** settings and the other pages open without a starting window.
- **Translations:** 160 texts (the setup guide, Television mode, TV inputs, AirPlay help, accessibility phrases) had no
  translation outside Turkish; they are now in all 23 languages (18 of them machine-translated in one pass and
  checked only for structure, not proofread by a speaker).
- **Weather:** the header shows a small icon and temperature beside its clock (no city); the clock layout has one small
  line under the clock. Weather information switches both. Detailed weather below the shortcuts is unchanged.
- **Quieter glass on Android 9 to 12:** the sheen and the top of the rim on dark glass are about half as bright; the
  header buttons looked washed out towards the top.
- **Television mode:** on a TV with no tuner or TV app, the corner keeps TV Settings instead of a Live TV button
  that could only say it can't open. The corner button and the page it opens share one name (TV inputs), Live TV has
  the same icon everywhere (it shared AirPlay's in the inputs row), and the remote test names the HDMI port. A Live TV
  or input choice for "On TV start" left from Television mode no longer applies in box mode, and the settings show
  Home screen for it. Removed an unused shortcut list.
- **Move sound:** at an edge of the Other apps grid the focus doesn't move, and the sound no longer plays.
- **Popups and welcome guide:** the quick panel, long-press menu, PIN keypad and text field share the settings look;
  the welcome guide shows PureTV's icon and its four steps on one line.

Under the hood:

- Flat glass ignored its opacity, so both the day and the night panel were drawn in full during the day ⇄ night
  transition on older TVs.
- The clock layout built its high/low text per language in code; it now uses the catalogues like the forecast card.
- Removed code that could no longer run: the old hero (hourly picks) and the corner capsule, including a shortcut list
  that was still loaded on every catalogue refresh and never shown.
- The network status colour was decided in four places with two different rules; it is now one
  (`NetworkStatus.color`). An unused constructor argument of every settings row is gone.
- Each settings row now carries its own value; the values were written by row number in a separate method, which broke
  silently whenever a row was added or moved.

## v1.3

- **Keep AirPlay on** (All settings → Connections): AirPlay starts with the TV and stays on instead of stopping after 15
  minutes without a sender.
- **AirPlay receiver 0.0.36-puretv.3:** senders list the TV as **PureTV** (was "Android AirPlay"; earlier installs, also
  ones with a typed name, are renamed once; a name chosen afterwards is kept). Mirrored video is decoded straight into the display surface (no GL copy per frame; Amlogic chips
  use their video plane), and each frame is shown as soon as it is decoded instead of when the next one arrives.
  When sharing ends, the receiver returns to the home screen instead of staying open. Screen Mirroring from an
  iPhone confirmed on the Mi Stick. Engine libraries unchanged; full GPL source and review
  patch included.

- **Light theme header:** the clock/weather pill and the buttons opposite it were half-clear white on a light,
  content-tinted background and hard to see; by day they are now fuller, with a fine rim and a soft shadow. Dark theme
  unchanged.
- **Clearer settings:** explanations for Lightweight mode, Weather information, Device mode, Default home screen,
  Screensaver and Status and notices; hints no longer say "custom home mode" without explaining it, but "needs PureTV as
  the default home screen"; Optimise apps during the screensaver is described without compiler terms.

- **Faster on TVs:** Android installs an app with only profiled code compiled, and the background compile that should
  follow needs idle-and-charging time a TV stick rarely gets. In custom home mode PureTV now compiles itself and the
  AirPlay receiver ahead of time (`cmd package compile -m speed`) once per installed build: a minute after an update,
  three minutes after turning custom home mode on, and after a start if a build is still uncompiled. Measured on the Mi
  Stick (Android TV 9) while moving around the home screen: frames over 16 ms 61 % → 25 %, 90th percentile 31 → 18 ms;
  home cold start about 1030 → 950 ms. About 3 seconds of work per build.
- **Faster apps (Netflix, YouTube and others):** Android recompiles apps from how you use them only after an hour of
  idle time while charging, which a stick that sleeps with the TV never gets, so they kept their install-time compile.
  Device and performance → Optimise apps during the screensaver (on by default, custom home mode): ten minutes into
  the screensaver PureTV runs Android's own profile-guided compile for the apps you installed, one at a time, each again
  after an update or a week, and stops when you press a key. Measured on the Mi Stick: YouTube's cold start
  1314 → 1159 ms; Netflix, whose interface runs mostly in native code, barely changes (about 12–14 s of work per app).
- **Smoother first seconds:** when the posters arrive after the home screen starts, the background cross-fade no longer
  redraws the header, shelf, drawer and Other apps on every frame on TVs without the Android 13 glass (their panels
  don't show the photo). On the Mi Stick, moving along the shelf right after a start dropped frames (20–24 ms) in 4
  of 11 runs before; in 9 runs after, only the very first frame of the start was slow.
- **AirPlay control after a force stop:** if the AirPlay receiver was force-stopped in TV Settings, Android dropped
  PureTV's start/stop/status commands until the receiver was opened once; they now reach it (still only PureTV, by
  signature permission).
- **Translations:** the texts added since v1 (updates, app lock, hidden apps, sleep timer, memory, screensaver photos,
  the new settings hints and others, 129 in all) are now in all 23 other languages instead of English.

## v1.2

- **Background from content** (Appearance): on by default; off keeps the theme's plain background in the recent and
  recommended layouts instead of colours from the first card. Included in the settings backup.
- After PureTV updates itself in custom home mode, the new version removes the copied APK from `/data/local/tmp`
  (the old process ended during the install and left it there).

## v1.1

**Signed with a new key.** The v1 signing key is no longer available, so PureTV and PureTV AirPlay v1.1 are signed with a
new certificate (SHA-256 `808dd32ee3d027606fbaa586c71755ae2e97957182d7f565f8c777db15c93677`). Android can't update
across keys: if v1 is installed, back up your settings (System and about → Back up settings), uninstall PureTV and
PureTV AirPlay, install v1.1 and restore. Later releases update in place, also from inside PureTV.

Security and fixes:

- **Security:** install results (updates, AirPlay receiver) could be faked by another app on Android 12 and older, and
  the faked result could make PureTV open an activity. Results now need a signature permission only PureTV holds, and
  only the system installer's confirmation screen is ever opened.
- After restoring a settings backup on a new install, viewing information was on without Android's TV listings
  permission, so no app content appeared. The restore now asks for it, and OK on Content apps → TV viewing information
  asks for it instead of turning the feature off.
- The update check no longer fails, or downloads the same release every day, when a release is signed with another
  key; a newer release signed that way is reported once.
- App lock: a PIN shorter than 4 digits can't be confirmed (it no longer counts as a wrong try), and a held number key
  types one digit.
- The screensaver no longer redraws a still photo about ten times a second in lightweight mode.
- "Custom home mode?" is answered from the state file only when it changed (menus and settings rows on the main thread).

New, all optional and off until chosen:

- **Free up memory:** the Free up button on the RAM row of quick settings, or Device and performance → Free up memory, closes apps waiting in
  the background (custom home mode: the TV's shell; otherwise Android's own call per app). The app on screen and AirPlay
  keep running.
- **Automatic memory cleanup:** Device and performance → Automatic memory cleanup (off by default) frees memory when
  you return to the home screen with less than 15 % left, at most every 15 minutes.
- **Sleep timer:** Quick settings → Sleep timer puts the TV to sleep after 30, 60, 90 or 120 minutes, with a notice a
  minute before (custom home mode: the TV's own sleep key through the existing ADB grant).
- **Screensaver burn-in guard:** the clock, date and photo caption move a few pixels each minute.
- **City search** opens in the settings popup style instead of the system dialog.
- **Hidden apps:** hold OK on an app in Other apps → Hide app; Home screen → Hidden apps shows it again.
- **On TV start:** Home screen → On TV start opens an app, Live TV or an input once, in the first minutes after the TV
  starts (not after an update or a crash restart).
- **Chance of rain:** the detailed weather shows an hour's chance of rain from 30 % (Open-Meteo, same request).
- **Uninstall / Force stop:** in an app's long-press menu. Uninstall only for apps you installed, with Android's
  confirmation; Force stop in custom home mode, never for protected packages.
- **Screensaver photos:** Appearance → Screensaver photos shows your landscape photos from the TV and USB drives behind
  the clock (photos permission asked once; nothing leaves the TV).
- **App lock:** Device and performance → App lock asks a PIN (salted and hashed) for chosen apps opened from PureTV,
  always or in the evening; the same PIN guards its settings, hiding/uninstalling and the default-home switch. A simple
  household lock: apps opened another way are not locked. Never included in the settings backup. The PIN is typed on
  a keypad in the settings popup style (arrows and OK, or the remote's number keys); digits show only as dots.
- **Updates from GitHub:** System and about → Updates checks this repository's latest release and installs PureTV
  (and the AirPlay receiver, when installed) only when the file is the expected package, signed like the installed app
  and strictly newer — never a downgrade or another app. Daily, on Wi-Fi/Ethernet: **Notify** (default, a home-screen
  notice), **Install automatically** (custom home mode: the TV's own `pm install` through the existing ADB grant, while
  the TV is idle; otherwise it notifies) or **Off**. Manual installs elsewhere use Android's confirmation. Releases
  must raise `versionCode` to be offered.
- Screensaver photos now decode at most at twice the screen size before scaling (a 12 MP photo or a 4K catalogue image
  needs about a quarter of the transient memory).

- Build each poster's cards and blurred background straight from the decoded artwork, without a full-screen
  intermediate bitmap or card-sized cutouts (lower transient memory and CPU while posters are prepared).
- Load app labels only for the apps that recent content lists, instead of every launchable app on each read.
- Skip the home screen's 15-second notice check while the shared HOME state file is unchanged.
- Turn off Xiaomi's Alphonso ACR service (`tv.alphonso.alphonso_eula`) in custom home mode, like the existing Anoki ACR
  entries; the usual protected, TV-input and Wi-Fi-owner checks apply and **Original** turns it back on.
- Correct the Turkish guide's repository/release wording and the development guide's test count and QA status.
- Install the optional AirPlay receiver from PureTV (welcome guide and Connections): download from this repository's
  latest release, check package and certificate, then Android's own confirmation.
- AirPlay receiver 0.0.36-puretv.2: no longer starts with the TV by default and stops after 15 minutes without a sender;
  Start server at boot keeps it always on. Without a sender it no longer keeps the processor awake. Its screens follow
  PureTV's light/dark settings look (glass panels, filled focus, green switches). Engine libraries unchanged; matching
  full source and review patch included.
- Back up and restore settings (System and about); never viewing history, keys or HOME state.
- Optional voice-assistant switch in Device and performance (custom home mode; Original turns it back on).
- Remember a granted viewing permission for the process instead of asking the system on every check.
- Smoother home scrolling on Android 9–11: fading home layers no longer use a full offscreen buffer per frame, and the
  detailed-weather card is no longer re-rendered every minute. Lightweight mode no longer halves the home and settings
  frame rate (it keeps its other savings). Measured on the Mi Stick: home scrolling went from 30 to 60 fps with
  lightweight mode on, without dropped frames.
- Add `tools/device_qa.py` (read-only snapshot, native poster tests and timing) and a feature regression suite.
- Fix: reading the shared HOME state file while the main process replaced it with a longer one (a new notice) could
  return the defaults, so the home screen skipped that notice until the next change. Reads now run to the end of the
  file.
- Fix: after an app update, turning the remote-buttons service back on ran beside the custom-home check and could
  overwrite its record of turned-off apps. It now waits for the other mode jobs.
- Fix: mode-switch progress and notices could appear in the previous language after the language was changed, until
  the background process restarted. Each mode job now rereads the language first.
- Settings backup's Download copy no longer creates an ADB key when none exists; mode jobs use fixed job IDs; TMDB
  image file names are checked; the recommendations status file is replaced whole; a missing hourly weather value
  skips that hour instead of the whole forecast.
- No change to the 3+3 poster working set, HOME/Original recovery logic, settings activation or AirPlay control.

## v1 — final release

- Replace platform icons and the Android TV banner with original charcoal/gold PureTV artwork.
- Prepare shelf logos before drawing; retain them across unchanged layouts.
- Cull off-screen rows with a guard row, bound the shadow-mask LRU and precompute spring constants.
- Schedule photo screensaver callbacks at the existing render cadence; retain the clock's low-frequency callback.
- Preserve the dual 3+3 poster working set and ordinary-return artwork retention.
- Add readable bundled licence texts, component hashes, the full CC0 notice and service credits.
- Set product versions to v1 / Android code 2 and 1.0.0 on the experimental web ports.
- Add cache-boundary, motion-equivalence, culling and licence/package regressions.
- Verify fresh `io.puretv` welcome/setup and HOME after reboot on Mi Stick / Android TV 9.
- Verify renamed receiver settings control and owner-confirmed Screen Mirroring; distribute its matching full GPL source.

See [release checks](RELEASE.md) for verification and testing limits.

## v1 — easier installation

- Add one-line Claude Code/Codex installation prompts under Install.
- Provide a signed Android APK download with USB, file-transfer and ADB instructions.
- Separate LG and Samsung setup/build/sign/install steps and retain their experimental status.
- Add a Turkish reader guide while keeping the default README in English.

## v1 — preview gallery layout

- Restore the original PureTV title banner.
- Feature the combined home screen at full width, with the clock home below it.
- Arrange quick settings, the Shortcuts shelf and settings in a three-column feature row.
- Add two unedited Mi Stick captures; keep runtime packages and application behavior unchanged.

## v1 — preview and localization update

- Replaced interface illustrations with three genuine English Mi Stick screenshots without viewing-history content.
- Made native-only hardware testing and experimental LG/Samsung status explicit.
- Documented 24 language catalogues and English fallback for untranslated help text.
- Filled core menu/greeting labels, corrected English source extraction and repaired English catalogue text.
- Made RAM percentages and resource sizes follow the app language with cached formatting context.
- Added localization regressions; no extra background polling, service or receiver change.

## v1 — source preview

- PureTV branding and package IDs; a welcome guide with Box/stick and TV profiles.
- Clock, recent-content and combined home layouts with source selection and content hiding.
- Compact quick settings, unified settings, TV inputs and remote-button infrastructure.
- Prepared 3+3 artwork retention, stopped hidden-screen listeners and an older-GPU drawing path.
- Plain clock typography and a black/dimmed screensaver without the unused blurred photo copy.
- Optional separate, signature-controlled AirPlay receiver.
- LG webOS and Samsung Tizen web packaging, platform APIs and installers.
- English illustrated documentation, portable builds and regression/source checks.
- PureTV MIT notice with preserved third-party notices; optional receiver remains GPL-3.0.
- Simplified repository: two main guides, developer utilities under tools/, and no build output in source archives.

The PureTV IDs form separate installations; see [upgrade notes](INSTALL.md#identity-and-upgrades).
