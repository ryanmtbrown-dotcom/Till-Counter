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
tap_text(){
  local needle="$1"
  for attempt in $(seq 1 8); do
    dump
    if python3 - "$needle" <<'PY'
import re,sys,subprocess,xml.etree.ElementTree as ET
needle=sys.argv[1]; root=ET.parse('ui.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text')==needle:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        subprocess.check_call(['adb','shell','input','tap',str((int(m[1])+int(m[3]))//2),str((int(m[2])+int(m[4]))//2)])
        sys.exit(0)
sys.exit(1)
PY
    then
      sleep .3
      return 0
    fi
    # Native ScrollView exposes only visible descendants to uiautomator.
    # Search a bounded distance downward before declaring the control absent.
    adb shell input swipe 540 1500 540 650 250 >/dev/null
    sleep .2
  done
  echo "missing UI text after bounded scroll search: $needle"
  cat ui.xml
  exit 1
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
# Settings contract: disable Vendor Coupons and set a persistent $300 base till.
tap_text '⚙'; assert_text 'Settings'; assert_text 'Vendor Coupons'; assert_text 'CHECK FOR UPDATE'
tap_text 'Vendor Coupons'
# EditText is the only editable field on Settings; inject baseline through focused field.
dump
python3 - <<'PY'
import re,subprocess,xml.etree.ElementTree as ET
root=ET.parse('ui.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('class','').endswith('EditText'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        subprocess.check_call(['adb','shell','input','tap',str((int(m[1])+int(m[3]))//2),str((int(m[2])+int(m[4]))//2)])
        subprocess.check_call(['adb','shell','input','text','300.00'])
        raise SystemExit(0)
raise SystemExit('missing base till EditText')
PY
# Explicitly dismiss the IME before using Settings actions. On Android 7 the
# keyboard can otherwise consume/redirect subsequent automation taps.
adb shell input keyevent 4
sleep .3
tap_text 'SAVE BASE TILL'
tap_text 'DONE'
wait_text 'Store Charges'
assert_text '$0.00'
# Single-entry contract: NEXT must commit the pending amount without requiring +.
for k in 5 4 . 2 3; do tap_text "$k"; done
tap_text 'NEXT'; assert_text 'Gift Certificates'
# Multi-entry contract: + commits an amount and clears the entry for another.
for k in 1 0 . 0 0; do tap_text "$k"; done
tap_text '+'; assert_text '$10.00'
for k in 2 . 5 0; do tap_text "$k"; done
tap_text 'NEXT'; assert_text 'Checks'
tap_text 'NEXT'; assert_text 'Loans'
tap_text 'NEXT: CASH'; assert_text 'Cash'; assert_text '$100 bills'
tap_text '2'; tap_text 'NEXT'; assert_text '$50 bills'
# Advance to quarters (100, 50, 20, 10, 5, 2, 1 bills, $1 coin, half dollar).
for i in $(seq 1 9); do tap_text 'NEXT'; done
assert_text 'Quarters'; assert_text 'LOOSE: 0'; assert_text 'ROLLS: 0'
tap_text '5'; tap_text 'ROLLS: 0'; tap_text '2'; tap_text 'NEXT'
# Dimes, nickels, pennies.
tap_text 'NEXT'; tap_text 'NEXT'; assert_text 'Pennies'; tap_text 'FINISH'
assert_text 'Till Summary'; assert_text '$287.98'; assert_text 'BASE TILL  $300.00'; assert_text 'DROP  -$12.02'
tap_text '⚙'; assert_text 'Settings'; assert_text 'Version 1.2.0'; assert_text 'CHECK FOR UPDATE'; tap_text 'DONE'
tap_text 'BACK'; assert_text 'Pennies'
tap_text 'FINISH'; tap_text 'NEW COUNT'; assert_text 'Store Charges'; assert_text '$0.00'
adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/$ACT" >/dev/null
wait_text 'Store Charges'
adb logcat -d -v threadtime > device.log
if grep -E 'FATAL EXCEPTION|AndroidRuntime.*Process: com\.tillcounter\.app' device.log; then cat device.log; exit 1; fi
adb exec-out screencap -p > final-screen.png
echo DEVICE_FUNCTIONAL_PASS
