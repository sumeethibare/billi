package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.CURRENCY_SYMBOL_INR
import com.example.data.model.MultiDayTemplate
import com.example.data.model.PaymentMethod
import com.example.data.model.SplitMode
import com.example.data.model.Trip
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtSimplifier
import com.example.domain.SharedExpenseDraft
import com.example.ui.components.BilliMorphCard
import com.example.ui.components.BilliMotion
import com.example.ui.components.LocalBilliFeedback
import com.example.ui.components.ReceiptCameraScreen
import com.example.ui.components.billiPressScale
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.theme.NegativeRust
import com.example.ui.theme.PositiveSage
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.ReceiptScanUiState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorScreen(
    viewModel: ExpenseViewModel,
    trip: Trip,
    existingBill: Bill?,
    existingReceiptBitmap: Bitmap?,
    existingBills: List<Bill> = emptyList(),
    lastUsedSplitMode: SplitMode = SplitMode.EQUAL,
    initialSharedDraft: SharedExpenseDraft? = null,
    recentLocations: List<String>,
    onSaved: (billId: String, message: String) -> Unit,
    onDeleted: (deletedBill: Bill, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback = LocalBilliFeedback.current
    val isBusy = state.saving || state.deleting

    BackHandler { if (!isBusy) onDismiss() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(trip.id, existingBill?.id) {
        viewModel.initialize(
            trip = trip,
            existingBill = existingBill,
            existingReceiptBitmap = existingReceiptBitmap,
            existingBills = existingBills,
            lastUsedSplitMode = lastUsedSplitMode
        )
        if (existingBill == null && initialSharedDraft != null) {
            viewModel.applySharedDraft(context, initialSharedDraft)
        }
    }
    LaunchedEffect(existingReceiptBitmap) {
        viewModel.attachLoadedExistingReceipt(existingReceiptBitmap)
    }

    // Trigger warning nudge haptic whenever Duplicate Expense Shield flags a match
    LaunchedEffect(state.duplicateMatch?.existingBill?.id) {
        if (state.duplicateMatch != null) {
            feedback?.warningNudge()
        }
    }

    var showInAppCamera by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var locatingGps by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processReceiptUri(context, uri, autoScan = true)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                locatingGps = true
                val place = lookupCoarsePlaceName(context)
                locatingGps = false
                if (!place.isNullOrBlank()) {
                    viewModel.updateLocation(place)
                } else {
                    viewModel.setValidationError("Location unavailable — enter the place name manually.")
                }
            }
        }
    }

    if (showInAppCamera) {
        ReceiptCameraScreen(
            onPhotoCaptured = { uri ->
                showInAppCamera = false
                viewModel.processReceiptUri(context, uri, autoScan = true)
            },
            onClose = { showInAppCamera = false }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { if (!isBusy) onDismiss() },
                        modifier = Modifier.testTag("bill_editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close editor")
                    }
                },
                title = {
                    Text(
                        text = if (state.isEditing) "Edit Expense" else "Add Expense",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(
                            onClick = {
                                feedback?.tick()
                                showDeleteConfirm = true
                            },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("editor_delete_expense_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete expense",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.saveExpense(trip) { billId, msg ->
                                feedback?.celebrateDoublePulse()
                                onSaved(billId, msg)
                            }
                        },
                        enabled = !isBusy && !state.processingImage,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .billiPressScale()
                            .testTag("save_bill_button")
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- VALIDATION ERROR BANNER ---
                AnimatedVisibility(
                    visible = !state.validationError.isNullOrBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // --- SHARE-TO-SPLIT AUTO-FILL BANNER ---
                AnimatedVisibility(
                    visible = !state.sharedSourceBanner.isNullOrBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_to_split_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.sharedSourceBanner.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // --- DUPLICATE EXPENSE SHIELD (DISCREPANCY DETECTIVE) ---
                AnimatedVisibility(
                    visible = state.duplicateMatch != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    state.duplicateMatch?.let { dup ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("duplicate_shield_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = dup.headline,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = dup.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            feedback?.tick()
                                            viewModel.dismissDuplicateWarning()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("duplicate_keep_separate_button")
                                    ) {
                                        Text("Keep Separate")
                                    }
                                    Button(
                                        onClick = {
                                            feedback?.celebrateDoublePulse()
                                            viewModel.mergeIntoExistingDuplicate(trip, onSaved)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("duplicate_merge_button")
                                    ) {
                                        Icon(Icons.Rounded.MergeType, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Merge")
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 0. ZERO-TYPING SMART ACTIVITY RECOMMENDATIONS RIBBON ---
                if (!state.isEditing && state.recommendation != null) {
                    val rec = state.recommendation!!
                    BilliMorphCard(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_recommendations_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Zero-Typing 1-Tap Picks",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = rec.bannerSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rec.quickPresets.forEach { preset ->
                                    AssistChip(
                                        onClick = {
                                            feedback?.tick()
                                            viewModel.applyQuickPreset(preset)
                                        },
                                        label = { Text(preset.label) },
                                        modifier = Modifier.testTag("quick_preset_${preset.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 1. RECEIPT PHOTO & SMART RECEIPT SCANNER (Firebase AI Logic) ---
                BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Column {
                            Text("Receipt & Smart Scan", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Snap or pick a bill to auto-fill title, ₹ total & place",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        if (state.processingImage) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (state.previewBitmap != null) {
                            Image(
                                bitmap = state.previewBitmap!!.asImageBitmap(),
                                contentDescription = "Attached receipt preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(185.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(Modifier.height(10.dp))

                            AnimatedContent(
                                targetState = state.scanState,
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                                label = "smart_scan_status"
                            ) { scan ->
                                when (scan) {
                                    ReceiptScanUiState.Idle -> {
                                        FilledTonalButton(
                                            onClick = {
                                                feedback?.tick()
                                                viewModel.runSmartReceiptScan()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("smart_scan_receipt_button")
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Smart Scan Receipt")
                                        }
                                    }
                                    ReceiptScanUiState.Scanning -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = "Reading receipt total, merchant & location…",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    is ReceiptScanUiState.Success -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "Smart Scan auto-filled expense details",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                if (scan.result.lineItems.isNotEmpty()) {
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        text = scan.result.lineItems.joinToString(" • "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is ReceiptScanUiState.Error -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = scan.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(onClick = { viewModel.runSmartReceiptScan() }) {
                                                    Text("Retry")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showInAppCamera = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retake")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.removeReceipt() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remove")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        feedback?.tick()
                                        showInAppCamera = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bill_camera_capture_button")
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan / Snap")
                                }
                                OutlinedButton(
                                    onClick = {
                                        feedback?.tick()
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bill_gallery_picker_button")
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gallery")
                                }
                            }
                        }
                    }
                }

                // --- 2. TITLE, INR AMOUNT, DATE, CATEGORY, LOCATION ---
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("What was this expense for?") },
                    placeholder = { Text("e.g. Beach Shack Dinner, Cab to Fort") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bill_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = { viewModel.updateAmountInput(it) },
                        label = { Text("Total Amount (INR)") },
                        prefix = {
                            Text(
                                text = CURRENCY_SYMBOL_INR,
                                style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bill_amount_input")
                    )

                    OutlinedButton(
                        onClick = {
                            feedback?.tick()
                            showDatePicker = true
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("bill_date_button")
                    ) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = SimpleDateFormat("MMM d", Locale.US).format(Date(state.billDateMillis)),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // --- MULTI-DAY / PER-UNIT RECURRING TEMPLATES (STAY / SCOOTER / CAR RENT) ---
                if (!state.isEditing) {
                    BilliMorphCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("multi_day_template_card"),
                        animateSize = true
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Multi-Day / Rental Split",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Homestay nights, scooter rent, or daily car rate × days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.multiDayEnabled,
                                    onCheckedChange = {
                                        feedback?.tick()
                                        viewModel.toggleMultiDay(it)
                                    },
                                    modifier = Modifier.testTag("multi_day_toggle")
                                )
                            }

                            AnimatedVisibility(
                                visible = state.multiDayEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MultiDayTemplate.presets.forEach { preset ->
                                            AssistChip(
                                                onClick = {
                                                    feedback?.tick()
                                                    viewModel.applyMultiDayTemplate(preset)
                                                },
                                                label = { Text(preset.label) },
                                                modifier = Modifier.testTag("multiday_preset_${preset.id}")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = state.multiDayRateInput,
                                            onValueChange = { viewModel.updateMultiDayRate(it) },
                                            label = { Text("Rate / ${state.multiDayUnitLabel.removeSuffix("s")}") },
                                            prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("multiday_rate_input")
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.height(56.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        feedback?.tick()
                                                        viewModel.updateMultiDayCount(state.multiDayCount - 1)
                                                    },
                                                    enabled = state.multiDayCount > 1
                                                ) {
                                                    Text("−", style = MaterialTheme.typography.titleLarge)
                                                }
                                                Text(
                                                    text = "${state.multiDayCount} ${state.multiDayUnitLabel}",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                IconButton(
                                                    onClick = {
                                                        feedback?.tick()
                                                        viewModel.updateMultiDayCount(state.multiDayCount + 1)
                                                    },
                                                    enabled = state.multiDayCount < 30
                                                ) {
                                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Split into ${state.multiDayCount} separate daily bills",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                text = "Off = 1 combined bill • On = Day 1..${state.multiDayCount} ledger entries",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = state.splitIntoDailyBills,
                                            onCheckedChange = {
                                                feedback?.tick()
                                                viewModel.toggleSplitIntoDailyBills(it)
                                            },
                                            modifier = Modifier.testTag("multiday_split_daily_switch")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BillCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = state.category == cat,
                                onClick = {
                                    feedback?.tick()
                                    viewModel.updateCategory(cat)
                                },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }

                Column {
                    OutlinedTextField(
                        value = state.locationName,
                        onValueChange = { viewModel.updateLocation(it) },
                        label = { Text("Where (Location details)") },
                        placeholder = { Text("e.g. Baga Beach, Hotel Lobby") },
                        leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                        trailingIcon = {
                            if (locatingGps) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        feedback?.tick()
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            scope.launch {
                                                locatingGps = true
                                                val place = lookupCoarsePlaceName(context)
                                                locatingGps = false
                                                if (!place.isNullOrBlank()) {
                                                    viewModel.updateLocation(place)
                                                } else {
                                                    viewModel.setValidationError("Could not detect current location — enter it manually.")
                                                }
                                            }
                                        } else {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                    },
                                    modifier = Modifier.testTag("bill_gps_location_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MyLocation,
                                        contentDescription = "Use current location",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_location_input")
                    )

                    if (recentLocations.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recentLocations.forEach { place ->
                                AssistChip(
                                    onClick = {
                                        feedback?.tick()
                                        viewModel.updateLocation(place)
                                    },
                                    label = { Text(place) }
                                )
                            }
                        }
                    }
                }

                // --- OPTIONAL ORGANIZER NOTE ---
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { viewModel.updateNotes(it) },
                    label = { Text("Note / Breakdown (Optional)") },
                    placeholder = { Text("e.g. Includes 10% tip, 3 nights @ ₹3,500") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bill_notes_input")
                )

                // --- 3. PAID BY & SPLIT PAYMENT MODE (MULTIPLE PAYERS: CASH + UPI / CARD / POINTS) ---
                BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .animateContentSize(BilliMotion.snappySpring()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreditCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Paid By", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (state.multiPayerEnabled) {
                                            "Split Payment Mode (e.g. Card + Cash/UPI)"
                                        } else {
                                            "Single payer • Toggle for multiple payers"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = state.multiPayerEnabled,
                                onCheckedChange = {
                                    feedback?.tick()
                                    viewModel.toggleMultiPayer(it)
                                },
                                modifier = Modifier.testTag("multi_payer_switch")
                            )
                        }

                        if (!state.multiPayerEnabled) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                trip.members.forEach { member ->
                                    FilterChip(
                                        selected = state.paidByMemberId == member.memberId,
                                        onClick = {
                                            feedback?.tick()
                                            viewModel.updatePaidBy(member.memberId)
                                        },
                                        label = { Text(member.displayName) },
                                        modifier = Modifier.testTag("payer_chip_${member.memberId}")
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Via:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                PaymentMethod.entries.forEach { method ->
                                    FilterChip(
                                        selected = state.singlePayerMethod == method,
                                        onClick = {
                                            feedback?.tick()
                                            viewModel.updateSinglePayerMethod(method)
                                        },
                                        label = { Text(method.label) },
                                        modifier = Modifier.testTag("single_method_${method.wireValue}")
                                    )
                                }
                            }
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val delta = state.multiPayerDeltaCents
                            Text(
                                text = when {
                                    delta == 0L && state.totalCents > 0L -> "Split payments match bill total"
                                    delta > 0L -> "Remaining to assign: ${CurrencyFormatter.format(delta)}"
                                    else -> "Overpaid by: ${CurrencyFormatter.format(-delta)}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (delta == 0L && state.totalCents > 0L) PositiveSage else NegativeRust
                            )

                            trip.members.forEach { member ->
                                val mid = member.memberId
                                val curMethod = state.payerMethods[mid] ?: PaymentMethod.UPI
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = member.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = state.payerAmountInputs[mid].orEmpty(),
                                            onValueChange = { viewModel.updatePayerAmount(mid, it) },
                                            prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle) },
                                            placeholder = { Text("0") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier
                                                .width(128.dp)
                                                .testTag("multi_payer_amount_$mid")
                                        )
                                        if (delta != 0L) {
                                            TextButton(
                                                onClick = {
                                                    feedback?.tick()
                                                    viewModel.fitRemainingPayerToMember(mid)
                                                },
                                                modifier = Modifier.testTag("multi_payer_fill_$mid")
                                            ) {
                                                Text("Fill", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PaymentMethod.entries.forEach { m ->
                                            FilterChip(
                                                selected = curMethod == m,
                                                onClick = {
                                                    feedback?.tick()
                                                    viewModel.updatePayerMethod(mid, m)
                                                },
                                                label = { Text(m.label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 4. FLEXIBLE SUBSET PARTICIPANTS ---
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Split Among (${state.selectedMemberIds.size} of ${trip.members.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(
                            onClick = {
                                feedback?.tick()
                                viewModel.selectAllParticipants(trip.members.map { it.memberId })
                            }
                        ) {
                            Text("Select All")
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        trip.members.forEach { member ->
                            val isSelected = member.memberId in state.selectedMemberIds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    feedback?.tick()
                                    viewModel.toggleParticipant(member.memberId)
                                },
                                label = { Text(member.displayName) },
                                modifier = Modifier.testTag("participant_chip_${member.memberId}")
                            )
                        }
                    }
                }

                // --- 5. EQUAL VS CUSTOM % VS EXACT ₹ SPLIT MODE ---
                Column {
                    Text("Split Method", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SplitMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = state.splitMode == mode,
                                onClick = {
                                    feedback?.tick()
                                    viewModel.updateSplitMode(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(idx, SplitMode.entries.size),
                                modifier = Modifier.testTag("split_mode_${mode.wireValue}")
                            ) {
                                Text(mode.label, maxLines = 1)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        AnimatedContent(
                            targetState = state.splitMode,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            label = "split_mode_content"
                        ) { activeMode ->
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .animateContentSize(BilliMotion.snappySpring()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                when (activeMode) {
                                    SplitMode.EQUAL -> {
                                        state.selectedMemberIds.forEach { mid ->
                                            val memberName = trip.members.firstOrNull { it.memberId == mid }?.displayName ?: "Member"
                                            val share = state.equalSplits[mid] ?: 0L
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(memberName, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text = CurrencyFormatter.format(share),
                                                    style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    SplitMode.PERCENTAGE -> {
                                        val totalPctFloat = state.totalPercentageBps.toFloat() / DebtSimplifier.FULL_PERCENT_BPS.toFloat()
                                        val animPct by animateFloatAsState(
                                            targetValue = totalPctFloat.coerceIn(0f, 1f),
                                            animationSpec = BilliMotion.snappySpring(),
                                            label = "pct_progress"
                                        )
                                        val remBps = state.remainingPercentageBps
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when {
                                                    remBps == 0L -> "100% allocated — exact match"
                                                    remBps > 0L -> "Remaining: ${CurrencyFormatter.formatPercentInput(remBps)}%"
                                                    else -> "Over by: ${CurrencyFormatter.formatPercentInput(-remBps)}%"
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (remBps == 0L) PositiveSage else NegativeRust
                                            )
                                            TextButton(onClick = {
                                                feedback?.tick()
                                                viewModel.distributePercentagesEvenly()
                                            }) {
                                                Text("Even %")
                                            }
                                        }

                                        LinearProgressIndicator(
                                            progress = { animPct },
                                            color = if (remBps == 0L) PositiveSage else if (remBps < 0L) NegativeRust else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                        )

                                        val previewCentsMap = state.percentageSplits
                                        state.selectedMemberIds.forEach { mid ->
                                            val memberName = trip.members.firstOrNull { it.memberId == mid }?.displayName ?: "Member"
                                            val currentPct = state.percentageInputs[mid].orEmpty()
                                            val sharePreview = previewCentsMap[mid] ?: 0L
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(memberName, style = MaterialTheme.typography.bodyMedium)
                                                    Text(
                                                        text = CurrencyFormatter.format(sharePreview),
                                                        style = MoneyBodyStyle.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                OutlinedTextField(
                                                    value = currentPct,
                                                    onValueChange = { viewModel.updatePercentageSplit(mid, it) },
                                                    suffix = { Text("%", style = MoneyBodyStyle) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .testTag("pct_split_input_$mid")
                                                )
                                                if (remBps != 0L) {
                                                    TextButton(
                                                        onClick = {
                                                            feedback?.tick()
                                                            viewModel.fitRemainingPercentageToMember(mid)
                                                        }
                                                    ) {
                                                        Text("Fit", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    SplitMode.CUSTOM -> {
                                        val delta = state.customDeltaCents
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when {
                                                    delta == 0L && state.totalCents > 0L -> "Exact splits match bill total"
                                                    delta > 0L -> "Remaining: ${CurrencyFormatter.format(delta)}"
                                                    else -> "Over by: ${CurrencyFormatter.format(-delta)}"
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (delta == 0L && state.totalCents > 0L) PositiveSage else NegativeRust
                                            )
                                            TextButton(onClick = {
                                                feedback?.tick()
                                                viewModel.distributeCustomEvenly()
                                            }) {
                                                Text("Split Evenly")
                                            }
                                        }

                                        state.selectedMemberIds.forEach { mid ->
                                            val memberName = trip.members.firstOrNull { it.memberId == mid }?.displayName ?: "Member"
                                            val currentVal = state.customExactInputs[mid].orEmpty()
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = memberName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = currentVal,
                                                    onValueChange = { viewModel.updateCustomExactSplit(mid, it) },
                                                    prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier
                                                        .width(140.dp)
                                                        .testTag("custom_split_input_$mid")
                                                )
                                                if (delta != 0L) {
                                                    TextButton(
                                                        onClick = {
                                                            feedback?.tick()
                                                            viewModel.fitRemainingExactToMember(mid)
                                                        }
                                                    ) {
                                                        Text("Fit", style = MaterialTheme.typography.labelSmall)
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

                if (state.isEditing) {
                    OutlinedButton(
                        onClick = {
                            feedback?.tick()
                            showDeleteConfirm = true
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Move Expense to Trash")
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.billDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { viewModel.updateBillDate(it) }
                        showDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirm && state.existingBill != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move Expense to Deleted Bills?") },
            text = {
                Text("You can immediately tap Undo or restore \"${state.title.ifBlank { state.existingBill!!.title }}\" from Deleted Bills before ending the trip.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCurrentExpense(trip, onDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@SuppressLint("MissingPermission")
private suspend fun lookupCoarsePlaceName(context: Context): String? = withContext(Dispatchers.IO) {
    try {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val location = fused.lastLocation.await() ?: return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val addr = addresses?.firstOrNull()
        val label = listOfNotNull(addr?.featureName, addr?.subLocality, addr?.locality)
            .distinct()
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(", ")
        label.ifBlank {
            String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
        }
    } catch (_: Exception) {
        null
    }
}
