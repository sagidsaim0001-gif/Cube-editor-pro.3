package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioClockPlayer
import com.example.audio.AudioLibrary
import com.example.audio.DeviceSong
import com.example.audio.LyricsParser
import com.example.data.ProjectRepository
import com.example.data.ProjectTemplate
import com.example.data.SettingsDataStore
import com.example.engine.CubePose
import com.example.engine.FaceTextureCache
import com.example.engine.KeyframeEvaluator
import com.example.engine.SceneRenderer
import com.example.engine.TextAutoFit
import com.example.export.ExportForegroundService
import com.example.export.ExportProgress
import com.example.export.ExportResult
import com.example.export.VideoExporter
import com.example.model.*
import com.example.ui.theme.KineticPaletteHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

enum class EditorTool(val label: String) {
    MUSIC("Music"),
    TEXT("Text"),
    FONTS("Fonts"),
    COLOR("Color"),
    ADD_BOX("Add Box"),
    LYRICS("Lyrics"),
    KEYFRAME("Keyframe"),
    MOTION("Motion"),
    BACKGROUND("Background"),
    CREDIT("Credit"),
    EXPORT("Export")
}

enum class AxisLock(val label: String) {
    FREE("Free"),
    X("X"),
    Y("Y"),
    Z("Z")
}

enum class ColorApplyTarget(val label: String) {
    THIS_FACE("This Face"),
    FOUR_SIDES("4 Sides"),
    WHOLE_CUBE("Whole Cube"),
    ALL_CUBES("All Cubes")
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progress: ExportProgress) : ExportUiState
    data class Success(val result: ExportResult, val shareUri: Uri?) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

class CubeEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val projectRepo = ProjectRepository(appContext)
    private val settingsStore = SettingsDataStore(appContext)
    private val audioPlayer = AudioClockPlayer(appContext)
    private val previewPlayer = AudioClockPlayer(appContext)

    val faceTextureCache = FaceTextureCache(48)
    val templates: List<ProjectTemplate> = projectRepo.getStarterTemplates()

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _drafts = MutableStateFlow<List<DraftSummary>>(emptyList())
    val drafts: StateFlow<List<DraftSummary>> = _drafts.asStateFlow()

    private val _project = MutableStateFlow<CubeProject?>(null)
    val project: StateFlow<CubeProject?> = _project.asStateFlow()

    private val _bgBitmap = MutableStateFlow<Bitmap?>(null)
    val bgBitmap: StateFlow<Bitmap?> = _bgBitmap.asStateFlow()

    private val _timeMs = MutableStateFlow(0L)
    val timeMs: StateFlow<Long> = _timeMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val undoStack = ArrayDeque<CubeProject>()
    private val redoStack = ArrayDeque<CubeProject>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _selectedCubeId = MutableStateFlow<String?>(null)
    val selectedCubeId: StateFlow<String?> = _selectedCubeId.asStateFlow()

    private val _selectedFace = MutableStateFlow(FaceSide.FRONT)
    val selectedFace: StateFlow<FaceSide> = _selectedFace.asStateFlow()

    private val _selectedWordId = MutableStateFlow<String?>(null)
    val selectedWordId: StateFlow<String?> = _selectedWordId.asStateFlow()

    private val _selectedLyricId = MutableStateFlow<String?>(null)
    val selectedLyricId: StateFlow<String?> = _selectedLyricId.asStateFlow()

    private val _selectedKeyframeId = MutableStateFlow<String?>(null)
    val selectedKeyframeId: StateFlow<String?> = _selectedKeyframeId.asStateFlow()

    private val _activeTool = MutableStateFlow<EditorTool?>(null)
    val activeTool: StateFlow<EditorTool?> = _activeTool.asStateFlow()

    private val _axisLock = MutableStateFlow(AxisLock.FREE)
    val axisLock: StateFlow<AxisLock> = _axisLock.asStateFlow()

    private val _showSafeGuides = MutableStateFlow(false)
    val showSafeGuides: StateFlow<Boolean> = _showSafeGuides.asStateFlow()

    private val _deviceSongs = MutableStateFlow<List<DeviceSong>>(emptyList())
    val deviceSongs: StateFlow<List<DeviceSong>> = _deviceSongs.asStateFlow()

    private val _previewingSongId = MutableStateFlow<Long?>(null)
    val previewingSongId: StateFlow<Long?> = _previewingSongId.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var autoSaveJob: Job? = null
    private var clockJob: Job? = null
    private var exportJob: Job? = null
    private var previewStopJob: Job? = null

    init {
        refreshDrafts()
        audioPlayer.onError = { msg -> showToast(msg) }
    }

    override fun onCleared() {
        super.onCleared()
        clockJob?.cancel()
        autoSaveJob?.cancel()
        exportJob?.cancel()
        audioPlayer.release()
        previewPlayer.release()
        faceTextureCache.clear()
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refreshDrafts() {
        viewModelScope.launch {
            _drafts.value = projectRepo.listDrafts()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsStore.update { it.copy(onboardingCompleted = true) }
        }
    }

    fun dismissEditorCoach() {
        viewModelScope.launch {
            settingsStore.update { it.copy(editorCoachDismissed = true) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    suspend fun createNewProject(
        name: String,
        aspectRatio: AspectRatioPreset,
        resolution: ResolutionPreset,
        fps: Int,
        bgType: BackgroundType,
        bgColorHex: String
    ): String {
        val project = CubeProject(
            name = name.trim().ifEmpty { "Untitled Cube" },
            aspectRatio = aspectRatio,
            background = BackgroundConfig(
                type = bgType,
                primaryColorHex = bgColorHex,
                secondaryColorHex = "#1E113A"
            ),
            exportConfig = ExportConfig(
                resolution = resolution,
                fps = fps
            )
        )
        val thumb = renderThumbnailBitmap(project)
        projectRepo.saveProject(project, thumb)
        thumb?.recycle()
        refreshDrafts()
        return project.id
    }

    suspend fun createFromTemplate(template: ProjectTemplate): String {
        val proj = template.buildProject()
        val thumb = renderThumbnailBitmap(proj)
        projectRepo.saveProject(proj, thumb)
        thumb?.recycle()
        refreshDrafts()
        return proj.id
    }

    fun openProject(id: String) {
        viewModelScope.launch {
            pausePlayback()
            val loaded = projectRepo.loadProject(id)
            if (loaded == null) {
                showToast("Project load nahi ho saka")
                return@launch
            }
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoFlags()

            _project.value = loaded
            _timeMs.value = 0L
            _selectedCubeId.value = loaded.cubes.firstOrNull()?.id
            _selectedFace.value = FaceSide.FRONT
            _selectedWordId.value = null
            _selectedLyricId.value = loaded.lyrics.firstOrNull()?.id
            _selectedKeyframeId.value = null
            _activeTool.value = null

            loaded.background.imageUriString?.let { loadBackgroundBitmap(it) } ?: run { _bgBitmap.value = null }
            loaded.music?.let { m ->
                withContext(Dispatchers.IO) {
                    audioPlayer.prepare(m.uriString, m.trimStartMs, m.volume)
                }
            } ?: audioPlayer.release()
        }
    }

    fun renameDraft(id: String, newName: String) {
        viewModelScope.launch {
            projectRepo.renameProject(id, newName)
            if (_project.value?.id == id) {
                _project.value = _project.value?.copy(name = newName.trim().ifEmpty { _project.value?.name ?: "Cube" })
            }
            refreshDrafts()
        }
    }

    fun duplicateDraft(id: String) {
        viewModelScope.launch {
            projectRepo.duplicateProject(id)
            refreshDrafts()
            showToast("Project duplicate ho gaya")
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(id)
            refreshDrafts()
            showToast("Draft delete ho gaya")
        }
    }

    fun exportCubeProjToShare(projectId: String, onFileReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val proj = if (_project.value?.id == projectId) _project.value else projectRepo.loadProject(projectId)
            if (proj == null) return@launch
            val zip = projectRepo.exportCubeProjArchive(proj)
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", zip)
            onFileReady(uri)
        }
    }

    fun importCubeProj(uri: Uri, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                showToast("Project file read nahi ho payi")
                return@launch
            }
            val imported = projectRepo.importCubeProjArchive(bytes)
            if (imported == null) {
                showToast("Invalid .cubeproj file")
                return@launch
            }
            refreshDrafts()
            showToast("Project import ho gaya!")
            onImported(imported.id)
        }
    }

    fun saveAndCloseEditor() {
        pausePlayback()
        stopSongPreview()
        val current = _project.value ?: return
        viewModelScope.launch {
            val thumb = renderThumbnailBitmap(current)
            projectRepo.saveProject(current, thumb)
            thumb?.recycle()
            refreshDrafts()
        }
    }

    private fun renderThumbnailBitmap(proj: CubeProject): Bitmap? {
        return runCatching {
            val bmp = Bitmap.createBitmap(270, 480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            SceneRenderer.renderFrame(
                context = appContext,
                canvas = canvas,
                width = 270,
                height = 480,
                project = proj,
                timeMs = 500L,
                faceTextureCache = faceTextureCache,
                bgBitmap = null,
                textureSizePx = 256,
                isExport = true
            )
            bmp
        }.getOrNull()
    }

    private fun mutateProject(recordUndo: Boolean = true, transform: (CubeProject) -> CubeProject) {
        val current = _project.value ?: return
        val next = transform(current)
        if (next == current) return
        if (recordUndo) {
            if (undoStack.size >= 100) undoStack.removeFirst()
            undoStack.addLast(current)
            redoStack.clear()
            updateUndoRedoFlags()
        }
        _project.value = next
        scheduleAutoSave()
    }

    fun undo() {
        val current = _project.value ?: return
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        if (redoStack.size >= 100) redoStack.removeFirst()
        redoStack.addLast(current)
        _project.value = prev
        updateUndoRedoFlags()
        scheduleAutoSave()
        triggerHaptic()
    }

    fun redo() {
        val current = _project.value ?: return
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        if (undoStack.size >= 100) undoStack.removeFirst()
        undoStack.addLast(current)
        _project.value = next
        updateUndoRedoFlags()
        scheduleAutoSave()
        triggerHaptic()
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1000L)
            val proj = _project.value ?: return@launch
            val thumb = renderThumbnailBitmap(proj)
            projectRepo.saveProject(proj, thumb)
            thumb?.recycle()
            refreshDrafts()
        }
    }

    // ---- Master Clock & Transport ----

    fun togglePlayPause() {
        if (_isPlaying.value) pausePlayback() else startPlayback()
    }

    fun startPlayback() {
        val proj = _project.value ?: return
        stopSongPreview()
        val dur = proj.computedDurationMs()
        if (_timeMs.value >= dur - 40L) _timeMs.value = 0L

        _isPlaying.value = true
        val music = proj.music
        if (music != null) {
            audioPlayer.prepare(music.uriString, music.trimStartMs, music.volume)
            audioPlayer.setSpeed(_playbackSpeed.value)
            audioPlayer.play(_timeMs.value, music.timelineOffsetMs)
        }

        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            var lastWall = SystemClock.elapsedRealtime()
            while (isActive && _isPlaying.value) {
                val now = SystemClock.elapsedRealtime()
                val dt = now - lastWall
                lastWall = now

                val activeProj = _project.value ?: break
                val totalDur = activeProj.computedDurationMs()
                val audioPos = activeProj.music?.let { m ->
                    audioPlayer.currentTimelinePositionMs(m.timelineOffsetMs)
                }

                val nextMs = audioPos ?: (_timeMs.value + (dt * _playbackSpeed.value).toLong())
                if (nextMs >= totalDur) {
                    if (_isLooping.value) {
                        _timeMs.value = 0L
                        activeProj.music?.let { m -> audioPlayer.play(0L, m.timelineOffsetMs) }
                    } else {
                        _timeMs.value = totalDur
                        pausePlayback()
                        break
                    }
                } else {
                    _timeMs.value = nextMs
                }
                delay(16L)
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        clockJob?.cancel()
        audioPlayer.pause()
    }

    fun seekTo(targetMs: Long) {
        val proj = _project.value ?: return
        val clamped = targetMs.coerceIn(0L, proj.computedDurationMs())
        _timeMs.value = clamped
        proj.music?.let { m ->
            audioPlayer.seekTo(clamped, m.timelineOffsetMs)
        }
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    fun cyclePlaybackSpeed() {
        val next = when (_playbackSpeed.value) {
            1.0f -> 0.75f
            0.75f -> 0.5f
            else -> 1.0f
        }
        _playbackSpeed.value = next
        audioPlayer.setSpeed(next)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        audioPlayer.setSpeed(speed)
    }

    fun jumpPrevLyric() {
        val lyrics = _project.value?.lyrics?.sortedBy { it.startMs } ?: return
        val prev = lyrics.lastOrNull { it.startMs < _timeMs.value - 80L } ?: lyrics.firstOrNull()
        prev?.let {
            seekTo(it.startMs)
            _selectedLyricId.value = it.id
        }
    }

    fun jumpNextLyric() {
        val lyrics = _project.value?.lyrics?.sortedBy { it.startMs } ?: return
        val next = lyrics.firstOrNull { it.startMs > _timeMs.value + 80L } ?: lyrics.lastOrNull()
        next?.let {
            seekTo(it.startMs)
            _selectedLyricId.value = it.id
        }
    }

    fun jumpPrevKeyframe() {
        val keys = _project.value?.keyframes?.sortedBy { it.timeMs } ?: return
        val prev = keys.lastOrNull { it.timeMs < _timeMs.value - 40L } ?: keys.firstOrNull()
        prev?.let {
            seekTo(it.timeMs)
            _selectedKeyframeId.value = it.id
        }
    }

    fun jumpNextKeyframe() {
        val keys = _project.value?.keyframes?.sortedBy { it.timeMs } ?: return
        val next = keys.firstOrNull { it.timeMs > _timeMs.value + 40L } ?: keys.lastOrNull()
        next?.let {
            seekTo(it.timeMs)
            _selectedKeyframeId.value = it.id
        }
    }

    // ---- Selection & Tools ----

    fun openTool(tool: EditorTool?) {
        _activeTool.value = if (_activeTool.value == tool) null else tool
    }

    fun selectCube(cubeId: String) {
        _selectedCubeId.value = cubeId
        _selectedWordId.value = null
    }

    fun selectFace(face: FaceSide) {
        _selectedFace.value = face
        _selectedWordId.value = null
    }

    fun selectWord(wordId: String?) {
        _selectedWordId.value = wordId
    }

    fun selectLyric(lyricId: String?) {
        _selectedLyricId.value = lyricId
    }

    fun selectKeyframe(keyframeId: String?) {
        _selectedKeyframeId.value = keyframeId
    }

    fun setAxisLock(lock: AxisLock) {
        _axisLock.value = lock
    }

    fun toggleSafeGuides() {
        _showSafeGuides.value = !_showSafeGuides.value
    }

    // ---- 3D Gesture & Cube Manipulation ----

    fun rotateSelectedCubeBy(deltaXDeg: Float, deltaYDeg: Float, deltaZDeg: Float) {
        val cubeId = _selectedCubeId.value ?: return
        val lock = _axisLock.value
        val dx = if (lock == AxisLock.FREE || lock == AxisLock.X) deltaXDeg else 0f
        val dy = if (lock == AxisLock.FREE || lock == AxisLock.Y) deltaYDeg else 0f
        val dz = if (lock == AxisLock.FREE || lock == AxisLock.Z) deltaZDeg else 0f

        mutateProject(recordUndo = false) { proj ->
            val cube = proj.cubes.firstOrNull { it.id == cubeId } ?: return@mutateProject proj
            if (cube.locked) return@mutateProject proj
            proj.copy(
                cubes = proj.cubes.map { c ->
                    if (c.id == cubeId) {
                        c.copy(
                            rotXDeg = (c.rotXDeg + dx).coerceIn(-720f, 720f),
                            rotYDeg = c.rotYDeg + dy,
                            rotZDeg = c.rotZDeg + dz
                        )
                    } else c
                }
            )
        }
    }

    fun scaleAndMoveSelectedCube(scaleFactor: Float, panX: Float, panY: Float) {
        val cubeId = _selectedCubeId.value ?: return
        mutateProject(recordUndo = false) { proj ->
            val cube = proj.cubes.firstOrNull { it.id == cubeId } ?: return@mutateProject proj
            if (cube.locked) return@mutateProject proj
            proj.copy(
                cubes = proj.cubes.map { c ->
                    if (c.id == cubeId) {
                        c.copy(
                            scale = (c.scale * scaleFactor).coerceIn(0.35f, 2.5f),
                            posX = (c.posX + panX).coerceIn(-2.5f, 2.5f),
                            posY = (c.posY + panY).coerceIn(-2.5f, 2.5f)
                        )
                    } else c
                }
            )
        }
    }

    fun snapSelectedCubeToFace(face: FaceSide) {
        val cubeId = _selectedCubeId.value ?: return
        _selectedFace.value = face
        val (rx, ry, rz) = KeyframeEvaluator.faceTargetRotation(face)
        mutateProject(recordUndo = true) { proj ->
            proj.copy(
                cubes = proj.cubes.map { c ->
                    if (c.id == cubeId && !c.locked) {
                        c.copy(rotXDeg = rx, rotYDeg = ry, rotZDeg = rz)
                    } else c
                }
            )
        }
        triggerHaptic()
    }

    fun toggleCubeTopBottom(cubeId: String) {
        mutateProject { proj ->
            proj.copy(cubes = proj.cubes.map { if (it.id == cubeId) it.copy(showTopBottom = !it.showTopBottom) else it })
        }
    }

    fun toggleCubeVisible(cubeId: String) {
        mutateProject { proj ->
            proj.copy(cubes = proj.cubes.map { if (it.id == cubeId) it.copy(visible = !it.visible) else it })
        }
    }

    fun toggleCubeLocked(cubeId: String) {
        mutateProject { proj ->
            proj.copy(cubes = proj.cubes.map { if (it.id == cubeId) it.copy(locked = !it.locked) else it })
        }
    }

    fun toggleCubeHingeMode(cubeId: String) {
        mutateProject { proj ->
            proj.copy(cubes = proj.cubes.map { if (it.id == cubeId) it.copy(hingeMode = !it.hingeMode) else it })
        }
    }

    // ---- Add Box / Hierarchy ----

    fun addBoxAttachedTo(parentId: String, parentFace: CubeFaceIdFaceSideAlias, hingeMode: Boolean = false) {
        addBoxAttached(parentId, parentFace, hingeMode)
    }

    fun addBoxAttached(parentId: String, parentFace: FaceSide, hingeMode: Boolean = false) {
        val proj = _project.value ?: return
        val occupied = proj.cubes.any { it.parentId == parentId && it.parentFace == parentFace }
        if (occupied) {
            showToast("${parentFace.label} face par pehle se ek box attached hai!")
            return
        }
        val nextNum = proj.cubes.size + 1
        val newCube = SceneCube(
            name = "Cube $nextNum",
            size = 0.85f,
            parentId = parentId,
            parentFace = parentFace,
            hingeMode = hingeMode,
            faces = SceneCube.defaultFacesMap(defaultSeeded = false)
        )
        mutateProject { p ->
            p.copy(cubes = p.cubes + newCube)
        }
        _selectedCubeId.value = newCube.id
        _selectedFace.value = FaceSide.FRONT
        triggerHaptic()
        showToast("${newCube.name} ${parentFace.label} face se attach ho gaya")
    }

    fun unlinkCube(cubeId: String) {
        mutateProject { proj ->
            val target = proj.cubes.firstOrNull { it.id == cubeId } ?: return@mutateProject proj
            if (target.parentId == null) return@mutateProject proj
            val poses = proj.cubes.associate { c ->
                c.id to CubePose(c.posX, c.posY, c.posZ, c.rotXDeg, c.rotYDeg, c.rotZDeg, c.scale, c.opacity)
            }
            val mats = KeyframeEvaluator.computeCubeWorldMatrices(proj.cubes, poses)
            val worldOrigin = mats[cubeId]?.transformPoint(com.example.engine.Vec3()) ?: com.example.engine.Vec3()
            proj.copy(
                cubes = proj.cubes.map { c ->
                    if (c.id == cubeId) {
                        c.copy(parentId = null, parentFace = null, hingeMode = false, posX = worldOrigin.x, posY = worldOrigin.y, posZ = worldOrigin.z)
                    } else c
                }
            )
        }
        showToast("Cube unlink ho gaya")
    }

    fun reattachCube(cubeId: String, newParentId: String, newParentFace: FaceSide) {
        val proj = _project.value ?: return
        if (cubeId == newParentId || wouldCreateCycle(proj.cubes, cubeId, newParentId)) {
            showToast("Cycle attach allowed nahi hai")
            return
        }
        if (proj.cubes.any { it.id != cubeId && it.parentId == newParentId && it.parentFace == newParentFace }) {
            showToast("Ye face occupied hai")
            return
        }
        mutateProject { p ->
            p.copy(cubes = p.cubes.map { if (it.id == cubeId) it.copy(parentId = newParentId, parentFace = newParentFace, posX = 0f, posY = 0f, posZ = 0f) else it })
        }
    }

    private fun wouldCreateCycle(cubes: List<SceneCube>, nodeId: String, proposedParentId: String): Boolean {
        val byId = cubes.associateBy { it.id }
        var cur: String? = proposedParentId
        val visited = mutableSetOf<String>()
        while (cur != null) {
            if (cur == nodeId) return true
            if (!visited.add(cur)) return true
            cur = byId[cur]?.parentId
        }
        return false
    }

    fun duplicateCube(cubeId: String, includeChildren: Boolean) {
        mutateProject { proj ->
            val orig = proj.cubes.firstOrNull { it.id == cubeId } ?: return@mutateProject proj
            val newRootId = UUID.randomUUID().toString()
            val clonedRoot = orig.copy(
                id = newRootId,
                name = "${orig.name} Copy",
                parentId = null,
                parentFace = null,
                posX = orig.posX + 0.55f
            )
            val added = mutableListOf(clonedRoot)
            if (includeChildren) {
                proj.cubes.filter { it.parentId == cubeId }.forEach { child ->
                    added.add(child.copy(id = UUID.randomUUID().toString(), name = "${child.name} Copy", parentId = newRootId))
                }
            }
            proj.copy(cubes = proj.cubes + added)
        }
    }

    fun deleteCube(cubeId: String, deleteChildren: Boolean) {
        val proj = _project.value ?: return
        if (proj.cubes.size <= 1) {
            showToast("Kam se kam 1 cube zaroori hai")
            return
        }
        mutateProject { p ->
            val toDelete = mutableSetOf(cubeId)
            if (deleteChildren) {
                var added = true
                while (added) {
                    added = false
                    p.cubes.forEach { c ->
                        if (c.parentId in toDelete && c.id !in toDelete) {
                            toDelete.add(c.id)
                            added = true
                        }
                    }
                }
            }
            val remaining = p.cubes.filterNot { it.id in toDelete }.map { c ->
                if (!deleteChildren && c.parentId == cubeId) c.copy(parentId = null, parentFace = null) else c
            }
            p.copy(cubes = remaining)
        }
        _selectedCubeId.value = _project.value?.cubes?.firstOrNull()?.id
    }

    // ---- Face Text, Fonts, AutoFit, Colors ----

    private fun updateSelectedFace(transform: (CubeFace) -> CubeFace) {
        val cubeId = _selectedCubeId.value ?: return
        val side = _selectedFace.value
        mutateProject { proj ->
            proj.copy(
                cubes = proj.cubes.map { c ->
                    if (c.id == cubeId) {
                        val curFace = c.faces[side] ?: CubeFace(side = side)
                        val newMap = c.faces.toMutableMap().apply { put(side, transform(curFace)) }
                        c.copy(faces = newMap)
                    } else c
                }
            )
        }
    }

    fun addWordToSelectedFace(text: String, corner: CornerAnchor, fontId: String? = null, colorHex: String? = null) {
        if (text.isBlank()) return
        val newItem = FaceTextItem(
            text = text.trim(),
            corner = corner,
            fontId = fontId ?: "anton",
            colorHex = colorHex ?: KineticPaletteHex.random(),
            fontSizePx = 88f
        )
        updateSelectedFace { face ->
            val countInCorner = face.textItems.count { it.corner == corner }
            face.copy(textItems = face.textItems + newItem.copy(orderInCorner = countInCorner))
        }
        _selectedWordId.value = newItem.id
        triggerHaptic()
    }

    fun updateWord(wordId: String, transform: (FaceTextItem) -> FaceTextItem) {
        updateSelectedFace { face ->
            face.copy(textItems = face.textItems.map { if (it.id == wordId) transform(it) else it })
        }
    }

    fun moveWordCorner(wordId: String, newCorner: CornerAnchor) {
        updateSelectedFace { face ->
            val count = face.textItems.count { it.corner == newCorner && it.id != wordId }
            face.copy(
                layoutMode = FaceLayoutMode.CORNERS,
                textItems = face.textItems.map {
                    if (it.id == wordId) it.copy(corner = newCorner, orderInCorner = count) else it
                }
            )
        }
        triggerHaptic()
    }

    fun reorderWordInFace(wordId: String, delta: Int) {
        updateSelectedFace { face ->
            val list = face.textItems.toMutableList()
            val idx = list.indexOfFirst { it.id == wordId }
            val nextIdx = (idx + delta).coerceIn(0, list.lastIndex)
            if (idx >= 0 && idx != nextIdx) {
                val item = list.removeAt(idx)
                list.add(nextIdx, item)
            }
            face.copy(textItems = list.mapIndexed { i, it -> it.copy(orderInCorner = i) })
        }
    }

    fun duplicateWord(wordId: String) {
        updateSelectedFace { face ->
            val orig = face.textItems.firstOrNull { it.id == wordId } ?: return@updateSelectedFace face
            val copy = orig.copy(id = UUID.randomUUID().toString(), freeX = (orig.freeX + 0.08f).coerceAtMost(0.9f))
            face.copy(textItems = face.textItems + copy)
        }
    }

    fun deleteWord(wordId: String) {
        updateSelectedFace { face ->
            face.copy(textItems = face.textItems.filterNot { it.id == wordId })
        }
        if (_selectedWordId.value == wordId) _selectedWordId.value = null
    }

    fun setFaceLayoutMode(mode: FaceLayoutMode) {
        updateSelectedFace { face ->
            if (mode == FaceLayoutMode.FREE && face.layoutMode != FaceLayoutMode.FREE) {
                val fitted = TextAutoFit.computePlacements(face, 512f)
                val byId = fitted.associateBy { it.item.id }
                face.copy(
                    layoutMode = mode,
                    textItems = face.textItems.map { item ->
                        val p = byId[item.id]
                        if (p != null) {
                            item.copy(freeX = p.centerX / 512f, freeY = p.centerY / 512f, fontSizePx = p.fontSizePx, freeScale = 1f)
                        } else item
                    }
                )
            } else {
                face.copy(layoutMode = mode)
            }
        }
        triggerHaptic()
    }

    fun randomizeSelectedFaceColors(palette: List<String> = KineticPaletteHex) {
        updateSelectedFace { face ->
            face.copy(
                textItems = face.textItems.mapIndexed { idx, item ->
                    item.copy(colorHex = palette[(idx + (System.currentTimeMillis() % palette.size).toInt()) % palette.size])
                }
            )
        }
        triggerHaptic()
    }

    fun tintAllSelectedFaceText(colorHex: String) {
        updateSelectedFace { face ->
            face.copy(
                defaultTextColorHex = colorHex,
                textItems = face.textItems.map { it.copy(colorHex = colorHex) }
            )
        }
    }

    fun applyFaceStyle(
        target: ColorApplyTarget,
        bgHex: String,
        gradientHex: String?,
        transparent: Boolean,
        borderHex: String,
        borderWidth: Float
    ) {
        val cubeId = _selectedCubeId.value ?: return
        val faceSide = _selectedFace.value
        mutateProject { proj ->
            proj.copy(
                cubes = proj.cubes.map { c ->
                    val shouldUpdateCube = target == ColorApplyTarget.ALL_CUBES || c.id == cubeId
                    if (!shouldUpdateCube) return@map c
                    val updatedFaces = c.faces.mapValues { (side, f) ->
                        val match = when (target) {
                            ColorApplyTarget.THIS_FACE -> side == faceSide
                            ColorApplyTarget.FOUR_SIDES -> side in FaceSide.FOUR_SIDES
                            ColorApplyTarget.WHOLE_CUBE, ColorApplyTarget.ALL_CUBES -> true
                        }
                        if (match) {
                            f.copy(
                                backgroundColorHex = bgHex,
                                gradientColorHex = gradientHex,
                                transparent = transparent,
                                borderColorHex = borderHex,
                                borderWidth = borderWidth
                            )
                        } else f
                    }
                    c.copy(faces = updatedFaces)
                }
            )
        }
    }

    // ---- Music Picker & Audio Track Ops ----

    fun loadDeviceSongs() {
        viewModelScope.launch {
            _deviceSongs.value = AudioLibrary.queryDeviceSongs(appContext)
        }
    }

    fun togglePreviewSong(song: DeviceSong) {
        if (_previewingSongId.value == song.id) {
            stopSongPreview()
            return
        }
        stopSongPreview()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = previewPlayer.prepare(song.uriString, 0L, 1.0f)
            if (ok) {
                previewPlayer.play(0L, 0L)
                _previewingSongId.value = song.id
                previewStopJob = viewModelScope.launch {
                    delay(25_000L)
                    stopSongPreview()
                }
            }
        }
    }

    fun stopSongPreview() {
        previewStopJob?.cancel()
        previewPlayer.pause()
        _previewingSongId.value = null
    }

    fun attachDeviceSong(song: DeviceSong) {
        stopSongPreview()
        viewModelScope.launch {
            val wb = AudioLibrary.extractWaveformAndBeats(appContext, song.uriString, song.durationMs)
            val track = MusicTrackRef(
                uriString = song.uriString,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                trimStartMs = 0L,
                trimEndMs = minOf(song.durationMs, 30_000L).coerceAtLeast(1000L),
                timelineOffsetMs = 0L,
                volume = 1.0f,
                waveformPeaks = wb.peaks,
                beatMarkersMs = wb.beatMarkersMs
            )
            mutateProject { it.copy(music = track) }
            withContext(Dispatchers.IO) {
                audioPlayer.prepare(track.uriString, track.trimStartMs, track.volume)
            }
            showToast("Song add ho gaya: ${song.title}")
        }
    }

    fun importAudioFileUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val meta = AudioLibrary.probeAudioMetadata(appContext, uri.toString())
            if (meta == null) {
                showToast("Audio file open nahi ho payi")
                return@launch
            }
            val (title, artist, dur) = meta
            val wb = AudioLibrary.extractWaveformAndBeats(appContext, uri.toString(), dur)
            val track = MusicTrackRef(
                uriString = uri.toString(),
                title = title,
                artist = artist,
                durationMs = dur,
                trimStartMs = 0L,
                trimEndMs = dur,
                waveformPeaks = wb.peaks,
                beatMarkersMs = wb.beatMarkersMs
            )
            mutateProject { it.copy(music = track) }
            withContext(Dispatchers.IO) {
                audioPlayer.prepare(track.uriString, track.trimStartMs, track.volume)
            }
            showToast("Imported audio: $title")
        }
    }

    fun updateMusicTrack(transform: (MusicTrackRef) -> MusicTrackRef) {
        mutateProject { proj ->
            val m = proj.music ?: return@mutateProject proj
            val updated = transform(m)
            audioPlayer.setVolume(updated.volume)
            proj.copy(music = updated)
        }
    }

    fun removeMusicTrack() {
        pausePlayback()
        audioPlayer.release()
        mutateProject { it.copy(music = null) }
    }

    // ---- Lyrics & Tap-to-Sync & Auto Rotate ----

    fun importLyricsText(rawText: String, replaceExisting: Boolean) {
        val proj = _project.value ?: return
        val cubeId = _selectedCubeId.value ?: proj.cubes.firstOrNull()?.id ?: return
        val parsed = LyricsParser.parseAuto(rawText, cubeId, proj.computedDurationMs())
        if (parsed.isEmpty()) {
            showToast("Koi lyrics line nahi mili")
            return
        }
        val beats = proj.music?.beatMarkersMs.orEmpty()
        val snapped = if (settings.value.snapToBeat && beats.isNotEmpty()) {
            parsed.map { line ->
                val s = LyricsParser.snapTimeToBeat(line.startMs, beats)
                line.copy(startMs = s, endMs = maxOf(line.endMs, s + 600L))
            }
        } else parsed

        mutateProject { p ->
            val nextLyrics = if (replaceExisting) snapped else (p.lyrics + snapped).sortedBy { it.startMs }
            p.copy(lyrics = nextLyrics)
        }
        _selectedLyricId.value = snapped.first().id
        showToast("${snapped.size} lyric lines add ho gayi!")
    }

    fun addLyricLineAtPlayhead(text: String) {
        val proj = _project.value ?: return
        val cubeId = _selectedCubeId.value ?: proj.cubes.firstOrNull()?.id ?: return
        val start = maybeSnapToBeat(_timeMs.value)
        val line = LyricLine(
            text = text.trim().ifEmpty { "NEW LYRIC" },
            startMs = start,
            endMs = start + 2200L,
            targetCubeId = cubeId,
            targetFace = _selectedFace.value
        )
        mutateProject { p -> p.copy(lyrics = (p.lyrics + line).sortedBy { it.startMs }) }
        _selectedLyricId.value = line.id
    }

    fun updateLyricLine(id: String, transform: (LyricLine) -> LyricLine) {
        mutateProject { p ->
            p.copy(lyrics = p.lyrics.map { if (it.id == id) transform(it) else it }.sortedBy { it.startMs })
        }
    }

    fun deleteLyricLine(id: String) {
        mutateProject { p -> p.copy(lyrics = p.lyrics.filterNot { it.id == id }) }
        if (_selectedLyricId.value == id) _selectedLyricId.value = null
    }

    fun applySettingsToAllLyrics(source: LyricLine) {
        mutateProject { p ->
            p.copy(
                lyrics = p.lyrics.map { line ->
                    line.copy(
                        direction = source.direction,
                        extraSpins = source.extraSpins,
                        anticipationOffsetMs = source.anticipationOffsetMs,
                        rotationDurationMs = source.rotationDurationMs,
                        easing = source.easing,
                        zoomPeak = source.zoomPeak,
                        zTiltDeg = source.zTiltDeg,
                        motionBlurAmount = source.motionBlurAmount,
                        displayMode = source.displayMode
                    )
                }
            )
        }
        showToast("Settings saari lines par apply ho gayi")
    }

    fun autoRotateLyrics() {
        val dirs = listOf(RotationDirection.RIGHT, RotationDirection.RIGHT, RotationDirection.UP, RotationDirection.DIAGONAL)
        mutateProject { p ->
            val sorted = p.lyrics.sortedBy { it.startMs }
            val updated = sorted.mapIndexed { idx, line ->
                line.copy(
                    targetFace = FaceSide.FOUR_SIDES[idx % FaceSide.FOUR_SIDES.size],
                    direction = dirs[idx % dirs.size]
                )
            }
            p.copy(lyrics = updated)
        }
        triggerHaptic()
        showToast("Auto Rotate: F -> R -> B -> L set!")
    }

    fun tapToSyncLine(lineId: String) {
        val tapMs = maybeSnapToBeat(_timeMs.value)
        mutateProject { p ->
            val sorted = p.lyrics.sortedBy { it.startMs }.toMutableList()
            val idx = sorted.indexOfFirst { it.id == lineId }
            if (idx >= 0) {
                val cur = sorted[idx]
                val nextStart = sorted.getOrNull(idx + 1)?.startMs
                val newEnd = (nextStart ?: (tapMs + 2400L)).coerceAtLeast(tapMs + 500L)
                sorted[idx] = cur.copy(startMs = tapMs, endMs = newEnd)
                if (idx > 0 && sorted[idx - 1].endMs > tapMs) {
                    sorted[idx - 1] = sorted[idx - 1].copy(endMs = tapMs.coerceAtLeast(sorted[idx - 1].startMs + 250L))
                }
            }
            p.copy(lyrics = sorted.sortedBy { it.startMs })
        }
        triggerHaptic()
    }

    fun autoTimingFromBeats() {
        val proj = _project.value ?: return
        val beats = proj.music?.beatMarkersMs.orEmpty()
        if (beats.isEmpty() || proj.lyrics.isEmpty()) {
            showToast("Pehle song aur lyrics add karein")
            return
        }
        mutateProject { p ->
            val sorted = p.lyrics.sortedBy { it.startMs }
            val updated = sorted.mapIndexed { idx, line ->
                val bIdx = (idx * 2).coerceAtMost(beats.lastIndex)
                val s = beats[bIdx]
                val e = beats.getOrElse((idx + 1) * 2) { s + 2200L }.coerceAtLeast(s + 500L)
                line.copy(startMs = s, endMs = e)
            }
            p.copy(lyrics = updated)
        }
        showToast("Lyrics beats par auto-align ho gayi")
    }

    fun splitLyricAtPlayhead(id: String) {
        val t = _timeMs.value
        mutateProject { p ->
            val idx = p.lyrics.indexOfFirst { it.id == id }
            if (idx < 0) return@mutateProject p
            val orig = p.lyrics[idx]
            if (t <= orig.startMs + 250L || t >= orig.endMs - 250L) return@mutateProject p
            val words = orig.text.split(" ")
            val splitWord = (words.size / 2).coerceAtLeast(1)
            val partA = if (words.size > 1) words.take(splitWord).joinToString(" ") else orig.text
            val partB = if (words.size > 1) words.drop(splitWord).joinToString(" ") else orig.text
            val first = orig.copy(text = partA, endMs = t)
            val second = orig.copy(
                id = UUID.randomUUID().toString(),
                text = partB,
                startMs = t,
                targetFace = FaceSide.FOUR_SIDES[(FaceSide.FOUR_SIDES.indexOf(orig.targetFace) + 1) % 4]
            )
            val nextList = p.lyrics.toMutableList().apply {
                set(idx, first)
                add(idx + 1, second)
            }
            p.copy(lyrics = nextList)
        }
    }

    // ---- Keyframes & Motion Presets ----

    fun recordKeyframeAtPlayhead() {
        val proj = _project.value ?: return
        val cubeId = _selectedCubeId.value ?: proj.cubes.firstOrNull()?.id ?: return
        val cube = proj.cubes.firstOrNull { it.id == cubeId } ?: return
        val t = maybeSnapToBeat(_timeMs.value)

        val newKey = KeyframeTrackItem(
            cubeId = cubeId,
            timeMs = t,
            posX = cube.posX,
            posY = cube.posY,
            posZ = cube.posZ,
            rotXDeg = cube.rotXDeg,
            rotYDeg = cube.rotYDeg,
            rotZDeg = cube.rotZDeg,
            scale = cube.scale,
            opacity = cube.opacity,
            cameraZoom = proj.cameraZoom,
            shutterAngleDeg = proj.effects.shutterAngleDeg
        )

        mutateProject { p ->
            val filtered = p.keyframes.filterNot { it.cubeId == cubeId && abs(it.timeMs - t) <= 40L }
            p.copy(keyframes = (filtered + newKey).sortedBy { it.timeMs })
        }
        _selectedKeyframeId.value = newKey.id
        triggerHaptic()
        showToast("Keyframe recorded at ${formatTimeMs(t)}")
    }

    fun updateKeyframe(id: String, transform: (KeyframeTrackItem) -> KeyframeTrackItem) {
        mutateProject { p ->
            p.copy(keyframes = p.keyframes.map { if (it.id == id) transform(it) else it }.sortedBy { it.timeMs })
        }
    }

    fun deleteKeyframe(id: String) {
        mutateProject { p -> p.copy(keyframes = p.keyframes.filterNot { it.id == id }) }
        if (_selectedKeyframeId.value == id) _selectedKeyframeId.value = null
    }

    fun applyMotionPreset(preset: MotionPresetType, durationMs: Long, intensity: Float) {
        val proj = _project.value ?: return
        val cubeId = _selectedCubeId.value ?: proj.cubes.firstOrNull()?.id ?: return
        val cube = proj.cubes.firstOrNull { it.id == cubeId } ?: return
        val t = maybeSnapToBeat(_timeMs.value)

        val compiled = KeyframeEvaluator.compileEffectiveKeyframes(proj)[cubeId].orEmpty()
        val currentPose = KeyframeEvaluator.poseAt(
            compiled,
            t,
            CubePose(cube.posX, cube.posY, cube.posZ, cube.rotXDeg, cube.rotYDeg, cube.rotZDeg, cube.scale, cube.opacity)
        )
        val gen = KeyframeEvaluator.generateMotionPresetKeyframes(preset, cubeId, t, durationMs, intensity, currentPose)
        mutateProject { p ->
            val cleaned = p.keyframes.filterNot { it.cubeId == cubeId && it.timeMs in t..(t + durationMs) }
            p.copy(keyframes = (cleaned + gen).sortedBy { it.timeMs })
        }
        triggerHaptic()
        showToast("${preset.label} keyframes add ho gaye!")
    }

    // ---- Background, Effects, Credit, Karaoke, Export ----

    fun updateBackground(transform: (BackgroundConfig) -> BackgroundConfig) {
        mutateProject { it.copy(background = transform(it.background)) }
    }

    fun setBackgroundImageUri(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        loadBackgroundBitmap(uri.toString())
        updateBackground { it.copy(type = BackgroundType.IMAGE, imageUriString = uri.toString()) }
    }

    private fun loadBackgroundBitmap(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = runCatching {
                appContext.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            _bgBitmap.value = bmp
        }
    }

    fun updateEffects(transform: (EffectsConfig) -> EffectsConfig) {
        mutateProject { it.copy(effects = transform(it.effects)) }
    }

    fun updateKaraoke(transform: (KaraokeConfig) -> KaraokeConfig) {
        mutateProject { it.copy(karaoke = transform(it.karaoke)) }
    }

    fun updateCredit(transform: (CreditConfig) -> CreditConfig) {
        mutateProject { it.copy(credit = transform(it.credit)) }
    }

    fun updateProjectSettings(transform: (CubeProject) -> CubeProject) {
        mutateProject { transform(it) }
    }

    fun startExport(fastPreview: Boolean = false, rangeStartMs: Long = 0L, rangeEndMs: Long? = null) {
        val proj = _project.value ?: return
        pausePlayback()
        exportJob?.cancel()
        _exportState.value = ExportUiState.Exporting(ExportProgress())
        val endMs = rangeEndMs ?: proj.computedDurationMs()

        exportJob = viewModelScope.launch {
            try {
                val res = VideoExporter.exportProjectToMp4(
                    context = appContext,
                    project = proj,
                    bgBitmap = _bgBitmap.value,
                    fastPreview = fastPreview,
                    rangeStartMs = rangeStartMs,
                    rangeEndMs = endMs,
                    onProgress = { prog ->
                        _exportState.value = ExportUiState.Exporting(prog)
                    }
                )
                val shareUri = runCatching {
                    FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", res.cacheFile)
                }.getOrNull() ?: res.galleryUri
                _exportState.value = ExportUiState.Success(res, shareUri)
                triggerHaptic()
            } catch (e: Exception) {
                ExportForegroundService.stop(appContext)
                _exportState.value = ExportUiState.Error(e.message ?: "Export fail ho gaya. Low resolution try karein.")
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        ExportForegroundService.stop(appContext)
        _exportState.value = ExportUiState.Idle
    }

    fun dismissExportState() {
        _exportState.value = ExportUiState.Idle
    }

    // ---- Utilities ----

    fun maybeSnapToBeat(rawMs: Long): Long {
        if (!settings.value.snapToBeat) return rawMs
        val beats = _project.value?.music?.beatMarkersMs.orEmpty()
        return LyricsParser.snapTimeToBeat(rawMs, beats, thresholdMs = 140L)
    }

    fun cacheSizeMbText(): String {
        val bytes = projectRepo.cacheSizeBytes()
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    fun clearCacheStorage() {
        faceTextureCache.clear()
        projectRepo.clearCache()
        showToast("Cache clear ho gaya")
    }

    fun copyDebugInfoToClipboard() {
        val p = _project.value
        val info = buildString {
            appendLine("Cube Editor Diagnostics")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Active Project: ${p?.name ?: "None"} (schema v${p?.schemaVersion ?: 1})")
            appendLine("Cubes: ${p?.cubes?.size ?: 0}, Lyrics: ${p?.lyrics?.size ?: 0}, Keys: ${p?.keyframes?.size ?: 0}")
            appendLine("Cache size: ${cacheSizeMbText()}")
        }
        val cb = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Cube Editor Debug", info))
        showToast("Debug info clipboard par copy ho gaya")
    }

    fun triggerHaptic() {
        if (!settings.value.hapticsEnabled) return
        runCatching {
            val vib = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(18L)
            }
        }
    }
}

typealias CubeFaceIdFaceSideAlias = FaceSide

fun formatTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val dec = (safe % 1000L) / 100L
    return "%d:%02d.%d".format(min, sec, dec)
}
