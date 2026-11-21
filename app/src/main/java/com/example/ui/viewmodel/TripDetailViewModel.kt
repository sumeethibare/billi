package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BilliRepository
import com.example.data.firebase.SyncStatusMonitor
import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.DayBillGroup
import com.example.data.model.SettlementRecord
import com.example.data.model.SyncStatusUi
import com.example.data.model.SyncedTripUser
import com.example.data.model.Trip
import com.example.data.model.TripInsights
import com.example.data.model.TripLedgerSummary
import com.example.data.model.UiState
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.WidgetSnapshot
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtSimplifier
import com.example.domain.TripPdfExporter
import com.example.widget.refreshBilliWidgets
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class TripDetailContent(
    val trip: Trip,
    val bills: List<Bill>,
    val deletedBills: List<Bill>,
    val dayGroups: List<DayBillGroup>,
    val settlements: List<SettlementRecord>,
    val syncedUsers: List<SyncedTripUser>,
    val summary: TripLedgerSummary,
    val insights: TripInsights,
    val linkedMemberId: String?,
    val recentLocations: List<String>,
    val pendingBillIds: Set<String> = emptySet(),
    val pendingSettlementIds: Set<String> = emptySet()
)

data class BillFilterState(
    val searchQuery: String = "",
    val selectedCategory: BillCategory? = null,
    val selectedPayerId: String? = null,
    val onlyMyBills: Boolean = false
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() || selectedCategory != null || selectedPayerId != null || onlyMyBills
}

