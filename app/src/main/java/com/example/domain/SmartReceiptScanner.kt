package com.example.domain

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.BillCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.roundToLong

data class ScannedReceiptResult(
    val title: String,
    val amountCents: Long,
    val category: BillCategory,
    val locationName: String,
    val lineItems: List<String> = emptyList()
)

/**
 * Smart Receipt Scanner powered by Firebase AI Logic (`Firebase.ai` + `gemini-3.8-flash`)
 * using structured JSON output to extract merchant title, INR total, category, location,
 * and itemized line items from a receipt photo.
 */
object SmartReceiptScanner {

    private const val TAG = "SmartReceiptScanner"
    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val TIMEOUT_MS = 45_000L

    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Short merchant or expense title, e.g. 'Cafe Britto Dinner'"),
            "totalAmountInr" to Schema.double("Grand total payable amount in Indian Rupees (INR). 0 if unreadable."),
            "category" to Schema.enumeration(
                values = listOf("food", "stay", "transport", "activity", "shopping", "other"),
                description = "Best matching expense category"
            ),
            "locationName" to Schema.string("Restaurant, store address, neighborhood, or city printed on the receipt, or empty string if absent"),
            "lineItems" to Schema.array(
                items = Schema.string("Short item label with price, e.g. 'Paneer Tikka - ₹320'"),
                description = "Up to 8 main purchased items on the receipt"
            )
        ),
        optionalProperties = listOf("locationName", "lineItems")
    )

    // Lazy initialization ensures FirebaseApp and App Check are configured before instantiation
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun scanReceipt(bitmap: Bitmap): Result<ScannedReceiptResult> = withContext(Dispatchers.IO) {
        try {
            val responseText = withTimeout(TIMEOUT_MS) {
                val prompt = content {
                    image(bitmap)
                    text(
                        """
                        Extract the structured expense details from this receipt photo.
                        - Return the grand total payable (including GST/taxes/service charge) as totalAmountInr in INR.
                        - Provide a clean concise merchant or bill title (max 60 chars).
                        - Pick the closest category from: food, stay, transport, activity, shopping, other.
                        - Extract the place/address/city into locationName if visible on the receipt.
                        - List up to 8 key line items if legible.
                        """.trimIndent()
                    )
                }
                model.generateContent(prompt).text.orEmpty()
            }

            if (responseText.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Could not read receipt details. Please try a clearer photo.")
                )
            }

            val json = JSONObject(responseText)
            val title = json.optString("title", "").trim().take(120)
            val amountInr = json.optDouble("totalAmountInr", 0.0)
                .takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 } ?: 0.0
            val amountCents = (amountInr * 100.0).roundToLong().coerceIn(0L, 1_000_000_000L)
            val category = BillCategory.fromWire(json.optString("category", "other"))
            val location = json.optString("locationName", "").trim().take(160)
            val itemsJson = json.optJSONArray("lineItems")
            val lineItems = buildList {
                if (itemsJson != null) {
                    for (i in 0 until minOf(itemsJson.length(), 8)) {
                        val item = itemsJson.optString(i, "").trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }

            Result.success(
                ScannedReceiptResult(
                    title = title,
                    amountCents = amountCents,
                    category = category,
                    locationName = location,
                    lineItems = lineItems
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Smart receipt scan timed out", e)
            Result.failure(IllegalStateException("Receipt scan timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Smart receipt scan failed", e)
            Result.failure(
                IllegalStateException(
                    "Smart scan failed: ${e.localizedMessage ?: "Check your connection and try again."}",
                    e
                )
            )
        }
    }
}
