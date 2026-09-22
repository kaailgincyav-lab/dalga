package com.example.simulation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class LiquidSimulationEngine(
    var gridWidth: Int = 76,
    var gridHeight: Int = 160
) {
    private var size = gridWidth * gridHeight

    private var bufferA = FloatArray(size)
    private var bufferB = FloatArray(size)

    private var isBufferAActive = true
    private var pixelBuffer = IntArray(size)

    private var androidBitmap = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
    var composeBitmap: ImageBitmap = androidBitmap.asImageBitmap()
        private set

    private val touchEventQueue = ConcurrentLinkedQueue<TouchPoint>()

    private var clearFadeFrames = 0
    private var frameCount = 0L

    data class TouchPoint(
        val x: Float,
        val y: Float,
        val strength: Float,
        val radius: Float,
        val isVortex: Boolean = false
    )

    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (newWidth == gridWidth && newHeight == gridHeight) return

        gridWidth = newWidth.coerceIn(50, 140)
        gridHeight = newHeight.coerceIn(80, 300)
        size = gridWidth * gridHeight

        bufferA = FloatArray(size)
        bufferB = FloatArray(size)
        pixelBuffer = IntArray(size)

        try {
            androidBitmap.recycle()
        } catch (_: Exception) {}

        androidBitmap = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
        composeBitmap = androidBitmap.asImageBitmap()
        isBufferAActive = true
    }

    fun addRipple(
        normX: Float,
        normY: Float,
        strength: Float = 1.0f,
        radiusCells: Float = 3.2f
    ) {
        val gx = (normX * gridWidth).coerceIn(1f, (gridWidth - 2).toFloat())
        val gy = (normY * gridHeight).coerceIn(1f, (gridHeight - 2).toFloat())
        touchEventQueue.offer(TouchPoint(gx, gy, strength, radiusCells, isVortex = false))
    }

    fun addVortex(
        normX: Float,
        normY: Float,
        strength: Float = 1.8f,
        radiusCells: Float = 5.2f
    ) {
        val gx = (normX * gridWidth).coerceIn(2f, (gridWidth - 3).toFloat())
        val gy = (normY * gridHeight).coerceIn(2f, (gridHeight - 3).toFloat())
        touchEventQueue.offer(TouchPoint(gx, gy, strength, radiusCells, isVortex = true))
    }

    fun addDragLine(
        x0Norm: Float,
        y0Norm: Float,
        x1Norm: Float,
        y1Norm: Float,
        strength: Float,
        radiusCells: Float = 2.8f,
        isVortex: Boolean = false
    ) {
        val x0 = x0Norm * gridWidth
        val y0 = y0Norm * gridHeight
        val x1 = x1Norm * gridWidth
        val y1 = y1Norm * gridHeight

        val dist = hypot(x1 - x0, y1 - y0)
        val step = 1.4f
        val steps = (dist / step).toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val gx = (x0 + (x1 - x0) * t).coerceIn(1f, (gridWidth - 2).toFloat())
            val gy = (y0 + (y1 - y0) * t).coerceIn(1f, (gridHeight - 2).toFloat())
            touchEventQueue.offer(TouchPoint(gx, gy, strength, radiusCells, isVortex))
        }
    }

    fun clearSurface() {
        clearFadeFrames = 10
    }

    fun stepSimulation(
        settings: LiquidSettings,
        onAutoDrop: ((Float) -> Unit)? = null
    ): ImageBitmap {
        frameCount++
        val current = if (isBufferAActive) bufferA else bufferB
        val next = if (isBufferAActive) bufferB else bufferA

        // 1. Process queued touch disturbances
        var processedEvents = 0
        while (processedEvents < 16) {
            val point = touchEventQueue.poll() ?: break
            if (point.isVortex) {
                applyVortexDisturbance(current, point.x, point.y, point.strength * settings.waveStrength, point.radius)
            } else {
                applyDisturbance(current, point.x, point.y, point.strength * settings.waveStrength, point.radius)
            }
            processedEvents++
        }

        // 2. Handle clear surface calm animation
        if (clearFadeFrames > 0) {
            val fade = (clearFadeFrames - 1).toFloat() / 10f
            for (i in 0 until size) {
                current[i] *= fade
                next[i] *= fade
            }
            clearFadeFrames--
            if (clearFadeFrames == 0) {
                current.fill(0f)
                next.fill(0f)
            }
        }

        // 3. Auto-Rain / Rainstorm mode
        val isRainActive = settings.autoRain || settings.interactionMode == InteractionMode.RAINSTORM
        if (isRainActive) {
            val rainChance = if (settings.interactionMode == InteractionMode.RAINSTORM) 0.35f else 0.08f
            val dropCount = if (settings.interactionMode == InteractionMode.RAINSTORM) Random.nextInt(1, 3) else 1

            if (Random.nextFloat() < rainChance) {
                for (d in 0 until dropCount) {
                    val rx = Random.nextFloat() * (gridWidth - 6) + 3
                    val ry = Random.nextFloat() * (gridHeight - 6) + 3
                    val dropStrength = (Random.nextFloat() * 1.4f + 0.7f) * settings.waveStrength
                    applyDisturbance(current, rx, ry, dropStrength, 2.8f)
                    onAutoDrop?.invoke(dropStrength)
                }
            }
        }

        // 4. Wave propagation (if not frozen)
        if (!settings.isFrozen) {
            // Damping modulated by viscosity
            val baseDamping = 0.994f - (settings.viscosity * 0.080f)
            val damping = baseDamping.coerceIn(0.89f, 0.996f)

            // Surface tension acceleration factor: 0.46 .. 0.53
            val waveSpeed = (0.47f + (settings.surfaceTension - 1.0f) * 0.04f).coerceIn(0.44f, 0.52f)

            // Laplacian diffusion smoothing for high viscosity
            val diffusion = (settings.viscosity * 0.10f).coerceIn(0f, 0.18f)
            val oneMinusDiff = 1f - diffusion

            val w = gridWidth
            val h = gridHeight

            for (y in 1 until h - 1) {
                val row = y * w
                val topRow = (y - 1) * w
                val botRow = (y + 1) * w

                for (x in 1 until w - 1) {
                    val idx = row + x

                    val left = current[idx - 1]
                    val right = current[idx + 1]
                    val top = current[topRow + x]
                    val bottom = current[botRow + x]

                    val wave = (left + right + top + bottom) * waveSpeed - next[idx]
                    val smoothed = (left + right + top + bottom) * 0.25f
                    val result = (wave * oneMinusDiff + smoothed * diffusion) * damping

                    next[idx] = result
                }
            }

            // Swap active buffers
            isBufferAActive = !isBufferAActive
        }

        val displayBuffer = if (isBufferAActive) bufferA else bufferB

        // 5. Render to Pixel Buffer with Lighting, Shading, and Color LUT
        renderPixels(displayBuffer, settings.palette, settings.waveStrength)

        // Update Bitmap
        androidBitmap.setPixels(pixelBuffer, 0, gridWidth, 0, 0, gridWidth, gridHeight)
        return composeBitmap
    }

    private fun applyDisturbance(
        buffer: FloatArray,
        cx: Float,
        cy: Float,
        strength: Float,
        radius: Float
    ) {
        val w = gridWidth
        val h = gridHeight
        val r = radius.toInt() + 1
        val minX = (cx - r).toInt().coerceIn(1, w - 2)
        val maxX = (cx + r).toInt().coerceIn(1, w - 2)
        val minY = (cy - r).toInt().coerceIn(1, h - 2)
        val maxY = (cy + r).toInt().coerceIn(1, h - 2)

        val rSq = radius * radius
        val mult = strength * 8.0f

        for (y in minY..maxY) {
            val dy = y - cy
            val dySq = dy * dy
            val row = y * w
            for (x in minX..maxX) {
                val dx = x - cx
                val distSq = dx * dx + dySq
                if (distSq <= rSq) {
                    val falloff = 1f - (distSq / rSq)
                    buffer[row + x] += mult * (falloff * falloff)
                }
            }
        }
    }

    private fun applyVortexDisturbance(
        buffer: FloatArray,
        cx: Float,
        cy: Float,
        strength: Float,
        radius: Float
    ) {
        val w = gridWidth
        val h = gridHeight
        val r = (radius * 1.3f).toInt() + 1
        val minX = (cx - r).toInt().coerceIn(1, w - 2)
        val maxX = (cx + r).toInt().coerceIn(1, w - 2)
        val minY = (cy - r).toInt().coerceIn(1, h - 2)
        val maxY = (cy + r).toInt().coerceIn(1, h - 2)

        val rSq = radius * radius
        val mult = strength * 6.5f

        for (y in minY..maxY) {
            val dy = y - cy
            val row = y * w
            for (x in minX..maxX) {
                val dx = x - cx
                val distSq = dx * dx + dy * dy
                if (distSq <= rSq && distSq > 0.5f) {
                    val dist = kotlin.math.sqrt(distSq)
                    val normDist = dist / radius
                    val angle = atan2(dy, dx)
                    // Spiral swirl: negative whirlpool depression at center, rotating crest ring
                    val spiral = cos(angle * 2.0f + normDist * 4.0f)
                    val falloff = (1f - normDist) * normDist * 4f
                    val depression = if (normDist < 0.35f) -1.2f else 0.8f
                    buffer[row + x] += mult * falloff * (spiral * 0.6f + depression)
                }
            }
        }
    }

    private fun renderPixels(
        buffer: FloatArray,
        palette: LiquidPaletteType,
        strength: Float
    ) {
        val w = gridWidth
        val h = gridHeight
        val lut = palette.lut

        val lx = -0.55f
        val ly = -0.65f

        val heightAmp = 9.5f * (1f + (strength - 1f) * 0.25f)
        val slopeAmp = 30f

        for (y in 0 until h) {
            val row = y * w
            val topRow = if (y > 0) (y - 1) * w else row
            val botRow = if (y < h - 1) (y + 1) * w else row

            for (x in 0 until w) {
                val idx = row + x
                val left = if (x > 0) idx - 1 else idx
                val right = if (x < w - 1) idx + 1 else idx

                val dx = buffer[right] - buffer[left]
                val dy = buffer[botRow + x] - buffer[topRow + x]
                val heightVal = buffer[idx]

                val slope = dx * lx + dy * ly

                val lutIdx = (210 + (heightVal * heightAmp) + (slope * slopeAmp))
                    .toInt()
                    .coerceIn(0, 511)

                var color = lut[lutIdx]

                // Fast inline specular highlight
                val sheen = slope - 0.92f
                if (sheen > 0f) {
                    val shine = (sheen * 85f).toInt().coerceIn(0, 255)
                    val a = (color ushr 24) and 0xFF
                    val r = min(255, ((color ushr 16) and 0xFF) + shine)
                    val g = min(255, ((color ushr 8) and 0xFF) + shine)
                    val b = min(255, (color and 0xFF) + shine)
                    color = (a shl 24) or (r shl 16) or (g shl 8) or b
                }

                pixelBuffer[idx] = color
            }
        }
    }
}