class TripDetailViewModel(
    private val appContext: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val repository: BilliRepository,
    private val syncMonitor: SyncStatusMonitor,
    private val tripId: String,
    val currentUserId: String
) : ViewModel() {

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _exportingPdf = MutableStateFlow(false)
    val exportingPdf: StateFlow<Boolean> = _exportingPdf.asStateFlow()

    private val _receiptCache = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val receiptCache: StateFlow<Map<String, Bitmap>> = _receiptCache.asStateFlow()

    private val _filterState = MutableStateFlow(BillFilterState())
    val filterState: StateFlow<BillFilterState> = _filterState.asStateFlow()

    val syncStatus: StateFlow<SyncStatusUi> = syncMonitor.syncStatusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SyncStatusUi(isOnline = syncMonitor.isCurrentlyOnline())
        )

    val detailState: StateFlow<UiState<TripDetailContent>> = combine(
        repository.observeTrip(tripId, currentUserId).onEach { payload ->
            syncMonitor.reportPendingWrites("trip_$tripId", payload.pendingWritesCount, payload.isFromCache)
        },
        repository.observeBills(tripId, currentUserId).onEach { payload ->
            syncMonitor.reportPendingWrites("bills_$tripId", payload.pendingWritesCount, payload.isFromCache)
        },
        repository.observeSettlements(tripId, currentUserId).onEach { payload ->
            syncMonitor.reportPendingWrites("settlements_$tripId", payload.pendingWritesCount, payload.isFromCache)
        },
        repository.observeSyncedUsers(tripId, currentUserId),
        repository.observeUserTripLink(currentUserId, tripId)
    ) { tripPayload, billsPayload, settlementsPayload, syncedUsers, linkedMemberId ->
        val trip = tripPayload.data
        if (trip == null) {
            UiState.Error("Trip not found or access was removed.")
        } else {
            val activeBills = billsPayload.data.filter { !it.deleted }
            val deletedBills = billsPayload.data.filter { it.deleted }
                .sortedByDescending { it.deletedAtMillis }
            val settlements = settlementsPayload.data
            val summary = DebtSimplifier.summarizeTrip(
                trip = trip,
                bills = activeBills,
                currentUserId = currentUserId,
                linkedMemberId = linkedMemberId,
                settlements = settlements
            )
            val insights = DebtSimplifier.computeInsights(trip, activeBills)
            val dayGroups = DebtSimplifier.groupBillsByDay(activeBills)
            val recentLocs = activeBills
                .map { it.locationName.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)
            UiState.Success(
                TripDetailContent(
                    trip = trip,
                    bills = activeBills,
                    deletedBills = deletedBills,
                    dayGroups = dayGroups,
                    settlements = settlements,
                    syncedUsers = syncedUsers,
                    summary = summary,
                    insights = insights,
                    linkedMemberId = summary.currentUserMemberId,
                    recentLocations = recentLocs,
                    pendingBillIds = billsPayload.pendingDocIds,
                    pendingSettlementIds = settlementsPayload.pendingDocIds
                )
            )
        }
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing trip $tripId", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load trip details"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        observeSyncEvents()
        registerCurrentSyncedUser()
        syncWidgetSnapshotLoop()
    }

    private fun syncWidgetSnapshotLoop() {
        viewModelScope.launch {
            detailState.collect { uiState ->
                val content = (uiState as? UiState.Success)?.data ?: return@collect
                val net = content.summary.currentUserNetCents ?: 0L
                val subtitle = when {
                    content.summary.currentUserNetCents == null -> "Link your profile in trip"
                    net > 0L -> "You are owed"
                    net < 0L -> "You owe"
                    else -> "All settled up"
                }
                val amountText = CurrencyFormatter.format(kotlin.math.abs(net))
                val pendingCount = content.pendingBillIds.size + content.pendingSettlementIds.size
                preferencesRepository.saveWidgetSnapshot(
                    WidgetSnapshot(
                        tripId = content.trip.id,
                        tripTitle = content.trip.title,
                        balanceSubtitle = subtitle,
                        balanceAmountText = amountText,
                        netBalanceCents = net,
                        pendingSyncCount = pendingCount
                    )
                )
                refreshBilliWidgets(appContext)
            }
        }
    }

    private fun registerCurrentSyncedUser() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser ?: return@launch
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Traveler"
            repository.upsertSyncedTripUser(
                tripId = tripId,
                displayName = displayName,
                photoUrl = user.photoUrl?.toString(),
                linkedMemberId = null,
                isOnline = syncMonitor.isCurrentlyOnline()
            )
        }
    }

    private fun observeSyncEvents() {
        viewModelScope.launch {
            var previousOnline: Boolean? = null
            var hadPending = false
            syncStatus.collect { status ->
                if (previousOnline == true && !status.isOnline) {
                    _toastEvents.tryEmit("Offline mode — edits will queue for background sync")
                } else if (previousOnline == false && status.isOnline) {
                    _toastEvents.tryEmit("Back online — trip ledger synced")
                } else if (status.isOnline && hadPending && status.pendingWritesCount == 0) {
                    _toastEvents.tryEmit("Queued offline updates synced to cloud")
                }
                previousOnline = status.isOnline
                hadPending = status.pendingWritesCount > 0
            }
        }
    }

    fun emitToast(message: String) {
        _toastEvents.tryEmit(message)
    }

    // --- FILTER CONTROLS ---

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun toggleCategoryFilter(category: BillCategory) {
        val cur = _filterState.value.selectedCategory
        _filterState.value = _filterState.value.copy(
            selectedCategory = if (cur == category) null else category
        )
    }

    fun togglePayerFilter(memberId: String) {
        val cur = _filterState.value.selectedPayerId
        _filterState.value = _filterState.value.copy(
            selectedPayerId = if (cur == memberId) null else memberId
        )
    }

    fun toggleOnlyMyBills() {
        _filterState.value = _filterState.value.copy(onlyMyBills = !_filterState.value.onlyMyBills)
    }

    fun clearFilters() {
        _filterState.value = BillFilterState()
    }

    // --- BILL SOFT-DELETE, RESTORE, PERMANENT DELETE & SETTLEMENT MUTATIONS ---

    fun softDeleteBill(
        trip: Trip,
        bill: Bill,
        onSoftDeleted: (Bill) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.softDeleteBill(trip, bill, isOnline)
            _busy.value = false
            result.onSuccess {
                onSoftDeleted(bill)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to delete bill")
            }
        }
    }

    fun restoreBill(trip: Trip, bill: Bill) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.restoreBill(trip, bill, isOnline)
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit("Restored \"${bill.title}\"")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not restore bill")
            }
        }
    }

    fun permanentlyDeleteBill(trip: Trip, bill: Bill) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.deleteBill(trip, bill, isOnline)
            _busy.value = false
            result.onSuccess {
                _receiptCache.value = _receiptCache.value - bill.id
                _toastEvents.tryEmit(
                    if (isOnline) "Bill permanently removed" else "Permanent delete queued for sync"
                )
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to permanently delete bill")
            }
        }
    }

    fun recordSettlement(
        trip: Trip,
        fromMemberId: String,
        toMemberId: String,
        amountCents: Long,
        note: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.recordSettlement(
                trip = trip,
                fromMemberId = fromMemberId,
                toMemberId = toMemberId,
                amountCents = amountCents,
                note = note,
                isOnline = isOnline
            )
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(
                    if (isOnline) "Settlement recorded & balances updated" else "Settlement queued for sync"
                )
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to record settlement")
            }
        }
    }

    fun deleteSettlement(trip: Trip, settlementId: String) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.deleteSettlement(trip, settlementId, isOnline)
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit("Recorded settlement removed")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not remove settlement")
            }
        }
    }

    // --- BUDGET, CO-HOST, OWNERSHIP & MEMBERS ---

    fun updateBudget(trip: Trip, budgetCents: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.updateTripBudget(trip, budgetCents, isOnline)
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(
                    if (budgetCents > 0L) "Trip budget updated" else "Trip budget cleared"
                )
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not update budget")
            }
        }
    }

    fun toggleCoHost(trip: Trip, targetUid: String, targetName: String) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val isAlreadyCoHost = targetUid in trip.coHostUids
            val nextCoHosts = if (isAlreadyCoHost) {
                trip.coHostUids - targetUid
            } else {
                trip.coHostUids + targetUid
            }
            val result = repository.updateTripCoHosts(trip, nextCoHosts, isOnline)
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(
                    if (isAlreadyCoHost) "Removed $targetName as Co-Host" else "Promoted $targetName to Co-Host"
                )
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Unable to update co-hosts")
            }
        }
    }

    fun transferOwnership(
        trip: Trip,
        newCreatorUid: String,
        newCreatorName: String,
        keepSelfAsCoHost: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.transferTripOwnership(
                trip = trip,
                newCreatorUid = newCreatorUid,
                newCreatorName = newCreatorName,
                keepSelfAsCoHost = keepSelfAsCoHost,
                isOnline = isOnline
            )
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit("Ownership transferred to $newCreatorName")
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not transfer ownership")
            }
        }
    }

    fun addMember(trip: Trip, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.addTripMember(trip, name, isOnline)
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(
                    if (isOnline) "Added $name to trip" else "Added $name offline — queued for sync"
                )
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not add member")
            }
        }
    }

    fun removeMember(trip: Trip, memberId: String, bills: List<Bill>) {
        viewModelScope.launch {
            _busy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.removeTripMember(trip, memberId, bills, isOnline)
            _busy.value = false
            result.onSuccess {
                _toastEvents.tryEmit("Member removed")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Cannot remove member")
            }
        }
    }

    fun selectMyMemberProfile(memberId: String) {
        viewModelScope.launch {
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.setUserLinkedMember(tripId, memberId, isOnline)
            result.onSuccess {
                _toastEvents.tryEmit("Personal balance linked")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to link profile")
            }
        }
    }

    fun loadReceiptIfNeeded(billId: String, forceReload: Boolean = false) {
        if (!forceReload && _receiptCache.value.containsKey(billId)) return
        viewModelScope.launch {
            val bmp = repository.loadReceiptBitmap(tripId, billId)
            if (bmp != null) {
                _receiptCache.value = _receiptCache.value + (billId to bmp)
            }
        }
    }

    fun invalidateReceiptCache(billId: String) {
        _receiptCache.value = _receiptCache.value - billId
    }

    fun endTripAndExportPdf(
        context: Context,
        content: TripDetailContent,
        onPdfReady: (File) -> Unit
    ) {
        viewModelScope.launch {
            _exportingPdf.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val endResult = repository.endTrip(content.trip, isOnline)
            if (endResult.isFailure) {
                _exportingPdf.value = false
                _toastEvents.tryEmit(endResult.exceptionOrNull()?.localizedMessage ?: "Could not end trip")
                return@launch
            }
            val archivedTrip = content.trip.copy(status = "ended")
            exportPdfInternal(context, content.copy(trip = archivedTrip), onPdfReady)
        }
    }

    fun exportArchivalPdfOnly(
        context: Context,
        content: TripDetailContent,
        onPdfReady: (File) -> Unit
    ) {
        viewModelScope.launch {
            _exportingPdf.value = true
            exportPdfInternal(context, content, onPdfReady)
        }
    }

    private suspend fun exportPdfInternal(
        context: Context,
        content: TripDetailContent,
        onPdfReady: (File) -> Unit
    ) {
        try {
            val receiptMap = mutableMapOf<String, Bitmap>()
            content.bills.filter { it.receiptAttached }.forEach { bill ->
                val bmp = _receiptCache.value[bill.id] ?: repository.loadReceiptBitmap(tripId, bill.id)
                if (bmp != null) {
                    receiptMap[bill.id] = bmp
                }
            }
            val file = TripPdfExporter.generateArchivalPdf(
                context = context,
                trip = content.trip,
                bills = content.bills,
                summary = content.summary,
                receiptBitmaps = receiptMap,
                settlements = content.settlements
            )
            _exportingPdf.value = false
            onPdfReady(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _exportingPdf.value = false
            Log.e(TAG, "PDF generation failed", e)
            _toastEvents.tryEmit("Failed to generate archival PDF: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private companion object {
        const val TAG = "TripDetailVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
