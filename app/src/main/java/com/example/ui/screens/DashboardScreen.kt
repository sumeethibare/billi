package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.CURRENCY_SYMBOL_INR
import com.example.data.model.SyncConnectionState
import com.example.data.model.Trip
import com.example.data.model.UiState
import com.example.domain.CurrencyFormatter
import com.example.domain.SharedExpenseDraft
import com.example.ui.components.BilliMorphCard
import com.example.ui.components.BilliStaggeredItem
import com.example.ui.components.LocalBilliFeedback
import com.example.ui.components.LocalReduceMotion
import com.example.ui.components.QrScannerScreen
import com.example.ui.components.RoleLockBadge
import com.example.ui.components.SpotlightWalkthroughOverlay
import com.example.ui.components.SyncStatusPill
import com.example.ui.components.billiPressScale
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    revealProgress: Float = 1f,
    showOnboardingWalkthrough: Boolean = false,
    onCompleteWalkthrough: () -> Unit = {},
    pendingSharedDraft: SharedExpenseDraft? = null,
    onDismissSharedDraft: () -> Unit = {},
    onOpenTripWithSharedDraft: (String, SharedExpenseDraft) -> Unit = { _, _ -> },
    onOpenTrip: (String) -> Unit,
    onOpenAccount: () -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalBilliFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateTripDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var qrError by remember { mutableStateOf<String?>(null) }

    // Per-feature POST_NOTIFICATIONS permission launcher (requested ONLY when creating/joining an active trip)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Granted or denied gracefully */ }

    fun maybeRequestNotificationsOnTripEntry() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.syncEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showQrScanner) {
        QrScannerScreen(
            isBusy = actionBusy,
            errorMessage = qrError,
            onQrScanned = { raw ->
                qrError = null
                viewModel.joinTripFromQrPayload(
                    rawPayload = raw,
                    onSuccess = { tripId ->
                        showQrScanner = false
                        maybeRequestNotificationsOnTripEntry()
                        onOpenTrip(tripId)
                    },
                    onError = { err -> qrError = err }
                )
            },
            onClose = {
                qrError = null
                showQrScanner = false
            }
        )
        return
    }

    val blurRadius = if (!reduceMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && revealProgress < 0.99f) {
        ((1f - revealProgress) * 14f).dp
    } else {
        0.dp
    }
    val revealAlpha = if (reduceMotion) 1f else revealProgress.coerceIn(0f, 1f)
    val revealTranslationY = if (reduceMotion) 0f else (1f - revealAlpha) * 32f

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                .alpha(revealAlpha)
                .graphicsLayer { translationY = revealTranslationY },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Column {
                            Text(
                                text = "Billi",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Hi, ${viewModel.currentUserName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        SyncStatusPill(
                            syncStatus = syncStatus,
                            onClick = {
                                feedback?.tick()
                                scope.launch {
                                    val msg = when (syncStatus.state) {
                                        SyncConnectionState.ONLINE_SYNCED -> "Online — all trips synced with cloud"
                                        SyncConnectionState.SYNCING -> "Syncing ${syncStatus.pendingWritesCount} queued updates…"
                                        SyncConnectionState.OFFLINE_QUEUED -> "Offline — ${syncStatus.pendingWritesCount} changes queued for background sync"
                                        SyncConnectionState.OFFLINE_SYNCED -> "Offline — viewing locally cached trips"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                feedback?.tick()
                                qrError = null
                                showQrScanner = true
                            },
                            modifier = Modifier.testTag("dashboard_scan_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = "Scan Trip QR Code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Account & Settings avatar button (dock target of SignInAvatarMorphOverlay)
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                                .billiPressScale(0.92f)
                                .clickable {
                                    feedback?.tick()
                                    onOpenAccount()
                                }
                                .semantics { contentDescription = "Open Account and App Settings" }
                                .testTag("dashboard_account_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!viewModel.currentUserPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(viewModel.currentUserPhotoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Account",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = viewModel.currentUserName.firstOrNull()?.uppercase() ?: "B",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        feedback?.tick()
                        showCreateTripDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("New Trip") },
                    modifier = Modifier
                        .billiPressScale()
                        .testTag("create_trip_fab")
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = tripsState,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "dashboard_state_anim"
                ) { state ->
                    when (state) {
                        UiState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Unable to load trips",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is UiState.Success -> {
                            val trips = state.data
                            val activeTrips = trips.filter { !it.isEnded() }
                            val archivedTrips = trips.filter { it.isEnded() }

                            if (trips.isEmpty()) {
                                EmptyDashboardState(
                                    onCreateTrip = {
                                        feedback?.tick()
                                        showCreateTripDialog = true
                                    },
                                    onScanQr = {
                                        feedback?.tick()
                                        qrError = null
                                        showQrScanner = true
                                    }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 680.dp),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 96.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item(key = "qr_banner") {
                                        BilliStaggeredItem(index = 0) {
                                            BilliMorphCard(
                                                shape = RoundedCornerShape(18.dp),
                                                onClick = {
                                                    feedback?.tick()
                                                    qrError = null
                                                    showQrScanner = true
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("join_trip_qr_banner")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        modifier = Modifier.size(42.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.QrCodeScanner,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(9.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Joining a friend's trip?",
                                                            style = MaterialTheme.typography.titleSmall
                                                        )
                                                        Text(
                                                            text = "Scan their Billi QR code to sync bills live",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (activeTrips.isNotEmpty()) {
                                        item(key = "active_header") {
                                            Text(
                                                text = "Active Trips (${activeTrips.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                                            )
                                        }
                                        itemsIndexed(activeTrips, key = { _, it -> it.id }) { idx, trip ->
                                            BilliStaggeredItem(index = idx + 1) {
                                                TripListCard(
                                                    trip = trip,
                                                    currentUserId = viewModel.currentUserId,
                                                    onClick = {
                                                        feedback?.tick()
                                                        onOpenTrip(trip.id)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (archivedTrips.isNotEmpty()) {
                                        item(key = "archived_header") {
                                            Text(
                                                text = "Archived Trips (${archivedTrips.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                                            )
                                        }
                                        itemsIndexed(archivedTrips, key = { _, it -> it.id }) { idx, trip ->
                                            BilliStaggeredItem(index = idx + activeTrips.size + 1) {
                                                TripListCard(
                                                    trip = trip,
                                                    currentUserId = viewModel.currentUserId,
                                                    onClick = {
                                                        feedback?.tick()
                                                        onOpenTrip(trip.id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2-Step Interactive First-Time Walkthrough Spotlight Coachmark
        if (showOnboardingWalkthrough && revealProgress >= 0.98f) {
            SpotlightWalkthroughOverlay(onComplete = onCompleteWalkthrough)
        }
    }

    // "Share-to-Split" Universal Intent Trip Picker Sheet
    if (pendingSharedDraft != null) {
        val editableActiveTrips = (tripsState as? UiState.Success)?.data
            ?.filter { !it.isEnded() && it.canEdit(viewModel.currentUserId) }
            .orEmpty()
        ModalBottomSheet(
            onDismissRequest = onDismissSharedDraft,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Share-to-Split (${pendingSharedDraft.sourceApp})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pendingSharedDraft.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pendingSharedDraft.rawSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pendingSharedDraft.amountCents > 0L) {
                            Text(
                                text = CurrencyFormatter.format(pendingSharedDraft.amountCents),
                                style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (editableActiveTrips.isEmpty()) {
                    Text(
                        text = "Create an active trip first to split this shared expense.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            onDismissSharedDraft()
                            showCreateTripDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create a Trip")
                    }
                } else {
                    Text(
                        text = "Pick a trip to pre-fill & split in 1 tap:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    editableActiveTrips.forEach { trip ->
                        BilliMorphCard(
                            onClick = {
                                feedback?.celebrateDoublePulse()
                                onOpenTripWithSharedDraft(trip.id, pendingSharedDraft)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_pick_trip_${trip.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(trip.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${trip.members.size} members",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTripDialog) {
        CreateTripDialog(
            creatorName = viewModel.currentUserName,
            isBusy = actionBusy,
            onDismiss = { showCreateTripDialog = false },
            onCreate = { title, budgetCents, members ->
                viewModel.createTrip(
                    title = title,
                    budgetCents = budgetCents,
                    otherMemberNames = members,
                    onSuccess = { tripId ->
                        showCreateTripDialog = false
                        maybeRequestNotificationsOnTripEntry()
                        onOpenTrip(tripId)
                    },
                    onError = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }
        )
    }
}

@Composable
private fun TripListCard(
    trip: Trip,
    currentUserId: String,
    onClick: () -> Unit
) {
    BilliMorphCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_card_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleLockBadge(
                    isCreator = trip.isCreator(currentUserId),
                    isCoHost = trip.isCoHost(currentUserId),
                    isEnded = trip.isEnded()
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (trip.budgetCents > 0L) {
                            "Budget ${CurrencyFormatter.formatCompact(trip.budgetCents)}"
                        } else {
                            "₹ INR"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${trip.members.size} members • Organizer: ${trip.creatorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Open trip",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyDashboardState(
    onCreateTrip: () -> Unit,
    onScanQr: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Image(
            painter = painterResource(id = R.drawable.img_billi_welcome),
            contentDescription = "Start a new group trip ledger",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Ready for your next trip?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create an INR trip ledger as the organizer, scan receipts with AI, or scan a friend's QR code to sync live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .billiPressScale()
                    .testTag("empty_scan_qr_button")
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan QR")
            }
            Button(
                onClick = onCreateTrip,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .billiPressScale()
                    .testTag("empty_create_trip_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Trip")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateTripDialog(
    creatorName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, budgetCents: Long, otherMemberNames: List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var budgetInput by remember { mutableStateOf("") }
    var memberInput by remember { mutableStateOf("") }
    val companions = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(text = "Create New Trip", style = MaterialTheme.typography.headlineMedium)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Trip Name") },
                    placeholder = { Text("e.g. Goa Coastal Weekend") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_trip_title_input")
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) budgetInput = filtered
                    },
                    label = { Text("Optional Budget (INR)") },
                    placeholder = { Text("e.g. 50000") },
                    prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_trip_budget_input")
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Trip Members (You: $creatorName)",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = memberInput,
                        onValueChange = { if (it.length <= 50) memberInput = it },
                        placeholder = { Text("Add companion name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_trip_member_input")
                    )
                    IconButton(
                        onClick = {
                            val clean = memberInput.trim()
                            if (clean.isNotEmpty() && companions.size < 49) {
                                companions.add(clean)
                                memberInput = ""
                            }
                        },
                        modifier = Modifier.testTag("create_trip_add_member_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = "Add companion",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (companions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        companions.forEachIndexed { idx, name ->
                            InputChip(
                                selected = true,
                                onClick = { companions.removeAt(idx) },
                                label = { Text(name) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove $name",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pending = memberInput.trim()
                    val allCompanions = if (pending.isNotEmpty()) companions + pending else companions.toList()
                    val budgetCents = CurrencyFormatter.parseToMinorUnits(budgetInput) ?: 0L
                    onCreate(title, budgetCents, allCompanions)
                },
                enabled = title.trim().isNotEmpty() && !isBusy,
                modifier = Modifier.testTag("create_trip_confirm_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Trip")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}
