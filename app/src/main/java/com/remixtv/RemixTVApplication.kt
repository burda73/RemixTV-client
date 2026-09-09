package com.remixtv

import android.app.Application
import com.remixtv.data.repository.PlaylistRepository
import com.remixtv.domain.usercases.DownloadVideoUseCase
import com.remixtv.domain.usercases.SyncPlaylistUseCase
import com.remixtv.utils.AppDebugLog
import com.remixtv.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RemixTVApplication : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var playlistRepository: PlaylistRepository
        private set

    lateinit var syncPlaylistUseCase: SyncPlaylistUseCase
        private set

    lateinit var downloadVideoUseCase: DownloadVideoUseCase
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        AppDebugLog.setOutputEnabled(preferencesManager.debugEnabled)
        AppDebugLog.log("Application", "onCreate")

        playlistRepository = PlaylistRepository.create(this, preferencesManager, appScope)
        syncPlaylistUseCase = SyncPlaylistUseCase(playlistRepository)
        downloadVideoUseCase = DownloadVideoUseCase(playlistRepository)

        appScope.launch(Dispatchers.IO) {
            AppDebugLog.log("Application", "первичная синхронизация")
            val first = syncPlaylistUseCase()
            AppDebugLog.log("Application", "первичная синхронизация завершена: ok=${first.isSuccess}")
            while (isActive) {
                val min = preferencesManager.autoSyncIntervalMinutes
                delay(min * 60_000L)
                AppDebugLog.log("Application", "таймер: авто-синхронизация каждые $min мин")
                val r = syncPlaylistUseCase()
                AppDebugLog.log("Application", "авто-синхронизация: ok=${r.isSuccess}")
            }
        }
    }

    override fun onTerminate() {
        AppDebugLog.log("Application", "onTerminate")
        appScope.cancel()
        super.onTerminate()
    }
}
