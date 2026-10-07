package com.petcare.companionlite.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.FileProvider
import com.petcare.companionlite.data.model.WeeklyReport
import java.io.File
import java.io.FileOutputStream

object ShareHelper {
    fun shareWeeklyReport(context: Context, report: WeeklyReport) {
        val width = 800
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#4A6B56") // DeepSage
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Text paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0C4B1") // Sage variant
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }

        // Draw Content
        canvas.drawText("${report.petName}'s Weekly Report", width / 2f, 150f, titlePaint)

        var y = 300f
        val startX = 150f
        val lineSpacing = 80f

        canvas.drawText("${report.tasksCompleted} tasks completed", startX, y, textPaint)
        y += lineSpacing
        canvas.drawText("${report.bestStreak}-day streak", startX, y, textPaint)
        y += lineSpacing
        canvas.drawText("${report.daysActive} days active", startX, y, textPaint)

        // Branding
        canvas.drawText("Built with PetCare Companion Lite", width / 2f, height - 80f, brandPaint)

        // Save to cache and share
        try {
            val cachePath = File(context.cacheDir, "reports")
            cachePath.mkdirs()
            val file = File(cachePath, "weekly_report.png")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                // Fallback text
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${report.petName} Weekly Report\n${report.tasksCompleted} tasks completed\nBuilt with PetCare Companion Lite",
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
        } catch (e: Exception) {
            Log.e("ShareHelper", "Failed to share weekly report", e)
        }
    }
}
