# Screen share (Android)

RealtimeKit `localUser.enableScreenShare()` presents its own MediaProjection consent
Activity (`ScreenCaptureActivity` in `core-android`). The host app still needs a
foreground service with type `mediaProjection` on API 34+ while sharing.

The first-party app uses `StudioForegroundService` (`camera | microphone | mediaPlayback`,
plus `mediaProjection` while sharing). `StudioRoom` emits `SCREEN_SHARE_ON` /
`SCREEN_SHARE_OFF` so the app can restart the service with the extra type. If
`startForeground` rejects `mediaProjection` before consent, the service falls back
to `mediaPlayback` and the share toast from RTK still surfaces.

On `targetSdk` 36, `startForeground` may only include `camera` / `microphone` after
`CAMERA` / `RECORD_AUDIO` are granted (and the activity is visible). `livo-studio`
requests those runtime permissions before `join`. Omit a type if the matching
permission is denied.

On start failure, roll `screenShareOn` back and toast. Keep the Screen share
control even when `getDisplayMedia` is missing — RTK owns that check.
