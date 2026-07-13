package com.example.engine

/**
 * The kinds of power-up the player can pick up on the field.
 */
enum class PowerUpType {
    /** Absorbs the next crash without costing a life. */
    SHIELD,

    /** Freezes all enemies in place for a short time. */
    FREEZE,

    /** Slows all enemies to a crawl for a short time. */
    SLOW
}

/**
 * A collectible sitting on an EMPTY grid cell. The player collects it by moving
 * its head onto the cell.
 */
data class PowerUp(
    val id: Int,
    val type: PowerUpType,
    val x: Int,
    val y: Int
)

/**
 * Computes a 1-3 star rating for a completed level from how well the player did.
 * Returns 0 if the level was not actually completed (target not reached).
 *
 * - 1 star: level cleared.
 * - 2 stars: cleared and either kept all lives, captured comfortably past the
 *   target, or finished with plenty of time.
 * - 3 stars: cleared with no lives lost AND a strong capture or time margin.
 */
fun computeStars(
    capturedPercentage: Double,
    targetPercentage: Double,
    timeRemainingSeconds: Double,
    timeLimitSeconds: Int,
    livesRemaining: Int,
    initialLives: Int
): Int {
    if (capturedPercentage < targetPercentage) return 0

    val noDamage = livesRemaining >= initialLives
    val captureMargin = capturedPercentage - targetPercentage
    val timeFraction = if (timeLimitSeconds > 0) {
        timeRemainingSeconds / timeLimitSeconds
    } else 0.0

    val excellentCapture = captureMargin >= 12.0
    val excellentTime = timeFraction >= 0.5
    val goodCapture = captureMargin >= 6.0
    val goodTime = timeFraction >= 0.3

    return when {
        noDamage && (excellentCapture || excellentTime) -> 3
        noDamage || goodCapture || goodTime -> 2
        else -> 1
    }
}
