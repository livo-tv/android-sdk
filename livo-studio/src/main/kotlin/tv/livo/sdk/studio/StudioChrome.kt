package tv.livo.sdk.studio

import android.content.Intent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val TileBg = Color(0xFF1A1F2A)
private val LiveRed = Color(0xFFD6363B)

private sealed class HostConfirm {
    data class Kick(val participant: StudioParticipant) : HostConfirm()
    data class Mute(val participant: StudioParticipant) : HostConfirm()
    data class StopCamera(val participant: StudioParticipant) : HostConfirm()
    data class StopScreen(val participant: StudioParticipant) : HostConfirm()
    data class TakeOff(val participant: StudioParticipant) : HostConfirm()
    data object StopBroadcast : HostConfirm()
}

@Composable
internal fun StudioInRoom(model: StudioRoomModel) {
    val participants by model.participants.collectAsState()
    val activeSpeaker by model.activeSpeaker.collectAsState()
    val micOn by model.micOn.collectAsState()
    val cameraOn by model.cameraOn.collectAsState()
    val screenOn by model.screenOn.collectAsState()
    val live by model.live.collectAsState()
    val streamStatus by model.streamStatus.collectAsState()
    val publishing by model.publishing.collectAsState()
    val waitlist by model.waitlist.collectAsState()
    val stageRequests by model.stageRequests.collectAsState()
    val chat by model.chat.collectAsState()
    val unread by model.unreadChat.collectAsState()
    val selfStage by model.selfStage.collectAsState()
    val chatOpen by model.chatOpen.collectAsState()
    val layout =
        remember(participants, activeSpeaker) {
            val onStage = participants.filter { StudioStageLayout.isOnStage(it.stageStatus) || it.isSelf }
            StudioStageLayout.arrange(StudioStageLayout.expand(onStage), activeSpeaker)
        }
    var peopleOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<HostConfirm?>(null) }
    var menuFor by remember { mutableStateOf<StudioParticipant?>(null) }

    Box(Modifier.fillMaxSize()) {
        StudioInRoomChrome(
            model = model,
            layout = layout,
            live = live,
            streamStatus = streamStatus,
            publishing = publishing,
            micOn = micOn,
            cameraOn = cameraOn,
            peopleBadge = waitlist.size + stageRequests.size,
            unread = unread,
            onPeople = { peopleOpen = true },
            onMore = { moreOpen = true },
            onSettings = { settingsOpen = true },
            onHangup = { if (model.canStop) confirm = HostConfirm.StopBroadcast else model.leave() },
            onLongPress = { if (model.isModerator && !it.isSelf) menuFor = it },
        )
        StudioInRoomOverlays(
            model = model,
            participants = participants,
            waitlist = waitlist,
            stageRequests = stageRequests,
            chat = chat,
            live = live,
            screenOn = screenOn,
            selfStage = selfStage,
            chatOpen = chatOpen,
            peopleOpen = peopleOpen,
            moreOpen = moreOpen,
            settingsOpen = settingsOpen,
            menuFor = menuFor,
            confirm = confirm,
            onPeopleOpen = { peopleOpen = it },
            onMoreOpen = { moreOpen = it },
            onSettingsOpen = { settingsOpen = it },
            onMenuFor = { menuFor = it },
            onConfirm = { confirm = it },
        )
        StudioChatBubble(messages = chat, chatOpen = chatOpen)
    }
}

