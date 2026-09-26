#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$HOME/Till-Counter"
echo "== Till Counter deployment =="
git status --short
git add -A
git commit -m "Build Till Counter v0.1.0" || echo "No new commit required."
git push origin main
echo
echo "Push complete."
if command -v gh >/dev/null 2>&1; then
  echo "Watching GitHub Actions build..."
  sleep 4
  RUN_ID="$(gh run list --workflow build-apk.yml --branch main --limit 1 --json databaseId --jq '.[0].databaseId')"
  if [ -n "$RUN_ID" ]; then
    gh run watch "$RUN_ID" --exit-status || {
      echo
      echo "BUILD FAILED — error log follows:"
      gh run view "$RUN_ID" --log-failed || true
      exit 1
    }
    echo
    echo "BUILD SUCCEEDED. Downloading APK artifact..."
    mkdir -p "$HOME/storage/downloads/Till-Counter-Build"
    gh run download "$RUN_ID" -n Till-Counter-v0.1.0-APK -D "$HOME/storage/downloads/Till-Counter-Build"
    echo "APK downloaded to: $HOME/storage/downloads/Till-Counter-Build/app-debug.apk"
  fi
else
  echo "GitHub CLI (gh) is not installed; push succeeded but live watch was skipped."
  echo "Install it with: pkg install gh"
  echo "Then authenticate once with: gh auth login"
fi
