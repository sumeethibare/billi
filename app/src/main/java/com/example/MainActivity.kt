package com.example

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.example.data.preferences.AppThemeMode
import com.example.domain.AppUpdateDownloadState
import com.example.domain.GitHubReleaseInfo
import com.example.domain.GitHubReleaseUpdater
import com.example.domain.ShareIntentParser
import com.example.domain.SharedExpenseDraft
import com.example.ui.components.BilliMotion
import com.example.ui.components.BilliToastController
import com.example.ui.components.BilliTopToastHost
import com.example.ui.components.LocalBilliToastController
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
private const val ROUTE_TRIP_DETAIL = "trip/{tripId}?title={title}"

private fun buildTripDetailRoute(tripId: String, title: String = ""): String {
    val encodedTitle = Uri.encode(title)
    return "trip/$tripId?title=$encodedTitle"
}

class MainActivity : ComponentActivity() {

    private val incomingIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check BEFORE any Firebase AI / Firestore SDK or UI initialization
        configureAppCheck(this, intent)

        val prefsRepo = AppPreferencesRepository(applicationContext)
        val initialPrefs = prefsRepo.readStartupPreferencesSync()
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialUseDark = when (initialPrefs.themeMode) {
            AppThemeMode.SYSTEM -> systemIsDark
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
        val initialBgColor = when {
            !initialUseDark -> 0xFFFAF8F4.toInt()
            initialPrefs.amoledDark -> 0xFF000000.toInt()
            else -> 0xFF111513.toInt()
        }
        window.setBackgroundDrawable(ColorDrawable(initialBgColor))

        val barStyle = if (initialUseDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        incomingIntentFlow.value = intent

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = initialPrefs
            )
            val incomingIntent by incomingIntentFlow.collectAsStateWithLifecycle()
            val toastScope = rememberCoroutineScope()
            val toastController = remember(toastScope) { BilliToastController(toastScope) }

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
                    CompositionLocalProvider(
                        LocalBilliToastController provides toastController
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            BilliAppRoot(
                                preferencesRepository = prefsRepo,
                                prefs = prefs,
                                incomingIntent = incomingIntent,
                                onConsumeIntent = { incomingIntentFlow.value = null }
                            )
                            BilliTopToastHost(
                                controller = toastController,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
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

    // Every-launch & resume GitHub Releases check using ETag conditional caching (304 = 0 rate limit)
    var startupUpdateRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var startupDownloadState by remember { mutableStateOf<AppUpdateDownloadState>(AppUpdateDownloadState.Idle) }
    var dismissedSessionTag by remember { mutableStateOf<String?>(null) }
    var lastForegroundCheckMs by remember { mutableLongStateOf(0L) }

    val cachedAvailableUpdate = remember(prefs.cachedReleaseJson) {
        GitHubReleaseUpdater.parseCachedAvailableUpdate(prefs)
    }
    val hasAvailableUpdate = startupUpdateRelease != null || cachedAvailableUpdate != null

    fun triggerSilentReleaseCheck() {
        val now = System.currentTimeMillis()
        // Debounce rapid foreground resumes to 60 seconds; cold launch always runs immediately
        if (lastForegroundCheckMs > 0L && (now - lastForegroundCheckMs) < 60_000L) return
        lastForegroundCheckMs = now
        scope.launch {
            GitHubReleaseUpdater.checkForUpdate(preferencesRepository, force = false)
                .onSuccess { info ->
                    if (info != null && info.isNewerThanCurrent && info.canDownloadInApp) {
                        if (dismissedSessionTag != info.tagName) {
                            startupUpdateRelease = info
                        }
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        triggerSilentReleaseCheck()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        triggerSilentReleaseCheck()
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
                navController.navigate(buildTripDetailRoute(quickAddTripId)) {
                    launchSingleTop = true
                }
            }
            !openTripId.isNullOrBlank() -> {
                onConsumeIntent()
                navController.navigate(buildTripDetailRoute(openTripId)) {
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_DASHBOARD,
                enterTransition = { BilliMotion.screenEnterForward(prefs.reduceMotion) },
                exitTransition = { BilliMotion.screenExitForward(prefs.reduceMotion) },
                popEnterTransition = { BilliMotion.screenPopEnter(prefs.reduceMotion) },
                popExitTransition = { BilliMotion.screenPopExit(prefs.reduceMotion) }
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
                        hasAvailableUpdate = hasAvailableUpdate,
                        pendingSharedDraft = pendingSharedDraft,
                        onDismissSharedDraft = { pendingSharedDraft = null },
                        onOpenTripWithSharedDraft = { tripId, tripTitle, draft ->
                            pendingSharedDraft = null
                            tripQuickAddDraft = tripId to draft
                            navController.navigate(buildTripDetailRoute(tripId, tripTitle))
                        },
                        onOpenTrip = { tripId, tripTitle ->
                            navController.navigate(buildTripDetailRoute(tripId, tripTitle))
                        },
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
                arguments = listOf(
                    navArgument("tripId") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
                val rawTitle = backStackEntry.arguments?.getString("title").orEmpty()
                val initialTripTitle = remember(rawTitle) {
                    runCatching { Uri.decode(rawTitle) }.getOrDefault(rawTitle)
                }
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
                    initialTripTitle = initialTripTitle,
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
                    ).onSuccess { downloaded ->
                        startupDownloadState = AppUpdateDownloadState.ReadyToInstall(
                            apkFile = downloaded.file,
                            checksumVerified = downloaded.checksumVerified
                        )
                    }.onFailure { err ->
                        startupDownloadState = AppUpdateDownloadState.Error(
                            err.localizedMessage ?: "Download failed."
                        )
                    }
                }
            },
            onDismiss = {
                dismissedSessionTag = release.tagName
                startupUpdateRelease = null
                startupDownloadState = AppUpdateDownloadState.Idle
            }
        )
    }
}
