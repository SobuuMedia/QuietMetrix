package com.quietmetrix.sample

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.trackEvent
import com.quietmetrix.analytics.trackScreen
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    QuietMetrix.init(
        QuietMetrixConfig(
            storageKeyPrefix = "qmjvm_",
            autoTrackInitialPageView = false
        )
    )

    trackScreen("main")

    trackEvent("page_view", "main")

    trackEvent("app_start", null, mapOf("version" to "1.0"))

    QuietMetrix.stop()

    println("Hello QuietMetrix")
}
