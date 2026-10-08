package com.quietmetrix.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.compose.ExperimentElement
import com.quietmetrix.analytics.trackEvent
import com.quietmetrix.analytics.trackScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

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
                countryCode = "FR", // Supply only if your app already knows the user's country.
                languageTag = "fr",
                autoTrackInitialPageView = false
            )
        )

        runBlocking {
            trackScreen("main_activity")
            trackEvent("page_view", "main_activity")
        }

        setContent {
            val scope = rememberCoroutineScope()
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
                        ExperimentElement(
                            key = "login_message",
                            control = { Text("Welcome back") },
                            content = { Text("✨ Try the new welcome message") },
                        )
                        Button(onClick = { scope.launch { trackEvent("login_success") } }) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        QuietMetrix.stop()
    }
}
