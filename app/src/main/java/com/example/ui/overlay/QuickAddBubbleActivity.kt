package com.example.ui.overlay

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.BilliRepository
import com.example.data.firebase.SyncStatusMonitor
import com.example.data.firebase.configureAppCheck
import com.example.data.model.BillCategory
import com.example.data.model.BillPayer
import com.example.data.model.CURRENCY_SYMBOL_INR
import com.example.data.model.PaymentMethod
import com.example.data.model.SplitMode
import com.example.data.model.Trip
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesRepository
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtSimplifier
import com.example.ui.theme.BilliTheme
import com.example.ui.theme.MoneyBodyStyle
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

/**
 * Compact floating Compose Quick-Add overlay card opened from the Trip Bubble or notification
 * so travelers can log a ₹60 auto ride or ₹120 chai bill in 3 seconds without leaving Maps or WhatsApp.
 */
class QuickAddBubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)

        val passedTripId = intent.getStringExtra(EXTRA_TRIP_ID).orEmpty()
        val passedTripTitle = intent.getStringExtra(EXTRA_TRIP_TITLE).orEmpty()
        val prefsRepo = AppPreferencesRepository(applicationContext)
        val initialPrefs = prefsRepo.readStartupPreferencesSync()

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = initialPrefs)
            BilliTheme(
                themeMode = prefs.themeMode,
                amoledDark = prefs.amoledDark,
                useSystemFont = prefs.useSystemFont,
                reduceMotion = prefs.reduceMotion,
                syncWindowBackground = false
            ) {
                QuickAddOverlaySheet(
                    tripIdHint = passedTripId.ifBlank { prefs.widgetSnapshot.tripId },
                    tripTitleHint = passedTripTitle.ifBlank { prefs.widgetSnapshot.tripTitle },
                    onDismiss = { finish() },
                    onSaved = { msg ->
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_TRIP_TITLE = "extra_trip_title"
    }
}

@Composable
private fun QuickAddOverlaySheet(
    tripIdHint: String,
    tripTitleHint: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { BilliRepository(context) }
    val syncMonitor = remember { SyncStatusMonitor(context) }
    val scope = rememberCoroutineScope()

    var activeTrip by remember { mutableStateOf<Trip?>(null) }
    var loadingTrip by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BillCategory.FOOD) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tripIdHint) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank() || tripIdHint.isBlank()) {
            loadingTrip = false
            return@LaunchedEffect
        }
        activeTrip = repo.loadTripSnapshot(tripIdHint)
        loadingTrip = false
    }

    val quickPresets = remember {
        listOf(
            Triple("Auto Ride", "60", BillCategory.TRANSPORT),
            Triple("Chai & Tapri", "120", BillCategory.FOOD),
            Triple("Snacks", "200", BillCategory.FOOD),
            Triple("Cab", "350", BillCategory.TRANSPORT)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!saving) onDismiss() }
            )
            .imePadding()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("3-Second Quick Log", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = activeTrip?.title ?: tripTitleHint.ifBlank { "Active Trip" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { if (!saving) onDismiss() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close quick add")
                    }
                }

                if (loadingTrip) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (activeTrip == null) {
                    Text(
                        text = "Open Billi and select an active trip as Creator or Co-Host to use the Trip Bubble.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val trip = activeTrip!!
                    val uid = Firebase.auth.currentUser?.uid.orEmpty()
                    if (!trip.canEdit(uid)) {
                        Text(
                            text = "View-only mode on \"${trip.title}\". Only the Creator or Co-Host can add bills.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // 1-tap preset chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickPresets.forEach { (presetTitle, presetAmt, presetCat) ->
                                AssistChip(
                                    onClick = {
                                        title = presetTitle
                                        amountInput = presetAmt
                                        category = presetCat
                                        errorMsg = null
                                    },
                                    label = { Text("$presetTitle ₹$presetAmt") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 80) title = it },
                                label = { Text("What") },
                                placeholder = { Text("Auto / Chai") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("bubble_title_input")
                            )
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() || it == '.' }
                                    if (filtered.count { it == '.' } <= 1) amountInput = filtered
                                },
                                label = { Text("INR") },
                                prefix = { Text(CURRENCY_SYMBOL_INR, style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bubble_amount_input")
                            )
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(
                                text = errorMsg!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                val cents = CurrencyFormatter.parseToMinorUnits(amountInput) ?: 0L
                                if (title.isBlank() || cents <= 0L) {
                                    errorMsg = "Enter a title and INR amount"
                                    return@Button
                                }
                                val payerId = trip.members.firstOrNull { it.linkedUid == uid }?.memberId
                                    ?: trip.members.firstOrNull()?.memberId.orEmpty()
                                val participantIds = trip.members.map { it.memberId }
                                val splits = DebtSimplifier.computeEqualSplits(cents, participantIds)

                                scope.launch {
                                    saving = true
                                    val isOnline = syncMonitor.isCurrentlyOnline()
                                    val result = repo.saveBill(
                                        trip = trip,
                                        existingBill = null,
                                        title = title.trim(),
                                        category = category,
                                        amountCents = cents,
                                        locationName = "",
                                        paidByMemberId = payerId,
                                        payers = listOf(BillPayer(payerId, cents, PaymentMethod.UPI.wireValue)),
                                        splitMode = SplitMode.EQUAL,
                                        participantIds = participantIds,
                                        splits = splits,
                                        splitPercentages = emptyMap(),
                                        billDateMillis = System.currentTimeMillis(),
                                        newReceiptBase64 = null,
                                        removeExistingReceipt = false,
                                        isOnline = isOnline
                                    )
                                    saving = false
                                    result.onSuccess {
                                        onSaved("Logged ${title.trim()} (${CurrencyFormatter.format(cents)})")
                                    }.onFailure { err ->
                                        errorMsg = err.localizedMessage ?: "Unable to save"
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("bubble_save_button")
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Split Equally (${trip.members.size})")
                            }
                        }
                    }
                }
            }
        }
    }
}
