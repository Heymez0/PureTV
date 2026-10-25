#!/usr/bin/env python3
"""Check notice completeness and exact reviewed component hashes before packaging."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[2]
inventory=json.loads((ROOT/'docs/licenses/component-inventory.json').read_text(encoding='utf-8'))
for item in inventory['components']:
    data=(ROOT/item['path']).read_bytes()
    if not item['path'].endswith('.png'):data=data.replace(b'\r\n',b'\n')
    assert hashlib.sha256(data).hexdigest()==item['sha256'],item['path']
assert {'MIT','OFL-1.1','Apache-2.0','CC0-1.0'} <= {item['license'] for item in inventory['components']}
notice=(ROOT/'docs/licenses/NOTICE.md').read_text(encoding='utf-8')
assert 'Copyright' in (ROOT/'docs/licenses/LICENSE-launcher-MIT.txt').read_text(encoding='utf-8')
assert 'CC BY 4.0' in notice and 'GeoNames' in notice and 'Google' in notice
disclaimer='This product uses the TMDB API but is not endorsed or certified by TMDB.'
for file in ['app/src/io/puretv/Credits.java','web/credits.html','docs/licenses/NOTICE.md']:
    assert disclaimer in (ROOT/file).read_text(encoding='utf-8'),file
assert (ROOT/'app/assets/licenses/tmdb-logo.png').read_bytes().startswith(b'\x89PNG')
assert 'CC0 1.0 Universal' in (ROOT/'docs/licenses/LICENSE-simple-icons-CC0.txt').read_text(encoding='utf-8')
print('PASS: reviewed component hashes, complete licence texts, TMDB logo/disclaimer and weather attribution')
