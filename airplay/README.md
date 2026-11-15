# PureTV AirPlay receiver source distribution

Product package: io.puretv.airplay; version: 0.0.36-puretv.1; prebuilt engine ABI: armeabi-v7a.
Based on jqssun/android-airplay-server v0.0.36, commit a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea.
This remains a separate GPL-3.0 application. The launcher is separately MIT-licensed.

PureTV changes include its TV palette, rounded UI, translation resources and explicit launcher control.
LauncherControlReceiver accepts only QUERY, START and STOP under io.puretv.airplay.control.*.
The manifest guards it with io.puretv.airplay.permission.CONTROL at signature level. The launcher additionally
verifies component, permission and certificate on every command. No new network control endpoint is added.
The launcher can provide two opaque palette values, stored in puretv_style. Pairing/PIN preferences remain
receiver-owned and are not overridden by launcher commands. Existing legacy receiver data is not migrated.

This is a separate installation. Do not run two receiver editions together. Sign receiver and launcher with the
same certificate for quick-panel control. No signing/ADB key or personal configuration is distributed here.
English is the documentation language; existing UI translation resources remain supported.

## Interface build

Use JDK 17+, Android SDK 37 and the included Gradle wrapper. Set sdk.dir in your ignored local.properties, then:

    ./gradlew --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease

The interface-only build consumes the supplied unchanged ARMv7 libraries. Its release build/lint and native-byte
comparison passed for this identity. APK signing is performed separately with your production key.
No physical launch, first authorization, mirroring or new-identity control test is claimed for this build.

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
