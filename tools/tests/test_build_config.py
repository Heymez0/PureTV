#!/usr/bin/env python3
"""Untrusted build configuration stays literal Java data, including malformed coordinates/booleans."""
import json
from pathlib import Path
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools.build_android import properties, config_source

temporary_root = Path(__file__).resolve().parents[2] / "out/tests"
temporary_root.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(dir=temporary_root) as folder:
    path = Path(folder) / "config.properties"
    title = 'City "quoted" \\ Türkçe'
    path.write_text("city=" + title + "\nlatitude=0\nlongitude=0\nhide_animation=true\n", encoding="utf-8")
    values = properties(path)
    source = config_source(values)
    assert "static final String CITY = " + json.dumps(title, ensure_ascii=True) + ";" in source
    assert "static final boolean HIDE_ANIMATION = true;" in source
    for line in ["latitude=NaN", "longitude=181", "latitude=-91", "latitude=0;invalid", "hide_animation=invalid"]:
        path.write_text(line, encoding="utf-8")
        try:
            properties(path)
        except ValueError:
            pass
        else:
            raise AssertionError("Invalid configuration accepted")
    assert 'TMDB_KEY = "";' in config_source({})
print("PASS: build configuration escaping, empty credentials and invalid input rejection")
