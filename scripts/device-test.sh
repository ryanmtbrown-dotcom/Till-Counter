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
scroll=None
for n in root.iter('node'):
    if n.attrib.get('class')=='android.widget.ScrollView':
        sm=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        if sm: scroll=tuple(map(int,sm.groups()))
for n in root.iter('node'):
    if n.attrib.get('text')==needle:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        if not m: continue
        x1,y1,x2,y2=map(int,m.groups()); x=(x1+x2)//2; y=(y1+y2)//2
        # UIAutomator can expose a partially clipped descendant. Never tap it
        # unless its center is safely inside the app ScrollView.
        if scroll:
            sx1,sy1,sx2,sy2=scroll
            safe_top=max(sy1+80, 120)
            safe_bottom=min(sy2-180, 1450)
            if not (sx1+8 <= x <= sx2-8 and safe_top <= y <= safe_bottom):
                continue
        subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
        sys.exit(0)
sys.exit(1)
PY
    then
      sleep .3
      FG="$(adb shell dumpsys activity activities | grep -m1 'mResumedActivity\|mFocusedActivity' || true)"
      echo "$FG" | grep -Fq 'com.tillcounter.app/.MainActivity' || {
        echo "Till Counter lost foreground immediately after tapping: $needle"
        echo "$FG"
        exit 1
      }
      return 0
    fi
    # Only Settings/Summary are allowed to scroll. Counting screens intentionally
    # expose every control in the fixed viewport, so absence there is a failure.
    if ! python3 <<'PY'
import re,subprocess,xml.etree.ElementTree as ET
root=ET.parse('ui.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('class')=='android.widget.ScrollView':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        x=(int(m[1])+int(m[3]))//2
        top=int(m[2]); bottom=int(m[4])
        y1=top+int((bottom-top)*0.72); y2=top+int((bottom-top)*0.32)
        subprocess.check_call(['adb','shell','input','swipe',str(x),str(y1),str(x),str(y2),'250'])
        raise SystemExit(0)
raise SystemExit(1)
PY
    then
      echo "Control is not visible in fixed Till Counter viewport: $needle"
      cat ui.xml
      exit 1
    fi
    sleep .2
  done
  echo "missing UI text after bounded in-app scroll search: $needle"
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
tap_text '⚙'; assert_text 'SETTINGS'; assert_text 'Vendor Coupons'; assert_text 'CHECK FOR UPDATE'
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
dump
python3 <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('ui.xml').getroot()
vals=[n.attrib.get('text','') for n in root.iter('node') if n.attrib.get('class','').endswith('EditText')]
if '300.00' not in vals:
    raise SystemExit('Base Till field did not contain exact value 300.00: '+repr(vals))
PY
# Base Till deliberately suppresses the Android soft keyboard. Prove the
# Activity stayed foreground after entering the value before saving.
FOREGROUND="$(adb shell dumpsys activity activities | grep -m1 'mResumedActivity\|mFocusedActivity' || true)"
echo "$FOREGROUND" | grep -Fq 'com.tillcounter.app/.MainActivity' || {
  echo "Till Counter lost foreground during Base Till entry: $FOREGROUND"
  exit 1
}
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
tap_text '⚙'; assert_text 'SETTINGS'; assert_text 'Version 1.2.0'; assert_text 'CHECK FOR UPDATE'; tap_text 'DONE'
tap_text 'BACK'; assert_text 'Pennies'
tap_text 'FINISH'; tap_text 'NEW COUNT'; assert_text 'Store Charges'; assert_text '$0.00'
adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/$ACT" >/dev/null
wait_text 'Store Charges'
adb logcat -d -v threadtime > device.log
if grep -E 'FATAL EXCEPTION|AndroidRuntime.*Process: com\.tillcounter\.app' device.log; then cat device.log; exit 1; fi
adb exec-out screencap -p > final-screen.png
echo DEVICE_FUNCTIONAL_PASS
