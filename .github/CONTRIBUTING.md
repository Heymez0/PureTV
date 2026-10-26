# Contributing

Read the [README](../README.md) and [development guide](../docs/DEVELOPMENT.md) first.
Run the documented builds, regressions and source audits. Validate drawing/lifecycle and remote changes on hardware;
desktop tests do not cover them.

Keep work bounded: no network/bitmap preparation during drawing, continuous hidden-screen polling or clearing the
prepared 3+3 artwork set on ordinary returns. Prefer the existing SDK over new dependencies. Measure the same
scenario and units; PSS and bitmap bytes differ. Do not promise universal RAM/FPS results or remove quality silently.

Preserve HOME switching, Original recovery, Watchdog, Wi-Fi-owner protection and signature-guarded AirPlay control.
Do not uninstall/clear system packages or change picture, audio, network, accounts or tuner settings.
Never contribute private keys, device addresses, raw logs, viewing history or personal configured packages.
Use existing UI translation catalogues; the main README/artwork is English; the Turkish reader guide is docs/README.tr.md. Keep required [notices](../docs/licenses/NOTICE.md).

For web changes, preserve ES5, platform bridges and hidden-page timer suspension. Mock tests do not establish
physical TV, HDMI or certificate compatibility.
