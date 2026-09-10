package tv.livo.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tv.livo.sdk.models.MediaKind
import tv.livo.sdk.models.StreamSource
import java.util.concurrent.atomic.AtomicInteger

class LivoApiClientTest {
    private val hosts = LivoHosts.production

    @Test
    fun mediaListDecodesItemsStrictly() = runTest {
        val engine =
            jsonEngine(
                """{"items":[{"kind":"vod","id":"v1","title":"Talk"}],"nextCursor":"c1"}""",
            )
        val client = LivoApiClient(hosts, LivoCredentials.None, engine)
        val page = client.media.list(q = "talk", kind = MediaKind.VOD)
        assertEquals(1, page.items.size)
        assertEquals("v1", page.items.first().resolvedId)
        assertEquals("c1", page.nextCursor)
        client.close()
    }

    @Test
    fun createStreamSendsAgreeingKindAndSource() {
        val request = CreateStreamRequest(title = "Town hall", kind = MediaKind.WEBINAR)
        val agreed = request.agreeing()
        assertEquals(MediaKind.WEBINAR, agreed.kind)
        assertEquals(StreamSource.STUDIO, agreed.source)
    }

    @Test
    fun vodCreateMaps409Conflict() = runTest {
        val engine =
            MockEngine {
                respond(
                    content = ByteReadChannel("""{"error":"upload_in_progress","vodId":"v9"}"""),
                    status = HttpStatusCode.Conflict,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = LivoApiClient(hosts, LivoCredentials.None, engine)
        val result =
            client.vods.create(
                CreateVodRequest(
                    title = "Clip",
                    filename = "a.mp4",
                    contentType = "video/mp4",
                    sha256 = "abc",
                    byteSize = 12,
                ),
            )
        val conflict = assertInstanceOf(CreateVodResult.Conflict::class.java, result)
        assertEquals("upload_in_progress", conflict.value.error)
        assertEquals("v9", conflict.value.vodId)
        client.close()
    }

    @Test
    fun trashEmptyPostsCollection() = runTest {
        val paths = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                paths.add(request.url.encodedPath)
                respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders())
            }
        val client = LivoApiClient(hosts, LivoCredentials.None, engine)
        client.trash.empty()
        assertEquals("/trash/empty", paths.last())
        client.close()
    }

    @Test
    fun publicStudioJoinEndedOn404() = runTest {
        val engine =
            MockEngine {
                respond("gone", HttpStatusCode.NotFound, jsonHeaders())
            }
        val client = LivoApiClient(hosts, LivoCredentials.None, engine)
        val status = client.public.studioJoinStatus("tok")
        assertEquals(tv.livo.sdk.models.StudioJoinStatusResult.Ended, status)
        client.close()
    }

    @Test
    fun bearerRefreshesOnceOn401() = runTest {
        val hits = AtomicInteger()
        val engine =
            MockEngine { request ->
                val n = hits.incrementAndGet()
                if (n == 1) {
                    respond("nope", HttpStatusCode.Unauthorized, jsonHeaders())
                } else {
                    respond(
                        """{"id":"s1","title":"Live","status":"public"}""",
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                }
            }
        val client =
            LivoApiClient(
                hosts,
                LivoCredentials.Bearer(
                    tokenProvider = { "fresh" },
                    onUnauthorized = { "fresh" },
                ),
                engine,
            )
        val stream = client.streams.get("s1")
        assertEquals("s1", stream.stream.id)
        assertEquals(2, hits.get())
        client.close()
    }

    @Test
    fun communityCanAnswer() = runTest {
        val engine = jsonEngine("""{"canAnswer":true}""")
        val client = LivoApiClient(hosts, LivoCredentials.None, engine)
        assertTrue(client.public.community("streams", "s1").canAnswer("guest:1"))
        client.close()
    }

    @Test
    fun postersNeverGiveSpriteToPlayer() {
        val sprite = "https://hls.livo.tv/vod/1/thumbs/sprite_000.jpg"
        assertTrue(Posters.isSpriteUrl(sprite))
        assertEquals(
            sprite,
            Posters.posterUrlOrSprite(null, "https://hls.livo.tv/vod/1/index.m3u8"),
        )
    }

    @Test
    fun previewStackHostsUseSlugInfix() {
        val hosts = LivoHosts.previewStack("feat-android")
        assertEquals("https://media-svc-feat-android.livo-tv.workers.dev", hosts.api)
        assertEquals("wss://realtime-svc-feat-android.livo-tv.workers.dev", hosts.realtime)
    }

    @Test
    fun decodeEtagStripsQuotes() {
        assertEquals("abc", MultipartUploadController.decodeEtag("&quot;abc&quot;"))
        assertEquals("abc", MultipartUploadController.decodeEtag("\"abc\""))
    }

    @Test
    fun highlightsParseBagAndFlat() = runTest {
        val engine =
            jsonEngine(
                """{"metrics":{"views":{"value":12,"count":3}},"liveNow":2,"spendUsd":1.5}""",
            )
        val metrics = LivoMetricsClient(hosts, LivoCredentials.None, engine)
        val highlights = metrics.highlights()
        assertEquals(2.0, highlights.card("liveNow"))
        assertEquals(12.0, highlights.card("views"))
        assertEquals(1.5, highlights.card("spendUsd"))
        metrics.close()
    }
}

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

private fun jsonEngine(body: String): MockEngine = MockEngine {
    respond(ByteReadChannel(body), HttpStatusCode.OK, jsonHeaders())
}
