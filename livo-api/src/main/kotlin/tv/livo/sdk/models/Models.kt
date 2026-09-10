package tv.livo.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import tv.livo.sdk.Posters

@Serializable
public enum class StreamStatus {
    @SerialName("scheduled")
    SCHEDULED,

    @SerialName("idle")
    IDLE,

    @SerialName("spawning")
    SPAWNING,

    @SerialName("ingest_ready")
    INGEST_READY,

    @SerialName("preview")
    PREVIEW,

    @SerialName("public")
    PUBLIC,

    @SerialName("ended")
    ENDED,

    @SerialName("error")
    ERROR,
    ;

    public val pollIntervalMs: Long
        get() =
            when (this) {
                SPAWNING, INGEST_READY, IDLE -> 2_000
                SCHEDULED, PREVIEW, PUBLIC -> 5_000
                ENDED, ERROR -> 0
            }

    public val canStart: Boolean get() = this == SCHEDULED
    public val canPublish: Boolean get() = this == PREVIEW
    public val canStop: Boolean
        get() = this == PREVIEW || this == PUBLIC || this == SPAWNING || this == INGEST_READY || this == IDLE
    public val canTrash: Boolean get() = this == SCHEDULED || this == ENDED || this == ERROR
    public val showsIngest: Boolean
        get() = this == INGEST_READY || this == PREVIEW || this == PUBLIC
    public val isPlayable: Boolean get() = this == PREVIEW || this == PUBLIC || this == ENDED
    public val isLive: Boolean get() = this == PUBLIC
    public val isTerminal: Boolean get() = this == ENDED || this == ERROR
}

@Serializable
public enum class StreamSource {
    @SerialName("rtmp")
    RTMP,

    @SerialName("studio")
    STUDIO,
}

@Serializable
public enum class MediaKind {
    @SerialName("vod")
    VOD,

    @SerialName("stream")
    STREAM,

    @SerialName("webinar")
    WEBINAR,
}

@Serializable
public enum class StreamMode {
    @SerialName("instant")
    INSTANT,

    @SerialName("scheduled")
    SCHEDULED,
}

@Serializable
public enum class VodStatus {
    @SerialName("uploading")
    UPLOADING,

    @SerialName("uploaded")
    UPLOADED,

    @SerialName("transcoding")
    TRANSCODING,

    @SerialName("ready")
    READY,

    @SerialName("error")
    ERROR,
    ;

    public val canTrash: Boolean get() = this != UPLOADING
    public val canTranscode: Boolean get() = this == UPLOADED || this == READY || this == ERROR
}

@Serializable
public enum class CurtainKind {
    @SerialName("image")
    IMAGE,

    @SerialName("video")
    VIDEO,

    @SerialName("shader")
    SHADER,
}

@Serializable
public enum class OrganizationRole {
    @SerialName("owner")
    OWNER,

    @SerialName("manager")
    MANAGER,

    @SerialName("member")
    MEMBER,
    ;

    public val canWriteContent: Boolean get() = true
    public val canAdminister: Boolean get() = this == OWNER || this == MANAGER
    public val isOwner: Boolean get() = this == OWNER
}

@Serializable
public enum class ApiKeyScope {
    @SerialName("read")
    READ,

    @SerialName("streams:write")
    STREAMS_WRITE,

    @SerialName("vod:write")
    VOD_WRITE,

    @SerialName("organization:write")
    ORGANIZATION_WRITE,
}

@Serializable
public enum class StudioRole {
    @SerialName("moderator")
    MODERATOR,

    @SerialName("guest")
    GUEST,
}

@Serializable
public enum class StudioLivestreamEgress {
    @SerialName("live")
    LIVE,

    @SerialName("starting")
    STARTING,
}

@Serializable
public enum class CommunityType {
    @SerialName("comment")
    COMMENT,

    @SerialName("question")
    QUESTION,
}

@Serializable
public enum class MetricPeriod {
    @SerialName("day")
    DAY,

    @SerialName("week")
    WEEK,

    @SerialName("month")
    MONTH,

    @SerialName("year")
    YEAR,
}

@Serializable
public data class StreamCommunityFlags(
    val commentsEnabled: Boolean = true,
    val commentsAfterEnd: Boolean = false,
    val commentsOpen: Boolean? = null,
    val qaEnabled: Boolean = true,
    val qaOpen: Boolean? = null,
)

@Serializable
public data class VodCommunityFlags(val commentsEnabled: Boolean = true, val commentsOpen: Boolean? = null)

@Serializable
public data class IngestInfo(
    val host: String? = null,
    val port: Int? = null,
    val streamKey: String? = null,
    val url: String? = null,
    val whipUrl: String? = null,
    val rtmpUrl: String? = null,
)

