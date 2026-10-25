#!/usr/bin/env python3
"""Build and inspect generic webOS/Tizen packages without a TV, credentials or signing tools."""
from pathlib import Path
import io,json,re,subprocess,sys,tarfile,zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
for platform in ["webos","tizen"]:
    subprocess.run([sys.executable,str(ROOT/platform/"build.py"),"--generic","--preview"],check=True)
def ar_members(payload):
    assert payload[:8]==b"!<arch>\n"
    result={};at=8
    while at<len(payload):
        header=payload[at:at+60];assert header[58:60]==b"`\n";length=int(header[48:58]);at+=60
        result[header[:16].decode().strip().rstrip("/")]=payload[at:at+length];at+=length+(length%2)
    return result
members=ar_members((ROOT/"PureTV-webos.ipk").read_bytes())
assert members['debian-binary']==b'2.0\n'
with tarfile.open(fileobj=io.BytesIO(members['data.tar.gz']),mode='r:gz') as tar:
    webos={m.name:tar.extractfile(m).read() for m in tar if m.isfile()}
base='usr/palm/applications/io.puretv/'
info=json.loads(webos[base+'appinfo.json']);assert info['title']=='PureTV' and info['version']=='1.0.0' and info['id']=='io.puretv'
with zipfile.ZipFile(ROOT/'PureTV-tizen.wgt') as z:
    assert z.testzip() is None;tizen={n:z.read(n) for n in z.namelist()};assert 'author-signature.xml' not in tizen
xml=ET.fromstring(tizen['config.xml']);assert xml.attrib['version']=='1.0.0'
assert xml.find('{http://www.w3.org/ns/widgets}name').text=='PureTV'
assert xml.find('{http://tizen.org/ns/widgets}application').attrib['id']=='PureTVapp1.PureTV'
for bundle,prefix in [(webos,base),(tizen,'')]:
    assert not any(n.endswith('coins.js') for n in bundle)
    cfg=bundle[prefix+'config.js'].decode();data=json.loads(cfg.split('window.CONFIG = ',1)[1].split(';',1)[0])
    assert data['tmdbKey']=='' and data['city']==''
    assert bundle[prefix+'LICENSE'].replace(b'\r\n',b'\n')==(ROOT/'LICENSE').read_bytes().replace(b'\r\n',b'\n')
    assert prefix+'NOTICE.md' in bundle
    assert bundle[prefix+'third_party/LICENSE-launcher-MIT.txt'] == (ROOT/'docs/licenses/LICENSE-launcher-MIT.txt').read_bytes()
    assert not any('/docs/' in n or '/media/' in n for n in bundle)
    assert prefix+'fonts/InterVariable.ttf' in bundle and prefix+'fonts/OFL.txt' in bundle
    assert prefix+'symbols/LICENSE-material-symbols.txt' in bundle
    assert prefix+'third_party/LICENSE-simple-icons-CC0.txt' in bundle
    assert prefix+'credits.html' in bundle and prefix+'tmdb-logo.png' in bundle
    credit=bundle[prefix+'credits.html'].decode()
    for target in re.findall(r'(?:src|href)="([^"#]+)"',credit):
        if '://' not in target:assert prefix+target in bundle,target
    html=bundle[prefix+'index.html'].decode()
    for target in re.findall(r'(?:src|href)="([^"#]+)"',html):
        if '://' not in target:assert prefix+target in bundle,target
    for name in ['data.js','home.js']:
        script=bundle[prefix+name].decode();assert 'api.binance' not in script
    assert 'showCrypto' not in cfg and 'coins.js' not in html
print('PASS: IPK ar/tar structure, WGT CRC/manifests, numeric versions, empty generic credentials, fonts/licences and bundled resource paths')
