# Android SDK release runbook

## Bootstrap

1. Verify the Maven Central Portal namespace `tv.livo` (DNS TXT on `livo.tv`).
2. Store GitHub Actions secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (Central Portal user token), `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY_PASSWORD`.
3. Land the first conventional commit on `main`. semantic-release cuts `v1.0.0` and runs `./gradlew publishToMavenCentral -PVERSION_NAME=…`.
4. Ship `android-app` after that tag exists (same order as ios-sdk → ios-app).

## Channels

- `main` → stable Maven Central (`1.2.3`)
- `dev` → prerelease `1.2.3-rc.N` (also published; analog of npm `next`)

There are no PR preview packages.

## Local

Do **not** run `publishToMavenCentral` from a laptop. Use `./scripts/ci-check.sh`.
