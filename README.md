<div align="center">

<img src="docs/media/hero.svg" width="100%" alt="PureTV — a quieter TV experience">

# PureTV

**A quieter TV home screen, built around your apps.**

[Install](docs/INSTALL.md) · [Turkish guide](docs/README.tr.md) · [Build](docs/DEVELOPMENT.md#build) · [AirPlay](tools/airplay/README.md) · [Changelog](docs/CHANGELOG.md)

**Current release: [PureTV v1.3](https://github.com/Heymez0/PureTV/releases/tag/v1.3)** · Android 9+

</div>

PureTV brings a simple home screen to Android TVs, streaming boxes and sticks.
A native Java/Canvas launcher runs on Android; LG and Samsung use a lightweight web app.

**Hardware testing is limited to the Android TV / Google TV edition.** The reference device is a Xiaomi Mi Stick
running Android TV 9. **LG webOS and Samsung Tizen are experimental ports and have not been tested on real TV hardware.**

## Features

- **Choose your home:** a clock, recent content, or recent content with app recommendations.
- **Keep apps within reach:** separate app and shortcut shelves, other apps below, long-press shelf reordering, and
  hide, uninstall or force stop from an app's menu.
- **Make it yours:** light/dark themes, a background that follows the content (or stays plain), weather with the chance of rain, 12/24-hour time, and a plain black screensaver
  or one with your own photos from the TV and USB drives, with a burn-in guard for its clock.
- **Stay in control:** compact quick settings with a Free up button for memory and a sleep timer, optional lightweight
  mode and automatic memory cleanup, on-demand RAM/storage snapshots, settings backup and restore, a simple PIN lock for
  chosen apps, and updates from this repository's releases (notify or install automatically; only newer builds signed
  like yours).
- **Use your TV:** box/stick and TV profiles, available TV inputs, remote-button settings, and an app, Live TV or an
  input that opens when the TV starts.
- **Receive AirPlay:** optional separate Android receiver, installed and verified from PureTV, running only when you turn
  it on, with protected quick-panel on/off control.

Recent content depends on what installed Android apps publish. Optional web catalogue cards use your own TMDB key.
Android-specific settings, recent-content providers and AirPlay control are not available in the LG/Samsung web edition.

### New in v1.3

- **AirPlay receiver 0.0.36-puretv.3:** iPhones, iPads and Macs list the TV as **PureTV** (existing receivers are renamed once); mirrored video goes straight
  to the TV's video plane instead of an extra GPU copy, and each frame shows as soon as it is decoded.
- **Translations:** the 129 texts added since v1 are now in all 24 languages.
- **Smoother first seconds** on the home screen while posters arrive, and clearer settings descriptions.
- **Faster on TVs:** PureTV compiles itself ahead of time, and Optimise apps during the screensaver speeds up the apps
  you installed (custom home mode).
- Light theme: the clock/weather pill and the header buttons are easier to see.

### New in v1.2

- **Background from content** (Appearance): keep the theme's plain background instead of colours from the first card.
- After PureTV updates itself, the copied update file is removed from the TV.
- Updates in place from v1.1: System and about → Updates.

### New in v1.1

- **Updates from inside PureTV** (System and about → Updates), tested end to end on the reference Mi Stick.
- **Quick settings:** Free up memory and a 30–120 minute sleep timer.
- **App lock, hidden apps, On TV start, uninstall / force stop,** screensaver photos and automatic memory cleanup.
- **AirPlay receiver 0.0.36-puretv.2:** starts on demand and follows PureTV's light/dark look.
- **Security and fixes:** install results can no longer be faked by another app; app content appears after a settings
  restore; smoother home scrolling on Android 9–11.
- **New signing key:** v1 users uninstall v1 once before installing v1.1 (see [Install](#install)).

[Full changelog](docs/CHANGELOG.md) · [Release checks and testing scope](docs/RELEASE.md)

## Languages

PureTV includes **24 interface language catalogues**: English, Czech, Danish, Dutch, Finnish, French, German,
Greek, Hindi, Indonesian, Italian, Japanese, Korean, Norwegian Bokmål, Polish, Portuguese, Romanian, Russian,
Simplified Chinese, Spanish, Swedish, Turkish, Ukrainian and Vietnamese.

## Preview

Real English-interface screenshots of PureTV v1.1 on a Xiaomi Mi Stick running Android TV 9.

### Home Screens

<p align="center">
  <a href="docs/media/recommendations-en.png"><img src="docs/media/recommendations-en.png" width="100%" alt="PureTV recent and recommended home screen"></a>
  <br><strong>Recent + recommended</strong>
</p>

<p align="center">
  <a href="docs/media/home-en.png"><img src="docs/media/home-en.png" width="100%" alt="Clock home page with weather and the app shelf"></a>
  <br><strong>Clock home page</strong>
</p>

### Light and dark

PureTV has a light and a dark theme (or Automatic, by sunrise and sunset). In the recent and recommended layouts the
background takes its colours from the first card; Appearance → Background from content turns that off.

<table>
  <tr>
    <td width="50%" align="center" valign="top">
      <a href="docs/media/theme-home-light-en.png"><img src="docs/media/theme-home-light-en.png" width="100%" alt="Recommended home in the light theme"></a>
      <br><strong>Light</strong>
    </td>
    <td width="50%" align="center" valign="top">
      <a href="docs/media/recommendations-en.png"><img src="docs/media/recommendations-en.png" width="100%" alt="Recommended home in the dark theme"></a>
      <br><strong>Dark</strong>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center" valign="top">
      <a href="docs/media/theme-settings-light-en.png"><img src="docs/media/theme-settings-light-en.png" width="100%" alt="Appearance settings in the light theme"></a>
      <br><strong>Light settings</strong>
    </td>
    <td width="50%" align="center" valign="top">
      <a href="docs/media/theme-settings-dark-en.png"><img src="docs/media/theme-settings-dark-en.png" width="100%" alt="Appearance settings in the dark theme"></a>
      <br><strong>Dark settings</strong>
    </td>
  </tr>
</table>

### Features

<table>
  <tr>
    <td width="33%" align="center" valign="top">
      <a href="docs/media/quick-settings-en.png"><img src="docs/media/quick-settings-en.png" width="100%" alt="Compact quick settings with AirPlay control, RAM with a Free up button and the sleep timer"></a>
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

**Android TV / Google TV:** [Download PureTV.apk](https://github.com/Heymez0/PureTV/releases/latest/download/PureTV.apk) · [Release and SHA256 checksum](https://github.com/Heymez0/PureTV/releases/latest)

The signed v1.3 APK is for **Android 9 or newer**. Download it on your computer, copy it to a USB drive or transfer it
to the TV, then open it with the TV file manager. Follow PureTV's welcome guide after installation.
[Step-by-step Android instructions](docs/INSTALL.md#android-tv--google-tv).

**Coming from v1?** Since v1.1 PureTV is signed with a new key, so Android won't install it over v1: back up your settings (System
and about → Back up settings), uninstall PureTV v1 (and PureTV AirPlay), then install the latest release and restore. From v1.1 on,
releases update in place, also from PureTV's own Updates screen.

### Easy to install

With [Claude Code](https://code.claude.com/docs/en/cli-reference) or [Codex CLI](https://learn.chatgpt.com/docs/codex/cli) installed and signed in, run **one** of these commands in a folder on your computer.

**Claude Code**

```sh
claude "Install PureTV on my TV from https://github.com/Heymez0/PureTV. Read AGENTS.md and docs/INSTALL.md in that repository, identify my TV platform, ask only for missing device details, then follow the matching installation steps. If an older PureTV is already installed, follow the upgrade steps in docs/INSTALL.md, and keep the original HOME recovery working."
```

**Codex**

```sh
codex "Install PureTV on my TV from https://github.com/Heymez0/PureTV. Read AGENTS.md and docs/INSTALL.md in that repository, identify my TV platform, ask only for missing device details, then follow the matching installation steps. If an older PureTV is already installed, follow the upgrade steps in docs/INSTALL.md, and keep the original HOME recovery working."
```

Each line starts an interactive installation assistant. Have your TV model/platform and local IP address ready.
Developer Mode, device authorization and any required signing follow the platform guide below.
LG/Samsung ports remain experimental and hardware-untested.

### Choose your platform

| Platform | What to install | Instructions |
| --- | --- | --- |
| Android TV / Google TV | Signed **PureTV.apk**, by USB/file transfer or ADB | [Download and setup](docs/INSTALL.md#android-tv--google-tv) |
| LG webOS | Build an **IPK**; Developer Mode is required | [LG steps](docs/INSTALL.md#lg-webos) · experimental, hardware-untested |
| Samsung Tizen | Build and sign a **WGT** with your TV certificate | [Samsung steps](docs/INSTALL.md#samsung-tizen) · experimental, hardware-untested |

Android APKs are for Android devices. LG and Samsung use the separate packages listed above.
On Android, the welcome guide lets you choose Box/stick or TV. Use **Original** in All settings → System and about →
Default home screen to return to the manufacturer's HOME before removing an active replacement launcher.
[Updates and package identity](docs/INSTALL.md#identity-and-upgrades) · [Platform testing scope](docs/INSTALL.md#compatibility).

## Build

Android needs Python 3.10+, JDK 17 and Android SDK 34. Web packages need Python only; Samsung installation also needs a signing profile.
Choose the command for your platform:

```sh
python build.py --config config.example.properties
python webos/build.py --generic
python tizen/build.py --generic --sign YOUR_PROFILE
```

The default Android APK is unsigned. [Build, signing and configuration](docs/DEVELOPMENT.md#build).
The signed Android APK is available under [Install](#install). Private keys and personal configuration are excluded.

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
