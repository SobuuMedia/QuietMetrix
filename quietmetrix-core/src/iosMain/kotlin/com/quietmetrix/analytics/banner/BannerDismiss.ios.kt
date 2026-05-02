package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun isBannerDismissedThisMonth(): Boolean {
    val key = StorageKeys.bannerDismissed(ConfigHolder.config.storageKeyPrefix)
    val stored = InMemoryStore.get(key) ?: return false
    return stored == currentMonthStamp()
}

@OptIn(ExperimentalTime::class)
actual fun markBannerDismissed() {
    val key = StorageKeys.bannerDismissed(ConfigHolder.config.storageKeyPrefix)
    InMemoryStore.set(key, currentMonthStamp())
}

@OptIn(ExperimentalTime::class)
private fun currentMonthStamp(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.year}-${now.monthNumber}"
}
