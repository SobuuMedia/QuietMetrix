package com.quietmetrix.analytics.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundCounterTest {

    @Test
    fun `fires background when the last started component stops`() {
        var backgrounds = 0
        val counter = ForegroundCounter { backgrounds++ }

        counter.onStart() // app foregrounded
        counter.onStop()  // app backgrounded
        assertEquals(1, backgrounds)
    }

    @Test
    fun `does not fire while another component is still started`() {
        var backgrounds = 0
        val counter = ForegroundCounter { backgrounds++ }

        counter.onStart()
        counter.onStart() // e.g. transition between two activities
        counter.onStop()
        assertEquals(0, backgrounds)
        counter.onStop()
        assertEquals(1, backgrounds)
    }

    @Test
    fun `stray stop does not drive the count negative or fire spuriously`() {
        var backgrounds = 0
        val counter = ForegroundCounter { backgrounds++ }

        counter.onStop()
        assertEquals(0, backgrounds)
        counter.onStart()
        counter.onStop()
        assertEquals(1, backgrounds)
    }
}
