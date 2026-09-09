package com.remixtv.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remixtv.RemixTVApplication
import com.remixtv.utils.AppDebugLog
import com.remixtv.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RemixTVApplication

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    val deviceIp: String?
        get() = NetworkUtils.getDeviceIpv4()

    fun saveServerUrl(url: String) {
        AppDebugLog.log("SettingsVM", "saveServerUrl: $url")
        app.preferencesManager.serverUrl = url.trim()
        app.playlistRepository.rebuildApi()
    }

    fun savePlayerName(name: String) {
        AppDebugLog.log("SettingsVM", "savePlayerName: $name")
        app.preferencesManager.playerName = name
    }

    fun setDebugEnabled(enabled: Boolean) {
        AppDebugLog.log("SettingsVM", "debugEnabled=$enabled")
        app.preferencesManager.debugEnabled = enabled
    }

    fun setBootStartEnabled(enabled: Boolean) {
        AppDebugLog.log("SettingsVM", "bootStartEnabled=$enabled")
        app.preferencesManager.bootStartEnabled = enabled
    }

    fun saveCacheMaxMb(mb: Int) {
        AppDebugLog.log("SettingsVM", "saveCacheMaxMb: $mb")
        app.preferencesManager.cacheMaxMb = mb
    }

    fun saveAutoSyncMinutes(minutes: Int) {
        AppDebugLog.log("SettingsVM", "saveAutoSyncMinutes: $minutes")
        app.preferencesManager.autoSyncIntervalMinutes = minutes
    }

    fun syncNow() {
        viewModelScope.launch {
            AppDebugLog.log("SettingsVM", "syncNow: запуск")
            _status.value = "Синхронизация…"
            val result = app.syncPlaylistUseCase()
            _status.value = if (result.isSuccess) {
                "Готово"
            } else {
                "Ошибка: ${result.exceptionOrNull()?.message ?: "неизвестно"}"
            }
            AppDebugLog.log("SettingsVM", "syncNow: итог ${_status.value}")
        }
    }
}
