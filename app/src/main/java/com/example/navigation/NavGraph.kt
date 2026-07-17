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
import com.example.ui.screens.LevelSelectScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.OptionsScreen
import com.example.ui.screens.ScoresScreen

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
    val levelStars by viewModel.levelStars.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Screen.MainMenu.route,
        modifier = modifier
    ) {
        // 1. MAIN MENU SCREEN
        composable(route = Screen.MainMenu.route) {
            val lastPlayed by viewModel.lastPlayedLevel.collectAsStateWithLifecycle()

            MainMenuScreen(
                highestUnlockedLevel = highestUnlockedLevel,
                highScores = highScores,
                levelStars = levelStars,
                lastPlayedLevel = lastPlayed,
                onStartGame = { level ->
                    navController.navigate(Screen.Game.createRoute(level))
                },
                onResetProgress = {
                    viewModel.resetAllProgress()
                },
                onPlay = { navController.navigate(Screen.LevelSelect.route) },
                onOptions = { navController.navigate(Screen.Options.route) },
                onScores = { navController.navigate(Screen.Scores.route) }
            )
        }

        // 1b. PLAY / LEVEL SELECT SCREEN
        composable(route = Screen.LevelSelect.route) {
            val lastPlayed by viewModel.lastPlayedLevel.collectAsStateWithLifecycle()
            LevelSelectScreen(
                highestUnlockedLevel = highestUnlockedLevel,
                levelStars = levelStars,
                lastPlayedLevel = lastPlayed,
                onStartGame = { level ->
                    navController.navigate(Screen.Game.createRoute(level))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 1c. OPTIONS SCREEN
        composable(route = Screen.Options.route) {
            val ui by viewModel.uiState.collectAsStateWithLifecycle()
            OptionsScreen(
                soundEnabled = ui.soundEnabled,
                onToggleSound = { viewModel.toggleSound() },
                onResetProgress = { viewModel.resetAllProgress() },
                onBack = { navController.popBackStack() }
            )
        }

        // 1d. SCORE SCREEN
        composable(route = Screen.Scores.route) {
            ScoresScreen(
                highScores = highScores,
                levelStars = levelStars,
                onBack = { navController.popBackStack() }
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
                                timeRemaining = gameState.timeRemainingSeconds.toInt(),
                                stars = gameState.stars
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
                },
                onFieldSized = { aspect ->
                    viewModel.onFieldSized(aspect)
                }
            )
        }

        // 3. LEVEL COMPLETE VICTORY SCREEN
        composable(
            route = Screen.LevelComplete.route,
            arguments = listOf(
                navArgument("levelNumber") { type = NavType.IntType },
                navArgument("score") { type = NavType.IntType },
                navArgument("timeRemaining") { type = NavType.IntType },
                navArgument("stars") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt("levelNumber") ?: 1
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val timeRemaining = backStackEntry.arguments?.getInt("timeRemaining") ?: 0
            val stars = backStackEntry.arguments?.getInt("stars") ?: 0

            val bestScores by viewModel.highScores.collectAsStateWithLifecycle()

            LevelCompleteScreen(
                levelNumber = levelNumber,
                score = score,
                timeRemaining = timeRemaining,
                stars = stars,
                // The stored best already includes this run, so matching it means
                // this run set (or tied) the record.
                isNewRecord = score > 0 && score >= (bestScores[levelNumber] ?: 0),
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

            val bestScores by viewModel.highScores.collectAsStateWithLifecycle()

            GameOverScreen(
                levelNumber = levelNumber,
                score = score,
                bestScore = bestScores[levelNumber] ?: 0,
                // Losing must never erase the player's records or unlocks - it just
                // offers an instant retry of the same level. Frustration-free.
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
