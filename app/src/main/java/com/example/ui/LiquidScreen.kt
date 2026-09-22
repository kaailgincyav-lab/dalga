package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.LiquidSoundEngine
import com.example.simulation.InteractionMode
import com.example.simulation.LiquidPaletteType
import com.example.simulation.LiquidSettings
import com.example.simulation.LiquidSimulationEngine
import com.example.simulation.PerformanceQuality
import com.example.simulation.ViscosityPreset
import com.example.simulation.WeatherType
import com.example.weather.WeatherEngine
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

data class ActiveTouchIndicator(
    val id: Long,
    val x: Float,
    val y: Float
)

@Composable
fun LiquidScreen() {
    var settings by remember { mutableStateOf(LiquidSettings()) }
    val simulationEngine = remember { LiquidSimulationEngine() }
    val weatherEngine = remember { WeatherEngine() }
    val soundEngine = remember { LiquidSoundEngine() }
    val haptic = LocalHapticFeedback.current

    DisposableEffect(soundEngine) {
        soundEngine.start()
        onDispose {
            soundEngine.stop()
        }
    }

    LaunchedEffect(settings.soundEnabled) {
        soundEngine.soundEnabled = settings.soundEnabled
    }

    // High performance frame invalidation without UI recomposition!
    var frameTick by remember { mutableLongStateOf(0L) }
    var touchIndicators by remember { mutableStateOf(listOf<ActiveTouchIndicator>()) }
    var hasInteracted by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Dynamically adjust simulation grid based on aspect ratio and performance preset
        LaunchedEffect(screenWidthPx, screenHeightPx, settings.performanceQuality) {
            if (screenWidthPx > 0 && screenHeightPx > 0) {
                val gridW = settings.performanceQuality.baseGridW
                val aspect = screenHeightPx / screenWidthPx
                val gridH = (gridW * aspect).roundToInt().coerceIn(70, 260)
                simulationEngine.resize(gridW, gridH)
            }
        }

        // Real-time 60fps/120fps simulation & dynamic weather frame loop
        var lastThunderTime = remember { 0L }
        var lastRainDropSoundTime = remember { 0L }

        LaunchedEffect(screenWidthPx, screenHeightPx) {
            while (true) {
                withFrameNanos { _ ->
                    // 1. Advance fluid simulation physics
                    simulationEngine.stepSimulation(settings) { dropStrength ->
                        soundEngine.playDrop(viscosity = settings.viscosity, strength = dropStrength)
                    }

                    // 2. Advance atmospheric weather physics (rain, storm, snow)
                    if (screenWidthPx > 0f && screenHeightPx > 0f && settings.weatherType != WeatherType.CLEAR) {
                        val now = System.currentTimeMillis()
                        weatherEngine.update(
                            width = screenWidthPx,
                            height = screenHeightPx,
                            weatherType = settings.weatherType,
                            intensity = settings.weatherIntensity,
                            wind = settings.windSpeed
                        ) { normX, normY, isRain, strength ->
                            val waveStr = strength * settings.waveStrength
                            if (isRain) {
                                // Rain impact creates physical ripples on liquid
                                simulationEngine.addRipple(normX, normY, strength = waveStr, radiusCells = 2.8f)
                                if (now - lastRainDropSoundTime > 85) {
                                    lastRainDropSoundTime = now
                                    soundEngine.playDrop(viscosity = settings.viscosity, strength = strength * 0.7f)
                                }
                            } else {
                                // Snowflakes landing dissolve gently with micro-ripples
                                simulationEngine.addRipple(normX, normY, strength = waveStr * 0.45f, radiusCells = 1.8f)
                                if (Random.nextFloat() < 0.08f) {
                                    soundEngine.playSnowMelt()
                                }
                            }
                        }

                        // Thunder sound in storm mode
                        if (settings.weatherType == WeatherType.STORM && Random.nextFloat() < 0.003f && now - lastThunderTime > 4500) {
                            lastThunderTime = now
                            soundEngine.playThunder()
                        }
                    }

                    // Increment frame tick to trigger draw invalidation
                    frameTick++
                }
            }
        }

        // Fullscreen Liquid Simulation & Weather Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("liquid_canvas")
                .pointerInput(settings.interactionMode, settings.viscosity, settings.waveStrength) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hasInteracted = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        val normX = down.position.x / size.width
                        val normY = down.position.y / size.height

                        when (settings.interactionMode) {
                            InteractionMode.VORTEX -> {
                                simulationEngine.addVortex(normX, normY, strength = 2.0f)
                                soundEngine.playDrop(viscosity = settings.viscosity, strength = settings.waveStrength * 1.2f)
                            }
                            InteractionMode.FOUNTAIN -> {
                                simulationEngine.addRipple(normX, normY, strength = 1.8f, radiusCells = 4.2f)
                                soundEngine.playDrop(viscosity = settings.viscosity, strength = settings.waveStrength)
                            }
                            else -> {
                                simulationEngine.addRipple(normX, normY, strength = 1.6f)
                                soundEngine.playDrop(viscosity = settings.viscosity, strength = settings.waveStrength)
                            }
                        }

                        val lastPositions = mutableMapOf<Long, Offset>()
                        lastPositions[down.id.value] = down.position
                        touchIndicators = listOf(ActiveTouchIndicator(down.id.value, down.position.x, down.position.y))

                        var holdCount = 0

                        do {
                            val event = awaitPointerEvent()
                            holdCount++

                            // Continuous fountain emission while holding
                            if (settings.interactionMode == InteractionMode.FOUNTAIN && holdCount % 4 == 0) {
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        val fx = change.position.x / size.width
                                        val fy = change.position.y / size.height
                                        simulationEngine.addRipple(fx, fy, strength = 1.4f, radiusCells = 3.6f)
                                        soundEngine.playRippleWave(viscosity = settings.viscosity, speed = 1.2f)
                                    }
                                }
                            }

                            for (change in event.changes) {
                                if (change.pressed) {
                                    val id = change.id.value
                                    val currentPos = change.position
                                    val prevPos = lastPositions[id] ?: currentPos

                                    val dx = currentPos.x - prevPos.x
                                    val dy = currentPos.y - prevPos.y
                                    val dist = hypot(dx, dy)

                                    if (dist > 1.2f || change.positionChange() != Offset.Zero) {
                                        val normX0 = prevPos.x / size.width
                                        val normY0 = prevPos.y / size.height
                                        val normX1 = currentPos.x / size.width
                                        val normY1 = currentPos.y / size.height

                                        val velocityBonus = (dist / 18f).coerceIn(0.8f, 2.8f)

                                        if (settings.interactionMode == InteractionMode.VORTEX) {
                                            simulationEngine.addVortex(normX1, normY1, strength = 1.6f * velocityBonus)
                                        } else {
                                            simulationEngine.addDragLine(
                                                normX0, normY0,
                                                normX1, normY1,
                                                strength = 1.0f * velocityBonus
                                            )
                                        }

                                        soundEngine.playRippleWave(
                                            viscosity = settings.viscosity,
                                            speed = velocityBonus
                                        )
                                    }

                                    lastPositions[id] = currentPos
                                    change.consume()
                                }
                            }

                            // Update active touch locations for visual glow
                            val activeList = mutableListOf<ActiveTouchIndicator>()
                            for (change in event.changes) {
                                if (change.pressed) {
                                    activeList.add(ActiveTouchIndicator(change.id.value, change.position.x, change.position.y))
                                }
                            }
                            touchIndicators = activeList
                        } while (event.changes.any { it.pressed })

                        touchIndicators = emptyList()
                        lastPositions.clear()
                    }
                }
        ) {
            // Read frameTick inside DrawScope to schedule redraw without recomposition
            val tick = frameTick
            val bmp = simulationEngine.composeBitmap

            // 1. Draw Liquid Simulation Surface
            drawImage(
                image = bmp,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                dstOffset = IntOffset.Zero,
                filterQuality = FilterQuality.Medium
            )

            // 2. Draw Atmospheric Weather Particles (Rain / Storm / Snow & Splash Rings)
            weatherEngine.draw(
                drawScope = this,
                weatherType = settings.weatherType,
                accentColor = settings.palette.accentColor
            )

            // 3. Draw glowing rings beneath active touch contacts
            if (settings.touchGlow) {
                for (touch in touchIndicators) {
                    drawCircle(
                        color = settings.palette.accentColor.copy(alpha = 0.45f),
                        radius = 32.dp.toPx(),
                        center = Offset(touch.x, touch.y),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.65f),
                        radius = 8.dp.toPx(),
                        center = Offset(touch.x, touch.y)
                    )
                }
            }
        }

        // Zen Mode Exit Pill (When in Zen Mode, only show a minimalist return button)
        if (settings.zenMode) {
            Surface(
                onClick = { settings = settings.copy(zenMode = false) },
                color = Color.Black.copy(alpha = 0.60f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .testTag("exit_zen_mode_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Zen Modundan Çık",
                        tint = settings.palette.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Zen Modu", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            // Top Status Bar Overlay (Title, Weather, Freeze, Sound, Clear, Controls Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(settings.palette.previewColor)
                        )
                        Text(
                            text = when {
                                settings.isFrozen -> "Donduruldu"
                                settings.weatherType != WeatherType.CLEAR -> "Sıvı & ${settings.weatherType.title}"
                                else -> "Sıvı Dalgalanma"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quick Weather Cycle Toggle
                    IconButton(
                        onClick = {
                            val nextWeather = when (settings.weatherType) {
                                WeatherType.CLEAR -> WeatherType.RAIN
                                WeatherType.RAIN -> WeatherType.STORM
                                WeatherType.STORM -> WeatherType.SNOW
                                WeatherType.SNOW -> WeatherType.CLEAR
                            }
                            settings = settings.copy(weatherType = nextWeather)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier
                            .testTag("toggle_weather_button")
                            .clip(CircleShape)
                            .background(
                                if (settings.weatherType != WeatherType.CLEAR)
                                    settings.palette.accentColor.copy(alpha = 0.25f)
                                else Color.Black.copy(alpha = 0.6f)
                            )
                            .border(
                                1.dp,
                                if (settings.weatherType != WeatherType.CLEAR)
                                    settings.palette.accentColor
                                else Color.White.copy(alpha = 0.18f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = when (settings.weatherType) {
                                WeatherType.CLEAR -> Icons.Default.WbSunny
                                WeatherType.RAIN -> Icons.Default.WaterDrop
                                WeatherType.STORM -> Icons.Default.Thunderstorm
                                WeatherType.SNOW -> Icons.Default.AcUnit
                            },
                            contentDescription = "Hava Durumu: ${settings.weatherType.title}",
                            tint = if (settings.weatherType != WeatherType.CLEAR) settings.palette.accentColor else Color.White
                        )
                    }

                    // Freeze / Unfreeze simulation
                    IconButton(
                        onClick = {
                            settings = settings.copy(isFrozen = !settings.isFrozen)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier
                            .testTag("toggle_freeze_button")
                            .clip(CircleShape)
                            .background(if (settings.isFrozen) settings.palette.accentColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, if (settings.isFrozen) settings.palette.accentColor else Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (settings.isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (settings.isFrozen) "Oynat" else "Dondur",
                            tint = if (settings.isFrozen) settings.palette.accentColor else Color.White
                        )
                    }

                    // Sound Mute/Unmute Quick Toggle
                    IconButton(
                        onClick = {
                            settings = settings.copy(soundEnabled = !settings.soundEnabled)
                        },
                        modifier = Modifier
                            .testTag("toggle_sound_button")
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (settings.soundEnabled) "Sesi Kapat" else "Sesi Aç",
                            tint = if (settings.soundEnabled) settings.palette.accentColor else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Clear Surface Action
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            soundEngine.playCalmSound()
                            simulationEngine.clearSurface()
                        },
                        modifier = Modifier.testTag("clear_surface_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Yüzeyi Temizle",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Temizle", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Toggle Controls Visibility
                    IconButton(
                        onClick = {
                            settings = settings.copy(showControls = !settings.showControls)
                        },
                        modifier = Modifier
                            .testTag("toggle_controls_button")
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (settings.showControls) Icons.Default.KeyboardArrowDown else Icons.Default.Tune,
                            contentDescription = if (settings.showControls) "Kontrolleri Gizle" else "Kontrolleri Göster",
                            tint = Color.White
                        )
                    }
                }
            }

            // Onboarding Hint Overlay
            AnimatedVisibility(
                visible = !hasInteracted,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.70f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = settings.palette.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Dokunun ve Parmağınızı Kaydırın",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Yağmur ve kar damlacıkları dalgalarınızla etkileşir",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Collapsible Liquid Controls Bottom Card
            AnimatedVisibility(
                visible = settings.showControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LiquidControlsSheet(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onClearSurface = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        soundEngine.playCalmSound()
                        simulationEngine.clearSurface()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun LiquidControlsSheet(
    settings: LiquidSettings,
    onSettingsChange: (LiquidSettings) -> Unit,
    onClearSurface: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .widthIn(max = 640.dp)
            .testTag("controls_card"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xDD0D1117)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Drag Indicator Handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            // 1. Dinamik Hava Durumu Modu (Dinamik Yağmur & Kar)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dinamik Hava Durumu",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = settings.weatherType.subtitle,
                        color = settings.palette.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeatherType.values().forEach { weather ->
                        val isSelected = settings.weatherType == weather
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSettingsChange(settings.copy(weatherType = weather)) },
                            label = { Text(weather.title, fontSize = 12.sp) },
                            leadingIcon = {
                                val icon = when (weather) {
                                    WeatherType.CLEAR -> Icons.Default.WbSunny
                                    WeatherType.RAIN -> Icons.Default.WaterDrop
                                    WeatherType.STORM -> Icons.Default.Thunderstorm
                                    WeatherType.SNOW -> Icons.Default.AcUnit
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = settings.palette.accentColor.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = settings.palette.accentColor,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.12f),
                                selectedBorderColor = settings.palette.accentColor
                            )
                        )
                    }
                }

                // Weather Intensity & Wind Controls (Shown when weather is active)
                if (settings.weatherType != WeatherType.CLEAR) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Yağış Yoğunluğu: %${(settings.weatherIntensity * 100).roundToInt()}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                            Slider(
                                value = settings.weatherIntensity,
                                onValueChange = { onSettingsChange(settings.copy(weatherIntensity = it)) },
                                valueRange = 0.2f..1.0f,
                                modifier = Modifier.testTag("weather_intensity_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = settings.palette.accentColor,
                                    activeTrackColor = settings.palette.previewColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                )
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val windLabel = when {
                                settings.windSpeed < -0.2f -> "Batı Rüzgarı"
                                settings.windSpeed > 0.2f -> "Doğu Rüzgarı"
                                else -> "Durgun Hava"
                            }
                            Text(
                                text = "Rüzgar: $windLabel",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                            Slider(
                                value = settings.windSpeed,
                                onValueChange = { onSettingsChange(settings.copy(windSpeed = it)) },
                                valueRange = -1.0f..1.0f,
                                modifier = Modifier.testTag("wind_speed_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = settings.palette.accentColor,
                                    activeTrackColor = settings.palette.previewColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }

            // 2. Etkileşim Modları (Interaction Modes)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Etkileşim Modu",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InteractionMode.values().forEach { mode ->
                        val isSelected = settings.interactionMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSettingsChange(settings.copy(interactionMode = mode)) },
                            label = { Text(mode.title, fontSize = 12.sp) },
                            leadingIcon = {
                                val icon = when (mode) {
                                    InteractionMode.WAVE -> Icons.Default.Waves
                                    InteractionMode.VORTEX -> Icons.Default.Cyclone
                                    InteractionMode.FOUNTAIN -> Icons.Default.Water
                                    InteractionMode.RAINSTORM -> Icons.Default.Grain
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = settings.palette.accentColor.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = settings.palette.accentColor,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.12f),
                                selectedBorderColor = settings.palette.accentColor
                            )
                        )
                    }
                }
            }

            // 3. Sıvı Türü & Viskozite
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Viskozite & Akışkanlık",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            settings.viscosity < 0.25f -> "Akışkan Su"
                            settings.viscosity < 0.45f -> "Gliserin"
                            settings.viscosity < 0.70f -> "Kıvamlı Yağ"
                            else -> "Yoğun Bal / Jel"
                        },
                        color = settings.palette.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViscosityPreset.values().forEach { preset ->
                        val isSelected = kotlin.math.abs(settings.viscosity - preset.value) < 0.08f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSettingsChange(settings.copy(viscosity = preset.value)) },
                            label = { Text(preset.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = settings.palette.accentColor.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.12f),
                                selectedBorderColor = settings.palette.accentColor
                            )
                        )
                    }
                }

                Slider(
                    value = settings.viscosity,
                    onValueChange = { onSettingsChange(settings.copy(viscosity = it)) },
                    valueRange = 0.05f..0.85f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("viscosity_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = settings.palette.accentColor,
                        activeTrackColor = settings.palette.previewColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }

            // 4. Dalga Gücü & Yüzey Gerilimi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Dalga Gücü: ${(settings.waveStrength * 10).roundToInt() / 10f}x",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = settings.waveStrength,
                        onValueChange = { onSettingsChange(settings.copy(waveStrength = it)) },
                        valueRange = 0.4f..2.8f,
                        modifier = Modifier.testTag("wave_strength_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = settings.palette.accentColor,
                            activeTrackColor = settings.palette.previewColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Yüzey Gerilimi: ${(settings.surfaceTension * 10).roundToInt() / 10f}x",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = settings.surfaceTension,
                        onValueChange = { onSettingsChange(settings.copy(surfaceTension = it)) },
                        valueRange = 0.5f..1.8f,
                        modifier = Modifier.testTag("surface_tension_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = settings.palette.accentColor,
                            activeTrackColor = settings.palette.previewColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }

            // 5. Renk Haritası (10 Palettes)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Renk Haritası",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidPaletteType.values().forEach { palette ->
                        val isSelected = settings.palette == palette
                        ColorPaletteCard(
                            palette = palette,
                            isSelected = isSelected,
                            onClick = { onSettingsChange(settings.copy(palette = palette)) }
                        )
                    }
                }
            }

            // 6. Performans & Akıcılık Kalitesi
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = settings.palette.accentColor, modifier = Modifier.size(16.dp))
                        Text("Performans & Akıcılık", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(settings.performanceQuality.description, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PerformanceQuality.values().forEach { perf ->
                        val isSelected = settings.performanceQuality == perf
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSettingsChange(settings.copy(performanceQuality = perf)) },
                            label = { Text(perf.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = settings.palette.accentColor.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.12f),
                                selectedBorderColor = settings.palette.accentColor
                            )
                        )
                    }
                }
            }

            // 7. Ek Özellikler & Toggles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Su Sesi Efektleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = settings.palette.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Su Sesi Efektleri", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            val soundSubtext = when {
                                settings.weatherType == WeatherType.STORM -> "Sağanak yağmur ve gök gürültüsü"
                                settings.weatherType == WeatherType.SNOW -> "Hafif fısıltı ve kar erime tınıları"
                                settings.viscosity < 0.25f -> "Tiz ve berrak su damlası sesleri"
                                settings.viscosity < 0.55f -> "Yumuşak ve kıvamlı sıvı tınıları"
                                else -> "Derin, tok ve ağır jel sesleri"
                            }
                            Text(soundSubtext, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(soundEnabled = it)) },
                        modifier = Modifier.testTag("sound_enabled_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = settings.palette.previewColor,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                // Zen Modu (Tam Ekran Arınma)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = settings.palette.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Zen Modu", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Tüm butonları gizle ve sıvıya odaklan", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.zenMode,
                        onCheckedChange = { onSettingsChange(settings.copy(zenMode = it)) },
                        modifier = Modifier.testTag("zen_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = settings.palette.previewColor,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPaletteCard(
    palette: LiquidPaletteType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) palette.accentColor else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .width(108.dp)
            .testTag("palette_${palette.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(palette.deepColor),
                                Color(palette.surfaceColor),
                                Color(palette.crestColor),
                                Color(palette.highlightColor)
                            )
                        )
                    )
            )

            Text(
                text = palette.title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
