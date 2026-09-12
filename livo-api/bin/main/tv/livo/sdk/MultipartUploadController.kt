package tv.livo.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tv.livo.sdk.models.CompletedPart
import tv.livo.sdk.models.PART_SIZE_BYTES
import java.io.InputStream
import java.security.MessageDigest

public interface UploadSource {
    public suspend fun open(): InputStream

    public val size: Long
}

public data class MultipartProgress(val bytesSent: Long, val totalBytes: Long, val completedParts: Int, val totalParts: Int)

public class MultipartUploadController(
    private val source: UploadSource,
    private val presign: suspend (partNumber: Int) -> String,
    private val complete: suspend (parts: List<CompletedPart>) -> Unit,
    private val abort: suspend () -> Unit = {},
    private val partSize: Long = PART_SIZE_BYTES,
    private val concurrency: Int = 3,
    private val http: HttpClient = HttpClient(CIO),
) {
    private val _progress = MutableStateFlow(MultipartProgress(0, source.size, 0, 0))
    public val progress: Flow<MultipartProgress> = _progress.asStateFlow()

    public suspend fun sha256(): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        source.open().use { input ->
            val buf = ByteArray(8 * 1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    public suspend fun upload(existing: List<CompletedPart> = emptyList()) {
        val totalParts = ((source.size + partSize - 1) / partSize).toInt().coerceAtLeast(1)
        val done = existing.associateBy { it.partNumber }.toMutableMap()
        _progress.value = MultipartProgress(done.size * partSize, source.size, done.size, totalParts)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            (1..totalParts)
                .filter { it !in done }
                .map { part ->
                    async {
                        semaphore.withPermit {
                            val etag = putPart(part)
                            done[part] = CompletedPart(part, etag)
                            _progress.value =
                                MultipartProgress(
                                    bytesSent = done.size * partSize,
                                    totalBytes = source.size,
                                    completedParts = done.size,
                                    totalParts = totalParts,
                                )
                        }
                    }
                }.awaitAll()
        }
        complete(done.values.sortedBy { it.partNumber })
    }

    public suspend fun abortUpload() {
        abort()
    }

    public fun close() {
        http.close()
    }

    private suspend fun putPart(partNumber: Int): String {
        val bytes = readPart(partNumber)
        var last: Exception? = null
        repeat(2) {
            try {
                val url = presign(partNumber)
                val response: HttpResponse =
                    http.put(url) {
                        setBody(bytes)
                    }
                if (response.status.value !in 200..299) {
                    throw LivoApiException(response.status.value, null, "part PUT failed")
                }
                val etag = response.headers[HttpHeaders.ETag] ?: error("missing ETag")
                return decodeEtag(etag)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: LivoApiException(502, null, "part $partNumber failed")
    }

    private suspend fun readPart(partNumber: Int): ByteArray = withContext(Dispatchers.IO) {
        val offset = (partNumber - 1) * partSize
        source.open().use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val n = input.skip(offset - skipped)
                if (n <= 0) break
                skipped += n
            }
            val remaining = minOf(partSize, source.size - offset).toInt()
            val buf = ByteArray(remaining)
            var read = 0
            while (read < remaining) {
                val n = input.read(buf, read, remaining - read)
                if (n <= 0) break
                read += n
            }
            if (read == remaining) buf else buf.copyOf(read)
        }
    }

    public companion object {
        public fun decodeEtag(raw: String): String = raw
            .replace("&quot;", "\"")
            .trim()
            .trim('"')
    }
}