@Serializable
public data class LiveStream(
    val id: String,
    val organizationId: String? = null,
    val environmentSlug: String? = null,
    val title: String,
    val status: StreamStatus,
    val streamKey: String? = null,
    val playbackUrl: String? = null,
    val ingestHost: String? = null,
    val ingestPort: Int? = null,
    val whipUrl: String? = null,
    val mode: StreamMode = StreamMode.INSTANT,
    val source: StreamSource? = null,
    val kind: MediaKind? = null,
    val startsAt: Long? = null,
    val durationSeconds: Int? = null,
    val curtainId: String? = null,
    val startedAt: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val endedAt: Long? = null,
    val encoderLostAt: Long? = null,
    val ingestTimeoutAt: Long? = null,
    val errorMessage: String? = null,
    val posterUrl: String? = null,
    val studioGuestToken: String? = null,
    val deletedAt: Long? = null,
    val captionsEnabled: Boolean = false,
    val community: StreamCommunityFlags? = null,
) {
    public val effectiveSource: StreamSource
        get() = source ?: if (kind == MediaKind.WEBINAR) StreamSource.STUDIO else StreamSource.RTMP

    public val effectiveKind: MediaKind
        get() =
            kind ?: when (effectiveSource) {
                StreamSource.STUDIO -> MediaKind.WEBINAR
                StreamSource.RTMP -> MediaKind.STREAM
            }

    public val listPosterUrl: String? get() = Posters.posterUrlOrSprite(posterUrl, playbackUrl)

    public val playerPosterUrl: String?
        get() = posterUrl?.takeUnless { Posters.isSpriteUrl(it) }
}

@Serializable
public data class Vod(
    val id: String,
    val organizationId: String? = null,
    val environmentSlug: String? = null,
    val title: String,
    val status: VodStatus,
    val kind: MediaKind = MediaKind.VOD,
    val playbackUrl: String? = null,
    val posterUrl: String? = null,
    val durationSeconds: Int? = null,
    val sha256: String? = null,
    val byteSize: Long? = null,
    val filename: String? = null,
    val releaseAt: Long? = null,
    val curtainId: String? = null,
    val progressStage: String? = null,
    val progressPercent: Double? = null,
    val progressCurrent: Long? = null,
    val progressTotal: Long? = null,
    val captionsEnabled: Boolean = false,
    val community: VodCommunityFlags? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val deletedAt: Long? = null,
) {
    public val listPosterUrl: String? get() = Posters.posterUrlOrSprite(posterUrl, playbackUrl)
}

@Serializable
public data class MediaItem(
    val kind: MediaKind,
    val id: String? = null,
    val stream: LiveStream? = null,
    val vod: Vod? = null,
    val title: String? = null,
    val status: String? = null,
    val createdAt: Long? = null,
    val posterUrl: String? = null,
    val playbackUrl: String? = null,
) {
    public val resolvedId: String
        get() = id ?: stream?.id ?: vod?.id ?: error("media item missing id")

    public val resolvedTitle: String
        get() = title ?: stream?.title ?: vod?.title ?: ""
}

@Serializable
public data class Curtain(
    val id: String,
    val ownerType: String? = null,
    val organizationId: String? = null,
    val kind: CurtainKind = CurtainKind.IMAGE,
    val title: String? = null,
    val name: String? = null,
    val publicUrl: String? = null,
    val posterUrl: String? = null,
    val isDefault: Boolean = false,
    val hideBranding: Boolean = false,
    val hideStatus: Boolean = false,
    val config: JsonElement? = null,
    val envBasePath: String? = null,
    val createdAt: Long? = null,
    val deletedAt: Long? = null,
) {
    public val displayTitle: String get() = title ?: name ?: id
    public val displayUrl: String? get() = publicUrl ?: posterUrl
    public val isPlatform: Boolean get() = ownerType == "platform" || ownerType == "catalog"
}

@Serializable
public data class TrashItem(
    val kind: String,
    val id: String,
    val title: String? = null,
    val status: String? = null,
    val deletedAt: Long? = null,
    val trashedAt: Long? = null,
    val expiresAt: Long? = null,
)

@Serializable
public data class ApiKey(
    val id: String,
    val name: String,
    val scopes: List<String> = emptyList(),
    val lastUsedAt: Long? = null,
    val createdAt: Long? = null,
    val revokedAt: Long? = null,
)

@Serializable
public data class CreatedApiKey(val id: String? = null, val name: String? = null, val key: String? = null, val secret: String? = null) {
    public val plaintext: String? get() = key ?: secret
}

