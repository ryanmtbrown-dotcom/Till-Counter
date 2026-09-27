#!/usr/bin/env bash
set -euo pipefail
APK="$1"; PKG=com.tillcounter.app; ACT=.MainActivity
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT" | tee start.txt
grep -Fq 'Status: ok' start.txt
sleep 7
adb shell dumpsys activity activities | grep -F 'mResumedActivity' | grep -F "$PKG"
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
assert_text 'Store Charges'; assert_text '$0.00'
for k in 5 4 . 2 3; do tap_text "$k"; done
tap_text 'ADD'; assert_text '$54.23'
tap_text 'NEXT'; assert_text 'Gift Certificates'
tap_text 'NEXT'; assert_text 'Vendor Coupons'
tap_text 'NEXT'; assert_text 'Checks'
tap_text 'NEXT'; assert_text 'Loans'
tap_text 'NEXT'; assert_text 'Cash'; assert_text '$100 bills'
tap_text '2'; tap_text 'NEXT'; assert_text '$50 bills'
for i in $(seq 1 11); do tap_text 'NEXT'; done
assert_text 'Pennies'; tap_text 'FINISH'
assert_text 'Till Summary'; assert_text '$254.23'
tap_text 'BACK'; assert_text 'Pennies'
tap_text 'FINISH'; tap_text 'NEW COUNT'; assert_text 'Store Charges'; assert_text '$0.00'
adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/$ACT" >/dev/null; sleep 7
assert_text 'Store Charges'
adb logcat -d -v threadtime > device.log
if grep -E 'FATAL EXCEPTION|AndroidRuntime.*Process: com\.tillcounter\.app' device.log; then cat device.log; exit 1; fi
adb exec-out screencap -p > final-screen.png
echo DEVICE_FUNCTIONAL_PASS
