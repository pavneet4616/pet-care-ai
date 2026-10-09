package com.petcare.companionlite.export

import com.petcare.companionlite.R
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.core.inAppLanguage
import com.petcare.companionlite.core.labelResource
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
        weightUnit: String = "kg",
    ): Uri? {
        val strings = context.inAppLanguage()
        val months = pet.ageMonths()
        val years = months?.div(12)?.toInt() ?: pet.ageYears.toIntOrNull()
        val age = if (months != null && months < 24) strings.resources.getQuantityString(R.plurals.age_months,months.toInt(),months)
            else years?.let { strings.resources.getQuantityString(R.plurals.age_years,it,it) } ?: strings.getString(R.string.age_unknown)
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

        fun paragraph(text: String, inset: Float = 12f, paint: Paint = bodyPaint) {
            val layout = android.text.StaticLayout.Builder.obtain(text,0,text.length,android.text.TextPaint(paint),(pageWidth-marginLeft-marginRight-inset).toInt()).build()
            for (line in 0 until layout.lineCount) {
                canvas = ensureSpace(lineSpacing)
                canvas.drawText(text.substring(layout.getLineStart(line),layout.getLineEnd(line)).trimEnd(),marginLeft+inset,y,paint)
                y += lineSpacing
            }
        }

        // ── Header ──────────────────────────────────────────────
        canvas.drawText(strings.getString(R.string.app_name), marginLeft, y, titlePaint)
        y += 28f
        canvas.drawText(strings.getString(R.string.report_title), marginLeft, y, sectionPaint)
        y += 12f
        canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
        y += 24f

        // ── Pet Profile ─────────────────────────────────────────
        canvas.drawText(strings.getString(R.string.report_profile), marginLeft, y, sectionPaint)
        y += lineSpacing + 4f
        val typeLabel = when(pet.petType) {
            com.petcare.companionlite.data.model.PetType.DOG -> R.string.pet_dog
            com.petcare.companionlite.data.model.PetType.CAT -> R.string.pet_cat
            com.petcare.companionlite.data.model.PetType.BIRD -> R.string.pet_bird
            com.petcare.companionlite.data.model.PetType.OTHER -> R.string.pet_other
        }
        val profileLines = listOf(
            strings.getString(R.string.report_name,pet.name),
            strings.getString(R.string.report_type,strings.getString(typeLabel)),
            strings.getString(R.string.report_breed,pet.breed.ifBlank { strings.getString(R.string.report_unknown) }),
            strings.getString(R.string.report_weight,pet.weightKg.toDoubleOrNull()?.let { CareFormats.weight(it,weightUnit) } ?: strings.getString(R.string.weight_unknown)),
            strings.getString(R.string.report_age,age),
            strings.getString(R.string.report_streak,streakDays),
        )
        profileLines.forEach { line ->
            paragraph(line)
        }
        y += 8f
        canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
        y += 20f

        // ── Weight Chart ────────────────────────────────────────
        if (weightPoints.size >= 2) {
            canvas = ensureSpace(180f)
            canvas.drawText(strings.getString(R.string.report_weight_trend), marginLeft, y, sectionPaint)
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
            canvas.drawText(CareFormats.weight(maxWeight,weightUnit), marginLeft, chartTop + 10f, captionPaint)
            canvas.drawText(CareFormats.weight(minWeight,weightUnit), marginLeft, chartBottom, captionPaint)

            // Draw lines
            val firstTime = weightPoints.minOf { it.timestamp }
            val timeRange = (weightPoints.maxOf { it.timestamp } - firstTime).coerceAtLeast(1L)
            val points = weightPoints.sortedBy { it.timestamp }.map { point ->
                val x = chartLeft + ((point.timestamp - firstTime).toDouble() / timeRange).toFloat() * chartWidth
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
            canvas.drawText(strings.getString(R.string.report_weight_log), marginLeft, y, sectionPaint)
            y += lineSpacing + 4f
            weightPoints.takeLast(20).forEach { point ->
                canvas.drawText(
                    "${point.timestamp.toDateLabel()}  —  ${CareFormats.weight(point.value,weightUnit)}",
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
            canvas.drawText(strings.getString(R.string.report_activity_history,recentActivities.size), marginLeft, y, sectionPaint)
            y += lineSpacing + 4f

            recentActivities.forEach { activity ->
                canvas = ensureSpace(lineSpacing + 2f)
                val text = "${strings.getString(activity.type.labelResource())}  —  ${activity.timestamp.toDateTimeLabel()}"
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
            canvas.drawText(strings.getString(R.string.report_health_notes), marginLeft, y, sectionPaint)
            y += lineSpacing + 4f

            notes.forEach { log ->
                canvas = ensureSpace(lineSpacing * 2)
                val header = "${strings.getString(log.category.labelResource())}  —  ${log.timestamp.toDateTimeLabel()}"
                canvas.drawText(header, marginLeft + 12f, y, bodyPaint)
                y += lineSpacing
                val detail = buildString {
                    if (log.value != null) append(strings.getString(R.string.report_value,log.value.toString()) + "  ")
                    append(log.note)
                }
                paragraph(detail,24f,captionPaint)
            }
            y += 8f
        }

        // ── Footer ──────────────────────────────────────────────
        canvas = ensureSpace(60f)
        y += 12f
        canvas.drawLine(marginLeft, y, pageWidth - marginRight, y, separatorPaint)
        y += 20f
        canvas.drawText(strings.getString(R.string.report_disclaimer), marginLeft, y, captionPaint)
        y += 14f
        canvas.drawText(strings.getString(R.string.report_brand), marginLeft, y, captionPaint)

        document.finishPage(currentPage)

        // Save to cache and return shareable URI
        return try {
            val dir = File(context.cacheDir, "reports")
            dir.mkdirs()
            val file = File(dir, "pet-report-${java.util.UUID.randomUUID()}.pdf")
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
        context.startActivity(Intent.createChooser(intent, context.inAppLanguage().getString(R.string.report_share)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
