package com.remixtv.utils

import com.remixtv.data.database.VideoDao
import com.remixtv.data.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Управление каталогом кэша и LRU-очисткой по суммарному размеру.
 */
class CacheManager(
    private val cacheDir: File,
    private val videoDao: VideoDao
) {

    fun videoFileFor(videoId: Int, filename: String): File {
        return File(cacheDir, "${videoId}_$filename")
    }

    suspend fun enforceBudgetKeeping(
        activeVideoIds: Set<Int>,
        maxTotalBytes: Long
    ) = withContext(Dispatchers.IO) {
        val videos = videoDao.getAllOnce().filter { it.isDownloaded && it.localPath != null }
        val candidates = videos
            .filter { it.id !in activeVideoIds }
            .mapNotNull { v ->
                val f = v.localPath?.let { File(it) }
                if (f != null && f.exists()) Triple(v, f, f.lastModified()) else null
            }
            .sortedBy { it.third }

        var total = videos.sumOf { v ->
            val p = v.localPath ?: return@sumOf 0L
            File(p).length()
        }

        for ((video, file, _) in candidates) {
            if (total <= maxTotalBytes) break
            val len = file.length()
            if (file.delete()) {
                total -= len
                videoDao.upsert(
                    video.copy(localPath = null, isDownloaded = false)
                )
            }
        }

        // Если всё ещё перебор — удаляем самые старые среди активных (крайний случай)
        if (total > maxTotalBytes) {
            val activeFiles = videos
                .filter { it.id in activeVideoIds }
                .mapNotNull { v ->
                    val f = v.localPath?.let { File(it) } ?: return@mapNotNull null
                    if (f.exists()) Triple(v, f, f.lastModified()) else null
                }
                .sortedBy { it.third }
            for ((video, file, _) in activeFiles) {
                if (total <= maxTotalBytes) break
                val len = file.length()
                if (file.delete()) {
                    total -= len
                    videoDao.upsert(video.copy(localPath = null, isDownloaded = false))
                }
            }
        }
    }

    suspend fun deleteVideoFile(video: Video) = withContext(Dispatchers.IO) {
        val path = video.localPath ?: return@withContext
        File(path).delete()
        videoDao.upsert(video.copy(localPath = null, isDownloaded = false))
    }
}
