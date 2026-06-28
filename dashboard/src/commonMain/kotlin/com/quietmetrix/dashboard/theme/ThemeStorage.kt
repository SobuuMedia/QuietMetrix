package com.quietmetrix.dashboard.theme

// Persistence + system resolution for the theme mode. expect/actual so the
// wasmJs browser build can use localStorage + matchMedia while the JVM test
// target uses an in-memory fallback.

expect fun saveThemeMode(mode: ThemeMode)
expect fun getThemeMode(): ThemeMode
expect fun resolveSystemThemeMode(): ThemeMode
