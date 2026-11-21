package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.firebase.BilliRepository
import com.example.data.firebase.SyncStatusMonitor
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutUser
import com.example.data.model.SplitMode
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesRepository
import com.example.domain.AppUpdateDownloadState
import com.example.domain.GitHubReleaseInfo
import com.example.domain.GitHubReleaseUpdater
import com.example.domain.ShareIntentParser
import com.example.domain.SharedExpenseDraft
import com.example.ui.components.BilliMotion
import com.example.ui.components.ProvideBilliFeedback
import com.example.ui.components.SignInAvatarMorphOverlay
import com.example.ui.components.UpdateBottomSheet
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.TripDetailScreen
import com.example.ui.theme.BilliTheme
import com.example.ui.viewmodel.AccountViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.TripDetailViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_TRIP_DETAIL = "trip/{tripId}"

class MainActivity : ComponentActivity() {

    private val incomingIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check BEFORE any Firebase AI / Firestore SDK or UI initialization
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        incomingIntentFlow.value = intent

        setContent {
            val prefsRepo = remember { AppPreferencesRepository(applicationContext) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )
            val incomingIntent by incomingIntentFlow.collectAsStateWithLifecycle()

            ProvideBilliFeedback(
                hapticsEnabled = prefs.hapticsEnabled,
                soundEffectsEnabled = prefs.soundEffectsEnabled
            ) {
                BilliTheme(
                    themeMode = prefs.themeMode,
                    amoledDark = prefs.amoledDark,
                    useSystemFont = prefs.useSystemFont,
                    reduceMotion = prefs.reduceMotion
                ) {
                    BilliAppRoot(
                        preferencesRepository = prefsRepo,
                        prefs = prefs,
                        incomingIntent = incomingIntent,
                        onConsumeIntent = { incomingIntentFlow.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentFlow.value = intent
    }
}

@Composable
fun BilliAppRoot(
    preferencesRepository: AppPreferencesRepository,
    prefs: AppPreferences,
    incomingIntent: Intent?,
    onConsumeIntent: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    // Coordinates the Google avatar morph from AuthScreen center orb -> top-right Dashboard avatar
    var pendingMorphAvatar by remember { mutableStateOf<Pair<String?, String>?>(null) }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { photoUrl, displayName, animateMorph ->
                if (animateMorph) {
                    pendingMorphAvatar = photoUrl to displayName
                }
            }
        )
    } else {
        key(user.uid) {
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Traveler"
            BilliAuthenticatedNav(
                preferencesRepository = preferencesRepository,
                prefs = prefs,
                currentUserId = user.uid,
                currentUserName = displayName,
                currentUserEmail = user.email,
                currentUserPhotoUrl = user.photoUrl?.toString(),
                pendingMorphAvatar = pendingMorphAvatar,
                onClearMorphAvatar = { pendingMorphAvatar = null },
                incomingIntent = incomingIntent,
                onConsumeIntent = onConsumeIntent
            )
        }
    }
}

@Composable
private fun BilliAuthenticatedNav(
    preferencesRepository: AppPreferencesRepository,
    prefs: AppPreferences,
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String?,
    currentUserPhotoUrl: String?,
    pendingMorphAvatar: Pair<String?, String>?,
    onClearMorphAvatar: () -> Unit,
    incomingIntent: Intent?,
    onConsumeIntent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val syncMonitor = remember(context) { SyncStatusMonitor(context.applicationContext) }
    val navController = rememberNavController()

    var dashboardRevealProgress by remember(pendingMorphAvatar) {
        mutableFloatStateOf(if (pendingMorphAvatar != null) 0f else 1f)
    }

    // "Share-to-Split" & Glance Widget quick-launch state
    var pendingSharedDraft by remember { mutableStateOf<SharedExpenseDraft?>(null) }
    var tripQuickAddDraft by remember { mutableStateOf<Pair<String, SharedExpenseDraft?>?>(null) }

    // Silent 12-hour throttled GitHub Releases check on startup
    var startupUpdateRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var startupDownloadState by remember { mutableStateOf<AppUpdateDownloadState>(AppUpdateDownloadState.Idle) }

    LaunchedEffect(Unit) {
        GitHubReleaseUpdater.checkForUpdate(preferencesRepository, force = false)
            .onSuccess { info ->
                if (info != null && info.isNewerThanCurrent) {
                    startupUpdateRelease = info
                }
            }
    }

    // Parse incoming Share-to-Split (ACTION_SEND) or Glance Widget deep-link intents
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val quickAddTripId = intent.getStringExtra("extra_quick_add_trip_id")
        val openTripId = intent.getStringExtra("extra_open_trip_id")
        when {
            !quickAddTripId.isNullOrBlank() -> {
                onConsumeIntent()
                tripQuickAddDraft = quickAddTripId to null
                navController.navigate("trip/$quickAddTripId") {
                    launchSingleTop = true
                }
            }
            !openTripId.isNullOrBlank() -> {
                onConsumeIntent()
                navController.navigate("trip/$openTripId") {
                    launchSingleTop = true
                }
            }
            ShareIntentParser.hasSharePayload(intent) -> {
                onConsumeIntent()
                val parsed = ShareIntentParser.parseShareIntent(
                    context = context,
                    intent = intent,
                    isOnline = syncMonitor.isCurrentlyOnline()
                )
                if (parsed != null) {
                    pendingSharedDraft = parsed
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASHBOARD,
            enterTransition = { BilliMotion.screenEnterForward },
            exitTransition = { BilliMotion.screenExitForward },
            popEnterTransition = { BilliMotion.screenPopEnter },
            popExitTransition = { BilliMotion.screenPopExit }
        ) {
            composable(ROUTE_DASHBOARD) {
                val dashboardViewModel: DashboardViewModel = viewModel(
                    key = "dashboard_$currentUserId",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            DashboardViewModel(
                                repository = BilliRepository(app),
                                syncMonitor = syncMonitor,
                                currentUserId = currentUserId,
                                currentUserName = currentUserName,
                                currentUserPhotoUrl = currentUserPhotoUrl
                            )
                        }
                    }
                )

                DashboardScreen(
                    viewModel = dashboardViewModel,
                    revealProgress = dashboardRevealProgress,
                    showOnboardingWalkthrough = !prefs.hasSeenOnboardingWalkthrough,
                    onCompleteWalkthrough = {
                        scope.launch { preferencesRepository.setHasSeenOnboardingWalkthrough(true) }
                    },
                    pendingSharedDraft = pendingSharedDraft,
                    onDismissSharedDraft = { pendingSharedDraft = null },
                    onOpenTripWithSharedDraft = { tripId, draft ->
                        pendingSharedDraft = null
                        tripQuickAddDraft = tripId to draft
                        navController.navigate("trip/$tripId")
                    },
                    onOpenTrip = { tripId -> navController.navigate("trip/$tripId") },
                    onOpenAccount = { navController.navigate(ROUTE_ACCOUNT) }
                )
            }

            composable(ROUTE_ACCOUNT) {
                val accountViewModel: AccountViewModel = viewModel(
                    key = "account_$currentUserId",
                    factory = viewModelFactory {
                        initializer {
                            AccountViewModel(preferencesRepository = preferencesRepository)
                        }
                    }
                )

                AccountScreen(
                    viewModel = accountViewModel,
                    userDisplayName = currentUserName,
                    userEmail = currentUserEmail,
                    userPhotoUrl = currentUserPhotoUrl,
                    userId = currentUserId,
                    onNavigateBack = { navController.popBackStack() },
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }

            composable(
                route = ROUTE_TRIP_DETAIL,
                arguments = listOf(navArgument("tripId") { type = NavType.StringType })
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
                val detailViewModel: TripDetailViewModel = viewModel(
                    key = "trip_${currentUserId}_$tripId",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            TripDetailViewModel(
                                appContext = app.applicationContext,
                                preferencesRepository = preferencesRepository,
                                repository = BilliRepository(app),
                                syncMonitor = syncMonitor,
                                tripId = tripId,
                                currentUserId = currentUserId
                            )
                        }
                    }
                )
                val expenseViewModel: ExpenseViewModel = viewModel(
                    key = "expense_${currentUserId}_$tripId",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            ExpenseViewModel(
                                repository = BilliRepository(app),
                                syncMonitor = syncMonitor,
                                preferencesRepository = preferencesRepository
                            )
                        }
                    }
                )

                val quickLaunchForThisTrip = tripQuickAddDraft?.takeIf { it.first == tripId }

                TripDetailScreen(
                    viewModel = detailViewModel,
                    expenseViewModel = expenseViewModel,
                    lastUsedSplitMode = SplitMode.fromWire(prefs.lastUsedSplitMode),
                    autoOpenAddBill = quickLaunchForThisTrip != null,
                    initialSharedDraft = quickLaunchForThisTrip?.second,
                    onConsumeLaunchExtras = { tripQuickAddDraft = null },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Animated Google Profile Avatar Morph Overlay (AuthScreen -> Top-Right Dashboard Slot)
        if (pendingMorphAvatar != null) {
            SignInAvatarMorphOverlay(
                photoUrl = pendingMorphAvatar.first,
                displayName = pendingMorphAvatar.second,
                onRevealProgress = { p -> dashboardRevealProgress = p },
                onFinished = {
                    dashboardRevealProgress = 1f
                    onClearMorphAvatar()
                }
            )
        }
    }

    // Silent Startup GitHub Release Update Bottom Sheet
    startupUpdateRelease?.let { release ->
        UpdateBottomSheet(
            release = release,
            downloadState = startupDownloadState,
            onStartDownload = {
                scope.launch {
                    startupDownloadState = AppUpdateDownloadState.Downloading(0f, 0L, release.apkSizeBytes)
                    GitHubReleaseUpdater.downloadApk(
                        context = context.applicationContext,
                        release = release,
                        onProgress = { startupDownloadState = it }
                    ).onSuccess { file ->
                        startupDownloadState = AppUpdateDownloadState.ReadyToInstall(file)
                    }.onFailure { err ->
                        startupDownloadState = AppUpdateDownloadState.Error(
                            err.localizedMessage ?: "Download failed."
                        )
                    }
                }
            },
            onDismiss = {
                startupUpdateRelease = null
                startupDownloadState = AppUpdateDownloadState.Idle
            }
        )
    }
}
