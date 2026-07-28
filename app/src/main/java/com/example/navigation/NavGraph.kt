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
import com.example.ui.screens.ShopScreen

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
            val savedRun by viewModel.savedGame.collectAsStateWithLifecycle()

            // Menu soundtrack starts here and keeps playing through the sub-screens;
            // entering a level switches to the game track automatically.
            LaunchedEffect(Unit) { viewModel.playMenuMusic() }

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
                onScores = { navController.navigate(Screen.Scores.route) },
                onShop = { navController.navigate(Screen.Shop.route) },
                resumeLevel = savedRun?.level,
                onResume = {
                    val level = savedRun?.level ?: return@MainMenuScreen
                    viewModel.resumeSavedGame()
                    navController.navigate(Screen.Game.createRoute(level))
                }
            )
        }

        // 1e. SKINS SHOP
        composable(route = Screen.Shop.route) {
            val stars by viewModel.availableStars.collectAsStateWithLifecycle()
            val owned by viewModel.ownedSkins.collectAsStateWithLifecycle()
            val equipped by viewModel.selectedSkin.collectAsStateWithLifecycle()
            val spareLives by viewModel.extraLives.collectAsStateWithLifecycle()
            ShopScreen(
                availableStars = stars,
                ownedSkins = owned,
                selectedSkin = equipped,
                onBuy = { viewModel.buySkin(it) },
                onEquip = { viewModel.equipSkin(it) },
                onBack = { navController.popBackStack() },
                extraLives = spareLives,
                extraLifeCost = GameViewModel.EXTRA_LIFE_COST,
                maxExtraLives = GameViewModel.MAX_EXTRA_LIVES,
                onBuyExtraLife = { viewModel.buyExtraLife() }
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

            // Starts the level, then reacts to its terminal status - both in ONE
            // effect, in this order, so the status-watch can never act on a status
            // left over from whatever the player was doing before this screen
            // existed. viewModel.uiState is a single flow shared across the whole
            // app: retrying a failed level, or advancing to the next one, re-enters
            // this SAME composable type with the PREVIOUS run's terminal status
            // (LEVEL_COMPLETE / GAME_OVER) still sitting in it. startLevel() runs to
            // completion (it does not suspend) before the flow below is even
            // subscribed to, so the first value this collector observes is already
            // this level's fresh RUNNING state - never the stale one. A second,
            // separate LaunchedEffect keyed on the Compose State snapshot of that
            // same flow does not have this guarantee: recomposition can hand it the
            // stale status before startLevel()'s update propagates, firing an
            // immediate, spurious navigation back to the screen the player just
            // left - which is what made RETRY / NEXT LEVEL / re-entering a level
            // after a purchase need a second tap to actually stick.
            LaunchedEffect(levelNumber) {
                viewModel.startLevel(levelNumber)
                var handledTerminal = false
                viewModel.uiState.collect { state ->
                    if (state.levelNumber != levelNumber) return@collect
                    if (handledTerminal) return@collect
                    when (state.status) {
                        GameStateStatus.LEVEL_COMPLETE -> {
                            handledTerminal = true
                            navController.navigate(
                                Screen.LevelComplete.createRoute(
                                    levelNumber = state.levelNumber,
                                    score = state.score,
                                    timeRemaining = state.timeRemainingSeconds.toInt(),
                                    stars = state.stars,
                                    earned = state.starsEarned
                                )
                            ) {
                                popUpTo(Screen.MainMenu.route) // Clean game from backstack
                            }
                        }
                        GameStateStatus.GAME_OVER -> {
                            handledTerminal = true
                            navController.navigate(
                                Screen.GameOver.createRoute(
                                    levelNumber = state.levelNumber,
                                    score = state.score,
                                    captured = state.capturedPercentage.toInt(),
                                    target = state.targetPercentage.toInt()
                                )
                            ) {
                                popUpTo(Screen.MainMenu.route) // Clean game from backstack
                            }
                        }
                        else -> {}
                    }
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
                    // Tagged with this screen's own level so a layout pass that
                    // lands before the level switch cannot restart the old level.
                    viewModel.onFieldSized(aspect, levelNumber)
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
                navArgument("stars") { type = NavType.IntType },
                navArgument("earned") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt("levelNumber") ?: 1
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val timeRemaining = backStackEntry.arguments?.getInt("timeRemaining") ?: 0
            val stars = backStackEntry.arguments?.getInt("stars") ?: 0
            val earnedStars = backStackEntry.arguments?.getInt("earned") ?: 0

            val bestScores by viewModel.highScores.collectAsStateWithLifecycle()

            LevelCompleteScreen(
                levelNumber = levelNumber,
                score = score,
                timeRemaining = timeRemaining,
                stars = stars,
                starsEarned = earnedStars,
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
                navArgument("score") { type = NavType.IntType },
                navArgument("captured") { type = NavType.IntType },
                navArgument("target") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt("levelNumber") ?: 1
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val capturedPct = backStackEntry.arguments?.getInt("captured") ?: 0
            val targetPct = backStackEntry.arguments?.getInt("target") ?: 0

            val bestScores by viewModel.highScores.collectAsStateWithLifecycle()

            GameOverScreen(
                levelNumber = levelNumber,
                score = score,
                bestScore = bestScores[levelNumber] ?: 0,
                capturedPercent = capturedPct,
                targetPercent = targetPct,
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
