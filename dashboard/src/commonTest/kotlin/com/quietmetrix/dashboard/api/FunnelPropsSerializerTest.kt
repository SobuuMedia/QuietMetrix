package com.quietmetrix.dashboard.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the bug where a PHP-hosted server emits `"props":[]` for an empty
 * step-prop filter (PHP's json_decode(...,true) cannot tell an empty JSON object from an
 * empty JSON array, so json_encode guesses wrong) and kotlinx.serialization's strict
 * `Map<String, String>` rejects it outright, failing the whole `/funnels` response to decode.
 * [LenientStringMapSerializer] makes [FunnelStepDto.props] tolerant of that shape.
 */
class FunnelPropsSerializerTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun emptyArrayPropsDecodeToEmptyMap() {
        val step = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e","props":[]}""")
        assertEquals(emptyMap(), step.props)
    }

    @Test
    fun emptyObjectPropsDecodeToEmptyMap() {
        val step = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e","props":{}}""")
        assertEquals(emptyMap(), step.props)
    }

    @Test
    fun stringValuedPropsDecodeNormally() {
        val step = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e","props":{"referrer":"google"}}""")
        assertEquals(mapOf("referrer" to "google"), step.props)
    }

    @Test
    fun numericAndBooleanPropValuesAreCoercedToStrings() {
        val step = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e","props":{"tier":2,"ok":true}}""")
        assertEquals(mapOf("tier" to "2", "ok" to "true"), step.props)
    }

    @Test
    fun nullPropsDecodeToEmptyMap() {
        val step = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e","props":null}""")
        assertEquals(emptyMap(), step.props)
    }

    @Test
    fun absentPropsKeyDefaultsToEmptyMap() {
        val step = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e"}""")
        assertEquals(emptyMap(), step.props)
    }

    @Test
    fun encodingAlwaysEmitsAnObjectNeverAnArray() {
        // encodeDefaults mirrors ApiClient's real wire config (ApiClient.kt:33) — without it,
        // an empty default `props` map is omitted entirely rather than proving it round-trips
        // as `{}`.
        val encodingJson = Json { encodeDefaults = true }
        val encoded = encodingJson.encodeToString(FunnelStepDto(key = "a", event = "e"))
        assertTrue(encoded.contains("\"props\":{}"), "expected an empty-object props, got: $encoded")
    }

    @Test
    fun fullFunnelsResponseWithArrayPropsDecodesWithoutThrowing() {
        // The exact shape from the reported bug: $.funnels[0].steps[0].props was "[]".
        val response = json.decodeFromString<FunnelsResponse>(
            """{"funnels":[{"funnel_key":"onboarding","name":"Onboarding",
                "steps":[{"key":"open","event":"screen_view","screen":"shelves","props":[]},
                         {"key":"created","event":"shelf_created","props":{}}],
                "window_seconds":604800,"source":"sdk","locked":false}]}"""
        )
        assertEquals(1, response.funnels.size)
        assertEquals(2, response.funnels[0].steps.size)
        assertEquals(emptyMap(), response.funnels[0].steps[0].props)
        assertEquals(emptyMap(), response.funnels[0].steps[1].props)
    }
}