@Composable
private fun StudioInRoomChrome(
    model: StudioRoomModel,
    layout: StudioStageArrangement,
    live: Boolean,
    streamStatus: String,
    publishing: Boolean,
    micOn: Boolean,
    cameraOn: Boolean,
    peopleBadge: Int,
    unread: Int,
    onPeople: () -> Unit,
    onMore: () -> Unit,
    onSettings: () -> Unit,
    onHangup: () -> Unit,
    onLongPress: (StudioParticipant) -> Unit,
) {
    val theme = LocalStudioTheme.current
    Column(Modifier.fillMaxSize()) {
        StudioHeader(
            title = model.session.stream.title,
            live = live,
            preview = streamStatus == "preview",
            liveStartedAt = model.liveStartedAt.collectAsState().value,
            canPublish = model.canPublish,
            publishing = publishing,
            onSettings = onSettings,
            onGoLive = { model.goLive() },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StudioStage(
                model = model,
                layout = layout,
                isModerator = model.isModerator,
                selfOnStage = model.selfOnStage,
                onAskStage = { model.requestStage() },
                onOverflow = onPeople,
                onLongPress = onLongPress,
            )
            layout.pip?.let { pip -> StudioSelfPip(model = model, tile = pip) }
        }
        StudioToolbar(
            micOn = micOn,
            cameraOn = cameraOn,
            canUseMedia = model.canUseMediaControls,
            peopleBadge = peopleBadge,
            unread = unread,
            onMic = { model.toggleMic() },
            onCamera = { model.toggleCamera() },
            onChat = { model.setChatOpen(true) },
            onPeople = onPeople,
            onMore = onMore,
            onHangup = onHangup,
            hangupTint = theme.destructive,
        )
    }
}

@Composable
private fun StudioInRoomOverlays(
    model: StudioRoomModel,
    participants: List<StudioParticipant>,
    waitlist: List<StudioWaitlistedGuest>,
    stageRequests: List<StudioStageRequest>,
    chat: List<StudioChatMessage>,
    live: Boolean,
    screenOn: Boolean,
    selfStage: StudioStageStatus?,
    chatOpen: Boolean,
    peopleOpen: Boolean,
    moreOpen: Boolean,
    settingsOpen: Boolean,
    menuFor: StudioParticipant?,
    confirm: HostConfirm?,
    onPeopleOpen: (Boolean) -> Unit,
    onMoreOpen: (Boolean) -> Unit,
    onSettingsOpen: (Boolean) -> Unit,
    onMenuFor: (StudioParticipant?) -> Unit,
    onConfirm: (HostConfirm?) -> Unit,
) {
    if (moreOpen) {
        StudioMoreOverlay(model, screenOn, selfStage, onMoreOpen, onSettingsOpen)
    }
    if (peopleOpen) {
        StudioPeopleSheet(
            model = model,
            participants = participants,
            waitlist = waitlist,
            stageRequests = stageRequests,
            onDismiss = { onPeopleOpen(false) },
            onConfirm = onConfirm,
        )
    }
    if (chatOpen) {
        StudioChatSheet(model = model, messages = chat, onDismiss = { model.setChatOpen(false) })
    }
    if (settingsOpen) {
        StudioSettingsDialog(model = model, onDismiss = { onSettingsOpen(false) })
    }
    StudioHostActionLayer(model, live, menuFor, confirm, onMenuFor, onConfirm)
}

@Composable
private fun StudioMoreOverlay(model: StudioRoomModel, screenOn: Boolean, selfStage: StudioStageStatus?, onMoreOpen: (Boolean) -> Unit, onSettingsOpen: (Boolean) -> Unit) {
    StudioMoreMenu(
        expanded = true,
        onDismiss = { onMoreOpen(false) },
        canUseMedia = model.canUseMediaControls,
        isModerator = model.isModerator,
        selfStage = selfStage,
        screenOn = screenOn,
        onSwitchCamera = {
            model.switchCamera()
            onMoreOpen(false)
        },
        onScreenShare = {
            model.toggleScreenShare()
            onMoreOpen(false)
        },
        onSettings = {
            onSettingsOpen(true)
            onMoreOpen(false)
        },
        onAskStage = {
            model.requestStage()
            onMoreOpen(false)
        },
        onCancelStage = {
            model.cancelStageRequest()
            onMoreOpen(false)
        },
        onJoinStage = {
            model.joinStage()
            onMoreOpen(false)
        },
        onLeaveStage = {
            model.leaveStage()
            onMoreOpen(false)
        },
    )
}

