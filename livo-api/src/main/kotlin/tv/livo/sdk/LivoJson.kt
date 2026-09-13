package tv.livo.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

internal val livoJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

/** Missing keys and JSON `null` are both absent. `JsonObject[key]` is not Kotlin-null for `null`. */
internal inline fun <reified T> Json.decodeOrNull(element: JsonElement?): T? {
    if (element == null || element is JsonNull) return null
    return decodeFromJsonElement(element)
}
