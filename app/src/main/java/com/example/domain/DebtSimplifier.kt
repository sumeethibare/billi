package com.example.domain

import com.example.data.model.Bill
import com.example.data.model.CategorySpend
import com.example.data.model.DailySpend
import com.example.data.model.DayBillGroup
import com.example.data.model.MemberBalance
import com.example.data.model.SettlementRecord
import com.example.data.model.SettlementTransfer
import com.example.data.model.Trip
import com.example.data.model.TripInsights
import com.example.data.model.TripLedgerSummary
import com.example.data.model.TripMember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Deterministic integer-paise accounting, multi-payer split payment engine,
 * percentage/equal split engine, both raw pairwise & Smart Graph-Reduction debt simplifier,
 * day-by-day grouper, and insights calculator.
 */
object DebtSimplifier {

    const val FULL_PERCENT_BPS = 10_000L

    fun computeEqualSplits(totalCents: Long, participantIds: List<String>): Map<String, Long> {
        if (participantIds.isEmpty() || totalCents <= 0L) return emptyMap()
        val distinct = participantIds.distinct()
        val count = distinct.size.toLong()
        val base = totalCents / count
        val remainder = (totalCents % count).toInt()
        return distinct.mapIndexed { index, memberId ->
            memberId to (base + if (index < remainder) 1L else 0L)
        }.toMap()
    }

    fun computeEqualPercentagesBps(participantIds: List<String>): Map<String, Long> {
        if (participantIds.isEmpty()) return emptyMap()
        val distinct = participantIds.distinct()
        val count = distinct.size.toLong()
        val base = FULL_PERCENT_BPS / count
        val remainder = (FULL_PERCENT_BPS % count).toInt()
        return distinct.mapIndexed { index, memberId ->
            memberId to (base + if (index < remainder) 1L else 0L)
        }.toMap()
    }

    fun computePercentageSplits(
        totalCents: Long,
        percentagesBps: Map<String, Long>
    ): Map<String, Long> {
        if (totalCents <= 0L || percentagesBps.isEmpty()) return emptyMap()
        val totalBps = percentagesBps.values.sum()
        if (totalBps <= 0L) return emptyMap()

        data class Entry(
            val memberId: String,
            val order: Int,
            val floorShare: Long,
            val remainderNum: Long
        )

        val entries = percentagesBps.entries.mapIndexed { idx, (mid, bps) ->
            val cleanBps = bps.coerceAtLeast(0L)
            val numerator = totalCents * cleanBps
            Entry(
                memberId = mid,
                order = idx,
                floorShare = numerator / totalBps,
                remainderNum = numerator % totalBps
            )
        }

        val floorSum = entries.sumOf { it.floorShare }
        val leftover = (totalCents - floorSum).toInt().coerceAtLeast(0)

        val winners = entries
            .sortedWith(compareByDescending<Entry> { it.remainderNum }.thenBy { it.order })
            .take(leftover)
            .map { it.memberId }
            .toSet()

        return entries.associate { entry ->
            entry.memberId to (entry.floorShare + if (entry.memberId in winners) 1L else 0L)
        }
    }

    fun resolveCurrentUserMemberId(
        trip: Trip,
        currentUserId: String,
        linkedMemberId: String?
    ): String? {
        if (!linkedMemberId.isNullOrBlank() && trip.members.any { it.memberId == linkedMemberId }) {
            return linkedMemberId
        }
        trip.members.firstOrNull { it.linkedUid == currentUserId }?.let { return it.memberId }
        return null
    }

