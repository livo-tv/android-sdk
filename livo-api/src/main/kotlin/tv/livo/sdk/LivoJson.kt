package tv.livo.sdk

import kotlinx.serialization.json.Json

internal val livoJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }
