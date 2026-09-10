package tv.livo.sdk.studio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tv.livo.sdk.LivoHosts
import tv.livo.sdk.models.StudioRole
import tv.livo.sdk.models.StudioSession
import tv.livo.sdk.models.StudioStreamSummary

@OptIn(ExperimentalCoroutinesApi::class)
class StudioRoomModelTest {
    private fun session(live: Boolean = false) = StudioSession(
        authToken = "auth",
        meetingId = "m1",
        role = StudioRole.MODERATOR,
        studioControlToken = "sc_test",
        stream =
        StudioStreamSummary(
            id = "s1",
            title = "Town hall",
            status = if (live) "public" else "preview",
        ),
    )

    @Test
    fun joinMovesToInRoom() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        assertEquals(StudioPhase.IN_ROOM, model.phase.value)
        assertTrue(fake.joined)
        model.stop()
    }

    @Test
    fun lastOnAirGuardWhilePublic() = runTest {
        val fake = FakeMeetingController()
        val model =
            StudioRoomModel(session(live = true), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        fake.emitParticipants(
            listOf(
                StudioParticipant(
                    id = "a",
                    userId = "u1",
                    name = "Ada",
                    stageStatus = StudioStageStatus.ON_STAGE,
                ),
            ),
        )
        assertFalse(model.canTakeOffAir("a"))
        model.stop()
    }

    @Test
    fun hostMediaBroadcastUsesMapPayload() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        model.muteRemote("u2")
        assertEquals("host-media", fake.broadcasts.last().first)
        assertEquals("audio", fake.broadcasts.last().second["kind"])
        model.stop()
    }

    @Test
    fun rejectedPhase() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        fake.rejectJoin()
        assertEquals(StudioPhase.REJECTED, model.phase.value)
        model.stop()
    }
}
