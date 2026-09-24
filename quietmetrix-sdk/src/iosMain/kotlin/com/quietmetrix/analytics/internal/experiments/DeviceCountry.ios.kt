package com.quietmetrix.analytics.internal.experiments

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

internal actual fun deviceCountry(): String? = normalizeRegion(NSLocale.currentLocale.countryCode)
