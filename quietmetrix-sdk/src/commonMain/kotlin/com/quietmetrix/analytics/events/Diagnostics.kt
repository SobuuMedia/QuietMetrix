package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.counters.MetricGateway

/** Records one explicit crash signal without collecting a stack trace or device identifier. */
suspend fun reportCrash() {
    if (Gate.shouldTrack()) MetricGateway.record("crash_v2", emptyMap())
}

/** Records one explicit handled-error signal without collecting message text or exception data. */
suspend fun reportError() {
    if (Gate.shouldTrack()) MetricGateway.record("error_v2", emptyMap())
}
