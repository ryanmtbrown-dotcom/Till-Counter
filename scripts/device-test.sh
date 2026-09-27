#!/usr/bin/env bash
set -euo pipefail
APK="$1"; PKG=com.tillcounter.app; ACT=.MainActivity
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT" | tee start.txt
grep -Fq 'Status: ok' start.txt
test -n "$(adb shell pidof "$PKG")"
dump(){ adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null; }
tap_text(){ dump; python3 - "$1" <<'PY'
import re,sys,subprocess,xml.etree.ElementTree as ET
needle=sys.argv[1]; root=ET.parse('ui.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text')==needle:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        subprocess.check_call(['adb','shell','input','tap',str((int(m[1])+int(m[3]))//2),str((int(m[2])+int(m[4]))//2)])
        sys.exit(0)
raise SystemExit('missing UI text: '+needle)
PY
sleep .3
}
assert_text(){ dump; grep -Fq "text=\"$1\"" ui.xml || { echo "missing text: $1"; cat ui.xml; exit 1; }; }
wait_text(){
  for i in $(seq 1 20); do
    dump
    grep -Fq "text=\"$1\"" ui.xml && return 0
    sleep 1
  done
  echo "timed out waiting for text: $1"; cat ui.xml; exit 1
}
wait_text 'Store Charges'; assert_text '$0.00'
# Single-entry contract: NEXT must commit the pending amount without requiring +.
for k in 5 4 . 2 3; do tap_text "$k"; done
tap_text 'NEXT'; assert_text 'Gift Certificates'
# Multi-entry contract: + commits an amount and clears the entry for another.
for k in 1 0 . 0 0; do tap_text "$k"; done
tap_text '+'; assert_text '$10.00'
for k in 2 . 5 0; do tap_text "$k"; done
tap_text 'NEXT'; assert_text 'Vendor Coupons'
tap_text 'NEXT'; assert_text 'Checks'
tap_text 'NEXT'; assert_text 'Loans'
tap_text 'NEXT'; assert_text 'Cash'; assert_text '$100 bills'
tap_text '2'; tap_text 'NEXT'; assert_text '$50 bills'
for i in $(seq 1 11); do tap_text 'NEXT'; done
assert_text 'Pennies'; tap_text 'FINISH'
assert_text 'Till Summary'; assert_text '$266.73'; assert_text 'Version 1.1.0'; assert_text 'CHECK FOR UPDATE'
tap_text 'BACK'; assert_text 'Pennies'
tap_text 'FINISH'; tap_text 'NEW COUNT'; assert_text 'Store Charges'; assert_text '$0.00'
adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/$ACT" >/dev/null
wait_text 'Store Charges'
adb logcat -d -v threadtime > device.log
if grep -E 'FATAL EXCEPTION|AndroidRuntime.*Process: com\.tillcounter\.app' device.log; then cat device.log; exit 1; fi
adb exec-out screencap -p > final-screen.png
echo DEVICE_FUNCTIONAL_PASS
