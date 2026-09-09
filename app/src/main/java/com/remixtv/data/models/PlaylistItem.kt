package com.remixtv.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Элемент плейлиста на устройстве. Связан с [Video] по video_id.
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Video::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("video_id"), Index(value = ["playlist_item_id", "order"])]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    @ColumnInfo(name = "playlist_item_id")
    val playlistItemId: Int,
    @ColumnInfo(name = "video_id")
    val videoId: Int,
    val order: Int
)
