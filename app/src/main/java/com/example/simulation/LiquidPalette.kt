package com.example.simulation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class LiquidPaletteType(
    val title: String,
    val description: String,
    val previewColor: Color,
    val accentColor: Color,
    val deepColor: Int,
    val surfaceColor: Int,
    val crestColor: Int,
    val highlightColor: Int
) {
    OCEAN(
        title = "Okyanus",
        description = "Derin mavi sular ve akuamarin dalga tepeleri",
        previewColor = Color(0xFF0284C7),
        accentColor = Color(0xFF38BDF8),
        deepColor = 0xFF031525.toInt(),
        surfaceColor = 0xFF075985.toInt(),
        crestColor = 0xFF38BDF8.toInt(),
        highlightColor = 0xFFE0F2FE.toInt()
    ),
    GOLD(
        title = "Sıvı Altın",
        description = "Zengin kehribar ve ışıldayan altın yansımaları",
        previewColor = Color(0xFFF59E0B),
        accentColor = Color(0xFFFCD34D),
        deepColor = 0xFF1C1305.toInt(),
        surfaceColor = 0xFF92400E.toInt(),
        crestColor = 0xFFFBBF24.toInt(),
        highlightColor = 0xFFFFFBEB.toInt()
    ),
    CYBER_NEON(
        title = "Neon Siber",
        description = "Elektrik moru zemin ve parlayan camgöbeği dalgalar",
        previewColor = Color(0xFFD946EF),
        accentColor = Color(0xFF06B6D4),
        deepColor = 0xFF18032E.toInt(),
        surfaceColor = 0xFF7E22CE.toInt(),
        crestColor = 0xFF06B6D4.toInt(),
        highlightColor = 0xFFE0E7FF.toInt()
    ),
    EMERALD(
        title = "Zümrüt Vaha",
        description = "Derin yeşim ve ferahlatıcı nane yeşili dalgalanma",
        previewColor = Color(0xFF10B981),
        accentColor = Color(0xFF6EE7B7),
        deepColor = 0xFF042017.toInt(),
        surfaceColor = 0xFF065F46.toInt(),
        crestColor = 0xFF34D399.toInt(),
        highlightColor = 0xFFECFDF5.toInt()
    ),
    MAGMA(
        title = "Lav Magma",
        description = "Kızgın obsidyen ve parıldayan akkor dalga akışı",
        previewColor = Color(0xFFEF4444),
        accentColor = Color(0xFFF97316),
        deepColor = 0xFF1F0606.toInt(),
        surfaceColor = 0xFF991B1B.toInt(),
        crestColor = 0xFFF97316.toInt(),
        highlightColor = 0xFFFEF08A.toInt()
    ),
    AURORA(
        title = "Kutup Işığı",
        description = "Gece göğü çivit mavisi ve menekşe kutup ışıması",
        previewColor = Color(0xFF8B5CF6),
        accentColor = Color(0xFFA78BFA),
        deepColor = 0xFF0B0F2A.toInt(),
        surfaceColor = 0xFF4338CA.toInt(),
        crestColor = 0xFF8B5CF6.toInt(),
        highlightColor = 0xFFEDE9FE.toInt()
    ),
    MERCURY(
        title = "Saf Cıva",
        description = "Metalik krom ve parlak gümüş ayna yüzeyi",
        previewColor = Color(0xFF94A3B8),
        accentColor = Color(0xFFE2E8F0),
        deepColor = 0xFF0F172A.toInt(),
        surfaceColor = 0xFF334155.toInt(),
        crestColor = 0xFF94A3B8.toInt(),
        highlightColor = 0xFFFFFFFF.toInt()
    ),
    COSMIC_VIOLET(
        title = "Kozmik Mor",
        description = "Nebula moru zemin ve parlayan galaksi beyazı dalgalar",
        previewColor = Color(0xFFA855F7),
        accentColor = Color(0xFFF472B6),
        deepColor = 0xFF1E0B36.toInt(),
        surfaceColor = 0xFF581C87.toInt(),
        crestColor = 0xFFA855F7.toInt(),
        highlightColor = 0xFFFDF4FF.toInt()
    ),
    RADIOACTIVE_GREEN(
        title = "Siber Matriks",
        description = "Biyo-ışıltılı fosforlu zümrüt ve elektrik yeşili akış",
        previewColor = Color(0xFF22C55E),
        accentColor = Color(0xFF4ADE80),
        deepColor = 0xFF031A0B.toInt(),
        surfaceColor = 0xFF14532D.toInt(),
        crestColor = 0xFF22C55E.toInt(),
        highlightColor = 0xFFF0FDF4.toInt()
    ),
    SUNSET_CORAL(
        title = "Gün Batımı",
        description = "Sıcak mercan pembesi, altın şafak ve lavanta gölgeleri",
        previewColor = Color(0xFFFB7185),
        accentColor = Color(0xFFFBBF24),
        deepColor = 0xFF280A18.toInt(),
        surfaceColor = 0xFF9F1239.toInt(),
        crestColor = 0xFFFB7185.toInt(),
        highlightColor = 0xFFFEF3C7.toInt()
    );

    val lut: IntArray by lazy {
        generateLut(deepColor, surfaceColor, crestColor, highlightColor)
    }

    companion object {
        private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
            val a1 = (c1 ushr 24) and 0xFF
            val r1 = (c1 ushr 16) and 0xFF
            val g1 = (c1 ushr 8) and 0xFF
            val b1 = c1 and 0xFF

            val a2 = (c2 ushr 24) and 0xFF
            val r2 = (c2 ushr 16) and 0xFF
            val g2 = (c2 ushr 8) and 0xFF
            val b2 = c2 and 0xFF

            val a = (a1 + (a2 - a1) * t).toInt().coerceIn(0, 255)
            val r = (r1 + (r2 - r1) * t).toInt().coerceIn(0, 255)
            val g = (g1 + (g2 - g1) * t).toInt().coerceIn(0, 255)
            val b = (b1 + (b2 - b1) * t).toInt().coerceIn(0, 255)

            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun generateLut(
            deep: Int,
            surface: Int,
            crest: Int,
            highlight: Int
        ): IntArray {
            val size = 512
            val lut = IntArray(size)

            val p0 = 0
            val p1 = 160
            val p2 = 340
            val p3 = 511

            for (i in 0 until size) {
                lut[i] = when {
                    i < p1 -> {
                        val t = i.toFloat() / p1
                        lerpColor(deep, surface, t)
                    }
                    i < p2 -> {
                        val t = (i - p1).toFloat() / (p2 - p1)
                        lerpColor(surface, crest, t)
                    }
                    else -> {
                        val t = (i - p2).toFloat() / (p3 - p2)
                        lerpColor(crest, highlight, t)
                    }
                }
            }
            return lut
        }
    }
}
