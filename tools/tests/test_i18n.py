#!/usr/bin/env python3
"""Translation extraction and catalogue contracts; no translation service or credentials."""
from pathlib import Path
import contextlib
import io
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools import translate as t

ROOT = Path(__file__).resolve().parents[2]
output = ROOT / "out/i18n-tests"
output.mkdir(parents=True, exist_ok=True)
old_src, old_web = t.SRC, t.WEB
try:
    with tempfile.TemporaryDirectory(dir=output) as folder:
        root = Path(folder)
        source, web = root / "src", root / "web"
        source.mkdir(); web.mkdir()
        (source / "SettingsActivity.java").write_text("""class SettingsActivity {
            String tr(String text) { return Ui.tr(text); }
            void show() {
                Ui.tr("Início", "Home screen");
                Ui.tr("Abrir", "Open", "Abrir app");
                Ui.tr(mode.equals("clock") ? "Clock only" : flag ? "First" : "Second");
                tr("Language");
                action("Device mode", () -> {});
                adjust("Theme", d -> {});
                Ui.trf("{n} min ago", "n", count);
            }
        }""", encoding="utf-8")
        (source / "DeviceProfiles.java").write_text('class DeviceProfiles { static String label(String value) { return flag ? "Television" : "TV box / stick"; } }', encoding="utf-8")
        (web / "home.js").write_text("tr('Save'); trf('{n} items', {n: 3}); formatDate('format|HH:mm', now);", encoding="utf-8")
        t.SRC, t.WEB = str(source), str(web)
        keys = t.extract()
        expected = {"Home screen", "Open", "Clock only", "First", "Second", "Language", "Device mode", "Theme", "{n} min ago", "Television", "TV box / stick", "Save", "{n} items", "format|HH:mm"}
        assert set(keys) == expected, sorted(set(keys) ^ expected)
        assert not {"Início", "Abrir", "Abrir app", "clock", "n"} & set(keys)
        assert t.web_keys() == {"Save", "{n} items", "format|HH:mm", "HH:mm"}
finally:
    t.SRC, t.WEB = old_src, old_web

assert t.fits("Count {n}", "Count {n}", "Nombre : {n}")
assert not t.fits("Count {n}", "Count {n}", "Nombre")
assert not t.fits("Open", "Open", " ")
assert t.fits("format|HH:mm", "HH:mm", "HH:mm")
assert not t.fits("format|HH:mm", "HH:mm", "Clock")
assert t.fits("plural|one:{n} item|other:{n} items", "one:{n} item|other:{n} items", "one:{n} élément|other:{n} éléments")
assert not t.fits("plural|one:{n} item|other:{n} items", "one:{n} item|other:{n} items", "one:{n} élément")
source_keys = t.extract()
core = {"Home screen", "Appearance", "Clock and weather", "Content", "Connections", "Device and performance", "System and about", "Home screen layout", "All settings", "Edit home screen", "Device status", "Good morning", "Good afternoon", "Good evening", "Good night"}
assert core <= set(source_keys)
assert len(t.languages()) == 23
for lang in t.languages():
    cat = t.load(lang)
    assert core <= set(cat), lang
    assert all(t.fits(k, t.english_of(k), v) for k, v in cat.items()), lang
for key, value in source_keys.items():
    assert t.load("en")[key] == value, key
with contextlib.redirect_stdout(io.StringIO()) as log:
    assert t.check(source_keys, allow_fallback=True) == 0, log.getvalue()
print("PASS: English/legacy/wrapper/conditional extraction; context fallback, placeholders, plural/date validity and 24 language catalogues")
