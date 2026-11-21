package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.Bill
import com.example.data.model.SettlementRecord
import com.example.data.model.SplitMode
import com.example.data.model.Trip
import com.example.data.model.TripLedgerSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a polished, multi-page A4 archival PDF in INR (₹) containing:
 * 1. Trip Summary Header, Budget Status & Member Balance Breakdown
 * 2. Simplified Remaining Settlements & Recorded Payment History
 * 3. Itemized Bills with "Where" locations, payers, split modes (including %), and splits
 * 4. Attached Receipt Images Appendix
 */
object TripPdfExporter {

    private const val PAGE_WIDTH = 595   // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)

    suspend fun generateArchivalPdf(
        context: Context,
        trip: Trip,
        bills: List<Bill>,
        summary: TripLedgerSummary,
        receiptBitmaps: Map<String, Bitmap>,
        settlements: List<SettlementRecord> = emptyList()
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        val pineColor = Color.parseColor("#1B4D3E")
        val terracottaColor = Color.parseColor("#C85A32")
        val inkColor = Color.parseColor("#1C201E")
        val mutedColor = Color.parseColor("#5B6460")
        val paperBg = Color.parseColor("#FAF8F4")
        val cardBg = Color.parseColor("#F2EFE9")
        val dividerColor = Color.parseColor("#DFD9CC")

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9ECE5")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pineColor
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val monoBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dividerColor
            strokeWidth = 1f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        var pageNumber = 1
        var currentPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = currentPage.canvas
        var y = MARGIN

        fun drawPageChrome(c: Canvas, num: Int) {
            fillPaint.color = paperBg
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), fillPaint)
            c.drawLine(MARGIN, PAGE_HEIGHT - 32f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 32f, dividerPaint)
            c.drawText("Billi Archival Ledger • ${trip.title}", MARGIN, PAGE_HEIGHT - 16f, mutedPaint)
            val pageText = "Page $num"
            c.drawText(pageText, PAGE_WIDTH - MARGIN - mutedPaint.measureText(pageText), PAGE_HEIGHT - 16f, mutedPaint)
        }

        fun startNewPage() {
            document.finishPage(currentPage)
            pageNumber += 1
            currentPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = currentPage.canvas
            drawPageChrome(canvas, pageNumber)
            y = MARGIN
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > PAGE_HEIGHT - 46f) {
                startNewPage()
            }
        }

        drawPageChrome(canvas, pageNumber)

        // --- 1. COVER HEADER BANNER ---
        val bannerRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 96f)
        fillPaint.color = pineColor
        canvas.drawRoundRect(bannerRect, 12f, 12f, fillPaint)
        canvas.drawText("BILLI ARCHIVAL TRIP LEDGER (INR)", MARGIN + 18f, y + 24f, subtitlePaint)
        canvas.drawText(trip.title.take(38), MARGIN + 18f, y + 52f, titlePaint)
        val statusLabel = if (trip.isEnded()) "ARCHIVED" else "ACTIVE SNAPSHOT"
        val exportedDate = dateTimeFormat.format(Date())
        canvas.drawText(
            "$statusLabel  •  Organizer: ${trip.creatorName}  •  Currency: INR  •  Exported $exportedDate",
            MARGIN + 18f,
            y + 76f,
            subtitlePaint
        )
        y += 114f

        // --- SUMMARY STATS BOX ---
        val statsRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 56f)
        fillPaint.color = cardBg
        canvas.drawRoundRect(statsRect, 8f, 8f, fillPaint)
        val colW = CONTENT_WIDTH / 3f
        canvas.drawText("TOTAL TRIP SPEND", MARGIN + 14f, y + 20f, mutedPaint)
        monoBoldPaint.color = pineColor
        canvas.drawText(CurrencyFormatter.format(summary.totalTripSpendCents), MARGIN + 14f, y + 40f, monoBoldPaint)

        if (trip.budgetCents > 0L) {
            canvas.drawText("TRIP BUDGET", MARGIN + colW + 14f, y + 20f, mutedPaint)
            monoBoldPaint.color = if (summary.totalTripSpendCents > trip.budgetCents) terracottaColor else inkColor
            canvas.drawText(CurrencyFormatter.format(trip.budgetCents), MARGIN + colW + 14f, y + 40f, monoBoldPaint)
        } else {
            canvas.drawText("TOTAL BILLS", MARGIN + colW + 14f, y + 20f, mutedPaint)
            monoBoldPaint.color = inkColor
            canvas.drawText("${summary.billCount} bills", MARGIN + colW + 14f, y + 40f, monoBoldPaint)
        }

        canvas.drawText("RECORDED SETTLED", MARGIN + colW * 2 + 14f, y + 20f, mutedPaint)
        monoBoldPaint.color = pineColor
        canvas.drawText(CurrencyFormatter.format(summary.totalSettledCents), MARGIN + colW * 2 + 14f, y + 40f, monoBoldPaint)
        y += 74f

        // --- 2. MEMBER STANDINGS ---
        ensureSpace(40f + summary.memberBalances.size * 22f)
        canvas.drawText("1. Member Balances", MARGIN, y, sectionPaint)
        y += 16f
        canvas.drawText("Member", MARGIN, y, mutedPaint)
        canvas.drawText("Paid", MARGIN + 210f, y, mutedPaint)
        canvas.drawText("Share", MARGIN + 310f, y, mutedPaint)
        canvas.drawText("Net Remaining", MARGIN + 410f, y, mutedPaint)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
        y += 16f

        summary.memberBalances.forEach { mb ->
            ensureSpace(22f)
            canvas.drawText(mb.displayName.take(24), MARGIN, y, bodyBoldPaint)
            monoBoldPaint.color = inkColor
            canvas.drawText(CurrencyFormatter.format(mb.totalPaidCents), MARGIN + 210f, y, monoBoldPaint)
            canvas.drawText(CurrencyFormatter.format(mb.totalShareCents), MARGIN + 310f, y, monoBoldPaint)
            monoBoldPaint.color = when {
                mb.netBalanceCents > 0L -> pineColor
                mb.netBalanceCents < 0L -> terracottaColor
                else -> mutedColor
            }
            canvas.drawText(
                CurrencyFormatter.format(mb.netBalanceCents, showPlusSign = true),
                MARGIN + 410f,
                y,
                monoBoldPaint
            )
            y += 20f
        }
        y += 12f

        val memberNames = trip.members.associate { it.memberId to it.displayName }

        // --- 3. SIMPLIFIED REMAINING SETTLEMENTS ---
        ensureSpace(50f)
        canvas.drawText("2. Remaining Simplified Settlements", MARGIN, y, sectionPaint)
        y += 18f
        if (summary.simplifiedTransfers.isEmpty()) {
            canvas.drawText("All members are completely settled up — no transfers remaining.", MARGIN, y, mutedPaint)
            y += 24f
        } else {
            summary.simplifiedTransfers.forEachIndexed { idx, transfer ->
                ensureSpace(28f)
                val rowRect = RectF(MARGIN, y - 14f, PAGE_WIDTH - MARGIN, y + 10f)
                fillPaint.color = if (idx % 2 == 0) cardBg else Color.WHITE
                canvas.drawRoundRect(rowRect, 6f, 6f, fillPaint)
                canvas.drawText("${transfer.fromName}  ->  ${transfer.toName}", MARGIN + 10f, y + 2f, bodyBoldPaint)
                monoBoldPaint.color = terracottaColor
                val amtStr = CurrencyFormatter.format(transfer.amountCents)
                canvas.drawText(amtStr, PAGE_WIDTH - MARGIN - 10f - monoBoldPaint.measureText(amtStr), y + 2f, monoBoldPaint)
                y += 28f
            }
        }
        y += 8f

        // --- RECORDED SETTLEMENTS HISTORY ---
        if (settlements.isNotEmpty()) {
            ensureSpace(44f)
            canvas.drawText("Recorded Payments (${settlements.size})", MARGIN, y, sectionPaint)
            y += 16f
            settlements.sortedBy { it.settledAtMillis }.forEachIndexed { idx, rec ->
                ensureSpace(26f)
                val fromName = memberNames[rec.fromMemberId] ?: "Member"
                val toName = memberNames[rec.toMemberId] ?: "Member"
                val dateStr = if (rec.settledAtMillis > 0L) dateFormat.format(Date(rec.settledAtMillis)) else ""
                val notePart = if (rec.note.isNotBlank()) " • ${rec.note.take(28)}" else ""
                val rowRect = RectF(MARGIN, y - 13f, PAGE_WIDTH - MARGIN, y + 9f)
                fillPaint.color = if (idx % 2 == 0) Color.WHITE else cardBg
                canvas.drawRoundRect(rowRect, 6f, 6f, fillPaint)
                canvas.drawText("$fromName paid $toName ($dateStr$notePart)", MARGIN + 10f, y + 2f, bodyPaint)
                monoBoldPaint.color = pineColor
                val amtStr = CurrencyFormatter.format(rec.amountCents)
                canvas.drawText(amtStr, PAGE_WIDTH - MARGIN - 10f - monoBoldPaint.measureText(amtStr), y + 2f, monoBoldPaint)
                y += 24f
            }
            y += 10f
        }

        // --- 4. ITEMIZED BILLS WITH LOCATIONS & SPLIT DETAILS ---
        ensureSpace(44f)
        canvas.drawText("3. Itemized Bills & Split Details", MARGIN, y, sectionPaint)
        y += 18f
        val sortedBills = bills.filter { !it.deleted }.sortedBy { it.billDateMillis }

        if (sortedBills.isEmpty()) {
            canvas.drawText("No bills recorded in this trip.", MARGIN, y, mutedPaint)
            y += 22f
        } else {
            sortedBills.forEachIndexed { idx, bill ->
                val splitLines = bill.splits.entries.map { (memberId, shareCents) ->
                    val name = memberNames[memberId] ?: "Member"
                    val pctSuffix = if (bill.billSplitMode() == SplitMode.PERCENTAGE) {
                        val bps = bill.splitPercentages[memberId]
                        if (bps != null) " (${CurrencyFormatter.formatPercentInput(bps)}%)" else ""
                    } else ""
                    "$name: ${CurrencyFormatter.format(shareCents)}$pctSuffix"
                }
                val effPayers = bill.effectivePayers()
                val payerSummary = if (effPayers.size > 1) {
                    effPayers.joinToString(" + ") { p ->
                        val pName = memberNames[p.memberId] ?: "Member"
                        "$pName (${CurrencyFormatter.format(p.amountCents)} ${p.method().label})"
                    }
                } else {
                    val only = effPayers.firstOrNull()
                    val pName = memberNames[only?.memberId ?: bill.paidByMemberId] ?: "Member"
                    val method = only?.method()?.label ?: "UPI"
                    "$pName ($method)"
                }
                val dateStr = if (bill.billDateMillis > 0L) dateFormat.format(Date(bill.billDateMillis)) else "—"
                val whereStr = bill.locationName.ifBlank { "No location specified" }
                val wrappedMetaLines = wrapText(
                    "Where: ${whereStr.take(40)}  •  $dateStr  •  Paid by $payerSummary",
                    bodyPaint,
                    CONTENT_WIDTH - 20f
                )
                val wrappedSplitLines = wrapText(
                    "Split (${bill.billSplitMode().label}): ${splitLines.joinToString(" • ")}",
                    mutedPaint,
                    CONTENT_WIDTH - 20f
                )
                val blockHeight = 32f + wrappedMetaLines.size * 14f + wrappedSplitLines.size * 13f
                ensureSpace(blockHeight + 10f)

                val cardRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + blockHeight)
                fillPaint.color = Color.WHITE
                canvas.drawRoundRect(cardRect, 8f, 8f, fillPaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = dividerColor
                    strokeWidth = 1f
                }
                canvas.drawRoundRect(cardRect, 8f, 8f, borderPaint)

                val billNum = "#${idx + 1}"
                canvas.drawText("$billNum  ${bill.title.take(44)}", MARGIN + 10f, y + 18f, bodyBoldPaint)
                monoBoldPaint.color = pineColor
                val amtText = CurrencyFormatter.format(bill.amountCents)
                canvas.drawText(amtText, PAGE_WIDTH - MARGIN - 10f - monoBoldPaint.measureText(amtText), y + 18f, monoBoldPaint)

                var lineY = y + 34f
                wrappedMetaLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, lineY, bodyPaint)
                    lineY += 14f
                }
                wrappedSplitLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, lineY, mutedPaint)
                    lineY += 13f
                }
                y += blockHeight + 10f
            }
        }

        // --- 5. ATTACHED RECEIPT IMAGES APPENDIX ---
        val billsWithImages = sortedBills.mapIndexedNotNull { idx, bill ->
            val bmp = receiptBitmaps[bill.id] ?: return@mapIndexedNotNull null
            Triple(idx + 1, bill, bmp)
        }
        if (billsWithImages.isNotEmpty()) {
            startNewPage()
            canvas.drawText("4. Attached Receipt Images (${billsWithImages.size})", MARGIN, y, sectionPaint)
            y += 22f

            billsWithImages.forEach { (billNumber, bill, bitmap) ->
                val maxImgW = CONTENT_WIDTH
                val maxImgH = 320f
                val scale = minOf(maxImgW / bitmap.width.toFloat(), maxImgH / bitmap.height.toFloat(), 1f)
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                val captionHeight = 36f
                ensureSpace(drawH + captionHeight + 24f)

                val amtText = CurrencyFormatter.format(bill.amountCents)
                canvas.drawText("Bill #$billNumber: ${bill.title} ($amtText)", MARGIN, y + 12f, bodyBoldPaint)
                val locText = bill.locationName.ifBlank { "Location not specified" }
                canvas.drawText("Where: $locText", MARGIN, y + 26f, mutedPaint)
                y += captionHeight

                val left = MARGIN + (CONTENT_WIDTH - drawW) / 2f
                val destRect = RectF(left, y, left + drawW, y + drawH)
                canvas.drawBitmap(bitmap, null, destRect, null)
                val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = dividerColor
                    strokeWidth = 1f
                }
                canvas.drawRect(destRect, framePaint)
                y += drawH + 22f
            }
        }

        document.finishPage(currentPage)

        val exportsDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safeName = trip.title.replace(Regex("[^a-zA-Z0-9_\\-]+"), "_").take(32).ifBlank { "trip" }
        val outFile = File(exportsDir, "Billi_${safeName}_Archive.pdf")
        FileOutputStream(outFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()
        outFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf("") }
    }
}
