package com.quietmetrix.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.trackEvent

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        QuietMetrix.init(
            QuietMetrixConfig(
                storageKeyPrefix = "qmsample_",
                autoTrackInitialPageView = false
            )
        )

        trackEvent("page_view", "main_activity")

        val textView = TextView(this)
        textView.text = "Hello QuietMetrix"
        setContentView(textView)
    }
}
