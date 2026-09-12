package tv.livo.sdk

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import tv.livo.sdk.internal.LivoHttp
import tv.livo.sdk.internal.query
import tv.livo.sdk.models.EnvironmentSummary
import tv.livo.sdk.models.JwtClaims
import tv.livo.sdk.models.LicensePreview
import tv.livo.sdk.models.OrganizationInvitation
import tv.livo.sdk.models.OrganizationMember
import tv.livo.sdk.models.OrganizationRole
import tv.livo.sdk.models.OrganizationSummary
import tv.livo.sdk.models.PasskeyRecord
import tv.livo.sdk.models.SessionStatus

public class LivoAuthClient(public val hosts: LivoHosts, private val cookies: CookieStore, engine: HttpClientEngine? = null) {
    private val http =
        LivoHttp(
            engine = engine,
            credentials = LivoCredentials.None,
            cookieHeader = {
                val token = cookies.load() ?: return@LivoHttp null
                val name = SessionCookieNames.nameFor(hosts.auth)
                "$name=$token"
            },
            origin = hosts.authWeb,
        )

    public suspend fun loginMethod(email: String): LoginMethod = try {
        val url = query("${hosts.auth}/api/auth/login-method", mapOf("email" to email))
        val obj = http.json<JsonObject>(HttpMethod.Get, url)
        val method = obj["method"]?.let { livoJson.decodeFromJsonElement<String>(it) } ?: "otp"
        if (method == "password") LoginMethod.PASSWORD else LoginMethod.OTP
    } catch (_: Exception) {
        LoginMethod.OTP
    }

    public suspend fun sendOtp(email: String) {
        val body = buildJsonObject {
            put("email", email)
            put("type", "sign-in")
        }
        http.ensureOk(
            http.request(HttpMethod.Post, "${hosts.auth}/api/auth/email-otp/send-verification-otp", body),
        )
    }

    public suspend fun verifyOtp(email: String, otp: String) {
        val body = buildJsonObject {
            put("email", email)
            put("otp", otp)
        }
        val response =
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/auth/sign-in/email-otp", body))
        captureCookie(response)
    }

    public suspend fun signInPassword(email: String, password: String) {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        val response = http.request(HttpMethod.Post, "${hosts.auth}/api/auth/sign-in/email", body)
        if (response.status.value == 403) {
            throw LivoApiException(
                403,
                LivoApiException.PASSWORD_LOGIN_DISABLED,
                "Password sign-in is disabled for this account",
            )
        }
        http.ensureOk(response)
        captureCookie(response)
    }

    public suspend fun signInGoogle(idToken: String, accessToken: String? = null) {
        val idTokenObj = buildJsonObject {
            put("token", idToken)
            if (accessToken != null) put("accessToken", accessToken)
        }
        val body = buildJsonObject {
            put("provider", "google")
            put("idToken", idTokenObj)
        }
        val response =
            http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/auth/sign-in/social", body))
        captureCookie(response)
    }

    public suspend fun generateRegisterOptions(name: String? = null): JsonObject {
        val url = query("${hosts.auth}/api/auth/passkey/generate-register-options", mapOf("name" to name))
        return http.json(HttpMethod.Get, url)
    }

    public suspend fun verifyRegistration(response: JsonObject, name: String? = null): PasskeyRecord {
        val body = buildJsonObject {
            put("response", response)
            if (name != null) put("name", name)
        }
        return http.json(HttpMethod.Post, "${hosts.auth}/api/auth/passkey/verify-registration", body)
    }

    public suspend fun generateAuthenticateOptions(): JsonObject = http.json(HttpMethod.Get, "${hosts.auth}/api/auth/passkey/generate-authenticate-options")

    public suspend fun verifyAuthentication(response: JsonObject) {
        val body = buildJsonObject { put("response", response) }
        val httpResponse =
            http.ensureOk(
                http.request(HttpMethod.Post, "${hosts.auth}/api/auth/passkey/verify-authentication", body),
            )
        captureCookie(httpResponse)
    }

    public suspend fun listPasskeys(): List<PasskeyRecord> {
        val el = http.json<JsonElement>(
            HttpMethod.Get,
            "${hosts.auth}/api/auth/passkey/list-user-passkeys",
        )
        return livoJson.decodeFromJsonElement(el)
    }

    public suspend fun deletePasskey(id: String) {
        val body = buildJsonObject { put("id", id) }
        http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/auth/passkey/delete-passkey", body))
    }

