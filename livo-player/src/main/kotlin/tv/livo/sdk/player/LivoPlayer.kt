@file:OptIn(UnstableApi::class)

package tv.livo.sdk.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import tv.livo.sdk.LivoApiClient
import tv.livo.sdk.LivoCredentials
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.Posters
import tv.livo.sdk.models.PlaybackBeaconEvent
import tv.livo.sdk.models.PublicStream
import tv.livo.sdk.models.PublicVod
import tv.livo.sdk.models.ResolvedCurtain
import java.util.UUID

public enum class LivoPlayerPhase {
    LOADING,
    WAITING,
    PLAYING,
    ENDED,
}

public data class LivoPlayerEvent(val type: String, val detail: String? = null)

public data class LivoWaitingLabels(val waiting: String = "Waiting", val startingSoon: String = "Starting soon")

public class LivoPlayerState internal constructor(public val streamId: String?, public val vodId: String?, public val hosts: LivoHosts, public val surface: String) {
    internal val phase = MutableStateFlow(LivoPlayerPhase.LOADING)
    internal val curtain = MutableStateFlow<ResolvedCurtain?>(null)
    internal val playbackUrl = MutableStateFlow<String?>(null)
    internal val captions = MutableStateFlow(EMPTY_LIVE_CAPTION_PLAYOUT)
    internal val hideBranding = MutableStateFlow(false)
    internal val hideStatus = MutableStateFlow(false)
    internal val title = MutableStateFlow("")
    public val sessionId: String = UUID.randomUUID().toString()
}

@Composable
public fun rememberLivoPlayerState(streamId: String? = null, vodId: String? = null, hosts: LivoHosts, surface: String = "sdk"): LivoPlayerState =
    remember(streamId, vodId, hosts, surface) { LivoPlayerState(streamId, vodId, hosts, surface) }

@OptIn(UnstableApi::class)
@Composable
public fun LivoPlayer(
    modifier: Modifier = Modifier,
    streamId: String? = null,
    vodId: String? = null,
    hosts: LivoHosts = LivoHosts.production,
    autoPlay: Boolean = true,
    muted: Boolean = streamId != null,
    lang: String = "en",
    showCommunity: Boolean = false,
    waitingLabels: LivoWaitingLabels = LivoWaitingLabels(),
    surface: String = "sdk",
    state: LivoPlayerState = rememberLivoPlayerState(streamId, vodId, hosts, surface),
    onEvent: (LivoPlayerEvent) -> Unit = {},
    onPictureInPicture: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val client = remember(hosts) { LivoApiClient(hosts, LivoCredentials.None) }
    val phase by state.phase.collectAsState()
    val curtain by state.curtain.collectAsState()
    val playbackUrl by state.playbackUrl.collectAsState()
    val overlay by state.captions.collectAsState()
    val hideBranding by state.hideBranding.collectAsState()
    val hideStatus by state.hideStatus.collectAsState()
    val latestEvent = rememberUpdatedState(onEvent)
    DisposableEffect(client) { onDispose { client.close() } }

    LaunchedEffect(streamId, vodId) {
        pollPublic(client, state, latestEvent.value)
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (phase) {
            LivoPlayerPhase.LOADING, LivoPlayerPhase.WAITING ->
                WaitingRoom(
                    curtain = curtain,
                    labels = waitingLabels,
                    hideBranding = hideBranding,
                    hideStatus = hideStatus,
                    statusText = if (phase == LivoPlayerPhase.LOADING) "Loading" else waitingLabels.waiting,
                )
            LivoPlayerPhase.PLAYING, LivoPlayerPhase.ENDED -> {
                val url = playbackUrl
                if (url != null) {
                    HlsSurface(
                        url = url,
                        muted = muted,
                        autoPlay = autoPlay,
                        liveUntilEnded = streamId != null,
                    )
                }
                overlay.screen?.let { screen ->
                    Text(
                        text = screen.text,
                        color = Color.White,
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color(0x99000000))
                            .padding(8.dp),
                    )
                }
            }
        }
        if (hideBranding && hideStatus) {
            Text(
                text = phase.name.lowercase(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = Color.Transparent,
            )
        }
    }

    LaunchedEffect(phase, playbackUrl, lang) {
        val sid = streamId ?: return@LaunchedEffect
        if (phase != LivoPlayerPhase.PLAYING) return@LaunchedEffect
        var playout = EMPTY_LIVE_CAPTION_PLAYOUT
        while (isActive) {
            val cues = runCatching { client.public.captions(sid, lang) }.getOrDefault(emptyList())
            playout = advanceLiveCaptionPlayout(playout, cues, System.currentTimeMillis(), 0.0)
            state.captions.value = playout
            delay(3_000)
        }
    }

    LaunchedEffect(phase) {
        if (phase != LivoPlayerPhase.PLAYING) return@LaunchedEffect
        val sid = streamId ?: return@LaunchedEffect
        while (isActive) {
            runCatching { client.public.presence(sid, state.sessionId) }
            delay(30_000)
        }
    }

    LaunchedEffect(phase, playbackUrl) {
        val events = mutableListOf<PlaybackBeaconEvent>()
        val now = System.currentTimeMillis()
        events +=
            PlaybackBeaconEvent(
                type = "view",
                ts = now,
                streamId = streamId,
                vodId = vodId,
                surface = state.surface,
            )
        runCatching { client.public.beacon(state.sessionId, events) }
    }

    onPictureInPicture?.let { _ -> }
    showCommunity
    context
}

