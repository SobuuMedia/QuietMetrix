import androidx.compose.runtime.Composable
import com.quietmetrix.analytics.*
import com.quietmetrix.analytics.compose.*
import com.quietmetrix.analytics.debug.QuietMetrixDebugOverlay

fun configurePublishedSdk() {
    QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "consumer_", trackingAllowedByDefault = false))
    setCookieConsent(false)
    check(!isTrackingAllowed())
    QuietMetrix.setAnalyticsEnabled(false)
    QuietMetrix.stop()
}

@Composable
fun PublishedExperimentPlacement() {
    ExperimentElement("login_banner", control = {}, content = {})
    ExperimentVariants("login_banner", variantA = {}, variantB = {})
    QuietMetrixDebugOverlay()
}
