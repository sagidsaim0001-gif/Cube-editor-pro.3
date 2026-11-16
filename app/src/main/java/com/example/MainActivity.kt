package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.*
import com.example.ui.theme.CubeEditorTheme
import com.example.viewmodel.CubeEditorViewModel
import kotlinx.coroutines.launch

object CubeRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val EDITOR = "editor/{projectId}"
    const val SETTINGS = "settings"

    fun editorRoute(projectId: String) = "editor/$projectId"
}

class MainActivity : ComponentActivity() {
    private var sharedVm: CubeEditorViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: CubeEditorViewModel = viewModel()
            sharedVm = vm
            val settings by vm.settings.collectAsStateWithLifecycle()

            CubeEditorTheme(amoledBlack = settings.amoledBlack) {
                CubeEditorAppNav(vm = vm)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sharedVm?.saveAndCloseEditor()
    }
}

@Composable
private fun CubeEditorAppNav(vm: CubeEditorViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val settings by vm.settings.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val project by vm.project.collectAsStateWithLifecycle()
    val bgBitmap by vm.bgBitmap.collectAsStateWithLifecycle()
    val timeMs by vm.timeMs.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val isLooping by vm.isLooping.collectAsStateWithLifecycle()
    val playbackSpeed by vm.playbackSpeed.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val selectedCubeId by vm.selectedCubeId.collectAsStateWithLifecycle()
    val selectedFace by vm.selectedFace.collectAsStateWithLifecycle()
    val selectedWordId by vm.selectedWordId.collectAsStateWithLifecycle()
    val selectedLyricId by vm.selectedLyricId.collectAsStateWithLifecycle()
    val selectedKeyframeId by vm.selectedKeyframeId.collectAsStateWithLifecycle()
    val activeTool by vm.activeTool.collectAsStateWithLifecycle()
    val axisLock by vm.axisLock.collectAsStateWithLifecycle()
    val showSafeGuides by vm.showSafeGuides.collectAsStateWithLifecycle()
    val deviceSongs by vm.deviceSongs.collectAsStateWithLifecycle()
    val previewingSongId by vm.previewingSongId.collectAsStateWithLifecycle()
    val exportState by vm.exportState.collectAsStateWithLifecycle()
    val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()

    val startDest = remember(settings.onboardingCompleted) {
        if (settings.onboardingCompleted) CubeRoutes.HOME else CubeRoutes.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable(CubeRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    vm.completeOnboarding()
                    navController.navigate(CubeRoutes.HOME) {
                        popUpTo(CubeRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(CubeRoutes.HOME) {
            HomeScreen(
                drafts = drafts,
                templates = vm.templates,
                settings = settings,
                onOpenProject = { id ->
                    vm.openProject(id)
                    navController.navigate(CubeRoutes.editorRoute(id))
                },
                onCreateProject = { name, aspect, res, fps, bgType, bgColor ->
                    scope.launch {
                        val id = vm.createNewProject(name, aspect, res, fps, bgType, bgColor)
                        vm.openProject(id)
                        navController.navigate(CubeRoutes.editorRoute(id))
                    }
                },
                onUseTemplate = { tpl ->
                    scope.launch {
                        val id = vm.createFromTemplate(tpl)
                        vm.openProject(id)
                        navController.navigate(CubeRoutes.editorRoute(id))
                    }
                },
                onRenameDraft = vm::renameDraft,
                onDuplicateDraft = vm::duplicateDraft,
                onDeleteDraft = vm::deleteDraft,
                onImportCubeProj = { uri ->
                    vm.importCubeProj(uri) { id ->
                        vm.openProject(id)
                        navController.navigate(CubeRoutes.editorRoute(id))
                    }
                },
                onOpenSettings = { navController.navigate(CubeRoutes.SETTINGS) }
            )
        }

        composable(
            route = CubeRoutes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            LaunchedEffect(projectId) {
                if (project?.id != projectId) {
                    vm.openProject(projectId)
                }
            }
            EditorScreen(
                project = project,
                bgBitmap = bgBitmap,
                faceTextureCache = vm.faceTextureCache,
                timeMs = timeMs,
                isPlaying = isPlaying,
                isLooping = isLooping,
                playbackSpeed = playbackSpeed,
                canUndo = canUndo,
                canRedo = canRedo,
                selectedCubeId = selectedCubeId,
                selectedFace = selectedFace,
                selectedWordId = selectedWordId,
                selectedLyricId = selectedLyricId,
                selectedKeyframeId = selectedKeyframeId,
                activeTool = activeTool,
                axisLock = axisLock,
                showSafeGuides = showSafeGuides,
                settings = settings,
                deviceSongs = deviceSongs,
                previewingSongId = previewingSongId,
                exportState = exportState,
                toastMessage = toastMessage,
                vm = vm,
                onBackHome = { navController.popBackStack() }
            )
        }

        composable(CubeRoutes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
