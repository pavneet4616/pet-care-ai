package com.petcare.companionlite.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import com.petcare.companionlite.R
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.core.inAppLanguage
import com.petcare.companionlite.core.labelResource
import com.petcare.companionlite.data.model.HealthLog
import com.petcare.companionlite.data.model.PetActivity
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.WeightPoint
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Care report for one pet, written to the app cache and shared as "<PetName>-care-report-<yyyy-MM-dd>.pdf".
 * Sections: branded header, pet profile, weight trend and log, activity history, health notes.
 * Dates and times follow the app language and the device 12/24-hour setting.
 */
class PdfExportManager(private val context: Context) {

    private val ink = Color.rgb(34, 49, 39)          // Ink
    private val deepSage = Color.rgb(78, 122, 103)   // DeepSage
    private val sage = Color.rgb(132, 181, 159)      // Sage
    private val honey = Color.rgb(241, 194, 123)     // Honey
    private val mist = Color.rgb(245, 247, 244)      // Mist
    private val paleSage = Color.rgb(221, 239, 224)
    private val muted = Color.rgb(104, 118, 110)
    private val hairline = Color.rgb(224, 233, 227)

    private val brandPaint = textPaint(20f, Color.WHITE, bold = true)
    private val brandSubPaint = textPaint(10.5f, paleSage)
    private val petNamePaint = textPaint(22f, ink, bold = true)
    private val sectionPaint = textPaint(13f, deepSage, bold = true)
    private val bodyPaint = textPaint(10.5f, ink)
    private val bodyBoldPaint = textPaint(10f, ink, bold = true)
    private val captionPaint = textPaint(9f, muted)
    private val tableHeadPaint = textPaint(8.5f, muted, bold = true)

    private val bandPaint = fillPaint(deepSage)
    private val accentPaint = fillPaint(honey)
    private val panelPaint = fillPaint(mist)
    private val dotPaint = fillPaint(deepSage)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sage
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val hairlinePaint = Paint().apply {
        color = hairline
        strokeWidth = 0.8f
        style = Paint.Style.STROKE
    }

