#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$HOME/Till-Counter"
REPO='ryanmtbrown-dotcom/Till-Counter'
VERSION='0.2.0'
TAG="v$VERSION"
APK="Till-Counter-$TAG.apk"
LOGDIR="$HOME/Till-Counter-Build-Logs"
OUT="$HOME/storage/downloads"
mkdir -p "$LOGDIR" "$OUT"

echo '=== TILL COUNTER: SOURCE -> PROVEN APK -> RELEASE -> PHONE ==='
gh auth status
grep -q 'versionName = "0.2.0"' app/build.gradle.kts
test -f app/src/main/java/com/tillcounter/app/MainActivity.java
test ! -e app/src/main/java/com/tillcounter/app/MainActivity.kt

git add -A
if git diff --cached --quiet; then
  echo 'ERROR: Nothing new is staged. Refusing to pretend a new deployment occurred.' >&2
  exit 1
fi
git commit -m "Till Counter $TAG: prove full Android release path"
SHA="$(git rev-parse HEAD)"
git push origin main

echo "Waiting for workflow for exact commit $SHA ..."
RUN_ID=''
for _ in $(seq 1 90); do
  RUN_ID="$(gh run list --repo "$REPO" --workflow build-apk.yml --commit "$SHA" --limit 1 --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)"
  [ -n "$RUN_ID" ] && break
  sleep 2
done
[ -n "$RUN_ID" ] || { echo 'ERROR: exact workflow run never appeared.' >&2; exit 1; }

echo "=== LIVE PROOF RUN $RUN_ID ==="
set +e
gh run watch "$RUN_ID" --repo "$REPO" --exit-status
RC=$?
set -e
gh run view "$RUN_ID" --repo "$REPO" --log > "$LOGDIR/$TAG-$RUN_ID.log" 2>&1 || true
if [ "$RC" -ne 0 ]; then
  gh run view "$RUN_ID" --repo "$REPO" --log-failed | tee "$LOGDIR/$TAG-$RUN_ID-FAILED.log" || true
  echo "FAILED. Logs: $LOGDIR/$TAG-$RUN_ID-FAILED.log" >&2
  exit "$RC"
fi

# A green workflow means emulator startup and release round-trip verification both passed.
RELEASE_SHA="$(gh release view "$TAG" --repo "$REPO" --json targetCommitish --jq '.targetCommitish')"
[ "$RELEASE_SHA" = "$SHA" ] || { echo "ERROR: release target $RELEASE_SHA != deployed commit $SHA" >&2; exit 1; }
TMP="$HOME/.till-counter-$TAG"
rm -rf "$TMP"; mkdir -p "$TMP"
gh release download "$TAG" --repo "$REPO" --pattern "$APK" --dir "$TMP"
test -s "$TMP/$APK"
cp -f "$TMP/$APK" "$OUT/$APK"
cmp "$TMP/$APK" "$OUT/$APK"
rm -rf "$TMP"

echo '=== COMPLETE START-TO-FINISH PASS ==='
echo "Commit:  $SHA"
echo "Run:     $RUN_ID"
echo "APK:     $OUT/$APK"
echo "SHA-256: $(sha256sum "$OUT/$APK" | awk '{print $1}')"
echo "Release: $(gh release view "$TAG" --repo "$REPO" --json url --jq '.url')"
echo "Log:     $LOGDIR/$TAG-$RUN_ID.log"
