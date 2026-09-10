package tv.livo.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.math.min

public data class RealtimeEvent(val type: String, val payload: Map<String, String> = emptyMap(), val ts: Long? = null)

public class LivoRealtimeClient(private val hosts: LivoHosts, private val tokenProvider: suspend () -> String, engine: HttpClientEngine? = null) {
    private val client: HttpClient =
        HttpClient(engine ?: CIO.create()) {
            install(WebSockets)
        }

    public fun subscribe(channel: String): Flow<RealtimeEvent> = flow {
        var backoff = 2_000L
        while (true) {
            try {
                val token = URLEncoder.encode(tokenProvider(), Charsets.UTF_8)
                val url = "${hosts.realtime.trimEnd('/')}/channels/$channel/ws?token=$token"
                client.webSocket(url) {
                    var lastPing = 0L
                    while (isActive) {
                        val now = System.currentTimeMillis()
                        if (now - lastPing >= 30_000L) {
                            send(Frame.Text("""{"type":"ping"}"""))
                            lastPing = now
                        }
                        val frame = withTimeoutOrNull(1_000) { incoming.receive() }
                        if (frame is Frame.Text) {
                            val event = parse(frame.readText()) ?: continue
                            if (event.type == "pong") continue
                            emit(event)
                        }
                    }
                }
                backoff = 2_000L
            } catch (_: Exception) {
                delay(backoff)
                backoff = min(backoff * 2, 30_000L)
            }
        }
    }

    public fun close() {
        client.close()
    }

    private fun parse(text: String): RealtimeEvent? {
        val obj = runCatching { livoJson.decodeFromString<JsonObject>(text) }.getOrNull() ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val payload =
            (obj["payload"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
                ?: emptyMap()
        val ts = obj["ts"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        return RealtimeEvent(type, payload, ts)
    }
}
