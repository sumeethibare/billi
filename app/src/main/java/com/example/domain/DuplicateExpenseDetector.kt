package com.example.domain

import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.Trip
import kotlin.math.abs

data class DuplicateExpenseMatch(
    val existingBill: Bill,
    val payerName: String,
    val minutesApart: Long,
    val headline: String,
    val detail: String
)

/**
 * Smart "Receipt & UPI Discrepancy Detective" — Duplicate Expense Shield.
 * Flags probable accidental double-entries when another bill matches by amount
 * and time window (e.g., two friends logging the same ₹1,450 dinner 10 mins apart).
 */
object DuplicateExpenseDetector {

    private const val NEAR_WINDOW_MS = 90L * 60L * 1000L // 90 minutes
    private const val SAME_DAY_WINDOW_MS = 12L * 60L * 60L * 1000L // 12 hours

    fun findPotentialDuplicate(
        trip: Trip,
        existingBills: List<Bill>,
        editingBillId: String?,
        candidateAmountCents: Long,
        candidateTitle: String,
        candidateCategory: BillCategory,
        candidateDateMillis: Long
    ): DuplicateExpenseMatch? {
        if (candidateAmountCents <= 0L) return null
        val memberNames = trip.members.associate { it.memberId to it.displayName }
        val cleanTitle = candidateTitle.trim().lowercase()

        val candidates = existingBills.filter { bill ->
            !bill.deleted &&
                bill.id != editingBillId &&
                bill.amountCents > 0L
        }

        val bestMatch = candidates.firstOrNull { bill ->
            val diffAmount = abs(bill.amountCents - candidateAmountCents)
            val tolerance = (candidateAmountCents * 0.02).toLong().coerceAtLeast(100L) // within ±2% or ₹1
            val amountClose = diffAmount <= tolerance
            val timeDiffMs = abs(bill.billDateMillis - candidateDateMillis)

            val exactAmountNearTime = diffAmount == 0L && timeDiffMs <= NEAR_WINDOW_MS
            val closeAmountSameCategoryNearTime =
                amountClose && bill.billCategory() == candidateCategory && timeDiffMs <= NEAR_WINDOW_MS
            val exactAmountSameDaySimilarTitle =
                diffAmount == 0L && timeDiffMs <= SAME_DAY_WINDOW_MS &&
                    (bill.billCategory() == candidateCategory || titlesOverlap(cleanTitle, bill.title.lowercase()))

            exactAmountNearTime || closeAmountSameCategoryNearTime || exactAmountSameDaySimilarTitle
        } ?: return null

        val payerName = memberNames[bestMatch.paidByMemberId] ?: "A companion"
        val minutesApart = (abs(bestMatch.billDateMillis - candidateDateMillis) / 60_000L)
        val timeDesc = when {
            minutesApart <= 1L -> "just now"
            minutesApart < 60L -> "$minutesApart mins ago"
            else -> "${minutesApart / 60L} hr ago"
        }
        val formattedAmt = CurrencyFormatter.format(bestMatch.amountCents)
        return DuplicateExpenseMatch(
            existingBill = bestMatch,
            payerName = payerName,
            minutesApart = minutesApart,
            headline = "Possible duplicate expense detected",
            detail = "Looks like $payerName already logged \"${bestMatch.title}\" ($formattedAmt) $timeDesc — merge or keep separate?"
        )
    }

    private fun titlesOverlap(a: String, b: String): Boolean {
        if (a.length < 3 || b.length < 3) return false
        if (a.contains(b) || b.contains(a)) return true
        val wordsA = a.split(Regex("\\s+")).filter { it.length >= 3 }.toSet()
        val wordsB = b.split(Regex("\\s+")).filter { it.length >= 3 }.toSet()
        return wordsA.intersect(wordsB).isNotEmpty()
    }
}
