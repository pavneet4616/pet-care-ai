package com.petcare.companionlite.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------- Wire models (match worker/src/types.ts) ----------

data class AiPetContext(
    val name: String? = null,
    val species: String? = null,
    val breed: String? = null,
    val ageYears: String? = null,
    val ageMonths: Long? = null,
    val weightKg: String? = null,
    val summary7d: String? = null,
    val overdue: List<String> = emptyList(),
    val recentEvents: List<String> = emptyList(),
)

data class AiHistoryMessage(
    val role: String, // "user" | "assistant"
    val content: String,
)

data class AiQuota(
    val tier: String = "free",
    val limit: Int = 5,
    val used: Int = 0,
    val remaining: Int = 5,
    val bonusRemaining: Int = 1,
    val bonusReady: Boolean = false,
    val resetAt: String? = null,
)

data class AiStatus(
    val aiEnabled: Boolean = true,
    val freeLimit: Int = 5,
    val lifetimeLimit: Int = 10,
    val proLimit: Int = 20,
    val rewardedBonus: Int = 1,
)

sealed interface AiSendResult {
    data class Sent(val requestId: String, val answer: AiAnswer?, val quota: AiQuota?) : AiSendResult
    data class QuotaExhausted(val quota: AiQuota?) : AiSendResult
    data class Failed(val message: String, val errorCode: String? = null) : AiSendResult
}

// ---------- Structured answers (Worker validates; app renders) ----------

data class AiAnswerSection(
    val type: String = "INFO",
    val title: String = "",
    val items: List<String> = emptyList(),
)

data class AiAnswer(
    val language: String = "en",
    val title: String = "",
    val summary: String = "",
    val urgency: String = "ROUTINE",
    val sections: List<AiAnswerSection> = emptyList(),
    val followUps: List<String> = emptyList(),
    val contextNote: String? = null,
    val disclaimer: String = "AI guidance only. A veterinarian should diagnose medical problems.",
)

/** Defensive sanitizer for any model-produced display string. */
fun cleanAiText(value: String?, maxLength: Int): String {
    if (value.isNullOrEmpty()) return ""
    var s = value.replace("**", "").replace("__", "")
    s = s.replace(Regex("^null[\\s.:-]*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("[ \\t]+"), " ")
    s = s.replace(Regex("\n{3,}"), "\n\n")
    s = s.trim()
    if (s.length > maxLength) s = s.take(maxLength).trim()
    return s
}

fun parseAiAnswer(json: String?): AiAnswer? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val o = org.json.JSONObject(json)
        val schemaVersion = o.opt("schemaVersion")?.toString()?.toIntOrNull() ?: -1
        if (schemaVersion != 1) return null
        // Graceful validation (mirrors worker): coerce drifted labels so one
        // off-spec section never blanks a whole saved answer.
        val validUrgencies = setOf("ROUTINE", "MONITOR", "VET_SOON", "URGENT")
        val urgency = o.optString("urgency", "ROUTINE").uppercase().trim()
            .takeIf { it in validUrgencies } ?: "ROUTINE"
        val validTypes = setOf("INFO", "ACTION", "WATCH", "VET")
        val sections = mutableListOf<AiAnswerSection>()
        val arr = o.optJSONArray("sections") ?: return null
        for (i in 0 until arr.length()) {
            if (sections.size >= 3) break
            val s = arr.optJSONObject(i) ?: continue
            val items = mutableListOf<String>()
            val itemsArr = s.optJSONArray("items") ?: continue
            for (j in 0 until minOf(itemsArr.length(), 5)) {
                cleanAiText(itemsArr.optString(j), 240).takeIf { it.isNotEmpty() }?.let { items.add(it) }
            }
            val title = cleanAiText(s.optString("title"), 80)
            val type = s.optString("type", "INFO").uppercase().trim()
                .takeIf { it in validTypes } ?: "INFO"
            if (title.isEmpty() || items.isEmpty()) continue
            sections.add(AiAnswerSection(type, title, items))
        }
        if (sections.isEmpty()) return null
        val followUps = mutableListOf<String>()
        val fu = o.optJSONArray("followUps")
        if (fu != null) for (i in 0 until minOf(fu.length(), 3)) {
            cleanAiText(fu.optString(i), 160).takeIf { it.isNotEmpty() }?.let { followUps.add(it) }
        }
        AiAnswer(
            language = o.optString("language", "en").takeIf { it.isNotBlank() } ?: "en",
            title = cleanAiText(o.optString("title"), 120).takeIf { it.isNotEmpty() } ?: return null,
            summary = cleanAiText(o.optString("summary"), 600).takeIf { it.isNotEmpty() } ?: return null,
            urgency = urgency,
            sections = sections,
            followUps = followUps,
            contextNote = cleanAiText(o.optString("contextNote"), 200).takeIf { it.isNotEmpty() },
            disclaimer = cleanAiText(o.optString("disclaimer"), 200)
                .takeIf { it.isNotEmpty() }
                ?: "AI guidance only. A veterinarian should diagnose medical problems.",
        )
    }.getOrNull()
}

/** UI-layer chat rows. Assistant rows render explicit AiAnswer fields only. */
sealed interface UiChatItem {
    data class User(val id: Long, val text: String, val time: Long) : UiChatItem
    data class Assistant(
        val id: Long,
        val answer: AiAnswer,
        val rawFallback: String,
        val time: Long,
        val requestId: String?,
        val isSaved: Boolean,
        val feedback: String?,
        val reported: Boolean,
        val status: String,
        val errorCode: String?,
    ) : UiChatItem
}

const val AI_BASE_URL_DEFAULT = "https://pet-companion-ai.neetpav46.workers.dev"
const val AI_DAILY_BONUS = 1
