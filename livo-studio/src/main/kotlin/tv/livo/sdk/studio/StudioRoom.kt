package tv.livo.sdk.studio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tv.livo.sdk.LivoApiClient
import tv.livo.sdk.LivoCredentials
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.models.StudioJoinStatusResult
import tv.livo.sdk.models.StudioSession
import java.util.UUID

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
internal fun rememberStudioMeeting(meeting: MeetingControlling?): MeetingControlling {
    val context = LocalContext.current
    return meeting ?: remember {
        val activity = context.findActivity()
        if (activity != null) RealtimeKitMeetingController { activity } else FakeMeetingController()
    }
}

@Composable
public fun LivoHostStudio(hostToken: String, hosts: LivoHosts, meeting: MeetingControlling? = null, onEvent: (StudioHostEvent) -> Unit = {}) {
    val client = remember(hosts) { LivoApiClient(hosts, LivoCredentials.None) }
    var session by remember { mutableStateOf<StudioSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(client) { onDispose { client.close() } }
    LaunchedEffect(hostToken) {
        runCatching { client.public.studioRedeemHost(hostToken) }
            .onSuccess { session = it }
            .onFailure { error = it.message }
    }
    when {
        error != null -> Text(error ?: "failed")
        session != null -> StudioRoom(session = session!!, hosts = hosts, meeting = meeting, onEvent = onEvent)
        else -> Text(stringResource(R.string.studio_connecting))
    }
}

@Composable
public fun LivoGuestStudio(guestToken: String, hosts: LivoHosts, meeting: MeetingControlling? = null, onEvent: (StudioHostEvent) -> Unit = {}) {
    val client = remember(hosts) { LivoApiClient(hosts, LivoCredentials.None) }
    var name by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<StudioSession?>(null) }
    var ended by remember { mutableStateOf(false) }
    var retryAfter by remember { mutableStateOf<Long?>(null) }
    var ready by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    val guestId = remember { UUID.randomUUID().toString() }
    DisposableEffect(client) { onDispose { client.close() } }

    LaunchedEffect(guestToken) {
        while (isActive && session == null && !ended) {
            val status = runCatching { client.public.studioJoinStatus(guestToken) }.getOrNull()
            when (status) {
                StudioJoinStatusResult.Ended -> {
                    ended = true
                    return@LaunchedEffect
                }
                is StudioJoinStatusResult.Status -> ready = status.value.ready
                null -> {}
            }
            delay(2_000)
        }
    }

    when {
        ended -> StudioPhaseScreen(stringResource(R.string.studio_session_ended), onClose = { onEvent(StudioHostEvent.ENDED) })
        session != null -> StudioRoom(session = session!!, hosts = hosts, meeting = meeting, onEvent = onEvent)
        else ->
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0C0F14)).padding(16.dp),
            ) {
                Text(stringResource(R.string.studio_join_studio), color = Color.White)
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.studio_display_name)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (retryAfter != null) Text(stringResource(R.string.studio_retry_after, retryAfter ?: 0))
                Button(
                    onClick = {
                        joining = true
                        // Join is kicked from LaunchedEffect below.
                    },
                    enabled = name.isNotBlank() && ready && !joining,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (ready) stringResource(R.string.studio_join) else stringResource(R.string.studio_waiting_for_host))
                }
            }
    }

    LaunchedEffect(joining, name, ready) {
        if (!joining || name.isBlank() || !ready || session != null) return@LaunchedEffect
        runCatching { client.public.studioJoinGuest(guestToken, name, "guest:$guestId") }
            .onSuccess { session = it }
            .onFailure { err ->
                joining = false
                val api = err as? tv.livo.sdk.LivoApiException
                retryAfter = api?.retryAfterMs
            }
    }
}

@Composable
public fun StudioRoom(session: StudioSession, hosts: LivoHosts, meeting: MeetingControlling? = null, onEvent: (StudioHostEvent) -> Unit = {}) {
    val resolved = rememberStudioMeeting(meeting)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(session) { StudioRoomModel(session, hosts, resolved, scope) }
    val phase by model.phase.collectAsState()
    val toast by model.toast.collectAsState()
    val live by model.live.collectAsState()
    val screenOn by model.screenOn.collectAsState()
    var joinedStarted by remember { mutableStateOf(false) }
    val permissions =
        remember {
            buildList {
                add(Manifest.permission.CAMERA)
                add(Manifest.permission.RECORD_AUDIO)
            }.toTypedArray()
        }
    fun startRoom() {
        if (joinedStarted) return
        joinedStarted = true
        model.start()
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startRoom() }
    LaunchedEffect(Unit) {
        val missing = permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing) launcher.launch(permissions) else startRoom()
    }
    DisposableEffect(model) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model.stop()
        }
    }
    LaunchedEffect(phase) {
        when (phase) {
            StudioPhase.IN_ROOM -> onEvent(StudioHostEvent.JOINED)
            StudioPhase.ENDED -> onEvent(StudioHostEvent.ENDED)
            StudioPhase.LEFT, StudioPhase.KICKED -> onEvent(StudioHostEvent.LEFT)
            else -> {}
        }
    }
    LaunchedEffect(live) { if (live) onEvent(StudioHostEvent.LIVE) }
    LaunchedEffect(screenOn) {
        onEvent(if (screenOn) StudioHostEvent.SCREEN_SHARE_ON else StudioHostEvent.SCREEN_SHARE_OFF)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0C0F14))) {
        when (phase) {
            StudioPhase.CONNECTING -> StudioPhaseScreen(stringResource(R.string.studio_connecting))
            StudioPhase.WAITLISTED -> StudioPhaseScreen(stringResource(R.string.studio_waitlisted))
            StudioPhase.REJECTED ->
                StudioPhaseScreen(stringResource(R.string.studio_rejected), onClose = { onEvent(StudioHostEvent.LEFT) })
            StudioPhase.ENDED ->
                StudioPhaseScreen(stringResource(R.string.studio_ended), onClose = { onEvent(StudioHostEvent.ENDED) })
            StudioPhase.LEFT ->
                StudioPhaseScreen(stringResource(R.string.studio_left), onClose = { onEvent(StudioHostEvent.LEFT) })
            StudioPhase.KICKED ->
                StudioPhaseScreen(stringResource(R.string.studio_kicked), onClose = { onEvent(StudioHostEvent.LEFT) })
            StudioPhase.FAILED ->
                StudioPhaseScreen(
                    stringResource(R.string.studio_failed),
                    onRetry = { model.reconnect() },
                    onClose = { onEvent(StudioHostEvent.LEFT) },
                )
            StudioPhase.IN_ROOM -> StudioInRoom(model = model)
        }
        toast?.let {
            Text(it.message, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 16.dp, end = 16.dp))
        }
    }
}

@Composable
internal fun StudioPhaseScreen(message: String, onRetry: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White)
            if (onRetry != null) {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.studio_reconnect))
                }
            }
            if (onClose != null) {
                Button(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.studio_close))
                }
            }
        }
    }
}
