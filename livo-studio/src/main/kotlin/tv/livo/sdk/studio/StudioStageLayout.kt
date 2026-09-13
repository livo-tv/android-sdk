package tv.livo.sdk.studio

public enum class StudioTileKind {
    CAMERA,
    SCREEN,
    IDLE,
}

public data class StudioDisplayTile(val tileId: String, val kind: StudioTileKind, val participant: StudioParticipant) {
    public val isSelf: Boolean get() = participant.isSelf
    public val pinned: Boolean get() = participant.pinned
}

public data class StudioStageArrangement(val spotlight: List<StudioDisplayTile>, val strip: List<StudioDisplayTile>, val overflow: Int, val pip: StudioDisplayTile? = null)

public object StudioStageLayout {
    public const val STRIP_CAP: Int = 6
    public const val GRID_CAP: Int = 9

    public fun isOnStage(status: StudioStageStatus?): Boolean = status == null || status == StudioStageStatus.ON_STAGE

    public fun canTakeOffAir(participants: List<StudioParticipant>, participantId: String, isLive: Boolean): Boolean {
        if (!isLive) return true
        return participants.any { it.id != participantId && isOnStage(it.stageStatus) }
    }

    public fun expand(participants: List<StudioParticipant>): List<StudioDisplayTile> {
        val tiles = mutableListOf<StudioDisplayTile>()
        for (participant in participants) {
            val kind = if (participant.videoEnabled) StudioTileKind.CAMERA else StudioTileKind.IDLE
            tiles.add(StudioDisplayTile("${participant.id}:${kind.name.lowercase()}", kind, participant))
            if (participant.screenShareOn) {
                tiles.add(StudioDisplayTile("${participant.id}:screen", StudioTileKind.SCREEN, participant))
            }
        }
        return tiles
    }

    public fun arrange(tiles: List<StudioDisplayTile>, activeSpeakerId: String? = null, max: Int? = null): StudioStageArrangement {
        val pip = extractPip(tiles)
        val stageTiles = tiles.filter { it.tileId != pip?.tileId }
        val pinned = stageTiles.firstOrNull { it.pinned }
        val spotlight: List<StudioDisplayTile>
        val remaining: List<StudioDisplayTile>
        if (pinned != null) {
            val pinTiles = stageTiles.filter { it.participant.id == pinned.participant.id }
            val focus =
                pinTiles.firstOrNull { it.kind == StudioTileKind.SCREEN }
                    ?: pinTiles.firstOrNull { it.kind == StudioTileKind.CAMERA }
                    ?: pinTiles.firstOrNull()
            if (focus != null) {
                spotlight = listOf(focus)
                remaining = stageTiles.filter { it.tileId != focus.tileId }
            } else {
                spotlight = emptyList()
                remaining = stageTiles
            }
        } else {
            spotlight = stageTiles.filter { it.kind == StudioTileKind.SCREEN }
            remaining = stageTiles.filter { it.kind != StudioTileKind.SCREEN }
        }
        val cap = max ?: if (spotlight.isEmpty()) GRID_CAP else STRIP_CAP
        val ranked = remaining.sortedWith { lhs, rhs ->
            val diff = stripRank(lhs, activeSpeakerId) - stripRank(rhs, activeSpeakerId)
            if (diff != 0) diff else lhs.tileId.compareTo(rhs.tileId)
        }
        if (ranked.size <= cap) {
            return StudioStageArrangement(spotlight, ranked, 0, pip)
        }
        val kept = takeCappedStrip(ranked, cap)
        return StudioStageArrangement(spotlight, kept, ranked.size - kept.size, pip)
    }

    public fun arrangeParticipants(participants: List<StudioParticipant>, activeSpeakerId: String? = null): StudioStageArrangement {
        val onStage = participants.filter { isOnStage(it.stageStatus) || it.isSelf }
        return arrange(expand(onStage), activeSpeakerId)
    }

    internal fun extractPip(tiles: List<StudioDisplayTile>): StudioDisplayTile? {
        if (tiles.any { it.isSelf && it.pinned }) return null
        if (tiles.none { !it.isSelf }) return null
        return tiles.firstOrNull { it.isSelf && it.kind != StudioTileKind.SCREEN }
    }

    private fun stripRank(tile: StudioDisplayTile, activeSpeakerId: String?): Int = when {
        tile.pinned -> 0
        tile.kind == StudioTileKind.SCREEN -> 1
        tile.isSelf -> 2
        activeSpeakerId != null && tile.participant.id == activeSpeakerId -> 3
        tile.kind == StudioTileKind.CAMERA -> 4
        else -> 5
    }

    private fun takeCappedStrip(ranked: List<StudioDisplayTile>, cap: Int): List<StudioDisplayTile> {
        val selfTiles = ranked.filter { it.isSelf }
        val others = ranked.filter { !it.isSelf }
        val reserved = minOf(selfTiles.size, cap)
        return selfTiles + others.take(cap - reserved)
    }
}
