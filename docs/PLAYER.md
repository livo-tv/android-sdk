# LivoPlayer

`LivoPlayer` is a Compose HLS player on Media3 ExoPlayer.

- Waiting room uses the resolved curtain. Shader curtains render the captured JPEG poster only (no live WebGL). Honor `prefers-reduced-motion`.
- Live starts muted + autoplay. Re-apply `volume = 0` after prepare.
- Ended live stays on the same `/live/` URL (`#EXT-X-ENDLIST`). Do not tear down the player when the session flips to replay.
- Captions default **Off**. Live polls `GET /public/streams/:id/captions/:lang` with `Accept: application/json`. Overlay uses `advanceLiveCaptionPlayout` (dwell `chars/17s`, clamp 1.2–6s, rush 2.5s, drop screens past 8). Ended / VOD uses sidecar `subs/{lang}.vtt`. Never re-anchor `/live/` VTT.
- Seek thumbnails: `thumbs/preview.vtt`. `cueAtTime` must not treat NaN as cue 0. Players must not receive sprite URLs.
- Presence every 30s while playing. Beacons batch to `POST /public/playback/beacon` with `surface = "sdk"` (the app passes `"app"`).
- Always keep an sr-only `role="status"` even when `hideBranding && hideStatus`.
