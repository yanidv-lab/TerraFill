package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

/**
 * Central audio for TerraFill: Synthesizes real-time retro 8-bit chiptune
 * audio and arcade sound effects procedurally via [AudioTrack].
 * This avoids any native resource decoding errors on emulators, has zero loading latency,
 * and perfectly matches the neon retro grid aesthetic.
 */
class SoundManager(context: Context) {

    private val appContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        context.applicationContext.createAttributionContext("default")
    } else {
        context.applicationContext
    }
    
    var soundEnabled = true
        private set
    var musicEnabled = true
        private set

    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Short sound effects queue
    private val sfxQueue = ConcurrentLinkedQueue<SfxEnvelope>()
    private var sfxJob: Job? = null
    
    // Background music loop job
    private var musicJob: Job? = null
    private var musicTrack: AudioTrack? = null

    init {
        startSfxLoop()
    }

    /**
     * Computes a safe AudioTrack buffer size in bytes. Real devices reject buffers
     * smaller than [AudioTrack.getMinBufferSize] (emulators are lenient), so always
     * honor the hardware minimum or audio will be silently disabled on phones.
     */
    private fun safeTrackBufferBytes(desiredBytes: Int): Int {
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return if (min > 0) maxOf(min, desiredBytes) else maxOf(4096, desiredBytes)
    }

    private fun startSfxLoop() {
        sfxJob = scope.launch {
            val bufferSize = 256
            val track = try {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    safeTrackBufferBytes(bufferSize * 2),
                    AudioTrack.MODE_STREAM
                )
            } catch (e: Exception) {
                Log.e("SoundManager", "Failed to create SFX AudioTrack", e)
                null
            }
            
            track?.let {
                try {
                    it.play()
                } catch (e: Exception) {
                    Log.e("SoundManager", "Failed to play SFX AudioTrack", e)
                }
            }

            val buffer = ShortArray(bufferSize)
            while (isActive) {
                if (!soundEnabled || sfxQueue.isEmpty()) {
                    delay(20)
                    continue
                }

                // Clear buffer
                for (i in 0 until bufferSize) {
                    buffer[i] = 0
                }

                var hasActiveSfx = false
                val iterator = sfxQueue.iterator()
                while (iterator.hasNext()) {
                    val sfx = iterator.next()
                    if (sfx.isFinished(sampleRate)) {
                        iterator.remove()
                        continue
                    }
                    hasActiveSfx = true
                    for (i in 0 until bufferSize) {
                        val sample = sfx.nextSample(sampleRate)
                        val mixed = buffer[i] + sample
                        buffer[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }

                if (hasActiveSfx && track != null) {
                    try {
                        track.write(buffer, 0, bufferSize)
                    } catch (e: Exception) {
                        Log.e("SoundManager", "Error writing to SFX AudioTrack", e)
                    }
                } else {
                    delay(20)
                }
            }
            
            try {
                track?.stop()
                track?.release()
            } catch (e: Exception) {}
        }
    }

    private fun playSfx(sfx: SfxEnvelope) {
        if (!soundEnabled) return
        // Limit queue size to prevent overlap noise buildup
        if (sfxQueue.size < 4) {
            sfxQueue.add(sfx)
        }
    }

    fun capture() {
        // High-pitched retro arpeggio of rising notes (C5, E5, G5, C6)
        playSfx(RetroArpeggio(floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f), 60f, "square", 0.35f))
    }

    fun powerUp() {
        // Sci-fi powerup slide upwards
        playSfx(ToneSweep(350f, 1100f, 160f, "square", 0.3f))
    }

    fun crash() {
        // High-impact noisy white noise explosion mixed with low pitch slide downwards
        playSfx(NoiseExplosion(450f, 0.5f))
        playSfx(ToneSweep(180f, 40f, 350f, "square", 0.45f))
    }

    fun levelComplete() {
        // Triumphant rising arpeggio (C5, G5, C6, E6, G6)
        playSfx(RetroArpeggio(floatArrayOf(523.25f, 783.99f, 1046.50f, 1318.51f, 1567.98f), 100f, "square", 0.4f))
    }

    fun gameOver() {
        // Sad, slow descending bass tones
        playSfx(RetroArpeggio(floatArrayOf(392.00f, 311.13f, 246.94f, 196.00f), 200f, "triangle", 0.5f))
    }

    fun tap() {
        // Retro tap beep
        playSfx(ToneSweep(600f, 750f, 40f, "triangle", 0.15f))
    }

    fun move() {
        // Very soft subtle pulse when moving on safe ground
        playSfx(ToneSweep(150f, 200f, 25f, "triangle", 0.06f))
    }

    fun drawTrail() {
        // Rhythmic retro trail beep
        playSfx(ToneSweep(850f, 950f, 30f, "square", 0.1f))
    }

    fun startMusic() {
        if (!musicEnabled) return
        if (musicJob != null) return

        musicJob = scope.launch {
            val bufferSize = 512
            val track = try {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    safeTrackBufferBytes(bufferSize * 2),
                    AudioTrack.MODE_STREAM
                )
            } catch (e: Exception) {
                Log.e("SoundManager", "Failed to create music AudioTrack", e)
                null
            }
            
            musicTrack = track
            track?.let {
                try {
                    it.play()
                } catch (e: Exception) {
                    Log.e("SoundManager", "Failed to play music AudioTrack", e)
                }
            }

            // Beautiful 32-step retro chiptune melody and bassline
            val melody = floatArrayOf(
                // C Major
                523.25f, 659.25f, 783.99f, 523.25f, 659.25f, 783.99f, 1046.50f, 783.99f,
                // A Minor
                440.00f, 523.25f, 659.25f, 440.00f, 523.25f, 659.25f, 880.00f, 659.25f,
                // F Major
                349.23f, 440.00f, 523.25f, 349.23f, 440.00f, 523.25f, 698.46f, 523.25f,
                // G Major
                392.00f, 493.88f, 587.33f, 392.00f, 493.88f, 587.33f, 783.99f, 587.33f
            )

            val bass = floatArrayOf(
                // C Major
                130.81f, 130.81f, 130.81f, 130.81f, 130.81f, 130.81f, 130.81f, 130.81f,
                // A Minor
                110.00f, 110.00f, 110.00f, 110.00f, 110.00f, 110.00f, 110.00f, 110.00f,
                // F Major
                87.31f, 87.31f, 87.31f, 87.31f, 87.31f, 87.31f, 87.31f, 87.31f,
                // G Major
                98.00f, 98.00f, 98.00f, 98.00f, 98.00f, 98.00f, 98.00f, 98.00f
            )

            var step = 0
            val stepDurationMs = 150
            val samplesPerStep = (stepDurationMs * sampleRate / 1000)

            val buffer = ShortArray(256)
            var phaseMelody = 0f
            var phaseBass = 0f

            while (isActive) {
                if (!musicEnabled || track == null) {
                    delay(100)
                    continue
                }

                val currentMelodyFreq = melody[step % melody.size]
                val currentBassFreq = bass[step % bass.size]

                var samplesWritten = 0
                while (samplesWritten < samplesPerStep && isActive) {
                    val chunk = minOf(buffer.size, samplesPerStep - samplesWritten)
                    for (i in 0 until chunk) {
                        // Melody wave (square wave, 25% duty cycle for authentic chiptune vibe)
                        var melVal = 0f
                        if (currentMelodyFreq > 0f) {
                            val dPhaseMel = (2f * PI * currentMelodyFreq / sampleRate).toFloat()
                            phaseMelody = (phaseMelody + dPhaseMel) % (2f * PI).toFloat()
                            melVal = if (phaseMelody < PI * 0.5f) 0.12f else -0.12f
                        }

                        // Bass wave (triangle wave, smooth, bouncy chiptune bass)
                        var bassVal = 0f
                        if (currentBassFreq > 0f) {
                            val dPhaseBass = (2f * PI * currentBassFreq / sampleRate).toFloat()
                            phaseBass = (phaseBass + dPhaseBass) % (2f * PI).toFloat()
                            val p = phaseBass / (2f * PI.toFloat())
                            bassVal = (if (p < 0.5f) -1f + 4f * p else 3f - 4f * p) * 0.28f
                        }

                        // Retro slide envelope per step to make notes punchy
                        val stepProgress = (samplesWritten + i).toFloat() / samplesPerStep
                        val stepEnvelope = if (stepProgress < 0.15f) stepProgress / 0.15f else if (stepProgress > 0.85f) (1f - stepProgress) / 0.15f else 1f

                        val mixed = (melVal + bassVal) * 5500f * stepEnvelope
                        buffer[i] = mixed.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                    }
                    if (isActive && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            track.write(buffer, 0, chunk)
                        } catch (e: Exception) {
                            Log.e("SoundManager", "Error writing to music AudioTrack", e)
                        }
                    }
                    samplesWritten += chunk
                }

                step++
            }
            
            try {
                track?.stop()
                track?.release()
            } catch (e: Exception) {}
        }
    }

    fun pauseMusic() {
        musicEnabled = false
        try {
            musicTrack?.pause()
        } catch (e: Exception) {}
    }

    fun resumeMusic() {
        if (!soundEnabled) return
        musicEnabled = true
        try {
            musicTrack?.play()
        } catch (e: Exception) {}
        if (musicJob == null) {
            startMusic()
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
        try {
            musicTrack?.stop()
            musicTrack?.release()
        } catch (e: Exception) {}
        musicTrack = null
    }

    fun toggleAll(): Boolean {
        val on = !soundEnabled
        soundEnabled = on
        musicEnabled = on
        if (on) {
            resumeMusic()
        } else {
            pauseMusic()
        }
        return on
    }

    fun release() {
        stopMusic()
        sfxJob?.cancel()
        sfxJob = null
        sfxQueue.clear()
        scope.cancel()
    }
}

