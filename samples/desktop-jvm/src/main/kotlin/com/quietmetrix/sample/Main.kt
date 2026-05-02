package com.quietmetrix.sample

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.trackEvent
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    QuietMetrix.init(
        QuietMetrixConfig(
            storageKeyPrefix = "qmjvm_",
            autoTrackInitialPageView = false
        )
    )

    trackEvent("page_view", "main")

    trackEvent("app_start", null, mapOf("version" to "1.0"))

    QuietMetrix.flush()

    println("Hello QuietMetrix")
}
