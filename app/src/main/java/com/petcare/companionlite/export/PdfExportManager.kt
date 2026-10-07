package com.petcare.companionlite.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.petcare.companionlite.data.model.HealthLog
import com.petcare.companionlite.data.model.PetActivity
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.WeightPoint
import com.petcare.companionlite.data.model.toDateLabel
import com.petcare.companionlite.data.model.toDateTimeLabel
import java.io.File

/**
 * Generates a formatted PDF report for a single pet containing:
 * - Pet profile info
 * - Activity history
 * - Weight chart data (table + simple line chart)
 * - Health notes
 */
class PdfExportManager(private val context: Context) {

    private val pageWidth = 595  // A4 width in points
    private val pageHeight = 842 // A4 height in points
    private val marginLeft = 48f
    private val marginTop = 60f
    private val marginRight = 48f
    private val lineSpacing = 20f

    private val titlePaint = Paint().apply {
        color = Color.rgb(78, 122, 103) // DeepSage
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val sectionPaint = Paint().apply {
        color = Color.rgb(34, 49, 39) // Ink
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val bodyPaint = Paint().apply {
        color = Color.rgb(34, 49, 39) // Ink
        textSize = 11f
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }

    private val captionPaint = Paint().apply {
        color = Color.rgb(120, 120, 120)
        textSize = 9f
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.rgb(132, 181, 159) // Sage
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint().apply {
        color = Color.rgb(78, 122, 103) // DeepSage
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val separatorPaint = Paint().apply {
        color = Color.rgb(221, 239, 224) // DDEFe0
        strokeWidth = 1f
    }

    fun generateReport(
        pet: PetProfile,
        activities: List<PetActivity>,
        healthLogs: List<HealthLog>,
        weightPoints: List<WeightPoint>,
        streakDays: Int,
    ): Uri? {
        val document = PdfDocument()
        var pageNumber = 1
        var currentPage = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
        )
        var canvas = currentPage.canvas
        var y = marginTop

        fun ensureSpace(needed: Float): Canvas {
            if (y + needed > pageHeight - 60) {
                document.finishPage(currentPage)
                pageNumber++
                currentPage = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
                )
                y = marginTop
            }
            return currentPage.canvas
        }

        // ── Header ──────────────────────────────────────────────
        canvas.drawText("PetCare Companion Lite", marginLeft, y, titlePaint)
        y += 28f
        canvas.drawText("Pet Care Report", marginLeft, y, sectionPaint)
        y += 12f
        canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
        y += 24f

        // ── Pet Profile ─────────────────────────────────────────
        canvas.drawText("Pet Profile", marginLeft, y, sectionPaint)
        y += lineSpacing + 4f
        val profileLines = listOf(
            "Name: ${pet.name}",
            "Type: ${pet.type}",
            "Breed: ${pet.breed.ifBlank { "Not specified" }}",
            "Weight: ${pet.weightKg} kg",
            "Age: ${pet.ageYears} years",
            "Care Streak: $streakDays day${if (streakDays != 1) "s" else ""}",
        )
        profileLines.forEach { line ->
            canvas.drawText(line, marginLeft + 12f, y, bodyPaint)
            y += lineSpacing
        }
        y += 8f
        canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
        y += 20f

        // ── Weight Chart ────────────────────────────────────────
        if (weightPoints.size >= 2) {
            canvas = ensureSpace(180f)
            canvas.drawText("Weight Trend", marginLeft, y, sectionPaint)
            y += 24f

            val chartLeft = marginLeft + 40f
            val chartRight = pageWidth - marginRight
            val chartTop = y
            val chartBottom = y + 120f
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            val minWeight = weightPoints.minOf { it.value }
            val maxWeight = weightPoints.maxOf { it.value }
            val range = (maxWeight - minWeight).coerceAtLeast(0.1)

            // Y-axis labels
            canvas.drawText("${String.format("%.1f", maxWeight)} kg", marginLeft, chartTop + 10f, captionPaint)
            canvas.drawText("${String.format("%.1f", minWeight)} kg", marginLeft, chartBottom, captionPaint)

            // Draw lines
            val points = weightPoints.mapIndexed { index, point ->
                val x = chartLeft + (index.toFloat() / (weightPoints.size - 1)) * chartWidth
                val normalizedY = ((point.value - minWeight) / range).toFloat()
                val drawY = chartBottom - normalizedY * chartHeight
                x to drawY
            }

            for (i in 0 until points.size - 1) {
                canvas.drawLine(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second, linePaint)
            }
            points.forEach { (x, drawY) ->
                canvas.drawCircle(x, drawY, 3f, dotPaint)
            }

            y = chartBottom + 24f

            // Weight data table
            canvas = ensureSpace(20f + weightPoints.size * lineSpacing)
            canvas.drawText("Weight Log", marginLeft, y, sectionPaint)
            y += lineSpacing + 4f
            weightPoints.takeLast(20).forEach { point ->
                canvas.drawText(
                    "${point.timestamp.toDateLabel()}  —  ${String.format("%.1f", point.value)} kg",
                    marginLeft + 12f,
                    y,
                    bodyPaint,
                )
                y += lineSpacing
            }
            y += 8f
            canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
            y += 20f
        }

        // ── Activity History ────────────────────────────────────
        val recentActivities = activities.take(50)
        if (recentActivities.isNotEmpty()) {
            canvas = ensureSpace(40f)
            canvas.drawText("Activity History (Last ${recentActivities.size})", marginLeft, y, sectionPaint)
            y += lineSpacing + 4f

            recentActivities.forEach { activity ->
                canvas = ensureSpace(lineSpacing + 2f)
                val text = "${activity.type.label}  —  ${activity.timestamp.toDateTimeLabel()}"
                canvas.drawText(text, marginLeft + 12f, y, bodyPaint)
                y += lineSpacing
            }
            y += 8f
            canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
            y += 20f
        }

        // ── Health Notes ────────────────────────────────────────
        val notes = healthLogs.take(30)
        if (notes.isNotEmpty()) {
            canvas = ensureSpace(40f)
            canvas.drawText("Health Notes", marginLeft, y, sectionPaint)
            y += lineSpacing + 4f

            notes.forEach { log ->
                canvas = ensureSpace(lineSpacing * 2)
                val header = "${log.category.label}  —  ${log.timestamp.toDateTimeLabel()}"
                canvas.drawText(header, marginLeft + 12f, y, bodyPaint)
                y += lineSpacing
                val detail = buildString {
                    if (log.value != null) append("Value: ${log.value}  ")
                    append(log.note)
                }
                canvas.drawText(detail, marginLeft + 24f, y, captionPaint)
                y += lineSpacing
            }
            y += 8f
        }

        // ── Footer ──────────────────────────────────────────────
        canvas = ensureSpace(60f)
        y += 12f
        canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
        y += 20f
        canvas.drawText("Not a substitute for veterinary advice.", marginLeft, y, captionPaint)
        y += 14f
        canvas.drawText("Generated by PetCare Companion Lite", marginLeft, y, captionPaint)

        document.finishPage(currentPage)

        // Save to cache and return shareable URI
        return try {
            val dir = File(context.cacheDir, "reports")
            dir.mkdirs()
            val file = File(dir, "${pet.name.replace(" ", "_")}_report.pdf")
            file.outputStream().use { document.writeTo(it) }
            document.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            document.close()
            null
        }
    }

    fun shareReport(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Pet Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
