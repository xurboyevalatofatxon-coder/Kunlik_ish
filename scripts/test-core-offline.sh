#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v kotlinc >/dev/null || { echo 'Kotlin compiler (kotlinc) is required for offline tests.';exit 2; }
mkdir -p reports
kotlinc core/src/main/kotlin/uz/dailygoals/domain/*.kt core/src/testSupport/kotlin/uz/dailygoals/domain/*.kt -include-runtime -d reports/core-tests.jar
java -jar reports/core-tests.jar reports/core-tests.xml | tee reports/core-tests.txt
