<div align="center">

<img src="docs/media/hero.svg" width="100%" alt="PureTV — a quieter TV experience">

# PureTV

**A quieter TV home screen, built around your apps.**

[Install](docs/INSTALL.md) · [Build](docs/DEVELOPMENT.md#build) · [AirPlay](tools/airplay/README.md) · [Changelog](docs/CHANGELOG.md)

</div>

PureTV brings a simple home screen to Android TVs, streaming boxes and sticks.
A native Java/Canvas launcher runs on Android; LG and Samsung use a lightweight web app.

**Hardware testing is limited to the Android TV / Google TV edition.** The reference device is a Xiaomi Mi Stick
running Android TV 9. **LG webOS and Samsung Tizen are experimental ports and have not been tested on real TV hardware.**

## Features

- **Choose your home:** a clock, recent content, or recent content with app recommendations.
- **Keep apps within reach:** separate app and shortcut shelves, other apps below, and long-press shelf reordering.
- **Make it yours:** light/dark themes, weather, 12/24-hour time and a plain black screensaver.
- **Stay in control:** compact quick settings, optional lightweight mode and on-demand RAM/storage snapshots.
- **Use your TV:** box/stick and TV profiles, available TV inputs and remote-button settings.
- **Receive AirPlay:** optional separate Android receiver with protected quick-panel on/off control.

Recent content depends on what installed Android apps publish. Optional web catalogue cards use your own TMDB key.
Android-specific settings, recent-content providers and AirPlay control are not available in the LG/Samsung web edition.

## Languages

PureTV includes **24 interface language catalogues**: English, Czech, Danish, Dutch, Finnish, French, German,
Greek, Hindi, Indonesian, Italian, Japanese, Korean, Norwegian Bokmål, Polish, Portuguese, Romanian, Russian,
Simplified Chinese, Spanish, Swedish, Turkish, Ukrainian and Vietnamese.

Select a language in **All settings → System and about → Language**, or use Automatic to follow the TV.
Core menu labels, greetings and quick settings are translated. Some newer help text falls back to English.
Resource numbers follow the selected app language without changing Android's system language.

## Preview

Real English-interface screenshots from PureTV v2 on a Xiaomi Mi Stick running Android TV 9.

### Home Screens

<p align="center">
  <a href="docs/media/recommendations-en.png"><img src="docs/media/recommendations-en.png" width="100%" alt="PureTV recent and recommended home screen"></a>
  <br><strong>Recent + recommended</strong>
</p>

<p align="center">
  <a href="docs/media/home-en.png"><img src="docs/media/home-en.png" width="50%" alt="Clock home page with weather and the app shelf"></a>
  <br><strong>Clock home page</strong>
</p>

### Features

<table>
  <tr>
    <td width="33%" align="center" valign="top">
      <a href="docs/media/quick-settings-en.png"><img src="docs/media/quick-settings-en.png" width="100%" alt="Compact quick settings with AirPlay control and RAM information"></a>
      <br><strong>Quick settings</strong>
    </td>
    <td width="34%" align="center" valign="top">
      <a href="docs/media/shortcuts-en.png"><img src="docs/media/shortcuts-en.png" width="100%" alt="Home scrolled down once to the Shortcuts shelf and detailed weather"></a>
      <br><strong>Shortcuts</strong>
    </td>
    <td width="33%" align="center" valign="top">
      <a href="docs/media/settings-en.png"><img src="docs/media/settings-en.png" width="100%" alt="Settings with English selected as the interface language"></a>
      <br><strong>Settings</strong>
    </td>
  </tr>
</table>

Open any screenshot to see it at full resolution. The combined home shows app-published recommendations;
viewing-history titles and progress are not shown. Recommendation titles and artwork follow the source app's language.
The header banner is an illustration. [Capture details](docs/media/README.md).

## Install

| Platform | Target and testing status | Guide |
| --- | --- | --- |
| Android TV / Google TV | Native edition; reference hardware: Mi Stick / Android 9 | [Install and first launch](docs/INSTALL.md#android-tv--google-tv) |
| LG webOS | webOS 3+ · experimental, hardware-untested | [Developer Mode installation](docs/INSTALL.md#lg-webos) |
| Samsung Tizen | Tizen 3+ · experimental, hardware-untested | [Certificate and installation](docs/INSTALL.md#samsung-tizen) |

On Android, follow the welcome guide and choose Box/stick or TV. To go back, select **Original** in
All settings → System and about → Default home screen before uninstalling.
On LG/Samsung, PureTV is an app; the manufacturer's HOME remains available.

This source preview uses PureTV's own package IDs and is a separate installation from earlier packages.
See [compatibility and device-test status](docs/INSTALL.md#compatibility) before installing.

## Build

Android needs Python 3.10+, JDK 17 and Android SDK 34. Web packages need Python only; Samsung installation also needs a signing profile.
Choose the command for your platform:

```sh
python build.py --config config.example.properties
python webos/build.py --generic
python tizen/build.py --generic --sign YOUR_PROFILE
```

The default Android APK is unsigned. [Build, signing and configuration](docs/DEVELOPMENT.md#build).
No private keys or personal configuration are included. A public download/Release has not been published yet.

## Source layout

```text
app/       Native Android launcher and assets
web/       Shared web interface
webos/     LG packaging and installer
tizen/     Samsung packaging and installer
tools/     Build helpers, tests and optional AirPlay integration
docs/      Installation, development, artwork and licences
```

[Contributing](.github/CONTRIBUTING.md) · [Security](.github/SECURITY.md) · [Development and tests](docs/DEVELOPMENT.md)

PureTV contributions are [MIT](LICENSE). Incorporated code retains its [third-party notices](docs/licenses/NOTICE.md).
The optional AirPlay receiver is a separate GPL-3.0 application. App names and logos belong to their owners.
