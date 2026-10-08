package com.quietmetrix.dashboard.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.quietmetrix.dashboard.api.AiCapabilities

/** Every Ask entry point reads the same project entitlement. */
internal val LocalAiAccess = staticCompositionLocalOf { AiCapabilities() }