    public suspend fun updatePasskey(id: String, name: String) {
        val body = buildJsonObject {
            put("id", id)
            put("name", name)
        }
        http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/auth/passkey/update-passkey", body))
    }

    public suspend fun sessionWithStatus(): SessionStatus = http.json(HttpMethod.Get, "${hosts.auth}/api/me/session-with-status")

    public suspend fun getSession(): JsonObject? {
        val response = http.request(HttpMethod.Get, "${hosts.auth}/api/auth/get-session")
        if (response.status.value == 401) return null
        val text = response.bodyAsText()
        if (text.isBlank() || text == "null") return null
        http.ensureOk(response)
        return livoJson.decodeFromString(text)
    }

    public suspend fun mintJwt(): String {
        val obj = http.json<JsonObject>(HttpMethod.Get, "${hosts.auth}/api/auth/token")
        return obj["token"]?.let { livoJson.decodeFromJsonElement(it) }
            ?: error("token response missing token")
    }

    public suspend fun signOut() {
        val response =
            http.request(HttpMethod.Post, "${hosts.auth}/api/auth/sign-out", emptyMap<String, String>()) {
                header(HttpHeaders.ContentType, "application/json")
            }
        http.ensureOk(response)
        cookies.clear()
    }

    public suspend fun previewLicense(licenseKey: String): LicensePreview {
        val body = buildJsonObject { put("licenseKey", licenseKey) }
        return http.json(HttpMethod.Post, "${hosts.auth}/api/me/licenses/preview", body)
    }

    public suspend fun createOrganization(name: String, licenseKey: String): OrganizationSummary? {
        val body = buildJsonObject {
            put("name", name)
            put("licenseKey", licenseKey)
        }
        val obj = http.json<JsonObject>(HttpMethod.Post, "${hosts.auth}/api/me/organizations", body)
        return obj["id"]?.let { livoJson.decodeFromJsonElement<OrganizationSummary>(obj) }
    }

    public suspend fun setActiveOrganization(organizationId: String) {
        val body = buildJsonObject { put("organizationId", organizationId) }
        http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/me/active-organization", body))
    }

    public suspend fun setActiveEnvironment(environmentId: String) {
        val body = buildJsonObject { put("environmentId", environmentId) }
        http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/me/active-environment", body))
    }

    public suspend fun members(orgId: String): MembersPayload {
        val el = http.json<JsonElement>(
            HttpMethod.Get,
            "${hosts.auth}/api/me/organizations/$orgId/members",
        )
        return parseMembers(el)
    }

    public suspend fun invitations(orgId: String): List<OrganizationInvitation> {
        val obj = http.json<JsonObject>(HttpMethod.Get, "${hosts.auth}/api/me/organizations/$orgId/invitations")
        val list = obj["invitations"] ?: obj["pendingInvitations"] ?: return emptyList()
        return livoJson.decodeFromJsonElement(list)
    }

    public suspend fun invite(orgId: String, email: String, role: OrganizationRole) {
        val body = buildJsonObject {
            put("email", email)
            put("role", role.name.lowercase())
        }
        http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/me/organizations/$orgId/invitations", body))
    }

    public suspend fun revokeInvitation(orgId: String, invitationId: String) {
        http.ensureOk(
            http.request(HttpMethod.Delete, "${hosts.auth}/api/me/organizations/$orgId/invitations/$invitationId"),
        )
    }

    public suspend fun updateMemberRole(orgId: String, userId: String, role: OrganizationRole) {
        val body = buildJsonObject { put("role", role.name.lowercase()) }
        http.ensureOk(
            http.request(HttpMethod.Patch, "${hosts.auth}/api/me/organizations/$orgId/members/$userId", body),
        )
    }

    public suspend fun removeMember(orgId: String, userId: String) {
        http.ensureOk(http.request(HttpMethod.Delete, "${hosts.auth}/api/me/organizations/$orgId/members/$userId"))
    }

    public suspend fun transferOwnership(orgId: String, userId: String) {
        val body = buildJsonObject { put("userId", userId) }
        val paths = listOf("transfer", "transfer-ownership")
        var last: Exception? = null
        for (path in paths) {
            try {
                http.ensureOk(
                    http.request(HttpMethod.Post, "${hosts.auth}/api/me/organizations/$orgId/$path", body),
                )
                return
            } catch (e: LivoApiException) {
                last = e
                if (e.status != 404) throw e
            }
        }
        throw last ?: LivoApiException(404, null, "transfer not found")
    }

