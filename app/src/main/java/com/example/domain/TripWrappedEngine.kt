package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.model.Bill
import com.example.data.model.SettlementRecord
import com.example.data.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class TripWrappedCardData(
    val id: String,
    val badge: String,
    val title: String,
    val heroValue: String,
    val subtitle: String,
    val footnote: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long
)

/**
 * Transforms Trip Insights, Bills & Settlements into swipeable Instagram Story–style
 * "Trip Wrapped" Indian group award cards and renders high-res shareable WhatsApp PNGs.
 */
object TripWrappedEngine {

    fun generateStoryCards(
        trip: Trip,
        bills: List<Bill>,
        settlements: List<SettlementRecord>
    ): List<TripWrappedCardData> {
        val activeBills = bills.filter { !it.deleted }
        val insights = DebtSimplifier.computeInsights(trip, activeBills)
        val memberNames = trip.members.associate { it.memberId to it.displayName }

        val cards = mutableListOf<TripWrappedCardData>()

        // Card 1: Trip Grand Intro
        cards.add(
            TripWrappedCardData(
                id = "intro",
                badge = "BILLI TRIP WRAPPED",
                title = trip.title,
                heroValue = CurrencyFormatter.formatCompact(insights.totalSpendCents),
                subtitle = "${activeBills.size} bills logged across ${insights.dailySpends.size.coerceAtLeast(1)} days with ${trip.members.size} squad members",
                footnote = "Avg ${CurrencyFormatter.formatCompact(insights.averagePerDayCents)}/day • Tap Share for WhatsApp",
                gradientStartHex = 0xFF0E6E52L,
                gradientEndHex = 0xFF1F4037L
            )
        )

        // Card 2: The Ambani of the Trip (Highest total paid upfront)
        val paidByMember = mutableMapOf<String, Long>()
        activeBills.forEach { bill ->
            bill.effectivePayers().forEach { payer ->
                paidByMember[payer.memberId] = (paidByMember[payer.memberId] ?: 0L) + payer.amountCents
            }
        }
        val ambaniEntry = paidByMember.maxByOrNull { it.value }
        if (ambaniEntry != null) {
            val name = memberNames[ambaniEntry.key] ?: "Organizer"
            val pct = if (insights.totalSpendCents > 0L) {
                ((ambaniEntry.value * 100L) / insights.totalSpendCents).toInt()
            } else 0
            cards.add(
                TripWrappedCardData(
                    id = "ambani",
                    badge = "CROWNED TOP SPONSOR",
                    title = "The Ambani of the Trip",
                    heroValue = name,
                    subtitle = "Fronted ${CurrencyFormatter.format(ambaniEntry.value)} ($pct% of the entire trip spend)!",
                    footnote = "Card & UPI hero keeping the squad moving",
                    gradientStartHex = 0xFFB87300L,
                    gradientEndHex = 0xFF5D2E0CL
                )
            )
        }

        // Card 3: The Chai & Tapri Sponsor (Most frequent small transactions <= ₹300, or most bills)
        val smallBills = activeBills.filter { it.amountCents <= 300_00L }
        val sourceForTapri = smallBills.ifEmpty { activeBills }
        val tapriEntry = sourceForTapri
            .flatMap { it.effectivePayers() }
            .groupingBy { it.memberId }
            .eachCount()
            .maxByOrNull { it.value }
        if (tapriEntry != null) {
            val name = memberNames[tapriEntry.key] ?: "Member"
            val count = tapriEntry.value
            val desc = if (smallBills.isNotEmpty()) {
                "Picked up $count quick chai, snacks & auto bills under ₹300"
            } else {
                "Tapped UPI fastest — logged $count separate payments!"
            }
            cards.add(
                TripWrappedCardData(
                    id = "chai_tapri",
                    badge = "MICRO-EXPENSE MVP",
                    title = "The Chai & Tapri Sponsor",
                    heroValue = name,
                    subtitle = desc,
                    footnote = "Small bills, huge squad morale",
                    gradientStartHex = 0xFF9C3D18L,
                    gradientEndHex = 0xFF4A190BL
                )
            )
        }

        // Card 4: Fastest Settler (Earliest / highest recorded settlement)
        val fastestSettlement = settlements.minByOrNull { it.settledAtMillis }
        if (fastestSettlement != null) {
            val settlerName = memberNames[fastestSettlement.fromMemberId] ?: "Member"
            val totalBySettler = settlements
                .filter { it.fromMemberId == fastestSettlement.fromMemberId }
                .sumOf { it.amountCents }
            cards.add(
                TripWrappedCardData(
                    id = "fastest_settler",
                    badge = "ZERO DRAMA AWARD",
                    title = "Fastest Settler",
                    heroValue = settlerName,
                    subtitle = "Cleared ${CurrencyFormatter.format(totalBySettler)} in record time without a single reminder!",
                    footnote = "${settlements.size} settlements verified in ledger",
                    gradientStartHex = 0xFF1D5D7AL,
                    gradientEndHex = 0xFF0E2A47L
                )
            )
        } else if (trip.members.size >= 2) {
            cards.add(
                TripWrappedCardData(
                    id = "fastest_settler_pending",
                    badge = "UP FOR GRABS",
                    title = "Fastest Settler",
                    heroValue = "Who's First?",
                    subtitle = "Settle your balance in the Settle tab to claim the Fastest Settler trophy!",
                    footnote = "Smart Debt Simplification cuts extra transfers",
                    gradientStartHex = 0xFF1D5D7AL,
                    gradientEndHex = 0xFF0E2A47L
                )
            )
        }

        // Card 5: Peak Spend Hour & Top Category
        val peakHourEntry = activeBills
            .filter { it.billDateMillis > 0L }
            .groupingBy {
                Calendar.getInstance().apply { timeInMillis = it.billDateMillis }
                    .get(Calendar.HOUR_OF_DAY)
            }
            .fold(0L) { acc, bill -> acc + bill.amountCents }
            .maxByOrNull { it.value }
        val topCategory = insights.categoryBreakdown.firstOrNull()
        if (topCategory != null) {
            val peakHourLabel = peakHourEntry?.key?.let { formatHour(it) } ?: "Evening"
            val topVenue = activeBills
                .map { it.locationName.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            cards.add(
                TripWrappedCardData(
                    id = "peak_category",
                    badge = "SPENDING DNA",
                    title = "Peak Hour: $peakHourLabel",
                    heroValue = topCategory.category.label,
                    subtitle = "${(topCategory.percentage * 100).toInt()}% of spend (${CurrencyFormatter.format(topCategory.amountCents)}) went to ${topCategory.category.label}.",
                    footnote = if (!topVenue.isNullOrBlank()) "Favorite hotspot: $topVenue" else "Tracked with Billi • com.isuh.billi",
                    gradientStartHex = 0xFF5B2E91L,
                    gradientEndHex = 0xFF211138L
                )
            )
        }

        return cards
    }

    private fun formatHour(hour24: Int): String = when {
        hour24 == 0 -> "12 AM"
        hour24 < 12 -> "$hour24 AM"
        hour24 == 12 -> "12 PM"
        else -> "${hour24 - 12} PM"
    }

    /**
     * Renders a crisp 1080x1920 vertical story PNG card ready for WhatsApp / Instagram sharing.
     */
    suspend fun renderCardToPngFile(
        context: Context,
        card: TripWrappedCardData,
        tripTitle: String
    ): File = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1620
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                card.gradientStartHex.toInt(),
                card.gradientEndHex.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 48f, 48f, bgPaint)

        // Subtle decorative circles
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x18FFFFFF
        }
        canvas.drawCircle(width * 0.85f, height * 0.16f, 260f, glowPaint)
        canvas.drawCircle(width * 0.15f, height * 0.84f, 320f, glowPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 34f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(card.badge, width / 2f, 260f, badgePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 68f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(card.title.take(30), width / 2f, 430f, titlePaint)

        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFE082.toInt()
            textSize = 118f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(card.heroValue.take(18), width / 2f, 760f, heroPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val lines = wrapText(card.subtitle, 34)
        var y = 920f
        lines.forEach { line ->
            canvas.drawText(line, width / 2f, y, subPaint)
            y += 66f
        }

        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xBBFFFFFF.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(card.footnote.take(46), width / 2f, height - 240f, footPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Billi • $tripTitle", width / 2f, height - 120f, brandPaint)

        val dir = File(context.cacheDir, "wrapped").apply { mkdirs() }
        val file = File(dir, "billi_wrapped_${card.id}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bmp.recycle()
        file
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > maxChars && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.take(4)
    }
}
