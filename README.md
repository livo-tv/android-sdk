# Livo Android SDK

Public Kotlin SDK for Livo. Artifacts publish to Maven Central under group `tv.livo`.

| Artifact | Module | Use |
| --- | --- | --- |
| `tv.livo:livo-bom` | Gradle platform | Align versions |
| `tv.livo:livo-api` | JVM | Media, auth, notifications, metrics, realtime, multipart |
| `tv.livo:livo-player` | Android / Compose | HLS player, waiting room, captions, beacons |
| `tv.livo:livo-studio` | Android / Compose | Native studio room (RealtimeKit Core) |
| `tv.livo:livo-community` | Android / Compose | Comments and Q&A |

## Gradle

```kotlin
dependencies {
    implementation(platform("tv.livo:livo-bom:<version>"))
    implementation("tv.livo:livo-api")
    implementation("tv.livo:livo-player")
    implementation("tv.livo:livo-studio")
    implementation("tv.livo:livo-community")
}
```

`livo-api` is pure JVM. Partner backends use it with `LivoCredentials.ApiKey`. **Never ship `lk_` / `lp_` keys in an APK.**

Studio token handoff: the partner backend calls `POST /streams/:id/studio/host-session` and passes `hostToken` / `guestToken` to the app. First-party apps use an org JWT and `POST /streams/:id/studio/session`.

## Hosts

```kotlin
LivoHosts.production
LivoHosts.development
LivoHosts.previewStack("feat-android")
```

Canonical media host is `https://media-svc.livo.tv`.

## Quality gate

```bash
./scripts/ci-check.sh
```

Never publish to Maven Central from a laptop. CI on `main` / `dev` owns `publishToMavenCentral`.
