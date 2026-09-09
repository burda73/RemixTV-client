package com.remixtv.data.network

import com.remixtv.data.models.Video
import com.remixtv.utils.AppDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed class DownloadProgress {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : DownloadProgress()
    data class Completed(val file: File) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
}

/**
 * Скачивание бинарного потока с проверкой MD5 (серверный file_hash).
 * Заголовки X-Video-* опционально используются для контроля.
 */
class DownloadManager(
    private val okHttpClient: OkHttpClient
) {

    fun downloadVideoFile(
        baseUrl: String,
        video: Video,
        destination: File,
        relativePath: String = "api/download/${video.id}"
    ): Flow<DownloadProgress> = flow {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val url = normalizedBase.trimEnd('/') + "/" + relativePath.trimStart('/')
        AppDebugLog.log("OkHttp", "GET $url")
        val request = Request.Builder().url(url).get().build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppDebugLog.log("OkHttp", "ответ ${response.code} для video id=${video.id}")
                    emit(DownloadProgress.Failed("HTTP ${response.code}"))
                    return@use
                }
                val body = response.body ?: run {
                    emit(DownloadProgress.Failed("Пустое тело ответа"))
                    return@use
                }
                val headerSize = response.header("X-Video-Size")?.toLongOrNull()
                val contentLength = body.contentLength()
                val total = when {
                    headerSize != null && headerSize > 0 -> headerSize
                    contentLength > 0 -> contentLength
                    video.fileSize > 0 -> video.fileSize
                    else -> -1L
                }

                destination.parentFile?.mkdirs()
                if (destination.exists()) {
                    destination.delete()
                }

                var readTotal = 0L
                body.source().use { source ->
                    destination.sink().buffer().use { sink ->
                        val buffer = okio.Buffer()
                        var read: Long
                        while (true) {
                            read = source.read(buffer, 8192L)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            readTotal += read
                            if (total > 0) {
                                emit(DownloadProgress.Progress(readTotal, total))
                            }
                        }
                        sink.flush()
                    }
                }

                val md5Local = md5Hex(destination)
                if (!md5Local.equals(video.fileHash, ignoreCase = true)) {
                    AppDebugLog.log("OkHttp", "MD5 не совпал id=${video.id}")
                    destination.delete()
                    emit(DownloadProgress.Failed("Неверный MD5: ожидали ${video.fileHash}, получили $md5Local"))
                    return@use
                }

                AppDebugLog.log("OkHttp", "файл записан id=${video.id} bytes=${destination.length()}")
                emit(DownloadProgress.Completed(destination))
            }
        }.onFailure { e ->
            AppDebugLog.log("OkHttp", "исключение: ${e.message}")
            emit(DownloadProgress.Failed(e.message ?: "Ошибка загрузки"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun md5Hex(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        digest.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
