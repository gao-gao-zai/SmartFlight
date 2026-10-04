#!/usr/bin/env bash
set -euo pipefail
adb_bin="${ANDROID_HOME:?}/platform-tools/adb"
artifacts=app/build/emulator-artifacts/screen-410x502
mkdir -p "$artifacts"
cleanup() {
    "$adb_bin" pull /sdcard/Download/smartflight-small-screen "$artifacts/screenshots" || true
    "$adb_bin" pull /sdcard/Download/smartflight-activities "$artifacts/activity-screenshots" || true
    "$adb_bin" logcat -d > "$artifacts/logcat.txt" || true
    "$adb_bin" shell wm size reset || true
    "$adb_bin" shell wm density reset || true
    "$adb_bin" shell settings put system font_scale 1.0 || true
}
trap cleanup EXIT
bash scripts/build_activity_fixtures.sh
"$adb_bin" push app/build/activity-fixtures/fixture-v1.apk /data/local/tmp/smartflight-activity-v1.apk
"$adb_bin" push app/build/activity-fixtures/fixture-v2.apk /data/local/tmp/smartflight-activity-v2.apk
"$adb_bin" install -r app/build/activity-fixtures/fixture-v1.apk
"$adb_bin" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$adb_bin" shell am force-stop com.google.android.apps.nexuslauncher
"$adb_bin" shell wm size 410x502
for config in '160 1.0 zh' '160 1.0 en' '240 1.0 zh' '240 1.3 en'; do
    read -r density font locale <<< "$config"
    case_name="410x502-${density}dpi-${font}font-${locale}"
    "$adb_bin" shell wm density "$density"
    "$adb_bin" shell settings put system font_scale "$font"
    "$adb_bin" shell pm clear com.gaozay.smartflight
    "$adb_bin" shell wm size > "$artifacts/$case_name-display.txt"
    "$adb_bin" shell wm density >> "$artifacts/$case_name-display.txt"
    "$adb_bin" shell settings get system font_scale >> "$artifacts/$case_name-display.txt"
    "$adb_bin" shell am instrument -w -r \
        -e class com.gaozay.smartflight.screen.SmallScreenUiTest \
        -e screenCase "$case_name" -e screenLocale "$locale" \
        com.gaozay.smartflight.test/androidx.test.runner.AndroidJUnitRunner | tee "$artifacts/$case_name-ui.txt"
    python3 - "$artifacts/$case_name-ui.txt" <<'PY'
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text()
assert 'OK (3 tests)' in text and 'FAILURES!!!' not in text, text
PY
    "$adb_bin" shell am instrument -w -r \
        -e class com.gaozay.smartflight.quickrule.QuickRuleIntegrationTest \
        com.gaozay.smartflight.test/androidx.test.runner.AndroidJUnitRunner | tee "$artifacts/$case_name-quick-rule.txt"
    python3 - "$artifacts/$case_name-quick-rule.txt" <<'PY'
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text()
assert 'OK (3 tests)' in text and 'FAILURES!!!' not in text, text
PY
    "$adb_bin" pull /sdcard/Download/smartflight-activities "$artifacts/$case_name-quick-rule-screenshots"
done
