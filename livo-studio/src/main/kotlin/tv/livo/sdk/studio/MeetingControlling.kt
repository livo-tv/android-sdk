package tv.livo.sdk.studio

import android.view.View

public enum class StudioSignalingState {
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    FAILED,
}

public enum class StudioStageStatus {
    ON_STAGE,
    OFF_STAGE,
    REQUESTED,
    ACCEPTED_TO_JOIN_STAGE,
}

public enum class StudioHostEvent { JOINED, LIVE, ENDED, LEFT, SCREEN_SHARE_ON, SCREEN_SHARE_OFF }

public enum class MeetingDisconnectReason {
    LEFT,
    KICKED,
    ENDED,
    FAILED,
}

public data class StudioMediaDevice(val id: String, val name: String, val kind: Kind) {
    public enum class Kind { AUDIO, VIDEO }
}

public data class StudioParticipant(
    val id: String,
    val userId: String = "",
    val name: String,
    val picture: String? = null,
    val isSelf: Boolean = false,
    val audioEnabled: Boolean = true,
    val videoEnabled: Boolean = true,
    val screenShareOn: Boolean = false,
    val isWaitlisted: Boolean = false,
    val stageStatus: StudioStageStatus? = null,
    val pinned: Boolean = false,
) {
    public val stageId: String get() = userId.ifEmpty { id }
}

public data class StudioWaitlistedGuest(val id: String, val name: String, val userId: String = "")

public data class StudioChatMessage(val id: String, val displayName: String, val text: String, val isSelf: Boolean = false, val userId: String = "")

public data class StudioStageRequest(val id: String, val userId: String, val name: String) {
    public val stageId: String get() = userId.ifEmpty { id }
}

public data class StudioBroadcastMessage(val type: String, val kind: String? = null, val userId: String? = null, val payload: Map<String, String> = emptyMap())

public interface MeetingControllerDelegate {
    public fun meetingDidJoin()

    public fun meetingDidWaitlist()

    public fun meetingWasRejected()

    public fun meetingDidDisconnect(reason: MeetingDisconnectReason)

    public fun meetingDidUpdateParticipants(participants: List<StudioParticipant>)

    public fun meetingDidUpdateWaitlist(guests: List<StudioWaitlistedGuest>)

    public fun meetingDidReceiveChat(message: StudioChatMessage)

    public fun meetingDidUpdateStageRequests(requests: List<StudioStageRequest>)

    public fun meetingMediaDidChange(cameraOn: Boolean, micOn: Boolean, screenShareOn: Boolean)

    public fun meetingDidUpdateActiveSpeaker(id: String?)

    public fun meetingSelfStageDidChange(status: StudioStageStatus?)

    public fun meetingDidReceiveBroadcast(message: StudioBroadcastMessage)

    public fun meetingDidFailScreenShare(reason: String)

    public fun meetingDidUpdateDevices()

    public fun meetingDidUpdateSignaling(state: StudioSignalingState) {}

    public fun meetingDidWarn(message: String) {}
}

public interface MeetingControlling {
    public var delegate: MeetingControllerDelegate?

    public val signalingState: StudioSignalingState

    public suspend fun join(authToken: String, enableAudio: Boolean, enableVideo: Boolean)

    public fun leave()

    public fun setCameraEnabled(enabled: Boolean)

    public fun setMicrophoneEnabled(enabled: Boolean)

    public fun switchCamera()

    public fun enableScreenShare()

    public fun disableScreenShare()

    public fun acceptWaitingRoom(id: String)

    public fun rejectWaitingRoom(id: String)

    public fun acceptAllWaitingRoom(ids: List<String>)

    public fun kick(id: String)

    public fun pin(id: String)

    public fun unpin(id: String)

    public fun sendChat(text: String)

    public fun requestStage()

    public fun cancelStageRequest()

    public fun joinStage()

    public fun leaveStage()

    public fun grantStage(id: String)

    public fun denyStage(id: String)

    public fun takeOffStage(id: String)

    public fun muteRemoteAudio(id: String)

    public fun disableRemoteVideo(id: String)

    public fun sendBroadcast(type: String, payload: Map<String, String>)

    public fun audioDevices(): List<StudioMediaDevice>

    public fun videoDevices(): List<StudioMediaDevice>

    public fun selectedAudioDeviceId(): String?

    public fun selectedVideoDeviceId(): String?

    public fun setAudioDevice(id: String)

    public fun setVideoDevice(id: String)

    public fun videoView(participantId: String, screenShare: Boolean): View? = null
}

public class FakeMeetingController : MeetingControlling {
    override var delegate: MeetingControllerDelegate? = null
    override var signalingState: StudioSignalingState = StudioSignalingState.DISCONNECTED
    public var joined: Boolean = false
    public var cameraOn: Boolean = true
    public var micOn: Boolean = true
    public var screenOn: Boolean = false
    public val broadcasts: MutableList<Pair<String, Map<String, String>>> = mutableListOf()
    public val kicked: MutableList<String> = mutableListOf()
    public val acceptedWait: MutableList<String> = mutableListOf()
    public val rejectedWait: MutableList<String> = mutableListOf()
    public val grantedStage: MutableList<String> = mutableListOf()
    public val deniedStage: MutableList<String> = mutableListOf()
    public val takenOffStage: MutableList<String> = mutableListOf()
    public val pinned: MutableList<String> = mutableListOf()
    public val chatSent: MutableList<String> = mutableListOf()
    public var stageRequested: Boolean = false
    public var stageJoined: Boolean = false
    public val waitlisted: MutableList<StudioWaitlistedGuest> = mutableListOf()
    public val participants: MutableList<StudioParticipant> = mutableListOf()
    public var audioDeviceList: List<StudioMediaDevice> = emptyList()
    public var videoDeviceList: List<StudioMediaDevice> = emptyList()
    public var selectedAudio: String? = null
    public var selectedVideo: String? = null

