# PureTV development

Read README.md, docs/DEVELOPMENT.md and .github/CONTRIBUTING.md first. Keep changes small and measurable.

- Java 8, min API 28, target API 33. Build with Python 3.10+, JDK 17 and Android SDK 34. No Android runtime libraries. Web TV packages use ES5/HTML/CSS without a framework.
- Run python build.py, python tools/tests/run.py, python tools/audit_public.py. Unsigned output is expected unless an existing key is explicitly supplied.
- Keep Canvas drawing free of network/bitmap work. Stop callbacks/listeners on pause. Keep the dual 3+3 poster working set during ordinary navigation.
- Preserve HomeMode, ModeJob, Original restoration, Watchdog and protected/Wi-Fi-owner packages. Do not uninstall/clear system packages or change picture/audio/network/account settings.
- Never explicitly launch HomeActivity during device QA; send the HOME key or resolve MAIN/HOME.
- Keep AirPlay as a separate GPL app; preserve certificate/signature-permission checks. Never include signing/ADB keys, config.properties or raw device logs.
- No physical TV testing, release publication, or full native receiver rebuild should be claimed unless actually performed.

- Web platforms: run python tools/tests/test_web_packages.py and node tools/tests/web_regression.js. Generic builds must have empty credentials. Never call an unsigned Tizen package install-ready.
- Keep the compatibility matrix accurate: Android features are not automatically available in LG/Samsung. Use the PureTV platform IDs consistently and preserve original licence/provenance. These IDs form a separate install from legacy packages; never claim an in-place migration.
- Label illustrations accurately and use only genuine captured screenshots when claiming device previews. Keep viewing history and network identifiers out of shared images. App-published recommendations may be shown when the user requests them; keep playback history and progress private. docs/media must not be shipped in application packages.
