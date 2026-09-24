package com.quietmetrix.analytics.internal.experiments

internal actual fun deviceCountry(): String? = normalizeRegion(java.util.Locale.getDefault()?.country)
