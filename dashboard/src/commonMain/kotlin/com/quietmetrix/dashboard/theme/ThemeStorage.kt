package com.quietmetrix.dashboard.theme

// Persistence + system resolution for the theme mode. expect/actual so the
// wasmJs browser build can use localStorage + matchMedia while the JVM test
// target uses an in-memory fallback.

expect fun saveThemeMode(mode: ThemeMode)
expect fun getThemeMode(): ThemeMode
expect fun resolveSystemThemeMode(): ThemeMode

/** Watches system appearance changes; returns a function that removes the listener. */
expect fun observeSystemThemeMode(onChange: (ThemeMode) -> Unit): () -> Unit

internal fun parseThemeMode(raw: String?): ThemeMode =
    raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System
