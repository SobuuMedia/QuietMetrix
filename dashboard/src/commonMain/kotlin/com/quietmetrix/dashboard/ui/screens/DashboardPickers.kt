package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.handCursor
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProjectPicker(
    projects: List<ApiProject>,
    currentId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = projects.firstOrNull { it.id == currentId }
    val noProjects = stringResource(Res.string.picker_no_projects)
    val selectProject = stringResource(Res.string.picker_select_project)
    OutlinedButton(
        onClick = { expanded = true },
        enabled = projects.isNotEmpty(),
        modifier = Modifier.handCursor(),
    ) {
        Text(current?.name ?: if (projects.isEmpty()) noProjects else selectProject)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        projects.forEach { p ->
            DropdownMenuItem(
                text = { Text(p.name) },
                onClick = { onSelect(p.id); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

@Composable
internal fun WindowPicker(range: TimeRange, onSelect: (TimeRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.handCursor(),
    ) { Text(stringResource(range.labelRes)) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        TimeRange.entries.forEach { r ->
            DropdownMenuItem(
                text = { Text(stringResource(r.labelRes)) },
                onClick = { onSelect(r); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

@Composable
internal fun DemoBadge() {
    val ext = LocalExtendedColors.current
    Surface(color = ext.demoBadge, shape = RoundedCornerShape(999.dp)) {
        Text(
            stringResource(Res.string.badge_demo),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ext.onDemoBadge,
        )
    }
}