@Composable
private fun StudioHostActionLayer(
    model: StudioRoomModel,
    live: Boolean,
    menuFor: StudioParticipant?,
    confirm: HostConfirm?,
    onMenuFor: (StudioParticipant?) -> Unit,
    onConfirm: (HostConfirm?) -> Unit,
) {
    menuFor?.let { target ->
        StudioHostMenu(
            participant = target,
            isLive = live,
            canTakeOff = model.canTakeOffAir(target.id),
            onDismiss = { onMenuFor(null) },
            onConfirm = {
                onConfirm(it)
                onMenuFor(null)
            },
            onPin = {
                model.pin(target)
                onMenuFor(null)
            },
            onBringOnAir = {
                model.bringOnAir(target.id)
                onMenuFor(null)
            },
        )
    }
    confirm?.let { pending ->
        StudioConfirmDialog(
            pending = pending,
            onDismiss = { onConfirm(null) },
            onConfirm = {
                applyHostConfirm(model, pending)
                onConfirm(null)
            },
        )
    }
}

private fun applyHostConfirm(model: StudioRoomModel, pending: HostConfirm) {
    when (pending) {
        is HostConfirm.Kick -> model.kick(pending.participant.id)
        is HostConfirm.Mute -> model.muteRemote(pending.participant.id)
        is HostConfirm.StopCamera -> model.stopRemoteCamera(pending.participant.id)
        is HostConfirm.StopScreen -> model.stopRemoteScreen(pending.participant.id)
        is HostConfirm.TakeOff -> model.takeOffAir(pending.participant.id)
        HostConfirm.StopBroadcast -> model.stopBroadcast()
    }
}

@Composable
private fun StudioHeader(
    title: String,
    live: Boolean,
    preview: Boolean,
    liveStartedAt: Long?,
    canPublish: Boolean,
    publishing: Boolean,
    onSettings: () -> Unit,
    onGoLive: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (live) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        if (live) {
            Row(
                Modifier.background(LiveRed.copy(alpha = 0.25f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.studio_live), color = Color.White, fontSize = 12.sp)
                liveStartedAt?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(elapsed(now - it), color = Color.White, fontSize = 12.sp)
                }
            }
        } else if (preview) {
            Text(
                stringResource(R.string.studio_preview),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.studio_settings), tint = Color.White)
        }
        if (canPublish) {
            Button(onClick = onGoLive, enabled = !publishing) {
                Text(if (publishing) stringResource(R.string.studio_going_live) else stringResource(R.string.studio_go_live))
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remain = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remain) else "%d:%02d".format(minutes, remain)
}

@Composable
private fun StudioStage(
    model: StudioRoomModel,
    layout: StudioStageArrangement,
    isModerator: Boolean,
    selfOnStage: Boolean,
    onAskStage: () -> Unit,
    onOverflow: () -> Unit,
    onLongPress: (StudioParticipant) -> Unit,
) {
    if (layout.spotlight.isEmpty() && layout.strip.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.studio_no_one_on_stage), color = Color.White.copy(alpha = 0.7f))
                if (!isModerator && !selfOnStage) {
                    Button(onClick = onAskStage, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.studio_ask_stage))
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val focus = layout.spotlight.firstOrNull()
        if (focus != null) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ParticipantTile(model, focus, Modifier.fillMaxSize(), onLongPress)
            }
        }
        if (layout.strip.isNotEmpty() || layout.overflow > 0) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(90.dp)) {
                items(layout.strip, key = { it.tileId }) { tile ->
                    ParticipantTile(model, tile, Modifier.width(160.dp).aspectRatio(16f / 9f), onLongPress)
                }
                if (layout.overflow > 0) {
                    item {
                        Box(
                            Modifier.width(160.dp).aspectRatio(16f / 9f).background(TileBg, RoundedCornerShape(8.dp)).pointerInput(Unit) {
                                detectTapGestures(onTap = { onOverflow() })
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.studio_overflow, layout.overflow), color = Color.White)
                        }
                    }
                }
            }
        } else if (focus == null) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                layout.strip.firstOrNull()?.let { ParticipantTile(model, it, Modifier.fillMaxSize(), onLongPress) }
            }
        }
    }
}

