package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BilliRepository
import com.example.data.firebase.SyncStatusMonitor
import com.example.data.model.SyncStatusUi
import com.example.data.model.Trip
import com.example.data.model.UiState
import com.example.domain.QrCodeCodec
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: BilliRepository,
    private val syncMonitor: SyncStatusMonitor,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserPhotoUrl: String?
) : ViewModel() {

    private val _syncEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val syncEvents: SharedFlow<String> = _syncEvents.asSharedFlow()

    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

    val syncStatus: StateFlow<SyncStatusUi> = syncMonitor.syncStatusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SyncStatusUi(isOnline = syncMonitor.isCurrentlyOnline())
        )

    val tripsState: StateFlow<UiState<List<Trip>>> = repository.observeTrips(currentUserId)
        .onEach { payload ->
            syncMonitor.reportPendingWrites(
                sourceKey = "dashboard_trips",
                pendingCount = payload.pendingWritesCount,
                fromCache = payload.isFromCache
            )
        }
        .map<_, UiState<List<Trip>>> { UiState.Success(it.data) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing trips", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load trips"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        observeSyncTransitions()
        viewModelScope.launch {
            repository.ensureUserProfile(
                displayName = currentUserName,
                photoUrl = currentUserPhotoUrl ?: Firebase.auth.currentUser?.photoUrl?.toString(),
                isOnline = syncMonitor.isCurrentlyOnline()
            )
        }
    }

    private fun observeSyncTransitions() {
        viewModelScope.launch {
            var previousOnline: Boolean? = null
            var hadPendingWrites = false
            syncStatus.collect { status ->
                if (previousOnline == true && !status.isOnline) {
                    _syncEvents.tryEmit("You're offline — changes will queue for background sync")
                } else if (previousOnline == false && status.isOnline) {
                    _syncEvents.tryEmit("Back online — syncing trip ledgers")
                } else if (status.isOnline && hadPendingWrites && status.pendingWritesCount == 0) {
                    _syncEvents.tryEmit("All queued changes synced to cloud")
                }
                previousOnline = status.isOnline
                hadPendingWrites = status.pendingWritesCount > 0
            }
        }
    }

    fun createTrip(
        title: String,
        budgetCents: Long,
        otherMemberNames: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _actionBusy.value = true
            val isOnline = syncMonitor.isCurrentlyOnline()
            val result = repository.createTrip(
                title = title,
                budgetCents = budgetCents,
                creatorDisplayName = currentUserName,
                otherMemberNames = otherMemberNames,
                isOnline = isOnline
            )
            _actionBusy.value = false
            result.onSuccess { tripId ->
                if (!isOnline) {
                    _syncEvents.tryEmit("Trip saved offline — queued for background sync")
                }
                onSuccess(tripId)
            }.onFailure { err ->
                onError(err.localizedMessage ?: "Failed to create trip")
            }
        }
    }

    fun joinTripFromQrPayload(
        rawPayload: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val invite = QrCodeCodec.parseInvitePayload(rawPayload)
        if (invite == null) {
            onError("Unrecognized QR code. Scan a valid Billi trip QR or enter TripID:CODE.")
            return
        }
        if (!syncMonitor.isCurrentlyOnline()) {
            onError("Connect to the internet to join and sync a new trip via QR code.")
            return
        }
        viewModelScope.launch {
            _actionBusy.value = true
            val result = repository.joinTripViaQr(invite.tripId, invite.joinCode)
            _actionBusy.value = false
            result.onSuccess { tripId ->
                _syncEvents.tryEmit("Joined trip — syncing latest ledger from cloud")
                onSuccess(tripId)
            }.onFailure { err ->
                onError(err.localizedMessage ?: "Unable to join trip")
            }
        }
    }

    private companion object {
        const val TAG = "DashboardVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
