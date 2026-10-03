package com.quietmetrix.dashboard.ui.components

import com.quietmetrix.dashboard.api.ExperimentDto
import com.quietmetrix.dashboard.api.VariantDto
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/** Checks generated SDK examples use the actual experiment key and variant names safely. */
class CodeSnippetPanelTest {
    @Test
    fun examplesUseConfiguredVariantNamesAndEscapeTheKey() {
        val experiment = ExperimentDto(
            key = "checkout\"v2",
            name = "Checkout",
            variants = listOf(VariantDto("old", 50), VariantDto("new", 50)),
        )
        for (language in SnippetLanguage.entries) {
            val code = experimentSnippet(language, experiment)
            assertContains(code, "checkout\\\"v2")
            assertContains(code, "new")
            assertContains(code, "old")
            assertContains(code, "trackExperimentInteraction")
            assertFalse("control" in code || "treatment" in code)
        }
    }
}
