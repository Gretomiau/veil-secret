"""Verify packaged asset migration without Minecraft or third-party dependencies.

Usage: python tools/verify-assets.py fabric.jar neoforge.jar
Text hashes normalize CRLF to LF so Windows and CI checkouts agree.
"""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEXT = {'.json', '.mcmeta', '.glsl', '.fsh', '.vsh', '.txt', '.obj'}

def digest(name, data):
    if Path(name).suffix in TEXT:
        data = data.replace(b'\r\n', b'\n')
    return hashlib.sha256(data).hexdigest()

manifest = json.loads((ROOT / 'docs/audit/asset-migration.json').read_text(encoding='utf-8'))
assert len(manifest['entries']) == 210
assert len({entry['source'] for entry in manifest['entries']}) == 210
artifacts = sys.argv[1:] or [str(path) for loader in ('fabric','neoforge')
    for path in (ROOT / loader / 'build/libs').glob(f'veil-{loader}-*.jar')
    if '-sources' not in path.name and '-dev' not in path.name]
assert len(artifacts) >= 2, 'Build the Fabric and NeoForge release jars first'
for file in artifacts:
    with zipfile.ZipFile(file) as jar:
        names = set(jar.namelist())
        for entry in manifest['entries']:
            data = jar.read(entry['target'])
            assert digest(entry['target'], data) == entry['target_sha256'], entry['target']
        for pack in ('test_particles', 'test_shaders', 'fog', 'black_hole'):
            metadata = json.loads(jar.read(f'resourcepacks/{pack}/pack.mcmeta'))
            assert metadata['pack']['pack_format'] == 34
            assert metadata['pack']['description'].startswith('Mystrix')
        assert not any('/pinwheel/' in name for name in names)
        assert 'licenses/jetbrains-mono-OFL.txt' in names
        assert jar.read('foundry/veil/Veil.class')[6:8] == bytes([0, 65])
        assert not any(name.startswith('org/lwjgl/util/shaderc/') or name.endswith('EffectsShaderTest.class') for name in names)
    print(f'{Path(file).name}: all 210 migration destinations, four packs, aliases and Java 21 packaging verified')
