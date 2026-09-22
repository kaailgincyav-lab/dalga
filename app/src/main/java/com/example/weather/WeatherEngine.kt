package com.example.weather

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.simulation.WeatherType
import kotlin.math.sin
import kotlin.random.Random

class WeatherEngine {

    companion object {
        private const val MAX_PARTICLES = 120
        private const val MAX_SPLASHES = 32
    }

    private class Particle {
        var x: Float = 0f
        var y: Float = 0f
        var vx: Float = 0f
        var vy: Float = 0f
        var length: Float = 16f
        var size: Float = 3f
        var alpha: Float = 0.6f
        var swayPhase: Float = 0f
        var swaySpeed: Float = 2f
        var targetSurfaceY: Float = 0f
        var active: Boolean = false
    }

    private class SplashRing {
        var x: Float = 0f
        var y: Float = 0f
        var radius: Float = 0f
        var maxRadius: Float = 24f
        var alpha: Float = 0f
        var active: Boolean = false
    }

    private val particles = Array(MAX_PARTICLES) { Particle() }
    private val splashes = Array(MAX_SPLASHES) { SplashRing() }

    private var lightningFlashAlpha = 0f
    private var nextLightningCounter = 180

    private var soundThrottleTime = 0L

    fun update(
        width: Float,
        height: Float,
        weatherType: WeatherType,
        intensity: Float,
        wind: Float,
        onSurfaceImpact: (normX: Float, normY: Float, isRain: Boolean, strength: Float) -> Unit
    ) {
        if (weatherType == WeatherType.CLEAR || width <= 0f || height <= 0f) {
            lightningFlashAlpha = 0f
            return
        }

        val activeCount = when (weatherType) {
            WeatherType.RAIN -> (MAX_PARTICLES * 0.55f * intensity).toInt().coerceIn(15, MAX_PARTICLES)
            WeatherType.STORM -> (MAX_PARTICLES * 0.95f * intensity).toInt().coerceIn(30, MAX_PARTICLES)
            WeatherType.SNOW -> (MAX_PARTICLES * 0.50f * intensity).toInt().coerceIn(15, MAX_PARTICLES)
            WeatherType.CLEAR -> 0
        }

        // Lightning effect in storm mode
        if (weatherType == WeatherType.STORM) {
            nextLightningCounter--
            if (nextLightningCounter <= 0) {
                lightningFlashAlpha = Random.nextFloat() * 0.35f + 0.25f
                nextLightningCounter = Random.nextInt(120, 360)
            }
        }
        if (lightningFlashAlpha > 0.01f) {
            lightningFlashAlpha *= 0.82f
        } else {
            lightningFlashAlpha = 0f
        }

        // Update Splash Rings
        for (splash in splashes) {
            if (!splash.active) continue
            splash.radius += 1.8f
            splash.alpha *= 0.88f
            if (splash.alpha < 0.04f || splash.radius >= splash.maxRadius) {
                splash.active = false
            }
        }

        // Update Particles
        for (i in 0 until MAX_PARTICLES) {
            val p = particles[i]
            if (i >= activeCount) {
                p.active = false
                continue
            }

            if (!p.active) {
                initParticle(p, width, height, weatherType, wind, randomY = true)
                p.active = true
            }

            when (weatherType) {
                WeatherType.RAIN, WeatherType.STORM -> {
                    p.x += p.vx + (wind * 6f)
                    p.y += p.vy

                    // Check if raindrop hit target surface height
                    if (p.y >= p.targetSurfaceY || p.y >= height) {
                        val normX = (p.x / width).coerceIn(0.05f, 0.95f)
                        val normY = (p.targetSurfaceY / height).coerceIn(0.05f, 0.95f)

                        val impactStrength = if (weatherType == WeatherType.STORM) 1.2f else 0.85f
                        onSurfaceImpact(normX, normY, true, impactStrength)

                        // Spawn splash ring at landing point
                        spawnSplash(p.x.coerceIn(0f, width), p.targetSurfaceY, if (weatherType == WeatherType.STORM) 30f else 20f)

                        // Reset particle to top
                        initParticle(p, width, height, weatherType, wind, randomY = false)
                    }
                }
                WeatherType.SNOW -> {
                    p.swayPhase += p.swaySpeed * 0.05f
                    p.x += sin(p.swayPhase) * 1.5f + (wind * 2.5f)
                    p.y += p.vy

                    if (p.y >= p.targetSurfaceY || p.y >= height) {
                        val normX = (p.x / width).coerceIn(0.05f, 0.95f)
                        val normY = (p.targetSurfaceY / height).coerceIn(0.05f, 0.95f)

                        // Snow dissolves gently creating delicate micro-ripples
                        onSurfaceImpact(normX, normY, false, 0.35f)

                        initParticle(p, width, height, weatherType, wind, randomY = false)
                    }
                }
                WeatherType.CLEAR -> {}
            }

            // Wrap horizontal bounds
            if (p.x < -40f) p.x = width + 20f
            if (p.x > width + 40f) p.x = -20f
        }
    }

