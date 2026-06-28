package com.quietmetrix.dashboard.theme

import java.util.concurrent.ConcurrentHashMap

// JVM actual for the test-only target. Mirrors the wasmJs localStorage flow
// with a process-local map so unit tests can exercise save/load round-trips.
private val store = ConcurrentHashMap<String, String>()
private const val KEY = "qm_theme_mode"

actual fun saveThemeMode(mode: ThemeMode) {
    store[KEY] = mode.name
}

actual fun getThemeMode(): ThemeMode {
    val raw = store[KEY] ?: return ThemeMode.Dark
    return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.Dark)
}

actual fun resolveSystemThemeMode(): ThemeMode = ThemeMode.Dark