    fun generateReport(
        pet: PetProfile,
        activities: List<PetActivity>,
        healthLogs: List<HealthLog>,
        weightPoints: List<WeightPoint>,
        streakDays: Int,
        weightUnit: String = "kg",
    ): Uri? {
        val document = PdfDocument()
        return try {
            Report(document, context.inAppLanguage()).render(
                pet = pet,
                activities = activities,
                healthLogs = healthLogs,
                weightPoints = weightPoints,
                streakDays = streakDays,
                weightUnit = weightUnit,
            )
            val file = reportFile(pet)
            file.outputStream().use { document.writeTo(it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Could not build the care report", e)
            null
        } finally {
            document.close()
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

    /** Unique folder per export so the shared file keeps its readable name. */
    private fun reportFile(pet: PetProfile): File {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dir = File(context.cacheDir, "reports/${UUID.randomUUID()}").apply { mkdirs() }
        return File(dir, "${safeFileStem(pet.name)}-care-report-$date.pdf")
    }

    /** Letters, digits, hyphen and underscore only; accents are folded (Cafe, not Caf). */
    private fun safeFileStem(name: String): String {
        val ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ascii.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').take(40).ifEmpty { "pet" }
    }

    /** Lays out the report on A4 pages, starting a new page when the next block does not fit. */
    private inner class Report(
        private val document: PdfDocument,
        private val strings: Context,
    ) {
        private val resources = strings.resources
        private val locale: Locale = resources.configuration.locales.get(0) ?: Locale.getDefault()
        private val dateFormat = android.text.format.DateFormat.getMediumDateFormat(strings)
        private val timeFormat = android.text.format.DateFormat.getTimeFormat(strings)

        private var pageNumber = 0
        private var page: PdfDocument.Page = newPage()
        private var y = TOP_MARGIN

        private val canvas: Canvas get() = page.canvas

        fun render(
            pet: PetProfile,
            activities: List<PetActivity>,
            healthLogs: List<HealthLog>,
            weightPoints: List<WeightPoint>,
            streakDays: Int,
            weightUnit: String,
        ) {
            drawHeader(pet)
            drawProfile(pet, streakDays, weightUnit)
            if (weightPoints.size >= 2) {
                drawWeightTrend(weightPoints, weightUnit)
                drawWeightLog(weightPoints, weightUnit)
            }
            val recentActivities = activities.take(50)
            if (recentActivities.isNotEmpty()) drawActivityHistory(recentActivities)
            val notes = healthLogs.take(30)
            if (notes.isNotEmpty()) drawHealthNotes(notes)
            drawFooter()
            document.finishPage(page)
        }

        private fun newPage(): PdfDocument.Page {
            pageNumber++
            return document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        }

        /** Starts a new page when [height] no longer fits. Returns true when a page break happened. */
        private fun reserve(height: Float): Boolean {
            if (y + height <= PAGE_HEIGHT - BOTTOM_MARGIN) return false
            document.finishPage(page)
            page = newPage()
            y = TOP_MARGIN
            return true
        }

        private fun drawHeader(pet: PetProfile) {
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 88f, bandPaint)
            canvas.drawRect(0f, 88f, PAGE_WIDTH.toFloat(), 92f, accentPaint)
            canvas.drawText(strings.getString(R.string.app_name), MARGIN_X, 40f, brandPaint)
            canvas.drawText(strings.getString(R.string.report_title), MARGIN_X, 62f, brandSubPaint)

            y = 124f
            canvas.drawText(pet.name.ifBlank { strings.getString(R.string.report_title) }, MARGIN_X, y + 22f, petNamePaint)
            y += 34f
            line(strings.getString(R.string.shell_report_generated, dateFormat.format(Date())), captionPaint, height = 16f)
            y += 14f
        }

        private fun drawProfile(pet: PetProfile, streakDays: Int, weightUnit: String) {
            heading(strings.getString(R.string.report_profile))
            val today = LocalDate.now()
            val months = pet.ageMonths(today)
            val years = months?.div(12)?.toInt() ?: pet.ageYears.toIntOrNull()
            val age = when {
                months != null && months < 24 -> resources.getQuantityString(R.plurals.age_months, months.toInt(), months)
                years != null -> resources.getQuantityString(R.plurals.age_years, years, years)
                else -> strings.getString(R.string.age_unknown)
            }
            val typeLabel = strings.getString(
                when (pet.petType) {
                    PetType.DOG -> R.string.pet_dog
                    PetType.CAT -> R.string.pet_cat
                    PetType.BIRD -> R.string.pet_bird
                    PetType.OTHER -> R.string.pet_other
                },
            )
            val weight = pet.weightKg.toDoubleOrNull()?.let { CareFormats.weight(it, weightUnit) }
                ?: strings.getString(R.string.weight_unknown)
            val rows = listOf(
                strings.getString(R.string.report_name, pet.name),
                strings.getString(R.string.report_type, typeLabel),
                strings.getString(R.string.report_breed, pet.breed.ifBlank { strings.getString(R.string.report_unknown) }),
                strings.getString(R.string.report_weight, weight),
                strings.getString(R.string.report_age, age),
                resources.getQuantityString(R.plurals.shell_report_streak_days, streakDays, streakDays),
            )
            val wrapped = rows.flatMap { wrap(it, bodyPaint, PAGE_WIDTH - 2 * MARGIN_X - 28f) }
            val panelHeight = wrapped.size * LINE_HEIGHT + 20f
            reserve(panelHeight)
            canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + panelHeight), 14f, 14f, panelPaint)
            y += 10f
            wrapped.forEach { line(it, bodyPaint, x = MARGIN_X + 14f) }
            y += 20f
        }

        private fun drawWeightTrend(points: List<WeightPoint>, unit: String) {
            heading(strings.getString(R.string.report_weight_trend))
            val sorted = points.sortedBy { it.timestamp }
            val chartHeight = 110f
            reserve(chartHeight + 36f)
            val top = y + 12f
            val bottom = top + chartHeight
            val left = MARGIN_X + 52f
            val right = PAGE_WIDTH - MARGIN_X - 12f
            canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, bottom + 14f), 14f, 14f, panelPaint)

            val minWeight = sorted.minOf { it.value }
            val maxWeight = sorted.maxOf { it.value }
            val range = (maxWeight - minWeight).coerceAtLeast(0.1)
            val firstTime = sorted.first().timestamp
            val timeRange = (sorted.last().timestamp - firstTime).coerceAtLeast(1L)

            canvas.drawText(CareFormats.weight(maxWeight, unit), MARGIN_X + 12f, top + 4f, captionPaint)
            canvas.drawText(CareFormats.weight(minWeight, unit), MARGIN_X + 12f, bottom, captionPaint)

            val coords = sorted.map { point ->
                val x = left + ((point.timestamp - firstTime).toDouble() / timeRange).toFloat() * (right - left)
                val normalized = ((point.value - minWeight) / range).toFloat()
                x to (bottom - normalized * chartHeight)
            }
            coords.zipWithNext().forEach { (from, to) ->
                canvas.drawLine(from.first, from.second, to.first, to.second, linePaint)
            }
            coords.forEach { (x, cy) -> canvas.drawCircle(x, cy, 3.2f, dotPaint) }
            y = bottom + 30f
        }

