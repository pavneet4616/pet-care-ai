package com.petcare.companionlite.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Context.petPhotoModel(value: String?): Any? =
    if (value?.matches(Regex("pets/[a-f0-9-]+\\.jpg")) == true) File(filesDir, value) else value

class PetPhotoStore(private val context: Context) {
    suspend fun importPhoto(value: String?): String? = withContext(Dispatchers.IO) {
        if (value == null || value.matches(Regex("pets/[a-f0-9-]+\\.jpg"))) return@withContext value
        val uri = Uri.parse(value)
        require(uri.scheme == "content") { "Select a photo from your device" }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Photo cannot be read" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        var bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Photo cannot be decoded")
        try {
            val orientation = resolver.openInputStream(uri).use { stream ->
                stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
            val ratio = minOf(1f, 1024f / maxOf(bitmap.width, bitmap.height))
            val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
            if (scaled !== bitmap) { bitmap.recycle(); bitmap = scaled }
            val relative = "pets/${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, relative)
            file.parentFile?.mkdirs()
            try {
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            } catch (e: Exception) { file.delete(); throw e }
            // Relative paths survive restore into a different sandbox. Re-encoding drops EXIF location.
            relative
        } finally { bitmap.recycle() }
    }

    fun delete(value: String?) {
        if (value?.matches(Regex("pets/[a-f0-9-]+\\.jpg")) == true) File(context.filesDir, value).delete()
    }

    fun clear() { File(context.filesDir, "pets").listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
}