// Retro Sound Envelope Abstractions
abstract class SfxEnvelope {
    abstract fun isFinished(sampleRate: Int): Boolean
    abstract fun nextSample(sampleRate: Int): Short
}

class ToneSweep(
    private val startFreq: Float,
    private val endFreq: Float,
    private val durationMs: Float,
    private val waveType: String = "square",
    private val volume: Float = 0.5f
) : SfxEnvelope() {
    private var sampleCount = 0
    private val totalSamples = (durationMs * 22050f / 1000f).toInt()
    private var phase = 0f

    override fun isFinished(sampleRate: Int): Boolean = sampleCount >= totalSamples

    override fun nextSample(sampleRate: Int): Short {
        if (sampleCount >= totalSamples) return 0

        val progress = sampleCount.toFloat() / totalSamples
        val currentFreq = startFreq + (endFreq - startFreq) * progress
        val envelope = if (progress > 0.8f) (1f - progress) / 0.2f else 1f

        val deltaPhase = (2f * PI * currentFreq / sampleRate).toFloat()
        phase = (phase + deltaPhase) % (2f * PI).toFloat()

        val waveVal = when (waveType) {
            "square" -> if (phase < PI) 1f else -1f
            "triangle" -> {
                val p = phase / (2f * PI.toFloat())
                if (p < 0.5f) -1f + 4f * p else 3f - 4f * p
            }
            else -> sin(phase)
        }

        sampleCount++
        return (waveVal * volume * envelope * 8000f).toInt().toShort()
    }
}

