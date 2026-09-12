package tv.livo.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoryCookies : CookieStore {
    var value: String? = null

    override fun load(): String? = value

    override fun save(token: String) {
        value = token
    }

    override fun clear() {
        value = null
    }
}

class LivoAuthClientTest {
    @Test
    fun loginMethodFailsOpenToOtp() = runTest {
        val engine =
            MockEngine {
                respond("nope", HttpStatusCode.TooManyRequests, headersOf())
            }
        val auth = LivoAuthClient(LivoHosts.production, MemoryCookies(), engine)
        assertEquals(LoginMethod.OTP, auth.loginMethod("a@b.c"))
        auth.close()
    }

    @Test
    fun password403IsTyped() = runTest {
        val engine =
            MockEngine {
                respond(
                    """{"code":"PASSWORD_LOGIN_DISABLED","message":"off"}""",
                    HttpStatusCode.Forbidden,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val auth = LivoAuthClient(LivoHosts.production, MemoryCookies(), engine)
        val ex =
            runCatching { auth.signInPassword("a@b.c", "x") }.exceptionOrNull()
                as LivoApiException
        assertEquals(LivoApiException.PASSWORD_LOGIN_DISABLED, ex.code)
        auth.close()
    }

    @Test
    fun verifyOtpCapturesSessionCookie() = runTest {
        val cookies = MemoryCookies()
        val engine =
            MockEngine {
                respond(
                    """{"ok":true}""",
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to
                            listOf("${SessionCookieNames.SECURE}=abc123; Path=/; HttpOnly"),
                    ),
                )
            }
        val auth = LivoAuthClient(LivoHosts.production, cookies, engine)
        auth.verifyOtp("a@b.c", "123456")
        assertEquals("abc123", cookies.value)
        auth.close()
    }
}
