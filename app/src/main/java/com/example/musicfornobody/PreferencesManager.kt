package com.example.musicfornobody

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "music_for_nobody_prefs")

data class UserSettings(
    val preferredPlatform: MusicPlatform = MusicPlatform.ASK_EVERY_TIME,
    val autoAction: AutoAction = AutoAction.COPY_LINK
)

class PreferencesManager(private val context: Context) {
    private val PLATFORM_KEY = stringPreferencesKey("preferred_platform")
    private val AUTO_ACTION_KEY = stringPreferencesKey("auto_action")

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            preferredPlatform = MusicPlatform.fromKey(prefs[PLATFORM_KEY]),
            autoAction = AutoAction.fromKey(prefs[AUTO_ACTION_KEY])
        )
    }

    suspend fun setPreferredPlatform(platform: MusicPlatform) {
        context.dataStore.edit { it[PLATFORM_KEY] = platform.apiKey }
    }

    suspend fun setAutoAction(action: AutoAction) {
        context.dataStore.edit { it[AUTO_ACTION_KEY] = action.key }
    }
}
