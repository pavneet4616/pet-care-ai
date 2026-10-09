package com.petcare.companionlite.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.monetization.BillingManager
import com.petcare.companionlite.ui.ai.AiChatScreen
import com.petcare.companionlite.ui.ai.AiChatViewModel
import com.petcare.companionlite.ui.dashboard.DashboardScreen
import com.petcare.companionlite.ui.dashboard.DashboardViewModel
import com.petcare.companionlite.ui.emergency.EmergencyScreen
import com.petcare.companionlite.ui.onboarding.OnboardingScreen
import com.petcare.companionlite.ui.pet.AddPetScreen
import com.petcare.companionlite.ui.pet.PetDetailScreen
import com.petcare.companionlite.ui.report.WeeklyReportScreen
import com.petcare.companionlite.ui.stats.StatsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Screens reachable from the bottom navigation bar. */
private val BottomTabRoutes: Set<String> = setOf(
    AppDestination.Dashboard.route,
    AppDestination.CareTools.route,
    AppDestination.Emergency.route,
    AppDestination.Settings.route,
)

/** Duration of the quick fade used when switching between bottom tabs. */
private const val TAB_FADE_MILLIS = 200

@Composable
fun PetCareApp(
    deepLinkKey: Int = 0,
    openWeeklyReport: Boolean = false,
    openAiChatPetId: Long = 0L,
    openAiChatPrefill: String = "",
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as PetCareApplication
    val dashboardViewModel: DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()

    // The tab the visible screen belongs to. Sub-screens (e.g. pet detail) keep the tab they were opened from.
    var lastTabRoute by rememberSaveable { mutableStateOf(AppDestination.Dashboard.route) }
    val selectedTabRoute = currentRoute?.takeIf { it in BottomTabRoutes } ?: lastTabRoute
    LaunchedEffect(selectedTabRoute) { lastTabRoute = selectedTabRoute }

    val onOnboarding = currentRoute == AppDestination.Onboarding.route
    val onAiChat = currentRoute?.startsWith("ai-chat") == true
    // No bottom navigation during onboarding or in the focused chat screen.
    val showBottomBar = !onOnboarding && !onAiChat
    val hideAiFab = currentRoute == null || onAiChat || onOnboarding ||
        currentRoute == AppDestination.Settings.route || currentRoute == AppDestination.Emergency.route

    val launchAiPurchase: (Boolean) -> Unit = { weekly ->
        val hostActivity = activity
        if (hostActivity == null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.billing_no_activity),
                )
            }
        } else {
            scope.launch {
                delay(400)
                if (weekly) {
                    app.container.billingManager.launchAiWeekly(hostActivity)
                } else {
                    app.container.billingManager.launchAiMonthly(hostActivity)
                }
            }
        }
    }
    var notificationRequestSignal by remember { mutableIntStateOf(0) }
    var notificationsEnabled by remember { mutableStateOf(context.notificationsEnabled()) }
    val selectedPet = dashboardState.pets.firstOrNull { it.pet.id == dashboardState.settings.selectedPetId } ?: dashboardState.pets.firstOrNull()

    // Localized price from Play, or empty until the product details are loaded (the paywall shows a loading label).
    fun priceFor(productId: String): String =
        dashboardState.billing.products.firstOrNull { it.id == productId }?.priceText.orEmpty()

    LaunchedEffect(activity) {
        app.container.reviewPrompter.positiveMoments.collect { activity?.let { app.container.reviewPrompter.request(it) } }
    }

    // Deep link: Send to ViewModel to avoid NavHost cold-start race conditions
    LaunchedEffect(openWeeklyReport, deepLinkKey) {
        if (openWeeklyReport) {
            dashboardViewModel.handleDeepLinkWeeklyReport()
        }
    }

    // Deep link from AI follow-up notification: open chat once pets are loaded.
    var aiDeepLinkConsumed by remember(deepLinkKey) { mutableStateOf(false) }
    LaunchedEffect(openAiChatPetId, dashboardState.pets.isNotEmpty(), aiDeepLinkConsumed) {
        if (!aiDeepLinkConsumed && openAiChatPetId != 0L && dashboardState.pets.isNotEmpty()) {
            aiDeepLinkConsumed = true
            navController.navigate(AppDestination.AiChat.createRoute(openAiChatPetId, openAiChatPrefill)) {
                launchSingleTop = true
            }
        }
    }

    // Only navigate to onboarding after DataStore has loaded (settingsLoaded = true)
    // to avoid the race condition where default values trigger onboarding every launch
    LaunchedEffect(dashboardState.settingsLoaded, dashboardState.settings.onboardingCompleted, dashboardState.pets.isEmpty()) {
        if (dashboardState.settingsLoaded && !dashboardState.settings.onboardingCompleted && dashboardState.pets.isEmpty()) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != AppDestination.Onboarding.route) {
                navController.navigate(AppDestination.Onboarding.route) {
                    popUpTo(AppDestination.Dashboard.route) { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(dashboardState.navigateToWeeklyReport) {
        if (dashboardState.navigateToWeeklyReport) {
            navController.navigate(AppDestination.WeeklyReport.route) {
                launchSingleTop = true
            }
            dashboardViewModel.markDeepLinkConsumed()
        }
    }

    NotificationPermissionEffect(
        hasReminder = app.container.repository.hasReminders.collectAsStateWithLifecycle(initialValue = false).value,
        hasBeenPrompted = dashboardState.settings.notificationPromptShown,
        requestSignal = notificationRequestSignal,
        onPromptHandled = {
            dashboardViewModel.markNotificationPromptShown()
            notificationsEnabled = context.notificationsEnabled()
        },
        onResult = { granted ->
            app.container.diagnostics.event("notif_permission_result",if (granted) "granted" else "denied")
            notificationsEnabled = granted || context.notificationsEnabled()
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (notificationsEnabled) {
                        context.getString(R.string.notifications_enabled_message)
                    } else {
                        context.getString(R.string.notifications_off_message)
                    },
                )
            }
        },
    )

    LaunchedEffect(Unit) {
        dashboardViewModel.startBilling()
    }

    val adsAllowed by app.container.adsManager.adsAllowed.collectAsStateWithLifecycle()
    LaunchedEffect(adsAllowed, dashboardState.settings.hasNoAds) {
        if (adsAllowed && !dashboardState.settings.hasNoAds) app.container.adsManager.loadRewarded(context)
    }

    LaunchedEffect(dashboardState.billing.message) {
        val message = dashboardState.billing.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        dashboardViewModel.consumeBillingMessage()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = context.notificationsEnabled()
                dashboardViewModel.refreshBilling()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            AnimatedVisibility(
                visible = dashboardState.pets.isNotEmpty() && !hideAiFab,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        val petId = selectedPet?.pet?.id ?: return@FloatingActionButton
                        navController.navigate(AppDestination.AiChat.createRoute(petId)) {
                            launchSingleTop = true
                        }
                    },
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = stringResource(R.string.ask_ai_fab))
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                NavigationBar {
                    val items = listOf(
                        Triple(AppDestination.Dashboard, Icons.Filled.Home, R.string.nav_dashboard),
                        Triple(AppDestination.CareTools, Icons.Filled.Handyman, R.string.nav_tools),
                        Triple(AppDestination.Emergency, Icons.Filled.LocalHospital, R.string.nav_emergency),
                        Triple(AppDestination.Settings, Icons.Filled.Settings, R.string.nav_settings),
                    )
                    items.forEach { (destination, icon, labelRes) ->
                        val tabRoute = destination.route
                        NavigationBarItem(
                            selected = selectedTabRoute == tabRoute,
                            onClick = {
                                when {
                                    // Re-tapping the tab on its own root screen does nothing.
                                    currentRoute == tabRoute -> Unit
                                    // Re-tapping the active tab from a sub-screen pops back to its root.
                                    selectedTabRoute == tabRoute &&
                                        navController.popBackStack(tabRoute, inclusive = false) -> Unit
                                    else -> {
                                        val route = when (destination) {
                                            is AppDestination.CareTools -> selectedPet?.pet?.id
                                                ?.let { AppDestination.CareTools.createRoute(it) }
                                                ?: AppDestination.Dashboard.route
                                            else -> destination.route
                                        }
                                        lastTabRoute = tabRoute
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = destination != AppDestination.CareTools
                                        }
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Dashboard.route,
                // Tab-to-tab switches fade quickly; everything else slides as before.
                enterTransition = {
                    if (initialState.isBottomTab() && targetState.isBottomTab()) {
                        fadeIn(animationSpec = tween(TAB_FADE_MILLIS))
                    } else {
                        slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()
                    }
                },
                exitTransition = {
                    if (initialState.isBottomTab() && targetState.isBottomTab()) {
                        fadeOut(animationSpec = tween(TAB_FADE_MILLIS))
                    } else {
                        slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                    }
                },
                popEnterTransition = {
                    if (initialState.isBottomTab() && targetState.isBottomTab()) {
                        fadeIn(animationSpec = tween(TAB_FADE_MILLIS))
                    } else {
                        slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                    }
                },
                popExitTransition = {
                    if (initialState.isBottomTab() && targetState.isBottomTab()) {
                        fadeOut(animationSpec = tween(TAB_FADE_MILLIS))
                    } else {
                        slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()
                    }
                },
            ) {
                composable(AppDestination.Onboarding.route) {
                    OnboardingScreen(
                        onComplete = { petId ->
                            app.container.diagnostics.event("onboarding_complete")
                            navController.navigate(AppDestination.Dashboard.route) {
                                popUpTo(AppDestination.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(AppDestination.Dashboard.route) {
                    DashboardScreen(
                        state = dashboardState,
                        onSelectPet = { id -> scope.launch { app.container.settingsStore.selectPet(id) } },
                        notificationsEnabled = notificationsEnabled,
                        onPetClick = { petId -> navController.navigate(AppDestination.Detail.createRoute(petId)) },
                        onAddPet = { navController.navigate(AppDestination.AddPet.route) },
                        onEnableNotifications = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                // Before Android 13 there is no runtime prompt: notifications can only be turned on in system settings.
                                if (context.notificationsEnabled()) {
                                    notificationsEnabled = true
                                } else {
                                    context.openAppNotificationSettings()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.notifications_system_message))
                                    }
                                }
                            } else {
                                val hostActivity = activity
                                if (
                                    hostActivity != null &&
                                    dashboardState.settings.notificationPromptShown &&
                                    !ActivityCompat.shouldShowRequestPermissionRationale(
                                        hostActivity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) &&
                                    !notificationsEnabled
                                ) {
                                    context.openAppNotificationSettings()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.notifications_system_message))
                                    }
                                } else {
                                    notificationRequestSignal += 1
                                }
                            }
                        },
                        onBuyPremium = {
                            val hostActivity = activity
                            if (hostActivity != null) {
                                app.container.billingManager.launchPurchase(
                                    hostActivity,
                                    BillingManager.aiMonthlyProductId,
                                )
                            }
                        },
                        onBuyRemoveAds = {
                            val hostActivity = activity
                            if (hostActivity != null) {
                                app.container.billingManager.launchPurchase(
                                    hostActivity,
                                    BillingManager.aiMonthlyProductId,
                                )
                            }
                        },
                        onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                        onOpenTools = { petId -> navController.navigate(AppDestination.CareTools.createRoute(petId)) },
                        onOpenAi = { petId ->
                            navController.navigate(AppDestination.AiChat.createRoute(petId)) {
                                launchSingleTop = true
                            }
                        },
                        weeklyPriceText = priceFor(BillingManager.aiWeeklyProductId),
                        monthlyPriceText = priceFor(BillingManager.aiMonthlyProductId),
                        weeklyReady = dashboardState.billing.weeklyReady,
                        monthlyReady = dashboardState.billing.monthlyReady,
                        billingLoading = dashboardState.billing.isLoading,
                        billingMessage = dashboardState.billing.message,
                        onBuyWeekly = { launchAiPurchase(true) },
                        onBuyMonthly = { launchAiPurchase(false) },
                        onRestorePurchases = { dashboardViewModel.restorePurchases() },
                        onViewWeeklyReport = { dashboardViewModel.handleDeepLinkWeeklyReport() },
                        onDismissWeeklyReportBanner = { dashboardViewModel.markBannerShown() },
                        onConsumeCelebration = { dashboardViewModel.consumeCelebration() },
                        onLogQuickAction = { petId, taskType -> dashboardViewModel.logQuickAction(petId, taskType) },
                        onLogWaterQuickAction = { petId -> dashboardViewModel.logWaterQuickAction(petId) }
                    )
                }
                composable(AppDestination.AddPet.route) {
                    AddPetScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { petId ->
                            navController.navigate(AppDestination.Detail.createRoute(petId)) {
                                popUpTo(AppDestination.Dashboard.route)
                            }
                        },
                    )
                }
                composable(AppDestination.EditPet.route) { entry ->
                    val petId = entry.arguments?.getString("petId")?.toLongOrNull() ?: 0L
                    AddPetScreen(
                        onBack = { navController.popBackStack() },
                        editPetId = petId,
                        onSaved = {
                            navController.popBackStack()
                        },
                    )
                }
                composable(AppDestination.Detail.route) {
                    PetDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStats = { petId -> navController.navigate(AppDestination.Stats.createRoute(petId)) },
                        onEditPet = { petId -> navController.navigate(AppDestination.EditPet.createRoute(petId)) },
                        onOpenTools = { petId -> navController.navigate(AppDestination.CareTools.createRoute(petId)) },
                        onMessage = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )
                }
                composable(AppDestination.Stats.route) {
                    StatsScreen(onBack = { navController.popBackStack() })
                }
                composable(AppDestination.Emergency.route) {
                    EmergencyScreen(
                        onAskAi = {
                            val petId = selectedPet?.pet?.id
                            if (petId != null) {
                                navController.navigate(
                                    AppDestination.AiChat.createRoute(
                                        petId,
                                        context.getString(R.string.emergency_ai_prompt),
                                    ),
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
                composable(AppDestination.WeeklyReport.route) {
                    WeeklyReportScreen(
                        onBack = { navController.popBackStack() },
                        onBuyPremium = {
                            val hostActivity = activity
                            if (hostActivity != null) {
                                app.container.billingManager.launchPurchase(
                                    hostActivity,
                                    BillingManager.aiMonthlyProductId,
                                )
                            }
                        },
                        onShare = { report ->
                            com.petcare.companionlite.core.ShareHelper.shareWeeklyReport(context, report)
                        },
                    )
                }
                composable(AppDestination.Settings.route) {
                    val petName = selectedPet?.pet?.name?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.ui_5ec089c9b1)
                    com.petcare.companionlite.ui.dashboard.SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onManagePets = {
                            navController.navigate(AppDestination.Dashboard.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenChat = { petId ->
                            navController.navigate(AppDestination.AiChat.createRoute(petId)) {
                                launchSingleTop = true
                            }
                        },
                        weeklyPriceText = priceFor(BillingManager.aiWeeklyProductId),
                        monthlyPriceText = priceFor(BillingManager.aiMonthlyProductId),
                        petName = petName,
                        weeklyReady = dashboardState.billing.weeklyReady,
                        monthlyReady = dashboardState.billing.monthlyReady,
                        billingLoading = dashboardState.billing.isLoading,
                        billingMessage = dashboardState.billing.message,
                        onBuyWeekly = { launchAiPurchase(true) },
                        onBuyMonthly = { launchAiPurchase(false) },
                        onRestorePurchases = { dashboardViewModel.restorePurchases() },
                    )
                }
                composable(AppDestination.CareTools.route) { backStackEntry ->
                    val petIdStr = backStackEntry.arguments?.getString("petId")
                    val petId = petIdStr?.toLongOrNull() ?: return@composable
                    com.petcare.companionlite.ui.pet.ToolsHubScreen(
                        petId = petId,
                        onBack = { navController.popBackStack() },
                        onAskAi = { id, prefill ->
                            navController.navigate(AppDestination.AiChat.createRoute(id, prefill)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    AppDestination.AiChat.route,
                    arguments = listOf(
                        navArgument("petId") { type = NavType.StringType },
                        navArgument("prefill") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val petId = entry.arguments?.getString("petId")?.toLongOrNull() ?: return@composable
                    val prefill = entry.arguments?.getString("prefill").orEmpty()
                    val petName = dashboardState.pets.firstOrNull { it.pet.id == petId }?.pet?.name
                        ?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.ui_5ec089c9b1)
                    val aiVm: AiChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        key = "ai-$petId",
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return AiChatViewModel(petId, app.container.aiRepository, app) as T
                            }
                        },
                    )
                    AiChatScreen(
                        petId = petId,
                        petName = petName,
                        prefill = prefill,
                        vm = aiVm,
                        weeklyPriceText = priceFor(BillingManager.aiWeeklyProductId),
                        monthlyPriceText = priceFor(BillingManager.aiMonthlyProductId),
                        weeklyReady = dashboardState.billing.weeklyReady,
                        monthlyReady = dashboardState.billing.monthlyReady,
                        billingLoading = dashboardState.billing.isLoading,
                        billingMessage = dashboardState.billing.message,
                        isPro = dashboardState.settings.hasFullAccess,
                        onBack = { navController.popBackStack() },
                        onBuyWeekly = { launchAiPurchase(true) },
                        onBuyMonthly = { launchAiPurchase(false) },
                        onRestorePurchases = { dashboardViewModel.restorePurchases() },
                        onWatchAdForBonus = {
                            val hostActivity = activity
                            if (hostActivity != null && app.container.adsManager.isRewardedReady()) {
                                scope.launch {
                                    val installation = app.container.settingsStore.getOrCreateInstallationId()
                                    app.container.adsManager.showRewarded(hostActivity, installation) { aiVm.retryWithBonus() }
                                }
                            } else {
                                // Not ready yet: make sure a load is running, then tell the user to try again.
                                app.container.adsManager.loadRewarded(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.reward_loading))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionEffect(
    hasReminder: Boolean,
    hasBeenPrompted: Boolean,
    requestSignal: Int,
    onPromptHandled: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPromptHandled()
        onResult(granted)
    }

    var showExplanation by remember { mutableStateOf(false) }
    LaunchedEffect(hasReminder, hasBeenPrompted) {
        if (hasReminder && !hasBeenPrompted && !context.notificationsEnabled()) showExplanation = true
    }
    if (showExplanation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExplanation = false; onPromptHandled() },
            title = { Text(stringResource(R.string.notifications)) },
            text = { Text(stringResource(R.string.notification_permission_reason)) },
            confirmButton = {
                TextButton(onClick = { showExplanation = false; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.enable_notifications))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExplanation = false; onPromptHandled() }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    }

    LaunchedEffect(requestSignal) {
        val granted = context.notificationsEnabled()
        if (granted) return@LaunchedEffect
        if (requestSignal > 0) {
            showExplanation = true
        }
    }
}

sealed class AppDestination(val route: String, val label: String) {
    data object Onboarding : AppDestination("onboarding", "Onboarding")
    data object Dashboard : AppDestination("dashboard", "Dashboard")
    data object AddPet : AppDestination("add-pet", "Add")
    data object Emergency : AppDestination("emergency", "Emergency")
    data object Detail : AppDestination("pet/{petId}", "Timeline") {
        fun createRoute(petId: Long): String = "pet/$petId"
    }
    data object EditPet : AppDestination("edit-pet/{petId}", "Edit") {
        fun createRoute(petId: Long): String = "edit-pet/$petId"
    }
    data object WeeklyReport : AppDestination("weekly-report", "Report")
    data object Settings : AppDestination("settings", "Settings")
    data object CareTools : AppDestination("care-tools/{petId}", "Tools") {
        fun createRoute(petId: Long): String = "care-tools/$petId"
    }
    data object Stats : AppDestination("stats/{petId}", "Stats") {
        fun createRoute(petId: Long): String = "stats/$petId"
    }
    data object AiChat : AppDestination("ai-chat/{petId}?prefill={prefill}", "AI Companion") {
        fun createRoute(petId: Long, prefill: String = ""): String =
            if (prefill.isBlank()) "ai-chat/$petId"
            else "ai-chat/$petId?prefill=${android.net.Uri.encode(prefill)}"
    }
}

private fun NavBackStackEntry.isBottomTab(): Boolean = destination.route?.let { it in BottomTabRoutes } == true

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Whether the app may post notifications right now (permission on 33+, app-level toggle on every API). */
private fun Context.notificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    startActivity(intent)
}