    /**
     * Calculates per-member balances factoring in active (non-deleted) multi-payer bills
     * and recorded settlements, returning BOTH raw pairwise debts (before graph reduction)
     * and smart simplified minimum transfers (after graph reduction).
     */
    fun summarizeTrip(
        trip: Trip,
        bills: List<Bill>,
        currentUserId: String,
        linkedMemberId: String?,
        settlements: List<SettlementRecord> = emptyList()
    ): TripLedgerSummary {
        val activeBills = bills.filter { !it.deleted }
        val membersById = LinkedHashMap<String, TripMember>()
        trip.members.forEach { membersById[it.memberId] = it }

        fun ensureMember(mid: String) {
            if (mid.isNotBlank() && !membersById.containsKey(mid)) {
                membersById[mid] = TripMember(mid, "Former Member")
            }
        }

        activeBills.forEach { bill ->
            ensureMember(bill.paidByMemberId)
            bill.effectivePayers().forEach { ensureMember(it.memberId) }
            bill.splits.keys.forEach { ensureMember(it) }
        }
        settlements.forEach { s ->
            ensureMember(s.fromMemberId)
            ensureMember(s.toMemberId)
        }

        val paidMap = mutableMapOf<String, Long>()
        val shareMap = mutableMapOf<String, Long>()
        val settledPaidMap = mutableMapOf<String, Long>()
        val settledReceivedMap = mutableMapOf<String, Long>()
        membersById.keys.forEach {
            paidMap[it] = 0L
            shareMap[it] = 0L
            settledPaidMap[it] = 0L
            settledReceivedMap[it] = 0L
        }

        // Pairwise directed debt matrix (debtor -> creditor -> amount) before graph reduction
        val pairwise = mutableMapOf<Pair<String, String>, Long>()
        fun addDirectedDebt(fromId: String, toId: String, amount: Long) {
            if (fromId == toId || amount <= 0L) return
            val key = fromId to toId
            pairwise[key] = (pairwise[key] ?: 0L) + amount
        }

        var totalSpend = 0L
        activeBills.forEach { bill ->
            if (bill.amountCents > 0L) {
                totalSpend += bill.amountCents
                val payers = bill.effectivePayers()
                payers.forEach { payer ->
                    paidMap[payer.memberId] = (paidMap[payer.memberId] ?: 0L) + payer.amountCents
                }
                val effectiveSplits = if (bill.splits.isNotEmpty()) {
                    bill.splits
                } else {
                    computeEqualSplits(bill.amountCents, bill.participantIds)
                }
                effectiveSplits.forEach { (memberId, shareCents) ->
                    shareMap[memberId] = (shareMap[memberId] ?: 0L) + shareCents
                }

                // Compute per-bill pairwise edges from debtors to payers
                val billParticipantSet = (payers.map { it.memberId } + effectiveSplits.keys).distinct()
                val billDebtors = mutableListOf<Pair<String, Long>>()
                val billCreditors = mutableListOf<Pair<String, Long>>()
                val billPaidByMember = payers.groupBy { it.memberId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }

                billParticipantSet.forEach { mid ->
                    val net = (billPaidByMember[mid] ?: 0L) - (effectiveSplits[mid] ?: 0L)
                    if (net < 0L) billDebtors.add(mid to -net)
                    else if (net > 0L) billCreditors.add(mid to net)
                }

                var dIdx = 0
                var cIdx = 0
                val dRem = billDebtors.map { it.second }.toMutableList()
                val cRem = billCreditors.map { it.second }.toMutableList()
                while (dIdx < billDebtors.size && cIdx < billCreditors.size) {
                    val amt = min(dRem[dIdx], cRem[cIdx])
                    if (amt > 0L) {
                        addDirectedDebt(billDebtors[dIdx].first, billCreditors[cIdx].first, amt)
                    }
                    dRem[dIdx] -= amt
                    cRem[cIdx] -= amt
                    if (dRem[dIdx] <= 0L) dIdx++
                    if (cRem[cIdx] <= 0L) cIdx++
                }
            }
        }

        var totalSettled = 0L
        settlements.forEach { record ->
            if (record.amountCents > 0L && record.fromMemberId != record.toMemberId) {
                totalSettled += record.amountCents
                settledPaidMap[record.fromMemberId] =
                    (settledPaidMap[record.fromMemberId] ?: 0L) + record.amountCents
                settledReceivedMap[record.toMemberId] =
                    (settledReceivedMap[record.toMemberId] ?: 0L) + record.amountCents
                // A settlement from X -> Y offsets X's pairwise debt to Y (i.e. adds reverse edge Y -> X)
                addDirectedDebt(record.toMemberId, record.fromMemberId, record.amountCents)
            }
        }

        val balances = membersById.values.map { member ->
            val paid = paidMap[member.memberId] ?: 0L
            val share = shareMap[member.memberId] ?: 0L
            val settledOut = settledPaidMap[member.memberId] ?: 0L
            val settledIn = settledReceivedMap[member.memberId] ?: 0L
            val net = (paid - share) + settledOut - settledIn
            MemberBalance(
                memberId = member.memberId,
                displayName = member.displayName,
                totalPaidCents = paid,
                totalShareCents = share,
                settledPaidCents = settledOut,
                settledReceivedCents = settledIn,
                netBalanceCents = net
            )
        }

        // Net out direct reciprocal edges (A->B vs B->A) WITHOUT reducing 3-party chains (A->B->C)
        val rawPairwiseTransfers = buildRawPairwiseTransfers(pairwise, membersById)
        val simplifiedTransfers = simplifyDebts(balances)

        val resolvedMemberId = resolveCurrentUserMemberId(trip, currentUserId, linkedMemberId)
        val userBalance = resolvedMemberId?.let { id -> balances.firstOrNull { it.memberId == id } }
        val userTransfers = resolvedMemberId?.let { id ->
            simplifiedTransfers.filter { it.fromMemberId == id || it.toMemberId == id }
        } ?: emptyList()

        return TripLedgerSummary(
            totalTripSpendCents = totalSpend,
            totalSettledCents = totalSettled,
            billCount = activeBills.size,
            memberBalances = balances,
            simplifiedTransfers = simplifiedTransfers,
            rawPairwiseTransfers = rawPairwiseTransfers,
            currentUserMemberId = resolvedMemberId,
            currentUserBalance = userBalance,
            currentUserTransfers = userTransfers
        )
    }

