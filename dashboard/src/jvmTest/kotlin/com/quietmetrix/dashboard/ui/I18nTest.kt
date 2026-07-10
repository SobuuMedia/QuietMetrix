package com.quietmetrix.dashboard.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class I18nTest {

    @Test
    fun everyDefaultStringHasSpanishTranslation() {
        val default = parseKeys(findFile("src/commonMain/composeResources/values/strings.xml"))
        val spanish = parseKeys(findFile("src/commonMain/composeResources/values-es/strings.xml"))
        assertTrue(default.isNotEmpty(), "default strings.xml has no keys — parse is broken")
        val missing = default.filter { it !in spanish }
        if (missing.isNotEmpty()) {
            fail("Missing Spanish translations for: $missing")
        }
    }

    @Test
    fun noHardcodedUserFacingLiteralsInCommonMain() {
        val srcRoot = findDir("src/commonMain/kotlin")
        val patterns = listOf(
            Regex("""Text\("([^"]*)""""),
            Regex("""contentDescription\s*=\s*"([^"]*)""""),
            Regex("""showSnackbar\("([^"]*)""""),
        )
        val offenders = mutableListOf<String>()
        walk(srcRoot) { f ->
            if (f.extension != "kt") return@walk
            val text = f.readText()
            patterns.forEach { rx ->
                rx.findAll(text).forEach { m ->
                    val literal = m.groupValues[1]
                    // ignore empty/whitespace-only placeholders (no longer present, but be safe)
                    if (literal.isNotBlank()) {
                        offenders += "${f.relativeTo(srcRoot)}: \"$literal\""
                    }
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Hardcoded user-facing literals found (lift to composeResources):\n${offenders.joinToString("\n")}",
        )
    }

    private fun parseKeys(file: File): Set<String> {
        val text = file.readText()
        val rx = Regex("""<string\s+name="([^"]+)"""")
        return rx.findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun walk(root: File, visit: (File) -> Unit) {
        root.walkTopDown().forEach { if (it.isFile) visit(it) }
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

    private fun findDir(relative: String): File {
        var d = File(relative)
        if (d.isDirectory) return d
        var cursor = File(".").absoluteFile
        repeat(6) {
            val candidate = File(cursor, "dashboard/$relative")
            if (candidate.isDirectory) return candidate
            cursor = cursor.parentFile ?: return@repeat
        }
        return File(relative).absoluteFile
    }
}
