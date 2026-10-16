package com.quietmetrix.dashboard.storage

import kotlinx.browser.window

actual object ObservationStorage {
    actual fun read(): String? = runCatching { window.localStorage.getItem("qm_observations") }.getOrNull()
    actual fun write(value: String) { runCatching { window.localStorage.setItem("qm_observations", value) } }
}
