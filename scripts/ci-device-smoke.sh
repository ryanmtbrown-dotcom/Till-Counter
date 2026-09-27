#!/usr/bin/env bash
set -euo pipefail
PACKAGE='com.tillcounter.app'
ACTIVITY='.MainActivity'
APK='app/build/outputs/apk/debug/app-debug.apk'
LOG='device-smoke-logcat.txt'
SCREEN='device-smoke.png'
UI='till-counter-ui.xml'

test -s "$APK"
adb install -r "$APK"
adb logcat -c
START_OUTPUT="$(adb shell am start -W -n "$PACKAGE/$ACTIVITY")"
printf '%s\n' "$START_OUTPUT"
echo "$START_OUTPUT" | grep -Fq 'Status: ok'

# The branded splash intentionally lasts six seconds.
sleep 8

# Always collect evidence before assertions so a failure remains diagnosable.
adb logcat -d -v threadtime > "$LOG"
adb exec-out screencap -p > "$SCREEN"
test -s "$SCREEN"
adb shell uiautomator dump /sdcard/till-counter-ui.xml >/dev/null
adb pull /sdcard/till-counter-ui.xml "$UI" >/dev/null
echo '=== RENDERED UI TREE ==='
cat "$UI"

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
test -n "$PID"
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | grep -Fq "$PACKAGE/.MainActivity"
if grep -E "FATAL EXCEPTION|Process: $PACKAGE.*has died|Unable to instantiate activity|AndroidRuntime.*$PACKAGE" "$LOG"; then
  echo 'Fatal runtime evidence found.' >&2
  exit 1
fi

# Prove the post-splash application itself rendered, not merely that the Activity stayed alive.
grep -Fq 'Store Charges' "$UI"
grep -Fq 'Running total' "$UI"
grep -Fq '$0.00' "$UI"
echo "DEVICE_SMOKE_PASS pid=$PID"