@Serializable
public data class OrganizationSettings(val defaultCurtainId: String? = null, val webhookUrl: String? = null, val managedBy: ManagedBy? = null)

@Serializable
public data class ManagedBy(val partnerId: String? = null, val partnerName: String? = null, val externalRef: String? = null, val provisionedAt: Long? = null)

@Serializable
public data class Usage(val streamsLive: Int = 0, val streamsTotal: Int = 0, val vodsTotal: Int = 0, val minutesThisMonth: Int = 0)

@Serializable
public data class SessionUser(val id: String, val email: String? = null, val name: String? = null, val role: String? = null, val locale: String? = null, val image: String? = null)

@Serializable
public data class OrganizationSummary(val id: String, val name: String, val role: OrganizationRole? = null, val status: String? = null, val image: String? = null)

@Serializable
public data class EnvironmentSummary(val id: String, val organizationId: String? = null, val slug: String, val name: String, val accessPolicy: String? = null) {
    public val isProduction: Boolean get() = slug == "production"
}

@Serializable
public data class LegalDocument(val version: String? = null, val acceptedVersion: String? = null)

@Serializable
public data class LegalStatus(val terms: LegalDocument? = null, val privacy: LegalDocument? = null, val needsAcceptance: Boolean = false)

@Serializable
public data class SessionStatus(
    val user: SessionUser,
    val profileConfirmed: Boolean = false,
    val organizations: List<OrganizationSummary> = emptyList(),
    val activeOrganizationId: String? = null,
    val environments: List<EnvironmentSummary> = emptyList(),
    val activeEnvironmentId: String? = null,
    val impersonatedBy: String? = null,
    val legal: LegalStatus? = null,
    val session: JsonElement? = null,
)

@Serializable
public data class JwtClaims(
    val sub: String,
    val email: String? = null,
    val name: String? = null,
    val role: String? = null,
    val organizationId: String? = null,
    val organizationRole: OrganizationRole? = null,
    val impersonatedBy: String? = null,
    val environmentId: String? = null,
    val environmentSlug: String? = null,
    val exp: Long? = null,
)

@Serializable
public data class LicensePreview(val valid: Boolean? = null, val plan: String? = null, val name: String? = null, val seats: Int? = null, val expiresAt: Long? = null) {
    public val isUsable: Boolean get() = valid != false
}

@Serializable
public data class OrganizationMember(
    val id: String,
    val userId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: OrganizationRole? = null,
    val image: String? = null,
) {
    public val memberUserId: String get() = userId ?: id
}

@Serializable
public data class OrganizationInvitation(val id: String, val email: String, val role: OrganizationRole? = null, val status: String? = null, val expiresAt: Long? = null)

@Serializable
public data class StudioStreamSummary(val id: String, val title: String, val status: String, val encoderLostAt: Long? = null, val captionsEnabled: Boolean? = null) {
    public val isPreview: Boolean get() = status == "preview"
    public val isLive: Boolean get() = status == "public"
    public val isEnded: Boolean get() = status == "ended" || status == "error"
}

@Serializable
public data class StudioSession(
    val authToken: String,
    val meetingId: String,
    val role: StudioRole,
    val guestUrl: String? = null,
    val studioControlToken: String? = null,
    val expiresAt: Long? = null,
    val stream: StudioStreamSummary,
)

@Serializable
public data class StudioJoinStatus(val ready: Boolean, val streamStatus: String? = null)

public sealed class StudioJoinStatusResult {
    public data class Status(val value: StudioJoinStatus) : StudioJoinStatusResult()

    public data object Ended : StudioJoinStatusResult()
}

@Serializable
public data class StudioControlState(val stream: StudioStreamSummary)

@Serializable
public data class StudioControlRefresh(val studioControlToken: String, val expiresAt: Long)

@Serializable
public data class StudioHostSession(val hostUrl: String, val guestUrl: String, val hostToken: String? = null, val guestToken: String? = null, val expiresAt: Long) {
    public val resolvedHostToken: String?
        get() = hostToken ?: hostUrl.trimEnd('/').substringAfterLast('/')

    public val resolvedGuestToken: String?
        get() = guestToken ?: guestUrl.trimEnd('/').substringAfterLast('/')
}

@Serializable
public data class CommunityAnswer(val body: String, val displayName: String? = null, val picture: String? = null, val answeredAt: Long? = null)

@Serializable
public data class CommunityItem(
    val id: String,
    val type: CommunityType,
    val parentId: String? = null,
    val body: String,
    val displayName: String? = null,
    val picture: String? = null,
    val guestId: String? = null,
    val upvoteCount: Int = 0,
    val pinnedAt: Long? = null,
    val highlightedAt: Long? = null,
    val hiddenAt: Long? = null,
    val dismissedAt: Long? = null,
    val answer: CommunityAnswer? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val replies: List<CommunityItem>? = null,
)

