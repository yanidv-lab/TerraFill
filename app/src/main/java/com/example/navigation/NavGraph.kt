package com.example.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.engine.GameStateStatus
import com.example.ui.GameViewModel
import com.example.ui.screens.GameOverScreen
import com.example.ui.screens.GameScreen
import com.example.ui.screens.LevelCompleteScreen
import com.example.ui.screens.MainMenuScreen

/**
 * Orchestrates the screens and type-safe arguments inside the Jetpack Compose navigation structure.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val highestUnlockedLevel by viewModel.highestUnlockedLevel.collectAsStateWithLifecycle()
    val highScores by viewModel.highScores.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Screen.MainMenu.route,
        modifier = modifier
    ) {
        // 1. MAIN MENU SCREEN
        composable(route = Screen.MainMenu.route) {
            MainMenuScreen(
                highestUnlockedLevel = highestUnlockedLevel,
                highScores = highScores,
                onStartGame = { level ->
                    navController.navigate(Screen.Game.createRoute(level))
                },
                onResetProgress = {
                    viewModel.resetAllProgress()
                }
            )
        }

        // 2. ACTIVE GAME SCREEN
        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("levelNumber") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt("levelNumber") ?: 1
            val gameState by viewModel.uiState.collectAsStateWithLifecycle()

            // Initialize level once on navigate
            LaunchedEffect(levelNumber) {
                viewModel.startLevel(levelNumber)
            }

            // Route to end-game screens when simulation state changes
            LaunchedEffect(gameState.status) {
                when (gameState.status) {
                    GameStateStatus.LEVEL_COMPLETE -> {
                        navController.navigate(
                            Screen.LevelComplete.createRoute(
                                levelNumber = gameState.levelNumber,
                                score = gameState.score,
                                timeRemaining = gameState.timeRemainingSeconds.toInt()
                            )
                        ) {
                            popUpTo(Screen.MainMenu.route) // Clean game from backstack
                        }
                    }
                    GameStateStatus.GAME_OVER -> {
                        navController.navigate(
                            Screen.GameOver.createRoute(
                                levelNumber = gameState.levelNumber,
                                score = gameState.score
                            )
                        ) {
                            popUpTo(Screen.MainMenu.route) // Clean game from backstack
                        }
                    }
                    else -> {}
                }
            }

            GameScreen(
                state = gameState,
                onDirectionChanged = { direction ->
                    viewModel.setDirection(direction)
                },
                onTick = { dt ->
                    viewModel.tick(dt)
                },
                onPauseToggle = {
                    viewModel.togglePause()
                },
                onQuitGame = {
                    navController.navigate(Screen.MainMenu.route) {
                        popUpTo(Screen.MainMenu.route) { inclusive = true }
                    }
                },
                onToggleSound = {
                    viewModel.toggleSound()
                }
            )
        }

        // 3. LEVEL COMPLETE VICTORY SCREEN
        composable(
            route = Screen.LevelComplete.route,
            arguments = listOf(
                navArgument("levelNumber") { type = NavType.IntType },
                navArgument("score") { type = NavType.IntType },
                navArgument("timeRemaining") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt("levelNumber") ?: 1
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val timeRemaining = backStackEntry.arguments?.getInt("timeRemaining") ?: 0

            LevelCompleteScreen(
                levelNumber = levelNumber,
                score = score,
                timeRemaining = timeRemaining,
                onNextLevel = {
                    navController.navigate(Screen.Game.createRoute(levelNumber + 1)) {
                        popUpTo(Screen.MainMenu.route)
                    }
                },
                onMainMenu = {
                    navController.navigate(Screen.MainMenu.route) {
                        popUpTo(Screen.MainMenu.route) { inclusive = true }
                    }
                }
            )
        }

        // 4. GAME OVER SCREEN
        composable(
            route = Screen.GameOver.route,
            arguments = listOf(
                navArgument("levelNumber") { type = NavType.IntType },
                navArgument("score") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt("levelNumber") ?: 1
            val score = backStackEntry.arguments?.getInt("score") ?: 0

            GameOverScreen(
                levelNumber = levelNumber,
                score = score,
                onRetry = {
                    navController.navigate(Screen.Game.createRoute(levelNumber)) {
                        popUpTo(Screen.MainMenu.route)
                    }
                },
                onMainMenu = {
                    navController.navigate(Screen.MainMenu.route) {
                        popUpTo(Screen.MainMenu.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
