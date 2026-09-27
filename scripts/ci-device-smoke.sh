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
echo "$START_OUTPUT" | grep -Fq 'Status: ok'

# Splash is six seconds; wait for the native DOM proof scheduled after page load.
sleep 9
adb logcat -d -v threadtime > "$LOG"
adb exec-out screencap -p > "$SCREEN"
test -s "$SCREEN"

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
test -n "$PID"
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | grep -Fq "$PACKAGE/.MainActivity"
if grep -E "FATAL EXCEPTION|Process: $PACKAGE.*has died|Unable to instantiate activity|AndroidRuntime.*$PACKAGE" "$LOG"; then
  echo 'Fatal runtime evidence found.' >&2
  exit 1
fi
echo '=== WEBVIEW DIAGNOSTICS ==='
grep -E 'TillCounterProof|chromium|WebView|cr_' "$LOG" | tail -200 || true
grep -F 'TillCounterProof' "$LOG"
grep -F 'TillCounterProof' "$LOG" | grep -Fq 'APP_STATE="READY"'
echo "DEVICE_SMOKE_PASS pid=$PID"
