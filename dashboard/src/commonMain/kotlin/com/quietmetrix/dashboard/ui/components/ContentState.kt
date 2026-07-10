package com.quietmetrix.dashboard.ui.components

/**
 * The three render states a data section can be in. Resolved by the pure
 * [contentStateFor] helper so the loading/empty/data decision is testable
 * without a Compose host.
 */
enum class ContentState { Loading, Empty, Data }

fun contentStateFor(loading: Boolean, isEmpty: Boolean): ContentState = when {
    loading && isEmpty -> ContentState.Loading
    isEmpty -> ContentState.Empty
    else -> ContentState.Data
}
