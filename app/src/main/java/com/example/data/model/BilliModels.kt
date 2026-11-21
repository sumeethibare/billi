package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val DEFAULT_CURRENCY_CODE = "INR"
const val CURRENCY_SYMBOL_INR = "₹"

enum class PaymentMethod(val wireValue: String, val label: String) {
    UPI("upi", "UPI"),
    CASH("cash", "Cash"),
    CARD("card", "Card"),
    POINTS("points", "Card Points");

    companion object {
        fun fromWire(raw: String?): PaymentMethod =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: UPI
    }
}

@Keep
@IgnoreExtraProperties
data class BillPayer(
    val memberId: String = "",
    val amountCents: Long = 0L,
    val paymentMethod: String = PaymentMethod.UPI.wireValue
) {
    fun method(): PaymentMethod = PaymentMethod.fromWire(paymentMethod)

    fun toMap(): Map<String, Any> = mapOf(
        "memberId" to memberId,
        "amountCents" to amountCents,
        "paymentMethod" to paymentMethod
    )
}

@Keep
@IgnoreExtraProperties
data class TripMember(
    val memberId: String = "",
    val displayName: String = "",
    val linkedUid: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "memberId" to memberId,
        "displayName" to displayName,
        "linkedUid" to linkedUid
    )
}

@Keep
@IgnoreExtraProperties
data class Trip(
    @DocumentId val id: String = "",
    val creatorUid: String = "",
    val creatorName: String = "",
    val title: String = "",
    val currencyCode: String = DEFAULT_CURRENCY_CODE,
    val budgetCents: Long = 0L,
    val status: String = TripStatus.ACTIVE.wireValue,
    val joinCode: String = "",
    val memberUids: List<String> = emptyList(),
    val coHostUids: List<String> = emptyList(),
    val members: List<TripMember> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val endedAt: Timestamp? = null
) {
    fun tripStatus(): TripStatus = TripStatus.fromWire(status)
    fun isCreator(uid: String): Boolean = creatorUid == uid
    fun isCoHost(uid: String): Boolean = uid in coHostUids && creatorUid != uid
    fun canEdit(uid: String): Boolean =
        (creatorUid == uid || uid in coHostUids) && tripStatus() == TripStatus.ACTIVE
    fun canManageTripAdmin(uid: String): Boolean =
        creatorUid == uid && tripStatus() == TripStatus.ACTIVE
    fun isEnded(): Boolean = tripStatus() == TripStatus.ENDED
}

enum class TripStatus(val wireValue: String) {
    ACTIVE("active"),
    ENDED("ended");

    companion object {
        fun fromWire(raw: String): TripStatus =
            entries.firstOrNull { it.wireValue == raw } ?: ACTIVE
    }
}

enum class BillCategory(val wireValue: String, val label: String) {
    FOOD("food", "Food & Drink"),
    STAY("stay", "Lodging"),
    TRANSPORT("transport", "Transport"),
    ACTIVITY("activity", "Activities"),
    SHOPPING("shopping", "Shopping"),
    OTHER("other", "Other");

