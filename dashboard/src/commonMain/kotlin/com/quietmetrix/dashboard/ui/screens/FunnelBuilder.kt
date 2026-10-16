package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.CreateFunnelRequest
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelStepDto
import com.quietmetrix.dashboard.api.UiError
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_close
import com.quietmetrix.dashboard.resources.funnel_attempt_help
import com.quietmetrix.dashboard.resources.funnel_attempts
import com.quietmetrix.dashboard.resources.funnel_automatic
import com.quietmetrix.dashboard.resources.funnel_builder_add_step
import com.quietmetrix.dashboard.resources.funnel_builder_create
import com.quietmetrix.dashboard.resources.funnel_builder_description
import com.quietmetrix.dashboard.resources.funnel_builder_event
import com.quietmetrix.dashboard.resources.funnel_builder_intro
import com.quietmetrix.dashboard.resources.funnel_builder_key
import com.quietmetrix.dashboard.resources.funnel_builder_move_down
import com.quietmetrix.dashboard.resources.funnel_builder_move_up
import com.quietmetrix.dashboard.resources.funnel_builder_name
import com.quietmetrix.dashboard.resources.funnel_builder_remove_step
import com.quietmetrix.dashboard.resources.funnel_builder_screen_optional
import com.quietmetrix.dashboard.resources.funnel_builder_step_key
import com.quietmetrix.dashboard.resources.funnel_builder_step_name
import com.quietmetrix.dashboard.resources.funnel_builder_step_number
import com.quietmetrix.dashboard.resources.funnel_builder_title
import com.quietmetrix.dashboard.resources.funnel_correlation
import com.quietmetrix.dashboard.resources.funnel_counting
import com.quietmetrix.dashboard.resources.funnel_days
import com.quietmetrix.dashboard.resources.funnel_discard
import com.quietmetrix.dashboard.resources.funnel_discard_body
import com.quietmetrix.dashboard.resources.funnel_edit
import com.quietmetrix.dashboard.resources.funnel_editor_cancel
import com.quietmetrix.dashboard.resources.funnel_identity
import com.quietmetrix.dashboard.resources.funnel_install
import com.quietmetrix.dashboard.resources.funnel_once
import com.quietmetrix.dashboard.resources.funnel_one_day
import com.quietmetrix.dashboard.resources.funnel_one_hour
import com.quietmetrix.dashboard.resources.funnel_properties
import com.quietmetrix.dashboard.resources.funnel_properties_help
import com.quietmetrix.dashboard.resources.funnel_revision_warning
import com.quietmetrix.dashboard.resources.funnel_save
import com.quietmetrix.dashboard.resources.funnel_saving
import com.quietmetrix.dashboard.resources.funnel_session
import com.quietmetrix.dashboard.resources.funnel_view_definition
import com.quietmetrix.dashboard.resources.funnel_window
import com.quietmetrix.dashboard.ui.components.ConfirmDialog
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.ModalFooter
import com.quietmetrix.dashboard.ui.components.ModalFooterAction
import com.quietmetrix.dashboard.ui.components.ModalFooterEmphasis
import com.quietmetrix.dashboard.ui.components.ModalShell
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource

/** One editor for new, existing and immutable historical definitions. Drafts survive failed saves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewFunnelBuilderDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateFunnelRequest) -> Unit,
    initial: FunnelDto? = null,
    saving: Boolean = false,
    error: UiError? = null,
    catalog: Map<String, List<String>> = emptyMap(),
    readOnly: Boolean = false,
) {
    val bodyHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        .minus(240.dp).coerceIn(160.dp, 620.dp)
    var key by remember { mutableStateOf(initial?.funnelKey.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var windowSeconds by remember { mutableStateOf(initial?.windowSeconds ?: 604_800L) }
    var mode by remember { mutableStateOf(initial?.countMode ?: "actor") }
    var identity by remember { mutableStateOf(initial?.identityScope ?: "install") }
    var correlation by remember { mutableStateOf(initial?.correlationProperty.orEmpty()) }
    val steps = remember { mutableStateListOf<FunnelStepDraft>().apply {
        addAll(initial?.steps?.map { FunnelStepDraft(it.key, it.event, it.name.orEmpty(), it.screen.orEmpty(), Json.encodeToString(it.props)) }
            ?: listOf(FunnelStepDraft("step_1"), FunnelStepDraft("step_2")))
    } }
    var confirmDiscard by remember { mutableStateOf(false) }
    val draft = CreateFunnelRequest(funnelKey=key.trim(), name=name.trim(), description=description.trim().ifEmpty { null },
        steps=steps.map { FunnelStepDto(it.key, it.event.trim(), it.name.trim().ifEmpty { null }, it.screen.trim().ifEmpty { null }, parseFunnelProperties(it.props).orEmpty()) },
        windowSeconds=windowSeconds, countMode=mode, identityScope=identity, correlationProperty=correlation.trim().takeIf { mode == "attempt" && it.isNotEmpty() })
    val original = remember { draft }
    val dirty = draft != original || steps.any { parseFunnelProperties(it.props) == null }
    val valid = key.matches(Regex("^[a-z0-9_-]{1,64}$")) && name.trim().length in 1..80 &&
        steps.size in 2..10 && steps.all { it.key.matches(Regex("^[a-z0-9_-]{1,64}$")) && it.event.isNotBlank() && it.event.length <= 128 && parseFunnelProperties(it.props) != null } &&
        steps.map { it.key }.distinct().size == steps.size && windowSeconds in 60..7_776_000 &&
        (mode != "attempt" || correlation.matches(Regex("^[A-Za-z0-9_.-]{1,64}$")))
    val dismiss = { if (!saving) { if (!readOnly && dirty) confirmDiscard = true else onDismiss() } }
    ModalShell(
        title=stringResource(if (readOnly) Res.string.funnel_view_definition else if (initial != null) Res.string.funnel_edit else Res.string.funnel_builder_title),
        onDismiss=dismiss,
        footer={ ModalFooter(
            secondary=ModalFooterAction(stringResource(Res.string.funnel_editor_cancel), ModalFooterEmphasis.Secondary, dismiss),
            primary=ModalFooterAction(stringResource(if (readOnly) Res.string.action_close else if (saving) Res.string.funnel_saving else if (initial != null) Res.string.funnel_save else Res.string.funnel_builder_create), ModalFooterEmphasis.Primary) {
                if (readOnly) onDismiss() else if (valid && !saving) onCreate(draft)
            }, primaryEnabled=readOnly || valid && !saving,
        ) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max=bodyHeight).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            error?.let { InlineErrorNotice(it) }
            Text(stringResource(Res.string.funnel_builder_intro))
            if (initial != null && !readOnly) Text(stringResource(Res.string.funnel_revision_warning, initial.revision, initial.revision + 1))
            OutlinedTextField(key, { key=it.lowercase() }, label={ Text(stringResource(Res.string.funnel_builder_key)) }, enabled=initial==null && !saving && !readOnly, singleLine=true, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(name, { name=it.take(80) }, label={ Text(stringResource(Res.string.funnel_builder_name)) }, enabled=!saving && !readOnly, singleLine=true, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(description, { description=it }, label={ Text(stringResource(Res.string.funnel_builder_description)) }, enabled=!saving && !readOnly, modifier=Modifier.fillMaxWidth())
            Text(stringResource(Res.string.funnel_window))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(3600L,86400L,604800L,2592000L,7776000L).forEach { seconds ->
                    FilterChip(selected=windowSeconds==seconds, enabled=!saving && !readOnly, onClick={ windowSeconds=seconds }, label={ Text(if (seconds==3600L) stringResource(Res.string.funnel_one_hour) else if (seconds==86400L) stringResource(Res.string.funnel_one_day) else stringResource(Res.string.funnel_days, (seconds/86400).toInt())) })
                }
            }
            Text(stringResource(Res.string.funnel_counting))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("actor","attempt").forEach { value -> FilterChip(selected=mode==value, enabled=!saving && !readOnly, onClick={ mode=value }, label={ Text(stringResource(if (value=="actor") Res.string.funnel_once else Res.string.funnel_attempts)) }) }
            }
            Text(stringResource(Res.string.funnel_identity))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("install","session","install_or_session").forEach { value -> FilterChip(selected=identity==value, enabled=!saving && !readOnly, onClick={ identity=value }, label={ Text(stringResource(when(value) { "install"->Res.string.funnel_install; "session"->Res.string.funnel_session; else->Res.string.funnel_automatic })) }) }
            }
            if (mode=="attempt") {
                Text(stringResource(Res.string.funnel_attempt_help))
                OutlinedTextField(correlation, { correlation=it }, enabled=!saving && !readOnly, label={ Text(stringResource(Res.string.funnel_correlation)) }, modifier=Modifier.fillMaxWidth())
            }
            steps.forEachIndexed { index, step ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.funnel_builder_step_number,index+1))
                        if (!readOnly) Row {
                            TextButton(enabled=index>0 && !saving,onClick={ val item=steps.removeAt(index); steps.add(index-1,item) }) { Text(stringResource(Res.string.funnel_builder_move_up)) }
                            TextButton(enabled=index<steps.lastIndex && !saving,onClick={ val item=steps.removeAt(index); steps.add(index+1,item) }) { Text(stringResource(Res.string.funnel_builder_move_down)) }
                            TextButton(enabled=steps.size>2 && !saving,onClick={ steps.removeAt(index) }) { Text(stringResource(Res.string.funnel_builder_remove_step)) }
                        }
                        OutlinedTextField(step.key, { steps[index]=step.copy(key=it) }, enabled=!saving && !readOnly, label={ Text(stringResource(Res.string.funnel_builder_step_key)) }, modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(step.event, { steps[index]=step.copy(event=it) }, enabled=!saving && !readOnly, label={ Text(stringResource(Res.string.funnel_builder_event)) }, modifier=Modifier.fillMaxWidth().testTag("funnel-event-$index"))
                        if (!readOnly) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            catalog["events"].orEmpty().take(10).forEach { event -> SuggestionChip(onClick={ steps[index]=step.copy(event=event) }, enabled=!saving, label={ Text(event) }) }
                        }
                        OutlinedTextField(step.name, { steps[index]=step.copy(name=it) }, enabled=!saving && !readOnly, label={ Text(stringResource(Res.string.funnel_builder_step_name)) }, modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(step.screen, { steps[index]=step.copy(screen=it) }, enabled=!saving && !readOnly, label={ Text(stringResource(Res.string.funnel_builder_screen_optional)) }, modifier=Modifier.fillMaxWidth())
                        if (!readOnly) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            catalog["screens"].orEmpty().take(10).forEach { screen -> SuggestionChip(onClick={ steps[index]=step.copy(screen=screen) }, enabled=!saving, label={ Text(screen) }) }
                        }
                        OutlinedTextField(step.props, { steps[index]=step.copy(props=it) }, enabled=!saving && !readOnly, isError=parseFunnelProperties(step.props)==null, label={ Text(stringResource(Res.string.funnel_properties)) }, supportingText={ Text(stringResource(Res.string.funnel_properties_help)) }, modifier=Modifier.fillMaxWidth())
                    }
                }
            }
            if (!readOnly) OutlinedButton(enabled=steps.size<10 && !saving,onClick={ steps.add(FunnelStepDraft(nextFunnelStepKey(steps.map { it.key }.toSet()))) }) { Text(stringResource(Res.string.funnel_builder_add_step)) }
        }
    }
    if (confirmDiscard) ConfirmDialog("🗑️", stringResource(Res.string.funnel_discard), stringResource(Res.string.funnel_discard_body), stringResource(Res.string.funnel_discard), danger = true, onConfirm = { confirmDiscard=false; onDismiss() }, onDismiss = { confirmDiscard=false })
}
