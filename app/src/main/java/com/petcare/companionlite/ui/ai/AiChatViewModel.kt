package com.petcare.companionlite.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.companionlite.data.ai.AiQuota
import com.petcare.companionlite.data.ai.AiRepository
import com.petcare.companionlite.data.ai.AiSendResult
import com.petcare.companionlite.data.ai.UiChatItem
import com.petcare.companionlite.data.ai.isAssistant
import com.petcare.companionlite.data.ai.parseAiAnswer
import com.petcare.companionlite.data.model.ChatMessage
import com.petcare.companionlite.data.model.ChatSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiChatUiState(
    val input: String = "",
    val sending: Boolean = false,
    val quota: AiQuota? = null,
    val showPaywall: Boolean = false,
    val paywallReason: String = "",
    val error: String? = null,
    val reportTarget: ChatMessage? = null,
    val aiEnabled: Boolean = true,
    val sessionId: Long = 0L,
    val showHistory: Boolean = false,
    val renameTarget: ChatSession? = null,
)

class AiChatViewModel(
    private val petId: Long,
    private val repository: AiRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AiChatUiState())
    val ui: StateFlow<AiChatUiState> = _ui.asStateFlow()

    private val sessionIdFlow = MutableStateFlow(0L)
    private var pendingUseBonus: Boolean = false
    private var draftJob: Job? = null

    val sessions: StateFlow<List<ChatSession>> =
        repository.observeSessions(petId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<UiChatItem>> =
        sessionIdFlow.flatMapLatest { sid ->
            if (sid == 0L) flowOf(emptyList())
            else repository.observeMessages(sid)
        }.combine(sessionIdFlow) { messages, _ -> messages.mapNotNull { it.toUiItem() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val sid = repository.openLatestSession(petId)
            sessionIdFlow.value = sid
            _ui.value = _ui.value.copy(sessionId = sid)
            // Restore unsent draft so back-navigation never loses text.
            repository.getDraft(sid)?.let { draft ->
                if (draft.isNotBlank()) _ui.value = _ui.value.copy(input = draft)
            }
        }
        viewModelScope.launch {
            repository.syncStoredPurchase()
            repository.refreshStatus()
            _ui.value = _ui.value.copy(aiEnabled = repository.status.value.aiEnabled)
        }
        viewModelScope.launch {
            repository.quota.collect { quota ->
                quota?.let { _ui.value = _ui.value.copy(quota = it) }
            }
        }
    }

    fun prefill(question: String) {
        if (question.isNotBlank() && _ui.value.input.isBlank()) {
            _ui.value = _ui.value.copy(input = question)
        }
    }

    /** Follow-up chips pre-fill the composer without sending (saves quota). */
    fun prefillFollowUp(question: String) {
        _ui.value = _ui.value.copy(input = question, error = null)
        persistDraftDebounced(question)
    }

    fun onInputChange(value: String) {
        _ui.value = _ui.value.copy(input = value, error = null)
        persistDraftDebounced(value)
    }

    private fun persistDraftDebounced(value: String) {
        val sid = _ui.value.sessionId
        if (sid == 0L) return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(400)
            repository.saveDraft(sid, value)
        }
    }

    fun send(useBonus: Boolean = pendingUseBonus) {
        val text = _ui.value.input.trim()
        val sid = _ui.value.sessionId
        if (text.isEmpty() || _ui.value.sending || sid == 0L) return
        pendingUseBonus = false
        draftJob?.cancel()
        _ui.value = _ui.value.copy(sending = true, error = null, showPaywall = false)
        viewModelScope.launch {
            when (
                val result = repository.send(
                    petId = petId, sessionId = sid, text = text, useBonus = useBonus,
                )
            ) {
                is AiSendResult.Sent -> {
                    _ui.value = _ui.value.copy(
                        input = "", sending = false,
                        quota = result.quota ?: _ui.value.quota,
                    )
                }
                is AiSendResult.QuotaExhausted -> {
                    _ui.value = _ui.value.copy(
                        sending = false,
                        quota = result.quota ?: _ui.value.quota,
                        showPaywall = true,
                        paywallReason = "daily",
                    )
                }
                is AiSendResult.Failed -> {
                    _ui.value = _ui.value.copy(sending = false, error = result.message)
                }
            }
        }
    }

    /** Retry a failed assistant message with the SAME requestId (no double charge). */
    fun retryFailed(message: ChatMessage, questionText: String) {
        val sid = _ui.value.sessionId
        if (_ui.value.sending || sid == 0L || message.requestId == null) return
        _ui.value = _ui.value.copy(sending = true, error = null, showPaywall = false)
        viewModelScope.launch {
            when (
                val result = repository.send(
                    petId = petId, sessionId = sid, text = questionText,
                    retryRequestId = message.requestId, retryAssistantId = message.id,
                )
            ) {
                is AiSendResult.Sent -> {
                    _ui.value = _ui.value.copy(sending = false, quota = result.quota ?: _ui.value.quota)
                }
                is AiSendResult.QuotaExhausted -> {
                    _ui.value = _ui.value.copy(
                        sending = false,
                        quota = result.quota ?: _ui.value.quota,
                        showPaywall = true, paywallReason = "daily",
                    )
                }
                is AiSendResult.Failed -> {
                    _ui.value = _ui.value.copy(sending = false, error = result.message)
                }
            }
        }
    }

    fun retryWithBonus() {
        pendingUseBonus = true
        _ui.value = _ui.value.copy(showPaywall = false)
        send(useBonus = true)
    }

    fun dismissPaywall() {
        _ui.value = _ui.value.copy(showPaywall = false)
    }

    fun consumeError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun toggleSaved(message: ChatMessage) {
        viewModelScope.launch { repository.toggleSaved(message.id, !message.isSaved) }
    }

    fun markFeedback(message: ChatMessage, helpful: Boolean) {
        viewModelScope.launch { repository.markFeedback(message.id, helpful) }
    }

    fun openReport(message: ChatMessage) {
        _ui.value = _ui.value.copy(reportTarget = message)
    }

    fun closeReport() {
        _ui.value = _ui.value.copy(reportTarget = null)
    }

    fun submitReport(category: String, detail: String) {
        val target = _ui.value.reportTarget ?: return
        viewModelScope.launch {
            repository.reportMessage(
                messageId = target.id,
                requestId = target.requestId,
                category = category,
                detail = detail,
                excerpt = target.content.take(500),
            )
            _ui.value = _ui.value.copy(reportTarget = null)
        }
    }

    // ---- History ----
    fun openHistory() {
        _ui.value = _ui.value.copy(showHistory = true)
    }

    fun closeHistory() {
        _ui.value = _ui.value.copy(showHistory = false)
    }

    fun openSession(sessionId: Long) {
        if (sessionId == _ui.value.sessionId) {
            _ui.value = _ui.value.copy(showHistory = false)
            return
        }
        sessionIdFlow.value = sessionId
        viewModelScope.launch {
            val draft = repository.getDraft(sessionId).orEmpty()
            _ui.value = _ui.value.copy(sessionId = sessionId, input = draft, showHistory = false)
        }
    }

    fun newChat() {
        viewModelScope.launch {
            val sid = repository.newSession(petId)
            sessionIdFlow.value = sid
            _ui.value = _ui.value.copy(sessionId = sid, input = "", showHistory = false)
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            val remaining = sessions.value.filter { it.id != session.id }
            repository.deleteSession(session.id)
            if (session.id == _ui.value.sessionId) {
                val next = remaining.firstOrNull()?.id ?: repository.newSession(petId)
                sessionIdFlow.value = next
                _ui.value = _ui.value.copy(sessionId = next, input = "")
            }
        }
    }

    fun openRename(session: ChatSession) {
        _ui.value = _ui.value.copy(renameTarget = session)
    }

    fun closeRename() {
        _ui.value = _ui.value.copy(renameTarget = null)
    }

    fun submitRename(title: String) {
        val target = _ui.value.renameTarget ?: return
        if (title.isBlank()) {
            _ui.value = _ui.value.copy(renameTarget = null)
            return
        }
        viewModelScope.launch {
            repository.renameSession(target.id, title)
            _ui.value = _ui.value.copy(renameTarget = null)
        }
    }

    private fun ChatMessage.toUiItem(): UiChatItem? {
        return if (!isAssistant()) {
            if (content.isBlank()) return null
            UiChatItem.User(id, content, createdAt)
        } else {
            if (status == "SENDING") return null // thinking indicator covers this
            val answer = parseAiAnswer(structuredJson)
            if (answer != null) {
                UiChatItem.Assistant(
                    id = id, answer = answer, rawFallback = "",
                    time = createdAt, requestId = requestId, isSaved = isSaved,
                    feedback = reportState?.takeIf { it == "helpful" || it == "not_helpful" },
                    reported = reportState?.startsWith("reported:") == true,
                    status = status, errorCode = errorCode,
                )
            } else if (status == "FAILED") {
                UiChatItem.Assistant(
                    id = id,
                    answer = com.petcare.companionlite.data.ai.AiAnswer(
                        title = "", summary = "", urgency = "ROUTINE",
                    ),
                    rawFallback = "", time = createdAt, requestId = requestId,
                    isSaved = false, feedback = null, reported = false,
                    status = status, errorCode = errorCode,
                )
            } else {
                // Legacy plain-text rows (pre-structured builds): render sanitized.
                val clean = com.petcare.companionlite.data.ai.cleanAiText(content, 2000)
                if (clean.isEmpty()) return null
                UiChatItem.Assistant(
                    id = id,
                    answer = com.petcare.companionlite.data.ai.AiAnswer(
                        title = "", summary = clean, urgency = "ROUTINE",
                    ),
                    rawFallback = "", time = createdAt, requestId = requestId,
                    isSaved = isSaved,
                    feedback = reportState?.takeIf { it == "helpful" || it == "not_helpful" },
                    reported = reportState?.startsWith("reported:") == true,
                    status = status, errorCode = errorCode,
                )
            }
        }
    }
}
