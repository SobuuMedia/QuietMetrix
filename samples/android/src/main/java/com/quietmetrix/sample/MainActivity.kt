package com.quietmetrix.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.trackEvent
import com.quietmetrix.analytics.trackScreen
import kotlinx.coroutines.runBlocking

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wired up by an AI agent via `quietmetrix project create` — see
        // docs/agents/setup.md. trackingEndpoint/apiKey are safe to commit: the qm_ak_ key
        // is publishable and write-only (docs/security/publishable-api-key.md).
        QuietMetrix.init(
            QuietMetrixConfig(
                storageKeyPrefix = "qmsample_",
                trackingEndpoint = "https://your-server.com/api/v1/track",
                apiKey = "qm_ak_your_api_key",
                autoTrackInitialPageView = false
            )
        )

        runBlocking {
            trackScreen("main_activity")
            trackEvent("page_view", "main_activity")
        }

        val textView = TextView(this)
        textView.text = "Hello QuietMetrix"
        setContentView(textView)
    }

    override fun onDestroy() {
        super.onDestroy()
        QuietMetrix.stop()
    }
}
