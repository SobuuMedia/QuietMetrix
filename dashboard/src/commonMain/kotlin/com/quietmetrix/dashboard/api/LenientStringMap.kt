package com.quietmetrix.dashboard.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Decodes a JSON value into `Map<String, String>`, tolerant of shapes a strict
 * `MapSerializer(String, String)` rejects: an empty array (PHP's `json_encode` cannot tell an
 * empty object from an empty list once decoded, so `"props":{}` can come back as `"props":[]`),
 * `null`, or scalar values that aren't already strings (numbers/booleans are stringified).
 * Encoding always emits a JSON object, `{}` when empty, so a client round-tripping this type
 * never re-introduces the ambiguous shape.
 */
object LenientStringMapSerializer : KSerializer<Map<String, String>> {
    private val delegate = serializer<Map<String, String>>()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        val obj = jsonDecoder.decodeJsonElement() as? JsonObject ?: return emptyMap()
        return obj.mapValues { (_, v) -> jsonElementToString(v) }
    }

    private fun jsonElementToString(element: JsonElement): String =
        if (element is JsonNull) "null" else element.jsonPrimitive.content
}
