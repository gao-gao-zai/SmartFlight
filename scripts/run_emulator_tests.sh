#!/usr/bin/env bash
set -euo pipefail

artifacts=app/build/emulator-artifacts
sdk_adb="${ANDROID_HOME:?ANDROID_HOME is required}/platform-tools/adb"
mkdir -p "$artifacts"

collect_artifacts() {
    # Gradle also uses the SDK's adb. Mixing versions can restart the server
    # and briefly leave the emulator offline while collecting test evidence.
    timeout 30s "$sdk_adb" wait-for-device || return 0
    "$sdk_adb" logcat -d > "$artifacts/logcat.txt" || true
    "$sdk_adb" pull /sdcard/Download/smartflight-localization "$artifacts/screenshots" || true
    "$sdk_adb" pull /sdcard/Download/smartflight-activities "$artifacts/activity-screenshots" || true
    "$sdk_adb" pull /sdcard/Download/smartflight-tiles "$artifacts/tile-screenshots" || true
}
trap collect_artifacts EXIT

"$sdk_adb" shell getprop ro.build.version.release > "$artifacts/android-version.txt"
"$sdk_adb" shell getprop ro.product.cpu.abilist > "$artifacts/device-abis.txt"
"$sdk_adb" logcat -c
bash scripts/build_package_sync_fixtures.sh
bash scripts/build_activity_fixtures.sh
"$sdk_adb" push app/build/package-sync-fixtures/fixture-v1.apk /data/local/tmp/smartflight-fixture-v1.apk
"$sdk_adb" push app/build/package-sync-fixtures/fixture-v2.apk /data/local/tmp/smartflight-fixture-v2.apk
# Install before SmartFlight is launched to exercise process-start reconciliation.
"$sdk_adb" install -r app/build/package-sync-fixtures/fixture-v1.apk
"$sdk_adb" push app/build/activity-fixtures/fixture-v1.apk /data/local/tmp/smartflight-activity-v1.apk
"$sdk_adb" push app/build/activity-fixtures/fixture-v2.apk /data/local/tmp/smartflight-activity-v2.apk
"$sdk_adb" install -r app/build/activity-fixtures/fixture-v1.apk
bash gradlew connectedDebugAndroidTest -PemulatorAbi=x86_64 --no-daemon