@Composable
private fun ParticipantTile(model: StudioRoomModel, tile: StudioDisplayTile, modifier: Modifier, onLongPress: (StudioParticipant) -> Unit) {
    val participant = tile.participant
    val view = model.videoView(tile)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TileBg)
            .pointerInput(participant.id) { detectTapGestures(onLongPress = { onLongPress(participant) }) },
    ) {
        if (view != null) {
            AndroidView(
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            IdleAvatar(participant)
        }
        Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (tile.kind == StudioTileKind.SCREEN) {
                    stringResource(R.string.studio_screen_suffix, participant.name)
                } else {
                    participant.name
                },
                color = Color.White,
                fontSize = 11.sp,
            )
            if (!participant.audioEnabled) Text(stringResource(R.string.studio_muted), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            if (participant.pinned) Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.studio_pinned), tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun IdleAvatar(participant: StudioParticipant) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!participant.picture.isNullOrBlank()) {
            AsyncImage(model = participant.picture, contentDescription = participant.name, modifier = Modifier.size(64.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF226BC0)), contentAlignment = Alignment.Center) {
                Text(participant.name.take(1).uppercase(), color = Color.White, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun StudioSelfPip(model: StudioRoomModel, tile: StudioDisplayTile) {
    var ox by remember { mutableFloatStateOf(12f) }
    var oy by remember { mutableFloatStateOf(12f) }
    Box(
        Modifier.offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
            .width(120.dp)
            .aspectRatio(16f / 9f)
            .pointerInput(Unit) {
                detectDragGestures { _, drag ->
                    ox += drag.x
                    oy += drag.y
                }
            },
    ) {
        ParticipantTile(model, tile, Modifier.fillMaxSize()) {}
    }
}

@Composable
private fun StudioToolbar(
    micOn: Boolean,
    cameraOn: Boolean,
    canUseMedia: Boolean,
    peopleBadge: Int,
    unread: Int,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onChat: () -> Unit,
    onPeople: () -> Unit,
    onMore: () -> Unit,
    onHangup: () -> Unit,
    hangupTint: Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canUseMedia) {
            IconButton(onClick = onMic) {
                Icon(
                    if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = stringResource(if (micOn) R.string.studio_mute else R.string.studio_unmute),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onCamera) {
                Icon(
                    if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = stringResource(if (cameraOn) R.string.studio_camera_off else R.string.studio_camera_on),
                    tint = Color.White,
                )
            }
        }
        IconButton(onClick = onChat) {
            BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.studio_opens_chat), tint = Color.White)
            }
        }
        IconButton(onClick = onPeople) {
            BadgedBox(badge = { if (peopleBadge > 0) Badge { Text("$peopleBadge") } }) {
                Icon(Icons.Filled.People, contentDescription = stringResource(R.string.studio_people), tint = Color.White)
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.studio_more), tint = Color.White)
        }
        IconButton(onClick = onHangup) {
            Icon(Icons.Filled.CallEnd, contentDescription = stringResource(R.string.studio_leave), tint = hangupTint)
        }
    }
}