    public suspend fun renameOrganization(orgId: String, name: String) {
        val body = buildJsonObject { put("name", name) }
        http.ensureOk(http.request(HttpMethod.Patch, "${hosts.auth}/api/me/organizations/$orgId", body))
    }

    public suspend fun uploadOrgAvatar(orgId: String, bytes: ByteArray, filename: String = "avatar.jpg") {
        uploadMultipart("${hosts.auth}/api/me/organizations/$orgId/avatar", bytes, filename)
    }

    public suspend fun uploadAvatar(bytes: ByteArray, filename: String = "avatar.jpg") {
        uploadMultipart("${hosts.auth}/api/me/avatar", bytes, filename)
    }

    public suspend fun updateProfile(name: String) {
        val body = buildJsonObject { put("name", name) }
        http.ensureOk(http.request(HttpMethod.Patch, "${hosts.auth}/api/me", body))
    }

    public suspend fun updateLocale(locale: String) {
        val body = buildJsonObject { put("locale", locale) }
        http.ensureOk(http.request(HttpMethod.Patch, "${hosts.auth}/api/me/locale", body))
    }

    public suspend fun createEnvironment(organizationId: String, slug: String, name: String): EnvironmentSummary {
        val body = buildJsonObject {
            put("organizationId", organizationId)
            put("slug", slug)
            put("name", name)
        }
        return http.json(HttpMethod.Post, "${hosts.auth}/api/me/environments", body)
    }

    public suspend fun deleteEnvironment(id: String) {
        http.ensureOk(http.request(HttpMethod.Delete, "${hosts.auth}/api/me/environments/$id"))
    }

    public suspend fun environmentGrants(id: String): List<String> {
        val obj = http.json<JsonObject>(HttpMethod.Get, "${hosts.auth}/api/me/environments/$id/grants")
        val ids = obj["userIds"] ?: return emptyList()
        return livoJson.decodeFromJsonElement(ids)
    }

    public suspend fun setEnvironmentGrants(id: String, userIds: List<String>) {
        val body = buildJsonObject {
            put("userIds", JsonArray(userIds.map { JsonPrimitive(it) }))
        }
        http.ensureOk(http.request(HttpMethod.Put, "${hosts.auth}/api/me/environments/$id/grants", body))
    }

    public suspend fun acceptLegal(termsVersion: String, privacyVersion: String) {
        val body = buildJsonObject {
            put("termsVersion", termsVersion)
            put("privacyVersion", privacyVersion)
        }
        http.ensureOk(http.request(HttpMethod.Post, "${hosts.auth}/api/me/legal/accept", body))
    }

    public suspend fun publicLegal(kind: String): JsonObject = http.json(HttpMethod.Get, "${hosts.auth}/api/public/legal/$kind")

    public fun decodeJwt(token: String): JwtClaims {
        val payload = token.split('.').getOrNull(1) ?: error("invalid jwt")
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val json = String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        return livoJson.decodeFromString(json)
    }

    public fun close() {
        http.close()
    }

    private suspend fun uploadMultipart(url: String, bytes: ByteArray, filename: String) {
        val part =
            MultiPartFormDataContent(
                formData {
                    append(
                        "file",
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                        },
                    )
                },
            )
        http.ensureOk(
            http.request(HttpMethod.Post, url) {
                setBody(part)
            },
        )
    }

    private fun captureCookie(response: HttpResponse) {
        val header = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        val names = listOf(SessionCookieNames.SECURE, SessionCookieNames.PLAIN)
        for (raw in header) {
            for (name in names) {
                val prefix = "$name="
                if (raw.startsWith(prefix)) {
                    val value = raw.removePrefix(prefix).substringBefore(';')
                    if (value.isNotBlank()) cookies.save(value)
                    return
                }
            }
        }
    }

    private fun parseMembers(el: JsonElement): MembersPayload {
        if (el is JsonArray) {
            return MembersPayload(livoJson.decodeFromJsonElement(el), emptyList())
        }
        val obj = el.jsonObject
        val members = obj["members"] ?: obj["items"]
        val invitations = obj["invitations"] ?: obj["pendingInvitations"]
        return MembersPayload(
            members = members?.let { livoJson.decodeFromJsonElement(it) } ?: emptyList(),
            invitations = invitations?.let { livoJson.decodeFromJsonElement(it) } ?: emptyList(),
        )
    }
}

public enum class LoginMethod {
    OTP,
    PASSWORD,
}

public data class MembersPayload(val members: List<OrganizationMember>, val invitations: List<OrganizationInvitation>)