@Composable
internal fun WaitingRoom(curtain: ResolvedCurtain?, labels: LivoWaitingLabels, hideBranding: Boolean, hideStatus: Boolean, statusText: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val url = curtain?.url
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        if (!(hideBranding && hideStatus)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(labels.waiting, color = Color.White)
            }
        }
        Text(
            text = statusText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (hideBranding && hideStatus) Color.Transparent else Color.White,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HlsSurface(url: String, muted: Boolean, autoPlay: Boolean, liveUntilEnded: Boolean) {
    val context = LocalContext.current
    val player =
        remember(url) {
            val source =
                HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            ExoPlayer.Builder(context).build().apply {
                setMediaSource(source)
                volume = if (muted) 0f else 1f
                playWhenReady = autoPlay
                prepare()
            }
        }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (liveUntilEnded && playbackState == Player.STATE_ENDED) {
                        // Keep the same player attached for ended-live replay.
                    }
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { it.player = player },
    )
}

private suspend fun pollPublic(client: LivoApiClient, state: LivoPlayerState, onEvent: (LivoPlayerEvent) -> Unit) {
    val sid = state.streamId
    val vid = state.vodId
    while (true) {
        try {
            if (sid != null) {
                val stream: PublicStream = client.public.stream(sid)
                state.title.value = stream.title
                state.curtain.value = stream.curtain
                state.hideBranding.value = stream.curtain?.hideBranding == true
                state.hideStatus.value = stream.curtain?.hideStatus == true
                when (stream.status) {
                    "public", "preview" -> {
                        state.playbackUrl.value = stream.playbackUrl
                        state.phase.value = LivoPlayerPhase.PLAYING
                        onEvent(LivoPlayerEvent("playing"))
                    }
                    "ended" -> {
                        state.playbackUrl.value = stream.playbackUrl
                        state.phase.value = LivoPlayerPhase.ENDED
                    }
                    else -> state.phase.value = LivoPlayerPhase.WAITING
                }
                if (stream.status == "ended" || stream.status == "error") return
                delay(5_000)
            } else if (vid != null) {
                val vod: PublicVod = client.public.vod(vid)
                state.title.value = vod.title
                state.curtain.value = vod.curtain
                if (vod.playbackUrl.isNullOrBlank()) {
                    state.phase.value = LivoPlayerPhase.WAITING
                    delay(5_000)
                } else {
                    state.playbackUrl.value = vod.playbackUrl
                    state.phase.value = LivoPlayerPhase.PLAYING
                    return
                }
            } else {
                return
            }
        } catch (_: Exception) {
            delay(5_000)
        }
    }
}

public fun Activity.enterLivoPictureInPicture() {
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
    }
}

public fun Context.livoLockLandscape() {
    (this as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

@Suppress("unused")
internal fun spriteGuard(url: String?): String? = url?.takeUnless { Posters.isSpriteUrl(it) }
