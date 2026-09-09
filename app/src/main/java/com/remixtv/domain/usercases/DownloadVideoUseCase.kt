package com.remixtv.domain.usercases

import com.remixtv.data.models.Video
import com.remixtv.data.repository.PlaylistRepository
import com.remixtv.utils.AppDebugLog

/**
 * Принудительное скачивание/перескачивание ролика с проверкой MD5.
 */
class DownloadVideoUseCase(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(video: Video) {
        AppDebugLog.log("UseCase", "DownloadVideoUseCase id=${video.id} ${video.filename}")
        repository.downloadAndVerify(video)
    }
}
