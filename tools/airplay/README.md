# Optional PureTV AirPlay receiver

The receiver is a separate **GPL-3.0** Android app; the launcher remains MIT-licensed.
Package: `io.puretv.airplay`. Version: `0.0.36-puretv.1`. Supplied native engine: **armeabi-v7a**.

## Integration

Sign launcher and receiver with the same certificate. Launcher commands use
`io.puretv.airplay.permission.CONTROL` at signature level and verify the component/certificate on every command.
Only QUERY/START/STOP are accepted under `io.puretv.airplay.control.*`; no network-control port is added.
Receiver pairing/PIN settings are not overridden. Do not run multiple receiver editions together.

## Source and build

`source-manifest.json` identifies the pinned base, exact APK and matching **PureTV-AirPlay-Source.zip** hashes.
The complete source archive contains the Gradle wrapper, interface code, native-source snapshots, licences and build notes.
The patch in this folder is for review; it is not a substitute for that full source distribution.
No public download/Release URL is configured yet.

Extract the full archive and follow its `PURETV-SOURCE-NOTES.md`. The interface build uses JDK 17+, SDK 37 and its wrapper:

```sh
./gradlew --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease
```

Full native rebuilding additionally needs Linux, NDK 27.0.12077973, CMake, Git, make and Perl; follow the archive's recipe.
The interface build/lint and unchanged ARMv7 engine-byte comparison passed. Full native rebuilding and new-identity
physical mirroring/control QA are not claimed. Other ABIs, HomePod output and DRM support are not added.
Distribute the matching full GPL source alongside any receiver APK.
