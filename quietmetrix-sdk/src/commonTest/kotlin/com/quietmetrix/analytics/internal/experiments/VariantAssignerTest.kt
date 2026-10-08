package com.quietmetrix.analytics.internal.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariantAssignerTest {

    private fun spec(
        key: String = "cta_color",
        traffic: Int = 100,
        variants: List<VariantWeightDto> = listOf(VariantWeightDto("a", 50), VariantWeightDto("b", 50)),
        countries: List<String>? = null,
    ) = ExperimentSpec(key, traffic, variants, countries)

    @Test
    fun assign_worked_example_bkt0001_evenSplit_isVariantA() {
        assertEquals("a", VariantAssigner.assign(spec(), "qm_bkt_0001", null))
    }

    @Test
    fun assign_worked_example_bkt0002_evenSplit_isVariantA() {
        assertEquals("a", VariantAssigner.assign(spec(), "qm_bkt_0002", null))
    }

    @Test
    fun assign_worked_example_bkt0002_skewedSplit_isVariantB() {
        val skewed = spec(variants = listOf(VariantWeightDto("a", 20), VariantWeightDto("b", 80)))
        assertEquals("b", VariantAssigner.assign(skewed, "qm_bkt_0002", null))
    }

    @Test
    fun assign_enrollmentThreshold_bkt0001_notEnrolledAtTraffic21() {
        // enroll bucket for bkt_0001 is 21 -> requires traffic_percent >= 22 to enroll.
        assertEquals(NONE_VARIANT, VariantAssigner.assign(spec(traffic = 21), "qm_bkt_0001", null))
    }

    @Test
    fun assign_enrollmentThreshold_bkt0001_enrolledAtTraffic22() {
        assertTrue(VariantAssigner.assign(spec(traffic = 22), "qm_bkt_0001", null) != NONE_VARIANT)
    }

    @Test
    fun assign_countryRequired_countryCodeNull_isNone() {
        val s = spec(countries = listOf("ES"))
        assertEquals(NONE_VARIANT, VariantAssigner.assign(s, "qm_bkt_0001", null))
    }

    @Test
    fun assign_countryRequired_countryCodeNotInList_isNone() {
        val s = spec(countries = listOf("ES"))
        assertEquals(NONE_VARIANT, VariantAssigner.assign(s, "qm_bkt_0001", "FR"))
    }

    @Test
    fun assign_countryRequired_countryCodeInList_proceedsNormally() {
        val s = spec(countries = listOf("ES"), traffic = 100)
        assertTrue(VariantAssigner.assign(s, "qm_bkt_0001", "ES") != NONE_VARIANT)
    }

    @Test
    fun assign_isDeterministic() {
        val s = spec()
        val first = VariantAssigner.assign(s, "qm_bkt_0001", null)
        val second = VariantAssigner.assign(s, "qm_bkt_0001", null)
        assertEquals(first, second)
    }

    @Test
    fun assign_distributionMatchesDeclaredWeights_withinTolerance() {
        val skewed = spec(variants = listOf(VariantWeightDto("a", 20), VariantWeightDto("b", 80)), traffic = 100)
        var aCount = 0
        val total = 10_000
        for (i in 0 until total) {
            if (VariantAssigner.assign(skewed, "qm_bkt_$i", null) == "a") aCount++
        }
        val fraction = aCount.toDouble() / total
        assertTrue(fraction in 0.17..0.23, "expected ~0.20, got $fraction")
    }

    @Test
    fun assign_enrolledShareMatchesTrafficPercent_withinTolerance() {
        val s = spec(traffic = 30)
        var enrolledCount = 0
        val total = 10_000
        for (i in 0 until total) {
            if (VariantAssigner.assign(s, "qm_bkt_$i", null) != NONE_VARIANT) enrolledCount++
        }
        val fraction = enrolledCount.toDouble() / total
        assertTrue(fraction in 0.27..0.33, "expected ~0.30, got $fraction")
    }

    @Test
    fun assign_noDegenerateAlwaysSameBucketBug() {
        val s = spec(variants = listOf(VariantWeightDto("a", 50), VariantWeightDto("b", 50)), traffic = 100)
        val results = (0 until 1000).map { VariantAssigner.assign(s, "qm_bkt_$it", null) }.toSet()
        assertTrue(results.size > 1, "expected both variants to appear across 1000 synthetic ids")
    }
}
