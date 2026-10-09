package com.petcare.companionlite.core

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** Kept outside Android backup so restoring a phone never shares an installation credential. */
class InstallationIdentity(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ai-installation.json"))
    @Synchronized fun get(legacyId: String?, wasInitialized: Boolean): Pair<String, String> {
        if (file.baseFile.exists()) {
            val json=file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            return json.getString("id") to json.getString("secret")
        }
        val id=if (!wasInitialized && !legacyId.isNullOrBlank()) legacyId else UUID.randomUUID().toString()
        val bytes=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val stream=file.startWrite()
        try { stream.write(JSONObject().put("id",id).put("secret",secret).toString().toByteArray());file.finishWrite(stream) }
        catch(e:Exception) { file.failWrite(stream);throw e }
        return id to secret
    }
}