@Serializable
public data class CommunityIdentity(val displayName: String, val guestId: String, val picture: String? = null)

@Serializable
public data class Notification(
    val id: String,
    val userId: String? = null,
    val type: String? = null,
    val title: String,
    val message: String,
    val link: String? = null,
    val organizationId: String? = null,
    val readAt: String? = null,
    val createdAt: String? = null,
    val read: Boolean = false,
)

@Serializable
public data class NotificationsPage(val items: List<Notification> = emptyList(), val unreadCount: Int = 0, val nextCursor: String? = null)

@Serializable
public data class MetricValue(val value: Double = 0.0, val count: Long = 0)

@Serializable
public data class MetricsHighlights(
    val period: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val metrics: Map<String, MetricValue>? = null,
    val liveNow: Double? = null,
    val views: Double? = null,
    val watchHours: Double? = null,
    val storageBytes: Double? = null,
    val spendUsd: Double? = null,
    val streamsThisMonth: Double? = null,
    val vodsThisMonth: Double? = null,
) {
    public fun card(name: String, bagKey: String? = null): Double {
        val flat =
            when (name) {
                "liveNow" -> liveNow
                "views" -> views
                "watchHours" -> watchHours
                "storageBytes" -> storageBytes
                "spendUsd" -> spendUsd
                "streamsThisMonth" -> streamsThisMonth
                "vodsThisMonth" -> vodsThisMonth
                else -> null
            }
        if (flat != null) return flat
        val bag = bagKey ?: name
        return metrics?.get(bag)?.value ?: 0.0
    }
}

@Serializable
public data class MetricRollup(
    val periodStart: Long? = null,
    val organizationId: String? = null,
    val environmentSlug: String? = null,
    val metric: String,
    val value: Double = 0.0,
    val count: Long? = null,
)

@Serializable
public data class ResolvedCurtain(
    val kind: String,
    val url: String? = null,
    val hideBranding: Boolean = false,
    val hideStatus: Boolean = false,
    val config: JsonElement? = null,
    val envBasePath: String? = null,
)

@Serializable
public data class PublicOrganization(val name: String? = null, val image: String? = null)

@Serializable
public data class PublicStream(
    val id: String,
    val title: String,
    val status: String,
    val mode: StreamMode? = null,
    val startsAt: Long? = null,
    val durationSeconds: Int? = null,
    val startedAt: Long? = null,
    val playbackUrl: String? = null,
    val curtain: ResolvedCurtain? = null,
    val organization: PublicOrganization? = null,
    val viewers: Int? = null,
    val captionsUrl: String? = null,
    val captionsEnabled: Boolean? = null,
    val community: StreamCommunityFlags? = null,
)

@Serializable
public data class PublicVod(
    val id: String,
    val title: String,
    val status: String,
    val releaseAt: Long? = null,
    val playbackUrl: String? = null,
    val posterUrl: String? = null,
    val durationSeconds: Int? = null,
    val curtain: ResolvedCurtain? = null,
    val organization: PublicOrganization? = null,
    val captionsEnabled: Boolean = false,
    val community: VodCommunityFlags? = null,
)

@Serializable
public data class CaptionCue(val text: String, val receivedAt: Long? = null, val speaker: String? = null)

@Serializable
public data class PlaybackBeaconEvent(
    val type: String,
    val ts: Long,
    val streamId: String? = null,
    val vodId: String? = null,
    val currentTime: Double? = null,
    val duration: Double? = null,
    val rebufferSeconds: Double? = null,
    val quality: String? = null,
    val errorCode: String? = null,
    val surface: String = "sdk",
    val locale: String? = null,
)

@Serializable
public data class CursorPage<T>(val items: List<T> = emptyList(), val nextCursor: String? = null)

@Serializable
public data class CreateVodResponse(val vodId: String, val uploadId: String? = null, val partSize: Long = PART_SIZE_BYTES)

@Serializable
public data class UploadConflict(val error: String, val vodId: String? = null)

@Serializable
public data class UploadPartUrl(val uploadUrl: String)

@Serializable
public data class CompletedPart(val partNumber: Int, val etag: String)

@Serializable
public data class UploadPartsResponse(val parts: List<CompletedPart> = emptyList(), val partSize: Long? = null, val sha256: String? = null, val byteSize: Long? = null)

@Serializable
public data class PasskeyRecord(val id: String, val name: String? = null, val createdAt: String? = null, val deviceType: String? = null)

public const val PART_SIZE_BYTES: Long = 8L * 1024L * 1024L
