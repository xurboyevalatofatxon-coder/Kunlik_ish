#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  echo 'BUILD BLOCKED: Android SDK not configured. Set ANDROID_HOME or use the included GitHub Actions workflow.' >&2
  exit 2
fi
python3 scripts/check_project.py
./gradlew --no-daemon clean :core:test :app:lintDebug :app:assembleDebug :app:assembleRelease
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/DailyGoals-v1.0.0-debug.apk
python3 scripts/verify_apk.py dist/DailyGoals-v1.0.0-debug.apk --package uz.dailygoals.app.debug --output dist/debug-verification.json
if [[ -n "${DG_KEYSTORE_PATH:-}" ]]; then
  cp app/build/outputs/apk/release/app-release.apk dist/DailyGoals-v1.0.0-release.apk
  python3 scripts/verify_apk.py dist/DailyGoals-v1.0.0-release.apk --package uz.dailygoals.app --output dist/release-verification.json
else
  echo 'No release signing key provided. Verified debug APK is the installable artifact.'
fi
