#!/usr/bin/env bash
set -euo pipefail
sdk_adb="${ANDROID_HOME:?ANDROID_HOME is required}/platform-tools/adb"
pkg=com.gaozay.smartflight.preview
artifacts=app/build/emulator-artifacts/release-preview
mkdir -p "$artifacts"
apk=app/build/outputs/apk/release/app-universal-release.apk
"$sdk_adb" install -r "$apk"
"$sdk_adb" logcat -c
"$sdk_adb" shell am start -W -n "$pkg/com.gaozay.smartflight.MainActivity" > "$artifacts/main-start.txt"
"$sdk_adb" shell uiautomator dump /sdcard/smartflight-preview-main.xml
"$sdk_adb" pull /sdcard/smartflight-preview-main.xml "$artifacts/main.xml"
"$sdk_adb" shell am start -W -a com.gaozay.smartflight.action.QUICK_RULE -p "$pkg" \
    --es package_name com.gaozay.smartflight.activityfixture \
    --es activity_name com.gaozay.smartflight.activityfixture.SecondActivity > "$artifacts/quick-rule-start.txt"
# Wait for asynchronous scans and the release Compose UI, then validate the actual window tree.
for attempt in $(seq 1 20); do
    "$sdk_adb" shell uiautomator dump /sdcard/smartflight-preview-rule.xml > /dev/null
    "$sdk_adb" pull /sdcard/smartflight-preview-rule.xml "$artifacts/quick-rule.xml" > /dev/null
    if python3 - "$artifacts/quick-rule.xml" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
root=ET.fromstring(pathlib.Path(sys.argv[1]).read_text())
texts={n.get('text') for n in root.iter('node')}
assert 'com.gaozay.smartflight.activityfixture.SecondActivity' in texts
assert {'Entire app','This Activity','Online','Offline','Auto','Save'} <= texts, texts
PY
    then break; fi
    if [[ "$attempt" == 20 ]]; then echo 'Release preview rule dialog was not ready' >&2; exit 1; fi
    sleep 1
done
"$sdk_adb" shell screencap -p /sdcard/smartflight-preview-rule.png
"$sdk_adb" pull /sdcard/smartflight-preview-rule.png "$artifacts/quick-rule.png"
"$sdk_adb" logcat -d > "$artifacts/logcat.txt"
python3 - "$artifacts/main.xml" "$artifacts/main-start.txt" "$artifacts/quick-rule-start.txt" "$artifacts/logcat.txt" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
assert any(n.get('package') == 'com.gaozay.smartflight.preview' for n in ET.parse(sys.argv[1]).getroot().iter('node'))
for name in sys.argv[2:4]:
    text=pathlib.Path(name).read_text()
    assert 'Status: ok' in text, text
log=pathlib.Path(sys.argv[4]).read_text()
assert 'FATAL EXCEPTION' not in log, 'Release smoke log contains a fatal exception'
print('Optimized Release preview MainActivity and exported rule entry opened successfully')
PY
"$sdk_adb" shell am force-stop "$pkg"
