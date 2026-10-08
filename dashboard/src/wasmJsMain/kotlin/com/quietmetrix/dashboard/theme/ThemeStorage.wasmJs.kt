package com.quietmetrix.dashboard.theme

import kotlinx.browser.window
import org.w3c.dom.events.Event

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
        parseThemeMode(window.localStorage.getItem(KEY))
    } catch (_: Throwable) {
        ThemeMode.System
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

actual fun observeSystemThemeMode(onChange: (ThemeMode) -> Unit): () -> Unit {
    val query = window.matchMedia("(prefers-color-scheme: dark)")
    val listener: (Event) -> Unit = { onChange(resolveSystemThemeMode()) }
    query.addEventListener("change", listener)
    onChange(resolveSystemThemeMode())
    return { query.removeEventListener("change", listener) }
}
