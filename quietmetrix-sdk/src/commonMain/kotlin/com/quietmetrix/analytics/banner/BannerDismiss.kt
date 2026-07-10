package com.quietmetrix.analytics

/** Returns true if the promo banner was dismissed during the current calendar month. */
expect fun isBannerDismissedThisMonth(): Boolean

/** Records that the user dismissed the promo banner this month. */
expect fun markBannerDismissed()
