import androidx.compose.runtime.Composable
import com.quietmetrix.analytics.*
import com.quietmetrix.analytics.compose.*

fun configurePublishedSdk() {
    QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "consumer_", trackingAllowedByDefault = true))
    setCookieConsent(false)
    check(!isTrackingAllowed())
    QuietMetrix.setAnalyticsEnabled(false)
    QuietMetrix.stop()
}

@Composable
fun PublishedExperimentPlacement() {
    ExperimentElement("login_banner", control = {}, content = {})
    ExperimentVariants("login_banner", variantA = {}, variantB = {})
}

fun main() { configurePublishedSdk() }
