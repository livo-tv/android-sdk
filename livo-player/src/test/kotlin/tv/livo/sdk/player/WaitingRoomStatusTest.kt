package tv.livo.sdk.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WaitingRoomStatusTest {
    @Test
    fun loadingUsesLoadingNotWaiting() {
        val labels = LivoWaitingLabels(waiting = "Waiting")
        assertEquals("Loading", waitingRoomStatus(LivoPlayerPhase.LOADING, labels))
        assertEquals("Waiting", waitingRoomStatus(LivoPlayerPhase.WAITING, labels))
    }
}
