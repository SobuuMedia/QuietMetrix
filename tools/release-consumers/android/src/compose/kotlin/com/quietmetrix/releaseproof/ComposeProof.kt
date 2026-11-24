package com.quietmetrix.releaseproof

import androidx.compose.runtime.Composable
import com.quietmetrix.analytics.compose.ExperimentElement
import com.quietmetrix.analytics.compose.ExperimentVariants

/** Compile public composable calls without introducing UI, Activity Compose or tooling. */
@Composable
fun ComposeProof() {
    ExperimentElement("proof_element", control = {}) {}
    ExperimentVariants("proof_variants", variantA = {}, variantB = {})
}
