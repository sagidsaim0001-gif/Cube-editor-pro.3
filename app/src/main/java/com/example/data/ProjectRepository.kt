package com.example.data

import android.content.Context
import android.graphics.Bitmap
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val buildProject: () -> CubeProject
)

class ProjectRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val projectAdapter = moshi.adapter(CubeProject::class.java).indent("  ")

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    private fun projectDir(id: String): File = File(projectsRoot, id).apply { if (!exists()) mkdirs() }

    fun serializeProject(project: CubeProject): String = projectAdapter.toJson(project)

    fun deserializeProject(json: String): CubeProject? {
        return runCatching {
            val parsed = projectAdapter.fromJson(json) ?: return null
            migrateIfNeeded(parsed)
        }.getOrNull()
    }

    private fun migrateIfNeeded(project: CubeProject): CubeProject {
        var current = project
        if (current.schemaVersion < CURRENT_SCHEMA_VERSION) {
            current = current.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
        }
        return current
    }

    suspend fun listDrafts(): List<DraftSummary> = withContext(Dispatchers.IO) {
        val dirs = projectsRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        dirs.mapNotNull { dir ->
            val file = File(dir, "project.json")
            val project = loadFromWithBackups(dir, file) ?: return@mapNotNull null
            val thumb = File(dir, "thumbnail.png").takeIf { it.exists() }?.absolutePath
            DraftSummary(
                id = project.id,
                name = project.name,
                updatedAtMs = project.updatedAtMs,
                durationMs = project.computedDurationMs(),
                hasMusic = project.music != null,
                musicTitle = project.music?.title,
                cubeCount = project.cubes.size,
                lyricCount = project.lyrics.size,
                thumbnailPath = thumb
            )
        }.sortedByDescending { it.updatedAtMs }
    }

    suspend fun loadProject(id: String): CubeProject? = withContext(Dispatchers.IO) {
        val dir = File(projectsRoot, id)
        if (!dir.exists()) return@withContext null
        loadFromWithBackups(dir, File(dir, "project.json"))
    }

    private fun loadFromWithBackups(dir: File, primary: File): CubeProject? {
        val candidates = listOf(
            primary,
            File(dir, "project.json.bak1"),
            File(dir, "project.json.bak2"),
            File(dir, "project.json.bak3")
        )
        for (candidate in candidates) {
            if (candidate.exists()) {
                val parsed = runCatching { deserializeProject(candidate.readText()) }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        return null
    }

    suspend fun saveProject(project: CubeProject, thumbnail: Bitmap? = null) = withContext(Dispatchers.IO) {
        val dir = projectDir(project.id)
        val target = File(dir, "project.json")
        val tmp = File(dir, "project.json.tmp")
        val updated = project.copy(updatedAtMs = System.currentTimeMillis())

        val bak3 = File(dir, "project.json.bak3")
        val bak2 = File(dir, "project.json.bak2")
        val bak1 = File(dir, "project.json.bak1")

        if (target.exists()) {
            if (bak2.exists()) bak2.copyTo(bak3, overwrite = true)
            if (bak1.exists()) bak1.copyTo(bak2, overwrite = true)
            target.copyTo(bak1, overwrite = true)
        }

        tmp.writeText(serializeProject(updated))
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }

        if (thumbnail != null) {
            val thumbFile = File(dir, "thumbnail.png")
            val thumbTmp = File(dir, "thumbnail.png.tmp")
            FileOutputStream(thumbTmp).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            if (!thumbTmp.renameTo(thumbFile)) {
                thumbTmp.copyTo(thumbFile, overwrite = true)
                thumbTmp.delete()
            }
        }
    }

    suspend fun renameProject(id: String, newName: String) = withContext(Dispatchers.IO) {
        val project = loadProject(id) ?: return@withContext
        saveProject(project.copy(name = newName.trim().ifEmpty { project.name }))
    }

    suspend fun duplicateProject(id: String): CubeProject? = withContext(Dispatchers.IO) {
        val original = loadProject(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val copy = original.copy(
            id = newId,
            name = "${original.name} Copy",
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
        saveProject(copy)
        val origThumb = File(projectDir(id), "thumbnail.png")
        if (origThumb.exists()) {
            origThumb.copyTo(File(projectDir(newId), "thumbnail.png"), overwrite = true)
        }
        copy
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        File(projectsRoot, id).deleteRecursively()
    }

    suspend fun exportCubeProjArchive(project: CubeProject): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "project" }
        val zipFile = File(exportDir, "${safeName}.cubeproj")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("project.json"))
            zos.write(serializeProject(project).toByteArray())
            zos.closeEntry()
            val thumb = File(projectDir(project.id), "thumbnail.png")
            if (thumb.exists()) {
                zos.putNextEntry(ZipEntry("thumbnail.png"))
                zos.write(thumb.readBytes())
                zos.closeEntry()
            }
        }
        zipFile
    }

    suspend fun importCubeProjArchive(bytes: ByteArray): CubeProject? = withContext(Dispatchers.IO) {
        var json: String? = null
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "project.json") {
                    json = zis.readBytes().decodeToString()
                    break
                }
                entry = zis.nextEntry
            }
        }
        val parsed = json?.let { deserializeProject(it) } ?: return@withContext null
        val fresh = parsed.copy(
            id = UUID.randomUUID().toString(),
            name = "${parsed.name} (Imported)",
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
        saveProject(fresh)
        fresh
    }

    fun cacheSizeBytes(): Long {
        val cache = context.cacheDir
        return cache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun getStarterTemplates(): List<ProjectTemplate> = listOf(
        ProjectTemplate(
            id = "tpl_4face",
            title = "4-Face Lyric Cube",
            subtitle = "Classic Reels kinetic block spin",
            badge = "REELS 9:16"
        ) {
            val cubeId = UUID.randomUUID().toString()
            val cube = SceneCube(
                id = cubeId,
                name = "Main Cube",
                faces = SceneCube.defaultFacesMap(defaultSeeded = true)
            )
            val lines = listOf(
                LyricLine(text = "NIGHT LIGHTS GLOWING", startMs = 500, endMs = 3000, targetCubeId = cubeId, targetFace = FaceSide.FRONT, direction = RotationDirection.RIGHT),
                LyricLine(text = "DANCING ON THE BEAT", startMs = 3000, endMs = 5500, targetCubeId = cubeId, targetFace = FaceSide.RIGHT, direction = RotationDirection.RIGHT),
                LyricLine(text = "FEEL THE BASS DROP", startMs = 5500, endMs = 8000, targetCubeId = cubeId, targetFace = FaceSide.BACK, direction = RotationDirection.UP, extraSpins = 1),
                LyricLine(text = "NEVER STOP MOVING", startMs = 8000, endMs = 11000, targetCubeId = cubeId, targetFace = FaceSide.LEFT, direction = RotationDirection.RIGHT)
            )
            CubeProject(
                name = "4-Face Lyric Cube",
                manualDurationMs = 12_000L,
                cubes = listOf(cube),
                lyrics = lines,
                effects = EffectsConfig(beatPulseEnabled = true, vignetteStrength = 0.25f)
            )
        },
        ProjectTemplate(
            id = "tpl_tumble",
            title = "Tumble Intro + Stack",
            subtitle = "Dual locked cubes with diagonal spin",
            badge = "2 CUBES"
        ) {
            val parentId = UUID.randomUUID().toString()
            val childId = UUID.randomUUID().toString()
            val parentCube = SceneCube(
                id = parentId,
                name = "Hero Cube",
                faces = SceneCube.defaultFacesMap(defaultSeeded = true)
            )
            val childCube = SceneCube(
                id = childId,
                name = "Top Box",
                size = 0.68f,
                parentId = parentId,
                parentFace = FaceSide.TOP,
                faces = FaceSide.entries.associateWith { side ->
                    CubeFace(
                        side = side,
                        backgroundColorHex = "#151028",
                        borderColorHex = "#00D2FF",
                        layoutMode = FaceLayoutMode.AUTO_STACKED,
                        textItems = listOf(
                            FaceTextItem(text = "VIBES", corner = CornerAnchor.TOP_LEFT, colorHex = "#00D2FF", fontSizePx = 110f, fontId = "syne"),
                            FaceTextItem(text = "ONLY", corner = CornerAnchor.BOTTOM_RIGHT, colorHex = "#FF4081", fontSizePx = 104f, fontId = "anton")
                        )
                    )
                }
            )
            val keys = listOf(
                KeyframeTrackItem(cubeId = parentId, timeMs = 0L, rotXDeg = -18f, rotYDeg = -25f, scale = 0.85f),
                KeyframeTrackItem(cubeId = parentId, timeMs = 1600L, rotXDeg = 0f, rotYDeg = 0f, scale = 1.05f, easing = EasingType.OVERSHOOT),
                KeyframeTrackItem(cubeId = parentId, timeMs = 4000L, rotXDeg = 18f, rotYDeg = 180f, rotZDeg = 12f, scale = 1.0f),
                KeyframeTrackItem(cubeId = parentId, timeMs = 6800L, rotXDeg = 0f, rotYDeg = 360f, scale = 1.1f)
            )
            CubeProject(
                name = "Tumble Intro",
                manualDurationMs = 9_000L,
                cubes = listOf(parentCube, childCube),
                keyframes = keys,
                background = BackgroundConfig(type = BackgroundType.GRADIENT, primaryColorHex = "#0B0B12", secondaryColorHex = "#1E113A")
            )
        },
        ProjectTemplate(
            id = "tpl_hinglish",
            title = "Hinglish Beat Pulse",
            subtitle = "High-energy Hindi/Hinglish typography",
            badge = "HINGLISH"
        ) {
            val cubeId = UUID.randomUUID().toString()
            val faces = FaceSide.entries.associateWith { side ->
                val words = when (side) {
                    FaceSide.FRONT -> listOf(
                        FaceTextItem(text = "RAAT", colorHex = "#FFFFFF", fontSizePx = 115f, fontId = "anton", corner = CornerAnchor.TOP_LEFT),
                        FaceTextItem(text = "BHAR", colorHex = "#FFD600", fontSizePx = 105f, fontId = "bebas_neue", corner = CornerAnchor.BOTTOM_RIGHT)
                    )
                    FaceSide.RIGHT -> listOf(
                        FaceTextItem(text = "BASS", colorHex = "#00D2FF", fontSizePx = 120f, fontId = "syne", corner = CornerAnchor.TOP_LEFT),
                        FaceTextItem(text = "BAJEGA", colorHex = "#FF4081", fontSizePx = 92f, fontId = "oswald", corner = CornerAnchor.BOTTOM_LEFT)
                    )
                    FaceSide.BACK -> listOf(
                        FaceTextItem(text = "DIL", colorHex = "#FF5252", fontSizePx = 126f, fontId = "anton", corner = CornerAnchor.TOP_LEFT),
                        FaceTextItem(text = "SE", colorHex = "#FFFFFF", fontSizePx = 96f, fontId = "montserrat", corner = CornerAnchor.TOP_RIGHT),
                        FaceTextItem(text = "SUNO", colorHex = "#00E676", fontSizePx = 104f, fontId = "bebas_neue", corner = CornerAnchor.BOTTOM_RIGHT)
                    )
                    FaceSide.LEFT -> listOf(
                        FaceTextItem(text = "दिल", colorHex = "#FFD600", fontSizePx = 110f, fontId = "noto_sans_devanagari", uppercase = false, corner = CornerAnchor.TOP_LEFT),
                        FaceTextItem(text = "BEATS", colorHex = "#6C5CE7", fontSizePx = 102f, fontId = "anton", corner = CornerAnchor.BOTTOM_RIGHT)
                    )
                    else -> emptyList()
                }
                CubeFace(side = side, backgroundColorHex = "#06060B", layoutMode = FaceLayoutMode.AUTO_STACKED, textItems = words)
            }
            val cube = SceneCube(id = cubeId, name = "Hinglish Cube", faces = faces)
            val lyrics = listOf(
                LyricLine(text = "RAAT BHAR", startMs = 400, endMs = 2600, targetCubeId = cubeId, targetFace = FaceSide.FRONT, direction = RotationDirection.RIGHT),
                LyricLine(text = "BASS BAJEGA", startMs = 2600, endMs = 5000, targetCubeId = cubeId, targetFace = FaceSide.RIGHT, direction = RotationDirection.RIGHT),
                LyricLine(text = "DIL SE SUNO", startMs = 5000, endMs = 7400, targetCubeId = cubeId, targetFace = FaceSide.BACK, direction = RotationDirection.DIAGONAL),
                LyricLine(text = "दिल BEATS", startMs = 7400, endMs = 10000, targetCubeId = cubeId, targetFace = FaceSide.LEFT, direction = RotationDirection.UP)
            )
            CubeProject(
                name = "Hinglish Beat Pulse",
                manualDurationMs = 11_000L,
                cubes = listOf(cube),
                lyrics = lyrics,
                karaoke = KaraokeConfig(enabled = true),
                effects = EffectsConfig(beatPulseEnabled = true, chromaticAberrationEnabled = true, vignetteStrength = 0.3f)
            )
        }
    )
}