    companion object {
        fun fromWire(raw: String): BillCategory =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

enum class SplitMode(val wireValue: String, val label: String) {
    EQUAL("equal", "Equal"),
    PERCENTAGE("percentage", "Custom %"),
    CUSTOM("custom", "Exact ₹");

    companion object {
        fun fromWire(raw: String): SplitMode =
            entries.firstOrNull { it.wireValue == raw } ?: EQUAL
    }
}

@Keep
@IgnoreExtraProperties
data class Bill(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val creatorUid: String = "",
    val title: String = "",
    val category: String = BillCategory.FOOD.wireValue,
    val amountCents: Long = 0L,
    val locationName: String = "",
    val notes: String = "",
    val paidByMemberId: String = "",
    val payers: List<BillPayer> = emptyList(),
    val splitMode: String = SplitMode.EQUAL.wireValue,
    val participantIds: List<String> = emptyList(),
    val splits: Map<String, Long> = emptyMap(),
    val splitPercentages: Map<String, Long> = emptyMap(), // Basis points (10000 = 100.00%)
    val receiptAttached: Boolean = false,
    val billDateMillis: Long = 0L,
    val deleted: Boolean = false,
    val deletedAtMillis: Long = 0L,
    val deletedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun billCategory(): BillCategory = BillCategory.fromWire(category)
    fun billSplitMode(): SplitMode = SplitMode.fromWire(splitMode)

    /**
     * Returns the list of effective payers for accounting. Falls back to the single
     * [paidByMemberId] for backwards compatibility with single-payer bills.
     */
    fun effectivePayers(): List<BillPayer> {
        val valid = payers.filter { it.memberId.isNotBlank() && it.amountCents > 0L }
        return if (valid.isNotEmpty()) {
            valid
        } else if (paidByMemberId.isNotBlank() && amountCents > 0L) {
            listOf(BillPayer(memberId = paidByMemberId, amountCents = amountCents))
        } else {
            emptyList()
        }
    }

    fun isMultiPayer(): Boolean = payers.count { it.amountCents > 0L } > 1
}

@Keep
@IgnoreExtraProperties
data class BillReceipt(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val billId: String = "",
    val creatorUid: String = "",
    val imageBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ReactionType(val wireValue: String, val emoji: String, val label: String) {
    VERIFIED("verified", "✅", "Verified"),
    CHECK("check", "👀", "Check"),
    THANKS("thanks", "🙏", "Thanks"),
    RECEIPT("receipt", "🧾", "Got it");

    companion object {
        fun fromWire(raw: String?): ReactionType =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: VERIFIED
    }
}

@Keep
@IgnoreExtraProperties
data class BillComment(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val billId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BillReaction(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val billId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val emoji: String = ReactionType.VERIFIED.wireValue,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun reactionType(): ReactionType = ReactionType.fromWire(emoji)
}

enum class DashboardFilterMode(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    OWED("You're Owed"),
    OWE("You Owe")
}

enum class DashboardSortOrder(val label: String) {
    RECENT("Recent"),
    HIGHEST_BALANCE("Top Balance"),
    ALPHABETICAL("A–Z")
}

data class MultiDayTemplate(
    val id: String,
    val label: String,
    val unitLabel: String,
    val defaultCategory: BillCategory,
    val suggestedRateCents: Long,
    val defaultDays: Int
) {
    companion object {
        val presets: List<MultiDayTemplate> = listOf(
            MultiDayTemplate("stay", "Homestay / Villa", "nights", BillCategory.STAY, 350_000L, 2),
            MultiDayTemplate("scooter", "Scooter / Bike Rent", "days", BillCategory.TRANSPORT, 50_000L, 3),
            MultiDayTemplate("car", "Self-Drive Car", "days", BillCategory.TRANSPORT, 220_000L, 2),
            MultiDayTemplate("pool", "Daily Chai & Snacks Pool", "days", BillCategory.FOOD, 40_000L, 3)
        )
    }
}

@Keep
@IgnoreExtraProperties
data class SettlementRecord(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val creatorUid: String = "",
    val fromMemberId: String = "",
    val toMemberId: String = "",
    val amountCents: Long = 0L,
    val note: String = "",
    val settledAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SyncedTripUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tripId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val linkedMemberId: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val defaultCurrencyCode: String = DEFAULT_CURRENCY_CODE,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserTripLink(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val linkedMemberId: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class MemberBalance(
    val memberId: String,
    val displayName: String,
    val totalPaidCents: Long,
    val totalShareCents: Long,
    val settledPaidCents: Long = 0L,
    val settledReceivedCents: Long = 0L,
    val netBalanceCents: Long // >0 means owed to member; <0 means member owes others
)

data class SettlementTransfer(
    val fromMemberId: String,
    val fromName: String,
    val toMemberId: String,
    val toName: String,
    val amountCents: Long
)

data class TripLedgerSummary(
    val totalTripSpendCents: Long = 0L,
    val totalSettledCents: Long = 0L,
    val billCount: Int = 0,
    val memberBalances: List<MemberBalance> = emptyList(),
    val simplifiedTransfers: List<SettlementTransfer> = emptyList(),
    val rawPairwiseTransfers: List<SettlementTransfer> = emptyList(),
    val currentUserMemberId: String? = null,
    val currentUserBalance: MemberBalance? = null,
    val currentUserTransfers: List<SettlementTransfer> = emptyList()
) {
    val currentUserNetCents: Long?
        get() = currentUserBalance?.netBalanceCents
}

data class CategorySpend(
    val category: BillCategory,
    val amountCents: Long,
    val percentage: Float,
    val billCount: Int
)

data class DailySpend(
    val dayNumber: Int,
    val dayKey: String,
    val shortLabel: String,
    val fullDateLabel: String,
    val amountCents: Long,
    val billCount: Int
)

data class DayBillGroup(
    val dayNumber: Int,
    val dayKey: String,
    val dateHeader: String,
    val totalCents: Long,
    val bills: List<Bill>
)

data class TripInsights(
    val totalSpendCents: Long = 0L,
    val budgetCents: Long = 0L,
    val budgetProgress: Float = 0f,
    val averagePerDayCents: Long = 0L,
    val averagePerBillCents: Long = 0L,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val dailySpends: List<DailySpend> = emptyList(),
    val topSpenderName: String? = null,
    val topSpenderCents: Long = 0L
)

enum class SyncConnectionState {
    ONLINE_SYNCED,
    SYNCING,
    OFFLINE_SYNCED,
    OFFLINE_QUEUED
}

data class SyncStatusUi(
    val isOnline: Boolean = true,
    val pendingWritesCount: Int = 0,
    val isFromCache: Boolean = false
) {
    val state: SyncConnectionState
        get() = when {
            !isOnline && pendingWritesCount > 0 -> SyncConnectionState.OFFLINE_QUEUED
            !isOnline -> SyncConnectionState.OFFLINE_SYNCED
            pendingWritesCount > 0 -> SyncConnectionState.SYNCING
            else -> SyncConnectionState.ONLINE_SYNCED
        }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
