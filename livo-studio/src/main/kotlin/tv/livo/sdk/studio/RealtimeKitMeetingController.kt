package tv.livo.sdk.studio

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.cloudflare.realtimekit.RealtimeKitClient
import com.cloudflare.realtimekit.RealtimeKitMeetingBuilder
import com.cloudflare.realtimekit.RtkMeetingRoomEventListener
import com.cloudflare.realtimekit.chat.RtkChatEventListener
import com.cloudflare.realtimekit.chat.TextMessage
import com.cloudflare.realtimekit.events.RtkDataUpdateListener
import com.cloudflare.realtimekit.media.MediaPermission
import com.cloudflare.realtimekit.meta.SocketConnectionState
import com.cloudflare.realtimekit.meta.SocketState
import com.cloudflare.realtimekit.models.RtkMeetingInfo
import com.cloudflare.realtimekit.participants.RtkParticipants
import com.cloudflare.realtimekit.participants.RtkParticipantsEventListener
import com.cloudflare.realtimekit.participants.RtkRemoteParticipant
import com.cloudflare.realtimekit.self.RtkSelfEventListener
import com.cloudflare.realtimekit.self.RtkSelfParticipant
import com.cloudflare.realtimekit.stage.RtkStageEventListener
import com.cloudflare.realtimekit.stage.StageError
import com.cloudflare.realtimekit.stage.StageStatus
import com.cloudflare.realtimekit.waitingroom.RtkWaitlistEventListener
import com.cloudflare.realtimekit.waitingroom.WaitListStatus
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Host Activity is required for camera / MediaProjection. Video views are cached 1:1 per
 * (participant id, screen) — registering the same view twice nulls the track.
 */
public class RealtimeKitMeetingController(private val activityProvider: () -> Activity) : MeetingControlling {
    override var delegate: MeetingControllerDelegate? = null
    override var signalingState: StudioSignalingState = StudioSignalingState.DISCONNECTED
        private set

    private val main = Handler(Looper.getMainLooper())
    private val rendererCache = mutableMapOf<String, View>()
    private val relay = RealtimeKitListenerRelay(this)
    private var meeting: RealtimeKitClient? = null
    private var joining = false
    private var didJoinRoom = false
    private var pendingStageJoin = false
    private var joinStageInFlight = false
    private var joinStageAttempts = 0