class NoiseExplosion(
    private val durationMs: Float,
    private val volume: Float = 0.5f
) : SfxEnvelope() {
    private var sampleCount = 0
    private val totalSamples = (durationMs * 22050f / 1000f).toInt()
    private val random = Random(42)
    private var lastValue = 0f

    override fun isFinished(sampleRate: Int): Boolean = sampleCount >= totalSamples

    override fun nextSample(sampleRate: Int): Short {
        if (sampleCount >= totalSamples) return 0

        val progress = sampleCount.toFloat() / totalSamples
        val envelope = (1f - progress)

        if (sampleCount % 4 == 0) {
            lastValue = random.nextFloat() * 2f - 1f
        }

        sampleCount++
        return (lastValue * volume * envelope * 8000f).toInt().toShort()
    }
}

class RetroArpeggio(
    private val freqs: FloatArray,
    private val noteDurationMs: Float,
    private val waveType: String = "square",
    private val volume: Float = 0.5f
) : SfxEnvelope() {
    private var sampleCount = 0
    private val noteSamples = (noteDurationMs * 22050f / 1000f).toInt()
    private val totalSamples = noteSamples * freqs.size
    private var phase = 0f

    override fun isFinished(sampleRate: Int): Boolean = sampleCount >= totalSamples

    override fun nextSample(sampleRate: Int): Short {
        if (sampleCount >= totalSamples) return 0

        val currentNoteIdx = sampleCount / noteSamples
        val currentFreq = freqs[currentNoteIdx]

        val progressInNote = (sampleCount % noteSamples).toFloat() / noteSamples
        val envelope = if (progressInNote > 0.7f) (1f - progressInNote) / 0.3f else 1f

        val deltaPhase = (2f * PI * currentFreq / sampleRate).toFloat()
        phase = (phase + deltaPhase) % (2f * PI).toFloat()

        val waveVal = when (waveType) {
            "square" -> if (phase < PI) 1f else -1f
            "triangle" -> {
                val p = phase / (2f * PI.toFloat())
                if (p < 0.5f) -1f + 4f * p else 3f - 4f * p
            }
            else -> sin(phase)
        }

        sampleCount++
        return (waveVal * volume * envelope * 8000f).toInt().toShort()
    }
}
