# Changelog

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
