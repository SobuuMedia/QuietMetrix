package com.quietmetrix.releaseproof

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.quietmetrix.analytics.*

/** Separate app ID and preferences; never changes the user's demo/sample app data. */
class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        QuietMetrix.init(QuietMetrixConfig(
            applicationContext = applicationContext,
            storageKeyPrefix = "release_proof_",
            trackingAllowedByDefault = intent.getBooleanExtra("default_allowed", false),
        ))
        when (intent.getStringExtra("proof_action")) {
            "grant" -> { QuietMetrix.setAnalyticsEnabled(true); setCookieConsent(true) }
            "revoke" -> { setCookieConsent(false); QuietMetrix.setAnalyticsEnabled(false) }
        }
        val result = "consent=${isTrackingAllowed()};enabled=${QuietMetrix.isAnalyticsEnabled};chosen=${hasCookieConsent()}"
        openFileOutput("proof.txt", MODE_PRIVATE).use { it.write(result.toByteArray()) }
        setContentView(TextView(this).apply { text = result })
    }
}
