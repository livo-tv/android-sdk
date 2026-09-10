package tv.livo.sdk

/**
 * Single source of truth for every backend hostname. Never hardcode a host in a feature.
 */
public data class LivoHosts(
    public val api: String,
    public val auth: String,
    public val notifications: String,
    public val realtime: String,
    public val metrics: String,
    public val authWeb: String,
    public val player: String,
    public val dashboard: String,
) {
    public fun watchUrl(streamId: String): String = "${player.trimEnd('/')}/$streamId"

    public fun vodWatchUrl(vodId: String): String = "${player.trimEnd('/')}/vod/$vodId"

    public fun studioUrl(streamId: String): String = "${dashboard.trimEnd('/')}/studio/$streamId"

    public fun studioGuestUrl(token: String): String = "${dashboard.trimEnd('/')}/studio/join/$token"

    public companion object {
        public val production: LivoHosts =
            LivoHosts(
                api = "https://media-svc.livo.tv",
                auth = "https://auth-svc.livo.tv",
                notifications = "https://notifications-svc.livo.tv",
                realtime = "wss://realtime-svc.livo.tv",
                metrics = "https://metrics-svc.livo.tv",
                authWeb = "https://auth.livo.tv",
                player = "https://player.livo.tv",
                dashboard = "https://app.livo.tv",
            )

        public val development: LivoHosts =
            LivoHosts(
                api = "https://media-svc.livo-tv.workers.dev",
                auth = "https://auth-svc.livo-tv.workers.dev",
                notifications = "https://notifications-svc.livo-tv.workers.dev",
                realtime = "wss://realtime-svc.livo-tv.workers.dev",
                metrics = "https://metrics-svc.livo-tv.workers.dev",
                authWeb = "https://auth.livo-tv.workers.dev",
                player = "https://player.livo-tv.workers.dev",
                dashboard = "https://app.livo-tv.workers.dev",
            )

        public fun previewStack(slug: String): LivoHosts {
            val s = slug.trim().lowercase()
            return LivoHosts(
                api = "https://media-svc-$s.livo-tv.workers.dev",
                auth = "https://auth-svc-$s.livo-tv.workers.dev",
                notifications = "https://notifications-svc-$s.livo-tv.workers.dev",
                realtime = "wss://realtime-svc-$s.livo-tv.workers.dev",
                metrics = "https://metrics-svc-$s.livo-tv.workers.dev",
                authWeb = "https://auth-$s.livo-tv.workers.dev",
                player = "https://player-$s.livo-tv.workers.dev",
                dashboard = "https://app-$s.livo-tv.workers.dev",
            )
        }

        public fun normalizeApiUrl(url: String): String = url.trim().trimEnd('/')
    }
}
