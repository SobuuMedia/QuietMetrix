package com.quietmetrix.dashboard.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Task 1 gate — guarantees the curated icon inventory exists on disk so that
 * `Res.drawable.*` accessors can be generated and so that no icon call site
 * silently renders as a "tofu" square because the underlying XML is missing.
 */
class IconInventoryTest {

    @Test
    fun allExpectedIconsExist() {
        val dir = findDrawableDir()
        val expected = listOf(
            // navigation
            "ic_overview", "ic_events", "ic_flow", "ic_live", "ic_retention",
            "ic_projects", "ic_funnel", "ic_settings",
            // actions
            "ic_signout", "ic_close", "ic_refresh", "ic_delete",
            "ic_search", "ic_menu", "ic_chevron_down", "ic_plus",
            // theme
            "ic_sun", "ic_moon",
            // pre-existing
            "ic_info", "ic_copy",
        )
        val missing = expected.mapNotNull { name ->
            if (File(dir, "$name.xml").exists()) null else name
        }
        if (missing.isNotEmpty()) {
            fail("Missing drawable XML files: $missing (in $dir)")
        }
    }

    @Test
    fun everyDrawableIsAValidVector() {
        val dir = findDrawableDir()
        val files = dir.listFiles { f -> f.extension == "xml" } ?: emptyArray()
        assertTrue(files.isNotEmpty(), "drawable directory has no XML files: $dir")
        val invalid = files.filter { f ->
            val text = f.readText()
            !text.contains("<vector") || !text.contains("pathData")
        }
        assertTrue(invalid.isEmpty(), "Malformed vector drawable(s): ${invalid.map { it.name }}")
    }

    private fun findDrawableDir(): File {
        var dir = File("src/commonMain/composeResources/drawable")
        if (dir.isDirectory) return dir
        // Walk up from the test working directory to locate the dashboard module.
        var cursor = File(".").absoluteFile
        repeat(6) {
            val candidate = File(cursor, "dashboard/src/commonMain/composeResources/drawable")
            if (candidate.isDirectory) return candidate
            cursor = cursor.parentFile ?: return@repeat
        }
        // Fall back to the module-relative path (will fail the caller's checks).
        return File("src/commonMain/composeResources/drawable").absoluteFile
    }
}
