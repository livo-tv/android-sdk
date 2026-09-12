package tv.livo.sdk

/**
 * List posters: `posterUrl`, else derive `thumbs/sprite_000.jpg` from `index.m3u8`.
 * Player chrome must drop sprite URLs — they are seek tiles, not a still.
 */
public object Posters {
    public fun posterUrlOrSprite(posterUrl: String?, playbackUrl: String?): String? {
        if (!posterUrl.isNullOrBlank() && !isSpriteUrl(posterUrl)) return posterUrl
        val playback = playbackUrl ?: return null
        val sprite = playback.replace("index.m3u8", "thumbs/sprite_000.jpg")
        return if (sprite != playback) sprite else null
    }

    public fun isSpriteUrl(url: String): Boolean = Regex("/thumbs/sprite_\\d+\\.jpg(?:\\?.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(url)

    public fun playbackDir(playbackUrl: String): String = playbackUrl.substringBeforeLast("/").trimEnd('/')

    public fun captionsVttUrl(playbackUrl: String, lang: String): String = playbackUrl.replace("index.m3u8", "subs/$lang.vtt")
}
