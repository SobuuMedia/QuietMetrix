package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.analytics_loading
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** Shared loading rows for first-load data sections. */
@Composable
internal fun SectionLoading() {
    val label = stringResource(Res.string.analytics_loading)
    Column(Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(32.dp)) }
    }
}
