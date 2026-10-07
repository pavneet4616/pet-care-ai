package com.petcare.companionlite.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AiStructuredResult(
    val requestId: String?,
    val answer: AiAnswer?,
    val quota: AiQuota?,
    val duplicate: Boolean = false,
)

sealed interface AiCallOutcome {
    data class Success(val result: AiStructuredResult) : AiCallOutcome
    data class QuotaExhausted(val quota: AiQuota?) : AiCallOutcome
    data class Failure(val message: String, val errorCode: String? = null) : AiCallOutcome
}

// OkHttp client for the Pet Companion AI Worker.
// v1 structured contract: single non-streaming JSON answer, validated server-side.
// Android renders explicit answer fields and never displays raw model output.
class AiApi(
    private val baseUrl: String = AI_BASE_URL_DEFAULT,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun chatStructured(
        installationId: String,
        petId: Long,
        message: String,
        petContext: AiPetContext,
        history: List<AiHistoryMessage>,
        requestId: String = UUID.randomUUID().toString(),
        useBonus: Boolean = false,
        languageMode: String = "AUTO",
        appLanguage: String = Locale.getDefault().toLanguageTag(),
        purchaseToken: String? = null,
        subscriptionId: String? = null,
    ): AiCallOutcome = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("requestId", requestId)
            .put("installationId", installationId)
            .put("petId", petId)
            .put("message", message)
            .put("answerFormat", "structured")
            .put("langHint", appLanguage)
            .put("languageMode", languageMode)
            .put("appLanguage", appLanguage)
            .put("petContext", aiContextJson(petContext))
            .put("recentMessages", JSONArray(history.map {
                JSONObject().put("role", it.role).put("content", it.content)
            }))
        if (useBonus) payload.put("useBonus", true)
        if (!purchaseToken.isNullOrBlank()) payload.put("purchaseToken", purchaseToken)
        if (!subscriptionId.isNullOrBlank()) payload.put("subscriptionId", subscriptionId)

        val req = Request.Builder()
            .url("$baseUrl/v1/chat")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("x-request-id", requestId)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (res.code == 429) {
                    val quota = runCatching { parseQuota(JSONObject(raw)) }.getOrNull()
                    return@withContext AiCallOutcome.QuotaExhausted(quota)
                }
                if (!res.isSuccessful) {
                    Log.w(TAG, "chat HTTP ${res.code}")
                    return@withContext AiCallOutcome.Failure(
                        "AI service error (${res.code}). Your question was kept — tap Retry.",
                        "HTTP_${res.code}",
                    )
                }
                val json = runCatching { JSONObject(raw) }.getOrElse {
                    Log.w(TAG, "chat BAD_RESPONSE len=${raw.length}")
                    return@withContext AiCallOutcome.Failure(
                        "Unexpected AI response. Your question was kept — tap Retry.",
                        "BAD_RESPONSE",
                    )
                }
                if (json.optBoolean("duplicate", false)) {
                    val quota = parseQuotaOrNull(json.optJSONObject("quota"))
                    return@withContext AiCallOutcome.Success(
                        AiStructuredResult(requestId, null, quota, duplicate = true),
                    )
                }
                // Legacy shape (old server): { requestId, reply, quota }.
                if (!json.optBoolean("ok", false) && json.has("reply")) {
                    val quota = parseQuotaOrNull(json.optJSONObject("quota"))
                    val answer = AiAnswer(
                        title = "",
                        summary = cleanAiText(json.optString("reply"), 1200),
                        urgency = "ROUTINE",
                    )
                    if (answer.summary.isEmpty()) {
                        return@withContext AiCallOutcome.Failure(
                            "Empty reply. Your question was kept — tap Retry.",
                            "EMPTY_REPLY",
                        )
                    }
                    return@withContext AiCallOutcome.Success(AiStructuredResult(requestId, answer, quota))
                }
                val quota = parseQuotaOrNull(json.optJSONObject("quota"))
                val answer = parseAiAnswer(json.optJSONObject("answer")?.toString())
                if (answer == null) {
                    Log.w(TAG, "chat BAD_ANSWER")
                    return@withContext AiCallOutcome.Failure(
                        "Couldn't format that answer. Your question was kept — tap Retry.",
                        "BAD_ANSWER",
                    )
                }
                AiCallOutcome.Success(AiStructuredResult(requestId, answer, quota))
            }
        } catch (e: Exception) {
            Log.w(TAG, "chat network failure: ${e.javaClass.simpleName}")
            val offline = e is java.net.UnknownHostException || e is java.net.ConnectException ||
                e is java.net.SocketTimeoutException
            AiCallOutcome.Failure(
                if (offline) "You're offline. AI needs an internet connection — your question was kept."
                else (e.message ?: "Network error. Your question was kept — tap Retry."),
                if (offline) "OFFLINE" else "NETWORK",
            )
        }
    }

    suspend fun report(
        installationId: String,
        requestId: String?,
        category: String,
        detail: String,
        excerpt: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("installationId", installationId)
                .put("requestId", requestId)
                .put("category", category)
                .put("detail", detail.take(1000))
                .put("messageExcerpt", excerpt.take(1000))
            val req = Request.Builder()
                .url("$baseUrl/v1/chat/report")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    data class VerifyResult(
        val tier: String,
        val expiryMillis: Long,
        val quota: AiQuota?,
        val verified: Boolean = true,
    )

    suspend fun verifyPurchase(
        installationId: String,
        subscriptionId: String,
        purchaseToken: String,
    ): VerifyResult? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("installationId", installationId)
                .put("subscriptionId", subscriptionId)
                .put("purchaseToken", purchaseToken)
            val req = Request.Builder()
                .url("$baseUrl/v1/billing/google/verify")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "verify HTTP ${res.code}")
                    return@withContext null
                }
                val json = JSONObject(res.body?.string().orEmpty())
                val tier = json.optString("tier", "free")
                val verified = json.optBoolean("verified", json.has("tier"))
                Log.i(TAG, "verify tier=$tier verified=$verified")
                VerifyResult(
                    tier = tier,
                    expiryMillis = json.optLong("expiryTimeMillis", 0L),
                    quota = parseQuotaOrNull(json.optJSONObject("quota")),
                    verified = verified,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchStatus(): AiStatus = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/v1/ai/status").get().build()
            client.newCall(req).execute().use { res ->
                val json = JSONObject(res.body?.string().orEmpty())
                AiStatus(
                    aiEnabled = json.optBoolean("aiEnabled", true),
                    freeLimit = json.optInt("freeLimit", 5),
                    lifetimeLimit = json.optInt("lifetimeLimit", 10),
                    proLimit = json.optInt("proLimit", 20),
                    rewardedBonus = json.optInt("rewardedBonus", 1),
                )
            }
        } catch (_: Exception) {
            AiStatus()
        }
    }

    private fun aiContextJson(ctx: AiPetContext): JSONObject = JSONObject()
        .put("name", ctx.name)
        .put("species", ctx.species)
        .put("breed", ctx.breed)
        .put("ageYears", ctx.ageYears)
        .put("weightKg", ctx.weightKg)
        .put("summary7d", ctx.summary7d)
        .put("overdue", JSONArray(ctx.overdue))
        .put("recentEvents", JSONArray(ctx.recentEvents))

    private fun parseQuotaOrNull(obj: JSONObject?): AiQuota? =
        obj?.let { runCatching { parseQuota(it) }.getOrNull() }

    companion object {
        private const val TAG = "PetCareAi"
    }

    private fun parseQuota(wrapper: JSONObject): AiQuota {
        // Worker returns { quota: {...} } for 429, or the quota object itself.
        val q = if (wrapper.has("quota")) wrapper.getJSONObject("quota") else wrapper
        return AiQuota(
            tier = q.optString("tier", "free"),
            limit = q.optInt("limit", 5),
            used = q.optInt("used", 0),
            remaining = q.optInt("remaining", 0),
            bonusRemaining = q.optInt("bonusRemaining", 0),
            resetAt = q.optString("resetAt").takeIf { it.isNotEmpty() },
        )
    }
}