    private fun buildRawPairwiseTransfers(
        pairwise: Map<Pair<String, String>, Long>,
        membersById: Map<String, TripMember>
    ): List<SettlementTransfer> {
        val visited = mutableSetOf<Pair<String, String>>()
        val result = mutableListOf<SettlementTransfer>()

        pairwise.keys.forEach { (a, b) ->
            val canonical = if (a < b) a to b else b to a
            if (canonical in visited) return@forEach
            visited.add(canonical)

            val forward = pairwise[a to b] ?: 0L
            val reverse = pairwise[b to a] ?: 0L
            val net = forward - reverse
            if (net > 0L) {
                result.add(
                    SettlementTransfer(
                        fromMemberId = a,
                        fromName = membersById[a]?.displayName ?: "Member",
                        toMemberId = b,
                        toName = membersById[b]?.displayName ?: "Member",
                        amountCents = net
                    )
                )
            } else if (net < 0L) {
                result.add(
                    SettlementTransfer(
                        fromMemberId = b,
                        fromName = membersById[b]?.displayName ?: "Member",
                        toMemberId = a,
                        toName = membersById[a]?.displayName ?: "Member",
                        amountCents = -net
                    )
                )
            }
        }
        return result.sortedWith(compareByDescending<SettlementTransfer> { it.amountCents }.thenBy { it.fromName })
    }

    /**
     * Smart Debt Simplification (Graph Reduction): collapses transitive debt chains
     * (e.g. A -> B ₹500 and B -> C ₹500 collapses into A -> C ₹500 directly).
     */
    fun simplifyDebts(balances: List<MemberBalance>): List<SettlementTransfer> {
        data class Node(val memberId: String, val name: String, var amount: Long)

        val debtors = balances
            .filter { it.netBalanceCents < 0L }
            .map { Node(it.memberId, it.displayName, -it.netBalanceCents) }
            .toMutableList()

        val creditors = balances
            .filter { it.netBalanceCents > 0L }
            .map { Node(it.memberId, it.displayName, it.netBalanceCents) }
            .toMutableList()

        val transfers = mutableListOf<SettlementTransfer>()

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortWith(compareByDescending<Node> { it.amount }.thenBy { it.name })
            creditors.sortWith(compareByDescending<Node> { it.amount }.thenBy { it.name })

            val debtor = debtors.first()
            val creditor = creditors.first()
            val settleAmount = min(debtor.amount, creditor.amount)

            if (settleAmount > 0L) {
                transfers.add(
                    SettlementTransfer(
                        fromMemberId = debtor.memberId,
                        fromName = debtor.name,
                        toMemberId = creditor.memberId,
                        toName = creditor.name,
                        amountCents = settleAmount
                    )
                )
            }

            debtor.amount -= settleAmount
            creditor.amount -= settleAmount

            if (debtor.amount <= 0L) debtors.removeAt(0)
            if (creditor.amount <= 0L) creditors.removeAt(0)
        }

