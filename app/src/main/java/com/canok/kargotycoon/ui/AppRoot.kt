package com.canok.kargotycoon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canok.kargotycoon.KargoApplication
import com.canok.kargotycoon.R
import com.canok.kargotycoon.data.Notice
import com.canok.kargotycoon.data.SessionMode
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.ui.components.TutorialBanner
import com.canok.kargotycoon.ui.format.noticeText
import com.canok.kargotycoon.ui.navigation.BackStackSaver
import com.canok.kargotycoon.ui.navigation.KargoBottomBar
import com.canok.kargotycoon.ui.navigation.KargoTopBar
import com.canok.kargotycoon.ui.navigation.Navigator
import com.canok.kargotycoon.ui.navigation.Route
import com.canok.kargotycoon.ui.screens.CompanyScreen
import com.canok.kargotycoon.ui.screens.AssignDriverScreen
import com.canok.kargotycoon.ui.screens.DashboardScreen
import com.canok.kargotycoon.ui.screens.DriverScreen
import com.canok.kargotycoon.ui.screens.FleetScreen
import com.canok.kargotycoon.ui.screens.HireScreen
import com.canok.kargotycoon.ui.screens.JobOfferScreen
import com.canok.kargotycoon.ui.screens.JobsScreen
import com.canok.kargotycoon.ui.screens.LaunchScreen
import com.canok.kargotycoon.ui.screens.MapScreen
import com.canok.kargotycoon.ui.screens.MoreScreen
import com.canok.kargotycoon.ui.screens.PurchaseScreen
import com.canok.kargotycoon.ui.screens.ReadErrorScreen
import com.canok.kargotycoon.ui.screens.RecoveryScreen
import com.canok.kargotycoon.ui.screens.SettingsScreen
import com.canok.kargotycoon.ui.screens.TeamScreen
import com.canok.kargotycoon.ui.screens.VehicleScreen
import com.canok.kargotycoon.ui.screens.WelcomeScreen
import com.canok.kargotycoon.viewmodel.GameViewModel
import java.util.Locale

