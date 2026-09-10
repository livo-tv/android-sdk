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

public enum class StudioPhase {
    CONNECTING,
    WAITLISTED,
    REJECTED,
    IN_ROOM,
    ENDED,
    LEFT,
    FAILED,
}

public enum class StudioEvent {
    JOINED,
    LIVE,
    ENDED,
    LEFT,
}

public data class StudioToast(val message: String)

public data class StudioStageLayout(val spotlight: List<StudioParticipant>, val strip: List<StudioParticipant>, val overflow: Int) {
    public companion object {
        public const val STRIP_CAP: Int = 6
        public const val GRID_CAP: Int = 9

        public fun arrange(participants: List<StudioParticipant>, selfId: String?, grid: Boolean): StudioStageLayout {
            val onStage = participants.filter { it.stageStatus == StudioStageStatus.ON_STAGE || it.isSelf }
            val cap = if (grid) GRID_CAP else STRIP_CAP
            val spotlight = onStage.take(1)
            val rest = onStage.drop(1)
            val overflow = (rest.size - cap).coerceAtLeast(0)
            return StudioStageLayout(spotlight, rest.take(cap), overflow)
        }
    }
}

public class StudioRoomModel(
    public val session: StudioSession,
    public val hosts: LivoHosts,
    public val meeting: MeetingControlling,
    private val scope: CoroutineScope,
    credentials: LivoCredentials = LivoCredentials.None,
    private val pollNetwork: Boolean = true,
) : MeetingControllerDelegate {
    private val api = LivoApiClient(hosts, credentials)
    private var control: StudioControlClient? =
        session.studioControlToken?.let { api.controlClient(it) }

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

    private val _live = MutableStateFlow(session.stream.isLive)
    public val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _toast = MutableStateFlow<StudioToast?>(null)
    public val toast: StateFlow<StudioToast?> = _toast.asStateFlow()

    private val _selfStage = MutableStateFlow<StudioStageStatus?>(null)
    public val selfStage: StateFlow<StudioStageStatus?> = _selfStage.asStateFlow()

    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var joinStageAttempts = 0

    public fun start() {
        meeting.delegate = this
        scope.launch {
            try {
                meeting.join(session.authToken, enableAudio = true, enableVideo = true)
            } catch (e: Exception) {
                _phase.value = StudioPhase.FAILED
                _toast.value = StudioToast(e.message ?: "join failed")
            }
        }
        if (pollNetwork && session.role == StudioRole.GUEST) {
            pollJob = scope.launch { pollPublicStatus(5_000) }
        } else if (pollNetwork && control != null) {
            pollJob = scope.launch { pollPublicStatus(3_000) }
            refreshJob =
                scope.launch {
                    while (true) {
                        delay(30 * 60_000)
                        runCatching { control?.refresh() }
                    }
                }
        }
    }

    public fun stop() {
        pollJob?.cancel()
        refreshJob?.cancel()
        meeting.leave()
        api.close()
    }

    public fun toggleCamera() {
        meeting.setCameraEnabled(!_cameraOn.value)
    }

    public fun toggleMic() {
        meeting.setMicrophoneEnabled(!_micOn.value)
    }

    public fun toggleScreenShare() {
        if (_screenOn.value) meeting.disableScreenShare() else meeting.enableScreenShare()
    }

    public fun goLive() {
        val token = control ?: return
        scope.launch {
            runCatching { token.livestream() }
            runCatching { token.publish() }
            _live.value = true
        }
    }

    public fun stopLive() {
        val token = control ?: return
        scope.launch {
            runCatching { token.stop() }
            _live.value = false
        }
    }

    public fun kick(id: String) {
        meeting.kick(id)
    }

    public fun muteRemote(id: String) {
        meeting.muteRemoteAudio(id)
        meeting.sendBroadcast("host-media", mapOf("kind" to "audio", "userId" to id))
    }

    public fun stopRemoteCamera(id: String) {
        meeting.disableRemoteVideo(id)
        meeting.sendBroadcast("host-media", mapOf("kind" to "video", "userId" to id))
    }

    public fun stopRemoteScreen(id: String) {
        meeting.sendBroadcast("host-media", mapOf("kind" to "screen", "userId" to id))
    }

    public fun takeOffAir(id: String) {
        if (!canTakeOffAir(id)) {
            _toast.value = StudioToast("Keep at least one person on air while live")
            return
        }
        meeting.takeOffStage(id)
    }

    public fun canTakeOffAir(id: String): Boolean {
        if (!_live.value) return true
        val onStage = _participants.value.filter { it.stageStatus == StudioStageStatus.ON_STAGE }
        return onStage.size > 1 || onStage.none { it.stageId == id || it.id == id }
    }

    public fun admitAsPanelist(id: String) {
        meeting.grantStage(id)
        meeting.acceptWaitingRoom(id)
    }

    public fun admitAsAudience(id: String) {
        meeting.acceptWaitingRoom(id)
    }

    public fun sendChat(text: String) {
        meeting.sendChat(text)
    }

    public fun retryJoinStage() {
        if (_selfStage.value != StudioStageStatus.ACCEPTED_TO_JOIN_STAGE) return
        scope.launch {
            repeat(3) {
                joinStageAttempts += 1
                meeting.joinStage()
                delay(1_000)
            }
        }
    }

    override fun meetingDidJoin() {
        _phase.value = StudioPhase.IN_ROOM
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
                MeetingDisconnectReason.KICKED, MeetingDisconnectReason.ENDED -> StudioPhase.ENDED
                MeetingDisconnectReason.FAILED -> StudioPhase.FAILED
            }
    }

    override fun meetingDidUpdateParticipants(participants: List<StudioParticipant>) {
        _participants.value = participants
    }

    override fun meetingDidUpdateWaitlist(guests: List<StudioWaitlistedGuest>) {
        _waitlist.value = guests
    }

    override fun meetingDidReceiveChat(message: StudioChatMessage) {
        _chat.value = _chat.value + message
    }

    override fun meetingDidUpdateStageRequests(requests: List<StudioStageRequest>) {
        _stageRequests.value = requests
    }

    override fun meetingMediaDidChange(cameraOn: Boolean, micOn: Boolean, screenShareOn: Boolean) {
        _cameraOn.value = cameraOn
        _micOn.value = micOn
        _screenOn.value = screenShareOn
    }

    override fun meetingDidUpdateActiveSpeaker(id: String?) {}

    override fun meetingDidUpdateDevices() {}

    override fun meetingSelfStageDidChange(status: StudioStageStatus?) {
        _selfStage.value = status
        if (status == StudioStageStatus.ACCEPTED_TO_JOIN_STAGE) retryJoinStage()
    }

    override fun meetingDidReceiveBroadcast(message: StudioBroadcastMessage) {
        if (message.type != "host-media") return
        when (message.kind) {
            "audio" -> meeting.setMicrophoneEnabled(false)
            "video" -> meeting.setCameraEnabled(false)
            "screen" -> meeting.disableScreenShare()
        }
        _toast.value = StudioToast("A host updated your media")
    }

    override fun meetingDidFailScreenShare(reason: String) {
        _screenOn.value = false
        _toast.value = StudioToast(reason)
    }

    private suspend fun pollPublicStatus(intervalMs: Long) {
        val streamId = session.stream.id
        while (true) {
            val public = runCatching { api.public.stream(streamId) }.getOrNull()
            if (public != null) {
                _live.value = public.status == "public"
                if (public.status == "ended" || public.status == "error") {
                    _phase.value = StudioPhase.ENDED
                    return
                }
            }
            delay(intervalMs)
        }
    }
}
