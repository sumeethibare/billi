package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.CURRENCY_SYMBOL_INR
import com.example.data.model.SettlementTransfer
import com.example.data.model.SplitMode
import com.example.data.model.SyncConnectionState
import com.example.data.model.SyncedTripUser
import com.example.data.model.Trip
import com.example.data.model.UiState
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtSimplifier
import com.example.domain.SharedExpenseDraft
import com.example.service.TripBubbleService
import com.example.ui.components.BilliMorphCard
import com.example.ui.components.LocalBilliFeedback
import com.example.ui.components.SyncStatusPill
import com.example.ui.components.TripInsightsTabContent
import com.example.ui.components.TripMembersTabContent
import com.example.ui.components.TripSettlementsTabContent
import com.example.ui.components.TripSummaryCard
import com.example.ui.components.billiPressScale
import com.example.ui.components.categoryAccentColor
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.theme.MoneyTitleStyle
import com.example.ui.viewmodel.BillFilterState
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.TripDetailContent
import com.example.ui.viewmodel.TripDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel,
    expenseViewModel: ExpenseViewModel,
    lastUsedSplitMode: SplitMode = SplitMode.EQUAL,
    autoOpenAddBill: Boolean = false,
    initialSharedDraft: SharedExpenseDraft? = null,
    onConsumeLaunchExtras: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalBilliFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val exportingPdf by viewModel.exportingPdf.collectAsStateWithLifecycle()
    val receiptCache by viewModel.receiptCache.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var showAddBillEditor by remember { mutableStateOf(false) }
    var activeSharedDraft by remember { mutableStateOf<SharedExpenseDraft?>(null) }
    var inspectingBill by remember { mutableStateOf<Bill?>(null) }
    var viewingReceiptBill by remember { mutableStateOf<Bill?>(null) }
    var showEndTripConfirm by remember { mutableStateOf(false) }
    var exportedPdfFile by remember { mutableStateOf<File?>(null) }
    var recordingTransfer by remember { mutableStateOf<SettlementTransfer?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var transferringToUser by remember { mutableStateOf<SyncedTripUser?>(null) }
    var showTrashBinSheet by remember { mutableStateOf(false) }
    var showTripWrapped by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val srcFile = exportedPdfFile
        if (destUri != null && srcFile != null && srcFile.exists()) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            srcFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                snackbarHostState.showSnackbar(
                    if (saved) "Archival PDF saved to device" else "Could not save PDF"
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentContent = (detailState as? UiState.Success)?.data
    val canEdit = currentContent?.trip?.canEdit(viewModel.currentUserId) == true

    // Auto-open Add Bill editor when launched from Glance Widget "+" or Share-to-Split
    LaunchedEffect(autoOpenAddBill, initialSharedDraft, currentContent != null, canEdit) {
        if ((autoOpenAddBill || initialSharedDraft != null) && currentContent != null && canEdit) {
            activeSharedDraft = initialSharedDraft
            showAddBillEditor = true
            onConsumeLaunchExtras()
        }
    }

    fun showUndoDeletedBillSnackbar(trip: Trip, deletedBill: Bill) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted \"${deletedBill.title}\"",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                feedback?.celebrateDoublePulse()
                viewModel.restoreBill(trip, deletedBill)
            }
        }
    }

    // Full-screen interactive Trip Wrapped story cards
    if (showTripWrapped && currentContent != null) {
        TripWrappedScreen(
            trip = currentContent.trip,
            bills = currentContent.bills,
            settlements = currentContent.settlements,
            onClose = { showTripWrapped = false }
        )
        return
    }

    // Full-screen Expense Editor backed by ExpenseViewModel
    if ((showAddBillEditor || editingBill != null) && currentContent != null && canEdit) {
        val targetEdit = editingBill
        LaunchedEffect(targetEdit?.id) {
            if (targetEdit != null && targetEdit.receiptAttached) {
                viewModel.loadReceiptIfNeeded(targetEdit.id)
            }
        }
        ExpenseEditorScreen(
            viewModel = expenseViewModel,
            trip = currentContent.trip,
            existingBill = targetEdit,
            existingReceiptBitmap = targetEdit?.let { receiptCache[it.id] },
            existingBills = currentContent.bills,
            lastUsedSplitMode = lastUsedSplitMode,
            initialSharedDraft = if (targetEdit == null) activeSharedDraft else null,
            recentLocations = currentContent.recentLocations,
            onSaved = { savedBillId, msg ->
                viewModel.invalidateReceiptCache(savedBillId)
                viewModel.loadReceiptIfNeeded(savedBillId, forceReload = true)
                showAddBillEditor = false
                editingBill = null
                activeSharedDraft = null
                viewModel.emitToast(msg)
            },
            onDeleted = { deletedBill, _ ->
                showAddBillEditor = false
                editingBill = null
                activeSharedDraft = null
                showUndoDeletedBillSnackbar(currentContent.trip, deletedBill)
            },
            onDismiss = {
                showAddBillEditor = false
                editingBill = null
                activeSharedDraft = null
            }
        )
        return
    }

    val navItems = listOf(
        Triple("Bills", Icons.Rounded.ReceiptLong, "tab_bills"),
        Triple("Insights", Icons.Rounded.Insights, "tab_insights"),
        Triple("Settle", Icons.Rounded.Balance, "tab_settlements"),
        Triple("Members", Icons.Rounded.Group, "tab_members")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                feedback?.tick()
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("trip_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to trips")
                        }
                    },
                    title = {
                        Text(
                            text = currentContent?.trip?.title ?: "Trip Ledger",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    },
                    actions = {
                        // Creator / Co-Host Soft-Delete Trash Bin button
                        if (canEdit && currentContent != null && currentContent.deletedBills.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    feedback?.tick()
                                    showTrashBinSheet = true
                                },
                                modifier = Modifier.testTag("trip_trash_bin_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text("${currentContent.deletedBills.size}")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestoreFromTrash,
                                        contentDescription = "Deleted Bills Trash Bin",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Floating Trip Bubble quick launcher (requests overlay permission ONLY on tap)
                        if (canEdit && currentContent != null && !currentContent.trip.isEnded()) {
                            IconButton(
                                onClick = {
                                    feedback?.tick()
                                    if (!TripBubbleService.canDrawOverlay(context)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Enable \"Display over other apps\" to pin the ₹+ Trip Bubble.")
                                        }
                                        runCatching {
                                            context.startActivity(TripBubbleService.overlaySettingsIntent(context))
                                        }
                                    } else {
                                        TripBubbleService.start(
                                            context,
                                            currentContent.trip.id,
                                            currentContent.trip.title
                                        )
                                        feedback?.celebrateDoublePulse()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Floating Trip Bubble pinned for ${currentContent.trip.title}")
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("trip_bubble_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BubbleChart,
                                    contentDescription = "Pin Floating Trip Bubble",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        SyncStatusPill(
                            syncStatus = syncStatus,
                            onClick = {
                                feedback?.tick()
                                scope.launch {
                                    val msg = when (syncStatus.state) {
                                        SyncConnectionState.ONLINE_SYNCED -> "Online — trip ledger synced"
                                        SyncConnectionState.SYNCING -> "Syncing ${syncStatus.pendingWritesCount} queued changes…"
                                        SyncConnectionState.OFFLINE_QUEUED -> "Offline — ${syncStatus.pendingWritesCount} edits queued for background sync"
                                        SyncConnectionState.OFFLINE_SYNCED -> "Offline — browsing locally cached trip"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            },
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        navItems.forEachIndexed { idx, (label, icon, tag) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = {
                                    feedback?.tick()
                                    selectedTab = idx
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = canEdit && selectedTab == 0,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            feedback?.tick()
                            showAddBillEditor = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Add Bill") },
                        modifier = Modifier
                            .billiPressScale()
                            .testTag("add_bill_fab")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        navItems.forEachIndexed { idx, (label, icon, tag) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = {
                                    feedback?.tick()
                                    selectedTab = idx
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (val state = detailState) {
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
                                    text = "Trip Unavailable",
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
                            val content = state.data
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                                label = "trip_tab_morph"
                            ) { tab ->
                                when (tab) {
                                    0 -> BillsDayGroupedTabContent(
                                        content = content,
                                        filterState = filterState,
                                        currentUserId = viewModel.currentUserId,
                                        canEdit = canEdit,
                                        onSearchChange = { viewModel.updateSearchQuery(it) },
                                        onToggleCategory = { viewModel.toggleCategoryFilter(it) },
                                        onTogglePayer = { viewModel.togglePayerFilter(it) },
                                        onToggleMyBills = { viewModel.toggleOnlyMyBills() },
                                        onClearFilters = { viewModel.clearFilters() },
                                        onSelectMyMember = { viewModel.selectMyMemberProfile(it) },
                                        onInspectBill = { bill ->
                                            if (bill.receiptAttached) viewModel.loadReceiptIfNeeded(bill.id)
                                            inspectingBill = bill
                                        },
                                        onViewReceipt = { bill ->
                                            viewModel.loadReceiptIfNeeded(bill.id)
                                            viewingReceiptBill = bill
                                        },
                                        onEditBill = { bill ->
                                            if (bill.receiptAttached) viewModel.loadReceiptIfNeeded(bill.id)
                                            editingBill = bill
                                        },
                                        onDeleteBill = { bill ->
                                            feedback?.tick()
                                            viewModel.softDeleteBill(content.trip, bill) { deleted ->
                                                showUndoDeletedBillSnackbar(content.trip, deleted)
                                            }
                                        }
                                    )
                                    1 -> TripInsightsTabContent(
                                        content = content,
                                        canManageBudget = content.trip.canManageTripAdmin(viewModel.currentUserId),
                                        onEditBudgetClick = { showBudgetDialog = true },
                                        onOpenTripWrapped = { showTripWrapped = true }
                                    )
                                    2 -> TripSettlementsTabContent(
                                        content = content,
                                        currentUserId = viewModel.currentUserId,
                                        canEdit = canEdit,
                                        exportingPdf = exportingPdf,
                                        onMarkTransferPaid = { recordingTransfer = it },
                                        onDeleteSettlement = { rec ->
                                            viewModel.deleteSettlement(content.trip, rec.id)
                                        },
                                        onEndTripClick = { showEndTripConfirm = true },
                                        onExportPdfOnly = {
                                            viewModel.exportArchivalPdfOnly(context, content) { file ->
                                                exportedPdfFile = file
                                            }
                                        }
                                    )
                                    else -> TripMembersTabContent(
                                        content = content,
                                        currentUserId = viewModel.currentUserId,
                                        isBusy = busy,
                                        onAddMember = { name, clear -> viewModel.addMember(content.trip, name, clear) },
                                        onRemoveMember = { memberId ->
                                            viewModel.removeMember(content.trip, memberId, content.bills)
                                        },
                                        onToggleCoHost = { uid, name ->
                                            viewModel.toggleCoHost(content.trip, uid, name)
                                        },
                                        onRequestOwnershipTransfer = { user -> transferringToUser = user },
                                        onCopiedMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- CREATOR / CO-HOST "DELETED BILLS" RECOVERY TRASH BIN SHEET ---
    if (showTrashBinSheet && currentContent != null && canEdit) {
        ModalBottomSheet(
            onDismissRequest = { showTrashBinSheet = false },
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
                        imageVector = Icons.Rounded.RestoreFromTrash,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text("Deleted Bills", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Restore accidentally deleted bills before ending & exporting the trip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (currentContent.deletedBills.isEmpty()) {
                    Text(
                        text = "Trash bin is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    currentContent.deletedBills.forEach { trashBill ->
                        BilliMorphCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trash_bill_${trashBill.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trashBill.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = CurrencyFormatter.format(trashBill.amountCents),
                                        style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            feedback?.celebrateDoublePulse()
                                            viewModel.restoreBill(currentContent.trip, trashBill)
                                        },
                                        modifier = Modifier.testTag("restore_bill_${trashBill.id}")
                                    ) {
                                        Text("Restore")
                                    }
                                    IconButton(
                                        onClick = {
                                            feedback?.tick()
                                            viewModel.permanentlyDeleteBill(currentContent.trip, trashBill)
                                        },
                                        modifier = Modifier.testTag("perm_delete_bill_${trashBill.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteForever,
                                            contentDescription = "Permanently delete",
                                            tint = MaterialTheme.colorScheme.error
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

    // --- RECORD SETTLEMENT PAYMENT DIALOG ---
    recordingTransfer?.let { transfer ->
        val trip = currentContent?.trip
        if (trip != null && canEdit) {
            var amountInput by remember(transfer) {
                mutableStateOf(CurrencyFormatter.formatInput(transfer.amountCents))
            }
            var noteInput by remember(transfer) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { if (!busy) recordingTransfer = null },
                title = { Text("Record Settlement") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${transfer.fromName} paid ${transfer.toName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) amountInput = filtered
                            },
                            label = { Text("Amount Paid (INR)") },
                            prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settlement_amount_input")
                        )
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { if (it.length <= 160) noteInput = it },
                            label = { Text("Payment Note (optional)") },
                            placeholder = { Text("e.g. UPI, GPay, Cash") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settlement_note_input")
                        )
                    }
                },
                confirmButton = {
                    val cents = CurrencyFormatter.parseToMinorUnits(amountInput) ?: 0L
                    Button(
                        onClick = {
                            feedback?.celebrateDoublePulse()
                            viewModel.recordSettlement(
                                trip = trip,
                                fromMemberId = transfer.fromMemberId,
                                toMemberId = transfer.toMemberId,
                                amountCents = cents,
                                note = noteInput,
                                onDone = { recordingTransfer = null }
                            )
                        },
                        enabled = cents > 0L && !busy,
                        modifier = Modifier.testTag("confirm_record_settlement_button")
                    ) {
                        Text("Confirm Paid")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordingTransfer = null }, enabled = !busy) { Text("Cancel") }
                }
            )
        }
    }

    // --- EDIT TRIP BUDGET DIALOG ---
    if (showBudgetDialog && currentContent != null) {
        val trip = currentContent.trip
        var budgetInput by remember(trip.budgetCents) {
            mutableStateOf(if (trip.budgetCents > 0L) CurrencyFormatter.formatInput(trip.budgetCents) else "")
        }
        AlertDialog(
            onDismissRequest = { if (!busy) showBudgetDialog = false },
            title = { Text("Trip Budget (INR)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set an overall spending target for ${trip.title}. Leave empty or 0 to clear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) budgetInput = filtered
                        },
                        label = { Text("Target Budget") },
                        prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_budget_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cents = CurrencyFormatter.parseToMinorUnits(budgetInput) ?: 0L
                        viewModel.updateBudget(trip, cents) { showBudgetDialog = false }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("save_budget_button")
                ) {
                    Text("Save Budget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }, enabled = !busy) { Text("Cancel") }
            }
        )
    }

    // --- TRANSFER OWNERSHIP DIALOG ---
    transferringToUser?.let { targetUser ->
        val trip = currentContent?.trip
        if (trip != null && trip.canManageTripAdmin(viewModel.currentUserId)) {
            var keepSelfCoHost by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = { if (!busy) transferringToUser = null },
                icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Transfer Trip Ownership?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${targetUser.displayName} will become the primary Creator of \"${trip.title}\" with control over co-hosts, budget, and ending the trip."
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { keepSelfCoHost = !keepSelfCoHost }
                        ) {
                            Checkbox(checked = keepSelfCoHost, onCheckedChange = { keepSelfCoHost = it })
                            Text("Keep me as a Co-Host so I can still edit bills", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.transferOwnership(
                                trip = trip,
                                newCreatorUid = targetUser.userId,
                                newCreatorName = targetUser.displayName,
                                keepSelfAsCoHost = keepSelfCoHost,
                                onDone = { transferringToUser = null }
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.testTag("confirm_transfer_ownership_button")
                    ) {
                        Text("Transfer Ownership")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { transferringToUser = null }, enabled = !busy) { Text("Cancel") }
                }
            )
        }
    }

    // --- BILL DETAIL MODAL ---
    inspectingBill?.let { bill ->
        val trip = currentContent?.trip
        if (trip != null) {
            BillDetailDialog(
                trip = trip,
                bill = bill,
                receiptBitmap = receiptCache[bill.id],
                canEdit = canEdit,
                onDismiss = { inspectingBill = null },
                onViewFullReceipt = {
                    inspectingBill = null
                    viewingReceiptBill = bill
                },
                onEdit = {
                    inspectingBill = null
                    editingBill = bill
                },
                onDelete = {
                    inspectingBill = null
                    viewModel.softDeleteBill(trip, bill) { deleted ->
                        showUndoDeletedBillSnackbar(trip, deleted)
                    }
                }
            )
        }
    }

    // --- FULL-SCREEN RECEIPT PHOTO VIEWER ---
    viewingReceiptBill?.let { bill ->
        val bmp = receiptCache[bill.id]
        Dialog(
            onDismissRequest = { viewingReceiptBill = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0E110F)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Receipt photo for ${bill.title}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(10.dp))
                            Text("Loading receipt photo…", color = Color.White)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bill.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            if (bill.locationName.isNotBlank()) {
                                Text(bill.locationName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                        IconButton(onClick = { viewingReceiptBill = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close receipt viewer", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // --- END TRIP & ARCHIVE CONFIRMATION (PRIMARY CREATOR ONLY) ---
    if (showEndTripConfirm && currentContent != null && currentContent.trip.canManageTripAdmin(viewModel.currentUserId)) {
        AlertDialog(
            onDismissRequest = { if (!exportingPdf) showEndTripConfirm = false },
            icon = { Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("End Trip & Generate Archival PDF?") },
            text = {
                Text(
                    "Ending \"${currentContent.trip.title}\" permanently locks the ledger so no more bills or settlements can be modified, and exports an archival INR PDF with summary, settlements, itemized bills, locations, multi-payer splits, and receipt photos."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.endTripAndExportPdf(context, currentContent) { pdfFile ->
                            showEndTripConfirm = false
                            exportedPdfFile = pdfFile
                        }
                    },
                    enabled = !exportingPdf,
                    modifier = Modifier.testTag("confirm_end_trip_button")
                ) {
                    if (exportingPdf) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Generating…")
                    } else {
                        Text("End & Export PDF")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTripConfirm = false }, enabled = !exportingPdf) { Text("Cancel") }
            }
        )
    }

    // --- EXPORTED ARCHIVAL PDF ACTION DIALOG ---
    exportedPdfFile?.let { pdfFile ->
        AlertDialog(
            onDismissRequest = { exportedPdfFile = null },
            icon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Archival PDF Ready") },
            text = {
                Text("Generated ${pdfFile.name} with INR summary, simplified settlements, recorded payments, itemized bills, and receipt photos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, pdfFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Billi Trip Archive: ${currentContent?.trip?.title ?: "Trip"}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Archival PDF"))
                    },
                    modifier = Modifier.testTag("share_pdf_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share PDF")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { savePdfLauncher.launch(pdfFile.name) },
                    modifier = Modifier.testTag("save_pdf_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Copy")
                }
            }
        )
    }
}

@Composable
private fun BillsDayGroupedTabContent(
    content: TripDetailContent,
    filterState: BillFilterState,
    currentUserId: String,
    canEdit: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleCategory: (BillCategory) -> Unit,
    onTogglePayer: (String) -> Unit,
    onToggleMyBills: () -> Unit,
    onClearFilters: () -> Unit,
    onSelectMyMember: (String) -> Unit,
    onInspectBill: (Bill) -> Unit,
    onViewReceipt: (Bill) -> Unit,
    onEditBill: (Bill) -> Unit,
    onDeleteBill: (Bill) -> Unit
) {
    val memberMap = remember(content.trip.members) {
        content.trip.members.associate { it.memberId to it.displayName }
    }
    val myMemberId = content.linkedMemberId

    val filteredBills = remember(content.bills, filterState, myMemberId) {
        val q = filterState.searchQuery.trim().lowercase()
        content.bills.filter { bill ->
            val matchQuery = q.isEmpty() ||
                bill.title.lowercase().contains(q) ||
                bill.locationName.lowercase().contains(q)
            val matchCat = filterState.selectedCategory == null ||
                bill.billCategory() == filterState.selectedCategory
            val matchPayer = filterState.selectedPayerId == null ||
                bill.paidByMemberId == filterState.selectedPayerId ||
                bill.effectivePayers().any { it.memberId == filterState.selectedPayerId }
            val matchMine = !filterState.onlyMyBills || myMemberId == null ||
                bill.paidByMemberId == myMemberId ||
                bill.effectivePayers().any { it.memberId == myMemberId } ||
                myMemberId in bill.participantIds
            matchQuery && matchCat && matchPayer && matchMine
        }
    }

    val groupedByDay = remember(filteredBills) {
        DebtSimplifier.groupBillsByDay(filteredBills)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_summary_card") {
            TripSummaryCard(
                trip = content.trip,
                summary = content.summary,
                currentUserId = currentUserId,
                onSelectMyMember = onSelectMyMember
            )
        }

        // --- SEARCH & FILTER BAR ---
        if (content.bills.isNotEmpty()) {
            item(key = "bill_search_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search bills or locations…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (myMemberId != null) {
                            FilterChip(
                                selected = filterState.onlyMyBills,
                                onClick = onToggleMyBills,
                                label = { Text("My Bills") },
                                modifier = Modifier.testTag("filter_my_bills_chip")
                            )
                        }
                        BillCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = filterState.selectedCategory == cat,
                                onClick = { onToggleCategory(cat) },
                                label = { Text(cat.label) }
                            )
                        }
                        content.trip.members.forEach { member ->
                            FilterChip(
                                selected = filterState.selectedPayerId == member.memberId,
                                onClick = { onTogglePayer(member.memberId) },
                                label = { Text("Paid: ${member.displayName}") }
                            )
                        }
                        if (filterState.isActive) {
                            TextButton(onClick = onClearFilters) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }

        if (groupedByDay.isEmpty()) {
            item(key = "empty_bills") {
                BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (filterState.isActive) "No matching bills" else "No bills recorded yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                filterState.isActive -> "Try clearing your search or category filters."
                                canEdit -> "Tap Add Bill to use 1-tap smart picks, scan receipts with AI, or split multi-payer payments."
                                else -> "Bills added by ${content.trip.creatorName} will sync here automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            groupedByDay.forEach { dayGroup ->
                item(key = "day_header_${dayGroup.dayKey}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = dayGroup.dateHeader,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = CurrencyFormatter.format(dayGroup.totalCents),
                                style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                items(dayGroup.bills, key = { it.id }) { bill ->
                    val effPayers = bill.effectivePayers()
                    val payerSummary = if (effPayers.size > 1) {
                        effPayers.joinToString(" + ") { p ->
                            "${memberMap[p.memberId] ?: "Member"} (${p.method().label})"
                        }
                    } else {
                        val pName = memberMap[bill.paidByMemberId] ?: "Member"
                        val method = effPayers.firstOrNull()?.method()?.label
                        if (!method.isNullOrBlank() && bill.payers.isNotEmpty()) "$pName ($method)" else pName
                    }
                    val catColor = categoryAccentColor(bill.billCategory())
                    val isPendingSync = bill.id in content.pendingBillIds
                    val subsetLabel = if (bill.participantIds.size == content.trip.members.size) {
                        "${bill.billSplitMode().label} • All ${bill.participantIds.size}"
                    } else {
                        "${bill.billSplitMode().label} • ${bill.participantIds.size} of ${content.trip.members.size}"
                    }

                    BilliMorphCard(
                        onClick = { onInspectBill(bill) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_card_${bill.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = catColor.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = bill.billCategory().label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = catColor,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        // Offline-First Per-Card Pending Sync Badge
                                        if (isPendingSync) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                modifier = Modifier.testTag("bill_pending_sync_${bill.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CloudUpload,
                                                        contentDescription = "Queued for cloud sync",
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "Sync queued",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = bill.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = CurrencyFormatter.format(bill.amountCents),
                                    style = MoneyTitleStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (bill.locationName.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = bill.locationName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paid by $payerSummary • $subsetLabel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (bill.receiptAttached) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .clickable { onViewReceipt(bill) }
                                                .testTag("bill_receipt_pill_${bill.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Image,
                                                    contentDescription = "View receipt",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "Receipt",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    if (canEdit) {
                                        IconButton(
                                            onClick = { onEditBill(bill) },
                                            modifier = Modifier.testTag("edit_bill_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Edit bill",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteBill(bill) },
                                            modifier = Modifier.testTag("delete_bill_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete bill",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
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
}

@Composable
private fun BillDetailDialog(
    trip: Trip,
    bill: Bill,
    receiptBitmap: Bitmap?,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onViewFullReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val memberMap = remember(trip.members) { trip.members.associate { it.memberId to it.displayName } }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US) }
    val effPayers = bill.effectivePayers()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(bill.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = CurrencyFormatter.format(bill.amountCents),
                    style = MoneyTitleStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (effPayers.size > 1) {
                    Text(
                        text = "Split Payment (${bill.billCategory().label})",
                        style = MaterialTheme.typography.labelLarge
                    )
                    effPayers.forEach { payer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${memberMap[payer.memberId] ?: "Member"} (${payer.method().label})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = CurrencyFormatter.format(payer.amountCents),
                                style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                } else {
                    val method = effPayers.firstOrNull()?.method()?.label ?: "UPI"
                    Text(
                        text = "Paid by ${memberMap[bill.paidByMemberId] ?: "Member"} via $method • ${bill.billCategory().label}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (bill.locationName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text(bill.locationName, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (bill.billDateMillis > 0L) {
                    Text(
                        text = dateFmt.format(Date(bill.billDateMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (bill.receiptAttached) {
                    if (receiptBitmap != null) {
                        Image(
                            bitmap = receiptBitmap.asImageBitmap(),
                            contentDescription = "Receipt thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onViewFullReceipt)
                        )
                    } else {
                        OutlinedButton(onClick = onViewFullReceipt, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View Attached Receipt")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Split Breakdown (${bill.billSplitMode().label})",
                    style = MaterialTheme.typography.labelLarge
                )
                bill.splits.forEach { (mid, share) ->
                    val pctLabel = if (bill.billSplitMode() == SplitMode.PERCENTAGE) {
                        val bps = bill.splitPercentages[mid]
                        if (bps != null) " (${CurrencyFormatter.formatPercentInput(bps)}%)" else ""
                    } else ""
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${memberMap[mid] ?: "Member"}$pctLabel", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = CurrencyFormatter.format(share),
                            style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onEdit) { Text("Edit") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (canEdit) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
