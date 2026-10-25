# Install PureTV

[Easy to install with an assistant](../README.md#easy-to-install) · [Android TV / Google TV](#android-tv--google-tv) · [LG webOS](#lg-webos) · [Samsung Tizen](#samsung-tizen) · [Compatibility](#compatibility)

## Android TV / Google TV

Use the signed [PureTV.apk download](https://github.com/Heymez0/PureTV/releases/download/v1.0.0-preview/PureTV.apk)
from the [v1 preview release](https://github.com/Heymez0/PureTV/releases/tag/v1.0.0-preview). Requires Android 9+.
Sign in with repository access while the project is private. Download **PureTV.apk**; the automatic Source code ZIP
contains source files for developers.

Choose **one** installation method:

### Option 1: USB drive

1. Download `PureTV.apk` on your computer.
2. Copy the APK to a USB drive and connect the drive to the TV/box.
3. Open the drive with a file manager on the TV and select `PureTV.apk`.
4. If Android asks, allow this file manager to install apps, then select **Install**.
5. Open PureTV from the applications list and follow [First launch](#first-launch).

For a stick without a usable USB storage connection, use file transfer or ADB below.

### Option 2: File transfer to the TV

1. Download `PureTV.apk` on your computer or phone with GitHub access.
2. Use your existing local file-transfer app to send the APK to the TV on the same network.
3. Open the transferred APK with the TV file manager and select **Install**. Allow that installer source if Android asks.
4. Open PureTV and follow [First launch](#first-launch).

### Option 3: ADB from a computer

1. Install Android platform-tools on your computer and download `PureTV.apk`.
2. Enable the TV's developer/ADB access and find its local IP in network settings.
3. Open a terminal in the folder containing the APK. Replace `TV_IP` with your TV's address:

```sh
adb connect TV_IP:5555
adb install -r PureTV.apk
```

Approve the connection prompt on the TV. The computer and TV must be on the same local network.
Some firmware uses wireless pairing or a different port; use its supported pairing instructions instead of port 5555.
Open PureTV from the applications list after installation. For your own signed build, see [build and signing](DEVELOPMENT.md#build).

### First launch

1. Open PureTV from the applications list.
2. Choose **Box/stick** or **TV** in the welcome guide; change it later under All settings → Home screen → Device mode.
3. Review the explained changes and authorize the app's ADB request on the TV.
4. Confirm the switch on the TV. **Original** remains the route back to the manufacturer's HOME.

<img src="media/remote.svg" width="100%" alt="Arrow keys, OK, Back and long-press OK navigation">

Arrow keys move focus; **OK** activates the selected item. Left/right do not toggle settings.
Long-press an app card for actions or **Move on shelf**. The All apps shortcut stays fixed.
Scroll down to reach other applications.

Quick settings is at the top right. All settings opens from that panel or the application list.
Layout and profile are under Home screen; viewing-information sources and hidden items are under Content.
Turning viewing information off leaves only the clock. Appearance and clock/weather have their own categories.
Connections contains AirPlay control/help and system network/Bluetooth links. Device and performance contains resource
snapshots and optional lightweight mode. Remote buttons contains supported mappings and the key test.

TV inputs appear only in TV mode when the device provides them. A streaming stick does not gain physical HDMI inputs
by changing profile. Existing TV input/CEC apps handle inputs; PureTV does not replace tuner, picture or audio settings.
Optional receiver installation and same-certificate requirements are in the [AirPlay guide](../tools/airplay/README.md).

### Restore the original HOME

Select **Original** under All settings → System and about → Default home screen. Finish restoration before uninstalling.
The saved HOME/screensaver and disabled promotional packages are restored; system apps are not uninstalled.
Watchdog restores the original HOME after three crashes within five minutes.

Do not remove an active replacement HOME or clear its data. Manual recovery must use your own TV's saved original HOME
and package records, not another device's commands. Preserve network, accounts, picture/audio and tuner components.

### Identity and upgrades

This edition uses `io.puretv` and the optional receiver uses `io.puretv.airplay`. They install separately from earlier
package IDs with fresh settings; HOME state, ADB approval and receiver preferences are not imported automatically.
First use the older launcher's **Original** option to restore the manufacturer's HOME and screensaver. Keep its data
until restoration completes, then install PureTV and follow its welcome guide. Do not run two receiver editions together.
Updates of `io.puretv` require the same signing certificate and an increasing versionCode.

## LG webOS

This port is **experimental and hardware-untested**. It uses an IPK package.

**You need:** Python 3, OpenSSH (`ssh`, `ssh-keygen`), PureTV source, and the LG Developer Mode app on your TV.
Download the repository source ZIP using **Code → Download ZIP**, extract it and open a terminal in its main folder
(the folder containing `README.md`, `webos/` and `tizen/`).

1. Install [LG Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app) on the TV and sign in with your LG developer account.
2. Turn on **Dev Mode Status**, complete the TV restart, then turn on **Key Server**. Note the TV IP and passphrase.
3. In the source folder, build the IPK:

```sh
python webos/build.py --generic
```

4. Install it. Replace `TV_IP` with the address shown by the TV:

```sh
python webos/install.py TV_IP
```

5. Enter the TV's passphrase in your own terminal when asked. The installer copies and opens PureTV.

The unlocked device key is kept in your user's `.ssh` folder. Updates reuse it. The first SSH host key is remembered;
unexpected changes are rejected. Keep keys/passphrases private. Track Developer Mode expiry and use **EXTEND** in time.
The manufacturer's HOME remains available. Remote number shortcuts depend on the model.

<details>
<summary>Already using the official webOS CLI?</summary>

Configure your TV in the [webOS CLI](https://webostv.developer.lge.com/develop/tools/cli-dev-guide), then use:

```sh
ares-install --device TV_NAME PureTV-webos.ipk
ares-launch --device TV_NAME io.puretv
```

</details>

Remove PureTV through the TV's app management, or use `python webos/install.py TV_IP --uninstall`.

## Samsung Tizen

This port is **experimental and hardware-untested**. It uses a signed WGT package.

**You need:** Python 3, PureTV source, and Tizen Studio's signing tools with a TV certificate profile.
Download **Code → Download ZIP**, extract it and open a terminal in the source folder containing `tizen/`.

1. Follow [Samsung's device guide](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html): on the TV open **Apps**, press **1 2 3 4 5**, enable **Developer Mode**, enter the computer's IP as **Host PC IP**, then restart.
2. Create a [Samsung TV certificate profile](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html) in Tizen Studio. Complete the DUID/account/device-permission steps on your computer. Keep the same author certificate for updates.
3. Build and sign. Replace `YOUR_PROFILE` with that certificate profile's name:

```sh
python tizen/build.py --generic --sign YOUR_PROFILE
```

4. Install and open it. Replace `TV_IP` with your TV's local address:

```sh
python tizen/install.py TV_IP
```

The Python installer implements the SDB connection itself. Omitting `--sign` creates an **unsigned inspection package**;
TV installation requires the signed package. The manufacturer's HOME stays available. Autorun Last App depends on firmware.

<details>
<summary>Prefer the official SDB/Tizen tools?</summary>

```sh
sdb connect TV_IP
tizen install -n PureTV-tizen.wgt -t DEVICE_SERIAL
tizen run -p PureTVapp1.PureTV -t DEVICE_SERIAL
```

</details>

Remove PureTV through TV app management, or use `python tizen/install.py TV_IP --uninstall`.

## Compatibility

| Feature | Android TV / Google TV | LG webOS / Samsung Tizen |
| --- | --- | --- |
| App shelf, launching, editing, weather, themes and translations | Yes | Yes |
| Clock/apps without API keys | Yes | Yes |
| Optional TMDB catalogue | Supported infrastructure | With your own key |
| Android recent/app-recommended content and content hiding | When apps publish it and permission allows | No |
| Prepared 3+3 posters, quick panel, RAM snapshot and lightweight mode | Yes | No Android implementation |
| Separate optional AirPlay receiver | ARMv7 Android app | Not included |
| Physical input selection | TV profile + TvInput API | LG Luna / Samsung VIDEOSOURCE and tvwindow APIs |
| Replace HOME, Original recovery and Watchdog | Android-specific | Manufacturer HOME remains |

Android targets Android 9+ / API 28. Google TV uses the same APK with firmware-dependent HOME/ADB policies.
LG targets webOS 3+; Samsung targets Tizen 3+ with `PureTVapp1.PureTV`. Older NetCast/Orsay are not supported.
Older web engines use ES5 and framework-free CSS. A TV's built-in AirPlay is separate from this Android receiver;
HomePod output and DRM support are not implemented by PureTV.

**Device testing is limited to the native Android TV / Google TV edition.** Live screenshots and locale checks use
a Xiaomi Mi Stick running Android TV 9. Separate Google TV firmware validation is not recorded; the shared native
implementation is not a claim that every device has been tested. **LG webOS and Samsung Tizen are experimental,
hardware-untested ports**; package builds and mock APIs do not establish physical compatibility.

The repository uses renamed package IDs. First-launch authorization, HOME/recovery and receiver mirroring on a fresh
new-ID installation remain separate validation targets. [Test commands](DEVELOPMENT.md#tests).
