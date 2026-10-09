package com.petcare.companionlite.data.ai

import com.petcare.companionlite.core.inAppLanguage

import com.petcare.companionlite.R

import com.petcare.companionlite.data.local.ChatDraftDao
import com.petcare.companionlite.data.local.ChatMessageDao
import com.petcare.companionlite.data.local.ChatSessionDao
import com.petcare.companionlite.data.model.ChatDraftEntity
import com.petcare.companionlite.data.model.ChatMessage
import com.petcare.companionlite.data.model.ChatMessageEntity
import com.petcare.companionlite.data.model.ChatSession
import com.petcare.companionlite.data.model.ChatSessionEntity
import com.petcare.companionlite.data.model.toDomain
import com.petcare.companionlite.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import org.json.JSONObject

// Orchestrates local-first chat: Room history + Worker generation.
// Server quota is authoritative; _quota mirrors the last server snapshot.
// Assistant answers are validated structured payloads, never raw model text.
class AiRepository(
    private val context: android.content.Context,
    private val api: AiApi,
    private val sessions: ChatSessionDao,
    private val messages: ChatMessageDao,
    private val drafts: ChatDraftDao,
    private val settings: SettingsStore,
    private val contextBuilder: AiContextBuilder,
) {
    private val dataEpoch = java.util.concurrent.atomic.AtomicLong()
    fun invalidatePendingResults() { dataEpoch.incrementAndGet(); _quota.value = null }
    private val _quota = MutableStateFlow<AiQuota?>(null)
    val quota: StateFlow<AiQuota?> = _quota.asStateFlow()

    private val _status = MutableStateFlow(AiStatus())
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    suspend fun refreshStatus() {
        _status.value = api.fetchStatus()
    }

    /**
     * Server-side Play verification; applies the verified tier locally.
     * Only ever GRANTS here. Revocation is owned by BillingManager's reconcile,
     * which clears flags when Play no longer lists the purchase (cancel/refund/
     * expiry) — so a flaky server response can never strand a paying user.
     */
    suspend fun verifyAndApplyPurchase(
        purchaseToken: String,
        subscriptionId: String = "ai_weekly_pass",
    ): Boolean {
        val installationId = settings.getOrCreateInstallationId()
        val epoch = dataEpoch.get()
        val result = api.verifyPurchase(installationId, subscriptionId, purchaseToken, settings.getLifetimeToken())
            ?: return false
        if (dataEpoch.get() != epoch || settings.getAiPurchaseToken() != purchaseToken) return false
        result.quota?.let { _quota.value = it }
        if (!result.verified || result.tier != "pro") return false
        return settings.applyVerifiedExpiry(purchaseToken, result.expiryMillis)
    }

    /** Re-apply a stored Play token so AI quota matches the local Pro plan. */
    suspend fun syncStoredPurchase() {
        val token = settings.getAiPurchaseToken()
        val lifetime = settings.getLifetimeToken()
        if (token == null) {
            if (lifetime != null) {
                api.verifyPurchase(settings.getOrCreateInstallationId(), null, null, lifetime)?.takeIf { it.verified }?.quota?.let { _quota.value = it }
            }
            return
        }
        val sku = settings.getAiSubscriptionId() ?: "ai_monthly_pass"
        verifyAndApplyPurchase(token, sku)
    }

    suspend fun awaitVerifiedReward():Boolean {
        val installation=settings.getOrCreateInstallationId()
        repeat(15) {
            val quota=api.quotaStatus(installation)
            quota?.let { _quota.value=it }
            if (quota?.bonusReady == true) return true
            kotlinx.coroutines.delay(2_000)
        }
        return false
    }

    fun observeSessions(petId: Long): Flow<List<ChatSession>> =
        sessions.observeForPet(petId).map { list -> list.map { it.toDomain() } }

    fun observeAllSessions(): Flow<List<ChatSession>> =
        sessions.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun clearAllChats() {
        invalidatePendingResults()
        drafts.deleteAll()
        messages.deleteAll()
        sessions.deleteAll()
    }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messages.observeForSession(sessionId).map { list -> list.map { it.toDomain() } }

    /** Reopen the latest active session for this pet; create one only if none exists. */
    suspend fun openLatestSession(petId: Long): Long {
        sessions.getLatestForPet(petId)?.let {
            recoverInterruptedMessages(it.id)
            return it.id
        }
        val now = System.currentTimeMillis()
        return sessions.insert(
            ChatSessionEntity(petId = petId, title = "", createdAt = now, updatedAt = now),
        )
    }

    suspend fun recoverInterruptedMessages(sessionId: Long) {
        messages.failInterruptedMessages(sessionId, System.currentTimeMillis() - 90_000L)
    }

    suspend fun newSession(petId: Long): Long {
        val now = System.currentTimeMillis()
        return sessions.insert(
            ChatSessionEntity(petId = petId, title = "", createdAt = now, updatedAt = now),
        )
    }

    suspend fun renameSession(sessionId: Long, title: String) {
        sessions.rename(sessionId, title.trim().take(60))
    }

    suspend fun deleteSession(sessionId: Long) {
        drafts.deleteDraft(sessionId)
        sessions.deleteById(sessionId)
    }

    suspend fun getDraft(sessionId: Long): String? = drafts.getDraft(sessionId)?.text

    suspend fun saveDraft(sessionId: Long, text: String) {
        if (text.isBlank()) {
            drafts.deleteDraft(sessionId)
        } else {
            drafts.saveDraft(ChatDraftEntity(sessionId, text.take(2000), System.currentTimeMillis()))
        }
    }

    suspend fun send(
        petId: Long,
        sessionId: Long,
        text: String,
        useBonus: Boolean = false,
        retryRequestId: String? = null,
        retryAssistantId: Long? = null,
    ): AiSendResult {
        val epoch = dataEpoch.get()
        val trimmed = text.trim().take(2000)
        if (trimmed.isEmpty()) return AiSendResult.Failed(context.inAppLanguage().getString(R.string.ui_c8032e9b51))
        val installationId = settings.getOrCreateInstallationId()
        val now = System.currentTimeMillis()
        // Reused across retries so the server never double-charges one question.
        val requestId = retryRequestId ?: UUID.randomUUID().toString()

        if (retryAssistantId == null) {
            messages.insert(
                ChatMessageEntity(
                    sessionId = sessionId, role = "user", content = trimmed,
                    createdAt = now, requestId = requestId,
                ),
            )
            sessions.touch(sessionId, now, trimmed.take(42))
            drafts.deleteDraft(sessionId)
        }

        val recent = messages.getRecent(sessionId, 8)
            .filter { it.requestId != requestId && it.status == "SENT" && it.content.isNotBlank() }
            .sortedBy { it.createdAt }
            .takeLast(6)
            .map {
                // Feed back only clean display text as conversation context.
                val clean = parseAiAnswer(it.structuredPayload)?.summary ?: it.content
                AiHistoryMessage(
                    role = if (it.role == "assistant") "assistant" else "user",
                    content = clean.take(800),
                )
            }
        val petContext = contextBuilder.build(petId)
        val prefs = settings.settings.first()
        val languageMode = prefs.aiLanguageMode.name
        val appLanguage = prefs.appLanguageTag ?: java.util.Locale.getDefault().toLanguageTag()
        val purchaseToken = settings.getAiPurchaseToken()
        val subscriptionId = settings.getAiSubscriptionId()

        // Placeholder assistant row, resolved to SENT or FAILED (never deleted).
        val assistantRowId = retryAssistantId ?: messages.insert(
            ChatMessageEntity(
                sessionId = sessionId, role = "assistant", content = "",
                createdAt = System.currentTimeMillis(), requestId = requestId,
                status = "SENDING",
            ),
        )
        if (retryAssistantId != null) {
            messages.updateMessageResult(retryAssistantId, "SENDING", "", null, null)
        }

        val out = api.chatStructured(
                installationId = installationId,
                petId = petId,
                message = trimmed,
                petContext = petContext,
                history = recent,
                requestId = requestId,
                useBonus = useBonus,
                languageMode = languageMode,
                appLanguage = appLanguage,
                purchaseToken = purchaseToken,
                subscriptionId = subscriptionId,
            )
        if (dataEpoch.get() != epoch) return AiSendResult.Failed(context.inAppLanguage().getString(R.string.ui_423380173a), "DATA_DELETED")
        return when (out) {
            is AiCallOutcome.Success -> {
                out.result.quota?.let { _quota.value = it }
                if (out.result.duplicate) {
                    messages.updateMessageResult(assistantRowId, "FAILED", "", null, "DUPLICATE")
                    AiSendResult.Failed(context.inAppLanguage().getString(R.string.ui_c59b6c9c51))
                } else {
                    val answer = out.result.answer
                    if (answer == null) {
                        messages.updateMessageResult(assistantRowId, "FAILED", "", null, "EMPTY_REPLY")
                        AiSendResult.Failed(context.inAppLanguage().getString(R.string.ui_ab02c70c11), "EMPTY_REPLY")
                    } else {
                        val payload = JSONObject().apply {
                            put("schemaVersion", 1)
                            put("language", answer.language)
                            put("title", answer.title)
                            put("summary", answer.summary)
                            put("urgency", answer.urgency)
                            put("sections", org.json.JSONArray(answer.sections.map { s ->
                                JSONObject().apply {
                                    put("type", s.type)
                                    put("title", s.title)
                                    put("items", org.json.JSONArray(s.items))
                                }
                            }))
                            put("followUps", org.json.JSONArray(answer.followUps))
                            put("contextNote", answer.contextNote)
                            put("disclaimer", answer.disclaimer)
                        }.toString()
                        messages.updateMessageResult(assistantRowId, "SENT", "", payload, null)
                        sessions.setPreview(sessionId, System.currentTimeMillis(), answer.title.take(80))
                        AiSendResult.Sent(out.result.requestId ?: requestId, answer, out.result.quota)
                    }
                }
            }
            is AiCallOutcome.QuotaExhausted -> {
                messages.updateMessageResult(assistantRowId, "FAILED", "", null, "QUOTA")
                out.quota?.let { _quota.value = it }
                AiSendResult.QuotaExhausted(out.quota)
            }
            is AiCallOutcome.Failure -> {
                messages.updateMessageResult(assistantRowId, "FAILED", "", null, out.errorCode)
                AiSendResult.Failed(out.message, out.errorCode)
            }
        }
    }

    suspend fun toggleSaved(messageId: Long, saved: Boolean) {
        messages.setSaved(messageId, saved)
    }

    suspend fun markFeedback(messageId: Long, helpful: Boolean) {
        messages.setReportState(messageId, if (helpful) "helpful" else "not_helpful")
    }

    suspend fun reportMessage(
        messageId: Long,
        requestId: String?,
        category: String,
        detail: String,
        excerpt: String,
    ): Boolean {
        val ok = api.report(
            installationId = settings.getOrCreateInstallationId(),
            requestId = requestId,
            category = category,
            detail = detail,
            excerpt = excerpt,
        )
        if (ok) messages.setReportState(messageId, "reported:$category")
        return ok
    }
}

fun ChatMessage.isAssistant(): Boolean = role == com.petcare.companionlite.data.model.ChatRole.ASSISTANT
