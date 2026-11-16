package com.example.model

import java.util.UUID

const val CURRENT_SCHEMA_VERSION = 1

enum class AspectRatioPreset(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_16_9("16:9", 16, 9),
    RATIO_4_5("4:5", 4, 5)
}

enum class ResolutionPreset(val label: String, val shortSidePx: Int) {
    HD_720("720p", 720),
    FHD_1080("1080p", 1080)
}

enum class BackgroundType {
    BLACK,
    SOLID,
    GRADIENT,
    IMAGE
}

enum class FaceSide(val label: String) {
    FRONT("Front"),
    RIGHT("Right"),
    BACK("Back"),
    LEFT("Left"),
    TOP("Top"),
    BOTTOM("Bottom");

    val shortLabel: String
        get() = if (this == BOTTOM) "Bm" else label.take(1)

    companion object {
        val FOUR_SIDES = listOf(FRONT, RIGHT, BACK, LEFT)
    }
}

enum class CornerAnchor(val label: String) {
    TOP_LEFT("TL"),
    TOP_RIGHT("TR"),
    BOTTOM_LEFT("BL"),
    BOTTOM_RIGHT("BR")
}

enum class FaceLayoutMode(val label: String) {
    CORNERS("Corners"),
    FREE("Free"),
    AUTO_STACKED("Auto Stack"),
    AUTO_GRID("Auto Grid"),
    AUTO_POSTER("Poster")
}

enum class EasingType(val label: String) {
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In-Out"),
    OVERSHOOT("Back"),
    BOUNCE("Bounce")
}

enum class RotationDirection(val label: String) {
    AUTO_SHORTEST("Auto"),
    RIGHT("Right"),
    LEFT("Left"),
    UP("Up"),
    DOWN("Down"),
    DIAGONAL("Diagonal"),
    RANDOM("Random")
}

enum class LyricDisplayMode(val label: String) {
    ON_CUBE("Already on Face"),
    AUTO_FIT_FACE("Auto-Fit to Face"),
    KARAOKE_ONLY("Karaoke Caption")
}

enum class CreditIconType(val label: String) {
    MUSIC_NOTE("Music"),
    INSTAGRAM("Reels"),
    SPARKLE("Star"),
    CUBE("Cube"),
    NONE("None")
}

enum class CreditCorner(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right")
}

enum class MotionPresetType(val label: String) {
    SPIN_RIGHT("Spin Right"),
    SPIN_LEFT("Spin Left"),
    FLIP_UP("Flip Up"),
    FLIP_DOWN("Flip Down"),
    DIAGONAL_TUMBLE("Diagonal Tumble"),
    BARREL_ROLL("Barrel Roll"),
    BOUNCE_IN("Bounce In"),
    PUNCH_ZOOM("Punch Zoom"),
    SHAKE("Shake"),
    WHIP_PAN("Whip Pan"),
    SLOW_DRIFT("Slow Drift"),
    RANDOM_TUMBLE("Random Tumble")
}

data class BackgroundConfig(
    val type: BackgroundType = BackgroundType.BLACK,
    val primaryColorHex: String = "#000000",
    val secondaryColorHex: String = "#191233",
    val gradientAngleDeg: Float = 135f,
    val imageUriString: String? = null,
    val dimAlpha: Float = 0.25f
)

data class FaceTextItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "WORD",
    val corner: CornerAnchor = CornerAnchor.TOP_LEFT,
    val orderInCorner: Int = 0,
    val freeX: Float = 0.5f,
    val freeY: Float = 0.5f,
    val freeScale: Float = 1.0f,
    val freeRotationDeg: Float = 0f,
    val fontId: String = "bebas_neue",
    val bold: Boolean = true,
    val fontSizePx: Float = 72f,
    val colorHex: String = "#FFFFFF",
    val letterSpacing: Float = 0.02f,
    val lineHeightMult: Float = 1.05f,
    val horizontalScaleX: Float = 0.9f,
    val skewX: Float = 0f,
    val uppercase: Boolean = true,
    val strokeColorHex: String = "#000000",
    val strokeWidth: Float = 0f,
    val shadowEnabled: Boolean = true,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f
)

data class CubeFace(
    val side: FaceSide,
    val backgroundColorHex: String = "#08080D",
    val gradientColorHex: String? = null,
    val transparent: Boolean = false,
    val borderColorHex: String = "#2B2B3C",
    val borderWidth: Float = 3f,
    val defaultFontId: String = "bebas_neue",
    val defaultTextColorHex: String = "#FFFFFF",
    val layoutMode: FaceLayoutMode = FaceLayoutMode.CORNERS,
    val textItems: List<FaceTextItem> = emptyList()
)

