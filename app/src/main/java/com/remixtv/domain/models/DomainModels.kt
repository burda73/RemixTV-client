package com.remixtv.domain.models

import com.remixtv.data.models.PlaylistItem
import com.remixtv.data.models.Video

/**
 * Доменная модель: элемент очереди воспроизведения с метаданными файла.
 */
data class PlayableItem(
    val playlistItem: PlaylistItem,
    val video: Video
)

data class DownloadState(
    val videoId: Int?,
    val bytesRead: Long,
    val totalBytes: Long,
    val isDownloading: Boolean,
    val lastError: String?
)