        private fun drawWeightLog(points: List<WeightPoint>, unit: String) {
            heading(strings.getString(R.string.report_weight_log))
            val columns = listOf(
                TableColumn(strings.getString(R.string.shell_col_date), MARGIN_X, 300f),
                TableColumn(strings.getString(R.string.shell_col_weight), MARGIN_X + 300f, PAGE_WIDTH - 2 * MARGIN_X - 300f, alignEnd = true),
            )
            val rows = points.sortedBy { it.timestamp }.takeLast(20).map { point ->
                listOf(dateOnly(point.timestamp), CareFormats.weight(point.value, unit))
            }
            table(columns, rows)
            y += 14f
        }

        private fun drawActivityHistory(activities: List<PetActivity>) {
            heading(strings.getString(R.string.report_activity_history, activities.size))
            val columns = listOf(
                TableColumn(strings.getString(R.string.shell_col_when), MARGIN_X, 230f),
                TableColumn(strings.getString(R.string.shell_col_activity), MARGIN_X + 230f, PAGE_WIDTH - 2 * MARGIN_X - 230f),
            )
            val rows = activities.map { activity ->
                listOf(stamp(activity.timestamp), capitalized(strings.getString(activity.type.labelResource())))
            }
            table(columns, rows)
            y += 14f
        }

        private fun drawHealthNotes(logs: List<HealthLog>) {
            heading(strings.getString(R.string.report_health_notes))
            logs.forEach { log ->
                val category = capitalized(strings.getString(log.category.labelResource()))
                line("$category  ·  ${stamp(log.timestamp)}", bodyBoldPaint, x = MARGIN_X + 10f)
                val detail = buildString {
                    log.value?.let { append(strings.getString(R.string.report_value, it.toString())).append("  ") }
                    append(log.note)
                }.trim()
                if (detail.isNotEmpty()) {
                    wrap(detail, captionPaint, PAGE_WIDTH - 2 * MARGIN_X - 22f).forEach { wrappedLine ->
                        line(wrappedLine, captionPaint, x = MARGIN_X + 22f)
                    }
                }
                y += 6f
            }
            y += 10f
        }

