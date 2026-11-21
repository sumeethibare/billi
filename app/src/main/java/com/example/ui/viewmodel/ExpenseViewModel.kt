package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BilliRepository
import com.example.data.firebase.SyncStatusMonitor
import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.BillPayer
import com.example.data.model.MultiDayTemplate
import com.example.data.model.PaymentMethod
import com.example.data.model.SplitMode
import com.example.data.model.Trip
import com.example.data.preferences.AppPreferencesRepository
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtSimplifier
import com.example.domain.DuplicateExpenseDetector
import com.example.domain.DuplicateExpenseMatch
import com.example.domain.ExpenseQuickPreset
import com.example.domain.ReceiptImageProcessor
import com.example.domain.ScannedReceiptResult
import com.example.domain.SharedExpenseDraft
import com.example.domain.SmartActivityRecommender
import com.example.domain.SmartExpenseRecommendation
import com.example.domain.SmartReceiptScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface ReceiptScanUiState {
    data object Idle : ReceiptScanUiState
    data object Scanning : ReceiptScanUiState
    data class Success(val result: ScannedReceiptResult) : ReceiptScanUiState
    data class Error(val message: String) : ReceiptScanUiState
}

data class ExpenseEditorUiState(
    val existingBill: Bill? = null,
    val title: String = "",
    val amountInput: String = "",
    val category: BillCategory = BillCategory.FOOD,
    val locationName: String = "",
    val notes: String = "",
    // Multi-Day / Per-Unit Recurring Template (Homestay nights, Scooter/Bike rent, Car rent)
    val multiDayEnabled: Boolean = false,
    val multiDayRateInput: String = "",
    val multiDayCount: Int = 2,
    val multiDayUnitLabel: String = "days",
    val splitIntoDailyBills: Boolean = false,
    val paidByMemberId: String = "",
    val singlePayerMethod: PaymentMethod = PaymentMethod.UPI,
    // Split Payment Mode (Multiple Payers: e.g., Card + Cash/UPI)
    val multiPayerEnabled: Boolean = false,
    val payerAmountInputs: Map<String, String> = emptyMap(),
    val payerMethods: Map<String, PaymentMethod> = emptyMap(),
    val splitMode: SplitMode = SplitMode.EQUAL,
    val selectedMemberIds: List<String> = emptyList(),
    val customExactInputs: Map<String, String> = emptyMap(),
    val percentageInputs: Map<String, String> = emptyMap(),
    val billDateMillis: Long = System.currentTimeMillis(),
    val previewBitmap: Bitmap? = null,
    val newReceiptBase64: String? = null,
    val removeExistingReceipt: Boolean = false,
    val processingImage: Boolean = false,
    val scanState: ReceiptScanUiState = ReceiptScanUiState.Idle,
    // Smart Zero-Typing Recommendations, Share-to-Split & Duplicate Shield
    val recommendation: SmartExpenseRecommendation? = null,
    val sharedSourceBanner: String? = null,
    val duplicateMatch: DuplicateExpenseMatch? = null,
    val duplicateDismissedForBillId: String? = null,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val validationError: String? = null
) {
    val multiDayRateCents: Long
        get() = CurrencyFormatter.parseToMinorUnits(multiDayRateInput) ?: 0L

    val multiDayComputedTotalCents: Long
        get() = multiDayRateCents * multiDayCount.coerceIn(1, 30)
    val totalCents: Long
        get() = CurrencyFormatter.parseToMinorUnits(amountInput) ?: 0L

    val parsedPayers: List<BillPayer>
        get() = if (!multiPayerEnabled) {
            if (paidByMemberId.isNotBlank() && totalCents > 0L) {
                listOf(
                    BillPayer(
                        memberId = paidByMemberId,
                        amountCents = totalCents,
                        paymentMethod = singlePayerMethod.wireValue
                    )
                )
            } else emptyList()
        } else {
            payerAmountInputs.entries.mapNotNull { (mid, raw) ->
                val cents = CurrencyFormatter.parseToMinorUnits(raw) ?: 0L
                if (cents > 0L) {
                    BillPayer(
                        memberId = mid,
                        amountCents = cents,
                        paymentMethod = (payerMethods[mid] ?: PaymentMethod.UPI).wireValue
                    )
                } else null
            }
        }

    val multiPayerSumCents: Long
        get() = parsedPayers.sumOf { it.amountCents }

    val multiPayerDeltaCents: Long
        get() = totalCents - multiPayerSumCents

    val equalSplits: Map<String, Long>
        get() = DebtSimplifier.computeEqualSplits(totalCents, selectedMemberIds)

    val parsedPercentagesBps: Map<String, Long>
        get() = selectedMemberIds.associateWith { mid ->
            CurrencyFormatter.parsePercentToBps(percentageInputs[mid].orEmpty()) ?: 0L
        }

    val totalPercentageBps: Long
        get() = parsedPercentagesBps.values.sum()

    val remainingPercentageBps: Long
        get() = DebtSimplifier.FULL_PERCENT_BPS - totalPercentageBps

    val percentageSplits: Map<String, Long>
        get() = if (totalPercentageBps == DebtSimplifier.FULL_PERCENT_BPS && totalCents > 0L) {
            DebtSimplifier.computePercentageSplits(totalCents, parsedPercentagesBps)
        } else {
            selectedMemberIds.associateWith { mid ->
                val bps = parsedPercentagesBps[mid] ?: 0L
                (totalCents * bps) / DebtSimplifier.FULL_PERCENT_BPS
            }
        }

    val customParsedSplits: Map<String, Long>
        get() = selectedMemberIds.associateWith { mid ->
            CurrencyFormatter.parseToMinorUnits(customExactInputs[mid].orEmpty()) ?: 0L
        }

    val customDeltaCents: Long
        get() = totalCents - customParsedSplits.values.sum()

    val isEditing: Boolean get() = existingBill != null
}