@Composable
fun KargoApp(viewModel: GameViewModel = viewModel()) {
    val sessionState by viewModel.state.collectAsStateWithLifecycle()
    var welcomeLanguage by rememberSaveable { mutableStateOf(defaultWelcomeLanguage()) }
    val language = sessionState.game?.settings?.languageTag ?: welcomeLanguage

    ForegroundLifecycle()

    AppLocale(language) {
        KargoTheme {
            Box(Modifier.fillMaxSize()) {
            when (sessionState.mode) {
                SessionMode.LOADING -> LaunchScreen()
                SessionMode.WELCOME -> WelcomeScreen(
                    languageTag = welcomeLanguage,
                    onLanguageChange = { welcomeLanguage = it },
                    onNewGame = { viewModel.startNewGame(welcomeLanguage) },
                )
                SessionMode.RECOVERY -> RecoveryScreen(
                    futureSave = sessionState.futureSave,
                    canRecover = sessionState.canRecover,
                    languageTag = welcomeLanguage,
                    onLanguageChange = { welcomeLanguage = it },
                    onRecover = { viewModel.recover() },
                    onNewGame = { viewModel.startNewGame(welcomeLanguage) },
                )
                SessionMode.READ_ERROR -> ReadErrorScreen(onRetry = { viewModel.retryOpen() })
                SessionMode.PLAYING -> sessionState.game?.let { game ->
                    PlayingScaffold(viewModel, game)
                } ?: LaunchScreen()
            }
                SessionNotices(
                    viewModel, sessionState.notice, sessionState.noticeSequence,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (sessionState.mode == SessionMode.PLAYING) 88.dp else 24.dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ForegroundLifecycle() {
    val context = LocalContext.current
    val session = remember(context) { (context.applicationContext as KargoApplication).session }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> session.setForeground(true)
                Lifecycle.Event.ON_STOP -> session.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) session.setForeground(true)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun SessionNotices(viewModel: GameViewModel, notice: Notice?, noticeSequence: Long, modifier: Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeMessage = notice?.let { noticeText(it) }
    val retryLabel = stringResource(R.string.action_retry)
    LaunchedEffect(noticeSequence, noticeMessage) {
        if (noticeMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = noticeMessage,
            actionLabel = if (notice == Notice.SAVING_FAILED) retryLabel else null,
            withDismissAction = notice != Notice.SAVING_FAILED,
            duration = if (notice == Notice.SAVING_FAILED) SnackbarDuration.Indefinite else SnackbarDuration.Short,
        )
        viewModel.clearNotice()
        if (result == SnackbarResult.ActionPerformed && notice == Notice.SAVING_FAILED) viewModel.retryOpen()
    }
    SnackbarHost(snackbarHostState, modifier)
}

@Composable
private fun PlayingScaffold(viewModel: GameViewModel, game: GameState) {
    val stack = rememberSaveable(saver = BackStackSaver) { androidx.compose.runtime.mutableStateListOf(Route.Dashboard) }
    val navigator = remember(stack) { Navigator(stack) }
    val feedback = com.canok.kargotycoon.ui.feedback.rememberKargoFeedback(game.settings.soundEnabled, game.settings.hapticsEnabled)
    BackHandler(enabled = navigator.canGoBack) { navigator.back() }

    Scaffold(
        topBar = { KargoTopBar(title = routeTitle(navigator.current), canGoBack = navigator.canGoBack, onBack = { navigator.back() }) },
        bottomBar = { KargoBottomBar(navigator.current) { navigator.selectTab(it) } },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        androidx.compose.runtime.CompositionLocalProvider(
            com.canok.kargotycoon.ui.feedback.LocalKargoFeedback provides feedback,
        ) {
            Column(Modifier.fillMaxSize().padding(inner)) {
                if (game.tutorial.step != com.canok.kargotycoon.game.domain.TutorialStep.COMPLETE && !game.tutorial.dismissed) {
                    TutorialBanner(
                        step = game.tutorial.step,
                        onAction = { navigator.selectTab(routeForTag(com.canok.kargotycoon.ui.state.tutorialRouteTag(game.tutorial.step))) },
                        onDismiss = { viewModel.setTutorialDismissed(true) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    ScreenHost(viewModel, game, navigator)
                }
            }
        }
    }
}

@Composable
private fun ScreenHost(viewModel: GameViewModel, game: GameState, navigator: Navigator) {
    val catalog = viewModel.catalog
    when (val route = navigator.current) {
        Route.Dashboard -> DashboardScreen(
            game = game,
            catalog = catalog,
            onEndDay = { viewModel.endDay() },
            onOpenJobs = { navigator.selectTab(Route.Jobs) },
            onOpenFleet = { navigator.selectTab(Route.Fleet) },
        )
        Route.Jobs -> JobsScreen(game = game, catalog = catalog, onOpenOffer = { navigator.push(Route.JobOffer(it)) })
        is Route.JobOffer -> JobOfferScreen(
            offerId = route.offerId,
            game = game,
            catalog = catalog,
            viewModel = viewModel,
            onBack = { navigator.back() },
        )
        Route.Fleet -> FleetScreen(
            game = game,
            catalog = catalog,
            onOpenVehicle = { navigator.push(Route.Vehicle(it)) },
            onOpenPurchase = { navigator.push(Route.Purchase) },
        )
        is Route.Vehicle -> VehicleScreen(
            vehicleId = route.vehicleId,
            game = game,
            catalog = catalog,
            viewModel = viewModel,
            onOpenAssign = { navigator.push(Route.AssignDriver(route.vehicleId)) },
            onBack = { navigator.back() },
        )
        Route.Purchase -> PurchaseScreen(game = game, catalog = catalog, viewModel = viewModel, onBack = { navigator.back() })
        is Route.AssignDriver -> AssignDriverScreen(
            vehicleId = route.vehicleId,
            game = game,
            onAssign = { driverId -> viewModel.assignDriver(driverId, com.canok.kargotycoon.game.domain.VehicleId(route.vehicleId)); navigator.back() },
            onBack = { navigator.back() },
        )
        Route.Team -> TeamScreen(
            game = game,
            catalog = catalog,
            onOpenDriver = { navigator.push(Route.Driver(it)) },
            onOpenHire = { navigator.push(Route.Hire) },
        )
        is Route.Driver -> DriverScreen(
            driverId = route.driverId,
            game = game,
            catalog = catalog,
            viewModel = viewModel,
            onBack = { navigator.back() },
        )
        Route.Hire -> HireScreen(game = game, catalog = catalog, viewModel = viewModel, onBack = { navigator.back() })
        Route.More -> MoreScreen(
            onOpenMap = { navigator.push(Route.Map) },
            onOpenCompany = { navigator.push(Route.Company) },
            onOpenSettings = { navigator.push(Route.Settings) },
        )
        Route.Map -> MapScreen(game = game, catalog = catalog)
        Route.Company -> CompanyScreen(game = game, catalog = catalog)
        Route.Settings -> SettingsScreen(
            game = game,
            onLanguage = { viewModel.changeSettings(game.settings.copy(languageTag = it)) },
            onSound = { viewModel.changeSettings(game.settings.copy(soundEnabled = it)) },
            onHaptics = { viewModel.changeSettings(game.settings.copy(hapticsEnabled = it)) },
            onTutorial = { viewModel.setTutorialDismissed(!it) },
            onReset = { viewModel.startNewGame(game.settings.languageTag) },
        )
    }
}

@Composable
private fun routeTitle(route: Route): String = when (route) {
    Route.Dashboard -> stringResource(R.string.nav_dashboard)
    Route.Jobs -> stringResource(R.string.nav_jobs)
    is Route.JobOffer -> stringResource(R.string.job_detail_title)
    Route.Fleet -> stringResource(R.string.nav_fleet)
    is Route.Vehicle -> stringResource(R.string.vehicle_detail_title)
    Route.Purchase -> stringResource(R.string.purchase_title)
    Route.Team -> stringResource(R.string.nav_team)
    is Route.Driver -> stringResource(R.string.driver_detail_title)
    Route.Hire -> stringResource(R.string.hire_title)
    is Route.AssignDriver -> stringResource(R.string.assign_title)
    Route.More -> stringResource(R.string.nav_more)
    Route.Map -> stringResource(R.string.map_title)
    Route.Company -> stringResource(R.string.company_title)
    Route.Settings -> stringResource(R.string.settings_title)
}

private fun routeForTag(tag: String): Route = when (tag) {
    "jobs" -> Route.Jobs
    "fleet" -> Route.Fleet
    "team" -> Route.Team
    else -> Route.Dashboard
}

private fun defaultWelcomeLanguage(): String = if (Locale.getDefault().language == "tr") "tr" else "en"
