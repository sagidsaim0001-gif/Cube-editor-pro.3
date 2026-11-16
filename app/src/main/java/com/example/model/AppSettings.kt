package com.example.model

enum class AppLanguage(val label: String) {
    HINGLISH("Hinglish"),
    ENGLISH("English"),
    HINDI("Hindi")
}

enum class PreviewQuality(val label: String, val textureSizePx: Int) {
    HIGH("High (512px)", 512),
    LOW("Fast (256px)", 256)
}

data class AppSettings(
    val amoledBlack: Boolean = false,
    val language: AppLanguage = AppLanguage.HINGLISH,
    val previewQuality: PreviewQuality = PreviewQuality.HIGH,
    val hapticsEnabled: Boolean = true,
    val defaultFps: Int = 30,
    val defaultResolution: ResolutionPreset = ResolutionPreset.FHD_1080,
    val snapToBeat: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val editorCoachDismissed: Boolean = false
)
