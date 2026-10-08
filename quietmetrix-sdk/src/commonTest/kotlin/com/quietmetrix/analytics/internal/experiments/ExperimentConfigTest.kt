package com.quietmetrix.analytics.internal.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentConfigTest {

    /** Both rollout choices parse as the original pair and assign all devices to the selected variant. */
    @Test
    fun fullRolloutKeepsTwoVariantsAndAssignsEveryDevice() {
        for (selected in listOf("a", "b")) {
            val a = if (selected == "a") 100 else 0
            val b = 100 - a
            val spec = parseExperimentConfig("""{"experiments":[{"key":"cta","traffic_percent":100,"variants":[{"name":"a","weight":$a},{"name":"b","weight":$b}],"countries":null}]}""").getValue("cta")
            for (id in 0..1000) for (country in listOf(null, "ES", "US")) {
                assertEquals(selected, VariantAssigner.assign(spec, "device-$id", country))
            }
        }
    }

    private val wellFormed = """
        {"experiments":[
          {"key":"checkout_cta","traffic_percent":100,
           "variants":[{"name":"a","weight":50},{"name":"b","weight":50}],
           "countries":["ES","FR"]}
        ]}
    """.trimIndent()

    @Test
    fun parseExperimentConfig_wellFormedJson_parsesTheSpec() {
        val parsed = parseExperimentConfig(wellFormed)
        assertEquals(1, parsed.size)
        val spec = parsed.getValue("checkout_cta")
        assertEquals(100, spec.trafficPercent)
        assertEquals(listOf(VariantWeightDto("a", 50), VariantWeightDto("b", 50)), spec.variants)
        assertEquals(listOf("ES", "FR"), spec.countries)
    }

    @Test
    fun parseExperimentConfig_countriesOmitted_isNull() {
        val json = """{"experiments":[{"key":"e","traffic_percent":100,"variants":[{"name":"a","weight":50},{"name":"b","weight":50}]}]}"""
        val spec = parseExperimentConfig(json).getValue("e")
        assertEquals(null, spec.countries)
    }

    @Test
    fun parseExperimentConfig_malformedJson_returnsEmptyMap() {
        assertTrue(parseExperimentConfig("not json at all").isEmpty())
        assertTrue(parseExperimentConfig("").isEmpty())
    }

    @Test
    fun parseExperimentConfig_unknownFields_areIgnored() {
        val json = """{"experiments":[{"key":"e","traffic_percent":100,
           "variants":[{"name":"a","weight":50},{"name":"b","weight":50}],
           "name":"Some display name","screen":"Checkout","unexpected_field":123}]}"""
        val parsed = parseExperimentConfig(json)
        assertEquals(1, parsed.size)
        assertTrue(parsed.containsKey("e"))
    }

    @Test
    fun parseExperimentConfig_entryWithWrongVariantCount_isDropped() {
        val json = """{"experiments":[{"key":"e","traffic_percent":100,"variants":[{"name":"a","weight":100}]}]}"""
        assertTrue(parseExperimentConfig(json).isEmpty())
    }

    @Test
    fun parseExperimentConfig_emptyExperimentsList_isValid() {
        assertTrue(parseExperimentConfig("""{"experiments":[]}""").isEmpty())
    }

    @Test
    fun isValidExperimentConfigJson_wellFormed_isTrue() {
        assertTrue(isValidExperimentConfigJson(wellFormed))
        assertTrue(isValidExperimentConfigJson("""{"experiments":[]}"""))
    }

    @Test
    fun isValidExperimentConfigJson_malformed_isFalse() {
        assertFalse(isValidExperimentConfigJson("not json"))
    }
}
