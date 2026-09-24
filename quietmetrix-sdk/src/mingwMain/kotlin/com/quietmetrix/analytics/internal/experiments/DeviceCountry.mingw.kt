package com.quietmetrix.analytics.internal.experiments

/** No reliable OS-level geo/locale-region signal on this target without a GeoIP dependency
 *  this project doesn't have — country-targeted experiments simply don't enroll here. */
internal actual fun deviceCountry(): String? = null
