package com.example.simulation

enum class InteractionMode(val title: String, val subtitle: String) {
    WAVE("Dalga", "Klasik dinamik dalga yayılımı"),
    VORTEX("Girdap", "Dönen burgu ve sarmal dalgalar"),
    FOUNTAIN("Fıskiye", "Basılı tutulan noktadan sürekli akış"),
    RAINSTORM("Fırtına", "Tropikal çoklu su damlası yağmuru")
}

enum class WeatherType(val title: String, val subtitle: String) {
    CLEAR("Açık", "Sakin hava, saf sıvı yüzeyi"),
    RAIN("Yağmur", "Yüzeyde dalgalar açan yağmur damlaları"),
    STORM("Fırtına", "Yoğun sağanak ve şimşek parıltıları"),
    SNOW("Kar", "Sıvı üzerinde süzülen ve eriyen kar taneleri")
}

enum class PerformanceQuality(val label: String, val baseGridW: Int, val description: String) {
    HIGH_FPS("120 FPS Akıcı", 68, "Sıfır gecikme, ultra akıcı ve serin"),
    BALANCED("Dengeli", 84, "İdeal çözünürlük ve akıcılık dengesi"),
    HIGH_DETAIL("Yüksek Çözünürlük", 104, "Maksimum mikro-dalga netliği")
}

data class LiquidSettings(
    val viscosity: Float = 0.28f,         // 0.05 (çok akışkan su) .. 0.85 (yoğun jel/bal)
    val waveStrength: Float = 1.4f,       // 0.4 (nazik) .. 2.8 (güçlü dalgalar)
    val surfaceTension: Float = 1.0f,     // 0.5 (yumuşak sönüm) .. 1.8 (esnek geri sekme)
    val palette: LiquidPaletteType = LiquidPaletteType.OCEAN,
    val interactionMode: InteractionMode = InteractionMode.WAVE,
    val weatherType: WeatherType = WeatherType.RAIN,   // Dinamik hava durumu (Yağmur / Kar / Fırtına)
    val weatherIntensity: Float = 0.70f,  // 0.2 .. 1.0 (yağış yoğunluğu)
    val windSpeed: Float = 0.25f,         // -1.0 .. 1.0 (rüzgar şiddeti)
    val performanceQuality: PerformanceQuality = PerformanceQuality.HIGH_FPS,
    val isFrozen: Boolean = false,        // Dalgaları dondur (Zamanı durdurma)
    val zenMode: Boolean = false,         // Tam ekran saf sıvı meditasyon modu
    val autoRain: Boolean = false,        // Kendiliğinden rastgele su damlaları
    val soundEnabled: Boolean = true,     // Viskoziteye duyarlı su ses efektleri
    val touchGlow: Boolean = true,        // Dokunma noktasında sıvı ışıltısı
    val showControls: Boolean = true      // Ayar panelini gizle/göster
)

enum class ViscosityPreset(val label: String, val value: Float) {
    WATER("Su", 0.15f),
    GLYCERIN("Gliserin", 0.35f),
    OIL("Yağ", 0.55f),
    HONEY("Jel / Bal", 0.82f)
}

