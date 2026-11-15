# PureTV AirPlay receiver source distribution

Product package: io.puretv.airplay; version: 0.0.36-puretv.4; prebuilt engine ABI: armeabi-v7a.
Based on jqssun/android-airplay-server v0.0.36, commit a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea.
This remains a separate GPL-3.0 application. The launcher is separately MIT-licensed.

PureTV changes include its TV palette, rounded UI, translation resources and explicit launcher control.
LauncherControlReceiver accepts only QUERY, START and STOP under io.puretv.airplay.control.*.
The manifest guards it with io.puretv.airplay.permission.CONTROL at signature level. The launcher additionally
verifies component, permission and certificate on every command. No new network control endpoint is added.
The launcher can provide two opaque palette values and a light/dark flag, stored in puretv_style. Pairing/PIN preferences remain
receiver-owned and are not overridden by launcher commands. Existing legacy receiver data is not migrated.

This is a separate installation. Do not run two receiver editions together. Sign receiver and launcher with the
same certificate for quick-panel control. No signing/ADB key or personal configuration is distributed here.
English is the documentation language; existing UI translation resources remain supported.

## On demand (0.0.36-puretv.2)

Start server at boot is now off by default. AirPlay then runs only after it is turned on (PureTV's quick settings,
All settings > Connections, or this app) and stops itself after 15 minutes without a connected sender
(AirPlayService.IDLE_STOP_MS); a sender cancels that timer. Turning Start server at boot on keeps the previous
always-on behaviour. Pairing/PIN preferences and the explicit launcher STOP are unchanged.

The CPU wake lock is held only while a sender is connected, or for the whole server lifetime when Start server at
boot is on; an idle on-demand receiver no longer keeps a sleeping TV awake. The idle timer does not advance while
the device is suspended.

The TV layout follows PureTV's settings: the launcher's light or dark choice (sunset/sunrise or its fixed theme),
glass navigation and content panels with a hairline rim, filled focus rows with inverted text, and the quick panel's
gold/grey switches (green before 0.0.36-puretv.4). Mirroring and video playback stay on black.

## Video output (0.0.36-puretv.3)

The mirroring decoder now renders straight into the display surface when Android allows re-pointing a decoder
(MediaCodec.setOutputSurface, Android 6+), instead of into a SurfaceTexture that a GL thread copied to the screen on
every frame. TV chips such as Amlogic then show the video on their video plane without a GPU copy. The GL pipeline
remains for the other cases: no display (the decoder keeps running), a display returning while the sender is still
(it repaints the last frame, and the decoder takes the display over with the next frame), and decoders that reject
setOutputSurface (the pipeline is used for the rest of the process).

Decoded frames are released from their own thread as soon as they are ready; before, a frame waited for the next
input packet, so the last frame of a change could stay hidden while the sender's screen was still. Scheduled
presentation (a developer option, off by default) re-anchors when a frame would be more than 100 ms ahead or 50 ms behind (clock drift, stalls).
A codec error seen while draining resets the codec at the next input, like a feed error.

0.0.36-puretv.3 was built (interface only; the six ARMv7 engine libraries are byte-identical to 0.0.36-puretv.2) and
installed on the Mi Stick: server start/stop from PureTV's quick settings and no idle wake lock were checked,
and the device owner confirmed Screen Mirroring from an iPhone.

The receiver's name is now PureTV (the default was "Android AirPlay"): senders list the TV as PureTV. Every earlier
install, including one with a name typed by its owner, is renamed to PureTV once when the app first starts after the
update; a name chosen after that is kept.

Start server at boot can also be switched from PureTV (All settings > Connections > Keep AirPlay on): the signature-
protected launcher receiver accepts ALWAYS_ON and ON_DEMAND, which only set that preference, and QUERY reports it as
"always". A running receiver applies the change at once (idle stop and wake lock), also when it is switched in this
app's own settings. The versionCode is 39 (the first v1.3 upload was 38 under the same version name).

When the last sender stops sharing (mirroring, video or audio), the receiver screen goes to the TV's home screen after
1.5 seconds instead of staying open; a sender that reconnects within that time keeps it. In picture-in-picture only the
small window closes. versionCode 40.

## Interface build

Use JDK 17+, Android SDK 37 and the included Gradle wrapper. Set sdk.dir in your ignored local.properties, then:

    ./gradlew --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease

The interface-only build consumes the supplied unchanged ARMv7 libraries. Its release build/lint and native-byte
comparison passed for this identity. APK signing is performed separately with your production key.
0.0.36-puretv.1 was checked on a Xiaomi Mi Stick / Android TV 9 on 2026-10-26 (settings start/stop, quick-panel stop,
rejected unprivileged control, owner-confirmed Screen Mirroring). 0.0.36-puretv.2's on-demand start and 15-minute
idle stop were checked on the same device on 2026-10-28, as were the light/dark layout (including a theme change while
open) and the wake lock: none while idle on demand, held with Start server at boot on, released when stopped. Taking the
lock when a sender connects was not exercised (no sender was used).

## Full native rebuild

Pinned UxPlay, FFmpeg, libplist and openssl-cmake snapshots, original licences and included patches are retained
under app/src/main/cpp/third_party. URLs, commits and source hashes are in native-source-provenance.json.
The exact OpenSSL 3.4.4 release source is in native-sources.

app/build-native.gradle.kts preserves the full native recipe. Use Linux, Git, make, Perl, Android NDK
27.0.12077973 and CMake 3.22.1+. Back up app/build.gradle.kts, replace it with build-native.gradle.kts,
and move prebuilt jniLibs aside before compiling. Restore executable permissions on gradlew and FFmpeg configure
if extraction removed them. Initialize/commit the supplied pristine UxPlay snapshot in a local Git repository
because applyUxplayPatches uses git checkout/apply, then build with the supplied patches.
OpenSSL's ExternalProject can use the supplied exact archive instead of its configured mirror for offline work.
A full native engine rebuild has not been verified; only the Android/Kotlin interface was rebuilt.

The original upstream README is retained as UPSTREAM-README.md for protocol/source documentation, not as a
PureTV feature validation report. Any upstream CI files in this source snapshot belong to that source snapshot;
they are not installed as this launcher's workflow. Configure your own release process before enabling them.
Distribute this complete matching source archive alongside any receiver APK, not merely a patch/upstream link.
HomePod audio output and DRM support are not implemented by these changes.