        private fun drawFooter() {
            reserve(70f)
            y += 16f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, hairlinePaint)
            y += 18f
            canvas.drawText(strings.getString(R.string.report_disclaimer), MARGIN_X, y, captionPaint)
            y += 14f
            canvas.drawText(strings.getString(R.string.report_brand), MARGIN_X, y, captionPaint)
        }

        /** Section title with a short honey accent bar underneath. */
        private fun heading(title: String) {
            reserve(HEADING_HEIGHT + 48f)
            canvas.drawText(title, MARGIN_X, y + 14f, sectionPaint)
            canvas.drawRect(MARGIN_X, y + 20f, MARGIN_X + 36f, y + 23f, accentPaint)
            y += HEADING_HEIGHT
        }

        /** Table with a header row and hairline rules between rows; the header repeats after a page break. */
        private fun table(columns: List<TableColumn>, rows: List<List<String>>) {
            if (rows.isEmpty()) return
            reserve(HEADER_ROW_HEIGHT + ROW_HEIGHT)
            drawTableHeader(columns)
            rows.forEach { cells ->
                if (reserve(ROW_HEIGHT)) drawTableHeader(columns)
                drawTableRow(columns, cells)
            }
            y += 6f
        }

        private fun drawTableHeader(columns: List<TableColumn>) {
            columns.forEach { column -> drawCell(column, column.title, tableHeadPaint, y + 14f) }
            canvas.drawLine(MARGIN_X, y + HEADER_ROW_HEIGHT - 1f, PAGE_WIDTH - MARGIN_X, y + HEADER_ROW_HEIGHT - 1f, linePaint)
            y += HEADER_ROW_HEIGHT
        }

        private fun drawTableRow(columns: List<TableColumn>, cells: List<String>) {
            columns.forEachIndexed { index, column ->
                drawCell(column, cells.getOrElse(index) { "" }, bodyPaint, y + 14f)
            }
            canvas.drawLine(MARGIN_X, y + ROW_HEIGHT, PAGE_WIDTH - MARGIN_X, y + ROW_HEIGHT, hairlinePaint)
            y += ROW_HEIGHT
        }

        private fun drawCell(column: TableColumn, value: String, paint: Paint, baseline: Float) {
            val fitted = TextUtils.ellipsize(value, TextPaint(paint), column.width - 8f, TextUtils.TruncateAt.END).toString()
            val x = if (column.alignEnd) column.x + column.width - 4f - paint.measureText(fitted) else column.x + 4f
            canvas.drawText(fitted, x, baseline, paint)
        }

        /** One line of text at the current position, advancing [y] by [height]. */
        private fun line(value: String, paint: Paint, x: Float = MARGIN_X, height: Float = LINE_HEIGHT) {
            reserve(height)
            canvas.drawText(value, x, y + paint.textSize, paint)
            y += height
        }

        private fun wrap(text: String, paint: Paint, width: Float): List<String> {
            if (text.isBlank()) return emptyList()
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, TextPaint(paint), width.toInt().coerceAtLeast(1))
                .build()
            return List(layout.lineCount) { index ->
                text.substring(layout.getLineStart(index), layout.getLineEnd(index)).trim()
            }
        }

        private fun dateOnly(millis: Long): String = dateFormat.format(Date(millis))

        private fun stamp(millis: Long): String = "${dateOnly(millis)} ${timeFormat.format(Date(millis))}"

        private fun capitalized(value: String): String =
            value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private companion object {
        const val TAG = "PdfExportManager"
        const val PAGE_WIDTH = 595   // A4 width in points
        const val PAGE_HEIGHT = 842  // A4 height in points
        const val MARGIN_X = 48f
        const val TOP_MARGIN = 60f
        const val BOTTOM_MARGIN = 56f
        const val LINE_HEIGHT = 15f
        const val ROW_HEIGHT = 20f
        const val HEADER_ROW_HEIGHT = 22f
        const val HEADING_HEIGHT = 34f
    }
}

private class TableColumn(
    val title: String,
    val x: Float,
    val width: Float,
    val alignEnd: Boolean = false,
)

private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

private fun fillPaint(color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
