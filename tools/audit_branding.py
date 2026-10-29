#!/usr/bin/env python3
"""Check PureTV identities, English presentation and preserved third-party notices."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OBSOLETE = re.compile(r"free[\s_.-]*my[\s_.-]*tv|\bfm" + r"tv[_.-]", re.I)
LEGAL = {
    "docs/licenses/NOTICE.md", "docs/licenses/provenance.json", "docs/licenses/web-provenance.json", "docs/licenses/component-inventory.json",
    "docs/licenses/LICENSE-launcher-MIT.txt", "app/assets/licenses/NOTICE.md",
    "app/assets/licenses/third_party/LICENSE-launcher-MIT.txt",
}
LOCALIZED_DOCS = {"docs/README.tr.md"}
SKIP = {".git", "out", "__pycache__"}


def check(root=ROOT):
    issues = []
    for path in root.rglob("*"):
        rel = path.relative_to(root)
        if not path.is_file() or any(n in SKIP for n in rel.parts):
            continue
        if OBSOLETE.search(rel.as_posix()):
            issues.append(rel.as_posix() + ": obsolete filename")
        if rel.as_posix() in LEGAL:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        if OBSOLETE.search(text):
            issues.append(rel.as_posix() + ": obsolete runtime/product reference")
        if rel.as_posix() not in LOCALIZED_DOCS and path.suffix in {".md", ".svg"} and re.search(r"[\u00c7\u00e7\u011e\u011f\u0130\u0131\u00d6\u00f6\u015e\u015f\u00dc\u00fc]", text):
            issues.append(rel.as_posix() + ": untranslated presentation text")
    try:
        manifest = ET.parse(root / "app/AndroidManifest.xml").getroot()
        assert manifest.attrib["package"] == "io.puretv"
        assert (root / "app/src/io/puretv/HomeMode.java").is_file()
        assert not (root / "README.en.md").exists()
        assert json.loads((root / "webos/appinfo.json").read_text())["id"] == "io.puretv"
        widget = ET.parse(root / "tizen/config.xml").getroot()
        tizen = widget.find("{http://tizen.org/ns/widgets}application")
        assert tizen.attrib["id"] == "PureTVapp1.PureTV" and tizen.attrib["package"] == "PureTVapp1"
        assert widget.attrib["id"] == "urn:puretv:tizen"
        mit = (root / "LICENSE").read_text(encoding="utf-8")
        assert "Copyright (c) 2026 PureTV contributors" in mit
        original = (root / "docs/licenses/LICENSE-launcher-MIT.txt").read_bytes()
        assert hashlib.sha256(original.replace(b'\r\n', b'\n')).hexdigest() == "966ef935eb1e3929d31f862e7d04a80c88cf695d0e659992bba6fef1e13e69c3"
        assert original == (root / "app/assets/licenses/third_party/LICENSE-launcher-MIT.txt").read_bytes()
        assert mit == (root / "app/assets/licenses/LICENSE").read_text(encoding="utf-8")
        source_notice = (root / "docs/licenses/NOTICE.md").read_text(encoding="utf-8")
        packaged_notice = source_notice.replace("(../../LICENSE)", "(LICENSE)").replace("(LICENSE-launcher-MIT.txt)", "(third_party/LICENSE-launcher-MIT.txt)")
        assert packaged_notice == (root / "app/assets/licenses/NOTICE.md").read_text(encoding="utf-8")
        assert {p.name for p in (root / "docs/media").iterdir()} == {"README.md", "remote.svg", "hero.svg", "home-en.png", "recommendations-en.png", "quick-settings-en.png", "shortcuts-en.png", "settings-en.png", "theme-home-light-en.png", "theme-settings-light-en.png", "theme-settings-dark-en.png"}
    except (OSError, KeyError, AssertionError, ET.ParseError) as error:
        issues.append("Identity, English-media or required-licence contract failed: " + str(error))
    return issues


if __name__ == "__main__":
    import sys
    issues = check()
    if issues:
        sys.exit("\n".join(issues))
    print("PASS: PureTV code/platform identities, English documentation/media and original licence retention")
