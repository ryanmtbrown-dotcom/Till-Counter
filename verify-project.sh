#!/usr/bin/env bash
set -euo pipefail
test -f app/src/main/java/com/tillcounter/app/MainActivity.kt
test -f app/src/main/res/drawable-nodpi/splash.png
test -f signing/till-counter.jks
grep -Fq 'compileSdk = 36' app/build.gradle.kts
grep -Fq 'targetSdk = 36' app/build.gradle.kts
grep -Fq 'version "8.13.2"' build.gradle.kts
grep -Fq 'version "2.2.21"' build.gradle.kts
grep -Fq 'androidx.appcompat:appcompat:1.7.1' app/build.gradle.kts
if grep -R -E 'WebView|android_asset|appassets|javascript|uses-permission' app/src/main app/build.gradle.kts; then
  echo 'Unexpected browser/network/permission implementation found' >&2; exit 1
fi
echo PROJECT_STRUCTURE_PASS
