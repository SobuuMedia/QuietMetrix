package com.quietmetrix.dashboard.storage

actual object ObservationStorage {
    private var value: String? = null
    actual fun read(): String? = value
    actual fun write(value: String) { this.value = value }
}
