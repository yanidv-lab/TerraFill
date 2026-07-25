package com.example.engine

/**
 * The star currency players spend on cosmetics.
 *
 * Every completed level pays out from a pool that grows with the level number, and
 * the payout is scaled by how much of the jungle was actually claimed - finishing a
 * level at 40% of the board hands over 40% of that level's pool. Levels can be
 * replayed for more stars, so a player can always grind toward an expensive skin by
 * replaying a level they enjoy (higher levels simply pay far better).
 *
 * The pool is deliberately non-linear: a steady 10-per-level climb early on, then
 * step changes at levels 10 and 15 so late levels are worth grinding.
 */
object StarEconomy {

    /** The maximum stars a level can pay out (i.e. at 100% capture). */
    fun poolForLevel(level: Int): Int {
        val l = level.coerceAtLeast(1)
        return when {
            l <= 9 -> 10 * l                      // 10, 20, 30 ... 90
            l <= 14 -> (10 * l * 1.5).toInt()     // jump at L10: 150 ... 210
            else -> (10 * l * 2.0).toInt()        // jump at L15: 300 ... 400 at L20
        }
    }

    /**
     * Stars awarded for finishing [level] having claimed [capturedPercentage] of the
     * board. Paid only on completion; a level abandoned or lost pays nothing.
     */
    fun award(level: Int, capturedPercentage: Double): Int {
        val fraction = (capturedPercentage / 100.0).coerceIn(0.0, 1.0)
        return Math.round(poolForLevel(level) * fraction).toInt()
    }
}
