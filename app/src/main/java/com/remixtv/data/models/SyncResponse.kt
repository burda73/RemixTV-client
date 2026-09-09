package com.remixtv.data.models

import com.google.gson.annotations.SerializedName

/**
 * Ответ GET /api/sync
 */
data class SyncResponse(
    @SerializedName("playlist_version")
    val playlistVersion: Int,
    @SerializedName("items")
    val items: List<SyncVideoDto>
)

/**
 * Элемент массива items из ответа синхронизации.
 */
data class SyncVideoDto(
    @SerializedName("playlist_item_id")
    val playlistItemId: Int,
    @SerializedName("video_id")
    val videoId: Int,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("download_path")
    val downloadPath: String,
    @SerializedName("file_hash")
    val fileHash: String,
    @SerializedName("version")
    val version: Int,
    @SerializedName("file_size")
    val fileSize: Long,
    @SerializedName("order")
    val order: Int
)

data class ClientStatusBody(
    @SerializedName("detail")
    val detail: String?,
    @SerializedName("synced_playlist_version")
    val syncedPlaylistVersion: Int?
)
