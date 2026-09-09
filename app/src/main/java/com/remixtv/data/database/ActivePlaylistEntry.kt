package com.remixtv.data.database

import androidx.room.Embedded
import androidx.room.Relation
import com.remixtv.data.models.PlaylistItem
import com.remixtv.data.models.Video

/**
 * Строка активного плейлиста с привязанным видео (Room @Relation).
 */
data class ActivePlaylistEntry(
    @Embedded val item: PlaylistItem,
    @Relation(
        parentColumn = "video_id",
        entityColumn = "id",
        entity = Video::class
    )
    val videos: List<Video>
) {
    val video: Video?
        get() = videos.firstOrNull()
}
