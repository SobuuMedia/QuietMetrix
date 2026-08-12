package com.quietmetrix.dashboard.api

import kotlinx.serialization.SerializationException

/**
 * Broad category an error falls into, driving which friendly sentence the UI shows. Never
 * rendered directly — [com.quietmetrix.dashboard.ui.components.friendlyMessage] maps each
 * value to a localized string resource.
 */
enum class ErrorKind {
    Network, SessionExpired, Forbidden, NotFound, Conflict,
    RateLimited, Invalid, ServerError, Incompatible, Unknown,
}

/**
 * The dashboard's Compose-free error model. [kind] drives the friendly on-screen sentence;
 * [where] and [detail] are technical and only ever reach the clipboard via
 * [toClipboardReport] — never rendered on screen.
 */
data class UiError(
    val kind: ErrorKind,
    val where: String,
    val detail: String,
)

/** Classifies a caught exception and captures its technical detail for [where] it occurred. */
fun Throwable.toUiError(where: String): UiError =
    UiError(kind = classifyError(this), where = where, detail = errorDetail(this))

private fun classifyError(t: Throwable): ErrorKind = when (t) {
    is ApiException -> when (t.status) {
        401 -> ErrorKind.SessionExpired
        403 -> ErrorKind.Forbidden
        404 -> ErrorKind.NotFound
        409 -> ErrorKind.Conflict
        429 -> ErrorKind.RateLimited
        400 -> ErrorKind.Invalid
        in 500..599 -> ErrorKind.ServerError
        else -> ErrorKind.Unknown
    }
    is SerializationException -> ErrorKind.Incompatible
    // Any other exception (connection refused, DNS failure, timeout, ...) is treated the
    // same way login() already does for a non-ApiException failure.
    else -> ErrorKind.Network
}

private fun errorDetail(t: Throwable): String {
    val parts = mutableListOf(t::class.simpleName ?: "Throwable")
    if (t is ApiException) {
        parts += "status=${t.status}"
        t.code?.let { parts += "code=$it" }
        t.serverMessage?.let { parts += "server_message=$it" }
    }
    t.message?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" ")
}

/** The technical report copied to the clipboard when the user taps the copy-error action. */
fun UiError.toClipboardReport(nowIso: String): String = buildString {
    appendLine("QuietMetrix error report")
    appendLine("when: $nowIso")
    appendLine("where: $where")
    appendLine("kind: ${kind.name}")
    append("detail: $detail")
}
