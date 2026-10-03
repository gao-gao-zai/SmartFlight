#!/usr/bin/env bash
set -euo pipefail

artifacts=app/build/emulator-artifacts
mkdir -p "$artifacts"

collect_artifacts() {
    adb logcat -d > "$artifacts/logcat.txt" || true
    adb pull /sdcard/Download/smartflight-localization "$artifacts/screenshots" || true
    adb pull /sdcard/Download/smartflight-tiles "$artifacts/tile-screenshots" || true
}
trap collect_artifacts EXIT

adb shell getprop ro.build.version.release > "$artifacts/android-version.txt"
adb shell getprop ro.product.cpu.abilist > "$artifacts/device-abis.txt"
adb logcat -c
bash gradlew connectedDebugAndroidTest -PemulatorAbi=x86_64 --no-daemon
