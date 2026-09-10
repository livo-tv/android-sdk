package tv.livo.sdk

import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tv.livo.sdk.internal.LivoHttp
import tv.livo.sdk.internal.query
import tv.livo.sdk.models.NotificationsPage

public class LivoNotificationsClient(hosts: LivoHosts, credentials: LivoCredentials, engine: HttpClientEngine? = null) {
    private val http = LivoHttp(engine, credentials)
    private val base = hosts.notifications.trimEnd('/')

    public suspend fun list(limit: Int = 30, cursor: String? = null): NotificationsPage {
        val url = query("$base/me/notifications", mapOf("limit" to "$limit", "cursor" to cursor))
        return http.json(HttpMethod.Get, url)
    }

    public suspend fun markRead(ids: List<String>? = null) {
        val body =
            if (ids == null) {
                emptyMap<String, String>()
            } else {
                buildJsonObject {
                    put("ids", livoJson.parseToJsonElement(livoJson.encodeToString(ids)))
                }
            }
        http.ensureOk(http.request(HttpMethod.Post, "$base/me/notifications/read", body))
    }

    public fun close() {
        http.close()
    }
}
