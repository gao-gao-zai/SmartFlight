#!/usr/bin/env bash
set -euo pipefail
adb_bin="${ANDROID_HOME:?}/platform-tools/adb"
out=app/build/emulator-artifacts/promo
mkdir -p "$out"
watcher_pid=""
cleanup() {
  # The watcher alone sends SIGINT: a second signal can abort MP4 finalization.
  "$adb_bin" shell 'echo > /sdcard/Download/smartflight-promo/active-clip' || true
  sleep 3
  if [ -n "$watcher_pid" ]; then kill "$watcher_pid" || true; fi
  "$adb_bin" pull /sdcard/Download/smartflight-promo "$out/recordings" || true
  "$adb_bin" logcat -d > "$out/logcat.txt" || true
}
trap cleanup EXIT
bash scripts/build_activity_fixtures.sh
curl --fail --location --retry 3 --output "$out/shizuku.apk" https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk
unzip -p "$out/shizuku.apk" lib/x86_64/libshizuku.so > "$out/shizuku-starter"
"$adb_bin" install -r "$out/shizuku.apk"
"$adb_bin" install -r app/build/activity-fixtures/fixture-v1.apk
curl --fail --location --retry 3 --output "$out/weather.apk" https://github.com/breezy-weather/breezy-weather/releases/download/v6.2.2/breezy-weather-v6.2.2_freenet.apk
"$adb_bin" install -r "$out/weather.apk"
"$adb_bin" shell cmd locale set-app-locales org.breezyweather --user current --locales zh-CN
"$adb_bin" install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
"$adb_bin" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$adb_bin" shell am start -W -n moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity
"$adb_bin" push "$out/shizuku-starter" /data/local/tmp/shizuku-starter
"$adb_bin" shell chmod 755 /data/local/tmp/shizuku-starter
apk_path=$("$adb_bin" shell pm path moe.shizuku.privileged.api | head -1 | sed 's/^package://' | tr -d '\r')
"$adb_bin" shell /data/local/tmp/shizuku-starter --apk="$apk_path"
"$adb_bin" shell input keyevent KEYCODE_HOME
"$adb_bin" shell wm size 410x502
"$adb_bin" shell wm density 160
"$adb_bin" shell settings put system font_scale 1.0
"$adb_bin" shell settings put system show_touches 1
"$adb_bin" shell settings put global window_animation_scale 0.5
"$adb_bin" shell settings put global transition_animation_scale 0.5
"$adb_bin" shell settings put global animator_duration_scale 0.5
"$adb_bin" shell settings put system screen_off_timeout 1800000
"$adb_bin" shell pm grant com.gaozay.smartflight android.permission.POST_NOTIFICATIONS
"$adb_bin" shell dumpsys deviceidle whitelist +com.gaozay.smartflight
"$adb_bin" shell svc wifi disable
# Keep screenrecord's adb connection open on the runner; avoid shell-child teardown.
python3 - "$adb_bin" "$out" <<'PYWATCH' &
import subprocess,sys,time,pathlib
adb,out=sys.argv[1],pathlib.Path(sys.argv[2]);last='';proc=None;logs=None
while True:
 name=subprocess.run([adb,'shell','cat /sdcard/Download/smartflight-promo/active-clip'],capture_output=True,text=True).stdout.strip()
 if name!=last:
  if proc and proc.poll() is None:
   subprocess.run([adb,'shell','pkill -2 screenrecord'],capture_output=True);proc.wait(timeout=10)
  if logs: logs.close()
  if name:
   logs=(out/(name+'-record.log')).open('w')
   proc=subprocess.Popen([adb,'shell','screenrecord --size 410x502 --bit-rate 5000000 --time-limit 180 /sdcard/Download/smartflight-promo/'+name+'.mp4'],stdout=logs,stderr=logs)
  last=name
 time.sleep(.15)
PYWATCH
watcher_pid=$!
"$adb_bin" shell am instrument -w -r -e class com.gaozay.smartflight.promo.PromoCaptureTest com.gaozay.smartflight.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
python3 - "$out/instrumentation.txt" <<'PY'
import pathlib,sys
s=pathlib.Path(sys.argv[1]).read_text()
assert 'OK (1 test)' in s and 'FAILURES!!!' not in s, s
PY