    override suspend fun join(authToken: String, enableAudio: Boolean, enableVideo: Boolean) {
        if (joining) return
        joining = true
        val activity = activityProvider()
        val client = RealtimeKitMeetingBuilder.build(activity)
        meeting = client
        attachListeners(client)
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                var resumed = false
                fun finish(error: Throwable?) {
                    if (resumed) return
                    resumed = true
                    if (error != null) {
                        cont.resumeWith(Result.failure(error))
                    } else {
                        cont.resumeWith(Result.success(Unit))
                    }
                }
                client.init(
                    RtkMeetingInfo(authToken = authToken, enableAudio = enableAudio, enableVideo = enableVideo),
                    onSuccess = {
                        client.joinRoom(
                            onSuccess = { finish(null) },
                            onFailure = { finish(IllegalStateException(it.message)) },
                        )
                    },
                    onFailure = { finish(IllegalStateException(it.message)) },
                )
                cont.invokeOnCancellation { leave() }
            }
        } catch (e: Exception) {
            joining = false
            detach(client)
            meeting = null
            throw e
        }
        didJoinRoom = true
        joining = false
        signalingState = StudioSignalingState.CONNECTED
        publishAll()
        flushPendingStageJoin()
    }

    override fun leave() {
        didJoinRoom = false
        pendingStageJoin = false
        joinStageInFlight = false
        joining = false
        joinStageAttempts = 0
        val outgoing = meeting
        meeting = null
        if (outgoing != null) {
            detach(outgoing)
            outgoing.leaveRoom(onSuccess = {}, onFailure = {})
            outgoing.release(onSuccess = {}, onFailure = {})
        }
        releaseRenderers()
        signalingState = StudioSignalingState.DISCONNECTED
        notify { it.meetingDidUpdateSignaling(StudioSignalingState.DISCONNECTED) }
    }

    override fun setCameraEnabled(enabled: Boolean) {
        val user = meeting?.localUser ?: return
        if (enabled) user.enableVideo { publishMedia() } else user.disableVideo { publishMedia() }
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        val user = meeting?.localUser ?: return
        if (enabled) user.enableAudio { publishMedia() } else user.disableAudio { publishMedia() }
    }

    override fun switchCamera() {
        meeting?.localUser?.switchCamera()
    }

    override fun enableScreenShare() {
        meeting?.localUser?.enableScreenShare { error ->
            if (error != null) {
                notify { it.meetingDidFailScreenShare(error.message) }
            } else {
                publishMedia()
            }
        }
    }

    override fun disableScreenShare() {
        meeting?.localUser?.disableScreenShare { publishMedia() }
    }

    override fun acceptWaitingRoom(id: String) {
        meeting?.participants?.acceptWaitingRoomRequest(id)
    }

    override fun rejectWaitingRoom(id: String) {
        meeting?.participants?.rejectWaitingRoomRequest(id)
    }

    override fun acceptAllWaitingRoom(ids: List<String>) {
        ids.forEach { meeting?.participants?.acceptWaitingRoomRequest(it) }
    }

    override fun kick(id: String) {
        remote(id)?.kick()
    }

    override fun pin(id: String) {
        remote(id)?.pin()
    }

    override fun unpin(id: String) {
        remote(id)?.unpin()
    }

    override fun sendChat(text: String) {
        meeting?.chat?.sendTextMessage(text)
    }

    override fun requestStage() {
        reportStageError(meeting?.stage?.requestAccess())
    }

    override fun cancelStageRequest() {
        reportStageError(meeting?.stage?.cancelRequestAccess())
    }

    override fun joinStage() {
        pendingStageJoin = true
        flushPendingStageJoin()
    }

    override fun leaveStage() {
        pendingStageJoin = false
        reportStageError(meeting?.stage?.leave())
        publishSelfStage()
    }

    override fun grantStage(id: String) {
        reportStageError(meeting?.stage?.grantAccess(listOf(id)))
    }

    override fun denyStage(id: String) {
        reportStageError(meeting?.stage?.denyAccess(listOf(id)))
    }

    override fun takeOffStage(id: String) {
        reportStageError(meeting?.stage?.kick(listOf(id)))
    }

    override fun muteRemoteAudio(id: String) {
        remote(id)?.disableAudio()
    }

    override fun disableRemoteVideo(id: String) {
        remote(id)?.disableVideo()
    }

    override fun sendBroadcast(type: String, payload: Map<String, String>) {
        meeting?.participants?.broadcastMessage(type, payload)
    }

    override fun audioDevices(): List<StudioMediaDevice> =
        meeting?.localUser?.getAudioDevices().orEmpty().map { StudioMediaDevice(it.id, it.type.displayName, StudioMediaDevice.Kind.AUDIO) }

    override fun videoDevices(): List<StudioMediaDevice> =
        meeting?.localUser?.getVideoDevices().orEmpty().map { StudioMediaDevice(it.id, it.toString(), StudioMediaDevice.Kind.VIDEO) }

    override fun selectedAudioDeviceId(): String? = meeting?.localUser?.getSelectedAudioDevice()?.id

    override fun selectedVideoDeviceId(): String? = meeting?.localUser?.getSelectedVideoDevice()?.id

    override fun setAudioDevice(id: String) {
        val device = meeting?.localUser?.getAudioDevices()?.firstOrNull { it.id == id } ?: return
        meeting?.localUser?.setAudioDevice(device)
    }

    override fun setVideoDevice(id: String) {
        val device = meeting?.localUser?.getVideoDevices()?.firstOrNull { it.id == id } ?: return
        meeting?.localUser?.setVideoDevice(device)
    }

    override fun videoView(participantId: String, screenShare: Boolean): View? {
        val key = rendererKey(participantId, screenShare)
        rendererCache[key]?.let { return it }
        val meeting = meeting ?: return null
        val view =
            if (isLocalUser(participantId)) {
                if (screenShare) {
                    meeting.localUser.getScreenShareVideoView()
                } else {
                    meeting.localUser.getVideoView() ?: meeting.localUser.getSelfPreview()
                }
            } else {
                val remote = remote(participantId) ?: return null
                if (screenShare) remote.getScreenShareVideoView() else remote.getVideoView()
            } ?: return null
        (view.parent as? ViewGroup)?.removeView(view)
        rendererCache[key] = view
        return view
    }

    internal fun handleJoinedRoom() {
        didJoinRoom = true
        notify { it.meetingDidJoin() }
        publishParticipants()
        flushPendingStageJoin()
    }

    internal fun handleWaitList(status: WaitListStatus) {
        when (status) {
            WaitListStatus.WAITING -> notify { it.meetingDidWaitlist() }
            WaitListStatus.REJECTED -> notify { it.meetingWasRejected() }
            WaitListStatus.ACCEPTED -> notify { it.meetingDidJoin() }
            WaitListStatus.NONE -> {}
        }
    }

    internal fun handleSocket(state: SocketConnectionState) {
        if (!didJoinRoom) return
        if (state.isReconnectionFailure) {
            signalingState = StudioSignalingState.FAILED
            notify { it.meetingDidUpdateSignaling(StudioSignalingState.FAILED) }
            notify { it.meetingDidDisconnect(MeetingDisconnectReason.FAILED) }
            return
        }
        signalingState = mapSignaling(state)
        notify { it.meetingDidUpdateSignaling(signalingState) }
        when (state.socketState) {
            SocketState.RECONNECTING -> notify { it.meetingDidWarn("Reconnecting…") }
            SocketState.DISCONNECTED, SocketState.FAILED ->
                notify { it.meetingDidDisconnect(MeetingDisconnectReason.FAILED) }
            SocketState.CONNECTED -> {}
        }
    }

    internal fun handlePermissionsAllowed() {
        pendingStageJoin = true
        flushPendingStageJoin()
    }

    internal fun handleChat(id: String, userId: String, name: String, text: String) {
        val selfId = meeting?.localUser?.userId
        notify {
            it.meetingDidReceiveChat(
                StudioChatMessage(
                    id = id,
                    displayName = name,
                    text = text,
                    isSelf = userId == selfId,
                    userId = userId,
                ),
            )
        }
    }

    internal fun handleBroadcast(type: String, payload: Map<String, *>) {
        val parsed = parseBroadcast(type, payload) ?: return
        notify { it.meetingDidReceiveBroadcast(parsed) }
    }

    internal fun handleStageAccepted() {
        pendingStageJoin = true
        joinStage()
    }

    internal fun handleStageStatus(status: StageStatus) {
        if (status == StageStatus.ACCEPTED_TO_JOIN_STAGE) {
            pendingStageJoin = true
            joinStage()
        }
        publishSelfStage()
        publishParticipants()
    }

    internal fun handleRemovedFromMeeting() {
        notify { it.meetingDidDisconnect(MeetingDisconnectReason.KICKED) }
    }

    internal fun handleMeetingEnded() {
        notify { it.meetingDidDisconnect(MeetingDisconnectReason.ENDED) }
    }

    internal fun handleScreenShareFailed(reason: String) {
        publishMedia()
        notify { it.meetingDidFailScreenShare(reason) }
    }

    internal fun handleActiveSpeaker(id: String?) {
        notify { it.meetingDidUpdateActiveSpeaker(id) }
    }

    internal fun invalidateRenderer(id: String, userId: String?, screenShare: Boolean) {
        listOfNotNull(id, userId).forEach { rendererCache.remove(rendererKey(it, screenShare))?.let(::releaseView) }
    }

    internal fun invalidateAllRenderers(id: String, userId: String?) {
        invalidateRenderer(id, userId, false)
        invalidateRenderer(id, userId, true)
    }

    internal fun publishParticipants() {
        val meeting = meeting ?: return
        val sharingIds = meeting.participants.screenShares.map { it.id }.toSet()
        val tiles = mutableListOf<StudioParticipant>()
        val selfUser = meeting.localUser
        tiles.add(mapTile(selfUser, isSelf = true, screen = selfUser.screenShareEnabled || sharingIds.contains(selfUser.id)))
        for (participant in meeting.participants.joined) {
            tiles.add(
                mapTile(
                    participant,
                    isSelf = false,
                    screen = participant.screenShareEnabled || sharingIds.contains(participant.id),
                ),
            )
        }
        notify { it.meetingDidUpdateParticipants(tiles) }
    }

    internal fun publishWaitlist() {
        val guests =
            meeting?.participants?.waitlisted.orEmpty().map {
                StudioWaitlistedGuest(id = it.id, name = it.name, userId = it.userId)
            }
        notify { it.meetingDidUpdateWaitlist(guests) }
    }

    internal fun publishStage(requests: List<RtkRemoteParticipant>) {
        val mapped = requests.map { StudioStageRequest(id = it.id, userId = it.userId, name = it.name) }
        notify { it.meetingDidUpdateStageRequests(mapped) }
    }

    internal fun publishMedia() {
        val user = meeting?.localUser ?: return
        notify { it.meetingMediaDidChange(user.videoEnabled, user.audioEnabled, user.screenShareEnabled) }
    }

    internal fun publishSelfStage() {
        val mapped = mapStage(meeting?.stage?.stageStatus ?: meeting?.localUser?.stageStatus)
        notify { it.meetingSelfStageDidChange(mapped) }
    }

    internal fun publishDevices() {
        notify { it.meetingDidUpdateDevices() }
    }

    internal fun publishParticipantsAndWaitlist() {
        publishParticipants()
        publishWaitlist()
    }

    private fun publishAll() {
        publishSignaling()
        publishParticipants()
        publishWaitlist()
        publishMedia()
        publishSelfStage()
        publishDevices()
    }

    private fun publishSignaling() {
        notify { it.meetingDidUpdateSignaling(signalingState) }
    }

    private fun flushPendingStageJoin() {
        if (!didJoinRoom) return
        val accepted = currentStageStatus() == StageStatus.ACCEPTED_TO_JOIN_STAGE
        if (!pendingStageJoin && !accepted) return
        if (joinStageInFlight) return
        joinStageInFlight = true
        joinStageAttempts = 0
        runJoinStageAttempt()
    }

    private fun runJoinStageAttempt() {
        val meeting = meeting
        if (meeting == null) {
            joinStageInFlight = false
            return
        }
        if (currentStageStatus() == StageStatus.ON_STAGE) {
            pendingStageJoin = false
            joinStageInFlight = false
            publishSelfStage()
            publishParticipants()
            return
        }
        val error = meeting.stage.join()
        if (error != null && isStageBusy(error) && joinStageAttempts < 2) {
            joinStageAttempts += 1
            main.postDelayed({ runJoinStageAttempt() }, 1_000)
            return
        }
        if (error != null) reportStageError(error)
        pendingStageJoin = false
        joinStageInFlight = false
        if (currentStageStatus() == StageStatus.ON_STAGE) republishCameraIfOn()
        publishSelfStage()
        publishParticipants()
        publishMedia()
    }

    private fun republishCameraIfOn() {
        val user = meeting?.localUser ?: return
        if (!user.videoEnabled) return
        user.disableVideo {
            user.enableVideo { publishMedia() }
        }
    }

    private fun currentStageStatus(): StageStatus? = meeting?.stage?.stageStatus ?: meeting?.localUser?.stageStatus

    private fun isStageBusy(error: StageError): Boolean {
        val message = error.message + error.toString()
        return message.contains("2006") ||
            message.contains("ERR2006") ||
            message.contains("OFF_STAGE", ignoreCase = true) ||
            message.contains("concurrent", ignoreCase = true)
    }

    private fun reportStageError(error: StageError?) {
        if (error == null) return
        val message = error.message.ifBlank { "Stage action failed" }
        notify { it.meetingDidWarn(message) }
    }

    private fun isLocalUser(id: String): Boolean {
        val user = meeting?.localUser ?: return false
        return user.id == id || user.userId == id
    }

    private fun remote(id: String): RtkRemoteParticipant? {
        val meeting = meeting ?: return null
        return meeting.participants.joined.firstOrNull { it.id == id || it.userId == id }
            ?: meeting.participants.screenShares.firstOrNull { it.id == id || it.userId == id }
    }

    private fun attachListeners(client: RealtimeKitClient) {
        client.addMeetingRoomEventListener(relay)
        client.addParticipantsEventListener(relay)
        client.addSelfEventListener(relay)
        client.addWaitlistEventListener(relay)
        client.addChatEventListener(relay)
        client.addStageEventListener(relay)
        client.addDataUpdateListener(relay)
    }

    private fun detach(client: RealtimeKitClient) {
        client.removeMeetingRoomEventListener(relay)
        client.removeParticipantsEventListener(relay)
        client.removeSelfEventListener(relay)
        client.removeWaitlistEventListener(relay)
        client.removeChatEventListener(relay)
        client.removeStageEventListener(relay)
        client.removeDataUpdateListener(relay)
    }

    private fun releaseRenderers() {
        rendererCache.values.forEach(::releaseView)
        rendererCache.clear()
    }

    private fun releaseView(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        (view as? com.cloudflare.realtimekit.platform.VideoView)?.release()
    }

    private fun notify(work: (MeetingControllerDelegate) -> Unit) {
        val target = delegate ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            work(target)
        } else {
            main.post { work(target) }
        }
    }

    private companion object {
        fun rendererKey(id: String, screenShare: Boolean): String = "$id:${if (screenShare) "screen" else "camera"}"

        fun mapSignaling(state: SocketConnectionState): StudioSignalingState = when (state.socketState) {
            SocketState.CONNECTED -> StudioSignalingState.CONNECTED
            SocketState.RECONNECTING -> StudioSignalingState.RECONNECTING
            SocketState.DISCONNECTED -> StudioSignalingState.DISCONNECTED
            SocketState.FAILED -> StudioSignalingState.FAILED
        }

        fun mapStage(status: StageStatus?): StudioStageStatus? = when (status) {
            StageStatus.OFF_STAGE -> StudioStageStatus.OFF_STAGE
            StageStatus.REQUESTED_TO_JOIN_STAGE -> StudioStageStatus.REQUESTED
            StageStatus.ACCEPTED_TO_JOIN_STAGE -> StudioStageStatus.ACCEPTED_TO_JOIN_STAGE
            StageStatus.ON_STAGE -> StudioStageStatus.ON_STAGE
            null -> null
        }

        fun mapTile(participant: com.cloudflare.realtimekit.RtkMeetingParticipant, isSelf: Boolean, screen: Boolean): StudioParticipant = StudioParticipant(
            id = participant.id,
            userId = participant.userId,
            name = participant.name.ifBlank { if (isSelf) "You" else "Guest" },
            picture = participant.picture,
            isSelf = isSelf,
            audioEnabled = participant.audioEnabled,
            videoEnabled = participant.videoEnabled,
            screenShareOn = screen,
            stageStatus = mapStage(participant.stageStatus),
            pinned = participant.isPinned,
        )

        fun parseBroadcast(type: String, payload: Map<String, *>): StudioBroadcastMessage? {
            @Suppress("UNCHECKED_CAST")
            val nested = payload["payload"] as? Map<String, *>
            val body = nested ?: payload
            val resolved = (body["type"] as? String) ?: type
            if (resolved != "host-media") return null
            val kind = body["kind"] as? String ?: return null
            val target = (body["userId"] as? String) ?: (body["targetUserId"] as? String)
            val flat = body.entries.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
            return StudioBroadcastMessage(type = "host-media", kind = kind, userId = target, payload = flat)
        }
    }
}

