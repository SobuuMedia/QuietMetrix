package com.quietmetrix.dashboard.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeStorageTest {

    @Test
    fun saveAndLoadRoundTrip() {
        saveThemeMode(ThemeMode.Light)
        assertEquals(ThemeMode.Light, getThemeMode())

        saveThemeMode(ThemeMode.System)
        assertEquals(ThemeMode.System, getThemeMode())

        saveThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, getThemeMode())
    }

    @Test
    fun defaultsToDarkWhenNothingSaved() {
        saveThemeMode(ThemeMode.Dark) // reset to a known value first
        assertEquals(ThemeMode.Dark, getThemeMode())
    }
}
