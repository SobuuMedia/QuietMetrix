package com.quietmetrix.server.ingest

import kotlinx.serialization.Serializable

@Serializable
data class IngestResult(
    val ok: Boolean = true,
    val queued: Int = 1,
)