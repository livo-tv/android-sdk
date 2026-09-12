package tv.livo.sdk

import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import tv.livo.sdk.internal.LivoHttp
import tv.livo.sdk.internal.query
import tv.livo.sdk.models.CursorPage
import tv.livo.sdk.models.MetricPeriod
import tv.livo.sdk.models.MetricRollup
import tv.livo.sdk.models.MetricsHighlights

public class LivoMetricsClient(hosts: LivoHosts, credentials: LivoCredentials, engine: HttpClientEngine? = null) {
    private val http = LivoHttp(engine, credentials)
    private val base = hosts.metrics.trimEnd('/')

    public suspend fun highlights(period: MetricPeriod = MetricPeriod.MONTH, from: Long? = null, to: Long? = null): MetricsHighlights {
        val url =
            query(
                "$base/metrics/highlights",
                mapOf(
                    "period" to period.name.lowercase(),
                    "from" to from?.toString(),
                    "to" to to?.toString(),
                ),
            )
        return http.json(HttpMethod.Get, url)
    }

    public suspend fun rollups(
        period: MetricPeriod,
        metric: String? = null,
        scope: String? = null,
        streamId: String? = null,
        vodId: String? = null,
        cursor: String? = null,
        limit: Int = 20,
    ): CursorPage<MetricRollup> {
        val url =
            query(
                "$base/metrics/rollups",
                mapOf(
                    "period" to period.name.lowercase(),
                    "metric" to metric,
                    "scope" to scope,
                    "streamId" to streamId,
                    "vodId" to vodId,
                    "cursor" to cursor,
                    "limit" to "$limit",
                ),
            )
        val obj = http.json<JsonObject>(HttpMethod.Get, url)
        val items = obj["items"] ?: return CursorPage()
        return CursorPage(
            livoJson.decodeFromJsonElement(items),
            obj["nextCursor"]?.let { livoJson.decodeFromJsonElement(it) },
        )
    }

    public suspend fun spend(period: MetricPeriod = MetricPeriod.MONTH): Double {
        val url = query("$base/metrics/spend", mapOf("period" to period.name.lowercase()))
        val obj = http.json<JsonObject>(HttpMethod.Get, url)
        return obj["spendUsd"]?.let { livoJson.decodeFromJsonElement(it) } ?: 0.0
    }

    public fun close() {
        http.close()
    }
}