@Composable
private fun StudioMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canUseMedia: Boolean,
    isModerator: Boolean,
    selfStage: StudioStageStatus?,
    screenOn: Boolean,
    onSwitchCamera: () -> Unit,
    onScreenShare: () -> Unit,
    onSettings: () -> Unit,
    onAskStage: () -> Unit,
    onCancelStage: () -> Unit,
    onJoinStage: () -> Unit,
    onLeaveStage: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            if (canUseMedia) {
                DropdownMenuItem(text = { Text(stringResource(R.string.studio_switch_camera)) }, onClick = onSwitchCamera, leadingIcon = { Icon(Icons.Filled.Cameraswitch, null) })
                DropdownMenuItem(
                    text = { Text(stringResource(if (screenOn) R.string.studio_stop_share else R.string.studio_screen_share)) },
                    onClick = onScreenShare,
                    leadingIcon = { Icon(if (screenOn) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare, null) },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.studio_settings)) }, onClick = onSettings, leadingIcon = { Icon(Icons.Filled.Settings, null) })
            if (!isModerator) {
                when (selfStage) {
                    StudioStageStatus.REQUESTED -> DropdownMenuItem(text = { Text(stringResource(R.string.studio_cancel_request)) }, onClick = onCancelStage)
                    StudioStageStatus.ACCEPTED_TO_JOIN_STAGE -> DropdownMenuItem(text = { Text(stringResource(R.string.studio_joining_stage)) }, onClick = onJoinStage)
                    StudioStageStatus.ON_STAGE -> DropdownMenuItem(text = { Text(stringResource(R.string.studio_leave_stage)) }, onClick = onLeaveStage)
                    else -> DropdownMenuItem(text = { Text(stringResource(R.string.studio_ask_stage)) }, onClick = onAskStage)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioPeopleSheet(
    model: StudioRoomModel,
    participants: List<StudioParticipant>,
    waitlist: List<StudioWaitlistedGuest>,
    stageRequests: List<StudioStageRequest>,
    onDismiss: () -> Unit,
    onConfirm: (HostConfirm) -> Unit,
) {
    val context = LocalContext.current
    val guestUrl = model.session.guestUrl
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp)) {
            if (!guestUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, guestUrl)
                            },
                        )
                    },
                ) { Text(stringResource(R.string.studio_share_guest_link)) }
            }
            if (model.isModerator && waitlist.isNotEmpty()) {
                Text(stringResource(R.string.studio_waiting_room), color = Color.White, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    TextButton(onClick = { model.admitAll(AdmitAs.PANELIST) }) { Text(stringResource(R.string.studio_admit_all_panelists)) }
                    TextButton(onClick = { model.admitAll(AdmitAs.AUDIENCE) }) { Text(stringResource(R.string.studio_admit_all_audience)) }
                }
                waitlist.forEach { guest ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(guest.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { model.admit(guest, AdmitAs.PANELIST) }) { Text(stringResource(R.string.studio_admit_panelist)) }
                        TextButton(onClick = { model.admit(guest, AdmitAs.AUDIENCE) }) { Text(stringResource(R.string.studio_admit_audience)) }
                        TextButton(onClick = { model.deny(guest) }) { Text(stringResource(R.string.studio_deny)) }
                    }
                }
            }
            if (model.isModerator && stageRequests.isNotEmpty()) {
                Text(stringResource(R.string.studio_stage_requests), modifier = Modifier.padding(top = 12.dp))
                stageRequests.forEach { request ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(request.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { model.grantStage(request) }) { Text(stringResource(R.string.studio_bring_on_air)) }
                        TextButton(onClick = { model.denyStage(request) }) { Text(stringResource(R.string.studio_deny)) }
                    }
                }
            }
            participants.forEach { participant ->
                val onStage = StudioStageLayout.isOnStage(participant.stageStatus)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(participant.name + if (participant.isSelf) " (${stringResource(R.string.studio_you)})" else "", modifier = Modifier.weight(1f))
                    Text(stringResource(if (onStage) R.string.studio_on_stage else R.string.studio_audience), fontSize = 12.sp)
                    if (model.isModerator && !participant.isSelf) {
                        IconButton(onClick = { onConfirm(HostConfirm.Kick(participant)) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.studio_kick))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioHostMenu(
    participant: StudioParticipant,
    isLive: Boolean,
    canTakeOff: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (HostConfirm) -> Unit,
    onPin: () -> Unit,
    onBringOnAir: () -> Unit,
) {
    val onStage = StudioStageLayout.isOnStage(participant.stageStatus)
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(if (participant.pinned) R.string.studio_unpin else R.string.studio_pin)) }, onClick = onPin)
        if (onStage) {
            DropdownMenuItem(text = { Text(stringResource(R.string.studio_mute)) }, onClick = { onConfirm(HostConfirm.Mute(participant)) })
            DropdownMenuItem(text = { Text(stringResource(R.string.studio_stop_camera)) }, onClick = { onConfirm(HostConfirm.StopCamera(participant)) })
            if (participant.screenShareOn) {
                DropdownMenuItem(text = { Text(stringResource(R.string.studio_stop_screen)) }, onClick = { onConfirm(HostConfirm.StopScreen(participant)) })
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.studio_take_off_stage)) },
                onClick = { onConfirm(HostConfirm.TakeOff(participant)) },
                enabled = canTakeOff || !isLive,
            )
        } else {
            DropdownMenuItem(text = { Text(stringResource(R.string.studio_bring_on_air)) }, onClick = onBringOnAir)
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.studio_kick)) }, onClick = { onConfirm(HostConfirm.Kick(participant)) })
    }
}

