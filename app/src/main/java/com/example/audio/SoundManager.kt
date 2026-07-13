package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.R

/**
 * Central audio for TerraFill: short sound effects via [SoundPool] (low latency,
 * many overlapping streams) and a looping background track via [MediaPlayer].
 *
 * All effects and music honor the [soundEnabled] / [musicEnabled] flags so the
 * player can mute. Call [release] when the owning ViewModel is cleared.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<Int, Int>()

    var soundEnabled = true
        private set
    var musicEnabled = true
        private set

    private var music: MediaPlayer? = null

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()

        for (res in intArrayOf(
            R.raw.sfx_capture, R.raw.sfx_crash, R.raw.sfx_level_complete,
            R.raw.sfx_game_over, R.raw.sfx_tap, R.raw.sfx_powerup
        )) {
            soundIds[res] = soundPool.load(appContext, res, 1)
        }
    }

    private fun play(res: Int, volume: Float = 1f) {
        if (!soundEnabled) return
        val id = soundIds[res] ?: return
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun capture() = play(R.raw.sfx_capture)
    fun powerUp() = play(R.raw.sfx_powerup)
    fun crash() = play(R.raw.sfx_crash)
    fun levelComplete() = play(R.raw.sfx_level_complete)
    fun gameOver() = play(R.raw.sfx_game_over)
    fun tap() = play(R.raw.sfx_tap, 0.5f)

    fun startMusic() {
        if (!musicEnabled) return
        if (music == null) {
            music = MediaPlayer.create(appContext, R.raw.music_loop)?.apply {
                isLooping = true
                setVolume(0.35f, 0.35f)
            }
        }
        music?.let { if (!it.isPlaying) it.start() }
    }

    fun pauseMusic() {
        music?.let { if (it.isPlaying) it.pause() }
    }

    fun resumeMusic() {
        if (musicEnabled) music?.let { if (!it.isPlaying) it.start() }
    }

    fun stopMusic() {
        music?.let { it.stop(); it.release() }
        music = null
    }

    /** Toggles both music and effects together. Returns the new enabled state. */
    fun toggleAll(): Boolean {
        val on = !soundEnabled
        soundEnabled = on
        musicEnabled = on
        if (on) startMusic() else pauseMusic()
        return on
    }

    fun release() {
        stopMusic()
        soundPool.release()
        soundIds.clear()
    }
}
