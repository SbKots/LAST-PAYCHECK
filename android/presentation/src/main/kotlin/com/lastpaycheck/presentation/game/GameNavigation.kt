package com.lastpaycheck.presentation.game

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lastpaycheck.domain.model.AppLanguage
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.account.AccountScreen
import com.lastpaycheck.presentation.account.AccountViewModel
import com.lastpaycheck.presentation.startup.StartupViewModel
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import java.util.Locale
import kotlinx.serialization.Serializable

internal object GameRoutes {
    @Serializable
    data object Start

    @Serializable
    data object NewGame

    @Serializable
    data class Apartment(val sessionId: String)

    @Serializable
    data class Shop(val sessionId: String)

    @Serializable
    data class City(val sessionId: String)

    @Serializable
    data class StoreSelection(val sessionId: String)

    @Serializable
    data class Dealership(val sessionId: String)

    @Serializable
    data class Garage(val sessionId: String)

    @Serializable
    data class Work(val sessionId: String)

    @Serializable
    data class Training(val sessionId: String)

    @Serializable
    data class Statistics(val sessionId: String)

    @Serializable
    data class Hero(val sessionId: String)

    @Serializable
    data class Fridge(val sessionId: String)

    @Serializable
    data class Results(val sessionId: String)

    @Serializable
    data object Account

    @Serializable
    data object Settings
}

