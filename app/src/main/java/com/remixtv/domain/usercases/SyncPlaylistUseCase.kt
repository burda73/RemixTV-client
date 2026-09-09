package com.remixtv.domain.usercases

import com.remixtv.data.repository.PlaylistRepository
import com.remixtv.utils.AppDebugLog

/**
 * Сценарий синхронизации плейлиста с сервером.
 */
class SyncPlaylistUseCase(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke() = run {
        AppDebugLog.log("UseCase", "SyncPlaylistUseCase.invoke()")
        repository.syncPlaylist()
    }
}
