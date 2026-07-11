package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import com.example.engine.Direction
import com.example.engine.GameStateStatus
import com.example.engine.GridCellState
import com.example.ui.GameUiState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GameScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRealTimeHUDOverlayIsDisplayedWithCorrectValues() {
        val testState = GameUiState(
            levelNumber = 4,
            gridWidth = 10,
            gridHeight = 10,
            grid = Array(10) { Array(10) { GridCellState.EMPTY } },
            lives = 3,
            capturedPercentage = 42.5,
            status = GameStateStatus.PAUSED
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                GameScreen(
                    state = testState,
                    onDirectionChanged = {},
                    onTick = {},
                    onPauseToggle = {},
                    onQuitGame = {}
                )
            }
        }

        // Print the semantic tree to help debug
        composeTestRule.onRoot().printToLog("GameScreenTestTree")

        // Verify that our HUD overlay is present and visible on screen
        composeTestRule.onNodeWithTag("real_time_hud_overlay").assertIsDisplayed()

        // Verify that current level is correctly printed inside the HUD overlay specifically
        composeTestRule.onNode(
            hasTestTag("real_time_hud_overlay") and hasAnyDescendant(hasText("L4"))
        ).assertIsDisplayed()

        // Verify that the exact percentage is displayed inside the HUD overlay specifically
        composeTestRule.onNode(
            hasTestTag("real_time_hud_overlay") and hasAnyDescendant(hasText("42.5%"))
        ).assertIsDisplayed()

        // Verify remaining lives indicator is displayed inside the HUD overlay specifically
        composeTestRule.onNode(
            hasTestTag("real_time_hud_overlay") and hasAnyDescendant(hasText("x3"))
        ).assertIsDisplayed()
    }
}
