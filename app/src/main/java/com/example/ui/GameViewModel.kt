package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GamePreferences
import com.example.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State container representing the real-time game simulation parameters for Jetpack Compose.
 *
 * Note: equality is intentionally the data-class default. `grid` (an Array) and `enemies`
 * (identity-based Enemy instances) are freshly copied on every engine tick, so consecutive
 * states never compare equal and StateFlow emits every frame — enemies must keep animating
 * even while all scalar fields (player position, score, timer) are unchanged.
 */
data class GameUiState(
    val levelNumber: Int = 1,
    val grid: Array<Array<GridCellState>> = Array(0) { emptyArray() },
    val gridWidth: Int = 40,
    val gridHeight: Int = 50,
    val playerX: Int = 20,
    val playerY: Int = 0,
    val playerDirection: Direction = Direction.NONE,
    val isDrawing: Boolean = false,
    val trail: List<Pair<Int, Int>> = emptyList(),
    /** Recent head cells, most recent first; drives the caterpillar body rendering. */
    val pathHistory: List<Pair<Int, Int>> = emptyList(),
    /** 0..1 interpolation of the head's glide between its previous and current cell. */
    val moveProgress: Float = 1f,
    /** Monotonic capture event counter; a change triggers the capture flash animation. */
    val captureCount: Int = 0,
    val lastCapturedCells: List<Pair<Int, Int>> = emptyList(),
    /** Monotonic crash event counter; a change triggers the shake/vignette animation. */
    val crashCount: Int = 0,
    val enemies: List<Enemy> = emptyList(),
    val lives: Int = 3,
    val score: Int = 0,
    val timeRemainingSeconds: Double = 180.0,
    val capturedPercentage: Double = 0.0,
    val status: GameStateStatus = GameStateStatus.RUNNING,
    val targetPercentage: Double = 75.0,
    val highestUnlockedLevel: Int = 1,
    val soundEnabled: Boolean = true,
    // Combo, power-ups and stars
    val scoreMultiplier: Int = 1,
    val comboTimeRemaining: Double = 0.0,
    val powerUps: List<PowerUp> = emptyList(),
    val shieldActive: Boolean = false,
    val freezeRemaining: Double = 0.0,
    val slowRemaining: Double = 0.0,
    val powerUpCollectedCount: Int = 0,
    val stars: Int = 0
)

