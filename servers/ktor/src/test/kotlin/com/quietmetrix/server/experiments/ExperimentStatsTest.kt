package com.quietmetrix.server.experiments

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentStatsTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-4) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, got $actual")
    }

    @Test
    fun `100 of 1000 vs 130 of 1000 is significant`() {
        val result = ExperimentStats.twoProportionZTest(100, 1000, 130, 1000)!!
        assertClose(2.1027, result.z)
        assertClose(0.0355, result.pValue)
        assertTrue(result.significant)
    }

    @Test
    fun `50 of 500 vs 52 of 500 is not significant`() {
        val result = ExperimentStats.twoProportionZTest(50, 500, 52, 500)!!
        assertClose(0.2090, result.z)
        assertClose(0.8345, result.pValue)
        assertTrue(!result.significant)
    }

    @Test
    fun `200 of 1000 vs 260 of 1000 is significant`() {
        val result = ExperimentStats.twoProportionZTest(200, 1000, 260, 1000)!!
        assertClose(3.1881, result.z)
        assertClose(0.0014, result.pValue)
        assertTrue(result.significant)
    }

    @Test
    fun `0 of 100 vs 0 of 100 is null (zero standard error)`() {
        assertNull(ExperimentStats.twoProportionZTest(0, 100, 0, 100))
    }

    @Test
    fun `an empty sample is null`() {
        assertNull(ExperimentStats.twoProportionZTest(0, 0, 5, 100))
        assertNull(ExperimentStats.twoProportionZTest(5, 100, 0, 0))
    }
}
