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
# Prove bundled JavaScript completed the six-second splash and entered the application.
adb logcat -d -v threadtime > "$LOG"
grep -q 'TillCounterProof.*APP_READY' "$LOG"
PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
test -n "$PID"
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PACKAGE/.MainActivity"
if grep -E "FATAL EXCEPTION|Process: $PACKAGE.*has died|Unable to instantiate activity|AndroidRuntime.*$PACKAGE" "$LOG"; then
  echo 'Fatal runtime evidence found.' >&2
  exit 1
fi
echo "DEVICE_SMOKE_PASS pid=$PID"
