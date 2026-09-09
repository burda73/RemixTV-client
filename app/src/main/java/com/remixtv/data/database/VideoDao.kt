package com.remixtv.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.remixtv.data.models.Video
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    suspend fun getById(videoId: Int): Video?

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    fun observeById(videoId: Int): Flow<Video?>

    @Query("SELECT * FROM videos")
    fun observeAll(): Flow<List<Video>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(videos: List<Video>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: Video)

    @Update
    suspend fun update(video: Video)

    @Query("DELETE FROM videos WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<Int>)

    @Query("DELETE FROM videos")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceVideosKeepingLocalState(incoming: List<Video>) {
        val existing = getAllOnce()
        val existingById = existing.associateBy { it.id }
        val merged = incoming.map { v ->
            val old = existingById[v.id]
            if (old != null &&
                old.fileHash == v.fileHash &&
                old.version == v.version &&
                old.isDownloaded &&
                old.localPath != null
            ) {
                // old здесь уже не nullable — smart cast
                v.copy(localPath = old.localPath, isDownloaded = true)
            } else {
                // Сбрасываем локальное состояние при смене версии/хеша
                v.copy(localPath = null, isDownloaded = false)
            }
        }
        upsertAll(merged)
    }

    @Query("SELECT * FROM videos")
    suspend fun getAllOnce(): List<Video>
}
