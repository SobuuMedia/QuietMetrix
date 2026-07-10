package com.quietmetrix.dashboard.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Scans every Kotlin source file under commonMain for `Res.drawable.<name>`
 * references and asserts that a matching `<name>.xml` exists in the drawable
 * resource directory. This is the long-term guard against the "icon renders as
 * a square" class of bug — a referenced-but-missing drawable shows tofu.
 */
class IconReferenceIntegrityTest {

    @Test
    fun everyReferencedDrawableHasAFile() {
        val srcRoot = findDir("src/commonMain/kotlin")
        val drawableDir = findDir("src/commonMain/composeResources/drawable")
        val pattern = Regex("Res\\.drawable\\.(\\w+)")
        val referenced = linkedSetOf<String>()
        walk(srcRoot) { f ->
            if (f.extension == "kt") {
                pattern.findAll(f.readText()).forEach { referenced += it.groupValues[1] }
            }
        }
        assertTrue(referenced.isNotEmpty(), "no Res.drawable.* references found — scan is misconfigured")
        val missing = referenced.mapNotNull { name ->
            if (File(drawableDir, "$name.xml").exists()) null else name
        }
        if (missing.isNotEmpty()) {
            fail("Source references drawables with no XML file: $missing (in $drawableDir)")
        }
    }

    private fun walk(root: File, visit: (File) -> Unit) {
        root.walkTopDown().forEach { if (it.isFile) visit(it) }
    }

    private fun findDir(relative: String): File {
        var dir = File(relative)
        if (dir.isDirectory) return dir
        var cursor = File(".").absoluteFile
        repeat(6) {
            val candidate = File(cursor, "dashboard/$relative")
            if (candidate.isDirectory) return candidate
            cursor = cursor.parentFile ?: return@repeat
        }
        return File(relative).absoluteFile
    }
}
