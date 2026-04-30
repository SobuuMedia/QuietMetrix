package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.QuietMetrixConfig

internal object ConfigHolder {
    private var _config: QuietMetrixConfig? = null

    val config: QuietMetrixConfig
        get() = _config ?: error("QuietMetrix.init(...) must be called before using analytics APIs")

    val configOrNull: QuietMetrixConfig? get() = _config

    val isInitialized: Boolean get() = _config != null

    fun set(config: QuietMetrixConfig) {
        _config = config
    }

    internal fun reset() {
        _config = null
    }
}