data class SceneCube(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Cube 1",
    val size: Float = 1.0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotXDeg: Float = 0f,
    val rotYDeg: Float = 0f,
    val rotZDeg: Float = 0f,
    val scale: Float = 1.0f,
    val parentId: String? = null,
    val parentFace: FaceSide? = null,
    val hingeMode: Boolean = false,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val showTopBottom: Boolean = false,
    val faces: Map<FaceSide, CubeFace> = defaultFacesMap()
) {
    companion object {
        fun defaultFacesMap(defaultSeeded: Boolean = false): Map<FaceSide, CubeFace> {
            return FaceSide.entries.associateWith { side ->
                val bg = when (side) {
                    FaceSide.FRONT -> "#090910"
                    FaceSide.RIGHT -> "#0B0D18"
                    FaceSide.BACK -> "#100916"
                    FaceSide.LEFT -> "#091215"
                    FaceSide.TOP -> "#0E0E18"
                    FaceSide.BOTTOM -> "#0E0E18"
                }
                val items = if (defaultSeeded && side in FaceSide.FOUR_SIDES) {
                    when (side) {
                        FaceSide.FRONT -> listOf(
                            FaceTextItem(text = "CUBE", corner = CornerAnchor.TOP_LEFT, colorHex = "#FFFFFF", fontSizePx = 108f, fontId = "anton"),
                            FaceTextItem(text = "LYRIC", corner = CornerAnchor.TOP_RIGHT, colorHex = "#00D2FF", fontSizePx = 88f, fontId = "bebas_neue"),
                            FaceTextItem(text = "EDITOR", corner = CornerAnchor.BOTTOM_LEFT, colorHex = "#FF4081", fontSizePx = 94f, fontId = "oswald"),
                            FaceTextItem(text = "3D", corner = CornerAnchor.BOTTOM_RIGHT, colorHex = "#FFD600", fontSizePx = 112f, fontId = "syne")
                        )
                        FaceSide.RIGHT -> listOf(
                            FaceTextItem(text = "SYNC", corner = CornerAnchor.TOP_LEFT, colorHex = "#00E676", fontSizePx = 104f, fontId = "bebas_neue"),
                            FaceTextItem(text = "TO THE", corner = CornerAnchor.BOTTOM_LEFT, colorHex = "#FFFFFF", fontSizePx = 68f, fontId = "montserrat"),
                            FaceTextItem(text = "BEAT", corner = CornerAnchor.BOTTOM_RIGHT, colorHex = "#FF9100", fontSizePx = 110f, fontId = "anton")
                        )
                        FaceSide.BACK -> listOf(
                            FaceTextItem(text = "TUMBLE", corner = CornerAnchor.TOP_LEFT, colorHex = "#FF4081", fontSizePx = 92f, fontId = "syne"),
                            FaceTextItem(text = "SPIN", corner = CornerAnchor.TOP_RIGHT, colorHex = "#FFD600", fontSizePx = 96f, fontId = "bebas_neue"),
                            FaceTextItem(text = "DROP", corner = CornerAnchor.BOTTOM_RIGHT, colorHex = "#00D2FF", fontSizePx = 108f, fontId = "anton")
                        )
                        FaceSide.LEFT -> listOf(
                            FaceTextItem(text = "REELS", corner = CornerAnchor.TOP_LEFT, colorHex = "#6C5CE7", fontSizePx = 100f, fontId = "anton"),
                            FaceTextItem(text = "READY", corner = CornerAnchor.BOTTOM_RIGHT, colorHex = "#FFFFFF", fontSizePx = 96f, fontId = "bebas_neue")
                        )
                        else -> emptyList()
                    }
                } else emptyList()

                CubeFace(
                    side = side,
                    backgroundColorHex = bg,
                    layoutMode = if (defaultSeeded && items.isNotEmpty()) FaceLayoutMode.AUTO_STACKED else FaceLayoutMode.CORNERS,
                    textItems = items
                )
            }
        }
    }
}

data class KeyframeTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val cubeId: String,
    val timeMs: Long,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotXDeg: Float = 0f,
    val rotYDeg: Float = 0f,
    val rotZDeg: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val cameraZoom: Float = 1f,
    val cameraRollDeg: Float = 0f,
    val shutterAngleDeg: Float = 180f,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val hold: Boolean = false,
    val isGeneratedFromLyric: Boolean = false
)

