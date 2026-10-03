#!/usr/bin/env bash
set -euo pipefail

# Manifest-only APKs exercise real PackageManager permissions and protected package broadcasts.
# These fixtures have no application code and are never shipped with SmartFlight.
fixture_dir=app/build/package-sync-fixtures
sdk_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/35.0.0"
mkdir -p "$fixture_dir"
if [[ ! -f "$fixture_dir/fixture.jks" ]]; then
    keytool -genkeypair -keystore "$fixture_dir/fixture.jks" -storepass android -keypass android \
        -alias fixture -keyalg RSA -validity 30 -dname 'CN=Package Sync Test' -noprompt
fi
for version in 1 2; do
    permission=''
    if [[ "$version" == 1 ]]; then
        permission='<uses-permission android:name="android.permission.INTERNET" />'
    fi
    cat > "$fixture_dir/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.gaozay.smartflight.syncfixture" android:versionCode="$version" android:versionName="$version">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
    $permission
    <application android:hasCode="false" android:label="Package Sync Fixture $version">
        <activity android:name="android.app.Activity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
MANIFEST
    "$sdk_tools/aapt2" link -I "$ANDROID_HOME/platforms/android-35/android.jar" \
        --manifest "$fixture_dir/AndroidManifest.xml" -o "$fixture_dir/unsigned.apk"
    "$sdk_tools/zipalign" -f 4 "$fixture_dir/unsigned.apk" "$fixture_dir/aligned.apk"
    "$sdk_tools/apksigner" sign --ks "$fixture_dir/fixture.jks" --ks-key-alias fixture \
        --ks-pass pass:android --key-pass pass:android --out "$fixture_dir/fixture-v$version.apk" "$fixture_dir/aligned.apk"
done
