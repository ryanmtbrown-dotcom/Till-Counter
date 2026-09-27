#!/usr/bin/env bash
set -euo pipefail
PACKAGE='com.tillcounter.app'
ACTIVITY='.MainActivity'
APK='app/build/outputs/apk/debug/app-debug.apk'
LOG='device-smoke-logcat.txt'
SCREEN='device-smoke.png'

test -s "$APK"
adb install -r "$APK"
adb logcat -c
START_OUTPUT="$(adb shell am start -W -n "$PACKAGE/$ACTIVITY")"
printf '%s\n' "$START_OUTPUT"
echo "$START_OUTPUT" | grep -q 'Status: ok'
# Splash is intentionally six seconds; wait beyond it and prove the activity remains alive/resumed.
sleep 8
# Prove the bundled WebView rendered the actual first workflow screen, not merely a living Activity.
adb shell uiautomator dump /sdcard/till-counter-ui.xml >/dev/null
adb pull /sdcard/till-counter-ui.xml till-counter-ui.xml >/dev/null
grep -q 'Store Charges' till-counter-ui.xml
grep -q 'Running total' till-counter-ui.xml
grep -q '\$0.00' till-counter-ui.xml
PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
test -n "$PID"
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PACKAGE/.MainActivity"
adb logcat -d -v threadtime > "$LOG"
if grep -E "FATAL EXCEPTION|Process: $PACKAGE.*has died|Unable to instantiate activity|AndroidRuntime.*$PACKAGE" "$LOG"; then
  echo 'Fatal runtime evidence found.' >&2
  exit 1
fi
adb exec-out screencap -p > "$SCREEN"
test -s "$SCREEN"
echo "DEVICE_SMOKE_PASS pid=$PID"
