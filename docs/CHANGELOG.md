# Changelog

## v1 — final release

- Replace platform icons and the Android TV banner with original charcoal/gold PureTV artwork.
- Prepare shelf logos before drawing; retain them across unchanged layouts.
- Cull off-screen rows with a guard row, bound the shadow-mask LRU and precompute spring constants.
- Schedule photo screensaver callbacks at the existing render cadence; retain the clock's low-frequency callback.
- Preserve the dual 3+3 poster working set and ordinary-return artwork retention.
- Add readable bundled licence texts, component hashes, the full CC0 notice and service credits.
- Set product versions to v1 / Android code 2 and 1.0.0 on the experimental web ports.
- Add cache-boundary, motion-equivalence, culling and licence/package regressions.
- Verify fresh `io.puretv` welcome/setup and HOME after reboot on Mi Stick / Android TV 9.
- Verify renamed receiver settings control and owner-confirmed Screen Mirroring; distribute its matching full GPL source.

See [release checks](RELEASE.md) for verification and testing limits.

## v1 — easier installation

- Add one-line Claude Code/Codex installation prompts under Install.
- Provide a signed Android APK download with USB, file-transfer and ADB instructions.
- Separate LG and Samsung setup/build/sign/install steps and retain their experimental status.
- Add a Turkish reader guide while keeping the default README in English.

## v1 — preview gallery layout

- Restore the original PureTV title banner.
- Feature the combined home screen at full width, with the clock home below it.
- Arrange quick settings, the Shortcuts shelf and settings in a three-column feature row.
- Add two unedited Mi Stick captures; keep runtime packages and application behavior unchanged.

## v1 — preview and localization update

- Replaced interface illustrations with three genuine English Mi Stick screenshots without viewing-history content.
- Made native-only hardware testing and experimental LG/Samsung status explicit.
- Documented 24 language catalogues and English fallback for untranslated help text.
- Filled core menu/greeting labels, corrected English source extraction and repaired English catalogue text.
- Made RAM percentages and resource sizes follow the app language with cached formatting context.
- Added localization regressions; no extra background polling, service or receiver change.

## v1 — source preview

- PureTV branding and package IDs; a welcome guide with Box/stick and TV profiles.
- Clock, recent-content and combined home layouts with source selection and content hiding.
- Compact quick settings, unified settings, TV inputs and remote-button infrastructure.
- Prepared 3+3 artwork retention, stopped hidden-screen listeners and an older-GPU drawing path.
- Plain clock typography and a black/dimmed screensaver without the unused blurred photo copy.
- Optional separate, signature-controlled AirPlay receiver.
- LG webOS and Samsung Tizen web packaging, platform APIs and installers.
- English illustrated documentation, portable builds and regression/source checks.
- PureTV MIT notice with preserved third-party notices; optional receiver remains GPL-3.0.
- Simplified repository: two main guides, developer utilities under tools/, and no build output in source archives.

The PureTV IDs form separate installations; see [upgrade notes](INSTALL.md#identity-and-upgrades).
