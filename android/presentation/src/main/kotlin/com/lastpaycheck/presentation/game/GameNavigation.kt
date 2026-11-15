package com.lastpaycheck.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.presentation.startup.StartupViewModel
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import kotlinx.serialization.Serializable

internal object GameRoutes {
    @Serializable
    data object Start

    @Serializable
    data object NewGame

    @Serializable
    data class Apartment(val sessionId: String)

    @Serializable
    data object Shop

    @Serializable
    data object Work

    @Serializable
    data object Training

    @Serializable
    data object Statistics

    @Serializable
    data object Settings
}

@Composable
fun GameNavigation(
    game: MainViewModel,
    startup: StartupViewModel,
    onThemeChanged: (Boolean) -> Unit
) {
    val nav = rememberNavController()
    val state by game.state.collectAsStateWithLifecycle()
    val preview by game.preview.collectAsStateWithLifecycle()
    val settings by startup.settings.collectAsStateWithLifecycle()
    val theme = (settings as? DomainResult.Success)?.value?.theme
    val dark = when (theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect { onThemeChanged(dark) }
    val openHome: (GameSession) -> Unit = {
        nav.navigate(GameRoutes.Apartment(it.id)) {
            popUpTo<GameRoutes.Start>()
            launchSingleTop = true
        }
    }

    LastPaycheckTheme(darkTheme = dark) {
        NavHost(navController = nav, startDestination = GameRoutes.Start) {
            composable<GameRoutes.Start> {
                GameStartScreen(
                    state = state,
                    dark = dark,
                    onContinue = { game.resumeGame(CheckpointChoice.LATEST, openHome) },
                    onRestore = { game.resumeGame(CheckpointChoice.PREVIOUS, openHome) },
                    onNew = { nav.navigate(GameRoutes.NewGame) { launchSingleTop = true } },
                    onRetry = game::retry,
                    onTheme = { startup.setTheme(!dark) }
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
                HomeLifecycle(game)
                GameHomeScreen(
                    state = state,
                    onAction = game::start,
                    onPause = game::togglePause,
                    onSave = game::save,
                    onRetry = game::retry,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun HomeLifecycle(viewModel: MainViewModel) {
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