/**
 * ViewModel dedicated to adding, editing, soft-deleting trip expenses,
 * managing Equal / Custom Percentage (%) / Exact ₹ splits,
 * Split Payment Mode (Multi-Payer: Cash + UPI + Card + Points),
 * Smart Zero-Typing Activity Recommendations, Share-to-Split Pre-Fill,
 * Duplicate Expense Shield, and Smart Receipt Scanning via Firebase AI Logic.
 */
class ExpenseViewModel(
    private val repository: BilliRepository,
    private val syncMonitor: SyncStatusMonitor,
    private val preferencesRepository: AppPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseEditorUiState())
    val uiState: StateFlow<ExpenseEditorUiState> = _uiState.asStateFlow()

    private var cachedTrip: Trip? = null
    private var cachedExistingBills: List<Bill> = emptyList()

    fun initialize(
        trip: Trip,
        existingBill: Bill?,
        existingReceiptBitmap: Bitmap?,
        existingBills: List<Bill> = emptyList(),
        lastUsedSplitMode: SplitMode = SplitMode.EQUAL
    ) {
        cachedTrip = trip
        cachedExistingBills = existingBills

        val rec = if (existingBill == null) {
            SmartActivityRecommender.buildRecommendation(
                trip = trip,
                activeBills = existingBills,
                lastUsedSplitMode = lastUsedSplitMode
            )
        } else null

        val defaultPayer = existingBill?.paidByMemberId
            ?.takeIf { id -> trip.members.any { it.memberId == id } }
            ?: rec?.defaultPayerId?.takeIf { id -> trip.members.any { it.memberId == id } }
            ?: trip.members.firstOrNull { it.linkedUid == trip.creatorUid }?.memberId
            ?: trip.members.firstOrNull()?.memberId.orEmpty()

        val existingPayers = existingBill?.effectivePayers().orEmpty()
        val isMultiPayer = existingPayers.size > 1
        val initialSingleMethod = existingPayers.firstOrNull()?.method() ?: PaymentMethod.UPI
        val initialPayerInputs = buildMap<String, String> {
            existingPayers.forEach { p ->
                put(p.memberId, CurrencyFormatter.formatInput(p.amountCents))
            }
        }
        val initialPayerMethods = buildMap<String, PaymentMethod> {
            trip.members.forEach { m -> put(m.memberId, PaymentMethod.UPI) }
            existingPayers.forEach { p -> put(p.memberId, p.method()) }
        }

        val initialParticipants = existingBill?.participantIds
            ?.filter { id -> trip.members.any { it.memberId == id } }
            ?.takeIf { it.isNotEmpty() }
            ?: rec?.defaultParticipantIds?.takeIf { it.isNotEmpty() }
            ?: trip.members.map { it.memberId }

        val initialCustom = buildMap<String, String> {
            existingBill?.splits?.forEach { (mid, cents) ->
                put(mid, CurrencyFormatter.formatInput(cents))
            }
        }

        val initialPct = buildMap<String, String> {
            val savedPct = existingBill?.splitPercentages.orEmpty()
            if (savedPct.isNotEmpty()) {
                savedPct.forEach { (mid, bps) ->
                    put(mid, CurrencyFormatter.formatPercentInput(bps))
                }
            } else {
                val evenBps = DebtSimplifier.computeEqualPercentagesBps(initialParticipants)
                evenBps.forEach { (mid, bps) ->
                    put(mid, CurrencyFormatter.formatPercentInput(bps))
                }
            }
        }

        _uiState.value = ExpenseEditorUiState(
            existingBill = existingBill,
            title = existingBill?.title.orEmpty(),
            amountInput = existingBill?.let { CurrencyFormatter.formatInput(it.amountCents) }.orEmpty(),
            category = existingBill?.billCategory() ?: rec?.defaultCategory ?: BillCategory.FOOD,
            locationName = existingBill?.locationName.orEmpty(),
            notes = existingBill?.notes.orEmpty(),
            paidByMemberId = defaultPayer,
            singlePayerMethod = initialSingleMethod,
            multiPayerEnabled = isMultiPayer,
            payerAmountInputs = initialPayerInputs,
            payerMethods = initialPayerMethods,
            splitMode = existingBill?.billSplitMode() ?: rec?.defaultSplitMode ?: SplitMode.EQUAL,
            selectedMemberIds = initialParticipants,
            customExactInputs = initialCustom,
            percentageInputs = initialPct,
            billDateMillis = existingBill?.billDateMillis?.takeIf { it > 0L } ?: System.currentTimeMillis(),
            previewBitmap = existingReceiptBitmap,
            newReceiptBase64 = null,
            removeExistingReceipt = false,
            scanState = ReceiptScanUiState.Idle,
            recommendation = rec,
            sharedSourceBanner = null,
            duplicateMatch = null,
            duplicateDismissedForBillId = null,
            validationError = null
        )
    }

    fun applyQuickPreset(preset: ExpenseQuickPreset) {
        _uiState.update { state ->
            val evenBps = DebtSimplifier.computeEqualPercentagesBps(preset.participantIds)
            val evenCents = DebtSimplifier.computeEqualSplits(preset.suggestedAmountCents, preset.participantIds)
            state.copy(
                title = preset.title,
                amountInput = CurrencyFormatter.formatInput(preset.suggestedAmountCents),
                category = preset.category,
                locationName = preset.locationHint.ifBlank { state.locationName },
                paidByMemberId = preset.paidByMemberId,
                multiPayerEnabled = false,
                splitMode = preset.splitMode,
                selectedMemberIds = preset.participantIds,
                percentageInputs = preset.participantIds.associateWith {
                    CurrencyFormatter.formatPercentInput(evenBps[it] ?: 0L)
                },
                customExactInputs = preset.participantIds.associateWith {
                    CurrencyFormatter.formatInput(evenCents[it] ?: 0L)
                },
                validationError = null
            )
        }
        recheckDuplicate()
    }

    fun applySharedDraft(context: Context, draft: SharedExpenseDraft) {
        viewModelScope.launch {
            val base64 = draft.previewBitmap?.let { bmp ->
                withContext(Dispatchers.IO) {
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 76, baos)
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }
            }
            _uiState.update { state ->
                val banner = buildString {
                    append("Auto-filled from ${draft.sourceApp}")
                    if (draft.lineItems.isNotEmpty()) {
                        append(" • ${draft.lineItems.size} items detected")
                    }
                }
                state.copy(
                    title = draft.title.ifBlank { state.title },
                    amountInput = if (draft.amountCents > 0L) {
                        CurrencyFormatter.formatInput(draft.amountCents)
                    } else state.amountInput,
                    category = draft.category,
                    locationName = draft.locationName.ifBlank { state.locationName },
                    previewBitmap = draft.previewBitmap ?: state.previewBitmap,
                    newReceiptBase64 = base64 ?: state.newReceiptBase64,
                    removeExistingReceipt = false,
                    sharedSourceBanner = banner,
                    validationError = null
                )
            }
            recheckDuplicate()
        }
    }

    private fun recheckDuplicate() {
        val trip = cachedTrip ?: return
        val state = _uiState.value
        val match = DuplicateExpenseDetector.findPotentialDuplicate(
            trip = trip,
            existingBills = cachedExistingBills,
            editingBillId = state.existingBill?.id,
            candidateAmountCents = state.totalCents,
            candidateTitle = state.title,
            candidateCategory = state.category,
            candidateDateMillis = state.billDateMillis
        )
        val filteredMatch = if (match != null && match.existingBill.id == state.duplicateDismissedForBillId) {
            null
        } else {
            match
        }
        _uiState.update { it.copy(duplicateMatch = filteredMatch) }
    }

    fun dismissDuplicateWarning() {
        _uiState.update { state ->
            state.copy(
                duplicateDismissedForBillId = state.duplicateMatch?.existingBill?.id,
                duplicateMatch = null
            )
        }
    }

    fun mergeIntoExistingDuplicate(
        trip: Trip,
        onMerged: (billId: String, message: String) -> Unit
    ) {
        val match = _uiState.value.duplicateMatch ?: return
        val target = match.existingBill
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, validationError = null) }
            val isOnline = syncMonitor.isCurrentlyOnline()
            val mergedLocation = target.locationName.ifBlank { state.locationName.trim() }
            val mergedTitle = if (state.title.trim().length > target.title.length) {
                state.title.trim()
            } else target.title
            val result = repository.saveBill(
                trip = trip,
                existingBill = target,
                title = mergedTitle,
                category = target.billCategory(),
                amountCents = target.amountCents,
                locationName = mergedLocation,
                paidByMemberId = target.paidByMemberId,
                payers = target.effectivePayers(),
                splitMode = target.billSplitMode(),
                participantIds = target.participantIds,
                splits = target.splits,
                splitPercentages = target.splitPercentages,
                billDateMillis = target.billDateMillis,
                newReceiptBase64 = state.newReceiptBase64,
                removeExistingReceipt = false,
                isOnline = isOnline
            )
            _uiState.update { it.copy(saving = false) }
            result.onSuccess { billId ->
                onMerged(billId, "Merged with \"${target.title}\" — duplicate avoided!")
            }.onFailure { err ->
                _uiState.update { it.copy(validationError = err.localizedMessage ?: "Unable to merge expense") }
            }
        }
    }

    fun attachLoadedExistingReceipt(bitmap: Bitmap?) {
        if (bitmap == null) return
        _uiState.update { state ->
            if (state.previewBitmap == null && !state.removeExistingReceipt && state.newReceiptBase64 == null) {
                state.copy(previewBitmap = bitmap)
            } else {
                state
            }
        }
    }

    fun updateTitle(value: String) {
        if (value.length <= 120) {
            _uiState.update { it.copy(title = value, validationError = null) }
            recheckDuplicate()
        }
    }

    fun updateAmountInput(raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.update { it.copy(amountInput = filtered, validationError = null) }
            recheckDuplicate()
        }
    }

    fun updateCategory(category: BillCategory) {
        _uiState.update { it.copy(category = category) }
        recheckDuplicate()
    }

    fun updateLocation(value: String) {
        if (value.length <= 160) _uiState.update { it.copy(locationName = value) }
    }

    fun updateBillDate(millis: Long) {
        if (millis > 0L) {
            _uiState.update { it.copy(billDateMillis = millis) }
            recheckDuplicate()
        }
    }

    fun updatePaidBy(memberId: String) {
        _uiState.update { it.copy(paidByMemberId = memberId) }
    }

    fun updateSinglePayerMethod(method: PaymentMethod) {
        _uiState.update { it.copy(singlePayerMethod = method) }
    }

    fun toggleMultiPayer(enabled: Boolean) {
        _uiState.update { state ->
            val nextInputs = state.payerAmountInputs.toMutableMap()
            val nextMethods = state.payerMethods.toMutableMap()
            if (enabled && nextInputs.values.all { it.isBlank() } && state.totalCents > 0L) {
                val primary = state.paidByMemberId
                if (primary.isNotBlank()) {
                    nextInputs[primary] = CurrencyFormatter.formatInput(state.totalCents)
                    nextMethods[primary] = state.singlePayerMethod
                }
            }
            state.copy(
                multiPayerEnabled = enabled,
                payerAmountInputs = nextInputs,
                payerMethods = nextMethods,
                validationError = null
            )
        }
    }

    fun updatePayerAmount(memberId: String, raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.update { state ->
                state.copy(
                    payerAmountInputs = state.payerAmountInputs + (memberId to filtered),
                    validationError = null
                )
            }
        }
    }

    fun updatePayerMethod(memberId: String, method: PaymentMethod) {
        _uiState.update { state ->
            state.copy(payerMethods = state.payerMethods + (memberId to method))
        }
    }

    fun fitRemainingPayerToMember(memberId: String) {
        _uiState.update { state ->
            val currentCents = CurrencyFormatter.parseToMinorUnits(state.payerAmountInputs[memberId].orEmpty()) ?: 0L
            val target = (currentCents + state.multiPayerDeltaCents).coerceAtLeast(0L)
            state.copy(
                payerAmountInputs = state.payerAmountInputs + (memberId to CurrencyFormatter.formatInput(target)),
                validationError = null
            )
        }
    }

    fun toggleParticipant(memberId: String) {
        _uiState.update { state ->
            val current = state.selectedMemberIds
            val updated = if (memberId in current) {
                if (current.size > 1) current - memberId else current
            } else {
                current + memberId
            }
            val nextCustom = state.customExactInputs.toMutableMap()
            val nextPct = state.percentageInputs.toMutableMap()
            if (state.splitMode == SplitMode.PERCENTAGE) {
                val evenBps = DebtSimplifier.computeEqualPercentagesBps(updated)
                updated.forEach { mid ->
                    if (!nextPct.containsKey(mid)) {
                        nextPct[mid] = CurrencyFormatter.formatPercentInput(evenBps[mid] ?: 0L)
                    }
                }
            } else if (state.splitMode == SplitMode.CUSTOM && state.totalCents > 0L) {
                val evenCents = DebtSimplifier.computeEqualSplits(state.totalCents, updated)
                updated.forEach { mid ->
                    if (!nextCustom.containsKey(mid)) {
                        nextCustom[mid] = CurrencyFormatter.formatInput(evenCents[mid] ?: 0L)
                    }
                }
            }
            state.copy(
                selectedMemberIds = updated,
                customExactInputs = nextCustom,
                percentageInputs = nextPct,
                validationError = null
            )
        }
    }

    fun selectAllParticipants(allMemberIds: List<String>) {
        _uiState.update { state ->
            val evenBps = DebtSimplifier.computeEqualPercentagesBps(allMemberIds)
            val evenCents = DebtSimplifier.computeEqualSplits(state.totalCents, allMemberIds)
            state.copy(
                selectedMemberIds = allMemberIds,
                percentageInputs = allMemberIds.associateWith {
                    CurrencyFormatter.formatPercentInput(evenBps[it] ?: 0L)
                },
                customExactInputs = if (state.splitMode == SplitMode.CUSTOM && state.totalCents > 0L) {
                    allMemberIds.associateWith { CurrencyFormatter.formatInput(evenCents[it] ?: 0L) }
                } else state.customExactInputs,
                validationError = null
            )
        }
    }

    fun updateSplitMode(mode: SplitMode) {
        _uiState.update { state ->
            val nextCustom = state.customExactInputs.toMutableMap()
            val nextPct = state.percentageInputs.toMutableMap()
            if (mode == SplitMode.CUSTOM && state.totalCents > 0L) {
                val missing = state.selectedMemberIds.any { !nextCustom.containsKey(it) }
                if (missing) {
                    val seeded = DebtSimplifier.computeEqualSplits(state.totalCents, state.selectedMemberIds)
                    state.selectedMemberIds.forEach { mid ->
                        if (!nextCustom.containsKey(mid)) {
                            nextCustom[mid] = CurrencyFormatter.formatInput(seeded[mid] ?: 0L)
                        }
                    }
                }
            } else if (mode == SplitMode.PERCENTAGE) {
                val sumBps = state.selectedMemberIds.sumOf {
                    CurrencyFormatter.parsePercentToBps(nextPct[it].orEmpty()) ?: 0L
                }
                if (sumBps == 0L || state.selectedMemberIds.any { !nextPct.containsKey(it) }) {
                    val evenBps = DebtSimplifier.computeEqualPercentagesBps(state.selectedMemberIds)
                    state.selectedMemberIds.forEach { mid ->
                        nextPct[mid] = CurrencyFormatter.formatPercentInput(evenBps[mid] ?: 0L)
                    }
                }
            }
            state.copy(
                splitMode = mode,
                customExactInputs = nextCustom,
                percentageInputs = nextPct,
                validationError = null
            )
        }
    }

    fun updateCustomExactSplit(memberId: String, raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.update { state ->
                state.copy(
                    customExactInputs = state.customExactInputs + (memberId to filtered),
                    validationError = null
                )
            }
        }
    }

    fun distributeCustomEvenly() {
        _uiState.update { state ->
            val even = DebtSimplifier.computeEqualSplits(state.totalCents, state.selectedMemberIds)
            state.copy(
                customExactInputs = state.selectedMemberIds.associateWith {
                    CurrencyFormatter.formatInput(even[it] ?: 0L)
                },
                validationError = null
            )
        }
    }

    fun fitRemainingExactToMember(memberId: String) {
        _uiState.update { state ->
            val currentCents = state.customParsedSplits[memberId] ?: 0L
            val target = (currentCents + state.customDeltaCents).coerceAtLeast(0L)
            state.copy(
                customExactInputs = state.customExactInputs + (memberId to CurrencyFormatter.formatInput(target)),
                validationError = null
            )
        }
    }

    fun updatePercentageSplit(memberId: String, raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.update { state ->
                state.copy(
                    percentageInputs = state.percentageInputs + (memberId to filtered),
                    validationError = null
                )
            }
        }
    }

    fun distributePercentagesEvenly() {
        _uiState.update { state ->
            val even = DebtSimplifier.computeEqualPercentagesBps(state.selectedMemberIds)
            state.copy(
                percentageInputs = state.selectedMemberIds.associateWith {
                    CurrencyFormatter.formatPercentInput(even[it] ?: 0L)
                },
                validationError = null
            )
        }
    }

    fun fitRemainingPercentageToMember(memberId: String) {
        _uiState.update { state ->
            val currentBps = state.parsedPercentagesBps[memberId] ?: 0L
            val targetBps = (currentBps + state.remainingPercentageBps)
                .coerceIn(0L, DebtSimplifier.FULL_PERCENT_BPS)
            state.copy(
                percentageInputs = state.percentageInputs + (memberId to CurrencyFormatter.formatPercentInput(targetBps)),
                validationError = null
            )
        }
    }

    fun processReceiptUri(context: Context, uri: Uri, autoScan: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingImage = true, validationError = null, scanState = ReceiptScanUiState.Idle) }
            val processed = ReceiptImageProcessor.processUriToCompressedBase64(context, uri)
            if (processed != null) {
                _uiState.update {
                    it.copy(
                        processingImage = false,
                        previewBitmap = processed.first,
                        newReceiptBase64 = processed.second,
                        removeExistingReceipt = false
                    )
                }
                if (autoScan) {
                    runSmartReceiptScan()
                }
            } else {
                _uiState.update {
                    it.copy(
                        processingImage = false,
                        validationError = "Could not process receipt image. Please try another photo."
                    )
                }
            }
        }
    }

    fun removeReceipt() {
        _uiState.update {
            it.copy(
                previewBitmap = null,
                newReceiptBase64 = null,
                removeExistingReceipt = true,
                scanState = ReceiptScanUiState.Idle
            )
        }
    }

    fun runSmartReceiptScan() {
        val bitmap = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(scanState = ReceiptScanUiState.Scanning, validationError = null) }
            val result = SmartReceiptScanner.scanReceipt(bitmap)
            result.onSuccess { scanned ->
                _uiState.update { state ->
                    state.copy(
                        title = scanned.title.ifBlank { state.title },
                        amountInput = if (scanned.amountCents > 0L) {
                            CurrencyFormatter.formatInput(scanned.amountCents)
                        } else state.amountInput,
                        category = scanned.category,
                        locationName = scanned.locationName.ifBlank { state.locationName },
                        scanState = ReceiptScanUiState.Success(scanned)
                    )
                }
                recheckDuplicate()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        scanState = ReceiptScanUiState.Error(
                            err.localizedMessage ?: "Smart scan could not read this receipt."
                        )
                    )
                }
            }
        }
    }

    fun setValidationError(message: String?) {
        _uiState.update { it.copy(validationError = message) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value.take(500), validationError = null) }
    }

    fun toggleMultiDay(enabled: Boolean) {
        _uiState.update { state ->
            val nextRate = if (enabled && state.multiDayRateInput.isBlank() && state.totalCents > 0L) {
                CurrencyFormatter.formatInput(state.totalCents)
            } else {
                state.multiDayRateInput
            }
            val next = state.copy(
                multiDayEnabled = enabled,
                multiDayRateInput = nextRate,
                validationError = null
            )
            if (enabled && next.multiDayComputedTotalCents > 0L) {
                next.copy(amountInput = CurrencyFormatter.formatInput(next.multiDayComputedTotalCents))
            } else {
                next
            }
        }
        recheckDuplicate()
    }

    fun applyMultiDayTemplate(template: MultiDayTemplate) {
        val total = template.suggestedRateCents * template.defaultDays
        _uiState.update { state ->
            state.copy(
                multiDayEnabled = true,
                title = template.label,
                category = template.defaultCategory,
                multiDayRateInput = CurrencyFormatter.formatInput(template.suggestedRateCents),
                multiDayCount = template.defaultDays,
                multiDayUnitLabel = template.unitLabel,
                amountInput = CurrencyFormatter.formatInput(total),
                notes = "${template.defaultDays} ${template.unitLabel} × ${CurrencyFormatter.format(template.suggestedRateCents)}/${template.unitLabel.removeSuffix("s")}",
                validationError = null
            )
        }
        recheckDuplicate()
    }

    fun updateMultiDayRate(raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return
        _uiState.update { state ->
            val updated = state.copy(multiDayRateInput = filtered, validationError = null)
            val total = updated.multiDayComputedTotalCents
            val rateFmt = if (updated.multiDayRateCents > 0L) CurrencyFormatter.format(updated.multiDayRateCents) else ""
            updated.copy(
                amountInput = if (total > 0L) CurrencyFormatter.formatInput(total) else updated.amountInput,
                notes = if (rateFmt.isNotBlank()) {
                    "${updated.multiDayCount} ${updated.multiDayUnitLabel} × $rateFmt"
                } else updated.notes
            )
        }
        recheckDuplicate()
    }

    fun updateMultiDayCount(days: Int) {
        val clamped = days.coerceIn(1, 30)
        _uiState.update { state ->
            val updated = state.copy(multiDayCount = clamped, validationError = null)
            val total = updated.multiDayComputedTotalCents
            val rateFmt = if (updated.multiDayRateCents > 0L) CurrencyFormatter.format(updated.multiDayRateCents) else ""
            updated.copy(
                amountInput = if (total > 0L) CurrencyFormatter.formatInput(total) else updated.amountInput,
                notes = if (rateFmt.isNotBlank()) {
                    "$clamped ${updated.multiDayUnitLabel} × $rateFmt"
                } else updated.notes
            )
        }
        recheckDuplicate()
    }

    fun toggleSplitIntoDailyBills(enabled: Boolean) {
        _uiState.update { it.copy(splitIntoDailyBills = enabled) }
    }

    fun saveExpense(
        trip: Trip,
        onSaved: (billId: String, message: String) -> Unit
    ) {
        val state = _uiState.value
        val cleanTitle = state.title.trim()
        if (cleanTitle.isEmpty()) {
            _uiState.update { it.copy(validationError = "Please enter an expense title.") }
            return
        }
        val totalCents = state.totalCents
        if (totalCents <= 0L) {
            _uiState.update { it.copy(validationError = "Please enter a valid INR amount greater than ₹0.") }
            return
        }
        if (state.selectedMemberIds.isEmpty()) {
            _uiState.update { it.copy(validationError = "Select at least one participant to split with.") }
            return
        }

        // Validate Split Payment Mode (multi-payer)
        val finalPayers = state.parsedPayers
        if (state.multiPayerEnabled) {
            if (finalPayers.isEmpty() || state.multiPayerDeltaCents != 0L) {
                _uiState.update {
                    it.copy(
                        validationError = "Split payments must total ${CurrencyFormatter.format(totalCents)}."
                    )
                }
                return
            }
        }

        val finalSplits: Map<String, Long>
        val finalPercentages: Map<String, Long>
        when (state.splitMode) {
            SplitMode.EQUAL -> {
                finalSplits = state.equalSplits
                finalPercentages = emptyMap()
            }
            SplitMode.PERCENTAGE -> {
                if (state.remainingPercentageBps != 0L) {
                    _uiState.update {
                        it.copy(
                            validationError = "Percentages must total exactly 100% (currently ${
                                CurrencyFormatter.formatPercentInput(state.totalPercentageBps).ifBlank { "0" }
                            }%)."
                        )
                    }
                    return
                }
                finalPercentages = state.parsedPercentagesBps
                finalSplits = DebtSimplifier.computePercentageSplits(totalCents, finalPercentages)
            }
            SplitMode.CUSTOM -> {
                if (state.customDeltaCents != 0L) {
                    _uiState.update {
                        it.copy(
                            validationError = "Custom amounts must equal ${CurrencyFormatter.format(totalCents)}."
                        )
                    }
                    return
                }
                finalSplits = state.customParsedSplits
                finalPercentages = emptyMap()
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, validationError = null) }
            preferencesRepository?.setLastUsedSplitMode(state.splitMode.wireValue)
            val isOnline = syncMonitor.isCurrentlyOnline()

            // Multi-Day Recurring batch mode: create consecutive day-stamped bills
            val shouldBatchDaily = !state.isEditing &&
                state.multiDayEnabled &&
                state.splitIntoDailyBills &&
                state.multiDayCount > 1 &&
                state.multiDayRateCents > 0L

            if (shouldBatchDaily) {
                val perDayCents = state.multiDayRateCents
                val perDaySplits = when (state.splitMode) {
                    SplitMode.PERCENTAGE -> DebtSimplifier.computePercentageSplits(perDayCents, finalPercentages)
                    else -> DebtSimplifier.computeEqualSplits(perDayCents, state.selectedMemberIds)
                }
                val perDayPayers = listOf(
                    BillPayer(
                        memberId = finalPayers.maxByOrNull { it.amountCents }?.memberId ?: state.paidByMemberId,
                        amountCents = perDayCents,
                        paymentMethod = state.singlePayerMethod.wireValue
                    )
                )
                val unitSingular = state.multiDayUnitLabel.removeSuffix("s").replaceFirstChar { it.uppercase() }
                var lastSavedId = ""
                var firstError: Throwable? = null

                for (dayIdx in 0 until state.multiDayCount) {
                    val dayMillis = state.billDateMillis + (dayIdx * 86_400_000L)
                    val res = repository.saveBill(
                        trip = trip,
                        existingBill = null,
                        title = "$cleanTitle ($unitSingular ${dayIdx + 1}/${state.multiDayCount})",
                        category = state.category,
                        amountCents = perDayCents,
                        locationName = state.locationName.trim(),
                        notes = state.notes.trim(),
                        paidByMemberId = perDayPayers.first().memberId,
                        payers = perDayPayers,
                        splitMode = if (state.splitMode == SplitMode.PERCENTAGE) SplitMode.PERCENTAGE else SplitMode.EQUAL,
                        participantIds = state.selectedMemberIds,
                        splits = perDaySplits,
                        splitPercentages = if (state.splitMode == SplitMode.PERCENTAGE) finalPercentages else emptyMap(),
                        billDateMillis = dayMillis,
                        newReceiptBase64 = if (dayIdx == 0) state.newReceiptBase64 else null,
                        removeExistingReceipt = false,
                        isOnline = isOnline
                    )
                    res.onSuccess { lastSavedId = it }.onFailure { if (firstError == null) firstError = it }
                }
                _uiState.update { it.copy(saving = false) }
                if (firstError != null && lastSavedId.isBlank()) {
                    _uiState.update {
                        it.copy(validationError = firstError?.localizedMessage ?: "Unable to save daily bills")
                    }
                } else {
                    val msg = if (isOnline) {
                        "Added ${state.multiDayCount} daily bills & synced"
                    } else {
                        "Saved ${state.multiDayCount} daily bills offline — queued for sync"
                    }
                    onSaved(lastSavedId, msg)
                }
                return@launch
            }

            val result = repository.saveBill(
                trip = trip,
                existingBill = state.existingBill,
                title = cleanTitle,
                category = state.category,
                amountCents = totalCents,
                locationName = state.locationName.trim(),
                notes = state.notes.trim(),
                paidByMemberId = finalPayers.maxByOrNull { it.amountCents }?.memberId ?: state.paidByMemberId,
                payers = finalPayers,
                splitMode = state.splitMode,
                participantIds = state.selectedMemberIds,
                splits = finalSplits,
                splitPercentages = finalPercentages,
                billDateMillis = state.billDateMillis,
                newReceiptBase64 = state.newReceiptBase64,
                removeExistingReceipt = state.removeExistingReceipt,
                isOnline = isOnline
            )
            _uiState.update { it.copy(saving = false) }
            result.onSuccess { billId ->
                val msg = when {
                    !isOnline -> "Saved offline — queued for background sync"
                    state.isEditing -> "Expense updated & synced"
                    else -> "Expense added & synced"
                }
                onSaved(billId, msg)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(validationError = err.localizedMessage ?: "Unable to save expense")
                }
            }
        }
    }

    fun deleteCurrentExpense(
        trip: Trip,
        onDeleted: (Bill, String) -> Unit
    ) {
        val target = _uiState.value.existingBill ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, validationError = null) }
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.softDeleteBill(trip, target, isOnline)
            _uiState.update { it.copy(deleting = false) }
            result.onSuccess {
                onDeleted(
                    target,
                    if (isOnline) "Moved to Deleted Bills" else "Move to trash queued for sync"
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(validationError = err.localizedMessage ?: "Failed to delete expense")
                }
            }
        }
    }
}
