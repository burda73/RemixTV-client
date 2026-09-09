package com.remixtv.data.repository

import android.content.Context
import com.remixtv.data.database.ActivePlaylistEntry
import com.remixtv.data.database.AppDatabase
import com.remixtv.data.models.ClientStatusBody
import com.remixtv.data.models.PlaylistItem
import com.remixtv.data.models.SyncVideoDto
import com.remixtv.data.models.Video
import com.remixtv.data.network.ApiService
import com.remixtv.data.network.DownloadManager
import com.remixtv.data.network.DownloadProgress
import com.remixtv.data.network.NetworkClient
import com.remixtv.domain.models.DownloadState
import com.remixtv.utils.AppDebugLog
import com.remixtv.utils.CacheManager
import com.remixtv.utils.NetworkUtils
import com.remixtv.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Единая точка доступа: сеть + Room + кэш + загрузки.
 */
class PlaylistRepository(
    private val appContext: Context,
    private val db: AppDatabase,
    private val prefs: PreferencesManager,
    private val okHttp: OkHttpClient,
    private val downloadManager: DownloadManager,
    private val cacheManager: CacheManager,
    private val externalScope: CoroutineScope
) {
    private var api: ApiService = NetworkClient.createApiService(prefs.serverUrl, okHttp)

    private val videoDao = db.videoDao()
    private val playlistDao = db.playlistItemDao()

    private val syncMutex = Mutex()
    private var serverRetryJob: Job? = null

    private val downloadState = MutableStateFlow(
        DownloadState(videoId = null, bytesRead = 0, totalBytes = 0, isDownloading = false, lastError = null)
    )

    fun observeDownloadState(): StateFlow<DownloadState> = downloadState.asStateFlow()

    suspend fun getVideoById(videoId: Int): Video? = videoDao.getById(videoId)

    /**
     * Активный плейлист с видео.
     */
    fun observePlayableEntries(): Flow<List<ActivePlaylistEntry>> {
        val ticker = flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(30_000L)
            }
        }.onStart { emit(Unit) }

        return ticker.flatMapLatest {
            playlistDao.observeActivePlaylistWithVideos()
        }.distinctUntilChanged()
    }

    fun rebuildApi() {
        AppDebugLog.log("Repository", "rebuildApi baseUrl=${prefs.serverUrl}")
        api = NetworkClient.createApiService(prefs.serverUrl, okHttp)
    }

    /**
     * Синхронизация с сервером. При офлайне — тихий выход (UI играет из кэша).
     * При ошибке сервера — повтор через 30 секунд.
     */
    suspend fun syncPlaylist(): Result<Unit> = syncMutex.withLock {
        AppDebugLog.log("Repository", "syncPlaylist: старт clientId=${prefs.clientId}")
        rebuildApi()
        if (!NetworkUtils.isOnline(appContext)) {
            AppDebugLog.log("Repository", "syncPlaylist: офлайн — только кэш")
            return@withLock Result.failure(IllegalStateException("offline"))
        }
        return@withLock try {
            val response = api.sync(prefs.clientId)
            AppDebugLog.log("Repository", "syncPlaylist: ответ, роликов=${response.items.size}")
            applySyncPayload(response.items)
            try {
                api.postStatus(
                    prefs.clientId,
                    ClientStatusBody(detail = "synced", syncedPlaylistVersion = response.playlistVersion)
                )
                AppDebugLog.log("Repository", "postStatus: OK")
            } catch (ex: Exception) {
                AppDebugLog.log("Repository", "postStatus: сбой ${ex.message}")
            }
            serverRetryJob?.cancel()
            AppDebugLog.log("Repository", "syncPlaylist: успех")
            Result.success(Unit)
        } catch (e: Exception) {
            AppDebugLog.log("Repository", "syncPlaylist: ошибка ${e.javaClass.simpleName}: ${e.message}")
            scheduleServerRetry()
            Result.failure(e)
        }
    }

    private fun scheduleServerRetry() {
        if (serverRetryJob?.isActive == true) return
        AppDebugLog.log("Repository", "повтор синхронизации через 30 с")
        serverRetryJob = externalScope.launch(Dispatchers.IO) {
            delay(30_000L)
            syncPlaylist()
        }
    }

    /**
     * Применяем ответ синхронизации: БД + кэш + фоновые загрузки.
     */
    private suspend fun applySyncPayload(dtos: List<SyncVideoDto>) = withContext(Dispatchers.IO) {
        AppDebugLog.log("Repository", "applySyncPayload: записей=${dtos.size}")
        val distinctVideos = dtos.distinctBy { it.videoId }.map { it.toVideoStub() }
        videoDao.replaceVideosKeepingLocalState(distinctVideos)

        val items = dtos.map { dto ->
            PlaylistItem(
                rowId = 0,
                playlistItemId = dto.playlistItemId,
                videoId = dto.videoId,
                order = dto.order
            )
        }
        playlistDao.replaceAll(items)

        val ids = distinctVideos.map { it.id }
        if (ids.isNotEmpty()) {
            val toDelete = videoDao.getAllOnce().filter { it.id !in ids }
            for (v in toDelete) {
                v.localPath?.let { path ->
                    val f = File(path)
                    if (f.exists()) {
                        AppDebugLog.log("Repository", "удалить файл ${f.name}")
                        f.delete()
                    }
                }
            }
            videoDao.deleteNotIn(ids)
        } else {
            val allVideos = videoDao.getAllOnce()
            for (v in allVideos) {
                v.localPath?.let { path ->
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            }
            videoDao.deleteAll()
            playlistDao.deleteAll()
        }

        val maxBytes = prefs.cacheMaxMb.toLong() * 1024L * 1024L
        cacheManager.enforceBudgetKeeping(ids.toSet(), maxBytes)

        // Проверка целостности уже скачанных
        for (v in videoDao.getAllOnce().filter { it.isDownloaded }) {
            val path = v.localPath ?: continue
            val f = File(path)
            if (!f.exists()) {
                AppDebugLog.log("Repository", "файл пропал с диска id=${v.id}, сброс локального")
                videoDao.upsert(v.copy(isDownloaded = false, localPath = null))
            }
        }

        externalScope.launch(Dispatchers.IO) {
            queueDownloads(dtos)
        }
        AppDebugLog.log("Repository", "applySyncPayload: готово, уникальных видео=${distinctVideos.size}")
    }

    private suspend fun queueDownloads(dtos: List<SyncVideoDto>) {
        AppDebugLog.log("Repository", "queueDownloads: проверка очереди загрузок")
        val entries = playlistDao.getActivePlaylistWithVideosOnce()
        for (e in entries) {
            val v = e.video ?: continue
            val needs = !v.isDownloaded || v.localPath.isNullOrBlank() || !File(v.localPath).exists()
            if (needs) {
                val dto = dtos.find { it.videoId == v.id }
                val downloadPath = dto?.downloadPath ?: "api/download/${v.id}"
                AppDebugLog.log("Repository", "очередь: скачать id=${v.id} ${v.filename}")
                downloadAndVerify(v, downloadPath)
            }
        }
        AppDebugLog.log("Repository", "queueDownloads: конец")
    }

    /**
     * Скачивание одного ролика с повтором при неверном хеше (DownloadManager сам пересчитает MD5).
     */
    suspend fun downloadAndVerify(video: Video, downloadPath: String = "api/download/${video.id}") {
        var current = video
        var attempt = 0
        while (attempt < 3) {
            val dest = cacheManager.videoFileFor(current.id, current.filename)
            AppDebugLog.log("Download", "старт id=${current.id} попытка=${attempt + 1} → ${dest.name}")
            downloadState.value = DownloadState(current.id, 0, current.fileSize, true, null)
            var completed = false
            downloadManager.downloadVideoFile(prefs.serverUrl, current, dest, downloadPath).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        downloadState.value = DownloadState(
                            current.id,
                            progress.bytesRead,
                            progress.totalBytes,
                            true,
                            null
                        )
                    }
                    is DownloadProgress.Completed -> {
                        val updated = current.copy(
                            localPath = progress.file.absolutePath,
                            isDownloaded = true
                        )
                        videoDao.upsert(updated)
                        downloadState.value = DownloadState(null, 0, 0, false, null)
                        completed = true
                        AppDebugLog.log("Download", "готово id=${current.id} path=${progress.file.absolutePath}")
                    }
                    is DownloadProgress.Failed -> {
                        downloadState.value = DownloadState(
                            current.id,
                            downloadState.value.bytesRead,
                            downloadState.value.totalBytes,
                            false,
                            progress.message
                        )
                        AppDebugLog.log("Download", "ошибка id=${current.id}: ${progress.message}")
                    }
                }
            }
            if (completed) return
            attempt++
            current = current.copy(isDownloaded = false, localPath = null)
            delay(2_000L)
        }
        AppDebugLog.log("Download", "id=${video.id}: исчерпаны попытки")
    }

    private fun SyncVideoDto.toVideoStub(): Video = Video(
        id = videoId,
        filename = filename,
        fileHash = fileHash,
        version = version,
        fileSize = fileSize,
        localPath = null,
        isDownloaded = false
    )

    companion object {
        fun create(
            context: Context,
            prefs: PreferencesManager,
            externalScope: CoroutineScope
        ): PlaylistRepository {
            val db = AppDatabase.getInstance(context)
            val cacheDir = File(context.filesDir, "video_cache").apply { mkdirs() }
            val cacheManager = CacheManager(cacheDir, db.videoDao())
            val okHttp = NetworkClient.createOkHttpClient()
            val dm = DownloadManager(okHttp)
            return PlaylistRepository(
                appContext = context.applicationContext,
                db = db,
                prefs = prefs,
                okHttp = okHttp,
                downloadManager = dm,
                cacheManager = cacheManager,
                externalScope = externalScope
            )
        }
    }
}
