#!/usr/bin/env sh
# Explicit network bootstrap, not a disguised or fabricated official wrapper JAR.
cd "$(dirname "$0")" || exit 1
exec python3 scripts/bootstrap_gradle.py "$@"
