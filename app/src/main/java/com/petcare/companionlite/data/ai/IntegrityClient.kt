package com.petcare.companionlite.data.ai

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import com.google.android.gms.tasks.Task
import com.petcare.companionlite.data.settings.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class IntegrityClient(context: Context, private val settings: SettingsStore) {
    private val manager=IntegrityManagerFactory.createStandard(context.applicationContext)
    private val mutex=Mutex()
    private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider?=null
    suspend fun installationSecret()=settings.installationSecret()
    private suspend fun prepared()=mutex.withLock {
        provider ?: manager.prepareIntegrityToken(StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(138551758971L).build()).awaitResult().also { provider=it }
    }
    suspend fun headers(path:String,body:String): Map<String,String> = withTimeout(60_000) {
        val secret=settings.installationSecret()
        val digest=MessageDigest.getInstance("SHA-256").digest("POST\n$path\n$body\n$secret".toByteArray(Charsets.UTF_8))
        val hash=Base64.encodeToString(digest,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val request=StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(hash).build()
        val token=try { prepared().request(request).awaitResult().token() }
        catch(e:StandardIntegrityException) {
            if (e.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw e
            mutex.withLock { provider=null }
            prepared().request(request).awaitResult().token()
        }
        mapOf("x-play-integrity" to token,"x-installation-secret" to secret)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
