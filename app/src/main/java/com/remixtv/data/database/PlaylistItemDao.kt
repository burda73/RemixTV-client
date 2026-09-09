package com.remixtv.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.remixtv.data.models.PlaylistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistItem>)

    @Query("DELETE FROM playlist_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<PlaylistItem>) {
        deleteAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }

    @Query(
        """
        SELECT pi.* FROM playlist_items pi
        INNER JOIN videos v ON v.id = pi.video_id
        ORDER BY pi.`order` ASC
        """
    )
    fun observeActivePlaylist(): Flow<List<PlaylistItem>>

    @Query(
        """
        SELECT pi.* FROM playlist_items pi
        INNER JOIN videos v ON v.id = pi.video_id
        ORDER BY pi.`order` ASC
        """
    )
    suspend fun getActivePlaylistOnce(): List<PlaylistItem>

    @Transaction
    @Query(
        """
        SELECT pi.* FROM playlist_items pi
        INNER JOIN videos v ON v.id = pi.video_id
        ORDER BY pi.`order` ASC
        """
    )
    fun observeActivePlaylistWithVideos(): Flow<List<ActivePlaylistEntry>>

    @Transaction
    @Query(
        """
        SELECT pi.* FROM playlist_items pi
        INNER JOIN videos v ON v.id = pi.video_id
        ORDER BY pi.`order` ASC
        """
    )
    suspend fun getActivePlaylistWithVideosOnce(): List<ActivePlaylistEntry>
}
