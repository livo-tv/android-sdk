package tv.livo.sdk.studio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StudioStageLayoutTest {
    private fun person(
        id: String,
        name: String = id,
        video: Boolean = true,
        screen: Boolean = false,
        pinned: Boolean = false,
        self: Boolean = false,
        stage: StudioStageStatus = StudioStageStatus.ON_STAGE,
    ) = StudioParticipant(
        id = id,
        userId = id,
        name = name,
        isSelf = self,
        videoEnabled = video,
        screenShareOn = screen,
        pinned = pinned,
        stageStatus = stage,
    )

    @Test
    fun pinWinsSpotlightPreferringScreen() {
        val tiles =
            StudioStageLayout.expand(
                listOf(
                    person("a", screen = true, pinned = true),
                    person("b"),
                ),
            )
        val layout = StudioStageLayout.arrange(tiles)
        assertEquals("a:screen", layout.spotlight.single().tileId)
        assertTrue(layout.strip.any { it.participant.id == "a" && it.kind == StudioTileKind.CAMERA })
    }

    @Test
    fun screenSharesSpotlightWhenNobodyPinned() {
        val tiles = StudioStageLayout.expand(listOf(person("a"), person("b", screen = true)))
        val layout = StudioStageLayout.arrange(tiles)
        assertEquals("b:screen", layout.spotlight.single().tileId)
        assertTrue(layout.strip.any { it.participant.id == "a" })
    }

    @Test
    fun cameraOffStillGetsIdleTile() {
        val tiles = StudioStageLayout.expand(listOf(person("a", video = false)))
        assertEquals(StudioTileKind.IDLE, tiles.single().kind)
    }

    @Test
    fun pipFloatsSelfWhenOthersOnStage() {
        val tiles = StudioStageLayout.expand(listOf(person("host", self = true), person("guest")))
        val pip = StudioStageLayout.extractPip(tiles)
        assertEquals("host", pip?.participant?.id)
        val layout = StudioStageLayout.arrange(tiles)
        assertEquals("host", layout.pip?.participant?.id)
        assertTrue(layout.strip.none { it.tileId == pip?.tileId } && layout.spotlight.none { it.tileId == pip?.tileId })
    }

    @Test
    fun pinnedSelfHasNoPip() {
        val tiles = StudioStageLayout.expand(listOf(person("host", self = true, pinned = true), person("guest")))
        assertNull(StudioStageLayout.extractPip(tiles))
    }
}
