package com.quietmetrix.dashboard.ui

import com.quietmetrix.dashboard.api.ErrorKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every [ErrorKind] must have a friendly, localized `error_kind_<suffix>` string — the sentence
 * shown on screen instead of the raw exception text. [suffixByKind] is kept explicit (not
 * derived from the enum name) so adding a kind without adding its string fails loudly here,
 * rather than falling through to whatever default `when` branch a future edit might add.
 */
class ErrorStringsTest {

    private val suffixByKind = mapOf(
        ErrorKind.Network to "network",
        ErrorKind.SessionExpired to "session_expired",
        ErrorKind.Forbidden to "forbidden",
        ErrorKind.NotFound to "not_found",
        ErrorKind.Conflict to "conflict",
        ErrorKind.RateLimited to "rate_limited",
        ErrorKind.Invalid to "invalid",
        ErrorKind.ServerError to "server",
        ErrorKind.Incompatible to "incompatible",
        ErrorKind.Unknown to "unknown",
    )

    @Test
    fun everyErrorKindHasAnExplicitStringMapping() {
        assertEquals(ErrorKind.entries.toSet(), suffixByKind.keys, "add a suffix mapping for every ErrorKind")
    }

    @Test
    fun everyErrorKindStringExistsInDefaultAndSpanish() {
        val default = parseKeys(findFile("src/commonMain/composeResources/values/strings.xml"))
        val spanish = parseKeys(findFile("src/commonMain/composeResources/values-es/strings.xml"))
        val missing = suffixByKind.values.map { "error_kind_$it" }.filter { it !in default || it !in spanish }
        assertTrue(missing.isEmpty(), "missing error_kind_* strings: $missing")
    }

    @Test
    fun errorActionStringsExistInDefaultAndSpanish() {
        val default = parseKeys(findFile("src/commonMain/composeResources/values/strings.xml"))
        val spanish = parseKeys(findFile("src/commonMain/composeResources/values-es/strings.xml"))
        val required = listOf("action_copy_error_details", "snackbar_error_details_copied", "action_retry")
        val missing = required.filter { it !in default || it !in spanish }
        assertTrue(missing.isEmpty(), "missing error-action strings: $missing")
    }

    private fun parseKeys(file: File): Set<String> {
        val text = file.readText()
        val rx = Regex("""<string\s+name="([^"]+)"""")
        return rx.findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun findFile(relative: String): File {
        var f = File(relative)
        if (f.isFile) return f
        var cursor = File(".").absoluteFile
        repeat(6) {
            val candidate = File(cursor, "dashboard/$relative")
            if (candidate.isFile) return candidate
            cursor = cursor.parentFile ?: return@repeat
        }
        return File(relative).absoluteFile
    }
}
