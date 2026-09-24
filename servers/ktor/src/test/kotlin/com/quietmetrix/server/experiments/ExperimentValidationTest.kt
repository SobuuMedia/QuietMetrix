package com.quietmetrix.server.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentValidationTest {

    private fun valid(
        key: String = "checkout_cta",
        variants: List<VariantSpec> = listOf(VariantSpec("a", 50), VariantSpec("b", 50)),
        traffic: Int = 100,
        countries: List<String>? = null,
        screen: String? = "Checkout",
        name: String = "Checkout button colour",
    ) = ExperimentDefinition(key, name, screen, traffic, variants, countries)

    @Test
    fun `a well-formed definition has no errors`() {
        assertTrue(ExperimentValidation.validate(valid()).isEmpty())
    }

    @Test
    fun `an invalid key is rejected`() {
        assertTrue(ExperimentValidation.validate(valid(key = "Checkout CTA!")).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(key = "")).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(key = "a".repeat(33))).isNotEmpty())
    }

    @Test
    fun `a blank name is rejected`() {
        assertTrue(ExperimentValidation.validate(valid(name = "  ")).isNotEmpty())
    }

    @Test
    fun `traffic_percent 0 and 101 are both rejected, 1 and 100 are accepted`() {
        assertTrue(ExperimentValidation.validate(valid(traffic = 0)).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(traffic = 101)).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(traffic = 1)).isEmpty())
        assertTrue(ExperimentValidation.validate(valid(traffic = 100)).isEmpty())
    }

    @Test
    fun `exactly 2 variants are required`() {
        assertTrue(ExperimentValidation.validate(valid(variants = listOf(VariantSpec("a", 100)))).isNotEmpty())
        assertTrue(
            ExperimentValidation.validate(
                valid(variants = listOf(VariantSpec("a", 34), VariantSpec("b", 33), VariantSpec("c", 33))),
            ).isNotEmpty(),
        )
    }

    @Test
    fun `variant named none is rejected`() {
        assertTrue(ExperimentValidation.validate(valid(variants = listOf(VariantSpec("none", 50), VariantSpec("b", 50)))).isNotEmpty())
    }

    @Test
    fun `duplicate variant names are rejected`() {
        assertTrue(ExperimentValidation.validate(valid(variants = listOf(VariantSpec("a", 50), VariantSpec("a", 50)))).isNotEmpty())
    }

    @Test
    fun `weights of 0 and 100 are rejected, weights must sum to 100`() {
        assertTrue(ExperimentValidation.validate(valid(variants = listOf(VariantSpec("a", 0), VariantSpec("b", 100)))).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(variants = listOf(VariantSpec("a", 60), VariantSpec("b", 60)))).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(variants = listOf(VariantSpec("a", 20), VariantSpec("b", 79)))).isNotEmpty())
    }

    @Test
    fun `an out-of-range country code is rejected`() {
        assertTrue(ExperimentValidation.validate(valid(countries = listOf("ESP"))).isNotEmpty())
        assertTrue(ExperimentValidation.validate(valid(countries = listOf("e5"))).isNotEmpty())
    }

    @Test
    fun `more than 50 countries is rejected`() {
        val many = (1..51).map { "A" + ('A' + (it % 26)) }
        assertTrue(ExperimentValidation.validate(valid(countries = many)).isNotEmpty())
    }

    @Test
    fun `a valid country list of 2-letter codes is accepted`() {
        assertTrue(ExperimentValidation.validate(valid(countries = listOf("ES", "FR"))).isEmpty())
    }

    @Test
    fun `normalizeCountries uppercases trims dedupes sorts and treats empty as null`() {
        assertEquals(listOf("ES", "FR"), ExperimentValidation.normalizeCountries(listOf(" fr", "ES", "es", "FR ")))
        assertNull(ExperimentValidation.normalizeCountries(null))
        assertNull(ExperimentValidation.normalizeCountries(emptyList()))
        assertNull(ExperimentValidation.normalizeCountries(listOf("  ")))
    }
}