internal class RealtimeKitListenerRelay(private val engine: RealtimeKitMeetingController) :
    RtkMeetingRoomEventListener,
    RtkParticipantsEventListener,
    RtkSelfEventListener,
    RtkWaitlistEventListener,
    RtkChatEventListener,
    RtkStageEventListener,
    RtkDataUpdateListener {
    override fun onMeetingRoomJoinCompleted(meeting: RealtimeKitClient) {
        engine.handleJoinedRoom()
    }

    override fun onMeetingEnded() {
        engine.handleMeetingEnded()
    }

    override fun onSocketConnectionUpdate(newState: SocketConnectionState) {
        engine.handleSocket(newState)
    }

    override fun onUpdate(participants: RtkParticipants) {
        engine.publishParticipantsAndWaitlist()
    }

    override fun onParticipantJoin(participant: RtkRemoteParticipant) {
        engine.publishParticipants()
    }

    override fun onParticipantLeave(participant: RtkRemoteParticipant) {
        engine.invalidateAllRenderers(participant.id, participant.userId.ifEmpty { null })
        engine.publishParticipants()
    }

    override fun onAudioUpdate(participant: RtkRemoteParticipant, isEnabled: Boolean) {
        engine.publishParticipants()
    }

    override fun onVideoUpdate(participant: RtkRemoteParticipant, isEnabled: Boolean) {
        engine.invalidateRenderer(participant.id, participant.userId, false)
        engine.publishParticipants()
    }

    override fun onScreenShareUpdate(participant: RtkRemoteParticipant, isEnabled: Boolean) {
        engine.invalidateRenderer(participant.id, participant.userId, true)
        engine.publishParticipants()
    }

    override fun onParticipantPinned(participant: RtkRemoteParticipant) {
        engine.publishParticipants()
    }

    override fun onParticipantUnpinned(participant: RtkRemoteParticipant) {
        engine.publishParticipants()
    }

    override fun onActiveSpeakerChanged(participant: RtkRemoteParticipant?) {
        engine.handleActiveSpeaker(participant?.id)
    }

    override fun onNewBroadcastMessage(type: String, payload: Map<String, *>) {
        engine.handleBroadcast(type, payload)
    }

    override fun onWaitListStatusUpdate(waitListStatus: WaitListStatus) {
        engine.handleWaitList(waitListStatus)
    }

    override fun onRemovedFromMeeting() {
        engine.handleRemovedFromMeeting()
    }

    override fun onScreenShareStartFailed(reason: String) {
        engine.handleScreenShareFailed(reason)
    }

    override fun onUpdate(participant: RtkSelfParticipant) {
        engine.publishMedia()
        engine.publishParticipants()
    }

    override fun onVideoUpdate(isEnabled: Boolean) {
        engine.publishMedia()
        engine.publishParticipants()
    }

    override fun onAudioUpdate(isEnabled: Boolean) {
        engine.publishMedia()
    }

    override fun onScreenShareUpdate(isEnabled: Boolean) {
        engine.publishMedia()
        engine.publishParticipants()
    }

    override fun onAudioDevicesUpdated(devices: List<com.cloudflare.realtimekit.media.AudioDevice>) {
        engine.publishDevices()
    }

    override fun onAudioDeviceChanged(audioDevice: com.cloudflare.realtimekit.media.AudioDevice) {
        engine.publishDevices()
    }

    override fun onVideoDeviceChanged(videoDevice: com.cloudflare.realtimekit.media.VideoDevice) {
        engine.publishDevices()
    }

    override fun onPermissionsUpdated(permission: com.cloudflare.realtimekit.network.info.SelfPermissions) {
        if (permission.miscellaneous.stageAccess == MediaPermission.ALLOWED) {
            engine.handlePermissionsAllowed()
        }
    }

    override fun onWaitListParticipantJoined(participant: RtkRemoteParticipant) {
        engine.publishWaitlist()
    }

    override fun onWaitListParticipantAccepted(participant: RtkRemoteParticipant) {
        engine.publishParticipantsAndWaitlist()
    }

    override fun onWaitListParticipantRejected(participant: RtkRemoteParticipant) {
        engine.publishWaitlist()
    }

    override fun onWaitListParticipantClosed(participant: RtkRemoteParticipant) {
        engine.publishWaitlist()
    }

    override fun onNewChatMessage(message: com.cloudflare.realtimekit.chat.ChatMessage) {
        val text = (message as? TextMessage)?.message ?: return
        engine.handleChat(message.id, message.userId, message.displayName, text)
    }

    override fun onNewStageAccessRequest(participant: RtkRemoteParticipant) {
        engine.publishStage(listOf(participant))
    }

    override fun onStageAccessRequestsUpdated(accessRequests: List<RtkRemoteParticipant>) {
        engine.publishStage(accessRequests)
    }

    override fun onStageAccessRequestAccepted() {
        engine.handleStageAccepted()
    }

    override fun onStageAccessRequestRejected() {
        engine.publishSelfStage()
    }

    override fun onRemovedFromStage() {
        engine.publishSelfStage()
        engine.publishParticipants()
    }

    override fun onPeerStageStatusUpdated(participant: RtkRemoteParticipant, oldStatus: StageStatus, newStatus: StageStatus) {
        engine.publishParticipants()
    }

    override fun onStageStatusUpdated(oldStatus: StageStatus, newStatus: StageStatus) {
        engine.handleStageStatus(newStatus)
    }

    override fun onScreenShareUpdate(screenShares: List<RtkRemoteParticipant>) {
        screenShares.forEach { engine.invalidateRenderer(it.id, it.userId, true) }
        engine.publishParticipants()
    }
}
