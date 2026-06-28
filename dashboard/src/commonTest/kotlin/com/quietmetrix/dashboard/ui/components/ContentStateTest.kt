package com.quietmetrix.dashboard.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentStateTest {

    @Test
    fun loadingAndEmptyShowsSkeleton() {
        assertEquals(ContentState.Loading, contentStateFor(loading = true, isEmpty = true))
    }

    @Test
    fun loadingWithDataShowsData() {
        assertEquals(ContentState.Data, contentStateFor(loading = true, isEmpty = false))
    }

    @Test
    fun notLoadingAndEmptyShowsEmptyState() {
        assertEquals(ContentState.Empty, contentStateFor(loading = false, isEmpty = true))
    }

    @Test
    fun notLoadingWithDataShowsData() {
        assertEquals(ContentState.Data, contentStateFor(loading = false, isEmpty = false))
    }
}
