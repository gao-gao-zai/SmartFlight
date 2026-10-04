#!/usr/bin/env bash
set -euo pipefail
build_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/35.0.0"
apk_dir=app/build/outputs/apk/release
report_dir=app/build/release-preview-verification
mkdir -p "$report_dir"
shopt -s nullglob
apks=("$apk_dir"/*.apk)
if (( ${#apks[@]} == 0 )); then echo 'No release APK was produced' >&2; exit 1; fi
for apk in "${apks[@]}"; do
    name="$(basename "$apk")"
    "$build_tools/apksigner" verify --verbose --print-certs "$apk" > "$report_dir/$name.signing.txt"
    "$build_tools/aapt2" dump badging "$apk" > "$report_dir/$name.badging.txt"
    python3 - "$report_dir/$name.badging.txt" <<'PY'
import pathlib,sys
badging=pathlib.Path(sys.argv[1]).read_text()
assert "package: name='com.gaozay.smartflight.preview'" in badging, badging
assert 'application-debuggable' not in badging, 'The release preview must not be debuggable'
assert "sdkVersion:'26'" in badging, badging
print('Verified signed, non-debuggable Release preview:',pathlib.Path(sys.argv[1]).name)
PY
    python3 scripts/verify_shizuku_release.py "$apk" "$build_tools/dexdump"
    sha256sum "$apk" >> "$report_dir/SHA256SUMS"
done
