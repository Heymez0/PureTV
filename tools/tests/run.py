#!/usr/bin/env python3
"""Run the JVM regression suite after python build.py (no emulator or third-party libraries)."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools.build_android import ROOT, argument_file, java_tools

TESTS = ["V2Regression", "PosterCacheRegression", "PosterPlanRegression", "WelcomeRegression", "TvModeRegression", "LocaleFormatsRegression"]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--android-jar", type=Path)
    p.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    a = p.parse_args()
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    jar = a.android_jar or (Path(sdk) / "platforms/android-34/android.jar" if sdk else None)
    if not jar or not jar.is_file() or not (ROOT / "out/classes").is_dir():
        p.error("Build first and set ANDROID_HOME or --android-jar.")
    jdk = java_tools(a.java_home)
    output = ROOT / "out/tests"
    output.mkdir(exist_ok=True)
    args = ["-encoding", "UTF-8", "-cp", str(ROOT / "out/classes") + os.pathsep + str(jar.resolve()), "-d", output]
    args += [ROOT / ("tools/tests/io/puretv/" + name + ".java") for name in TESTS]
    argument_file(output / "javac.args", args)
    subprocess.run([jdk["javac"], "@" + str(output / "javac.args")], cwd=ROOT, check=True)
    for name in TESTS:
        subprocess.run([jdk["java"], "-cp", str(output) + os.pathsep + str(ROOT / "out/classes"), "io.puretv." + name], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools/tests/test_build_config.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools/tests/test_i18n.py")], check=True)

if __name__ == "__main__":
    main()
