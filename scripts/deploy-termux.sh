#!/data/data/com.termux/files/usr/bin/bash

set -uo pipefail

cd "$HOME/Till-Counter" || exit 1

REPO="ryanmtbrown-dotcom/Till-Counter"
LOGDIR="$HOME/Till-Counter-Build-Logs"
DOWNLOADS="$HOME/storage/downloads"

mkdir -p "$LOGDIR" "$DOWNLOADS"

echo
echo "========================================"
echo "       TILL COUNTER DEPLOYMENT"
echo "========================================"

VERSION="$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)"

if [ -z "$VERSION" ]; then
    echo "ERROR: Could not determine versionName."
    exit 1
fi

TAG="v$VERSION"
APK="Till-Counter-$TAG.apk"

echo "Version: $TAG"

echo
echo "[1/9] Validating GitHub authentication..."

gh auth status || exit 1

echo
echo "[2/9] Committing project..."

git add -A

if ! git diff --cached --quiet; then
    git commit -m "Build Till Counter $TAG" || exit 1
else
    echo "No uncommitted project changes."
fi

SHA="$(git rev-parse HEAD)"

echo "Commit: $SHA"

echo
echo "[3/9] Pushing exact commit..."

git push origin main || exit 1

echo
echo "[4/9] Waiting for GitHub Actions run..."

RUN_ID=""

for i in $(seq 1 60); do
    RUN_ID="$(
        gh run list \
          --repo "$REPO" \
          --workflow build-apk.yml \
          --commit "$SHA" \
          --limit 1 \
          --json databaseId \
          --jq '.[0].databaseId // empty' 2>/dev/null
    )"

    [ -n "$RUN_ID" ] && break
    sleep 2
done

if [ -z "$RUN_ID" ]; then
    echo "ERROR: No workflow run appeared for commit $SHA"
    exit 1
fi

echo "Run: $RUN_ID"

echo
echo "[5/9] LIVE BUILD WATCH"
echo "----------------------------------------"

gh run watch "$RUN_ID" \
    --repo "$REPO" \
    --exit-status

RESULT=$?

echo
echo "Saving complete build log..."

gh run view "$RUN_ID" \
    --repo "$REPO" \
    --log \
    > "$LOGDIR/$TAG-run-$RUN_ID.log" 2>&1 || true

if [ "$RESULT" -ne 0 ]; then
    echo
    echo "========================================"
    echo "             BUILD FAILED"
    echo "========================================"

    gh run view "$RUN_ID" \
        --repo "$REPO" \
        --log-failed \
        | tee "$LOGDIR/$TAG-run-$RUN_ID-FAILED.log"

    echo
    echo "Full log:"
    echo "$LOGDIR/$TAG-run-$RUN_ID.log"
    echo
    echo "Failure log:"
    echo "$LOGDIR/$TAG-run-$RUN_ID-FAILED.log"

    exit "$RESULT"
fi

echo
echo "[6/9] Verifying GitHub Release..."

RELEASE_URL="$(
    gh release view "$TAG" \
      --repo "$REPO" \
      --json url \
      --jq '.url'
)" || {
    echo "ERROR: Build succeeded but release $TAG does not exist."
    exit 1
}

ASSET_COUNT="$(
    gh release view "$TAG" \
      --repo "$REPO" \
      --json assets \
      --jq "[.assets[] | select(.name == \"$APK\")] | length"
)"

if [ "$ASSET_COUNT" != "1" ]; then
    echo "ERROR: Release exists but $APK is not attached exactly once."
    gh release view "$TAG" --repo "$REPO"
    exit 1
fi

echo "Release verified:"
echo "$RELEASE_URL"

echo
echo "[7/9] Downloading RELEASE APK..."

TMP="$HOME/.till-counter-release-download"
rm -rf "$TMP"
mkdir -p "$TMP"

gh release download "$TAG" \
    --repo "$REPO" \
    --pattern "$APK" \
    --dir "$TMP" || exit 1

if [ ! -s "$TMP/$APK" ]; then
    echo "ERROR: Release APK download is missing or empty."
    exit 1
fi

echo
echo "[8/9] Installing APK into Android Downloads..."

cp -f "$TMP/$APK" "$DOWNLOADS/$APK" || exit 1

if [ ! -s "$DOWNLOADS/$APK" ]; then
    echo "ERROR: APK copy to Android Downloads failed."
    exit 1
fi

LOCAL_SHA="$(sha256sum "$DOWNLOADS/$APK" | awk '{print $1}')"

echo
echo "[9/9] Final verification..."

echo
echo "========================================"
echo "          DEPLOYMENT SUCCESS"
echo "========================================"
echo
echo "Version:      $TAG"
echo "Commit:       $SHA"
echo "GitHub run:   $RUN_ID"
echo "APK:          $DOWNLOADS/$APK"
echo "SHA-256:      $LOCAL_SHA"
echo "Release:      $RELEASE_URL"
echo "Build log:    $LOGDIR/$TAG-run-$RUN_ID.log"
echo
echo "Android APK:"
ls -lh "$DOWNLOADS/$APK"
echo
echo "========================================"

rm -rf "$TMP"