/**
 * ViewModel coordinating UI flow and active GameEngine simulation.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = GamePreferences(application)
    private val sound = com.example.audio.SoundManager(application)

    // Mutable state for the currently active game
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Event edges used to fire one-shot sounds
    private var lastCaptureCount = 0
    private var lastCrashCount = 0
    private var lastPowerUpCount = 0
    private var lastStatus = GameStateStatus.RUNNING

    // Grid snapshot cache: only rebuilt when the engine's grid actually changes,
    // so most frames (which only move enemies) allocate nothing for the grid.
    private var cachedGrid: Array<Array<GridCellState>> = Array(0) { emptyArray() }
    private var cachedGridVersion = -1

    // Observe progress
    val highestUnlockedLevel: StateFlow<Int> = preferences.highestUnlockedLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // Level the player most recently played - powers the main menu's CONTINUE button
    val lastPlayedLevel: StateFlow<Int> = preferences.lastPlayedLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // Observe level high scores
    private val _highScores = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val highScores: StateFlow<Map<Int, Int>> = _highScores.asStateFlow()

    // Observe per-level star ratings
    private val _levelStars = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val levelStars: StateFlow<Map<Int, Int>> = _levelStars.asStateFlow()

    private var engine: GameEngine? = null

    // Width/height ratio of the on-screen play area, reported by the UI once the
    // playfield is laid out. The grid is generated to match it so the field fills
    // the screen with square cells. 0.62 is a typical portrait-phone starting guess.
    private var fieldAspect: Double = 0.62

    init {
        // Observe unlocked level to update UI state accordingly
        viewModelScope.launch {
            highestUnlockedLevel.collect { level ->
                _uiState.value = _uiState.value.copy(highestUnlockedLevel = level)
            }
        }

        // Collect high scores and stars for all levels
        for (lvl in 1..LevelConfig.TOTAL_LEVELS) {
            viewModelScope.launch {
                preferences.getBestScore(lvl).collect { score ->
                    _highScores.value = _highScores.value.toMutableMap().apply { put(lvl, score) }
                }
            }
            viewModelScope.launch {
                preferences.getBestStars(lvl).collect { stars ->
                    _levelStars.value = _levelStars.value.toMutableMap().apply { put(lvl, stars) }
                }
            }
        }
    }

    /**
     * Initializes and starts a new game session for the specified level.
     */
    fun startLevel(levelNumber: Int) {
        val config = LevelConfig.getConfig(levelNumber, fieldAspect)
        val newEngine = GameEngine(config)
        engine = newEngine
        lastCaptureCount = 0
        lastCrashCount = 0
        lastPowerUpCount = 0
        lastStatus = GameStateStatus.RUNNING
        cachedGridVersion = -1
        sound.startMusic()
        viewModelScope.launch { preferences.saveLastPlayedLevel(config.levelNumber) }
        updateUiStateFromEngine(newEngine)
    }

    /**
     * Called by the UI once the playfield box is laid out, with its width/height ratio.
     * If the running engine's grid was generated for a noticeably different shape and
     * the level has effectively not begun yet (no score, no trail, first seconds),
     * regenerate it so the grid exactly fits the screen. Later levels reuse the stored
     * aspect directly, so this restart only ever happens right after app start.
     */
    fun onFieldSized(aspectWidthOverHeight: Float) {
        if (!aspectWidthOverHeight.isFinite() || aspectWidthOverHeight <= 0f) return
        val newAspect = aspectWidthOverHeight.toDouble()
        val changed = kotlin.math.abs(newAspect - fieldAspect) > 0.02
        fieldAspect = newAspect
        val active = engine ?: return
        val justStarted = active.score == 0 &&
            !active.isDrawing &&
            active.timeRemainingSeconds > active.levelConfig.timeLimitSeconds - 3.0
        if (changed && justStarted && active.status == GameStateStatus.RUNNING) {
            startLevel(active.levelConfig.levelNumber)
        }
    }

    /**
     * Propagates a simulation frame tick.
     */
    fun tick(dt: Double) {
        val activeEngine = engine ?: return
        
        val oldPlayerX = activeEngine.playerX
        val oldPlayerY = activeEngine.playerY

        // Tick game simulation
        activeEngine.tick(dt)

        // Play subtle retro blip when the player moves to a new grid cell
        if (activeEngine.playerX != oldPlayerX || activeEngine.playerY != oldPlayerY) {
            if (activeEngine.isDrawing) {
                sound.drawTrail()
            } else {
                sound.move()
            }
        }

        // Fire one-shot sounds on event edges
        if (activeEngine.captureCount > lastCaptureCount) {
            lastCaptureCount = activeEngine.captureCount
            sound.capture()
        }
        if (activeEngine.crashCount > lastCrashCount) {
            lastCrashCount = activeEngine.crashCount
            sound.crash()
        }
        if (activeEngine.powerUpCollectedCount > lastPowerUpCount) {
            lastPowerUpCount = activeEngine.powerUpCollectedCount
            sound.powerUp()
        }
        if (activeEngine.status != lastStatus) {
            when (activeEngine.status) {
                GameStateStatus.LEVEL_COMPLETE -> { sound.levelComplete(); sound.pauseMusic() }
                GameStateStatus.GAME_OVER -> { sound.gameOver(); sound.stopMusic() }
                else -> {}
            }
            lastStatus = activeEngine.status
        }

        // Handle short flash/reset sequence if player is in reset mode
        if (activeEngine.status == GameStateStatus.CRASH_RESET) {
            viewModelScope.launch {
                // Briefly pause for impact animation and then resume
                kotlinx.coroutines.delay(1000)
                activeEngine.clearReset()
                updateUiStateFromEngine(activeEngine)
            }
        }

        // Handle auto-saving on level completion
        if (activeEngine.status == GameStateStatus.LEVEL_COMPLETE && _uiState.value.status == GameStateStatus.RUNNING) {
            viewModelScope.launch {
                preferences.saveLevelCompletion(
                    level = activeEngine.levelConfig.levelNumber,
                    percentage = activeEngine.capturedPercentage,
                    timeRemaining = activeEngine.timeRemainingSeconds.toInt(),
                    score = activeEngine.score,
                    stars = activeEngine.stars
                )
            }
        }

        updateUiStateFromEngine(activeEngine)
    }

    /**
     * Changes movement direction of the cursor.
     */
    fun setDirection(direction: Direction) {
        val oldDir = engine?.playerDirection ?: Direction.NONE
        engine?.setDirection(direction)
        val newDir = engine?.playerDirection ?: Direction.NONE
        if (oldDir != newDir && newDir != Direction.NONE) {
            sound.tap()
        }
        engine?.let { updateUiStateFromEngine(it) }
    }

    /**
     * Toggles the active pause state of the game loop.
     */
    fun togglePause() {
        engine?.togglePause()
        engine?.let {
            if (it.status == GameStateStatus.PAUSED) sound.pauseMusic() else sound.resumeMusic()
            updateUiStateFromEngine(it)
        }
    }

    /** Starts the menu soundtrack (called when a menu screen is shown). */
    fun playMenuMusic() = sound.startMenuMusic()

    /** Pauses background music (e.g. when the app goes to the background). */
    fun pauseAudio() = sound.pauseMusic()

    /** Resumes background music if the game is running, or menu music on menus. */
    fun resumeAudio() {
        if (engine?.status == GameStateStatus.RUNNING) {
            sound.resumeMusic()
        } else {
            sound.resumeMenuMusic()
        }
    }

    /** Mutes/unmutes all game audio and reflects the new state in the UI. */
    fun toggleSound() {
        val enabled = sound.toggleAll()
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }

    /**
     * Erases all persisted progress, resetting unlocked levels back to level 1.
     */
    fun resetAllProgress() {
        viewModelScope.launch {
            preferences.resetProgress()
            _uiState.value = _uiState.value.copy(highestUnlockedLevel = 1)
        }
    }

    /**
     * Copies parameters of the simulation engine to state flow.
     */
    private fun updateUiStateFromEngine(activeEngine: GameEngine) {
        // Rebuild the grid snapshot only when the engine reports it changed; otherwise
        // reuse the cached array so idle/enemy-only frames allocate nothing for the grid.
        if (activeEngine.gridVersion != cachedGridVersion) {
            cachedGrid = Array(activeEngine.width) { x ->
                Array(activeEngine.height) { y ->
                    activeEngine.grid[x][y]
                }
            }
            cachedGridVersion = activeEngine.gridVersion
        }

        // Map enemies list (creates a new list so recomposition detects changes)
        val enemiesCopy = activeEngine.enemies.map { it.copyWith() }

        _uiState.value = _uiState.value.copy(
            levelNumber = activeEngine.levelConfig.levelNumber,
            grid = cachedGrid,
            gridWidth = activeEngine.width,
            gridHeight = activeEngine.height,
            playerX = activeEngine.playerX,
            playerY = activeEngine.playerY,
            playerDirection = activeEngine.playerDirection,
            isDrawing = activeEngine.isDrawing,
            trail = activeEngine.trail.toList(),
            pathHistory = activeEngine.pathHistory.toList(),
            moveProgress = activeEngine.moveProgress.toFloat(),
            captureCount = activeEngine.captureCount,
            lastCapturedCells = activeEngine.lastCapturedCells,
            crashCount = activeEngine.crashCount,
            enemies = enemiesCopy,
            lives = activeEngine.lives,
            score = activeEngine.score,
            timeRemainingSeconds = activeEngine.timeRemainingSeconds,
            capturedPercentage = activeEngine.capturedPercentage,
            status = activeEngine.status,
            targetPercentage = activeEngine.levelConfig.targetPercentage,
            scoreMultiplier = activeEngine.scoreMultiplier,
            comboTimeRemaining = activeEngine.comboTimeRemaining,
            powerUps = activeEngine.powerUps.toList(),
            shieldActive = activeEngine.shieldActive,
            freezeRemaining = activeEngine.freezeRemaining,
            slowRemaining = activeEngine.slowRemaining,
            powerUpCollectedCount = activeEngine.powerUpCollectedCount,
            stars = activeEngine.stars
        )
    }
}
