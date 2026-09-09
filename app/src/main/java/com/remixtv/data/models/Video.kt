package com.remixtv.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальная сущность видео (Room).
 * [id] соответствует video_id на сервере.
 */
@Entity(tableName = "videos")
data class Video(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    val filename: String,
    @ColumnInfo(name = "file_hash")
    val fileHash: String,
    val version: Int,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    /** Абсолютный путь к скачанному файлу или null, если ещё не скачано */
    @ColumnInfo(name = "local_path")
    val localPath: String?,
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean
)
