package com.example.domain

import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.SplitMode
import com.example.data.model.Trip
import java.util.Calendar

data class ExpenseQuickPreset(
    val id: String,
    val label: String,
    val title: String,
    val category: BillCategory,
    val suggestedAmountCents: Long,
    val participantIds: List<String>,
    val paidByMemberId: String,
    val splitMode: SplitMode,
    val locationHint: String,
    val reason: String
)

data class SmartExpenseRecommendation(
    val defaultTitleHint: String,
    val defaultCategory: BillCategory,
    val defaultSplitMode: SplitMode,
    val defaultPayerId: String,
    val defaultParticipantIds: List<String>,
    val defaultLocation: String,
    val bannerSummary: String,
    val quickPresets: List<ExpenseQuickPreset>
)

/**
 * Local on-device activity recommender for Ultra-Fast Zero-Typing Expense Entry.
 * Uses time-of-day heuristics + historical trip bill patterns (frequent groups, usual payer,
 * recent locations, and last-used split mode) so adding expenses takes 1 tap.
 */
object SmartActivityRecommender {

    fun buildRecommendation(
        trip: Trip,
        activeBills: List<Bill>,
        lastUsedSplitMode: SplitMode,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartExpenseRecommendation {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val validBills = activeBills.filter { !it.deleted }
        val allMemberIds = trip.members.map { it.memberId }

        val (timeLabel, timeTitle, timeCat) = when (hour) {
            in 6..10 -> Triple("Morning", "Breakfast & Chai", BillCategory.FOOD)
            in 11..15 -> Triple("Midday", "Lunch", BillCategory.FOOD)
            in 16..18 -> Triple("Evening", "Chai & Snacks", BillCategory.FOOD)
            in 19..22 -> Triple("Night", "Dinner", BillCategory.FOOD)
            else -> Triple("Late Night", "Cab / Night Snacks", BillCategory.TRANSPORT)
        }

        // Infer usual split mode from recent trip history or persisted preference
        val preferredSplitMode = validBills.firstOrNull()?.billSplitMode() ?: lastUsedSplitMode

        // Infer most frequent payer for this category (or overall)
        val categoryBills = validBills.filter { it.billCategory() == timeCat }
        val referenceBills = categoryBills.ifEmpty { validBills }

        val usualPayer = referenceBills
            .groupingBy { it.paidByMemberId }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.takeIf { id -> trip.members.any { it.memberId == id } }
            ?: trip.members.firstOrNull { it.linkedUid == trip.creatorUid }?.memberId
            ?: allMemberIds.firstOrNull().orEmpty()

        // Infer usual participant group for this category
        val usualParticipants = referenceBills
            .map { it.participantIds.filter { id -> id in allMemberIds }.sorted() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: allMemberIds

        val lastLocation = referenceBills
            .firstOrNull { it.locationName.isNotBlank() }
            ?.locationName
            ?: validBills.firstOrNull { it.locationName.isNotBlank() }?.locationName.orEmpty()

        val avgCatAmount = if (categoryBills.isNotEmpty()) {
            val rawAvg = categoryBills.sumOf { it.amountCents } / categoryBills.size
            // Round to nearest ₹10 for clean 1-tap preset
            ((rawAvg + 500L) / 1000L) * 1000L
        } else {
            when (hour) {
                in 6..10 -> 250_00L
                in 11..15 -> 850_00L
                in 16..18 -> 150_00L
                in 19..22 -> 1400_00L
                else -> 300_00L
            }
        }

        val memberNames = trip.members.associate { it.memberId to it.displayName }
        val groupLabel = if (usualParticipants.size == allMemberIds.size) {
            "Everyone (${allMemberIds.size})"
        } else {
            usualParticipants.take(2).mapNotNull { memberNames[it] }.joinToString(", ") +
                if (usualParticipants.size > 2) " +${usualParticipants.size - 2}" else ""
        }

        val presets = buildList {
            // 1. Time-contextual primary preset
            add(
                ExpenseQuickPreset(
                    id = "time_primary",
                    label = "$timeTitle • ${CurrencyFormatter.formatCompact(avgCatAmount)}",
                    title = timeTitle,
                    category = timeCat,
                    suggestedAmountCents = avgCatAmount,
                    participantIds = usualParticipants,
                    paidByMemberId = usualPayer,
                    splitMode = preferredSplitMode,
                    locationHint = lastLocation,
                    reason = "$timeLabel smart pick"
                )
            )
            // 2. Classic Indian Trip Quick Taps (Chai, Auto, Cab, Water/Snacks)
            add(
                ExpenseQuickPreset(
                    id = "quick_chai",
                    label = "Chai & Tapri • ₹120",
                    title = "Chai & Snacks",
                    category = BillCategory.FOOD,
                    suggestedAmountCents = 120_00L,
                    participantIds = usualParticipants,
                    paidByMemberId = usualPayer,
                    splitMode = SplitMode.EQUAL,
                    locationHint = lastLocation,
                    reason = "1-tap street tea"
                )
            )
            add(
                ExpenseQuickPreset(
                    id = "quick_auto",
                    label = "Auto / Rickshaw • ₹80",
                    title = "Auto Ride",
                    category = BillCategory.TRANSPORT,
                    suggestedAmountCents = 80_00L,
                    participantIds = usualParticipants,
                    paidByMemberId = usualPayer,
                    splitMode = SplitMode.EQUAL,
                    locationHint = lastLocation,
                    reason = "1-tap local commute"
                )
            )
            add(
                ExpenseQuickPreset(
                    id = "quick_cab",
                    label = "Cab / Uber • ₹350",
                    title = "Cab Ride",
                    category = BillCategory.TRANSPORT,
                    suggestedAmountCents = 350_00L,
                    participantIds = usualParticipants,
                    paidByMemberId = usualPayer,
                    splitMode = SplitMode.EQUAL,
                    locationHint = lastLocation,
                    reason = "1-tap ride split"
                )
            )
            // 3. Repeat most frequent historical bill title if present
            val topRecurring = validBills
                .groupingBy { it.title.trim() }
                .eachCount()
                .filterKeys { it.isNotBlank() && !it.equals(timeTitle, ignoreCase = true) }
                .maxByOrNull { it.value }
                ?.key
            if (topRecurring != null) {
                val sample = validBills.first { it.title.trim() == topRecurring }
                add(
                    ExpenseQuickPreset(
                        id = "repeat_top",
                        label = "Repeat: $topRecurring • ${CurrencyFormatter.formatCompact(sample.amountCents)}",
                        title = sample.title,
                        category = sample.billCategory(),
                        suggestedAmountCents = sample.amountCents,
                        participantIds = sample.participantIds.filter { it in allMemberIds }.ifEmpty { allMemberIds },
                        paidByMemberId = sample.paidByMemberId.takeIf { id -> id in allMemberIds } ?: usualPayer,
                        splitMode = sample.billSplitMode(),
                        locationHint = sample.locationName,
                        reason = "Based on trip history"
                    )
                )
            }
        }

        val summary = "$timeLabel pick: $timeTitle • $groupLabel • ${preferredSplitMode.label}"
        return SmartExpenseRecommendation(
            defaultTitleHint = timeTitle,
            defaultCategory = timeCat,
            defaultSplitMode = preferredSplitMode,
            defaultPayerId = usualPayer,
            defaultParticipantIds = usualParticipants,
            defaultLocation = lastLocation,
            bannerSummary = summary,
            quickPresets = presets
        )
    }
}