        return transfers
    }

    fun groupBillsByDay(bills: List<Bill>): List<DayBillGroup> {
        val active = bills.filter { !it.deleted }
        if (active.isEmpty()) return emptyList()
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val headerFmt = SimpleDateFormat("EEE, MMM d", Locale.US)

        val sortedAsc = active.sortedBy { it.billDateMillis }
        val groupedAsc = LinkedHashMap<String, MutableList<Bill>>()
        sortedAsc.forEach { bill ->
            val millis = if (bill.billDateMillis > 0L) bill.billDateMillis else 0L
            val key = keyFmt.format(Date(millis))
            groupedAsc.getOrPut(key) { mutableListOf() }.add(bill)
        }

        val dayGroupsAsc = groupedAsc.entries.mapIndexed { idx, (dayKey, dayBills) ->
            val sampleMillis = dayBills.first().billDateMillis
            val formatted = if (sampleMillis > 0L) headerFmt.format(Date(sampleMillis)) else "Undated"
            DayBillGroup(
                dayNumber = idx + 1,
                dayKey = dayKey,
                dateHeader = "Day ${idx + 1} • $formatted",
                totalCents = dayBills.sumOf { it.amountCents },
                bills = dayBills.sortedByDescending { it.billDateMillis }
            )
        }
        return dayGroupsAsc.reversed()
    }

    fun computeInsights(trip: Trip, bills: List<Bill>): TripInsights {
        val active = bills.filter { !it.deleted }
        val totalSpend = active.sumOf { it.amountCents }
        val budget = trip.budgetCents.coerceAtLeast(0L)
        val budgetProgress = if (budget > 0L) totalSpend.toFloat() / budget.toFloat() else 0f

        val categoryBreakdown = active
            .groupBy { it.billCategory() }
            .map { (cat, catBills) ->
                val sum = catBills.sumOf { it.amountCents }
                CategorySpend(
                    category = cat,
                    amountCents = sum,
                    percentage = if (totalSpend > 0L) sum.toFloat() / totalSpend.toFloat() else 0f,
                    billCount = catBills.size
                )
            }
            .sortedByDescending { it.amountCents }

        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortFmt = SimpleDateFormat("MMM d", Locale.US)
        val fullFmt = SimpleDateFormat("EEE, MMM d", Locale.US)

        val byDayAsc = active.sortedBy { it.billDateMillis }
            .groupBy { keyFmt.format(Date(it.billDateMillis)) }
            .entries
            .mapIndexed { idx, (key, dayBills) ->
                val sample = dayBills.first().billDateMillis
                DailySpend(
                    dayNumber = idx + 1,
                    dayKey = key,
                    shortLabel = if (sample > 0L) shortFmt.format(Date(sample)) else "D${idx + 1}",
                    fullDateLabel = if (sample > 0L) fullFmt.format(Date(sample)) else "Day ${idx + 1}",
                    amountCents = dayBills.sumOf { it.amountCents },
                    billCount = dayBills.size
                )
            }

        val memberNames = trip.members.associate { it.memberId to it.displayName }
        val paidByTotals = mutableMapOf<String, Long>()
        active.forEach { bill ->
            bill.effectivePayers().forEach { payer ->
                paidByTotals[payer.memberId] = (paidByTotals[payer.memberId] ?: 0L) + payer.amountCents
            }
        }
        val topPaidEntry = paidByTotals.maxByOrNull { it.value }

        val dayCount = byDayAsc.size.coerceAtLeast(1)
        val billCount = active.size.coerceAtLeast(1)

        return TripInsights(
            totalSpendCents = totalSpend,
            budgetCents = budget,
            budgetProgress = budgetProgress,
            averagePerDayCents = if (active.isEmpty()) 0L else totalSpend / dayCount,
            averagePerBillCents = if (active.isEmpty()) 0L else totalSpend / billCount,
            categoryBreakdown = categoryBreakdown,
            dailySpends = byDayAsc,
            topSpenderName = topPaidEntry?.let { memberNames[it.key] ?: "Member" },
            topSpenderCents = topPaidEntry?.value ?: 0L
        )
    }
}
