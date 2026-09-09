package com.remixtv.utils

import android.content.Context
import android.content.SharedPreferences
import com.remixtv.data.network.NetworkClient
import java.util.UUID

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, NetworkClient.DEFAULT_BASE_URL)
            ?: NetworkClient.DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value).apply()
        }

    var clientId: String
        get() {
            val existing = prefs.getString(KEY_CLIENT_ID, null)
            if (existing != null) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, created).apply()
            return created
        }
        set(value) {
            prefs.edit().putString(KEY_CLIENT_ID, value).apply()
        }

    /** Отображаемое имя проигрывателя (уведомление и метаданные без имён файлов) */
    var playerName: String
        get() = prefs.getString(KEY_PLAYER_NAME, DEFAULT_PLAYER_NAME) ?: DEFAULT_PLAYER_NAME
        set(value) {
            val n = value.trim().ifBlank { DEFAULT_PLAYER_NAME }
            prefs.edit().putString(KEY_PLAYER_NAME, n).apply()
        }

    /** Панель отладки и запись в буфер AppDebugLog */
    var debugEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEBUG_ENABLED, value).apply()
        }

    /** Запуск приложения после загрузки системы */
    var bootStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_START, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BOOT_START, value).apply()
        }

    /** Лимит кэша видео, МБ */
    var cacheMaxMb: Int
        get() = prefs.getInt(KEY_CACHE_MAX_MB, 2048)
        set(value) {
            prefs.edit().putInt(KEY_CACHE_MAX_MB, value.coerceAtLeast(128)).apply()
        }

    /** Период авто-синхронизации, минут */
    var autoSyncIntervalMinutes: Int
        get() = prefs.getInt(KEY_AUTO_SYNC_MIN, 5)
        set(value) {
            prefs.edit().putInt(KEY_AUTO_SYNC_MIN, value.coerceAtLeast(1)).apply()
        }

    companion object {
        const val PREFS_NAME = "remixtv_prefs"
        private const val DEFAULT_PLAYER_NAME = "RemixTV"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_PLAYER_NAME = "player_name"
        private const val KEY_DEBUG_ENABLED = "debug_enabled"
        private const val KEY_BOOT_START = "boot_start_enabled"
        private const val KEY_CACHE_MAX_MB = "cache_max_mb"
        private const val KEY_AUTO_SYNC_MIN = "auto_sync_min"
    }
}
