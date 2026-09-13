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
    private fun session(live: Boolean = false, role: StudioRole = StudioRole.MODERATOR) = StudioSession(
        authToken = "auth",
        meetingId = "m1",
        role = role,
        studioControlToken = "sc_test",
        guestUrl = "https://app.livo.tv/studio/join/tok",
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

    @Test
    fun kickedPhase() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        fake.kickSelf()
        assertEquals(StudioPhase.KICKED, model.phase.value)
        model.stop()
    }

    @Test
    fun canPublishOnlyInPreview() = runTest {
        val fake = FakeMeetingController()
        val preview = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        preview.start()
        advanceUntilIdle()
        assertTrue(preview.canPublish)
        preview.stop()
        val live = StudioRoomModel(session(live = true), LivoHosts.production, fake, this, pollNetwork = false)
        live.start()
        advanceUntilIdle()
        assertFalse(live.canPublish)
        assertTrue(live.canStop)
        live.stop()
    }

    @Test
    fun guestLeaveDoesNotStopBroadcast() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(role = StudioRole.GUEST), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        model.leave()
        assertEquals(StudioPhase.LEFT, model.phase.value)
        assertFalse(fake.joined)
    }

    @Test
    fun admitAsPanelistGrantsWhenGuestJoins() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        val guest = StudioWaitlistedGuest(id = "p1", name = "Guest", userId = "u-guest")
        fake.emitWaitlist(listOf(guest))
        assertEquals(1, model.peopleBadge)
        model.admit(guest, AdmitAs.PANELIST)
        assertTrue("p1" in fake.acceptedWait)
        fake.emitParticipants(
            listOf(
                StudioParticipant(id = "p1", userId = "u-guest", name = "Guest", stageStatus = StudioStageStatus.OFF_STAGE),
            ),
        )
        assertTrue(fake.grantedStage.contains("u-guest") || fake.grantedStage.contains("p1"))
        model.stop()
    }

    @Test
    fun denyClearsWaitlist() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        val guest = StudioWaitlistedGuest(id = "p1", name = "Guest")
        fake.emitWaitlist(listOf(guest))
        model.deny(guest)
        assertEquals(emptyList<StudioWaitlistedGuest>(), model.waitlist.value)
        assertEquals("p1", fake.rejectedWait.single())
        model.stop()
    }

    @Test
    fun admitAllAcceptsEveryWaitlistedId() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        fake.emitWaitlist(
            listOf(
                StudioWaitlistedGuest(id = "a", name = "A"),
                StudioWaitlistedGuest(id = "b", name = "B"),
            ),
        )
        model.admitAll(AdmitAs.AUDIENCE)
        assertEquals(listOf("a", "b"), fake.acceptedWait)
        model.stop()
    }

    @Test
    fun hostMediaToastOnlyForSelf() = runTest {
        val fake = FakeMeetingController()
        val model = StudioRoomModel(session(), LivoHosts.production, fake, this, pollNetwork = false)
        model.start()
        advanceUntilIdle()
        fake.emitParticipants(listOf(StudioParticipant(id = "self", userId = "me", name = "Host", isSelf = true)))
        fake.emitBroadcast(StudioBroadcastMessage(type = "host-media", kind = "audio", userId = "other"))
        assertEquals(null, model.toast.value)
        fake.emitBroadcast(StudioBroadcastMessage(type = "host-media", kind = "audio", userId = "me"))
        assertEquals("The host muted you", model.toast.value?.message)
        model.stop()
    }
}
