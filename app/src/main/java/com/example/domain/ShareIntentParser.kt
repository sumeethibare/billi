package com.example.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import com.example.data.model.BillCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

data class SharedExpenseDraft(
    val sourceApp: String,
    val title: String,
    val amountCents: Long,
    val category: BillCategory,
    val locationName: String,
    val lineItems: List<String> = emptyList(),
    val attachedUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val rawSummary: String = ""
)

/**
 * "Share-to-Split" Universal Intent parser for Swiggy, Zomato, IRCTC, Uber, Ola, Rapido,
 * BookMyShow, and UPI payment confirmations shared via Intent.ACTION_SEND (text, images, PDFs).
 *
 * Uses zero-latency on-device regex parsers for shared text, plus PdfRenderer + Firebase AI Logic
 * (`SmartReceiptScanner`) hybrid extraction for shared images & PDF invoices.
 */
object ShareIntentParser {

    fun hasSharePayload(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_SEND) return false
        val hasText = !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()
        val hasStream = extractStreamUri(intent) != null
        return hasText || hasStream
    }

    @Suppress("DEPRECATION")
    private fun extractStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    suspend fun parseShareIntent(
        context: Context,
        intent: Intent,
        isOnline: Boolean
    ): SharedExpenseDraft? = withContext(Dispatchers.IO) {
        if (intent.action != Intent.ACTION_SEND) return@withContext null
        val mime = intent.type.orEmpty().lowercase()
        val sharedText = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getStringExtra(Intent.EXTRA_TEXT)
        ).joinToString("\n").trim()
        val streamUri = extractStreamUri(intent)

        // 1. Text / Receipt SMS / Order Summary share (instant offline regex extraction)
        if (streamUri == null && sharedText.isNotBlank()) {
            return@withContext parseStructuredText(sharedText)
        }

        // 2. Image share (Screenshot of Swiggy/Zomato/Uber/UPI or receipt photo)
        if (streamUri != null && mime.startsWith("image/")) {
            val processed = ReceiptImageProcessor.processUriToCompressedBase64(context, streamUri)
            val bitmap = processed?.first
            if (bitmap != null && isOnline) {
                val aiScan = SmartReceiptScanner.scanReceipt(bitmap).getOrNull()
                if (aiScan != null && (aiScan.title.isNotBlank() || aiScan.amountCents > 0L)) {
                    return@withContext SharedExpenseDraft(
                        sourceApp = "Shared Receipt Image",
                        title = aiScan.title.ifBlank { "Shared Expense" },
                        amountCents = aiScan.amountCents,
                        category = aiScan.category,
                        locationName = aiScan.locationName,
                        lineItems = aiScan.lineItems,
                        attachedUri = streamUri,
                        previewBitmap = bitmap,
                        rawSummary = "AI-extracted from shared image"
                    )
                }
            }
            val textFallback = if (sharedText.isNotBlank()) parseStructuredText(sharedText) else null
            return@withContext SharedExpenseDraft(
                sourceApp = textFallback?.sourceApp ?: "Shared Image",
                title = textFallback?.title ?: "Shared Receipt",
                amountCents = textFallback?.amountCents ?: 0L,
                category = textFallback?.category ?: BillCategory.FOOD,
                locationName = textFallback?.locationName.orEmpty(),
                attachedUri = streamUri,
                previewBitmap = bitmap,
                rawSummary = if (isOnline) "Attached shared receipt image" else "Saved offline — enter total or scan when online"
            )
        }

        // 3. PDF share (IRCTC e-ticket, Swiggy/Zomato PDF invoice, Uber/Flight PDF receipt)
        if (streamUri != null && mime.contains("pdf")) {
            val renderedBitmap = renderFirstPdfPage(context, streamUri)
            if (renderedBitmap != null && isOnline) {
                val aiScan = SmartReceiptScanner.scanReceipt(renderedBitmap).getOrNull()
                if (aiScan != null && (aiScan.title.isNotBlank() || aiScan.amountCents > 0L)) {
                    return@withContext SharedExpenseDraft(
                        sourceApp = "Shared PDF Invoice",
                        title = aiScan.title.ifBlank { "PDF Ticket / Invoice" },
                        amountCents = aiScan.amountCents,
                        category = aiScan.category,
                        locationName = aiScan.locationName,
                        lineItems = aiScan.lineItems,
                        attachedUri = streamUri,
                        previewBitmap = renderedBitmap,
                        rawSummary = "Extracted from shared PDF"
                    )
                }
            }
            val textFallback = if (sharedText.isNotBlank()) parseStructuredText(sharedText) else null
            return@withContext SharedExpenseDraft(
                sourceApp = textFallback?.sourceApp ?: "Shared PDF",
                title = textFallback?.title ?: "Shared PDF Bill",
                amountCents = textFallback?.amountCents ?: 0L,
                category = textFallback?.category ?: BillCategory.TRANSPORT,
                locationName = textFallback?.locationName.orEmpty(),
                attachedUri = streamUri,
                previewBitmap = renderedBitmap,
                rawSummary = "First page of PDF attached"
            )
        }

        if (sharedText.isNotBlank()) parseStructuredText(sharedText) else null
    }

    /**
     * Pure on-device structured regex parser tailored for Indian trip apps:
     * Swiggy, Zomato, IRCTC, Uber, Ola, Rapido, BookMyShow, MakeMyTrip, and UPI apps.
     */
    fun parseStructuredText(raw: String): SharedExpenseDraft {
        val text = raw.trim()
        val lower = text.lowercase()

        val (source, category) = when {
            "swiggy" in lower -> "Swiggy" to BillCategory.FOOD
            "zomato" in lower || "blinkit" in lower -> "Zomato" to BillCategory.FOOD
            "irctc" in lower || "pnr" in lower || "train" in lower -> "IRCTC" to BillCategory.TRANSPORT
            "uber" in lower -> "Uber" to BillCategory.TRANSPORT
            "ola" in lower || "rapido" in lower -> "Cab / Auto" to BillCategory.TRANSPORT
            "makemytrip" in lower || "oyo" in lower || "airbnb" in lower || "hotel" in lower -> "Stay" to BillCategory.STAY
            "bookmyshow" in lower || "ticket" in lower -> "Activities" to BillCategory.ACTIVITY
            "upi" in lower || "gpay" in lower || "phonepe" in lower || "paytm" in lower -> "UPI Payment" to BillCategory.OTHER
            else -> "Shared Bill" to BillCategory.OTHER
        }

        val amountCents = extractHighestLikelyTotalCents(text)

        val merchant = extractMerchantOrRoute(text, source)
        val location = extractLocationHint(text)
        val items = extractLineItems(text)

        return SharedExpenseDraft(
            sourceApp = source,
            title = merchant.take(100),
            amountCents = amountCents,
            category = category,
            locationName = location.take(140),
            lineItems = items,
            rawSummary = "Parsed from $source share"
        )
    }

    private fun extractHighestLikelyTotalCents(text: String): Long {
        // Prefer explicitly labeled total / paid / fare amounts
        val labeledRegex = Regex(
            """(?i)(?:grand\s*total|total\s*(?:fare|paid|payable|amount)?|paid|fare|bill\s*amount|debited\s*by|rs\.?|inr|₹)\s*[:\-]?\s*(?:₹|rs\.?|inr)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
        )
        val labeledMatches = labeledRegex.findAll(text)
            .mapNotNull { parseInrToCents(it.groupValues[1]) }
            .filter { it in 100L..100_000_000L }
            .toList()
        if (labeledMatches.isNotEmpty()) {
            // Look specifically for "total" or "paid" match first
            val explicitTotalRegex = Regex(
                """(?i)(?:grand\s*total|total\s*(?:fare|paid|payable|amount)?|amount\s*paid|paid)\s*[:\-]?\s*(?:₹|rs\.?|inr)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
            )
            explicitTotalRegex.find(text)?.groupValues?.getOrNull(1)?.let {
                parseInrToCents(it)?.let { cents -> return cents }
            }
            return labeledMatches.max()
        }

        val currencyRegex = Regex("""(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        return currencyRegex.findAll(text)
            .mapNotNull { parseInrToCents(it.groupValues[1]) }
            .filter { it in 100L..100_000_000L }
            .maxOrNull() ?: 0L
    }

    private fun parseInrToCents(raw: String): Long? {
        val clean = raw.replace(",", "").trim()
        val d = clean.toDoubleOrNull() ?: return null
        if (d <= 0.0 || d.isNaN() || d.isInfinite()) return null
        return (d * 100.0).roundToLong()
    }

    private fun extractMerchantOrRoute(text: String, source: String): String {
        val fromRegex = Regex("""(?i)(?:order\s+from|from|at|paid\s+to|to)\s+([A-Za-z0-9&'().,\-\s]{3,48})""")
        val matched = fromRegex.find(text)?.groupValues?.getOrNull(1)
            ?.substringBefore("\n")
            ?.substringBefore(" for ")
            ?.substringBefore(" on ")
            ?.trim()
        if (!matched.isNullOrBlank()) {
            return if (source in listOf("Swiggy", "Zomato", "IRCTC", "Uber")) {
                "$source • $matched"
            } else matched
        }
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.length in 3..60 }
        return firstLine ?: "$source Expense"
    }

    private fun extractLocationHint(text: String): String {
        val routeRegex = Regex("""(?i)(?:boarding|pickup|drop|destination|station|city|near|in)\s*[:\-]?\s*([A-Za-z0-9,\-\s]{3,45})""")
        return routeRegex.find(text)?.groupValues?.getOrNull(1)
            ?.substringBefore("\n")
            ?.trim()
            .orEmpty()
    }

    private fun extractLineItems(text: String): List<String> {
        val itemRegex = Regex("""(?m)^\s*(?:\d+\s*x\s*)?([A-Za-z][A-Za-z0-9\s&'()\-]{2,36})\s+[-–:]?\s*(?:₹|Rs\.?)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        return itemRegex.findAll(text)
            .take(6)
            .map { "${it.groupValues[1].trim()} - ₹${it.groupValues[2].trim()}" }
            .toList()
    }

    private fun renderFirstPdfPage(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val scale = (1080f / page.width.coerceAtLeast(1)).coerceIn(1f, 2f)
                        val w = (page.width * scale).toInt().coerceIn(320, 1440)
                        val h = (page.height * scale).toInt().coerceIn(320, 2048)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
