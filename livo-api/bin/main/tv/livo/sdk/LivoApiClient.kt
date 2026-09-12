package tv.livo.sdk

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import tv.livo.sdk.internal.LivoHttp
import tv.livo.sdk.internal.query
import tv.livo.sdk.models.ApiKey
import tv.livo.sdk.models.CaptionCue
import tv.livo.sdk.models.CommunityItem
import tv.livo.sdk.models.CommunityType
import tv.livo.sdk.models.CompletedPart
import tv.livo.sdk.models.CreateVodResponse
import tv.livo.sdk.models.CreatedApiKey
import tv.livo.sdk.models.CursorPage
import tv.livo.sdk.models.Curtain
import tv.livo.sdk.models.IngestInfo
import tv.livo.sdk.models.LiveStream
import tv.livo.sdk.models.MediaItem
import tv.livo.sdk.models.MediaKind
import tv.livo.sdk.models.OrganizationSettings
import tv.livo.sdk.models.PART_SIZE_BYTES
import tv.livo.sdk.models.PlaybackBeaconEvent
import tv.livo.sdk.models.PublicStream
import tv.livo.sdk.models.PublicVod
import tv.livo.sdk.models.StreamMode
import tv.livo.sdk.models.StreamSource
import tv.livo.sdk.models.StudioControlRefresh
import tv.livo.sdk.models.StudioControlState
import tv.livo.sdk.models.StudioHostSession
import tv.livo.sdk.models.StudioJoinStatusResult
import tv.livo.sdk.models.StudioLivestreamEgress
import tv.livo.sdk.models.StudioSession
import tv.livo.sdk.models.TrashItem
import tv.livo.sdk.models.UploadConflict
import tv.livo.sdk.models.UploadPartUrl
import tv.livo.sdk.models.UploadPartsResponse
import tv.livo.sdk.models.Usage
import tv.livo.sdk.models.Vod

public class LivoApiClient(public val hosts: LivoHosts, credentials: LivoCredentials, engine: HttpClientEngine? = null) {
    private val http = LivoHttp(engine, credentials)
    public val streams: StreamsClient = StreamsClient()
    public val vods: VodsClient = VodsClient()
    public val media: MediaClient = MediaClient()
    public val curtains: CurtainsClient = CurtainsClient()
    public val trash: TrashClient = TrashClient()
    public val keys: KeysClient = KeysClient()
    public val organization: OrganizationClient = OrganizationClient()
    public val public: PublicClient = PublicClient()
    public val studio: StudioJoinClient = StudioJoinClient()

    public fun close() {
        http.close()
    }

