package com.petcare.companionlite.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.monetization.BillingManager
import com.petcare.companionlite.ui.dashboard.DashboardScreen
import com.petcare.companionlite.ui.dashboard.DashboardViewModel
import com.petcare.companionlite.ui.ai.AiChatScreen
import com.petcare.companionlite.ui.ai.AiChatViewModel
import com.petcare.companionlite.ui.onboarding.OnboardingScreen
import com.petcare.companionlite.ui.emergency.EmergencyScreen
import com.petcare.companionlite.ui.pet.AddPetScreen
import com.petcare.companionlite.ui.pet.PetDetailScreen
import com.petcare.companionlite.ui.report.WeeklyReportScreen
import com.petcare.companionlite.ui.stats.StatsScreen
import com.petcare.companionlite.ui.theme.Blush
import com.petcare.companionlite.ui.theme.Cream
import com.petcare.companionlite.ui.theme.Mist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PetCareApp(
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
    val launchAiPurchase: (Boolean) -> Unit = { weekly ->
        val hostActivity = activity
        if (hostActivity == null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(com.petcare.companionlite.R.string.billing_no_activity),
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

    // Deep link: Send to ViewModel to avoid NavHost cold-start race conditions
    LaunchedEffect(openWeeklyReport) {
        if (openWeeklyReport) {
            dashboardViewModel.handleDeepLinkWeeklyReport()
        }
    }

    // Deep link from AI follow-up notification: open chat once pets are loaded.
    var aiDeepLinkConsumed by remember { mutableStateOf(false) }
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
        hasBeenPrompted = dashboardState.settings.notificationPromptShown,
        requestSignal = notificationRequestSignal,
        onPromptHandled = {
            dashboardViewModel.markNotificationPromptShown()
            notificationsEnabled = context.notificationsEnabled()
        },
        onResult = { granted ->
            notificationsEnabled = granted || context.notificationsEnabled()
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (notificationsEnabled) {
                        "Reminder notifications enabled."
                    } else {
                        "Notifications are off. Reminders will stay in-app until you enable them."
                    },
                )
            }
        },
    )

    LaunchedEffect(Unit) {
        app.container.adsManager.loadInterstitial(context)
        app.container.adsManager.loadRewarded(context)
        dashboardViewModel.startBilling()
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
            val hideFab = currentRoute == null ||
                currentRoute.startsWith("ai-chat") ||
                currentRoute == AppDestination.Settings.route ||
                currentRoute == AppDestination.Onboarding.route ||
                currentRoute == AppDestination.Emergency.route
            val showAiFab = dashboardState.pets.isNotEmpty() && !hideFab
            if (showAiFab) {
                FloatingActionButton(
                    onClick = {
                        val petId = dashboardState.pets.firstOrNull()?.pet?.id ?: return@FloatingActionButton
                        navController.navigate(AppDestination.AiChat.createRoute(petId)) {
                            launchSingleTop = true
                        }
                    },
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = androidx.compose.ui.res.stringResource(com.petcare.companionlite.R.string.ask_ai_fab))
                }
            }
        },
        bottomBar = {
            // Focused chat screen: no bottom navigation (more space, no accidental taps).
            if (currentRoute?.startsWith("ai-chat") != true) {
            NavigationBar {
                val items = listOf(
                    AppDestination.Dashboard to Icons.Filled.Home,
                    AppDestination.CareTools to Icons.Filled.Handyman,
                    AppDestination.Emergency to Icons.Filled.LocalHospital,
                    AppDestination.Settings to Icons.Filled.Settings,
                )
                items.forEach { (destination, icon) ->
                    val selected = backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val route = when (destination) {
                                is AppDestination.CareTools -> {
                                    val petId = dashboardState.pets.firstOrNull()?.pet?.id
                                    if (petId != null) AppDestination.CareTools.createRoute(petId)
                                    else AppDestination.Dashboard.route
                                }
                                else -> destination.route
                            }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = destination.label) },
                        label = {
                            val labelRes = when (destination) {
                                AppDestination.Dashboard -> com.petcare.companionlite.R.string.nav_dashboard
                                AppDestination.CareTools -> com.petcare.companionlite.R.string.nav_tools
                                AppDestination.Emergency -> com.petcare.companionlite.R.string.nav_emergency
                                AppDestination.Settings -> com.petcare.companionlite.R.string.nav_settings
                                else -> null
                            }
                            Text(if (labelRes != null) androidx.compose.ui.res.stringResource(labelRes) else destination.label)
                        },
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
                enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() },
            ) {
                composable(AppDestination.Onboarding.route) {
                    OnboardingScreen(
                        onComplete = { petId ->
                            navController.navigate(AppDestination.Dashboard.route) {
                                popUpTo(AppDestination.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(AppDestination.Dashboard.route) {
                    DashboardScreen(
                        state = dashboardState,
                        notificationsEnabled = notificationsEnabled,
                        onPetClick = { petId -> navController.navigate(AppDestination.Detail.createRoute(petId)) },
                        onAddPet = { navController.navigate(AppDestination.AddPet.route) },
                        onEnableNotifications = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                notificationsEnabled = true
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
                                        snackbarHostState.showSnackbar("Open notifications in system settings to re-enable reminders.")
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
                                    BillingManager.premiumProductId,
                                )
                            }
                        },
                        onBuyRemoveAds = {
                            val hostActivity = activity
                            if (hostActivity != null) {
                                app.container.billingManager.launchPurchase(
                                    hostActivity,
                                    BillingManager.removeAdsProductId,
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
                        weeklyPriceText = dashboardState.billing.products
                            .firstOrNull { it.id == BillingManager.aiWeeklyProductId }
                            ?.priceText ?: "$2.99",
                        monthlyPriceText = dashboardState.billing.products
                            .firstOrNull { it.id == BillingManager.aiMonthlyProductId }
                            ?.priceText ?: "$9.99",
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
                            if (activity != null && !dashboardState.settings.hasNoAds) {
                                app.container.adsManager.showInterstitialIfReady(activity)
                            }
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
                            val petId = dashboardState.pets.firstOrNull()?.pet?.id
                            if (petId != null) {
                                navController.navigate(
                                    AppDestination.AiChat.createRoute(
                                        petId,
                                        "This may be urgent. What safe steps can I take right now while contacting my vet?",
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
                                    BillingManager.premiumProductId,
                                )
                            }
                        },
                        onShare = { report ->
                            com.petcare.companionlite.core.ShareHelper.shareWeeklyReport(context, report)
                        },
                    )
                }
                composable(AppDestination.Settings.route) {
                    val weeklyPrice = dashboardState.billing.products
                        .firstOrNull { it.id == BillingManager.aiWeeklyProductId }
                        ?.priceText ?: "$2.99"
                    val monthlyPrice = dashboardState.billing.products
                        .firstOrNull { it.id == BillingManager.aiMonthlyProductId }
                        ?.priceText ?: "$9.99"
                    val petName = dashboardState.pets.firstOrNull()?.pet?.name ?: "your pet"
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
                        weeklyPriceText = weeklyPrice,
                        monthlyPriceText = monthlyPrice,
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
                        ?: "your pet"
                    val aiVm: AiChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        key = "ai-$petId",
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return AiChatViewModel(petId, app.container.aiRepository) as T
                            }
                        },
                    )
                    val weeklyPrice = dashboardState.billing.products
                        .firstOrNull { it.id == BillingManager.aiWeeklyProductId }
                        ?.priceText ?: "$2.99"
                    val monthlyPrice = dashboardState.billing.products
                        .firstOrNull { it.id == BillingManager.aiMonthlyProductId }
                        ?.priceText ?: "$9.99"
                    AiChatScreen(
                        petId = petId,
                        petName = petName,
                        prefill = prefill,
                        vm = aiVm,
                        weeklyPriceText = weeklyPrice,
                        monthlyPriceText = monthlyPrice,
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
                                app.container.adsManager.showRewarded(hostActivity) {
                                    aiVm.retryWithBonus()
                                }
                            } else {
                                app.container.adsManager.loadRewarded(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Rewarded video is loading. Try again in a moment.")
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

    LaunchedEffect(hasBeenPrompted) {
        val granted = context.notificationsEnabled()
        if (granted) return@LaunchedEffect
        if (!hasBeenPrompted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(requestSignal) {
        val granted = context.notificationsEnabled()
        if (granted) return@LaunchedEffect
        if (requestSignal > 0) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.notificationsEnabled(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    startActivity(intent)
}
