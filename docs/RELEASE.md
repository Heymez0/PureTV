# Release preparation

Current product versions: **Android v1.1 / versionCode 3**, **webOS 1.0.0**, **Tizen 1.0.0**.
v1 was versionCode 2 (its higher code let it update the preceding preview despite the simpler version name).
Historical commits and third-party protocol/version names are retained.

## Source and packaging

- Source is organised under app/, web/, webos/, tizen/, docs/, tools/ and .github/.
- Generated packages, signing/ADB keys, personal configuration, raw device logs and working folders stay outside the source distribution.
- Generic builds contain no TMDB or Watchmode credential. Android contains no native receiver engine or runtime libraries.
- Platform icons have an editable original SVG; their checked-in PNG exports need no extra build dependency.
- Documentation images are actual unedited English-interface Mi Stick captures. Recommendation art comes from its source app; private viewing history is omitted.
- Main documentation is English; the Turkish guide is a separate file.

## Verification

Required desktop checks:

```sh
python build.py --config config.example.properties
python tools/tests/run.py
python tools/tests/test_web_packages.py
node tools/tests/web_regression.js
python tools/audit_branding.py
python tools/audit_public.py
```

The JVM suite covers bounded input, refresh generations, immutable content snapshots, long-list navigation,
viewing-permission/layout gates, welcome/recovery state, TV inputs/remote allowlists, locale formatting,
poster working sets, memory bounds and identical animation trajectories. Web checks inspect generic IPK/WGT
structures, local resource paths, licences, platform bridges, empty credentials and hidden-page refresh behavior.
GitHub Actions runs the checks for every main-branch update; see Actions for the result of the exact commit being released.

Real-device checks use the existing same-key Mi Stick installation for navigation, credits, light-theme focus,
screensaver return and the performance comparison. The exact signed generic `io.puretv` v1/code2 APK was also
installed with fresh data. Its welcome guide, TV ADB authorization, default HOME/screensaver setup and HOME after
reboot passed. Its **Original** option restored the factory HOME/screensaver and the pre-existing disabled-package
set. The previous working launcher, its preferences and Android locale were preserved after the temporary test.

Installing a second HOME-capable package can display Android's HOME chooser; this is expected platform behavior.
The two technical package IDs are separate installations, not an in-place migration. This setup/recovery result
applies to the reference Mi Stick; firmware-specific HOME authorization still needs validation on other TVs.

## Signing key

The v1 signing key is no longer available. From v1.1 the launcher and receiver APKs are signed with a new key
(certificate SHA-256 `808dd32ee3d027606fbaa586c71755ae2e97957182d7f565f8c777db15c93677`), and both must use it.
Android cannot update across keys, so v1 installations must uninstall PureTV and PureTV AirPlay before installing
v1.1, and v1's built-in update check will not offer it to them. Restore preferences afterwards from a settings backup.
Every later release must use this key and a higher versionCode (v1.1 is 3).

## Licences and distribution

PureTV additions use MIT. Incorporated MIT notices, Inter's OFL and Material Symbols' Apache 2.0 text are preserved.
The complete Simple Icons CC0 text is included with the web packages. Weather/GeoNames and TMDB credits are
available in the interface. The exact reviewed notice and artwork hashes are in
[component-inventory.json](licenses/component-inventory.json); [NOTICE](licenses/NOTICE.md) identifies ownership and terms.
Neither the MIT licence nor an API key grants ownership of app logos or catalogue artwork.

AirPlay remains a separate optional GPL app. Any receiver APK distribution must include the exact complete
corresponding source identified in tools/airplay/source-manifest.json. The source patch alone is insufficient.
The prepared receiver uses the unchanged upstream ARMv7 engine; a full native rebuild has not been verified.
The renamed `io.puretv.airplay` receiver was tested with the new launcher on Mi Stick / Android TV 9. Settings
start/stop, quick-panel stop and keeping an explicit stop after reopening the receiver passed. The device owner
confirmed a new Screen Mirroring session. An unprivileged control broadcast was not accepted.
Each release distributes the receiver APK and its matching full GPL source together. The main APK contains only the launcher.

## Testing limits

- Reference hardware: Xiaomi Mi Stick, Android TV 9. No separate Google TV firmware or other manufacturer's TV inputs is claimed.
- LG webOS and Samsung Tizen are experimental, desktop/package-tested and physically untested. A WGT needs the owner's Samsung certificate before installation.
- Older Mi Stick GPUs still miss some 60 Hz deadlines during detailed-weather scrolling. No universal RAM/FPS guarantee is made.
- Public/stable publication and repository visibility are separate owner actions. A private release does not make the project public.