    public inner class StreamsClient {
        public suspend fun list(q: String? = null, limit: Int = 20, cursor: String? = null): CursorPage<LiveStream> {
            val url = query("${hosts.api}/streams", mapOf("q" to q, "limit" to "$limit", "cursor" to cursor))
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            val items = decodeList<LiveStream>(obj, "streams")
            return CursorPage(items, obj["nextCursor"]?.let { livoJson.decodeFromJsonElement(it) })
        }

        public suspend fun get(id: String): StreamDetail {
            val obj = http.json<JsonObject>(HttpMethod.Get, "${hosts.api}/streams/$id")
            val stream =
                obj["stream"]?.let { livoJson.decodeFromJsonElement<LiveStream>(it) }
                    ?: livoJson.decodeFromJsonElement<LiveStream>(obj)
            val playback =
                obj["playbackUrl"]?.let { livoJson.decodeFromJsonElement<String>(it) }
                    ?: stream.playbackUrl
            val ingest = obj["ingest"]?.let { livoJson.decodeFromJsonElement<IngestInfo>(it) }
            return StreamDetail(stream, playback, ingest)
        }

        public suspend fun create(request: CreateStreamRequest): CreateStreamResponse {
            val body = request.agreeing()
            return http.json(HttpMethod.Post, "${hosts.api}/streams", body)
        }

        public suspend fun patch(id: String, body: JsonObject): LiveStream {
            val obj = http.json<JsonObject>(HttpMethod.Patch, "${hosts.api}/streams/$id", body)
            return obj["stream"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun start(id: String): LiveStream = action(id, "start")

        public suspend fun publish(id: String): LiveStream = action(id, "publish")

        public suspend fun stop(id: String): LiveStream = action(id, "stop")

        public suspend fun trash(id: String) {
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/streams/$id/trash", emptyMap<String, String>()))
        }

        public suspend fun openStudioSession(id: String): StudioSession = http.json(HttpMethod.Post, "${hosts.api}/streams/$id/studio/session", emptyMap<String, String>())

        public suspend fun startStudioLivestream(id: String): StudioLivestreamResult = http.json(
            HttpMethod.Post,
            "${hosts.api}/streams/$id/studio/livestream",
            emptyMap<String, String>(),
        )

        public suspend fun mintHostSession(id: String, displayName: String, picture: String? = null): StudioHostSession {
            val body = buildJsonObject {
                put("displayName", displayName)
                if (picture != null) put("picture", picture)
            }
            return http.json(HttpMethod.Post, "${hosts.api}/streams/$id/studio/host-session", body)
        }

        public fun community(id: String): CommunityClient = CommunityClient("streams", id)

        private suspend fun action(id: String, name: String): LiveStream {
            val obj =
                http.json<JsonObject>(
                    HttpMethod.Post,
                    "${hosts.api}/streams/$id/$name",
                    emptyMap<String, String>(),
                )
            return obj["stream"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }
    }

    public inner class VodsClient {
        public suspend fun list(q: String? = null, limit: Int = 20, cursor: String? = null): CursorPage<Vod> {
            val url = query("${hosts.api}/vods", mapOf("q" to q, "limit" to "$limit", "cursor" to cursor))
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return CursorPage(decodeList(obj, "vods"), cursorOf(obj))
        }

        public suspend fun get(id: String): Vod {
            val obj = http.json<JsonObject>(HttpMethod.Get, "${hosts.api}/vods/$id")
            return obj["vod"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun create(request: CreateVodRequest): CreateVodResult {
            val response =
                http.request(HttpMethod.Post, "${hosts.api}/vods", request)
            if (response.status.value == 409) {
                val text = runCatching { response.bodyAsText() }.getOrDefault("")
                val conflict =
                    runCatching { livoJson.decodeFromString<UploadConflict>(text) }.getOrNull()
                        ?: UploadConflict(LivoApiException.DUPLICATE_SOURCE, null)
                return CreateVodResult.Conflict(conflict)
            }
            http.ensureOk(response)
            return CreateVodResult.Created(response.body())
        }

        public suspend fun patch(id: String, body: JsonObject): Vod {
            val obj = http.json<JsonObject>(HttpMethod.Patch, "${hosts.api}/vods/$id", body)
            return obj["vod"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun uploadParts(id: String): UploadPartsResponse = http.json(HttpMethod.Get, "${hosts.api}/vods/$id/upload-parts")

        public suspend fun presignPart(id: String, partNumber: Int): UploadPartUrl {
            val body = buildJsonObject { put("partNumber", JsonPrimitive(partNumber)) }
            return http.json(HttpMethod.Post, "${hosts.api}/vods/$id/upload-part", body)
        }

        public suspend fun completeUpload(id: String, parts: List<CompletedPart>) {
            val body = buildJsonObject {
                put(
                    "parts",
                    livoJson.parseToJsonElement(livoJson.encodeToString(parts.sortedBy { it.partNumber })),
                )
            }
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/vods/$id/complete-upload", body))
        }

        public suspend fun abortUpload(id: String) {
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/vods/$id/abort-upload", emptyMap<String, String>()))
        }

        public suspend fun transcode(id: String) {
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/vods/$id/transcode", emptyMap<String, String>()))
        }

        public suspend fun captions(id: String) {
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/vods/$id/captions", emptyMap<String, String>()))
        }

        public suspend fun trash(id: String) {
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/vods/$id/trash", emptyMap<String, String>()))
        }

        public fun community(id: String): CommunityClient = CommunityClient("vods", id)
    }

    public inner class MediaClient {
        public suspend fun list(q: String? = null, kind: MediaKind? = null, limit: Int = 20, cursor: String? = null): CursorPage<MediaItem> {
            val url =
                query(
                    "${hosts.api}/media",
                    mapOf(
                        "q" to q,
                        "kind" to kindWire(kind),
                        "limit" to "$limit",
                        "cursor" to cursor,
                    ),
                )
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            val items = obj["items"] ?: error("media list missing items")
            return CursorPage(livoJson.decodeFromJsonElement(items), cursorOf(obj))
        }
    }

    public inner class CurtainsClient {
        public suspend fun list(q: String? = null, limit: Int = 20, cursor: String? = null): CursorPage<Curtain> {
            val url = query("${hosts.api}/curtains", mapOf("q" to q, "limit" to "$limit", "cursor" to cursor))
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return CursorPage(decodeList(obj, "curtains"), cursorOf(obj))
        }

        public suspend fun create(body: JsonObject): CurtainCreateResponse = http.json(HttpMethod.Post, "${hosts.api}/curtains", body)

        public suspend fun patch(id: String, body: JsonObject): Curtain {
            val obj = http.json<JsonObject>(HttpMethod.Patch, "${hosts.api}/curtains/$id", body)
            return obj["curtain"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun presignPart(id: String, partNumber: Int): UploadPartUrl {
            val body = buildJsonObject { put("partNumber", JsonPrimitive(partNumber)) }
            return http.json(HttpMethod.Post, "${hosts.api}/curtains/$id/upload-part", body)
        }

        public suspend fun complete(id: String, parts: List<CompletedPart>) {
            val body = buildJsonObject {
                put("parts", livoJson.parseToJsonElement(livoJson.encodeToString(parts.sortedBy { it.partNumber })))
            }
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/curtains/$id/complete", body))
        }

        public suspend fun abortUpload(id: String) {
            http.ensureOk(
                http.request(HttpMethod.Post, "${hosts.api}/curtains/$id/abort-upload", emptyMap<String, String>()),
            )
        }

        public suspend fun delete(id: String) {
            http.ensureOk(http.request(HttpMethod.Delete, "${hosts.api}/curtains/$id"))
        }
    }

    public inner class TrashClient {
        public suspend fun list(q: String? = null, kind: String? = null, limit: Int = 20, cursor: String? = null): CursorPage<TrashItem> {
            val url =
                query("${hosts.api}/trash", mapOf("q" to q, "kind" to kind, "limit" to "$limit", "cursor" to cursor))
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return CursorPage(decodeList(obj, "items"), cursorOf(obj))
        }

        public suspend fun restore(kind: String, id: String) {
            http.ensureOk(
                http.request(HttpMethod.Post, "${hosts.api}/trash/$kind/$id/restore", emptyMap<String, String>()),
            )
        }

        public suspend fun deleteForever(kind: String, id: String) {
            http.ensureOk(http.request(HttpMethod.Delete, "${hosts.api}/trash/$kind/$id"))
        }

        public suspend fun empty() {
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/trash/empty", emptyMap<String, String>()))
        }
    }

    public inner class KeysClient {
        public suspend fun list(q: String? = null, limit: Int = 20, cursor: String? = null): CursorPage<ApiKey> {
            val url = query("${hosts.api}/keys", mapOf("q" to q, "limit" to "$limit", "cursor" to cursor))
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return CursorPage(decodeList(obj, "keys"), cursorOf(obj))
        }

        public suspend fun create(name: String, scopes: List<String>): CreatedApiKey {
            val body = buildJsonObject {
                put("name", name)
                put("scopes", livoJson.parseToJsonElement(livoJson.encodeToString(scopes)))
            }
            return http.json(HttpMethod.Post, "${hosts.api}/keys", body)
        }

        public suspend fun revoke(id: String) {
            http.ensureOk(http.request(HttpMethod.Delete, "${hosts.api}/keys/$id"))
        }
    }

    public inner class OrganizationClient {
        public suspend fun settings(): OrganizationSettings = http.json(HttpMethod.Get, "${hosts.api}/organization/settings")

        public suspend fun updateSettings(defaultCurtainId: String? = UNSET, webhookUrl: String? = UNSET, webhookSecret: String? = UNSET): OrganizationSettings {
            val body = buildJsonObject {
                if (defaultCurtainId !== UNSET) {
                    if (defaultCurtainId == null) put("defaultCurtainId", JsonNull) else put("defaultCurtainId", defaultCurtainId)
                }
                if (webhookUrl !== UNSET) {
                    if (webhookUrl == null) put("webhookUrl", JsonNull) else put("webhookUrl", webhookUrl)
                }
                if (webhookSecret !== UNSET && webhookSecret != null) put("webhookSecret", webhookSecret)
            }
            return http.json(HttpMethod.Put, "${hosts.api}/organization/settings", body)
        }

        public suspend fun delete() {
            http.ensureOk(http.request(HttpMethod.Delete, "${hosts.api}/organization"))
        }
    }

    public suspend fun usage(): Usage = http.json(HttpMethod.Get, "${hosts.api}/usage")

    public inner class StudioJoinClient {
        public suspend fun joinGuest(token: String): StudioSession = http.json(HttpMethod.Post, "${hosts.api}/studio/join/$token", emptyMap<String, String>())
    }

    public inner class PublicClient {
        public suspend fun stream(id: String): PublicStream = http.json(HttpMethod.Get, "${hosts.api}/public/streams/$id")

        public suspend fun vod(id: String): PublicVod = http.json(HttpMethod.Get, "${hosts.api}/public/vods/$id")

        public suspend fun presence(id: String, sessionId: String): Int {
            val body = buildJsonObject { put("sessionId", sessionId) }
            val obj = http.json<JsonObject>(HttpMethod.Post, "${hosts.api}/public/streams/$id/presence", body)
            return obj["viewers"]?.let { livoJson.decodeFromJsonElement<Int>(it) } ?: 0
        }

        public suspend fun captions(id: String, lang: String): List<CaptionCue> {
            val obj =
                http.json<JsonObject>(HttpMethod.Get, "${hosts.api}/public/streams/$id/captions/$lang") {
                    header(HttpHeaders.Accept, "application/json")
                }
            val cues = obj["cues"] ?: return emptyList()
            return livoJson.decodeFromJsonElement(cues)
        }

        public suspend fun beacon(sessionId: String, events: List<PlaybackBeaconEvent>) {
            val body = buildJsonObject {
                put("sessionId", sessionId)
                put("events", livoJson.parseToJsonElement(livoJson.encodeToString(events)))
            }
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.api}/public/playback/beacon", body))
        }

        public suspend fun studioJoinStatus(token: String): StudioJoinStatusResult {
            val encoded = java.net.URLEncoder.encode(token, Charsets.UTF_8)
            val response = http.request(HttpMethod.Get, "${hosts.api}/public/studio/join/$encoded/status")
            if (response.status.value == 404 || response.status.value == 410) {
                return StudioJoinStatusResult.Ended
            }
            http.ensureOk(response)
            return StudioJoinStatusResult.Status(response.body())
        }

        public suspend fun studioJoinGuest(token: String, displayName: String, guestId: String): StudioSession {
            val encoded = java.net.URLEncoder.encode(token, Charsets.UTF_8)
            val body = buildJsonObject {
                put("displayName", displayName)
                put("guestId", guestId)
            }
            return http.json(HttpMethod.Post, "${hosts.api}/public/studio/join/$encoded", body)
        }

        public suspend fun studioRedeemHost(token: String): StudioSession {
            val encoded = java.net.URLEncoder.encode(token, Charsets.UTF_8)
            return http.json(
                HttpMethod.Post,
                "${hosts.api}/public/studio/host/$encoded",
                emptyMap<String, String>(),
            )
        }

        public fun community(kind: String, id: String): PublicCommunityClient = PublicCommunityClient(kind, id)
    }

    public inner class CommunityClient(private val kind: String, private val id: String) {
        public suspend fun list(type: CommunityType? = null, q: String? = null, limit: Int = 20, cursor: String? = null): CursorPage<CommunityItem> {
            val url =
                query(
                    "${hosts.api}/$kind/$id/community",
                    mapOf(
                        "type" to type?.serial(),
                        "q" to q,
                        "limit" to "$limit",
                        "cursor" to cursor,
                    ),
                )
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return CursorPage(decodeList(obj, "items"), cursorOf(obj))
        }

        public suspend fun create(body: JsonObject): CommunityItem {
            val obj = http.json<JsonObject>(HttpMethod.Post, "${hosts.api}/$kind/$id/community", body)
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun patch(itemId: String, body: JsonObject): CommunityItem {
            val obj = http.json<JsonObject>(HttpMethod.Patch, "${hosts.api}/$kind/$id/community/$itemId", body)
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun upvote(itemId: String, guestId: String): CommunityItem {
            val body = buildJsonObject { put("guestId", guestId) }
            val obj =
                http.json<JsonObject>(HttpMethod.Post, "${hosts.api}/$kind/$id/community/$itemId/upvote", body)
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun answer(itemId: String, body: String, displayName: String, guestId: String, picture: String? = null): CommunityItem {
            val payload = buildJsonObject {
                put("body", body)
                put("displayName", displayName)
                put("guestId", guestId)
                if (picture != null) put("picture", picture)
            }
            val obj =
                http.json<JsonObject>(HttpMethod.Post, "${hosts.api}/$kind/$id/community/$itemId/answer", payload)
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }
    }

    public inner class PublicCommunityClient(private val kind: String, private val id: String) {
        public suspend fun list(type: CommunityType? = null): CursorPage<CommunityItem> {
            val url =
                query(
                    "${hosts.api}/public/$kind/$id/community",
                    mapOf("type" to type?.serial()),
                )
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return CursorPage(decodeList(obj, "items"), cursorOf(obj))
        }

        public suspend fun create(body: JsonObject): CommunityItem {
            val obj = http.json<JsonObject>(HttpMethod.Post, "${hosts.api}/public/$kind/$id/community", body)
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun canAnswer(guestId: String): Boolean {
            val url = query("${hosts.api}/public/$kind/$id/community/can-answer", mapOf("guestId" to guestId))
            val obj = http.json<JsonObject>(HttpMethod.Get, url)
            return obj["canAnswer"]?.let { livoJson.decodeFromJsonElement(it) } ?: false
        }

        public suspend fun upvote(itemId: String, guestId: String): CommunityItem {
            val body = buildJsonObject { put("guestId", guestId) }
            val obj =
                http.json<JsonObject>(
                    HttpMethod.Post,
                    "${hosts.api}/public/$kind/$id/community/$itemId/upvote",
                    body,
                )
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }

        public suspend fun answer(itemId: String, body: String, displayName: String, guestId: String, picture: String? = null): CommunityItem {
            val payload = buildJsonObject {
                put("body", body)
                put("displayName", displayName)
                put("guestId", guestId)
                if (picture != null) put("picture", picture)
            }
            val obj =
                http.json<JsonObject>(
                    HttpMethod.Post,
                    "${hosts.api}/public/$kind/$id/community/$itemId/answer",
                    payload,
                )
            return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
                ?: livoJson.decodeFromJsonElement(obj)
        }
    }

    public fun controlClient(token: String): StudioControlClient = StudioControlClient(token, hosts, http)

    private inline fun <reified T> decodeList(obj: JsonObject, key: String): List<T> {
        val el = obj[key] ?: return emptyList()
        return livoJson.decodeFromJsonElement(el)
    }

    private fun cursorOf(obj: JsonObject): String? = obj["nextCursor"]?.let { livoJson.decodeFromJsonElement(it) }

    private fun kindWire(kind: MediaKind?): String? = when (kind) {
        MediaKind.VOD -> "vod"
        MediaKind.STREAM -> "stream"
        MediaKind.WEBINAR -> "webinar"
        null -> null
    }

    private fun CommunityType.serial(): String = when (this) {
        CommunityType.COMMENT -> "comment"
        CommunityType.QUESTION -> "question"
    }

    public companion object {
        public val UNSET: String = "__livo_unset__"
    }
}

@Serializable
public data class StreamDetail(val stream: LiveStream, val playbackUrl: String? = null, val ingest: IngestInfo? = null)

@Serializable
public data class CreateStreamRequest(
    val title: String,
    val mode: StreamMode = StreamMode.INSTANT,
    val source: StreamSource? = null,
    val kind: MediaKind? = null,
    val startsAt: Long? = null,
    val durationSeconds: Int? = null,
    val curtainId: String? = null,
    val captionsEnabled: Boolean = false,
    val commentsEnabled: Boolean? = null,
    val commentsAfterEnd: Boolean? = null,
    val qaEnabled: Boolean? = null,
) {
    public fun agreeing(): CreateStreamRequest {
        val resolvedKind =
            kind
                ?: when (source) {
                    StreamSource.STUDIO -> MediaKind.WEBINAR
                    StreamSource.RTMP, null -> MediaKind.STREAM
                }
        val resolvedSource =
            source
                ?: when (resolvedKind) {
                    MediaKind.WEBINAR -> StreamSource.STUDIO
                    MediaKind.STREAM, MediaKind.VOD -> StreamSource.RTMP
                }
        if (
            (resolvedKind == MediaKind.WEBINAR && resolvedSource != StreamSource.STUDIO) ||
            (resolvedKind == MediaKind.STREAM && resolvedSource != StreamSource.RTMP)
        ) {
            throw LivoApiException(400, "kind_source_mismatch", "kind and source must agree")
        }
        return copy(kind = resolvedKind, source = resolvedSource)
    }
}

@Serializable
public data class CreateStreamResponse(val stream: LiveStream, val ingest: IngestInfo? = null)

@Serializable
public data class CreateVodRequest(
    val title: String,
    val filename: String,
    val contentType: String,
    val sha256: String,
    val byteSize: Long,
    val releaseAt: Long? = null,
    val curtainId: String? = null,
    val captionsEnabled: Boolean = false,
    val commentsEnabled: Boolean? = null,
)

public sealed class CreateVodResult {
    public data class Created(val value: CreateVodResponse) : CreateVodResult()

    public data class Conflict(val value: UploadConflict) : CreateVodResult()
}

@Serializable
public data class CurtainCreateResponse(
    val curtain: Curtain? = null,
    val curtainId: String? = null,
    val vodId: String? = null,
    val uploadId: String? = null,
    val partSize: Long = PART_SIZE_BYTES,
)

@Serializable
public data class StudioLivestreamResult(val ok: Boolean = true, val egress: StudioLivestreamEgress = StudioLivestreamEgress.STARTING)

public class StudioControlClient internal constructor(token: String, private val hosts: LivoHosts, private val http: LivoHttp) {
    public var token: String = token
        private set

    public suspend fun livestream(): StudioLivestreamEgress {
        val obj = http.json<JsonObject>(HttpMethod.Post, path("livestream"), emptyMap<String, String>())
        val raw = obj["egress"]?.let { livoJson.decodeFromJsonElement<String>(it) } ?: "starting"
        return if (raw == "live") StudioLivestreamEgress.LIVE else StudioLivestreamEgress.STARTING
    }

    public suspend fun publish(): StudioControlState = http.json(HttpMethod.Post, path("publish"), emptyMap<String, String>())

    public suspend fun stop(): StudioControlState = http.json(HttpMethod.Post, path("stop"), emptyMap<String, String>())

    public suspend fun state(): StudioControlState = http.json(HttpMethod.Get, path("state"))

    public suspend fun refresh(): StudioControlRefresh {
        val result: StudioControlRefresh =
            http.json(HttpMethod.Post, path("refresh"), emptyMap<String, String>())
        token = result.studioControlToken
        return result
    }

    public suspend fun answer(itemId: String, body: String, displayName: String, guestId: String): CommunityItem {
        val payload = buildJsonObject {
            put("body", body)
            put("displayName", displayName)
            put("guestId", guestId)
        }
        val obj =
            http.json<JsonObject>(
                HttpMethod.Post,
                "${hosts.api}/public/studio/control/${java.net.URLEncoder.encode(token, Charsets.UTF_8)}/community/$itemId/answer",
                payload,
            )
        return obj["item"]?.let { livoJson.decodeFromJsonElement(it) }
            ?: livoJson.decodeFromJsonElement(obj)
    }

    private fun path(action: String): String {
        val encoded = java.net.URLEncoder.encode(token, Charsets.UTF_8)
        return "${hosts.api}/public/studio/control/$encoded/$action"
    }
}
