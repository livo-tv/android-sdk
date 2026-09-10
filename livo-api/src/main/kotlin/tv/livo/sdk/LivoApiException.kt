package tv.livo.sdk

public class LivoApiException(public val status: Int, public val code: String? = null, message: String, public val retryAfterMs: Long? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    public val isEnded: Boolean get() = status == 404 || status == 410

    public val isUnauthenticated: Boolean get() = status == 401

    public companion object {
        public const val STUDIO_NOT_AVAILABLE: String = "studio_not_available"
        public const val STUDIO_NOT_STARTED: String = "studio_not_started"
        public const val STUDIO_HOST_TAKEN: String = "studio_host_taken"
        public const val UPLOAD_IN_PROGRESS: String = "upload_in_progress"
        public const val DUPLICATE_SOURCE: String = "duplicate_source"
        public const val COMMENTS_CLOSED: String = "comments_closed"
        public const val QA_CLOSED: String = "qa_closed"
        public const val PASSWORD_LOGIN_DISABLED: String = "PASSWORD_LOGIN_DISABLED"
        public const val SESSION_NOT_FRESH: String = "SESSION_NOT_FRESH"
    }
}
