# Regression tests

From the repository root, build Android and run:

```sh
python tools/tests/run.py
python tools/tests/test_web_packages.py
node tools/tests/web_regression.js
```

JVM tests cover content/cache/navigation, welcome flow and TV input rules. Native Android test classes need hardware
and are not included in the desktop runner or production APK. Results are written under `out/`.
See the [development guide](../../docs/DEVELOPMENT.md#tests) for test scope, source audits and validation limits.
