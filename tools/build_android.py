#!/usr/bin/env python3
"""Dependency-free Android build. Python 3.10+, JDK 17+, SDK platform/build-tools 34."""
from __future__ import annotations
import argparse
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
STRING_DEFAULTS = dict(TMDB_KEY="", TMDB_REGION="US", LATITUDE="40.7128", LONGITUDE="-74.0060",
                       CITY="", LANGUAGE="auto", TEMPERATURE="auto", WATCHMODE_KEY="", HIDE_SERVICES="")
BOOL_DEFAULTS = dict(DISABLE_ASSISTANT="false", HIDE_ANIMATION="false")


def properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    for key, low, high in [("latitude", -90, 90), ("longitude", -180, 180)]:
        value = values.get(key, STRING_DEFAULTS[key.upper()])
        try:
            number = float(value)
            if not math.isfinite(number) or not low <= number <= high:
                raise ValueError()
        except ValueError:
            raise ValueError("Invalid coordinate in " + key) from None
    for key in BOOL_DEFAULTS:
        if values.get(key.lower(), "false") not in {"true", "false"}:
            raise ValueError("Expected true or false for " + key.lower())
    return values


def config_source(values):
    lines = ["package io.puretv;", "final class Config { private Config() {}"]
    for key, default in STRING_DEFAULTS.items():
        lines.append("static final String " + key + " = " + json.dumps(values.get(key.lower(), default), ensure_ascii=True) + ";")
    for key, default in BOOL_DEFAULTS.items():
        lines.append("static final boolean " + key + " = " + values.get(key.lower(), default) + ";")
    return "\n".join(lines) + "\n}\n"


def java_tools(java_home):
    suffix = ".exe" if os.name == "nt" else ""
    result = {}
    for name in ["java", "javac", "keytool"]:
        found = Path(java_home) / "bin" / (name + suffix) if java_home else shutil.which(name)
        if not found or not Path(found).is_file():
            raise ValueError("JDK tool not found: " + name + ". Set JAVA_HOME or --java-home.")
        result[name] = str(Path(found).resolve())
    return result


def run(args, cwd=ROOT):
    subprocess.run([str(a) for a in args], cwd=cwd, check=True)


def argument_file(path, args):
    # Java argument-file quoting; never interpolate configuration values into commands.
    path.write_text("\n".join('"' + str(a).replace("\\", "/").replace('"', '\\"') + '"' for a in args), encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--build-tools", type=Path, help="Explicit build-tools directory (default: SDK/build-tools/34.0.0)")
    parser.add_argument("--android-jar", type=Path, help="Explicit android.jar (default: SDK/platforms/android-34/android.jar)")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--config", type=Path, help="Defaults to local config.properties, otherwise the empty-key example")
    parser.add_argument("--keystore", type=Path, help="Optional existing key; omitted produces an unsigned APK")
    parser.add_argument("--alias", default="puretv")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    sdk = Path(args.sdk) if args.sdk else None
    bt = args.build_tools or (sdk / "build-tools/34.0.0" if sdk else None)
    android = args.android_jar or (sdk / "platforms/android-34/android.jar" if sdk else None)
    if not bt or not android or not android.is_file():
        parser.error("Set ANDROID_HOME, or --build-tools and --android-jar (SDK 34).")
    bt, android = bt.resolve(), android.resolve()
    suffix = ".exe" if os.name == "nt" else ""
    for name in ["aapt2" + suffix, "zipalign" + suffix, "lib/d8.jar", "lib/apksigner.jar", "core-lambda-stubs.jar"]:
        if not (bt / name).is_file():
            parser.error("Build tool missing: " + name)
    jdk = java_tools(args.java_home)
    config = args.config or (ROOT / "config.properties" if (ROOT / "config.properties").exists() else ROOT / "config.example.properties")
    generated = config_source(properties(config))
    if args.keystore:
        if not args.keystore.is_file():
            parser.error("Signing key does not exist; no release key is created automatically.")
        for env in ["PURETV_STORE_PASSWORD", "PURETV_KEY_PASSWORD"]:
            if not os.environ.get(env):
                parser.error("Signing requires environment variable " + env)
    out = ROOT / "out"
    if out.is_symlink() or out.resolve() != ROOT.resolve() / "out":
        parser.error("Unexpected output directory")
    if out.exists():
        shutil.rmtree(out)
    gen, classes = out / "gen/io/puretv", out / "classes"
    gen.mkdir(parents=True)
    classes.mkdir()
    (gen / "Config.java").write_text(generated, encoding="utf-8")
    run([bt / ("aapt2" + suffix), "compile", "--dir", "app/res", "-o", "out/res.zip"])
    run([bt / ("aapt2" + suffix), "link", "-o", "out/base.apk", "-I", android,
         "--manifest", "app/AndroidManifest.xml", "--java", "out/gen", "out/res.zip",
         "--min-sdk-version", "28", "--target-sdk-version", "33", "-A", "app/assets", "-0", "ttf"])
    with zipfile.ZipFile(out / "base.apk") as src, zipfile.ZipFile(out / "normalized.apk", "w") as dst:
        for entry in src.infolist():
            payload = src.read(entry)
            entry.filename = entry.filename.replace("\\", "/")
            dst.writestr(entry, payload)
    os.replace(out / "normalized.apk", out / "base.apk")
    sources = sorted((ROOT / "app/src").rglob("*.java")) + sorted((out / "gen").rglob("*.java"))
    javac_args = ["-nowarn", "-Xlint:-options", "-source", "8", "-target", "8", "-encoding", "UTF-8",
                  "-bootclasspath", str(android) + os.pathsep + str(bt / "core-lambda-stubs.jar"),
                  "-classpath", android, "-d", classes] + sources
    argument_file(out / "javac.args", javac_args)
    run([jdk["javac"], "@out/javac.args"])
    (out / "d8.args").write_text("\n".join(p.relative_to(ROOT).as_posix() for p in sorted(classes.rglob("*.class"))), encoding="utf-8")
    run([jdk["java"], "-cp", bt / "lib/d8.jar", "com.android.tools.r8.D8", "--release",
         "--min-api", "28", "--lib", android, "--output", out, "@out/d8.args"])
    with zipfile.ZipFile(out / "base.apk", "a", zipfile.ZIP_DEFLATED) as apk:
        for dex in sorted(out.glob("classes*.dex")):
            apk.write(dex, dex.name)
    run([bt / ("zipalign" + suffix), "-f", "-p", "4", out / "base.apk", out / "aligned.apk"])
    destination = (args.output or ROOT / ("PureTV.apk" if args.keystore else "PureTV-unsigned.apk")).resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    if args.keystore:
        signer = [jdk["java"], "-jar", bt / "lib/apksigner.jar"]
        run(signer + ["sign", "--v4-signing-enabled", "false", "--ks", args.keystore.resolve(), "--ks-key-alias", args.alias,
                      "--ks-pass", "env:PURETV_STORE_PASSWORD", "--key-pass", "env:PURETV_KEY_PASSWORD",
                      "--out", destination, out / "aligned.apk"])
        run(signer + ["verify", "--verbose", destination])
    else:
        shutil.copy2(out / "aligned.apk", destination)
    run([bt / ("zipalign" + suffix), "-c", "-p", "4", destination])
    print("Built " + destination.name + " (" + str(destination.stat().st_size) + " bytes)")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        sys.exit(str(error))
