#!/usr/bin/env bash
# Quality gate for android-sdk: format, detekt, lint, unit tests, API dump, example APK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -x "./gradlew" ]]; then
	GRADLE="./gradlew"
elif [[ -x "./gradlew.bat" ]]; then
	GRADLE="./gradlew.bat"
else
	echo "gradle wrapper is missing" >&2
	exit 1
fi

"$GRADLE" --no-daemon ciCheck
