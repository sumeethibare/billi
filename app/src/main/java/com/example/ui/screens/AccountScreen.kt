package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.data.preferences.AppThemeMode
import com.example.service.TripBubbleService
import com.example.ui.components.BilliMorphCard
import com.example.ui.components.BilliStaggeredItem
import com.example.ui.components.LocalBilliFeedback
import com.example.ui.components.UpdateBottomSheet
import com.example.ui.components.billiPressScale
import com.example.ui.theme.MoneyTitleStyle
import com.example.ui.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    userId: String,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalBilliFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val checkingUpdate by viewModel.checkingUpdate.collectAsStateWithLifecycle()
    val updateRelease by viewModel.updateReleaseInfo.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val updateStatusMessage by viewModel.updateStatusMessage.collectAsStateWithLifecycle()

    var showSignOutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(updateStatusMessage) {
        updateStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* optional for persistent notification */ }

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
                        modifier = Modifier.testTag("account_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Account & Settings", style = MaterialTheme.typography.titleLarge)
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
                // --- 1. GOOGLE ACCOUNT PROFILE CARD ---
                BilliStaggeredItem(index = 0) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!userPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(userPhotoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile photo of $userDisplayName",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userDisplayName.firstOrNull()?.uppercase() ?: "B",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Google Synced • ID ${userId.take(6)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 2. APPEARANCE (LIGHT / DARK / SYSTEM + AMOLED + REDUCE MOTION) ---
                BilliStaggeredItem(index = 1) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Appearance & Motion", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Theme, AMOLED black, and battery-friendly animation tuning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                AppThemeMode.entries.forEachIndexed { idx, mode ->
                                    val icon = when (mode) {
                                        AppThemeMode.SYSTEM -> Icons.Rounded.PhoneAndroid
                                        AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        AppThemeMode.DARK -> Icons.Rounded.DarkMode
                                    }
                                    SegmentedButton(
                                        selected = prefs.themeMode == mode,
                                        onClick = {
                                            feedback?.tick()
                                            viewModel.setThemeMode(mode)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(idx, AppThemeMode.entries.size),
                                        icon = {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.testTag("theme_mode_${mode.wireValue}")
                                    ) {
                                        Text(mode.label)
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = prefs.themeMode != AppThemeMode.LIGHT,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    SettingsToggleRow(
                                        icon = Icons.Rounded.Contrast,
                                        title = "AMOLED Pure Black",
                                        subtitle = "True #000000 black surfaces in Dark mode",
                                        checked = prefs.amoledDark,
                                        onCheckedChange = {
                                            feedback?.tick()
                                            viewModel.setAmoledDark(it)
                                        },
                                        testTag = "amoled_dark_switch"
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Low-End Device & Battery-Friendly Animation Toggle
                            SettingsToggleRow(
                                icon = Icons.Rounded.BatterySaver,
                                title = "Reduce Motion / Battery Saver",
                                subtitle = "Simplifies Compose spring physics & blur for smooth 60Hz performance",
                                checked = prefs.reduceMotion,
                                onCheckedChange = {
                                    feedback?.tick()
                                    viewModel.setReduceMotion(it)
                                },
                                testTag = "reduce_motion_switch"
                            )
                        }
                    }
                }

                // --- 3. TYPOGRAPHY (BILLI APP FONT VS SYSTEM FONT) ---
                BilliStaggeredItem(index = 2) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Rounded.FontDownload,
                                title = "Use System Font",
                                subtitle = if (prefs.useSystemFont) {
                                    "Using your device's system typeface"
                                } else {
                                    "Using Billi's Outfit & JetBrains Mono fonts"
                                },
                                checked = prefs.useSystemFont,
                                onCheckedChange = {
                                    feedback?.tick()
                                    viewModel.setUseSystemFont(it)
                                },
                                testTag = "system_font_switch"
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Goa Beach Shack",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (prefs.useSystemFont) "System Font Preview" else "Billi Editorial Preview",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "₹12,450.00",
                                        style = MoneyTitleStyle,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 4. TRIP BUBBLE OVERLAY, HAPTICS & SOUND ---
                BilliStaggeredItem(index = 3) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Rounded.BubbleChart,
                                title = "Floating Trip Bubble",
                                subtitle = "Pin a draggable ₹+ bubble over other apps for 3-second expense logging",
                                checked = prefs.tripBubbleEnabled,
                                onCheckedChange = { enabled ->
                                    feedback?.tick()
                                    if (enabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        if (!TripBubbleService.canDrawOverlay(context)) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Grant \"Display over other apps\" for Billi, then toggle Trip Bubble on."
                                                )
                                            }
                                            runCatching {
                                                context.startActivity(TripBubbleService.overlaySettingsIntent(context))
                                            }
                                        } else {
                                            val snap = prefs.widgetSnapshot
                                            if (snap.tripId.isBlank()) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Open any active trip first to pin its Trip Bubble.")
                                                }
                                            } else {
                                                viewModel.setTripBubbleEnabled(true)
                                                TripBubbleService.start(context, snap.tripId, snap.tripTitle)
                                                feedback?.celebrateDoublePulse()
                                            }
                                        }
                                    } else {
                                        viewModel.setTripBubbleEnabled(false)
                                        TripBubbleService.stop(context)
                                    }
                                },
                                testTag = "trip_bubble_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SettingsToggleRow(
                                icon = Icons.Rounded.Vibration,
                                title = "Haptic Micro-Interactions",
                                subtitle = "Crisp ticks on split chips, avatar dock thud & settle double-pulse",
                                checked = prefs.hapticsEnabled,
                                onCheckedChange = {
                                    viewModel.setHapticsEnabled(it)
                                    if (it) feedback?.celebrateDoublePulse()
                                },
                                testTag = "haptics_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SettingsToggleRow(
                                icon = Icons.Rounded.VolumeUp,
                                title = "Subtle UI Sound Effects",
                                subtitle = "Soft audio feedback alongside haptics",
                                checked = prefs.soundEffectsEnabled,
                                onCheckedChange = {
                                    viewModel.setSoundEffectsEnabled(it)
                                },
                                testTag = "sound_effects_switch"
                            )
                        }
                    }
                }

                // --- 5. IN-APP UPDATES (GITHUB RELEASES) & WALKTHROUGH ---
                BilliStaggeredItem(index = 4) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Billi App Updates", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} • com.isuh.billi • Built by isuh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        feedback?.tick()
                                        viewModel.checkForUpdates(force = true)
                                    },
                                    enabled = !checkingUpdate,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("check_updates_button")
                                ) {
                                    if (checkingUpdate) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Checking…")
                                    } else {
                                        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Check Updates")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        feedback?.tick()
                                        viewModel.resetOnboardingWalkthrough()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("replay_walkthrough_button")
                                ) {
                                    Icon(Icons.Rounded.Explore, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tour")
                                }
                            }
                        }
                    }
                }

                // --- 6. LEDGER CURRENCY INFO (INR ONLY) ---
                BilliStaggeredItem(index = 5) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CurrencyRupee,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Ledger Currency", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Indian Rupee (₹ INR) — unified across all trips, splits & PDFs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // --- 7. SIGN OUT BUTTON ---
                BilliStaggeredItem(index = 6) {
                    OutlinedButton(
                        onClick = {
                            feedback?.tick()
                            showSignOutConfirm = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .billiPressScale()
                            .testTag("account_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (updateRelease != null) {
        UpdateBottomSheet(
            release = updateRelease!!,
            downloadState = downloadState,
            onStartDownload = { viewModel.startApkDownload(context) },
            onDismiss = { viewModel.dismissUpdateSheet() }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out of Billi?") },
            text = { Text("Your synced trips and receipts stay safely stored in the cloud.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        TripBubbleService.stop(context)
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