data class LyricLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val targetCubeId: String,
    val targetFace: FaceSide = FaceSide.FRONT,
    val direction: RotationDirection = RotationDirection.RIGHT,
    val extraSpins: Int = 0,
    val anticipationOffsetMs: Long = -80L,
    val rotationDurationMs: Long = 450L,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val zoomPeak: Float = 1.15f,
    val zTiltDeg: Float = 10f,
    val motionBlurAmount: Float = 1f,
    val displayMode: LyricDisplayMode = LyricDisplayMode.AUTO_FIT_FACE
)

data class MusicTrackRef(
    val uriString: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val timelineOffsetMs: Long = 0L,
    val volume: Float = 1.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 500L,
    val contentHash: String = "",
    val waveformPeaks: List<Float> = emptyList(),
    val beatMarkersMs: List<Long> = emptyList()
)

data class CreditConfig(
    val enabled: Boolean = true,
    val handle: String = "@cubeeditor",
    val subtitle: String = "3D Kinetic Lyrics",
    val iconType: CreditIconType = CreditIconType.CUBE,
    val corner: CreditCorner = CreditCorner.BOTTOM_RIGHT,
    val opacity: Float = 0.85f,
    val scale: Float = 1.0f,
    val outroCenterEnabled: Boolean = true,
    val outroDurationMs: Long = 1800L
)

data class EffectsConfig(
    val shutterAngleDeg: Float = 180f,
    val motionBlurPreview: Boolean = true,
    val beatPulseEnabled: Boolean = true,
    val beatPulseStrength: Float = 0.08f,
    val glowEnabled: Boolean = false,
    val chromaticAberrationEnabled: Boolean = false,
    val vignetteStrength: Float = 0.2f,
    val filmGrainStrength: Float = 0f,
    val shakeStrength: Float = 0f
)

data class KaraokeConfig(
    val enabled: Boolean = false,
    val fontId: String = "plus_jakarta_sans",
    val fontSizeSp: Float = 20f,
    val activeColorHex: String = "#00D2FF",
    val inactiveColorHex: String = "#B8B8D0",
    val pillColorHex: String = "#9915151F",
    val bottomFraction: Float = 0.84f
)

data class ExportConfig(
    val resolution: ResolutionPreset = ResolutionPreset.FHD_1080,
    val fps: Int = 30,
    val motionBlurSubFrames: Int = 4,
    val includeAudio: Boolean = true,
    val audioBitrateKbps: Int = 192
)

data class CubeProject(
    val id: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val name: String = "Untitled Cube",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
    val manualDurationMs: Long = 15_000L,
    val cameraFovDeg: Float = 45f,
    val cameraDistance: Float = 4.2f,
    val cameraZoom: Float = 1.0f,
    val background: BackgroundConfig = BackgroundConfig(),
    val cubes: List<SceneCube> = listOf(
        SceneCube(
            name = "Cube 1",
            faces = SceneCube.defaultFacesMap(defaultSeeded = true)
        )
    ),
    val keyframes: List<KeyframeTrackItem> = emptyList(),
    val lyrics: List<LyricLine> = emptyList(),
    val music: MusicTrackRef? = null,
    val credit: CreditConfig = CreditConfig(),
    val effects: EffectsConfig = EffectsConfig(),
    val karaoke: KaraokeConfig = KaraokeConfig(),
    val exportConfig: ExportConfig = ExportConfig()
) {
    fun computedDurationMs(): Long {
        val musicEnd = music?.let { (it.trimEndMs - it.trimStartMs).coerceAtLeast(1000L) + it.timelineOffsetMs } ?: 0L
        val lyricEnd = lyrics.maxOfOrNull { it.endMs } ?: 0L
        val keyEnd = keyframes.maxOfOrNull { it.timeMs } ?: 0L
        return maxOf(manualDurationMs, musicEnd, lyricEnd, keyEnd, 5_000L)
    }

    fun outputDimensions(fastPreview: Boolean = false): Pair<Int, Int> {
        val shortSide = if (fastPreview) 480 else exportConfig.resolution.shortSidePx
        return when (aspectRatio) {
            AspectRatioPreset.RATIO_9_16 -> shortSide to (shortSide * 16 / 9)
            AspectRatioPreset.RATIO_16_9 -> (shortSide * 16 / 9) to shortSide
            AspectRatioPreset.RATIO_1_1 -> shortSide to shortSide
            AspectRatioPreset.RATIO_4_5 -> shortSide to (shortSide * 5 / 4)
        }.let { (w, h) ->
            (w - (w % 2)) to (h - (h % 2))
        }
    }
}

data class DraftSummary(
    val id: String,
    val name: String,
    val updatedAtMs: Long,
    val durationMs: Long,
    val hasMusic: Boolean,
    val musicTitle: String?,
    val cubeCount: Int,
    val lyricCount: Int,
    val thumbnailPath: String?
)
