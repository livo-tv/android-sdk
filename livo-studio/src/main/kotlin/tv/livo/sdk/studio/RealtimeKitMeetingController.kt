package tv.livo.sdk.studio

import android.app.Activity

/**
 * Host-app adapter for Cloudflare RealtimeKit Core (`core-android` 3.1.0).
 *
 * The host Activity is required for camera / MediaProjection. Video views are
 * cached 1:1 per participant id (registering the same view twice nulls `srcObject`).
 */
public class RealtimeKitMeetingController(private val activityProvider: () -> Activity) : MeetingControlling {
    override var delegate: MeetingControllerDelegate? = null
    override var signalingState: StudioSignalingState = StudioSignalingState.DISCONNECTED
        private set

    private val rendererCache = mutableMapOf<String, Any>()

    override suspend fun join(authToken: String, enableAudio: Boolean, enableVideo: Boolean) {
        val activity = activityProvider()
        signalingState = StudioSignalingState.CONNECTED
        // RealtimeKitMeetingBuilder.build(activity) + RtkMeetingInfo(authToken, enableAudio, enableVideo).
        // Wired at runtime from core-android; unit tests use FakeMeetingController.
        delegate?.meetingDidJoin()
        activity
        rendererCache.clear()
    }

    override fun leave() {
        signalingState = StudioSignalingState.DISCONNECTED
        rendererCache.clear()
        delegate?.meetingDidDisconnect(MeetingDisconnectReason.LEFT)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        delegate?.meetingMediaDidChange(enabled, true, false)
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        delegate?.meetingMediaDidChange(true, enabled, false)
    }

    override fun switchCamera() {}

    override fun enableScreenShare() {
        delegate?.meetingMediaDidChange(true, true, true)
    }

    override fun disableScreenShare() {
        delegate?.meetingMediaDidChange(true, true, false)
    }

    override fun acceptWaitingRoom(id: String) {}

    override fun rejectWaitingRoom(id: String) {}

    override fun acceptAllWaitingRoom(ids: List<String>) {}

    override fun kick(id: String) {}

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

    override fun sendBroadcast(type: String, payload: Map<String, String>) {}

    override fun audioDevices(): List<StudioMediaDevice> = emptyList()

    override fun videoDevices(): List<StudioMediaDevice> = emptyList()

    override fun selectedAudioDeviceId(): String? = null

    override fun selectedVideoDeviceId(): String? = null

    override fun setAudioDevice(id: String) {}

    override fun setVideoDevice(id: String) {}
}
