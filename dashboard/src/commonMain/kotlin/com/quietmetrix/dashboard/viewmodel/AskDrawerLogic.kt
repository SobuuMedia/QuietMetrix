package com.quietmetrix.dashboard.viewmodel

import org.jetbrains.compose.resources.StringResource

/** Chat bubble kind for the Ask drawer. */
enum class AskMessageRole { User, Answer, Error }

/** One question or computed server response shown by the Ask drawer. */
data class AskMessage(val role: AskMessageRole, val text: String, val source: String? = null, val errorRes: StringResource? = null)

/** Reducer-owned Ask drawer state, including the currently pending retry question. */
data class AskDrawerState(
    val open: Boolean = false,
    val draft: String = "",
    val messages: List<AskMessage> = emptyList(),
    val loading: Boolean = false,
    val retryQuestion: String? = null,
)

/** Events for deterministic Ask drawer state transitions. */
internal sealed interface AskDrawerEvent {
    data class Open(val prefill: String? = null) : AskDrawerEvent
    data object Close : AskDrawerEvent
    data class Edit(val text: String) : AskDrawerEvent
    data class Submit(val question: String) : AskDrawerEvent
    data class Answer(val text: String, val source: String) : AskDrawerEvent
    data class Fail(val message: String, val errorRes: StringResource? = null) : AskDrawerEvent
    data object Retry : AskDrawerEvent
}

/** Pure state reducer for open/prefill/send/loading/answer/error/retry chat behavior. */
internal fun reduceAskDrawer(state: AskDrawerState, event: AskDrawerEvent): AskDrawerState = when (event) {
    is AskDrawerEvent.Open -> state.copy(open = true, draft = event.prefill ?: state.draft)
    AskDrawerEvent.Close -> state.copy(open = false)
    is AskDrawerEvent.Edit -> state.copy(draft = event.text)
    is AskDrawerEvent.Submit -> event.question.trim().takeIf { it.isNotEmpty() }?.let { question ->
        state.copy(draft = "", loading = true, retryQuestion = question,
            messages = state.messages + AskMessage(AskMessageRole.User, question))
    } ?: state
    is AskDrawerEvent.Answer -> state.copy(loading = false, retryQuestion = null,
        messages = state.messages + AskMessage(AskMessageRole.Answer, event.text, event.source))
    is AskDrawerEvent.Fail -> state.copy(loading = false,
        messages = state.messages + AskMessage(AskMessageRole.Error, event.message, errorRes = event.errorRes))
    AskDrawerEvent.Retry -> state.retryQuestion?.let { question ->
        state.copy(loading = true, messages = state.messages.filterNot { it.role == AskMessageRole.Error })
    } ?: state
}
