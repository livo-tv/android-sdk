package tv.livo.sdk.internal

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import tv.livo.sdk.LivoApiException
import tv.livo.sdk.LivoCredentials
import tv.livo.sdk.livoJson

internal class LivoHttp(
    engine: HttpClientEngine? = null,
    private val credentials: LivoCredentials,
    private val cookieHeader: (() -> String?)? = null,
    private val origin: String? = null,
) {
    val client: HttpClient =
        HttpClient(engine ?: CIO.create()) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            install(ContentNegotiation) {
                json(livoJson)
            }
            defaultRequest {
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                origin?.let { header("Origin", it) }
            }
        }

    suspend fun request(method: HttpMethod, url: String, body: Any? = null, retryUnauthorized: Boolean = true, extra: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val idempotent = method == HttpMethod.Get || method == HttpMethod.Put
        var last: HttpResponse? = null
        var attempts = 0
        while (true) {
            val response =
                client.request {
                    this.method = method
                    url { takeFrom(url) }
                    applyAuth()
                    if (body != null) {
                        when (body) {
                            is JsonElement -> {
                                contentType(ContentType.Application.Json)
                                setBody(
                                    TextContent(
                                        livoJson.encodeToString(JsonElement.serializer(), body),
                                        ContentType.Application.Json,
                                    ),
                                )
                            }
                            is io.ktor.client.request.forms.MultiPartFormDataContent -> setBody(body)
                            else -> {
                                contentType(ContentType.Application.Json)
                                setBody(body)
                            }
                        }
                    }
                    extra()
                }
            if (response.status.value == 401 && retryUnauthorized) {
                val refreshed = refreshBearer()
                if (refreshed) {
                    return request(method, url, body, retryUnauthorized = false, extra = extra)
                }
            }
            if (idempotent && response.status.value in 500..599 && attempts < 2) {
                attempts += 1
                val delayMs =
                    response.headers["Retry-After"]?.toLongOrNull()?.times(1000)?.coerceAtMost(10_000)
                        ?: (1L shl attempts) * 1000
                kotlinx.coroutines.delay(delayMs)
                last = response
                continue
            }
            return response
        }
    }

    private suspend fun HttpRequestBuilder.applyAuth() {
        when (val creds = credentials) {
            is LivoCredentials.Bearer -> {
                header(HttpHeaders.Authorization, "Bearer ${creds.tokenProvider()}")
            }
            is LivoCredentials.ApiKey -> header("X-Api-Key", creds.key)
            LivoCredentials.None -> {}
        }
        cookieHeader?.invoke()?.let { header(HttpHeaders.Cookie, it) }
    }

    private suspend fun refreshBearer(): Boolean {
        val creds = credentials as? LivoCredentials.Bearer ?: return false
        val next = creds.onUnauthorized() ?: return false
        return next.isNotBlank()
    }

    suspend fun ensureOk(response: HttpResponse): HttpResponse {
        val status = response.status.value
        if (status in 200..299) return response
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { livoJson.decodeFromString<JsonObject>(text) }.getOrNull()
        val code =
            parsed?.get("code")?.jsonPrimitive?.contentOrNull
                ?: parsed?.get("error")?.let { (it as? JsonPrimitive)?.contentOrNull }
        val message =
            parsed?.get("message")?.jsonPrimitive?.contentOrNull
                ?: parsed?.get("error")?.jsonPrimitive?.contentOrNull
                ?: text.ifBlank { response.status.description }
        val retryAfter =
            response.headers["Retry-After"]?.toLongOrNull()?.times(1000)
        if (status == 403 && code == LivoApiException.PASSWORD_LOGIN_DISABLED) {
            throw LivoApiException(status, code, message, retryAfter)
        }
        if (status == 403 && code == LivoApiException.SESSION_NOT_FRESH) {
            throw LivoApiException(status, code, message, retryAfter)
        }
        throw LivoApiException(status, code, message, retryAfter)
    }

    suspend inline fun <reified T> json(method: HttpMethod, url: String, body: Any? = null, noinline extra: HttpRequestBuilder.() -> Unit = {}): T {
        val response = ensureOk(request(method, url, body, extra = extra))
        return response.body()
    }

    fun close() {
        client.close()
    }
}

internal fun query(base: String, params: Map<String, String?>): String {
    val items =
        params.entries
            .filter { !it.value.isNullOrBlank() }
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, Charsets.UTF_8)}" }
    return if (items.isEmpty()) base else "$base?$items"
}
