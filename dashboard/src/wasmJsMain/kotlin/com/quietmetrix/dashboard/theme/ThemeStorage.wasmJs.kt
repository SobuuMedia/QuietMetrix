package com.quietmetrix.dashboard.theme

import kotlinx.browser.window

private const val KEY = "qm_theme_mode"

actual fun saveThemeMode(mode: ThemeMode) {
    try {
        window.localStorage.setItem(KEY, mode.name)
    } catch (_: Throwable) {
        // localStorage may be unavailable (private mode); fall back silently.
    }
}

actual fun getThemeMode(): ThemeMode {
    return try {
        val raw = window.localStorage.getItem(KEY) ?: return ThemeMode.Dark
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.Dark)
    } catch (_: Throwable) {
        ThemeMode.Dark
    }
}

actual fun resolveSystemThemeMode(): ThemeMode {
    return try {
        if (window.matchMedia("(prefers-color-scheme: dark)").matches) ThemeMode.Dark
        else ThemeMode.Light
    } catch (_: Throwable) {
        ThemeMode.Dark
    }
}
