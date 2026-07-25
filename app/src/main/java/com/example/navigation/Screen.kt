package com.example.navigation

/**
 * Defines the navigation screens and helper routes for TerraFill.
 */
sealed class Screen(val route: String) {
    object MainMenu : Screen("main_menu")

    object LevelSelect : Screen("level_select")

    object Options : Screen("options")

    object Scores : Screen("scores")

    object Shop : Screen("shop")


    object Game : Screen("game/{levelNumber}") {
        fun createRoute(levelNumber: Int) = "game/$levelNumber"
    }

    object LevelComplete : Screen("level_complete/{levelNumber}/{score}/{timeRemaining}/{stars}/{earned}") {
        fun createRoute(levelNumber: Int, score: Int, timeRemaining: Int, stars: Int, earned: Int) =
            "level_complete/$levelNumber/$score/$timeRemaining/$stars/$earned"
    }

    object GameOver : Screen("game_over/{levelNumber}/{score}/{captured}/{target}") {
        fun createRoute(levelNumber: Int, score: Int, captured: Int, target: Int) =
            "game_over/$levelNumber/$score/$captured/$target"
    }
}