    private fun initParticle(
        p: Particle,
        width: Float,
        height: Float,
        weatherType: WeatherType,
        wind: Float,
        randomY: Boolean
    ) {
        p.x = Random.nextFloat() * (width + 80f) - 40f
        p.y = if (randomY) Random.nextFloat() * height else Random.nextFloat() * -120f - 10f

        // Random landing depth on liquid surface (spanning 20% to 95% of screen height)
        p.targetSurfaceY = Random.nextFloat() * (height * 0.75f) + (height * 0.20f)

        when (weatherType) {
            WeatherType.RAIN -> {
                p.vy = Random.nextFloat() * 10f + 20f
                p.vx = wind * 4f
                p.length = Random.nextFloat() * 14f + 16f
                p.size = Random.nextFloat() * 1.0f + 1.2f
                p.alpha = Random.nextFloat() * 0.4f + 0.35f
            }
            WeatherType.STORM -> {
                p.vy = Random.nextFloat() * 14f + 28f
                p.vx = wind * 8f
                p.length = Random.nextFloat() * 20f + 24f
                p.size = Random.nextFloat() * 1.2f + 1.5f
                p.alpha = Random.nextFloat() * 0.45f + 0.45f
            }
            WeatherType.SNOW -> {
                p.vy = Random.nextFloat() * 2.2f + 1.2f
                p.vx = wind * 1.5f
                p.length = 0f
                p.size = Random.nextFloat() * 3.5f + 2.0f
                p.alpha = Random.nextFloat() * 0.45f + 0.40f
                p.swayPhase = Random.nextFloat() * 6.28f
                p.swaySpeed = Random.nextFloat() * 1.5f + 1.0f
            }
            WeatherType.CLEAR -> {}
        }
    }

    private fun spawnSplash(x: Float, y: Float, maxR: Float) {
        for (splash in splashes) {
            if (!splash.active) {
                splash.x = x
                splash.y = y
                splash.radius = 2f
                splash.maxRadius = maxR
                splash.alpha = 0.70f
                splash.active = true
                break
            }
        }
    }

    fun draw(
        drawScope: DrawScope,
        weatherType: WeatherType,
        accentColor: Color
    ) {
        if (weatherType == WeatherType.CLEAR) return

        // Lightning atmospheric sheen
        if (lightningFlashAlpha > 0.01f) {
            drawScope.drawRect(
                color = Color.White.copy(alpha = lightningFlashAlpha)
            )
        }

        // Draw Splashes on surface
        val splashColor = if (weatherType == WeatherType.STORM) Color.White.copy(alpha = 0.55f) else accentColor.copy(alpha = 0.65f)
        for (splash in splashes) {
            if (!splash.active) continue
            drawScope.drawCircle(
                color = splashColor.copy(alpha = splash.alpha),
                radius = splash.radius,
                center = Offset(splash.x, splash.y),
                style = Stroke(width = 1.5f)
            )
        }

        // Draw Particles
        for (p in particles) {
            if (!p.active) continue

            when (weatherType) {
                WeatherType.RAIN, WeatherType.STORM -> {
                    val streakColor = if (weatherType == WeatherType.STORM) {
                        Color(0xFFE2E8F0).copy(alpha = p.alpha)
                    } else {
                        Color(0xFFBAE6FD).copy(alpha = p.alpha)
                    }

                    val dx = p.vx * 0.7f
                    val dy = p.length
                    drawScope.drawLine(
                        color = streakColor,
                        start = Offset(p.x, p.y),
                        end = Offset(p.x + dx, p.y + dy),
                        strokeWidth = p.size
                    )
                }
                WeatherType.SNOW -> {
                    // Soft glowing snowflake dot
                    drawScope.drawCircle(
                        color = Color.White.copy(alpha = p.alpha),
                        radius = p.size,
                        center = Offset(p.x, p.y)
                    )
                    // Faint outer aura
                    drawScope.drawCircle(
                        color = Color(0xFFE0F2FE).copy(alpha = p.alpha * 0.35f),
                        radius = p.size * 1.8f,
                        center = Offset(p.x, p.y)
                    )
                }
                WeatherType.CLEAR -> {}
            }
        }
    }
}
