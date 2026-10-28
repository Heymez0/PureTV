#!/usr/bin/env python3
"""Repeatable device checks over ADB. Read-only snapshot, native poster tests in a temporary folder, settings file copy.

    python tools/device_qa.py --serial TV_ADDRESS:5555 snapshot
    python tools/device_qa.py --serial TV_ADDRESS:5555 native      # after python build.py
    python tools/device_qa.py --serial TV_ADDRESS:5555 settings pull PureTV-settings.json

Nothing here installs an app, changes HOME, settings or packages, or prints network names, addresses, accounts or
viewing history. `native` runs the test classes with app_process from /data/local/tmp/puretv-qa and always deletes it.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.build_android import ROOT, argument_file, java_tools

REMOTE = "/data/local/tmp/puretv-qa"
SHARED_SETTINGS = "/sdcard/Download/PureTV-settings.json"
NATIVE = ["PosterNativeRegression", "DualPosterNativeRegression", "PosterBenchNative"]

# Packages are found on the device: PureTV, any AirPlay receiver and whatever holds HOME (also an earlier launcher).
SNAPSHOT = r"""
home=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
homepkg=${home%%/*}
echo "device: $(getprop ro.product.manufacturer) $(getprop ro.product.model), Android $(getprop ro.build.version.release) (API $(getprop ro.build.version.sdk)), $(getprop ro.product.cpu.abi)"
echo "home: $home"
echo "screensaver: $(settings get secure screensaver_components)"
for p in $( (pm list packages | sed 's/^package://' | grep -E 'puretv|airplay'; echo "$homepkg") | sort -u); do
  v=$(dumpsys package $p | grep -m2 -E 'versionName=|versionCode=' | tr -s ' ' | tr '\n' ' ')
  [ -n "$v" ] && echo "package $p:$v"
done
echo "disabled packages ($(pm list packages -d | wc -l)):"; pm list packages -d | sed 's/^package:/  /' | sort
grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo
echo "process PSS (KiB):"; dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | grep -E "puretv|airplay|katniss|alphonso|$homepkg" | sed 's/^ */  /'
echo "airplay receiver foreground services: $(dumpsys activity services | grep -E 'ServiceRecord|isForeground=true' | grep -A1 -E 'ServiceRecord.*airplay' | grep -c 'isForeground=true')"
"""


def adb_path(value):
    if value:
        return value
    found = shutil.which("adb")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not found and sdk:
        candidate = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        found = str(candidate) if candidate.is_file() else None
    if not found:
        sys.exit("adb not found: pass --adb or set ANDROID_HOME")
    return found


class Device:
    def __init__(self, adb, serial):
        self.base = [adb] + (["-s", serial] if serial else [])

    def run(self, *args, script=None, check=True):
        result = subprocess.run(self.base + list(args), input=script, capture_output=True, text=True,
                                encoding="utf-8", errors="replace")
        if check and result.returncode != 0:
            sys.exit((result.stdout + result.stderr).strip() or "adb failed")
        return result.stdout

    def shell(self, script):
        return self.run("shell", "sh", script=script.replace("\r\n", "\n"))


def snapshot(device, _args):
    print(device.shell(SNAPSHOT).rstrip())


def native(device, args):
    classes = ROOT / "out/classes"
    if not classes.is_dir():
        sys.exit("Build first: python build.py --config config.example.properties")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    bt = args.build_tools or (Path(sdk) / "build-tools/34.0.0" if sdk else None)
    jar = args.android_jar or (Path(sdk) / "platforms/android-34/android.jar" if sdk else None)
    if not bt or not jar or not jar.is_file():
        sys.exit("Set ANDROID_HOME, or --build-tools and --android-jar (SDK 34).")
    jdk = java_tools(args.java_home)
    with tempfile.TemporaryDirectory(dir=ROOT / "out") as folder:
        work = Path(folder)
        compiled = work / "classes"
        compiled.mkdir()
        javac = ["-nowarn", "-Xlint:-options", "-source", "8", "-target", "8", "-encoding", "UTF-8",
                 "-bootclasspath", str(jar) + os.pathsep + str(Path(bt) / "core-lambda-stubs.jar"),
                 "-classpath", str(classes) + os.pathsep + str(jar), "-d", compiled]
        javac += [ROOT / ("tools/tests/io/puretv/" + name + ".java") for name in NATIVE]
        argument_file(work / "javac.args", javac)
        subprocess.run([jdk["javac"], "@" + str(work / "javac.args")], check=True)
        bundle = work / "input.jar"
        jar_tool = Path(jdk["javac"]).parent / ("jar.exe" if os.name == "nt" else "jar")
        subprocess.run([str(jar_tool), "cf", bundle, "-C", classes, ".", "-C", compiled, "."], check=True)
        dex = work / "qa.jar"
        subprocess.run([jdk["java"], "-cp", Path(bt) / "lib/d8.jar", "com.android.tools.r8.D8", "--release",
                        "--min-api", "28", "--lib", jar, "--output", dex, bundle], check=True)
        device.run("shell", "mkdir -p " + REMOTE)
        try:
            device.run("push", str(dex), REMOTE + "/qa.jar")
            failed = 0
            for name in NATIVE:
                print("== " + name, flush=True)
                out = device.shell("cd %s && CLASSPATH=%s/qa.jar app_process %s io.puretv.%s; echo \"exit=$?\"\n"
                                   % (REMOTE, REMOTE, REMOTE, name))
                print(out.rstrip())
                if not out.rstrip().endswith("exit=0"):
                    failed += 1
        finally:
            device.run("shell", "rm -rf " + REMOTE, check=False)
            print("removed " + REMOTE)
        if failed:
            sys.exit("%d native check(s) failed" % failed)


def settings(device, args):
    local = Path(args.file)
    if args.direction == "pull":
        device.run("pull", SHARED_SETTINGS, str(local))
        print("saved " + str(local))
    else:
        if not local.is_file() or local.stat().st_size > 256 * 1024:
            sys.exit("not a settings backup: " + str(local))
        device.run("push", str(local), SHARED_SETTINGS)
        print("copied to " + SHARED_SETTINGS + "; restore it in PureTV: Settings > System and about > Restore settings")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--adb")
    parser.add_argument("--serial", help="device serial or host:port (adb devices)")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--build-tools", type=Path)
    parser.add_argument("--android-jar", type=Path)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("snapshot", help="read-only device summary")
    commands.add_parser("native", help="native poster tests and measurement in a temporary folder")
    copy = commands.add_parser("settings", help="copy the settings backup to or from this computer")
    copy.add_argument("direction", choices=["pull", "push"])
    copy.add_argument("file")
    args = parser.parse_args(argv)
    device = Device(adb_path(args.adb), args.serial)
    {"snapshot": snapshot, "native": native, "settings": settings}[args.command](device, args)


if __name__ == "__main__":
    main()
