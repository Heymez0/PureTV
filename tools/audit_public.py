#!/usr/bin/env python3
"""Check the Git index (or a clean source folder) before sharing. Never prints matching values."""
import argparse
import re
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = {"config.properties", "local.properties", ".env", "id_rsa", "id_ed25519", "tv-adb-key", "tv-adb-key.pub"}
SUFFIXES = {".apk", ".ipk", ".wgt", ".idsig", ".keystore", ".jks", ".pem", ".key", ".p12", ".pyc", ".log"}
PATTERNS = [re.compile(p) for p in [
    rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    rb"gh[pousr]_[A-Za-z0-9]{30,}", rb"github_pat_[A-Za-z0-9_]{40,}",
    rb"sk-(?:proj-)?[A-Za-z0-9_-]{30,}",
    rb"(?m)^(?:tmdb_key|watchmode_key)\s*=\s*[^\s#]+",
]]

def check(root, all_files=False):
    paths = []
    indexed = False
    if (root / ".git").exists() and not all_files:
        raw = subprocess.check_output(["git", "ls-files", "-z", "--cached"], cwd=root)
        paths = [root / s.decode("utf-8") for s in raw.split(b"\0") if s]
        indexed = bool(paths)
    if not paths:
        paths = [p for p in root.rglob("*") if p.is_file() and not any(n in {".git", "out", "__pycache__"} for n in p.relative_to(root).parts)]
    issues = []
    for p in paths:
        rel = p.relative_to(root).as_posix()
        if p.is_symlink() or p.name in FORBIDDEN or p.suffix.lower() in SUFFIXES or "private" in p.name.lower():
            issues.append(rel + ": private/generated file")
            continue
        data = subprocess.check_output(["git", "show", ":" + rel], cwd=root) if indexed else p.read_bytes()
        if any(pattern.search(data) for pattern in PATTERNS):
            issues.append(rel + ": possible credential (value hidden)")
    return paths, issues

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--all-files", action="store_true")
    args = parser.parse_args()
    paths, issues = check(args.root.resolve(), args.all_files)
    if issues:
        sys.exit("\n".join(issues))
    print("PASS: " + str(len(paths)) + " public source files; no prohibited files or credential patterns found")
