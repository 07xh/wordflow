#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash ./scripts/test_parser.sh
guard_tmp="$(mktemp -d)"
trap 'rm -rf "$guard_tmp"' EXIT
javac -encoding UTF-8 -d "$guard_tmp" app/src/main/java/dev/wordflow/app/CardActionGuard.java scripts/CardActionGuardCliTest.java
java -cp "$guard_tmp" CardActionGuardCliTest
syntax_tmp="$(mktemp -d)"
trap 'rm -rf "$guard_tmp" "$syntax_tmp"' EXIT
javac -encoding UTF-8 -d "$syntax_tmp" scripts/SyntaxCheck.java
mapfile -t java_files < <(find app/src/main/java -name '*.java' | sort)
java -cp "$syntax_tmp" SyntaxCheck "${java_files[@]}"
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
manifest = ET.parse('app/src/main/AndroidManifest.xml')
ns = '{http://schemas.android.com/apk/res/android}'
permissions = [x.attrib.get(ns+'name') for x in manifest.findall('uses-permission')]
assert 'android.permission.INTERNET' not in permissions, 'Offline first: no internet permission expected'
assert 'android.permission.POST_NOTIFICATIONS' in permissions
services = manifest.findall('.//service')
assert len(services) == 1
assert services[0].attrib[ns+'foregroundServiceType'] == 'specialUse'
for path in Path('app/src/main/res').rglob('*.xml'):
    ET.parse(path)
activity = Path('app/src/main/java/dev/wordflow/app/MainActivity.java').read_text()
assert 'WindowInsets.Type.systemBars()' in activity, 'Edge-to-edge insets missing'
assert 'io.execute(store::close)' in activity, 'Background database close must be queued'
assert 'private void postUi(Runnable operation)' in activity, 'Lifecycle-safe UI updates missing'
print('PASS: Android resources, manifest, and lifecycle safety checks')
PY