@Composable
fun GameNavigation(
    game: MainViewModel,
    startup: StartupViewModel,
    account: AccountViewModel,
    finances: FinanceViewModel,
    onThemeChanged: (Boolean) -> Unit
) {
    val nav = rememberNavController()
    val state by game.state.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val preview by game.preview.collectAsStateWithLifecycle()
    val settings by startup.settings.collectAsStateWithLifecycle()
    val theme = (settings as? DomainResult.Success)?.value?.theme
    val language = (settings as? DomainResult.Success)?.value?.language ?: AppLanguage.SYSTEM
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedConfiguration = remember(configuration, language) {
        Configuration(configuration).apply {
            when (language) {
                AppLanguage.RUSSIAN -> setLocale(Locale.forLanguageTag("ru"))
                AppLanguage.ENGLISH -> setLocale(Locale.ENGLISH)
                AppLanguage.SYSTEM -> Unit
            }
        }
    }
    val localizedContext = remember(context, localizedConfiguration) {
        context.createConfigurationContext(localizedConfiguration)
    }
    val dark = when (theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect { onThemeChanged(dark) }
    val destination = entry?.destination
    LaunchedEffect(destination) {
        if (destination?.hasRoute<GameRoutes.Apartment>() == true) game.dismissSectionEffect()
    }
    if (destination?.hasRoute<GameRoutes.Apartment>() == true ||
        destination?.hasRoute<GameRoutes.Work>() == true ||
        destination?.hasRoute<GameRoutes.Shop>() == true ||
        destination?.hasRoute<GameRoutes.City>() == true ||
        destination?.hasRoute<GameRoutes.StoreSelection>() == true ||
        destination?.hasRoute<GameRoutes.Dealership>() == true ||
        destination?.hasRoute<GameRoutes.Garage>() == true ||
        destination?.hasRoute<GameRoutes.Training>() == true ||
        destination?.hasRoute<GameRoutes.Hero>() == true ||
        destination?.hasRoute<GameRoutes.Fridge>() == true ||
        destination?.hasRoute<GameRoutes.Statistics>() == true
    ) {
        GameLifecycle(game)
    }
    val openHome: (GameSession) -> Unit = {
        nav.navigate(GameRoutes.Apartment(it.id)) {
            popUpTo<GameRoutes.Start>()
            launchSingleTop = true
        }
    }

    val currentSession = (state as? MainUiState.Content)?.session
    LaunchedEffect(currentSession?.status, destination) {
        if (currentSession?.status == SessionStatus.FINISHED &&
            destination?.hasRoute<GameRoutes.Start>() != true &&
            destination?.hasRoute<GameRoutes.NewGame>() != true &&
            destination?.hasRoute<GameRoutes.Results>() != true &&
            destination?.hasRoute<GameRoutes.Settings>() != true &&
            destination?.hasRoute<GameRoutes.Account>() != true
        ) {
            nav.navigate(GameRoutes.Results(currentSession.id)) { launchSingleTop = true }
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration
    ) {
        LastPaycheckTheme(darkTheme = dark) {
            Column(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = GameRoutes.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<GameRoutes.Start> {
                        GameStartScreen(
                            state = state,
                            dark = dark,
                            onContinue = { game.resumeGame(CheckpointChoice.LATEST, openHome) },
                            onRestore = { game.resumeGame(CheckpointChoice.PREVIOUS, openHome) },
                            onNew = { nav.navigate(GameRoutes.NewGame) { launchSingleTop = true } },
                            onRetry = game::retry,
                            onTheme = { startup.setTheme(!dark) },
                            onSettings = {
                                nav.navigate(
                                    GameRoutes.Settings
                                ) { launchSingleTop = true }
                            }
                        )
                    }
                    composable<GameRoutes.NewGame> {
                        LaunchedEffect(Unit) { game.prepareNewGame() }
                        BackHandler(enabled = state == MainUiState.Loading) {}
                        NewGameScreen(
                            preview = preview,
                            replacing = state is MainUiState.Content,
                            creating = state == MainUiState.Loading,
                            onStart = { game.createGame(openHome) },
                            onBack = { nav.popBackStack() },
                            onRetry = game::prepareNewGame
                        )
                    }
                    composable<GameRoutes.Apartment> { entry ->
                        val route = entry.toRoute<GameRoutes.Apartment>()
                        LaunchedEffect(route.sessionId) { game.restoreHome(route.sessionId) }
                        val homeState = state.forSession(route.sessionId)
                        GameHomeScreen(
                            state = homeState,
                            onAction = game::start,
                            onPause = game::togglePause,
                            onSave = game::save,
                            onRetry = game::retry,
                            onBack = { nav.popBackStack() },
                            onCity = if (homeState is MainUiState.Content) {
                                {
                                    nav.navigate(
                                        GameRoutes.City(route.sessionId)
                                    ) { launchSingleTop = true }
                                }
                            } else {
                                null
                            },
                            onTraining = {
                                nav.navigate(
                                    GameRoutes.Training(route.sessionId)
                                ) { launchSingleTop = true }
                            },
                            onFridge = {
                                nav.navigate(
                                    GameRoutes.Fridge(route.sessionId)
                                ) { launchSingleTop = true }
                            },
                            onSettings = {
                                nav.navigate(
                                    GameRoutes.Settings
                                ) { launchSingleTop = true }
                            }
                        )
                    }
                    composable<GameRoutes.Work> { entry ->
                        val route = entry.toRoute<GameRoutes.Work>()
                        SectionRoute(game, route.sessionId, Section.Work) { nav.popBackStack() }
                    }
                    composable<GameRoutes.Shop> { entry ->
                        val route = entry.toRoute<GameRoutes.Shop>()
                        SectionRoute(game, route.sessionId, Section.Shop) { nav.popBackStack() }
                    }
                    composable<GameRoutes.City> { entry ->
                        val route = entry.toRoute<GameRoutes.City>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            CityScreen(
                                session,
                                {
                                    nav.navigate(
                                        GameRoutes.Work(session.id)
                                    ) { launchSingleTop = true }
                                },
                                {
                                    nav.navigate(
                                        GameRoutes.StoreSelection(session.id)
                                    ) { launchSingleTop = true }
                                },
                                {
                                    nav.navigate(
                                        GameRoutes.Garage(session.id)
                                    ) { launchSingleTop = true }
                                },
                                { nav.popBackStack<GameRoutes.Apartment>(false) },
                                { nav.popBackStack() }
                            )
                        }
                    }
                    composable<GameRoutes.StoreSelection> { entry ->
                        val route = entry.toRoute<GameRoutes.StoreSelection>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            StoreSelectionScreen(
                                session,
                                game,
                                {
                                    nav.navigate(
                                        GameRoutes.Shop(session.id)
                                    ) { launchSingleTop = true }
                                },
                                {
                                    nav.navigate(
                                        GameRoutes.Dealership(session.id)
                                    ) { launchSingleTop = true }
                                },
                                { nav.popBackStack() }
                            )
                        }
                    }
                    composable<GameRoutes.Dealership> { entry ->
                        val route = entry.toRoute<GameRoutes.Dealership>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            VehicleStatusScreen(session, garage = false) { nav.popBackStack() }
                        }
                    }
                    composable<GameRoutes.Garage> { entry ->
                        val route = entry.toRoute<GameRoutes.Garage>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            VehicleStatusScreen(session, garage = true) { nav.popBackStack() }
                        }
                    }
                    composable<GameRoutes.Statistics> { entry ->
                        val route = entry.toRoute<GameRoutes.Statistics>()
                        FinanceScreen(route.sessionId, finances, game) { nav.popBackStack() }
                    }
                    composable<GameRoutes.Hero> { entry ->
                        val route = entry.toRoute<GameRoutes.Hero>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            HeroScreen(
                                session,
                                game,
                                {
                                    nav.navigate(
                                        GameRoutes.Training(session.id)
                                    ) { launchSingleTop = true }
                                },
                                { nav.navigate(GameRoutes.Settings) { launchSingleTop = true } },
                                { nav.navigate(GameRoutes.Account) { launchSingleTop = true } },
                                { nav.popBackStack() }
                            )
                        }
                    }
                    composable<GameRoutes.Training> { entry ->
                        val route = entry.toRoute<GameRoutes.Training>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            TrainingScreen(session, game) { nav.popBackStack() }
                        }
                    }
                    composable<GameRoutes.Fridge> { entry ->
                        val route = entry.toRoute<GameRoutes.Fridge>()
                        ProgressRoute(game, route.sessionId, { nav.popBackStack() }) { session ->
                            FridgeScreen(
                                session,
                                game,
                                {
                                    nav.navigate(
                                        GameRoutes.StoreSelection(session.id)
                                    ) { launchSingleTop = true }
                                },
                                { nav.popBackStack() }
                            )
                        }
                    }
                    composable<GameRoutes.Results> { entry ->
                        val route = entry.toRoute<GameRoutes.Results>()
                        ProgressRoute(
                            game,
                            route.sessionId,
                            { nav.popBackStack<GameRoutes.Start>(false) }
                        ) { session ->
                            ResultsScreen(
                                session,
                                finances,
                                { nav.navigate(GameRoutes.NewGame) { launchSingleTop = true } },
                                { nav.popBackStack<GameRoutes.Start>(false) }
                            )
                        }
                    }
                    composable<GameRoutes.Settings> {
                        SettingsScreen(
                            startup,
                            { nav.navigate(GameRoutes.Account) { launchSingleTop = true } },
                            { nav.popBackStack() }
                        )
                    }
                    composable<GameRoutes.Account> {
                        AccountScreen(account) { nav.popBackStack() }
                    }
                }
                val gameDestination = destination?.hasRoute<GameRoutes.Apartment>() == true ||
                    destination?.hasRoute<GameRoutes.Work>() == true ||
                    destination?.hasRoute<GameRoutes.Shop>() == true ||
                    destination?.hasRoute<GameRoutes.City>() == true ||
                    destination?.hasRoute<GameRoutes.StoreSelection>() == true ||
                    destination?.hasRoute<GameRoutes.Dealership>() == true ||
                    destination?.hasRoute<GameRoutes.Garage>() == true ||
                    destination?.hasRoute<GameRoutes.Statistics>() == true ||
                    destination?.hasRoute<GameRoutes.Hero>() == true ||
                    destination?.hasRoute<GameRoutes.Training>() == true ||
                    destination?.hasRoute<GameRoutes.Fridge>() == true
                if (gameDestination && currentSession != null) {
                    val sessionId = currentSession.id
                    val selected = when {
                        destination?.hasRoute<GameRoutes.Work>() == true ->
                            R.string.game_work_section
                        destination?.hasRoute<GameRoutes.Shop>() == true ||
                            destination?.hasRoute<GameRoutes.StoreSelection>() == true ||
                            destination?.hasRoute<GameRoutes.Dealership>() == true ->
                            R.string.game_shop_section
                        destination?.hasRoute<GameRoutes.Statistics>() == true ->
                            R.string.game_finance_section
                        destination?.hasRoute<GameRoutes.Hero>() == true ||
                            destination?.hasRoute<GameRoutes.Training>() == true ->
                            R.string.game_character_section
                        else -> R.string.game_home_section
                    }
                    Box(Modifier.navigationBarsPadding()) {
                        HomeSections(
                            {
                                nav.navigate(GameRoutes.Work(sessionId)) {
                                    popUpTo<GameRoutes.Apartment>()
                                    launchSingleTop = true
                                }
                            },
                            {
                                nav.navigate(GameRoutes.StoreSelection(sessionId)) {
                                    popUpTo<GameRoutes.Apartment>()
                                    launchSingleTop = true
                                }
                            },
                            { nav.popBackStack<GameRoutes.Apartment>(false) },
                            {
                                nav.navigate(GameRoutes.Statistics(sessionId)) {
                                    popUpTo<GameRoutes.Apartment>()
                                    launchSingleTop = true
                                }
                            },
                            {
                                nav.navigate(GameRoutes.Hero(sessionId)) {
                                    popUpTo<GameRoutes.Apartment>()
                                    launchSingleTop = true
                                }
                            },
                            selected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRoute(
    game: MainViewModel,
    sessionId: String,
    onBack: () -> Unit,
    content: @Composable (GameSession) -> Unit
) {
    val state by game.state.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { game.restoreHome(sessionId) }
    when (val current = state.forSession(sessionId)) {
        is MainUiState.Content -> content(current.session)
        else -> GamePage {
            if (current is MainUiState.Error) {
                GameErrorPanel(
                    current.reason,
                    game::retry
                )
            } else if (current == MainUiState.Empty) {
                LpBody(stringResource(R.string.game_no_save))
            } else {
                GameLoading()
            }
            GameBackButton(onBack)
        }
    }
}

@Composable
private fun GameLifecycle(viewModel: MainViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setHomeVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setHomeVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setHomeVisible(false)
        }
    }
}

internal fun MainUiState.forSession(sessionId: String): MainUiState = when (this) {
    is MainUiState.Content -> if (session.id == sessionId) this else MainUiState.Empty
    is MainUiState.Error -> if (session == null || session.id == sessionId) {
        this
    } else {
        copy(
            session = null
        )
    }
    else -> this
}
