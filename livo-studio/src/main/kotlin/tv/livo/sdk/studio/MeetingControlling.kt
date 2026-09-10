package tv.livo.sdk.studio

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
) {
    public val stageId: String get() = userId.ifEmpty { id }
}

public data class StudioWaitlistedGuest(val id: String, val name: String)

public data class StudioChatMessage(val id: String, val displayName: String, val text: String, val isSelf: Boolean = false)

public data class StudioStageRequest(val id: String, val userId: String, val name: String)

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

    override suspend fun join(authToken: String, enableAudio: Boolean, enableVideo: Boolean) {
        joined = true
        signalingState = StudioSignalingState.CONNECTED
        cameraOn = enableVideo
        micOn = enableAudio
        delegate?.meetingDidJoin()
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
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
        delegate?.meetingDidUpdateParticipants(participants)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        cameraOn = enabled
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        micOn = enabled
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun switchCamera() {}

    override fun enableScreenShare() {
        screenOn = true
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun disableScreenShare() {
        screenOn = false
        delegate?.meetingMediaDidChange(cameraOn, micOn, screenOn)
    }

    override fun acceptWaitingRoom(id: String) {}

    override fun rejectWaitingRoom(id: String) {}

    override fun acceptAllWaitingRoom(ids: List<String>) {}

    override fun kick(id: String) {
        kicked += id
    }

    override fun pin(id: String) {}

    override fun unpin(id: String) {}

    override fun sendChat(text: String) {}

    override fun requestStage() {}

    override fun cancelStageRequest() {}

    override fun joinStage() {}

    override fun leaveStage() {}

    override fun grantStage(id: String) {}

    override fun denyStage(id: String) {}

    override fun takeOffStage(id: String) {}

    override fun muteRemoteAudio(id: String) {}

    override fun disableRemoteVideo(id: String) {}

    override fun sendBroadcast(type: String, payload: Map<String, String>) {
        broadcasts += type to payload
    }

    override fun audioDevices(): List<StudioMediaDevice> = emptyList()

    override fun videoDevices(): List<StudioMediaDevice> = emptyList()

    override fun selectedAudioDeviceId(): String? = null

    override fun selectedVideoDeviceId(): String? = null

    override fun setAudioDevice(id: String) {}

    override fun setVideoDevice(id: String) {}
}
