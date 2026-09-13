package tv.livo.sdk.studio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.livo.sdk.LivoApiClient
import tv.livo.sdk.LivoCredentials
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.StudioControlClient
import tv.livo.sdk.models.StudioRole
import tv.livo.sdk.models.StudioSession
import java.util.UUID

public enum class StudioPhase {
    CONNECTING,
    WAITLISTED,
    REJECTED,
    IN_ROOM,
    ENDED,
    LEFT,
    KICKED,
    FAILED,
}

public enum class StudioEvent {
    JOINED,
    LIVE,
    ENDED,
    LEFT,
}

public enum class AdmitAs { PANELIST, AUDIENCE }

public data class StudioToast(val id: String = UUID.randomUUID().toString(), val message: String)

public class StudioRoomModel(
    public val session: StudioSession,
    public val hosts: LivoHosts,
    public val meeting: MeetingControlling,
    private val scope: CoroutineScope,
    credentials: LivoCredentials = LivoCredentials.None,
    private val pollNetwork: Boolean = true,
) : MeetingControllerDelegate {
    private val api = LivoApiClient(hosts, credentials)
    private var control: StudioControlClient? = session.studioControlToken?.let { api.controlClient(it) }

    private val _phase = MutableStateFlow(StudioPhase.CONNECTING)
    public val phase: StateFlow<StudioPhase> = _phase.asStateFlow()

    private val _participants = MutableStateFlow<List<StudioParticipant>>(emptyList())
    public val participants: StateFlow<List<StudioParticipant>> = _participants.asStateFlow()

    private val _waitlist = MutableStateFlow<List<StudioWaitlistedGuest>>(emptyList())
    public val waitlist: StateFlow<List<StudioWaitlistedGuest>> = _waitlist.asStateFlow()

    private val _chat = MutableStateFlow<List<StudioChatMessage>>(emptyList())
    public val chat: StateFlow<List<StudioChatMessage>> = _chat.asStateFlow()

    private val _stageRequests = MutableStateFlow<List<StudioStageRequest>>(emptyList())
    public val stageRequests: StateFlow<List<StudioStageRequest>> = _stageRequests.asStateFlow()

    private val _cameraOn = MutableStateFlow(true)
    public val cameraOn: StateFlow<Boolean> = _cameraOn.asStateFlow()

    private val _micOn = MutableStateFlow(true)
    public val micOn: StateFlow<Boolean> = _micOn.asStateFlow()

    private val _screenOn = MutableStateFlow(false)
    public val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val _streamStatus = MutableStateFlow(session.stream.status)
    public val streamStatus: StateFlow<String> = _streamStatus.asStateFlow()

    private val _live = MutableStateFlow(session.stream.isLive)
    public val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _publishing = MutableStateFlow(false)
    public val publishing: StateFlow<Boolean> = _publishing.asStateFlow()

    private val _toast = MutableStateFlow<StudioToast?>(null)
    public val toast: StateFlow<StudioToast?> = _toast.asStateFlow()

    private val _selfStage = MutableStateFlow<StudioStageStatus?>(null)
    public val selfStage: StateFlow<StudioStageStatus?> = _selfStage.asStateFlow()

    private val _activeSpeaker = MutableStateFlow<String?>(null)
    public val activeSpeaker: StateFlow<String?> = _activeSpeaker.asStateFlow()

    private val _signaling = MutableStateFlow(StudioSignalingState.DISCONNECTED)
    public val signaling: StateFlow<StudioSignalingState> = _signaling.asStateFlow()

    private val _audioDevices = MutableStateFlow<List<StudioMediaDevice>>(emptyList())
    public val audioDevices: StateFlow<List<StudioMediaDevice>> = _audioDevices.asStateFlow()

    private val _videoDevices = MutableStateFlow<List<StudioMediaDevice>>(emptyList())
    public val videoDevices: StateFlow<List<StudioMediaDevice>> = _videoDevices.asStateFlow()

    private val _selectedAudioId = MutableStateFlow<String?>(null)
    public val selectedAudioId: StateFlow<String?> = _selectedAudioId.asStateFlow()

    private val _selectedVideoId = MutableStateFlow<String?>(null)
    public val selectedVideoId: StateFlow<String?> = _selectedVideoId.asStateFlow()

    private val _unreadChat = MutableStateFlow(0)
    public val unreadChat: StateFlow<Int> = _unreadChat.asStateFlow()

    private val _chatOpen = MutableStateFlow(false)
    public val chatOpen: StateFlow<Boolean> = _chatOpen.asStateFlow()

    private val _liveStartedAt = MutableStateFlow<Long?>(if (session.stream.isLive) System.currentTimeMillis() else null)
    public val liveStartedAt: StateFlow<Long?> = _liveStartedAt.asStateFlow()

    private val pendingPanelists = mutableSetOf<String>()
    private var knownWaitlistIds = setOf<String>()
    private var primedWaitlist = false
    private var knownStageRequestIds = setOf<String>()
    private var primedStageRequests = false
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var toastJob: Job? = null
    private var grantVerifyJob: Job? = null
    private var joinStageWatchdog: Job? = null
    private var chatSheetOpen = false

    public val isModerator: Boolean get() = session.role == StudioRole.MODERATOR
    public val canPublish: Boolean get() = isModerator && _streamStatus.value == "preview"
    public val canStop: Boolean get() = isModerator && (_streamStatus.value == "preview" || _streamStatus.value == "public")
    public val selfParticipant: StudioParticipant? get() = _participants.value.firstOrNull { it.isSelf }
    public val selfOnStage: Boolean get() = StudioStageLayout.isOnStage(_selfStage.value ?: selfParticipant?.stageStatus)
    public val canUseMediaControls: Boolean get() = isModerator || selfOnStage
    public val peopleBadge: Int get() = _waitlist.value.size + _stageRequests.value.size
    public val stageArrangement: StudioStageArrangement
        get() {
            val onStage = _participants.value.filter { StudioStageLayout.isOnStage(it.stageStatus) || it.isSelf }
            return StudioStageLayout.arrange(StudioStageLayout.expand(onStage), _activeSpeaker.value)
        }

    public fun start() {
        meeting.delegate = this
        _phase.value = StudioPhase.CONNECTING
        scope.launch {
            try {
                meeting.join(session.authToken, enableAudio = true, enableVideo = true)
                startEgressIfNeeded()
                startPolling()
            } catch (e: Exception) {
                _phase.value = StudioPhase.FAILED
                pushToast(e.message ?: "join failed")
            }
        }
    }

    public fun stop() {
        pollJob?.cancel()
        refreshJob?.cancel()
        toastJob?.cancel()
        grantVerifyJob?.cancel()
        joinStageWatchdog?.cancel()
        meeting.leave()
        api.close()
    }

    public fun leave() {
        pollJob?.cancel()
        refreshJob?.cancel()
        meeting.leave()
        _phase.value = StudioPhase.LEFT
        api.close()
    }

    public fun stopBroadcast() {
        if (!isModerator) {
            leave()
            return
        }
        val token = control
        scope.launch {
            if (token != null) runCatching { token.stop() }
            _streamStatus.value = "ended"
            _live.value = false
            meeting.leave()
            _phase.value = StudioPhase.ENDED
        }
    }

    public fun reconnect() {
        if (_phase.value == StudioPhase.ENDED || _phase.value == StudioPhase.LEFT || _phase.value == StudioPhase.REJECTED) return
        scope.launch {
            meeting.leave()
            _phase.value = StudioPhase.CONNECTING
            try {
                meeting.join(session.authToken, enableAudio = _micOn.value, enableVideo = _cameraOn.value)
                startEgressIfNeeded()
                startPolling()
            } catch (e: Exception) {
                _phase.value = StudioPhase.FAILED
                pushToast(e.message ?: "join failed")
            }
        }
    }

    public fun toggleCamera() {
        meeting.setCameraEnabled(!_cameraOn.value)
    }

    public fun toggleMic() {
        meeting.setMicrophoneEnabled(!_micOn.value)
    }

    public fun switchCamera() {
        meeting.switchCamera()
    }

    public fun toggleScreenShare() {
        if (_screenOn.value) meeting.disableScreenShare() else meeting.enableScreenShare()
    }

    public fun goLive() {
        if (!canPublish) return
        val token = control ?: return
        _publishing.value = true
        scope.launch {
            runCatching { token.publish() }
                .onSuccess { state ->
                    _streamStatus.value = state.stream.status
                    _live.value = state.stream.isLive
                    if (state.stream.isLive) _liveStartedAt.value = System.currentTimeMillis()
                }
                .onFailure { pushToast(it.message ?: "Could not go live") }
            _publishing.value = false
        }
    }

    public fun pin(participant: StudioParticipant) {
        if (participant.pinned) meeting.unpin(participant.id) else meeting.pin(participant.id)
    }

    public fun kick(id: String) {
        meeting.kick(id)
    }

    public fun muteRemote(id: String) {
        meeting.muteRemoteAudio(id)
        sendHostMedia("audio", findParticipant(id) ?: StudioParticipant(id = id, userId = id, name = ""))
    }

    public fun stopRemoteCamera(id: String) {
        meeting.disableRemoteVideo(id)
        sendHostMedia("video", findParticipant(id) ?: StudioParticipant(id = id, userId = id, name = ""))
    }

    public fun stopRemoteScreen(id: String) {
        val participant = findParticipant(id) ?: StudioParticipant(id = id, userId = id, name = "")
        sendHostMedia("screen", participant)
    }

    public fun takeOffAir(id: String) {
        if (!canTakeOffAir(id)) {
            pushToast("Can't take the last person off air while live")
            return
        }
        meeting.takeOffStage(id)
    }

    public fun canTakeOffAir(id: String): Boolean = StudioStageLayout.canTakeOffAir(_participants.value, id, _live.value)

    public fun bringOnAir(id: String) {
        val participant = findParticipant(id) ?: return
        meeting.grantStage(participant.stageId)
    }

    public fun admit(guest: StudioWaitlistedGuest, asRole: AdmitAs) {
        if (asRole == AdmitAs.PANELIST) queuePanelist(guest)
        meeting.acceptWaitingRoom(guest.id)
        if (asRole == AdmitAs.PANELIST) grantPendingIfPresent(guest)
    }

    public fun admitAll(asRole: AdmitAs) {
        val waiting = _waitlist.value
        if (asRole == AdmitAs.PANELIST) waiting.forEach(::queuePanelist)
        meeting.acceptAllWaitingRoom(waiting.map { it.id })
        if (asRole == AdmitAs.PANELIST) waiting.forEach(::grantPendingIfPresent)
    }

    public fun deny(guest: StudioWaitlistedGuest) {
        meeting.rejectWaitingRoom(guest.id)
    }

    public fun grantStage(request: StudioStageRequest) {
        meeting.grantStage(request.stageId)
    }

    public fun denyStage(request: StudioStageRequest) {
        meeting.denyStage(request.stageId)
    }

    public fun requestStage() {
        meeting.requestStage()
    }

    public fun cancelStageRequest() {
        meeting.cancelStageRequest()
    }

    public fun joinStage() {
        meeting.joinStage()
    }

    public fun leaveStage() {
        val self = selfParticipant
        if (self != null && !canTakeOffAir(self.id)) {
            pushToast("Can't leave the stage — you are the last person on air")
            return
        }
        meeting.leaveStage()
    }

    public fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        meeting.sendChat(trimmed.take(2_000))
    }

    public fun setChatOpen(open: Boolean) {
        chatSheetOpen = open
        _chatOpen.value = open
        if (open) _unreadChat.value = 0
    }

    public fun refreshDevices() {
        _audioDevices.value = meeting.audioDevices()
        _videoDevices.value = meeting.videoDevices()
        _selectedAudioId.value = meeting.selectedAudioDeviceId()
        _selectedVideoId.value = meeting.selectedVideoDeviceId()
    }

    public fun selectDevice(device: StudioMediaDevice) {
        when (device.kind) {
            StudioMediaDevice.Kind.AUDIO -> {
                meeting.setAudioDevice(device.id)
                _selectedAudioId.value = device.id
            }
            StudioMediaDevice.Kind.VIDEO -> {
                meeting.setVideoDevice(device.id)
                _selectedVideoId.value = device.id
            }
        }
    }

    public fun videoView(tile: StudioDisplayTile): android.view.View? {
        if (tile.kind == StudioTileKind.IDLE) return null
        return meeting.videoView(tile.participant.id, tile.kind == StudioTileKind.SCREEN)
    }

    public fun retryJoinStage() {
        if (_selfStage.value != StudioStageStatus.ACCEPTED_TO_JOIN_STAGE) return
        meeting.joinStage()
    }

    override fun meetingDidJoin() {
        if (_phase.value == StudioPhase.WAITLISTED || _phase.value == StudioPhase.CONNECTING || _phase.value == StudioPhase.FAILED) {
            _phase.value = StudioPhase.IN_ROOM
        }
        startEgressIfNeeded()
    }

    override fun meetingDidWaitlist() {
        _phase.value = StudioPhase.WAITLISTED
    }

    override fun meetingWasRejected() {
        _phase.value = StudioPhase.REJECTED
    }

    override fun meetingDidDisconnect(reason: MeetingDisconnectReason) {
        _phase.value =
            when (reason) {
                MeetingDisconnectReason.LEFT -> StudioPhase.LEFT
                MeetingDisconnectReason.KICKED -> StudioPhase.KICKED
                MeetingDisconnectReason.ENDED -> StudioPhase.ENDED
                MeetingDisconnectReason.FAILED -> StudioPhase.FAILED
            }
    }

    override fun meetingDidUpdateParticipants(participants: List<StudioParticipant>) {
        _participants.value = participants
        participants.forEach(::takeAndGrant)
        val self = participants.firstOrNull { it.isSelf }
        if (self != null) _selfStage.value = self.stageStatus ?: _selfStage.value
    }

    override fun meetingDidUpdateWaitlist(guests: List<StudioWaitlistedGuest>) {
        val ids = guests.map { it.id }.toSet()
        if (primedWaitlist) {
            guests.filter { it.id !in knownWaitlistIds }.forEach { pushToast("${it.name} is knocking") }
        } else {
            primedWaitlist = true
        }
        knownWaitlistIds = ids
        _waitlist.value = guests
    }

    override fun meetingDidReceiveChat(message: StudioChatMessage) {
        _chat.value = (_chat.value + message).takeLast(200)
        if (!chatSheetOpen && !message.isSelf) _unreadChat.value += 1
    }

    override fun meetingDidUpdateStageRequests(requests: List<StudioStageRequest>) {
        val ids = requests.map { it.id }.toSet()
        if (primedStageRequests) {
            requests.filter { it.id !in knownStageRequestIds }.forEach { pushToast("${it.name} wants to join the stage") }
        } else {
            primedStageRequests = true
        }
        knownStageRequestIds = ids
        _stageRequests.value = requests
    }

    override fun meetingMediaDidChange(cameraOn: Boolean, micOn: Boolean, screenShareOn: Boolean) {
        _cameraOn.value = cameraOn
        _micOn.value = micOn
        _screenOn.value = screenShareOn
    }

    override fun meetingDidUpdateActiveSpeaker(id: String?) {
        _activeSpeaker.value = id
    }

    override fun meetingDidUpdateDevices() {
        refreshDevices()
    }

    override fun meetingDidUpdateSignaling(state: StudioSignalingState) {
        _signaling.value = state
    }

    override fun meetingSelfStageDidChange(status: StudioStageStatus?) {
        val previous = _selfStage.value
        _selfStage.value = status
        when (status) {
            StudioStageStatus.ACCEPTED_TO_JOIN_STAGE -> {
                pushToast("You're approved to join the stage")
                meeting.joinStage()
                startJoinStageWatchdog()
            }
            StudioStageStatus.ON_STAGE -> joinStageWatchdog?.cancel()
            StudioStageStatus.OFF_STAGE ->
                if (previous == StudioStageStatus.REQUESTED) pushToast("Stage request was denied")
            else -> {}
        }
    }

    override fun meetingDidReceiveBroadcast(message: StudioBroadcastMessage) {
        if (message.type != "host-media") return
        val target = message.userId ?: message.payload["userId"] ?: message.payload["targetUserId"]
        val self = selfParticipant
        val matches = target.isNullOrEmpty() || target == self?.id || target == self?.userId
        if (!matches) return
        when (message.kind) {
            "audio" -> {
                meeting.setMicrophoneEnabled(false)
                pushToast("The host muted you")
            }
            "video" -> {
                meeting.setCameraEnabled(false)
                pushToast("The host stopped your camera")
            }
            "screen" -> {
                meeting.disableScreenShare()
                pushToast("The host stopped your screen share")
            }
        }
    }

    override fun meetingDidFailScreenShare(reason: String) {
        _screenOn.value = false
        pushToast(reason.ifBlank { "Screen share could not start" })
    }

    override fun meetingDidWarn(message: String) {
        pushToast(message)
    }

    private fun startEgressIfNeeded() {
        if (!pollNetwork || !isModerator) return
        val token = control ?: return
        scope.launch { runCatching { token.livestream() } }
    }

    private fun startPolling() {
        pollJob?.cancel()
        refreshJob?.cancel()
        if (!pollNetwork) return
        val interval = if (isModerator && control != null) 3_000L else 5_000L
        pollJob = scope.launch { pollPublicStatus(interval) }
        if (control != null) {
            refreshJob =
                scope.launch {
                    while (true) {
                        delay(30 * 60_000)
                        runCatching { control?.refresh() }
                    }
                }
        }
    }

    private suspend fun pollPublicStatus(intervalMs: Long) {
        val streamId = session.stream.id
        while (true) {
            val public = runCatching { api.public.stream(streamId) }.getOrNull()
            if (public != null) {
                _streamStatus.value = public.status
                _live.value = public.status == "public"
                if (public.status == "public" && _liveStartedAt.value == null) {
                    _liveStartedAt.value = System.currentTimeMillis()
                }
                if (public.status == "ended" || public.status == "error") {
                    _phase.value = StudioPhase.ENDED
                    return
                }
            }
            delay(intervalMs)
        }
    }

    private fun sendHostMedia(kind: String, participant: StudioParticipant) {
        val userId = participant.userId.ifEmpty { participant.id }
        meeting.sendBroadcast("host-media", mapOf("type" to "host-media", "kind" to kind, "userId" to userId))
    }

    private fun queuePanelist(guest: StudioWaitlistedGuest) {
        if (guest.userId.isNotEmpty()) pendingPanelists += guest.userId
        pendingPanelists += guest.id
    }

    private fun grantPendingIfPresent(guest: StudioWaitlistedGuest) {
        val match =
            _participants.value.firstOrNull {
                it.id == guest.id || it.userId == guest.id || (guest.userId.isNotEmpty() && it.userId == guest.userId)
            } ?: return
        takeAndGrant(match)
    }

    private fun takeAndGrant(participant: StudioParticipant) {
        val keys = listOf(participant.userId, participant.id).filter { it.isNotEmpty() }
        if (keys.none { it in pendingPanelists }) return
        pendingPanelists.removeAll(keys.toSet())
        meeting.grantStage(participant.stageId)
        grantVerifyJob?.cancel()
        grantVerifyJob =
            scope.launch {
                delay(4_000)
                val match = _participants.value.firstOrNull { it.id == participant.id || it.stageId == participant.stageId }
                if (match != null && !StudioStageLayout.isOnStage(match.stageStatus)) {
                    meeting.grantStage(match.stageId)
                }
            }
    }

    private fun startJoinStageWatchdog() {
        joinStageWatchdog?.cancel()
        joinStageWatchdog =
            scope.launch {
                repeat(8) {
                    delay(1_000)
                    if (_selfStage.value != StudioStageStatus.ACCEPTED_TO_JOIN_STAGE) return@launch
                    meeting.joinStage()
                }
                if (_selfStage.value == StudioStageStatus.ACCEPTED_TO_JOIN_STAGE) {
                    pushToast("Couldn't join the stage automatically. Tap Joining stage to retry.")
                }
            }
    }

    private fun findParticipant(id: String): StudioParticipant? = _participants.value.firstOrNull { it.id == id || it.userId == id || it.stageId == id }

    private fun pushToast(message: String) {
        val toast = StudioToast(message = message)
        _toast.value = toast
        toastJob?.cancel()
        toastJob =
            scope.launch {
                delay(4_000)
                if (_toast.value?.id == toast.id) _toast.value = null
            }
    }
}
