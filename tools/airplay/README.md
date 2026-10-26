# Optional PureTV AirPlay receiver

The receiver is a separate **GPL-3.0** Android app; the launcher remains MIT-licensed.
Package: `io.puretv.airplay`. Version: `0.0.36-puretv.1`. Supplied native engine: **armeabi-v7a**.

## Install

The [v1 release](https://github.com/Heymez0/PureTV/releases/tag/v1) includes
[PureTV-AirPlay.apk](https://github.com/Heymez0/PureTV/releases/download/v1/PureTV-AirPlay.apk) and its matching
[complete GPL source](https://github.com/Heymez0/PureTV/releases/download/v1/PureTV-AirPlay-Source.zip).
Verify both with the release's **SHA256SUMS**.

1. Install **PureTV.apk** and **PureTV-AirPlay.apk** from the same release. The receiver is optional.
2. Open the receiver once and choose its name and pairing preferences. Keep the sender on the same local network.
3. Open PureTV's quick settings and select **AirPlay** with OK to start or stop it. The same control is in
   All settings → Connections. Arrow keys move focus without changing the state.
4. On an iPhone, iPad or Mac, open Screen Mirroring and choose the receiver name. Enter the TV's PIN if enabled.

Stop an older receiver before trying this edition; receiver settings are separate and are not imported.
For USB/file-transfer/ADB installation, follow the [Android guide](../../docs/INSTALL.md#android-tv--google-tv).

## Integration

Sign launcher and receiver with the same certificate. Launcher commands use
`io.puretv.airplay.permission.CONTROL` at signature level and verify the component/certificate on every command.
Only QUERY/START/STOP are accepted under `io.puretv.airplay.control.*`; no network-control port is added.
Receiver pairing/PIN settings are not overridden. Do not run multiple receiver editions together.

## Source and build

`source-manifest.json` identifies the pinned base, exact APK and matching **PureTV-AirPlay-Source.zip** hashes.
The complete source archive contains the Gradle wrapper, interface code, native-source snapshots, licences and build notes.
The patch in this folder is for review; it is not a substitute for that full source distribution.
Both files are distributed together in the v1 release; keep that pair together when redistributing the receiver.

Extract the full archive and follow its `PURETV-SOURCE-NOTES.md`. The interface build uses JDK 17+, SDK 37 and its wrapper:

```sh
./gradlew --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease
```

Full native rebuilding additionally needs Linux, NDK 27.0.12077973, CMake, Git, make and Perl; follow the archive's recipe.
The interface build/lint and unchanged ARMv7 engine-byte comparison passed. The renamed receiver was tested with
the `io.puretv` launcher on Xiaomi Mi Stick / Android TV 9: settings on/off and arrow-key navigation passed,
and the device owner confirmed successful Screen Mirroring. This verifies that device and session, not every sender or TV.
Full native rebuilding is not verified. Other ABIs, HomePod output and DRM support are not added.
Distribute the matching full GPL source alongside any receiver APK.
