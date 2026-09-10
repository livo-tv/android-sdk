#!/usr/bin/env bash
# Quality gate for android-sdk: format, detekt, lint, unit tests, API dump, example APK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -f "./gradlew" ]]; then
	bash ./gradlew --no-daemon ciCheck
elif [[ -f "./gradlew.bat" ]]; then
	./gradlew.bat --no-daemon ciCheck
else
	echo "gradle wrapper is missing" >&2
	exit 1
fi
