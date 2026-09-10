package tv.livo.sdk.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tv.livo.sdk.LivoApiClient
import tv.livo.sdk.LivoCredentials
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.models.StudioJoinStatusResult
import tv.livo.sdk.models.StudioRole
import tv.livo.sdk.models.StudioSession
import java.util.UUID

public enum class StudioHostEvent { JOINED, LIVE, ENDED, LEFT }

@Composable
public fun LivoHostStudio(hostToken: String, hosts: LivoHosts, meeting: MeetingControlling = FakeMeetingController(), onEvent: (StudioHostEvent) -> Unit = {}) {
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
        else -> Text("Connecting")
    }
}

@Composable
public fun LivoGuestStudio(guestToken: String, hosts: LivoHosts, meeting: MeetingControlling = FakeMeetingController(), onEvent: (StudioHostEvent) -> Unit = {}) {
    val client = remember(hosts) { LivoApiClient(hosts, LivoCredentials.None) }
    var name by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<StudioSession?>(null) }
    var ended by remember { mutableStateOf(false) }
    var retryAfter by remember { mutableStateOf<Long?>(null) }
    val guestId = remember { UUID.randomUUID().toString() }
    var joined by remember { mutableStateOf(false) }
    DisposableEffect(client) { onDispose { client.close() } }

    LaunchedEffect(guestToken, name, joined) {
        if (name.isBlank()) return@LaunchedEffect
        while (isActive && session == null && !ended) {
            val status = runCatching { client.public.studioJoinStatus(guestToken) }.getOrNull()
            when (status) {
                StudioJoinStatusResult.Ended -> {
                    ended = true
                    return@LaunchedEffect
                }
                is StudioJoinStatusResult.Status -> {
                    if (status.value.ready && !joined) {
                        joined = true
                        runCatching {
                            client.public.studioJoinGuest(guestToken, name, "guest:$guestId")
                        }.onSuccess { session = it }
                            .onFailure { err ->
                                joined = false
                                val api = err as? tv.livo.sdk.LivoApiException
                                retryAfter = api?.retryAfterMs
                            }
                    }
                }
                null -> {}
            }
            delay(2_000)
        }
    }

    when {
        ended -> Text("This session has ended")
        session != null -> StudioRoom(session = session!!, hosts = hosts, meeting = meeting, onEvent = onEvent)
        else ->
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Display name") })
                if (retryAfter != null) Text("Retry after ${retryAfter}ms")
            }
    }
}

@Composable
public fun StudioRoom(session: StudioSession, hosts: LivoHosts, meeting: MeetingControlling = FakeMeetingController(), onEvent: (StudioHostEvent) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val model = remember(session) { StudioRoomModel(session, hosts, meeting, scope) }
    val phase by model.phase.collectAsState()
    val participants by model.participants.collectAsState()
    val cameraOn by model.cameraOn.collectAsState()
    val micOn by model.micOn.collectAsState()
    val live by model.live.collectAsState()
    val toast by model.toast.collectAsState()
    val theme = LocalStudioTheme.current
    DisposableEffect(model) {
        model.start()
        onDispose { model.stop() }
    }
    LaunchedEffect(phase) {
        when (phase) {
            StudioPhase.IN_ROOM -> onEvent(StudioHostEvent.JOINED)
            StudioPhase.ENDED -> onEvent(StudioHostEvent.ENDED)
            StudioPhase.LEFT -> onEvent(StudioHostEvent.LEFT)
            else -> {}
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0C0F14))) {
        when (phase) {
            StudioPhase.CONNECTING -> Text("Connecting", color = Color.White, modifier = Modifier.align(Alignment.Center))
            StudioPhase.WAITLISTED -> Text("Waiting for the host", color = Color.White, modifier = Modifier.align(Alignment.Center))
            StudioPhase.REJECTED -> Text("The host declined your request", color = Color.White, modifier = Modifier.align(Alignment.Center))
            StudioPhase.ENDED, StudioPhase.LEFT -> Text("This session has ended", color = Color.White, modifier = Modifier.align(Alignment.Center))
            StudioPhase.FAILED -> Text("Could not join", color = Color.White, modifier = Modifier.align(Alignment.Center))
            StudioPhase.IN_ROOM -> {
                val layout = StudioStageLayout.arrange(participants, null, grid = false)
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val spot = layout.spotlight.firstOrNull()
                        Text(spot?.name ?: "You", color = Color.White)
                    }
                    LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(layout.strip, key = { it.id }) { p ->
                            Box(
                                Modifier.size(72.dp).background(Color.DarkGray),
                                contentAlignment = Alignment.Center,
                            ) { Text(p.name.take(1), color = Color.White) }
                        }
                        if (layout.overflow > 0) {
                            item { Text("+${layout.overflow}", color = Color.White) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { model.toggleMic() }) {
                            Icon(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = "Microphone")
                        }
                        IconButton(onClick = { model.toggleCamera() }) {
                            Icon(if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, contentDescription = "Camera")
                        }
                        if (session.role == StudioRole.MODERATOR) {
                            if (!live) {
                                Button(onClick = {
                                    model.goLive()
                                    onEvent(StudioHostEvent.LIVE)
                                }) { Text("Go live") }
                            } else {
                                Button(onClick = { model.stopLive() }) { Text("Stop") }
                            }
                        }
                        IconButton(onClick = {
                            model.stop()
                            onEvent(StudioHostEvent.LEFT)
                        }) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "Leave", tint = theme.destructive)
                        }
                    }
                    toast?.let { Text(it.message, color = Color.White, modifier = Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun LeaveConfirm(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column {
        Text("Leave studio?")
        TextButton(onClick = onConfirm) { Text("Leave") }
        TextButton(onClick = onDismiss) { Text("Stay") }
    }
}
