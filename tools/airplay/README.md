# Optional PureTV AirPlay receiver

The receiver is a separate **GPL-3.0** Android app; the launcher remains MIT-licensed.
Package: `io.puretv.airplay`. Source version: `0.0.36-puretv.3`. Supplied native engine: **armeabi-v7a**.
[v1.3](https://github.com/Heymez0/PureTV/releases/tag/v1.3) distributes `0.0.36-puretv.3` (v1.1 and v1.2: `0.0.36-puretv.2`), signed with
PureTV's new key (v1 distributed `0.0.36-puretv.1` with the previous key).

## Install

**From PureTV:** the welcome guide's last page offers **Add AirPlay (optional)**, and the same installer is in
All settings → Connections → **AirPlay receiver**. PureTV downloads `PureTV-AirPlay.apk` from the latest release of this
repository, checks that it is `io.puretv.airplay` signed with the same certificate as the launcher, and Android asks you to
allow installs from PureTV and confirm. Nothing is downloaded or installed without that choice.

**By hand:** download [PureTV-AirPlay.apk](https://github.com/Heymez0/PureTV/releases/latest/download/PureTV-AirPlay.apk)
and its matching [complete GPL source](https://github.com/Heymez0/PureTV/releases/latest/download/PureTV-AirPlay-Source.zip)
from the same release, verify them with **SHA256SUMS** and install the APK after **PureTV.apk**.

Then:

1. Senders list the TV as **PureTV** (from `0.0.36-puretv.3`, which renames earlier installs once). Open the receiver
   to change the name or the pairing preferences. Keep the sender on the same local network.
2. Open PureTV's quick settings and select **AirPlay** with OK to start or stop it. The same control is in
   All settings → Connections. Arrow keys move focus without changing the state.
3. On an iPhone, iPad or Mac, open Screen Mirroring and choose the receiver name. Enter the TV's PIN if enabled.
   When you stop sharing, the TV returns to its home screen (from `0.0.36-puretv.3`).

From `0.0.36-puretv.2`, AirPlay runs **only when you turn it on**: it no longer starts with the TV, and it stops itself
after 15 minutes without a connected sender. To keep it always on, turn on **Keep AirPlay on** in PureTV (All settings → Connections) or **Start server at boot**
in the receiver.
While no sender is connected, the on-demand receiver no longer holds the processor awake, so a TV in standby can sleep.
Its screens follow PureTV's settings: the same light or dark appearance, glass panels, filled focus and green switches.

Stop an older receiver before trying this edition; receiver settings are separate and are not imported.
For USB/file-transfer/ADB installation, follow the [Android guide](../../docs/INSTALL.md#android-tv--google-tv).

## Integration

Sign launcher and receiver with the same certificate. Launcher commands use
`io.puretv.airplay.permission.CONTROL` at signature level and verify the component/certificate on every command.
Only QUERY/START/STOP are accepted under `io.puretv.airplay.control.*`, plus ALWAYS_ON/ON_DEMAND from
`0.0.36-puretv.3`, which only switch Start server at boot (PureTV: Connections → Keep AirPlay on); no network-control
port is added.
Receiver pairing/PIN settings are not overridden. Do not run multiple receiver editions together.

## Source and build

`source-manifest.json` identifies the pinned base and the matching **PureTV-AirPlay-Source.zip** hash; the receiver APK
hash is added once the APK is signed with the production key. The complete source archive contains the Gradle wrapper,
interface code, native-source snapshots, licences and build notes. The patches in this folder are for review
(`puretv-v0.0.36.patch`: upstream → `0.0.36-puretv.1`; `puretv-0.0.36-puretv.2.patch`: on demand, wake lock and theme; `puretv-0.0.36-puretv.3.patch`: direct video output,
immediate frame release and the PureTV name); they are not
a substitute for that full source distribution. Keep each receiver APK together with its matching source archive.

Extract the full archive and follow its `PURETV-SOURCE-NOTES.md`. The interface build uses JDK 17+, SDK 37 and its wrapper:

```sh
./gradlew --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease
```

Full native rebuilding additionally needs Linux, NDK 27.0.12077973, CMake, Git, make and Perl; follow the archive's recipe.
The interface build passed for `0.0.36-puretv.2` and its six ARMv7 engine libraries are byte-identical to
`0.0.36-puretv.1`. That earlier build was tested with the `io.puretv` launcher on Xiaomi Mi Stick / Android TV 9:
settings on/off and arrow-key navigation passed, and the device owner confirmed successful Screen Mirroring. The
on-demand start and 15-minute stop, light/dark layout and idle wake-lock behaviour of `0.0.36-puretv.2` were checked on
the same device with a test key; a sender connection was not part of that check. `0.0.36-puretv.3` (interface build,
engine libraries byte-identical) decodes mirrored video straight into the display surface, falling back to the GL
pipeline where needed; on the same device it was started and stopped from PureTV's quick settings and the device owner
confirmed Screen Mirroring from an iPhone. Full native rebuilding is not verified. Other ABIs, HomePod output and
DRM support are not added. Distribute the matching full GPL source alongside any receiver APK.
