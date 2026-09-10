# Screen share (Android)

RealtimeKit `localUser.enableScreenShare()` needs:

1. **MediaProjection consent** from an Activity (`MediaProjectionManager.createScreenCaptureIntent()`).
2. A **foreground service** with type `mediaProjection` on API 34+. The SDK does not ship that service — the host app must.

The first-party app uses `StudioForegroundService` (`camera | microphone | mediaPlayback`, plus `mediaProjection` while sharing).

On start failure, roll `screenShareOn` back and toast. Do not assume `getDisplayMedia` exists on every device — keep the button and show a download-app dialog on coarse-pointer devices without hover.
