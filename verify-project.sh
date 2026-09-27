#!/usr/bin/env bash
set -euo pipefail
test -f app/src/main/java/com/tillcounter/app/MainActivity.kt
test -f app/src/main/res/drawable-nodpi/splash.png
test -f app/src/main/res/xml/file_paths.xml
test -f signing/till-counter.jks
grep -Fq 'compileSdk = 36' app/build.gradle.kts
grep -Fq 'targetSdk = 36' app/build.gradle.kts
grep -Fq 'minSdk = 24' app/build.gradle.kts
! grep -Fq 'screenOrientation="portrait"' app/src/main/AndroidManifest.xml
grep -Fq 'version "8.13.2"' build.gradle.kts
grep -Fq 'version "2.2.21"' build.gradle.kts
grep -Fq 'androidx.appcompat:appcompat:1.7.1' app/build.gradle.kts
if grep -R -E 'WebView|android_asset|appassets|javascript' app/src/main app/build.gradle.kts; then
  echo 'Unexpected embedded browser implementation found' >&2; exit 1
fi
mapfile -t perms < <(grep -o 'android.permission.[A-Z_]*' app/src/main/AndroidManifest.xml | sort -u)
expected=("android.permission.INTERNET" "android.permission.REQUEST_INSTALL_PACKAGES")
if [[ "${perms[*]}" != "${expected[*]}" ]]; then
  printf 'Unexpected Android permission set: %s\n' "${perms[*]}" >&2; exit 1
fi
grep -Fq 'androidx.core.content.FileProvider' app/src/main/AndroidManifest.xml
echo PROJECT_STRUCTURE_PASS