@Composable
private fun StudioConfirmDialog(pending: HostConfirm, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val title: Int
    val body: Int
    when (pending) {
        is HostConfirm.Kick -> {
            title = R.string.studio_kick_confirm_title
            body = R.string.studio_kick_confirm_body
        }
        is HostConfirm.Mute -> {
            title = R.string.studio_mute_confirm_title
            body = R.string.studio_mute_confirm_body
        }
        is HostConfirm.StopCamera -> {
            title = R.string.studio_stop_camera_confirm_title
            body = R.string.studio_stop_camera_confirm_body
        }
        is HostConfirm.StopScreen -> {
            title = R.string.studio_stop_screen_confirm_title
            body = R.string.studio_stop_screen_confirm_body
        }
        is HostConfirm.TakeOff -> {
            title = R.string.studio_take_off_confirm_title
            body = R.string.studio_take_off_confirm_body
        }
        HostConfirm.StopBroadcast -> {
            title = R.string.studio_stop_confirm_title
            body = R.string.studio_stop_confirm_body
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (pending is HostConfirm.StopBroadcast) R.string.studio_stop_broadcast else R.string.studio_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioChatSheet(model: StudioRoomModel, messages: List<StudioChatMessage>, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().height(420.dp).padding(12.dp)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(messages, key = { it.id }) { message ->
                    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start) {
                        Text(if (message.isSelf) stringResource(R.string.studio_you) else message.displayName, fontSize = 11.sp, color = Color.Gray)
                        Text(message.text)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.studio_message)) },
                )
                IconButton(
                    onClick = {
                        model.sendChat(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.studio_send))
                }
            }
        }
    }
}

@Composable
private fun StudioChatBubble(messages: List<StudioChatMessage>, chatOpen: Boolean) {
    if (chatOpen) return
    val newest = messages.lastOrNull() ?: return
    var visible by remember(newest.id) { mutableStateOf(true) }
    LaunchedEffect(newest.id) {
        visible = true
        delay(6_000)
        visible = false
    }
    if (!visible) return
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
        Column(Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text(newest.displayName, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Text(newest.text, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StudioSettingsDialog(model: StudioRoomModel, onDismiss: () -> Unit) {
    val audio by model.audioDevices.collectAsState()
    val video by model.videoDevices.collectAsState()
    val selectedAudio by model.selectedAudioId.collectAsState()
    val selectedVideo by model.selectedVideoId.collectAsState()
    LaunchedEffect(Unit) { model.refreshDevices() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_settings)) },
        text = {
            Column {
                if (audio.isEmpty() && video.isEmpty()) {
                    Text(stringResource(R.string.studio_no_devices))
                }
                if (audio.isNotEmpty()) {
                    Text(stringResource(R.string.studio_microphone), modifier = Modifier.padding(top = 8.dp))
                    audio.forEach { device ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = device.id == selectedAudio, onClick = { model.selectDevice(device) })
                            Text(device.name)
                        }
                    }
                }
                if (video.isNotEmpty()) {
                    Text(stringResource(R.string.studio_camera), modifier = Modifier.padding(top = 12.dp))
                    video.forEach { device ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = device.id == selectedVideo, onClick = { model.selectDevice(device) })
                            Text(device.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_done)) } },
    )
}
