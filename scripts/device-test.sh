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
assert_app_foreground(){
  local context="$1"
  test -n "$(adb shell pidof "$PKG" 2>/dev/null || true)" || {
    echo "Till Counter process missing after: $context"
    exit 1
  }
  dump
  python3 - "$PKG" "$context" <<'PY'
import sys,xml.etree.ElementTree as ET
pkg,context=sys.argv[1],sys.argv[2]
root=ET.parse('ui.xml').getroot()
packages={n.attrib.get('package','') for n in root.iter('node')}
if pkg not in packages:
    raise SystemExit("Till Counter is not the visible UI after %s; packages=%r" % (context, sorted(packages)))
PY
}
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
    if n.attrib.get('text')==needle or n.attrib.get('content-desc')==needle:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib['bounds'])
        if not m: continue
        x1,y1,x2,y2=map(int,m.groups()); x=(x1+x2)//2; y=(y1+y2)//2
        # Apply ScrollView clipping rules only to nodes that actually intersect
        # the ScrollView. Fixed siblings such as Settings DONE remain tappable.
        if scroll:
            sx1,sy1,sx2,sy2=scroll
            intersects = not (x2 <= sx1 or x1 >= sx2 or y2 <= sy1 or y1 >= sy2)
            if intersects:
                safe_top=max(sy1+80, 120)
                safe_bottom=min(sy2-180, 1450)
                if not (sx1+8 <= x <= sx2-8 and safe_top <= y <= safe_bottom):
                    continue
        if x < 8 or y < 24 or x > 1072 or y > 1770:
            continue
        subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
        sys.exit(0)
sys.exit(1)
PY
    then
      sleep .3
      assert_app_foreground "tapping $needle"
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

# SETTINGS: all workflow stages enabled; persistent base till.
tap_text 'Settings'; wait_text 'SETTINGS'
for name in 'Store Charges' 'Gift Certificates' 'Vendor Coupons' 'Checks' 'Loans'; do assert_text "$name"; done
assert_text 'CHECK FOR UPDATE'
# Base Till is the only EditText.
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
tap_text 'DONE'; wait_text 'Store Charges'

# MONEY EDITING: C, backspace, decimal, +, Remove, BACK/NEXT and pending-NEXT commit.
tap_text '9'; tap_text 'C'; assert_text '$0.00'
for k in 1 . 0 0; do tap_text "$k"; done
tap_text '+'; wait_text 'Store Charges'; assert_text '$1.00'; assert_text 'Remove'
tap_text 'Remove'; wait_text 'Store Charges'; assert_text '$0.00'
for k in 5 4 . 2 4; do tap_text "$k"; done
tap_text '⌫'; tap_text '3'; assert_text '$54.23'
tap_text 'NEXT'; wait_text 'Gift Certificates'
for k in 1 0 . 0 0; do tap_text "$k"; done
tap_text 'NEXT'; wait_text 'Vendor Coupons'
# Prove BACK preserves committed prior-stage data.
tap_text 'BACK'; wait_text 'Gift Certificates'; assert_text '$10.00'
tap_text 'NEXT'; wait_text 'Vendor Coupons'
for k in 2 . 5 0; do tap_text "$k"; done
tap_text 'NEXT'; wait_text 'Checks'
for k in 3 . 7 5; do tap_text "$k"; done
tap_text 'NEXT'; wait_text 'Loans'
for k in 4 . 0 0; do tap_text "$k"; done
tap_text 'NEXT: CASH'; wait_text 'Cash'; assert_text '$100 bills'

# CASH: nonzero value in every denomination. Coins exercise both loose and roll modes.
# Bills: 100,50,20,10,5,2,1 = counts 1..7.
for n in 1 2 3 4 5 6 7; do tap_text "$n"; tap_text 'NEXT'; wait_text 'Cash'; done
# $1 coins, half dollars, quarters, dimes, nickels: loose=1 and rolls=1.
for label in '$1 coins' 'Half dollars' 'Quarters' 'Dimes' 'Nickels'; do
  wait_text "$label"; tap_text '1'; tap_text 'ROLLS: 0'; tap_text '1'; tap_text 'NEXT'; wait_text 'Cash'
done
# Pennies: exercise C/backspace in count mode, loose=12, rolls=1.
wait_text 'Pennies'; tap_text '9'; tap_text 'C'; tap_text '1'; tap_text '3'; tap_text '⌫'; tap_text '2'
tap_text 'ROLLS: 0'; tap_text '1'; tap_text 'FINISH'; wait_text 'Till Summary'

# Expected money stages = 54.23 + 10 + 2.50 + 3.75 + 4 = 74.48.
# Expected cash = 100 +100 +60 +40 +25 +12 +7 +26 +10.50 +10.25 +5.10 +2.05 +0.62 = 398.52.
# TOTAL is deliberately cash-only: 398.52; base = 300.00; drop = 98.52.\n# Non-cash stages remain separately visible and must not enter TOTAL/drop.\nassert_text '$398.52'; assert_text 'BASE TILL  $300.00'; assert_text 'DROP  $98.52'
for name in 'Store Charges' 'Gift Certificates' 'Vendor Coupons' 'Checks' 'Loans' 'Cash'; do assert_text "$name"; done

# Summary BACK returns to final cash denomination with values intact.
tap_text 'BACK'; wait_text 'Pennies'; assert_text 'LOOSE: 12'; assert_text 'ROLLS: 1'
tap_text 'FINISH'; wait_text 'Till Summary'

# SETTINGS persistence survives force-stop/process restart.
tap_text 'Settings'; wait_text 'SETTINGS'; assert_text 'Version 1.2.0'
dump
python3 <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('ui.xml').getroot()
vals=[n.attrib.get('text','') for n in root.iter('node') if n.attrib.get('class','').endswith('EditText')]
if '300.00' not in vals: raise SystemExit('Persisted Base Till value missing: '+repr(vals))
PY
tap_text 'DONE'; wait_text 'Till Summary'

# NEW COUNT clears transactional state but not persistent Settings.
tap_text 'NEW COUNT'; wait_text 'Store Charges'; assert_text '$0.00'
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT" >/dev/null
wait_text 'Store Charges'; assert_text '$0.00'
tap_text 'Settings'; wait_text 'SETTINGS'
dump
python3 <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('ui.xml').getroot()
vals=[n.attrib.get('text','') for n in root.iter('node') if n.attrib.get('class','').endswith('EditText')]
if '300.00' not in vals: raise SystemExit('Base Till did not survive process restart: '+repr(vals))
checks={n.attrib.get('text',''):n.attrib.get('checked') for n in root.iter('node') if n.attrib.get('class','').endswith('CheckBox')}
bad={k:v for k,v in checks.items() if v!='true'}
if bad: raise SystemExit('Workflow settings not persisted/enabled: '+repr(bad))
PY
tap_text 'DONE'; wait_text 'Store Charges'

adb logcat -d -v threadtime > device.log
if grep -E 'FATAL EXCEPTION|AndroidRuntime.*Process: com\.tillcounter\.app' device.log; then cat device.log; exit 1; fi
adb exec-out screencap -p > final-screen.png
echo DEVICE_FUNCTIONAL_PASS
