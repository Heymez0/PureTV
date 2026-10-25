# Install PureTV

[Android TV / Google TV](#android-tv--google-tv) · [LG webOS](#lg-webos) · [Samsung Tizen](#samsung-tizen) · [Compatibility](#compatibility)

## Android TV / Google TV

Use a signed `PureTV.apk`. For your own build, follow [build and signing](DEVELOPMENT.md#build).
Enable developer/ADB access on your TV and complete its authorization prompt. With the TV and computer on the same network:

```sh
adb connect TV_IP:5555
adb install -r PureTV.apk
```

Some firmware requires wireless ADB pairing or background permissions instead of port 5555. Use the manufacturer's supported method.

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

1. Sign into [LG Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app) with your own account.
2. Enable Dev Mode Status, finish the restart and enable Key Server. Use the address/session time shown by the TV.
3. Build and install from the source folder:

```sh
python webos/build.py --generic
python webos/install.py TV_IP
```

The first install asks for the TV's passphrase in your own terminal. Python and OpenSSH (`ssh`, `ssh-keygen`) are required.
The key is stored in your user's `.ssh` folder; the installer remembers the first SSH host key and rejects unexpected changes.
Do not post keys/passphrases in issues or disable host verification without checking the TV.

Alternatively configure the [official webOS CLI](https://webostv.developer.lge.com/develop/tools/cli-dev-guide), then:

```sh
ares-install --device TV_NAME PureTV-webos.ipk
ares-launch --device TV_NAME io.puretv
```

On webOS 6+, app listing may use verified known apps and optional local READ_INSTALLED_APPS consent.
A refusal is remembered. The manufacturer's HOME stays available; number-key shortcuts depend on the remote/model.
Track Developer Mode expiry and use EXTEND in time. Uninstall through TV app management or
`python webos/install.py TV_IP --uninstall`.

## Samsung Tizen

1. Follow [Samsung's device guide](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html):
   Apps → keys 1 2 3 4 5 → Developer Mode → Host PC IP → restart.
2. Create an appropriate [certificate profile](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html)
   using Tizen Studio / Certificate Manager. Complete DUID, account and device-permission steps on your own computer.
3. Build, sign and install:

```sh
python tizen/build.py --generic --sign YOUR_PROFILE
python tizen/install.py TV_IP
```

The Python installer uses SDB. Official-tool alternative:

```sh
sdb connect TV_IP
tizen install -n PureTV-tizen.wgt -t DEVICE_SERIAL
tizen run -p PureTVapp1.PureTV -t DEVICE_SERIAL
```

Without `--sign`, the WGT is an **unsigned inspection artifact**, not install-ready. Updates require the same author
certificate. The manufacturer's HOME remains; Autorun Last App is available only when the TV provides it.
Uninstall through TV app management or `python tizen/install.py TV_IP --uninstall`.

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

The earlier Android runtime was tested on a Mi Stick / Android 9. The current PureTV identities pass desktop
build/regression and package checks, but first-launch authorization, HOME/recovery and receiver mirroring require
new-identity device QA. Separate physical Google TV, LG/Samsung signing, remote and HDMI validation is pending.
Mock API tests and illustrative artwork are not physical-device tests. [Test commands](DEVELOPMENT.md#tests).
