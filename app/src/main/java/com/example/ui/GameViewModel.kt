package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DailyMissionState
import com.example.data.GamePreferences
import com.example.data.SavedGame
import com.example.engine.*
import com.example.ui.skins.CaterpillarSkin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val gridWidth: Int = 28,
    val gridHeight: Int = 36,
    val playerX: Int = 14,
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
    val stars: Int = 0,
    /** Star currency paid out by this completion (0 until the level is finished). */
    val starsEarned: Int = 0,
    /** In-flight web projectiles fired by Spitter enemies. */
    val webShots: List<WebShot> = emptyList(),
    /** Permanent sticky web traps spun by Weavers. */
    val webTraps: List<Pair<Int, Int>> = emptyList(),
    /** Id of the caterpillar skin the player has equipped. */
    val skinId: String = "classic"
)

/**
 * ViewModel coordinating UI flow and active GameEngine simulation.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Lives every level starts with before shop-bought spares. */
        const val BASE_LIVES = 3
        /** Star price of one spare life. */
        const val EXTRA_LIFE_COST = 350
        /** Most spare lives that may be banked at once. */
        const val MAX_EXTRA_LIVES = 3
        /** Stars granted per completed rewarded-ad watch. */
        const val REWARDED_AD_STAR_REWARD = 150
        /** Daily cap on rewarded-ad watches, so it stays a bonus rather than the main loop. */
        const val MAX_REWARDED_AD_WATCHES_PER_DAY = 8
        /** Stars granted once per week for sharing the game. */
        const val SHARE_STAR_REWARD = 700
    }

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
    // Clock-warning edges, so each threshold sounds exactly once per level
    private var warnedAt30 = false
    private var warnedAt10 = false

    // Peak stats for the CURRENT level attempt, used only to grade the daily
    // mission at level-complete - reset on every startLevel(), updated on every
    // capture. Deliberately not part of GameUiState: nothing in the UI needs them
    // moment-to-moment, only the level-complete check does.
    private var maxSingleCaptureFraction = 0.0
    private var peakComboThisLevel = 1

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

    // --- Cosmetic skins bought with stars ---
    val ownedSkins: StateFlow<Set<String>> = preferences.ownedSkins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Resolved through byId so a skin retired from the catalogue (a player still has
    // its id persisted as their selection) falls back to the default everywhere this
    // is read, instead of only where CaterpillarSkin.byId happens to be called again.
    val selectedSkin: StateFlow<String> = preferences.selectedSkin
        .map { CaterpillarSkin.byId(it).id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CaterpillarSkin.DEFAULT.id)

    /** Every star banked from level completions, including replays. */
    val totalStarsEarned: StateFlow<Int> = preferences.totalStarsEarned
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Spare lives bought from the shop and waiting to be spent. They are added on
     * top of the standard three for ONE level - whichever the player starts next,
     * replays and earlier levels included - and are withdrawn the moment that level
     * begins, whether or not they get used.
     *
     * This is the shop's view of the bank. The withdrawal itself does NOT read it:
     * see [withdrawSpareLives], which goes to storage so it can never act on a stale
     * cached value. Collected eagerly so the shop's cap check is accurate as soon as
     * the screen opens.
     */
    val extraLives: StateFlow<Int> = preferences.extraLives
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val starsSpentOnConsumables: StateFlow<Int> = preferences.starsSpentOnConsumables
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Stars still available to spend: everything earned, minus the cost of owned
     * skins and of every consumable (extra life) bought so far.
     */
    val availableStars: StateFlow<Int> =
        combine(totalStarsEarned, ownedSkins, starsSpentOnConsumables) { earned, owned, consumed ->
            // costFor covers skins retired from the catalogue too, so a purchase made
            // before a trim keeps counting against the balance instead of quietly
            // refunding itself the moment its id drops out of CaterpillarSkin.ALL.
            val skinSpend = owned.sumOf { CaterpillarSkin.costFor(it) }
            (earned - skinSpend - consumed).coerceAtLeast(0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Rewarded-ad watches used up today, out of [MAX_REWARDED_AD_WATCHES_PER_DAY]. */
    val rewardedAdWatchesToday: StateFlow<Int> = preferences.rewardedAdWatchesToday
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Grants the reward for a rewarded ad the player watched to completion, and
     * records it against the daily cap. The caller (the ad SDK wrapper) is what
     * guarantees this only fires on genuine completion, never on an early close.
     */
    fun grantRewardedAdStars() {
        viewModelScope.launch {
            preferences.addStars(REWARDED_AD_STAR_REWARD)
            preferences.recordRewardedAdWatch()
        }
    }

    /** Whether the share reward has already been claimed in the current week. */
    val hasClaimedShareRewardThisWeek: StateFlow<Boolean> = preferences.hasClaimedShareRewardThisWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Grants the share reward, the first time in a given week the player actually
     * picks a target app from the share sheet. Sharing itself is never limited - the
     * caller can reopen the share sheet as often as it likes - but the payout resets
     * only once a week, enforced atomically in storage so a stray double-call can't
     * pay it out twice in the same window.
     */
    fun grantShareStarsIfEligible() {
        viewModelScope.launch {
            preferences.claimShareRewardIfEligible(SHARE_STAR_REWARD)
        }
    }

    /** Today's mission and the player's progress on it - null until [refreshDailyMission] rolls one. */
    val dailyMission: StateFlow<DailyMissionState?> = preferences.dailyMissionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Rolls today's mission if one hasn't been rolled yet (a no-op every other time
     * it's called that day). Reads the persisted highest-unlocked-level directly
     * rather than the cached [highestUnlockedLevel] StateFlow, so the very first
     * call of a session - before that StateFlow has necessarily loaded its real
     * value - can't lock in a mission scaled to the wrong level for the whole day.
     */
    fun refreshDailyMission() {
        viewModelScope.launch {
            preferences.ensureTodayMission(preferences.highestUnlockedLevel.first())
        }
    }

    /**
     * Checked once per level completion: did this run satisfy today's still-open
     * mission? Graded against the peak stats tracked over the whole attempt (see the
     * field comments near [maxSingleCaptureFraction]), not just the run's final
     * numbers, so e.g. a big single capture early on still counts even if the level
     * was finished off with several small ones.
     */
    private fun checkDailyMissionOnLevelComplete(activeEngine: GameEngine) {
        val today = dailyMission.value ?: return
        if (today.completed) return
        val satisfied = when (today.mission.type) {
            DailyMissionType.CAPTURE_BURST -> maxSingleCaptureFraction * 100.0 >= today.mission.target
            DailyMissionType.FLAWLESS_LEVEL -> activeEngine.crashCount == 0
            DailyMissionType.COMBO_STREAK -> peakComboThisLevel >= today.mission.target
        }
        if (satisfied) {
            viewModelScope.launch { preferences.markTodayMissionCompleted() }
        }
    }

    // One-shot signal for the UI to play the flying-stars claim animation: the
    // amount just claimed, consumed (set back to null) once the UI has shown it.
    private val _dailyMissionClaimReward = MutableStateFlow<Int?>(null)
    val dailyMissionClaimReward: StateFlow<Int?> = _dailyMissionClaimReward.asStateFlow()

    /** Claims today's mission reward, if it's completed and not already claimed. */
    fun claimDailyMissionReward() {
        viewModelScope.launch {
            val reward = preferences.claimDailyMission()
            if (reward != null) _dailyMissionClaimReward.value = reward
        }
    }

    /** Called by the UI once it has started the claim animation for the last reward. */
    fun consumeDailyMissionClaimEvent() {
        _dailyMissionClaimReward.value = null
    }

    /** Buys one spare life if the player can afford it and is under the cap. */
    fun buyExtraLife() {
        if (extraLives.value >= MAX_EXTRA_LIVES) return
        if (availableStars.value < EXTRA_LIFE_COST) return
        viewModelScope.launch {
            preferences.purchaseExtraLife(cost = EXTRA_LIFE_COST, cap = MAX_EXTRA_LIVES)
        }
    }

    /** Buys a skin when the player can afford it, and equips it. */
    fun buySkin(skin: CaterpillarSkin) {
        if (skin.id in ownedSkins.value || skin.id == CaterpillarSkin.DEFAULT.id) {
            equipSkin(skin)
            return
        }
        if (availableStars.value < skin.cost) return
        viewModelScope.launch { preferences.purchaseSkin(skin.id) }
    }

    /** Equips an owned skin (the default skin is always owned). */
    fun equipSkin(skin: CaterpillarSkin) {
        if (skin.id != CaterpillarSkin.DEFAULT.id && skin.id !in ownedSkins.value) return
        viewModelScope.launch { preferences.selectSkin(skin.id) }
    }

    // --- Mid-level save so an interrupted run can be resumed ---
    val savedGame: StateFlow<SavedGame?> = preferences.savedGame
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun buildProgressSnapshot(): SavedGame? {
        val active = engine ?: return null
        if (active.status != GameStateStatus.RUNNING && active.status != GameStateStatus.PAUSED) return null
        return SavedGame(
            level = active.levelConfig.levelNumber,
            score = active.score,
            lives = active.lives,
            timeRemaining = active.timeRemainingSeconds,
            gridWidth = active.width,
            gridHeight = active.height,
            capturedMask = active.exportCapturedMask(),
            enemies = active.exportEnemies()
        )
    }

    /**
     * Persists the current run. Called after each capture, so a later interruption
     * never loses more than the last few seconds of play. Fire-and-forget is fine
     * here - there's always a next capture (or [saveProgressSnapshotBlocking]) to
     * catch up if this particular write loses a race with something.
     */
    fun saveProgressSnapshot() {
        val snapshot = buildProgressSnapshot() ?: return
        viewModelScope.launch { preferences.saveGame(snapshot) }
    }

    /**
     * Same save as [saveProgressSnapshot], but blocks the caller until the write
     * actually lands on disk. Exists for onPause(): that is the last callback
     * Android guarantees before it may kill the process with no further warning,
     * so a fire-and-forget coroutine launched there is racing process death and
     * can lose - the app would then quietly have no save at all, despite genuinely
     * calling saveProgressSnapshot() every time the player was interrupted. A
     * DataStore Preferences write is small enough that blocking briefly here is a
     * fair trade against silently discarding the run.
     */
    fun saveProgressSnapshotBlocking() {
        val snapshot = buildProgressSnapshot() ?: return
        kotlinx.coroutines.runBlocking { preferences.saveGame(snapshot) }
    }

    /**
     * Arms the saved run. Navigation to the game screen calls [startLevel], which
     * applies the snapshot - going through a pending flag (rather than restoring
     * here) means the fresh level start cannot overwrite the restored board.
     */
    fun resumeSavedGame() {
        pendingResume = savedGame.value
    }

    private fun clearSavedGame() {
        viewModelScope.launch { preferences.clearSavedGame() }
    }

    private var engine: GameEngine? = null

    // Width/height ratio of the on-screen play area, reported by the UI once the
    // playfield is laid out. The grid is generated to match it so the field fills
    // the screen with square cells. 0.62 is a typical portrait-phone starting guess.
    private var fieldAspect: Double = 0.62

    // A saved run waiting to be applied by the next startLevel() call
    private var pendingResume: SavedGame? = null
    // True when the current engine was restored from a save, so the aspect-driven
    // grid regeneration must not throw that restored board away.
    private var restoredCurrentLevel = false
    // Spare lives already withdrawn from the bank and granted to the running level,
    // so the aspect-driven restart can re-grant them without charging again.
    private var spareLivesInPlay = 0
    // The level the player last asked to play. Layout callbacks arriving for any
    // other level are stale and must not restart anything.
    private var requestedLevel: Int? = null

    init {
        // Observe unlocked level to update UI state accordingly
        viewModelScope.launch {
            highestUnlockedLevel.collect { level ->
                _uiState.value = _uiState.value.copy(highestUnlockedLevel = level)
            }
        }

        // Keep the equipped skin mirrored into the game state so the playfield can
        // tint the caterpillar without reaching into preferences mid-frame.
        viewModelScope.launch {
            selectedSkin.collect { id ->
                _uiState.value = _uiState.value.copy(skinId = id)
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
     *
     * [reuseSpareLives] is for the internal restart that reshapes the board to the
     * screen: it re-grants the spare lives already withdrawn for this level instead
     * of going back to the (by then empty) bank for a second helping.
     */
    fun startLevel(levelNumber: Int, reuseSpareLives: Boolean = false) {
        // A resume re-enters a level that already paid for its spare lives (they
        // are part of the saved life count), so it must not be charged again.
        val resume = pendingResume?.takeIf { it.level == levelNumber }
        pendingResume = null

        // A resume must land on the exact grid it was saved against, or the mask
        // restore below rejects it as a size mismatch. fieldAspect lives only in
        // memory and resets to its default whenever the ViewModel is recreated
        // (e.g. the process was killed in the background - the very scenario the
        // save exists for), so rebuild the board from the saved dimensions rather
        // than trusting whatever the current field happens to report.
        val config = if (resume != null && resume.gridHeight > 0) {
            LevelConfig.getConfig(levelNumber, resume.gridWidth.toDouble() / resume.gridHeight)
        } else {
            LevelConfig.getConfig(levelNumber, fieldAspect)
        }
        // Remember what the player actually asked for, so a late layout callback for
        // a level they have already left cannot restart the wrong one.
        requestedLevel = config.levelNumber

        val newEngine = GameEngine(config, initialLives = BASE_LIVES)
        engine = newEngine
        lastCaptureCount = 0
        lastCrashCount = 0
        lastPowerUpCount = 0
        lastStatus = GameStateStatus.RUNNING
        warnedAt30 = false
        warnedAt10 = false
        maxSingleCaptureFraction = 0.0
        peakComboThisLevel = 1
        cachedGridVersion = -1
        sound.startMusic()
        viewModelScope.launch { preferences.saveLastPlayedLevel(config.levelNumber) }

        // Apply a pending resume for this level, if one is armed. The board above is
        // already sized to match the save, so this should always succeed - but a
        // mask that still doesn't fit (an old save from a different width formula)
        // is rejected inside restoreSnapshot, in which case the save is left alone
        // rather than being wiped out for a restore that never actually happened.
        restoredCurrentLevel = false
        if (resume != null) {
            val applied = newEngine.restoreSnapshot(
                capturedMask = resume.capturedMask,
                savedScore = resume.score,
                savedLives = resume.lives,
                savedTime = resume.timeRemaining,
                savedEnemies = resume.enemies
            )
            if (applied) {
                restoredCurrentLevel = true
                viewModelScope.launch { preferences.clearSavedGame() }
            }
        }

        // Spare lives are a ONE-LEVEL boost, withdrawn whenever a level genuinely
        // begins - a fresh start or a resume alike. A resumed run's saved life count
        // already reflects any spare granted BEFORE the exit (the save is just a
        // snapshot of active.lives at that moment), so withdrawing again cannot
        // double-grant that one - takeAllExtraLives empties the bank atomically, so
        // there is nothing left in it to take a second time. What this DOES catch is
        // a life bought AFTER the exit and before hitting Resume: previously that
        // purchase was silently discarded, which is exactly the moment a player is
        // most likely to buy one - mid-struggle, right before continuing.
        if (reuseSpareLives) {
            newEngine.grantExtraLives(spareLivesInPlay)
        } else {
            withdrawSpareLives(config.levelNumber)
        }

        updateUiStateFromEngine(newEngine)
    }

    /**
     * Empties the bank of shop-bought lives and hands them to the level that is
     * starting, whichever level that is - replays and earlier levels included.
     *
     * The bank lives on disk, so it is read asynchronously and applied to the running
     * engine a moment after the level begins. Reading it synchronously from a cached
     * flow was the old approach and could observe a stale zero, silently swallowing a
     * purchase. If the player has already left the level by the time the read lands,
     * the lives go straight back into the bank rather than evaporating.
     */
    private fun withdrawSpareLives(levelNumber: Int) {
        spareLivesInPlay = 0
        viewModelScope.launch {
            val spare = preferences.takeAllExtraLives()
            if (spare <= 0) return@launch
            val active = engine
            if (active != null && active.levelConfig.levelNumber == levelNumber) {
                active.grantExtraLives(spare)
                spareLivesInPlay = spare
                updateUiStateFromEngine(active)
            } else {
                preferences.setExtraLives(spare)
            }
        }
    }

    /**
     * Called by the UI once the playfield box is laid out, with its width/height ratio.
     * If the running engine's grid was generated for a noticeably different shape and
     * the level has effectively not begun yet (no score, no trail, first seconds),
     * regenerate it so the grid exactly fits the screen. Later levels reuse the stored
     * aspect directly, so this restart only ever happens right after app start.
     *
     * [forLevel] is the level the screen reporting this size belongs to, taken from
     * the navigation argument. Layout runs on its own schedule and can fire before
     * the new level has been started, at which point [engine] is still the level the
     * player just left - restarting THAT one dropped the player back into the level
     * they had quit instead of the one they picked. Anything that does not match the
     * level currently being asked for is ignored.
     */
    fun onFieldSized(aspectWidthOverHeight: Float, forLevel: Int) {
        if (!aspectWidthOverHeight.isFinite() || aspectWidthOverHeight <= 0f) return
        val active = engine ?: return
        if (active.levelConfig.levelNumber != forLevel || requestedLevel != forLevel) return
        val newAspect = aspectWidthOverHeight.toDouble()
        val changed = kotlin.math.abs(newAspect - fieldAspect) > 0.02
        fieldAspect = newAspect
        val justStarted = active.score == 0 &&
            !active.isDrawing &&
            active.timeRemainingSeconds > active.levelConfig.timeLimitSeconds - 3.0
        if (changed && justStarted && !restoredCurrentLevel && active.status == GameStateStatus.RUNNING) {
            startLevel(active.levelConfig.levelNumber, reuseSpareLives = true)
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
            // Checkpoint the run: an interruption now costs at most the progress
            // made since the last claimed area.
            saveProgressSnapshot()
            // Peak stats for today's mission - see the field comments above.
            val fieldCells = activeEngine.width * activeEngine.height
            if (fieldCells > 0) {
                val fraction = activeEngine.lastCapturedCells.size.toDouble() / fieldCells
                if (fraction > maxSingleCaptureFraction) maxSingleCaptureFraction = fraction
            }
            if (activeEngine.scoreMultiplier > peakComboThisLevel) {
                peakComboThisLevel = activeEngine.scoreMultiplier
            }
        }
        if (activeEngine.crashCount > lastCrashCount) {
            lastCrashCount = activeEngine.crashCount
            sound.crash()
        }
        if (activeEngine.powerUpCollectedCount > lastPowerUpCount) {
            lastPowerUpCount = activeEngine.powerUpCollectedCount
            sound.powerUp()
        }

        // Clock warnings: one beep pair at 30s left, a sharper triple at 10s.
        val timeLeft = activeEngine.timeRemainingSeconds
        if (!warnedAt30 && timeLeft <= 30.0 && timeLeft > 10.0) {
            warnedAt30 = true
            sound.timeWarning(urgent = false)
        }
        if (!warnedAt10 && timeLeft <= 10.0) {
            warnedAt10 = true
            sound.timeWarning(urgent = true)
        }
        if (activeEngine.status != lastStatus) {
            when (activeEngine.status) {
                // The run is over either way, so the mid-level save is obsolete.
                // Spare lives were already spent at level start, so neither ending
                // adjusts the bank: unused ones simply expire with the level.
                GameStateStatus.LEVEL_COMPLETE -> {
                    sound.levelComplete(); sound.pauseMusic(); clearSavedGame()
                }
                GameStateStatus.GAME_OVER -> {
                    sound.gameOver(); sound.stopMusic(); clearSavedGame()
                }
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
                // Star currency accrues on EVERY completion, so replaying a level is a
                // legitimate way to grind toward an expensive skin.
                preferences.addStars(activeEngine.starsEarned)
            }
            checkDailyMissionOnLevelComplete(activeEngine)
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
        // Carry the smoothed rendering facing onto the snapshot, otherwise the UI
        // would fall back to the raw velocity sign and flicker when enemies are boxed in.
        val enemiesCopy = activeEngine.enemies.map { source ->
            source.copyWith().also { it.facing = source.facing }
        }

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
            stars = activeEngine.stars,
            starsEarned = activeEngine.starsEarned,
            webShots = activeEngine.webs
        )
    }
}
