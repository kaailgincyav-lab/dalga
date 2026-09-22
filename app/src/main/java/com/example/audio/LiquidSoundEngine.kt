package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class LiquidSoundEngine {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE = 1024
        private const val MAX_VOICES = 6
    }

    private sealed class SoundCommand {
        data class Drop(val viscosity: Float, val strength: Float) : SoundCommand()
        data class Ripple(val viscosity: Float, val speed: Float) : SoundCommand()
        object Calm : SoundCommand()
        object Thunder : SoundCommand()
        object SnowMelt : SoundCommand()
    }

    private class Voice {
        var active: Boolean = false
        var sampleIndex: Int = 0
        var totalSamples: Int = 0
        var startFreq: Float = 800f
        var endFreq: Float = 1400f
        var decayCoeff: Float = 30f
        var volume: Float = 0.5f
        var isViscousThud: Boolean = false
        var phase: Double = 0.0
    }

    private val isRunning = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var synthesisThread: Thread? = null

    // Lock-free event queue from UI thread -> Audio thread
    private val commandQueue = ConcurrentLinkedQueue<SoundCommand>()

    // Audio thread private state (no synchronization needed!)
    private val voices = Array(MAX_VOICES) { Voice() }
    private val buffer = ShortArray(BUFFER_SIZE)

    @Volatile var soundEnabled: Boolean = true

    private var lastDragSoundTime = 0L

    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBufferSize = (minBufferSize * 2).coerceAtLeast(BUFFER_SIZE * 4)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track

            synthesisThread = Thread {
                audioLoop()
            }.apply {
                name = "LiquidAudioSynth"
                isDaemon = true
                priority = Thread.NORM_PRIORITY + 1
                start()
            }
        } catch (_: Exception) {
            isRunning.set(false)
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            synthesisThread?.interrupt()
            synthesisThread = null
            audioTrack?.apply {
                stop()
                release()
            }
            audioTrack = null
        } catch (_: Exception) {}
    }

    /**
     * Completely non-blocking lock-free call from the UI thread.
     */
    fun playDrop(viscosity: Float, strength: Float = 1.0f) {
        if (!soundEnabled || !isRunning.get()) return
        commandQueue.offer(SoundCommand.Drop(viscosity, strength))
    }

    /**
     * Completely non-blocking lock-free call from the UI thread.
     */
    fun playRippleWave(viscosity: Float, speed: Float) {
        if (!soundEnabled || !isRunning.get()) return

        val now = System.currentTimeMillis()
        if (now - lastDragSoundTime < 95) return
        lastDragSoundTime = now

        commandQueue.offer(SoundCommand.Ripple(viscosity, speed))
    }

    fun playCalmSound() {
        if (!soundEnabled || !isRunning.get()) return
        commandQueue.offer(SoundCommand.Calm)
    }

    fun playThunder() {
        if (!soundEnabled || !isRunning.get()) return
        commandQueue.offer(SoundCommand.Thunder)
    }

    fun playSnowMelt() {
        if (!soundEnabled || !isRunning.get()) return
        commandQueue.offer(SoundCommand.SnowMelt)
    }

    private fun handleCommand(cmd: SoundCommand) {
        when (cmd) {
            is SoundCommand.Drop -> {
                val voice = findFreeVoice() ?: return
                val vNorm = cmd.viscosity.coerceIn(0.05f, 0.85f)
                val freqScale = 1.0f - ((vNorm - 0.05f) / 0.80f)

                val baseStart = 200f + freqScale * 850f + (Random.nextFloat() * 80f - 40f)
                val baseEnd = baseStart * (1.3f + freqScale * 0.5f)

                val decay = 20f + (1f - freqScale) * 35f
                val durationSec = 0.06f + freqScale * 0.08f
                val totalSamples = (durationSec * SAMPLE_RATE).toInt()
                val vol = (0.22f + cmd.strength * 0.25f).coerceIn(0.12f, 0.65f)

                voice.startFreq = baseStart
                voice.endFreq = baseEnd
                voice.decayCoeff = decay
                voice.totalSamples = totalSamples
                voice.sampleIndex = 0
                voice.volume = vol
                voice.isViscousThud = (freqScale < 0.35f)
                voice.phase = 0.0
                voice.active = true
            }
            is SoundCommand.Ripple -> {
                val voice = findFreeVoice() ?: return
                val vNorm = cmd.viscosity.coerceIn(0.05f, 0.85f)
                val freqScale = 1.0f - ((vNorm - 0.05f) / 0.80f)

                val baseStart = 160f + freqScale * 600f + (Random.nextFloat() * 60f)
                val baseEnd = baseStart * (1.15f + freqScale * 0.3f)

                val durationSec = 0.05f + freqScale * 0.05f
                val totalSamples = (durationSec * SAMPLE_RATE).toInt()
                val vol = (0.12f + (cmd.speed * 0.08f)).coerceIn(0.08f, 0.35f)

                voice.startFreq = baseStart
                voice.endFreq = baseEnd
                voice.decayCoeff = 35f + (1f - freqScale) * 20f
                voice.totalSamples = totalSamples
                voice.sampleIndex = 0
                voice.volume = vol
                voice.isViscousThud = (freqScale < 0.35f)
                voice.phase = 0.0
                voice.active = true
            }
            is SoundCommand.Calm -> {
                val voice = findFreeVoice() ?: return
                voice.startFreq = 280f
                voice.endFreq = 120f
                voice.decayCoeff = 14f
                voice.totalSamples = (0.22f * SAMPLE_RATE).toInt()
                voice.sampleIndex = 0
                voice.volume = 0.40f
                voice.isViscousThud = false
                voice.phase = 0.0
                voice.active = true
            }
            is SoundCommand.Thunder -> {
                val voice = findFreeVoice() ?: return
                voice.startFreq = 110f
                voice.endFreq = 42f
                voice.decayCoeff = 4.5f
                voice.totalSamples = (0.45f * SAMPLE_RATE).toInt()
                voice.sampleIndex = 0
                voice.volume = 0.50f
                voice.isViscousThud = true
                voice.phase = 0.0
                voice.active = true
            }
            is SoundCommand.SnowMelt -> {
                val voice = findFreeVoice() ?: return
                voice.startFreq = 1650f
                voice.endFreq = 950f
                voice.decayCoeff = 42f
                voice.totalSamples = (0.05f * SAMPLE_RATE).toInt()
                voice.sampleIndex = 0
                voice.volume = 0.16f
                voice.isViscousThud = false
                voice.phase = 0.0
                voice.active = true
            }
        }
    }

    private fun findFreeVoice(): Voice? {
        var freeVoice: Voice? = null
        var oldestVoice: Voice? = null
        var maxProgress = -1f

        for (voice in voices) {
            if (!voice.active) {
                return voice
            }
            val progress = voice.sampleIndex.toFloat() / voice.totalSamples.coerceAtLeast(1)
            if (progress > maxProgress) {
                maxProgress = progress
                oldestVoice = voice
            }
        }
        return freeVoice ?: oldestVoice
    }

    private fun audioLoop() {
        val dt = 1.0 / SAMPLE_RATE

        while (isRunning.get()) {
            // Drain up to 4 commands per buffer to avoid lag and sound build-up
            var processed = 0
            while (processed < 4) {
                val cmd = commandQueue.poll() ?: break
                handleCommand(cmd)
                processed++
            }

            var anyActive = false

            // Completely lock-free inner synthesis loop!
            for (i in 0 until BUFFER_SIZE) {
                var sampleSum = 0f

                for (v in voices) {
                    if (!v.active) continue
                    anyActive = true

                    val t = v.sampleIndex.toFloat() / SAMPLE_RATE
                    val normProgress = v.sampleIndex.toFloat() / v.totalSamples

                    if (v.sampleIndex >= v.totalSamples) {
                        v.active = false
                        continue
                    }

                    // Upward chirp frequency (bubble resonant pinch-off)
                    val currentFreq = v.startFreq + (v.endFreq - v.startFreq) * (normProgress * normProgress)
                    v.phase += 2.0 * PI * currentFreq * dt

                    // Envelope: fast rise, exponential decay
                    val attack = (normProgress * 6.0f).coerceAtMost(1.0f)
                    val env = attack * exp(-v.decayCoeff * t)

                    var wave = sin(v.phase).toFloat()

                    if (!v.isViscousThud) {
                        wave += 0.22f * sin(v.phase * 2.0).toFloat()
                        if (normProgress < 0.15f) {
                            wave += (Random.nextFloat() * 2f - 1f) * 0.12f * (1f - normProgress / 0.15f)
                        }
                    } else {
                        wave = (wave * 0.85f) + 0.15f * sin(v.phase * 0.5).toFloat()
                    }

                    sampleSum += wave * env * v.volume
                    v.sampleIndex++
                }

                val clamped = sampleSum.coerceIn(-1.0f, 1.0f)
                buffer[i] = (clamped * 32767f).toInt().toShort()
            }

            if (anyActive && soundEnabled) {
                audioTrack?.write(buffer, 0, BUFFER_SIZE)
            } else {
                buffer.fill(0)
                audioTrack?.write(buffer, 0, BUFFER_SIZE)
                try {
                    Thread.sleep(8)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }
}
