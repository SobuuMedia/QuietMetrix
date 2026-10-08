package com.quietmetrix.dashboard.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_twemoji_1f308
import com.quietmetrix.dashboard.resources.ic_twemoji_1f30d
import com.quietmetrix.dashboard.resources.ic_twemoji_1f310
import com.quietmetrix.dashboard.resources.ic_twemoji_1f319
import com.quietmetrix.dashboard.resources.ic_twemoji_1f34e
import com.quietmetrix.dashboard.resources.ic_twemoji_1f37f
import com.quietmetrix.dashboard.resources.ic_twemoji_1f388
import com.quietmetrix.dashboard.resources.ic_twemoji_1f3c6
import com.quietmetrix.dashboard.resources.ic_twemoji_1f3e0
import com.quietmetrix.dashboard.resources.ic_twemoji_1f440
import com.quietmetrix.dashboard.resources.ic_twemoji_1f44b
import com.quietmetrix.dashboard.resources.ic_twemoji_1f465
import com.quietmetrix.dashboard.resources.ic_twemoji_1f48c
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4a4
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4cd
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4da
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4df
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4e6
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4f1
import com.quietmetrix.dashboard.resources.ic_twemoji_1f501
import com.quietmetrix.dashboard.resources.ic_twemoji_1f504
import com.quietmetrix.dashboard.resources.ic_twemoji_1f50d
import com.quietmetrix.dashboard.resources.ic_twemoji_1f511
import com.quietmetrix.dashboard.resources.ic_twemoji_1f517
import com.quietmetrix.dashboard.resources.ic_twemoji_1f525
import com.quietmetrix.dashboard.resources.ic_twemoji_1f5a5
import com.quietmetrix.dashboard.resources.ic_twemoji_1f5d1
import com.quietmetrix.dashboard.resources.ic_twemoji_1f622
import com.quietmetrix.dashboard.resources.ic_twemoji_1f642
import com.quietmetrix.dashboard.resources.ic_twemoji_1f680
import com.quietmetrix.dashboard.resources.ic_twemoji_1f7e2
import com.quietmetrix.dashboard.resources.ic_twemoji_1f913
import com.quietmetrix.dashboard.resources.ic_twemoji_1f916
import com.quietmetrix.dashboard.resources.ic_twemoji_1f9ea
import com.quietmetrix.dashboard.resources.ic_twemoji_1f9ed
import com.quietmetrix.dashboard.resources.ic_twemoji_1fa9c
import com.quietmetrix.dashboard.resources.ic_twemoji_2600
import com.quietmetrix.dashboard.resources.ic_twemoji_2696
import com.quietmetrix.dashboard.resources.ic_twemoji_2699
import com.quietmetrix.dashboard.resources.ic_twemoji_26a0
import com.quietmetrix.dashboard.resources.ic_twemoji_26aa
import com.quietmetrix.dashboard.resources.ic_twemoji_2708
import com.quietmetrix.dashboard.resources.ic_twemoji_2728
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Renders supported emoji-like UI glyphs from the vendored, full-color vector set. */
@Composable
fun EmojiVector(glyph: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val normalized = glyph.replace("\uFE0F", "")
    val resource = emojiResourceFor(normalized)
    Icon(painter = painterResource(resource), contentDescription = contentDescription,
        tint = Color.Unspecified, modifier = modifier)
}

fun emojiResourceFor(glyph: String): DrawableResource = emojiResources[glyph.replace("\uFE0F", "")] ?: Res.drawable.ic_twemoji_2728

fun screenGlyphFor(screen: String): String = when {
    "home" in screen.lowercase() -> "🏠"
    "scanner" in screen.lowercase() -> "📟"
    "search" in screen.lowercase() -> "🔍"
    "profile" in screen.lowercase() -> "👥"
    "reader" in screen.lowercase() || "book" in screen.lowercase() || "library" in screen.lowercase() -> "📚"
    "login" in screen.lowercase() || "sign" in screen.lowercase() -> "🔑"
    "setting" in screen.lowercase() -> "⚙️"
    else -> "📱"
}

private val emojiResources: Map<String, DrawableResource> = mapOf(
    "👋" to Res.drawable.ic_twemoji_1f44b,
    "💤" to Res.drawable.ic_twemoji_1f4a4,
    "🌈" to Res.drawable.ic_twemoji_1f308,
    "🌐" to Res.drawable.ic_twemoji_1f310,
    "🍎" to Res.drawable.ic_twemoji_1f34e,
    "📟" to Res.drawable.ic_twemoji_1f4df,
    "📦" to Res.drawable.ic_twemoji_1f4e6,
    "📱" to Res.drawable.ic_twemoji_1f4f1,
    "🖥" to Res.drawable.ic_twemoji_1f5a5,
    "🤖" to Res.drawable.ic_twemoji_1f916,
    "⚠" to Res.drawable.ic_twemoji_26a0,
    "⚪" to Res.drawable.ic_twemoji_26aa,
    "✈" to Res.drawable.ic_twemoji_2708,
    "🌍" to Res.drawable.ic_twemoji_1f30d,
    "🎈" to Res.drawable.ic_twemoji_1f388,
    "👀" to Res.drawable.ic_twemoji_1f440,
    "📍" to Res.drawable.ic_twemoji_1f4cd,
    "🔑" to Res.drawable.ic_twemoji_1f511,
    "🔍" to Res.drawable.ic_twemoji_1f50d,
    "🔥" to Res.drawable.ic_twemoji_1f525,
    "🚀" to Res.drawable.ic_twemoji_1f680,
    "🟢" to Res.drawable.ic_twemoji_1f7e2,
    "🧭" to Res.drawable.ic_twemoji_1f9ed,
    "⚖" to Res.drawable.ic_twemoji_2696,
    "🗑" to Res.drawable.ic_twemoji_1f5d1,
    "🏆" to Res.drawable.ic_twemoji_1f3c6,
    "🧪" to Res.drawable.ic_twemoji_1f9ea,
    "🔁" to Res.drawable.ic_twemoji_1f501,
    "🙂" to Res.drawable.ic_twemoji_1f642,
    "😢" to Res.drawable.ic_twemoji_1f622,
    "🪜" to Res.drawable.ic_twemoji_1fa9c,
    "📚" to Res.drawable.ic_twemoji_1f4da,
    "🔄" to Res.drawable.ic_twemoji_1f504,
    "✨" to Res.drawable.ic_twemoji_2728,
    "🍿" to Res.drawable.ic_twemoji_1f37f,
    "🏠" to Res.drawable.ic_twemoji_1f3e0,
    "👥" to Res.drawable.ic_twemoji_1f465,
    "💌" to Res.drawable.ic_twemoji_1f48c,
    "🔗" to Res.drawable.ic_twemoji_1f517,
    "⚙" to Res.drawable.ic_twemoji_2699,
    "🤓" to Res.drawable.ic_twemoji_1f913,
    "🌙" to Res.drawable.ic_twemoji_1f319,
    "☀" to Res.drawable.ic_twemoji_2600,
)
