package com.quietmetrix.dashboard.api

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [UiError] is the Compose-free error model background loaders and user actions convert
 * exceptions into: a friendly-message [ErrorKind] the UI layer maps to a string resource, and
 * a technical [UiError.detail] that only ever reaches the clipboard, never the screen.
 */
class UiErrorTest {

    private fun apiException(status: Int, code: String? = null, serverMessage: String? = null) =
        ApiException(status = status, code = code, serverMessage = serverMessage)

    @Test
    fun sessionExpiredOn401() {
        assertEquals(ErrorKind.SessionExpired, apiException(401).toUiError("where").kind)
    }

    @Test
    fun forbiddenOn403() {
        assertEquals(ErrorKind.Forbidden, apiException(403).toUiError("where").kind)
    }

    @Test
    fun notFoundOn404() {
        assertEquals(ErrorKind.NotFound, apiException(404).toUiError("where").kind)
    }

    @Test
    fun conflictOn409() {
        assertEquals(ErrorKind.Conflict, apiException(409).toUiError("where").kind)
    }

    @Test
    fun rateLimitedOn429() {
        assertEquals(ErrorKind.RateLimited, apiException(429).toUiError("where").kind)
    }

    @Test
    fun invalidOn400() {
        assertEquals(ErrorKind.Invalid, apiException(400).toUiError("where").kind)
    }

    @Test
    fun serverErrorOn5xx() {
        assertEquals(ErrorKind.ServerError, apiException(500).toUiError("where").kind)
        assertEquals(ErrorKind.ServerError, apiException(503).toUiError("where").kind)
    }

    @Test
    fun incompatibleOnSerializationException() {
        val e = SerializationException("Unexpected JSON token at offset 177")
        assertEquals(ErrorKind.Incompatible, e.toUiError("where").kind)
    }

    @Test
    fun unclassifiedApiStatusIsUnknown() {
        assertEquals(ErrorKind.Unknown, apiException(418).toUiError("where").kind)
    }

    @Test
    fun anyOtherThrowableIsNetwork() {
        assertEquals(ErrorKind.Network, RuntimeException("connection refused").toUiError("where").kind)
    }

    @Test
    fun detailIsNeverBlankEvenForAMessagelessThrowable() {
        val detail = RuntimeException().toUiError("where").detail
        assertTrue(detail.isNotBlank())
    }

    @Test
    fun detailIncludesStatusCodeAndServerMessageForApiException() {
        val detail = apiException(409, code = "funnel_exists", serverMessage = "A funnel with this key already exists")
            .toUiError("where").detail
        assertTrue(detail.contains("409"), "expected status in detail: $detail")
        assertTrue(detail.contains("funnel_exists"), "expected code in detail: $detail")
        assertTrue(detail.contains("A funnel with this key already exists"), "expected server message in detail: $detail")
    }

    @Test
    fun clipboardReportContainsWhereAndDetail() {
        val error = apiException(500, code = "internal").toUiError("GET /projects/{id}/funnels")
        val report = error.toClipboardReport("2026-08-11T13:18:05Z")
        assertTrue(report.contains("GET /projects/{id}/funnels"))
        assertTrue(report.contains("2026-08-11T13:18:05Z"))
        assertTrue(report.contains(error.detail))
        assertTrue(report.contains("ServerError"))
    }
}
