package com.example.musicfornobody

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(
        val title: String,
        val artist: String,
        val availableLinks: Map<MusicPlatform, String>,
        val autoTriggeredMessage: String? = null
    ) : UiState
    data class Error(val message: String) : UiState
}

class MusicViewModel(
    private val api: SonglinkApi,
    private val prefs: PreferencesManager
) : ViewModel() {

    var inputUrl by mutableStateOf("")
    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set

    val settingsFlow = prefs.settingsFlow

    fun extractUrl(rawText: String): String {
        val urlRegex = "(https?://\\S+)".toRegex()
        return urlRegex.find(rawText)?.value ?: rawText.trim()
    }

    fun translateLink(
        rawInput: String,
        onCopyUrl: (String) -> Unit,
        onOpenUrl: (String) -> Unit
    ) {
        val cleanUrl = extractUrl(rawInput)
        if (cleanUrl.isBlank() || !cleanUrl.startsWith("http")) {
            uiState = UiState.Error("Please enter or share a valid music link.")
            return
        }
        inputUrl = cleanUrl
        uiState = UiState.Loading

        viewModelScope.launch {
            try {
                val response = api.resolveMusicLink(cleanUrl)
                val entity = response.entityUniqueId?.let { response.entitiesByUniqueId?.get(it) }
                    ?: response.entitiesByUniqueId?.values?.firstOrNull()

                val title = entity?.title ?: "Unknown Track"
                val artist = entity?.artistName ?: "Unknown Artist"

                val platformMap = mutableMapOf<MusicPlatform, String>()
                MusicPlatform.selectableTargets.forEach { platform ->
                    response.linksByPlatform?.get(platform.apiKey)?.url?.let { url ->
                        platformMap[platform] = url
                    }
                }

                if (platformMap.isEmpty()) {
                    uiState = UiState.Error("No matching links found for this track.")
                    return@launch
                }

                val currentSettings = prefs.settingsFlow.first()
                var autoMessage: String? = null

                if (currentSettings.preferredPlatform != MusicPlatform.ASK_EVERY_TIME) {
                    val targetUrl = platformMap[currentSettings.preferredPlatform]
                    if (targetUrl != null) {
                        when (currentSettings.autoAction) {
                            AutoAction.COPY_LINK -> {
                                onCopyUrl(targetUrl)
                                autoMessage = "Automatically copied ${currentSettings.preferredPlatform.displayName} link!"
                            }
                            AutoAction.OPEN_APP -> {
                                onOpenUrl(targetUrl)
                                autoMessage = "Automatically opened in ${currentSettings.preferredPlatform.displayName}!"
                            }
                            AutoAction.BOTH -> {
                                onCopyUrl(targetUrl)
                                onOpenUrl(targetUrl)
                                autoMessage = "Copied & opened ${currentSettings.preferredPlatform.displayName} link!"
                            }
                        }
                    } else {
                        autoMessage = "Track not found on ${currentSettings.preferredPlatform.displayName}. Choose another below:"
                    }
                }

                uiState = UiState.Success(
                    title = title,
                    artist = artist,
                    availableLinks = platformMap,
                    autoTriggeredMessage = autoMessage
                )
            } catch (e: Exception) {
                uiState = UiState.Error("Failed to translate link: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    fun updatePreferredPlatform(platform: MusicPlatform) {
        viewModelScope.launch { prefs.setPreferredPlatform(platform) }
    }

    fun updateAutoAction(action: AutoAction) {
        viewModelScope.launch { prefs.setAutoAction(action) }
    }
}
