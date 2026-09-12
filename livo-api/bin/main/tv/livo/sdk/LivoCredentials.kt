package tv.livo.sdk

/**
 * How the HTTP client authenticates. Partner `lk_` keys belong on a backend, never in an APK.
 */
public sealed interface LivoCredentials {
    public data class Bearer(public val tokenProvider: suspend () -> String, public val onUnauthorized: suspend () -> String? = { null }) : LivoCredentials

    /**
     * API-key auth for JVM backends. Never ship `lk_` / `lp_` in a mobile binary.
     */
    public data class ApiKey(public val key: String) : LivoCredentials

    public data object None : LivoCredentials
}

public interface CookieStore {
    public fun load(): String?

    public fun save(token: String)

    public fun clear()
}

public object SessionCookieNames {
    public const val SECURE: String = "__Secure-better-auth.session_token"
    public const val PLAIN: String = "better-auth.session_token"

    public fun nameFor(authUrl: String): String = if (authUrl.startsWith("https://")) SECURE else PLAIN
}
