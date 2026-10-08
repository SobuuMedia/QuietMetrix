package com.quietmetrix.analytics.internal.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.quietmetrix.analytics.currentAudienceCountryCode
import com.quietmetrix.analytics.updateAudience

class ExperimentV2AssignmentTest {
    private fun spec(audience: ExperimentV2AudienceDto = ExperimentV2AudienceDto()) = ExperimentV2ItemDto(
        key = "login_help", revision = 3, mode = "visibility", status = "active", placementKey = "login_form",
        bPercent = 25,
        variants = listOf(ExperimentV2VariantDto("a", "Control", 75), ExperimentV2VariantDto("b", "Help", 25)),
        audience = audience,
    )

    @Test fun `direct visibility is approximately 25 percent globally and within eligible audience`() {
        val global = spec()
        val allSeeds = (0 until 10_000).map { it.toString() }
        val globalB = allSeeds.count { experimentV2VariantForSeed(global, it) == "b" }
        assertTrue(globalB / 10_000.0 in 0.23..0.27, "global B share was ${globalB / 100.0}%")

        val french = spec(ExperimentV2AudienceDto(countries = listOf("FR"), languages = listOf("fr"), matchMode = "all"))
        val eligibleSeeds = allSeeds.filter { it.toInt() % 4 == 0 }
        val frenchB = eligibleSeeds.count { experimentV2VariantForSeed(french, it) == "b" }
        assertTrue(frenchB / eligibleSeeds.size.toDouble() in 0.23..0.27, "eligible B share was ${100.0 * frenchB / eligibleSeeds.size}%")
        val inOrder = eligibleSeeds.associateWith { experimentV2VariantForSeed(french, it) }
        val reordered = eligibleSeeds.reversed().associateWith { experimentV2VariantForSeed(french, it) }
        assertEquals(inOrder, reordered)
    }

    @Test fun `audience match all and any use only explicitly supplied country and language`() {
        val both = ExperimentV2AudienceDto(countries = listOf("FR"), languages = listOf("fr"), matchMode = "all")
        assertTrue(experimentAudienceMatches(both, "FR", "fr-CA"))
        assertFalse(experimentAudienceMatches(both, "FR", "en"))
        assertFalse(experimentAudienceMatches(both, null, "fr"))

        val any = both.copy(matchMode = "any")
        assertTrue(experimentAudienceMatches(any, "FR", "en"))
        assertTrue(experimentAudienceMatches(any, null, "fr"))
        assertFalse(experimentAudienceMatches(any, null, "en"))
    }

    @Test fun `country targeting remains unknown until the host app supplies it`() {
        updateAudience(null, null)
        assertNull(currentAudienceCountryCode())
        updateAudience("fr", "fr-CA")
        assertEquals("FR", currentAudienceCountryCode())
        updateAudience(null, null)
        assertNull(currentAudienceCountryCode())
    }
}