    override suspend fun join(authToken: String, enableAudio: Boolean, enableVideo: Boolean) {
        joined = true
        signalingState = StudioSignalingState.CONNECTED
        cameraOn = enableVideo
        micOn = enableAudio
        delegate?.meetingDidJoin()
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
        delegate?.meetingDidUpdateSignaling(signalingState)
    }

    override fun leave() {
        joined = false
        signalingState = StudioSignalingState.DISCONNECTED
        delegate?.meetingDidDisconnect(MeetingDisconnectReason.LEFT)
    }

    public fun rejectJoin() {
        delegate?.meetingWasRejected()
    }

    public fun waitlist() {
        delegate?.meetingDidWaitlist()
    }

    public fun emitParticipants(participants: List<StudioParticipant>) {
        this.participants.clear()
        this.participants.addAll(participants)
        delegate?.meetingDidUpdateParticipants(participants)
    }

    public fun emitWaitlist(guests: List<StudioWaitlistedGuest>) {
        waitlisted.clear()
        waitlisted.addAll(guests)
        delegate?.meetingDidUpdateWaitlist(guests.toList())
    }

    public fun emitStageRequests(requests: List<StudioStageRequest>) {
        delegate?.meetingDidUpdateStageRequests(requests)
    }

    public fun emitChat(message: StudioChatMessage) {
        delegate?.meetingDidReceiveChat(message)
    }

    public fun emitBroadcast(message: StudioBroadcastMessage) {
        delegate?.meetingDidReceiveBroadcast(message)
    }

    public fun emitDevices() {
        delegate?.meetingDidUpdateDevices()
    }

    public fun emitSelfStage(status: StudioStageStatus?) {
        delegate?.meetingSelfStageDidChange(status)
    }

    public fun kickSelf() {
        delegate?.meetingDidDisconnect(MeetingDisconnectReason.KICKED)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        cameraOn = enabled
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        micOn = enabled
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun switchCamera() = Unit

    override fun enableScreenShare() {
        screenOn = true
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun disableScreenShare() {
        screenOn = false
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun acceptWaitingRoom(id: String) {
        acceptedWait += id
        waitlisted.removeAll { it.id == id || it.userId == id }
        delegate?.meetingDidUpdateWaitlist(waitlisted.toList())
    }

    override fun rejectWaitingRoom(id: String) {
        rejectedWait += id
        waitlisted.removeAll { it.id == id || it.userId == id }
        delegate?.meetingDidUpdateWaitlist(waitlisted.toList())
    }

    override fun acceptAllWaitingRoom(ids: List<String>) {
        ids.forEach { acceptWaitingRoom(it) }
    }

    override fun kick(id: String) {
        kicked += id
        participants.removeAll { it.id == id || it.userId == id }
        delegate?.meetingDidUpdateParticipants(participants.toList())
    }

    override fun pin(id: String) {
        pinned.remove(id)
        pinned += id
        bumpPinned(id, true)
    }

    override fun unpin(id: String) {
        pinned.remove(id)
        bumpPinned(id, false)
    }

    override fun sendChat(text: String) {
        chatSent += text
        delegate?.meetingDidReceiveChat(StudioChatMessage(id = "local-${chatSent.size}", displayName = "You", text = text, isSelf = true))
    }

    override fun requestStage() {
        stageRequested = true
        delegate?.meetingSelfStageDidChange(StudioStageStatus.REQUESTED)
    }

    override fun cancelStageRequest() {
        stageRequested = false
        delegate?.meetingSelfStageDidChange(StudioStageStatus.OFF_STAGE)
    }

    override fun joinStage() {
        stageJoined = true
        delegate?.meetingSelfStageDidChange(StudioStageStatus.ON_STAGE)
    }

    override fun leaveStage() {
        stageJoined = false
        delegate?.meetingSelfStageDidChange(StudioStageStatus.OFF_STAGE)
    }

    override fun grantStage(id: String) {
        grantedStage += id
        rewriteStage(id, StudioStageStatus.ON_STAGE)
    }

    override fun denyStage(id: String) {
        deniedStage += id
    }

    override fun takeOffStage(id: String) {
        takenOffStage += id
        rewriteStage(id, StudioStageStatus.OFF_STAGE)
    }

    override fun muteRemoteAudio(id: String) = Unit

    override fun disableRemoteVideo(id: String) = Unit

    override fun sendBroadcast(type: String, payload: Map<String, String>) {
        broadcasts += type to payload
    }

    override fun audioDevices(): List<StudioMediaDevice> = audioDeviceList

    override fun videoDevices(): List<StudioMediaDevice> = videoDeviceList

    override fun selectedAudioDeviceId(): String? = selectedAudio

    override fun selectedVideoDeviceId(): String? = selectedVideo

    override fun setAudioDevice(id: String) {
        selectedAudio = id
    }

    override fun setVideoDevice(id: String) {
        selectedVideo = id
    }

    override fun videoView(participantId: String, screenShare: Boolean): View? = null

    private fun bumpPinned(id: String, value: Boolean) {
        val next =
            participants.map {
                if (it.id == id || it.userId == id) it.copy(pinned = value) else it.copy(pinned = if (value) false else it.pinned)
            }
        participants.clear()
        participants.addAll(next)
        delegate?.meetingDidUpdateParticipants(participants.toList())
    }

    private fun rewriteStage(id: String, status: StudioStageStatus) {
        val next =
            participants.map {
                if (it.id == id || it.userId == id || it.stageId == id) it.copy(stageStatus = status) else it
            }
        participants.clear()
        participants.addAll(next)
        delegate?.meetingDidUpdateParticipants(participants.toList())
    }
}
