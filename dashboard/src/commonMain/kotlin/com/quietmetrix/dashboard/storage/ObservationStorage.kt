package com.quietmetrix.dashboard.storage

/** Local preferences only; no analytics or observation choices are sent to the server. */
expect object ObservationStorage {
    fun read(): String?
    fun write(value: String)
}
