#!/usr/bin/env bash
set -euo pipefail
fixture_dir=app/build/activity-fixtures
sdk_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/35.0.0"
android_jar="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$fixture_dir/classes" "$fixture_dir/dex"
javac -source 8 -target 8 -cp "$android_jar" -d "$fixture_dir/classes" scripts/activity-fixture/*.java
jar cf "$fixture_dir/classes.jar" -C "$fixture_dir/classes" .
"$sdk_tools/d8" --lib "$android_jar" --min-api 26 --output "$fixture_dir/dex" "$fixture_dir/classes.jar"
if [[ ! -f "$fixture_dir/fixture.jks" ]]; then
    keytool -genkeypair -keystore "$fixture_dir/fixture.jks" -storepass android -keypass android \
        -alias fixture -keyalg RSA -validity 30 -dname 'CN=Activity Rule Test' -noprompt
fi
for version in 1 2; do
    second=SecondActivity
    if [[ "$version" == 2 ]]; then second=ReplacementActivity; fi
    cat > "$fixture_dir/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.gaozay.smartflight.activityfixture" android:versionCode="$version" android:versionName="$version">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:label="Activity Rule Fixture" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".FirstActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".$second" android:exported="true" />
        <activity android:name=".PrivateActivity" android:exported="false" />
        <activity android:name=".DisabledActivity" android:exported="true" android:enabled="false" />
        <activity-alias android:name=".EntryAlias" android:targetActivity=".FirstActivity" android:exported="true" />
    </application>
</manifest>
MANIFEST
    "$sdk_tools/aapt2" link -I "$android_jar" --manifest "$fixture_dir/AndroidManifest.xml" -o "$fixture_dir/unsigned.apk"
    zip -j -q "$fixture_dir/unsigned.apk" "$fixture_dir/dex/classes.dex"
    "$sdk_tools/zipalign" -f 4 "$fixture_dir/unsigned.apk" "$fixture_dir/aligned.apk"
    "$sdk_tools/apksigner" sign --ks "$fixture_dir/fixture.jks" --ks-key-alias fixture \
        --ks-pass pass:android --key-pass pass:android --out "$fixture_dir/fixture-v$version.apk" "$fixture_dir/aligned.apk"
done
