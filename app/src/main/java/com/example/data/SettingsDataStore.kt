package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.PreviewQuality
import com.example.model.ResolutionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cube_editor_settings")

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val AMOLED = booleanPreferencesKey("amoled_black")
        val LANGUAGE = stringPreferencesKey("language")
        val PREVIEW_QUALITY = stringPreferencesKey("preview_quality")
        val HAPTICS = booleanPreferencesKey("haptics")
        val DEFAULT_FPS = intPreferencesKey("default_fps")
        val DEFAULT_RES = stringPreferencesKey("default_res")
        val SNAP_TO_BEAT = booleanPreferencesKey("snap_to_beat")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val COACH_DISMISSED = booleanPreferencesKey("coach_dismissed")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            amoledBlack = prefs[Keys.AMOLED] ?: false,
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.HINGLISH,
            previewQuality = prefs[Keys.PREVIEW_QUALITY]?.let { runCatching { PreviewQuality.valueOf(it) }.getOrNull() }
                ?: PreviewQuality.HIGH,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 30,
            defaultResolution = prefs[Keys.DEFAULT_RES]?.let { runCatching { ResolutionPreset.valueOf(it) }.getOrNull() }
                ?: ResolutionPreset.FHD_1080,
            snapToBeat = prefs[Keys.SNAP_TO_BEAT] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            editorCoachDismissed = prefs[Keys.COACH_DISMISSED] ?: false
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                amoledBlack = prefs[Keys.AMOLED] ?: false,
                language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.HINGLISH,
                previewQuality = prefs[Keys.PREVIEW_QUALITY]?.let { runCatching { PreviewQuality.valueOf(it) }.getOrNull() } ?: PreviewQuality.HIGH,
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
                defaultFps = prefs[Keys.DEFAULT_FPS] ?: 30,
                defaultResolution = prefs[Keys.DEFAULT_RES]?.let { runCatching { ResolutionPreset.valueOf(it) }.getOrNull() } ?: ResolutionPreset.FHD_1080,
                snapToBeat = prefs[Keys.SNAP_TO_BEAT] ?: true,
                onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
                editorCoachDismissed = prefs[Keys.COACH_DISMISSED] ?: false
            )
            val next = transform(current)
            prefs[Keys.AMOLED] = next.amoledBlack
            prefs[Keys.LANGUAGE] = next.language.name
            prefs[Keys.PREVIEW_QUALITY] = next.previewQuality.name
            prefs[Keys.HAPTICS] = next.hapticsEnabled
            prefs[Keys.DEFAULT_FPS] = next.defaultFps
            prefs[Keys.DEFAULT_RES] = next.defaultResolution.name
            prefs[Keys.SNAP_TO_BEAT] = next.snapToBeat
            prefs[Keys.ONBOARDING_DONE] = next.onboardingCompleted
            prefs[Keys.COACH_DISMISSED] = next.editorCoachDismissed
        }
    }
}
