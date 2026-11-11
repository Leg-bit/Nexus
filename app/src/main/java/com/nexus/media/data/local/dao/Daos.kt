package com.nexus.media.data.local.dao

import androidx.room.*
import com.nexus.media.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MediaDao {
    @Query("SELECT * FROM media WHERE mediaType = :type ORDER BY title COLLATE NOCASE")
    abstract fun observe(type: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY title COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE LIMIT 200")
    abstract fun search(q: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE (title || '|' || duration) IN (SELECT title || '|' || duration FROM media GROUP BY title, duration HAVING COUNT(*) > 1) ORDER BY title COLLATE NOCASE")
    abstract fun duplicates(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    abstract suspend fun getMany(ids: List<String>): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<MediaEntity>)

    @Query("DELETE FROM media")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<MediaEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observe(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(p: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_media WHERE playlistId = :id")
    suspend fun clearRefs(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRef(r: PlaylistMediaCrossRef)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_media WHERE playlistId = :id")
    suspend fun nextPos(id: Long): Int

    @Query("DELETE FROM playlist_media WHERE playlistId = :pid AND mediaId = :mid")
    suspend fun removeRef(pid: Long, mid: String)

    @Query("UPDATE playlists SET updatedAt = :t WHERE id = :id")
    suspend fun touch(id: Long, t: Long)

    @Query("SELECT m.* FROM media m INNER JOIN playlist_media r ON m.id = r.mediaId WHERE r.playlistId = :id ORDER BY r.position")
    fun observeMedia(id: Long): Flow<List<MediaEntity>>
}

@Dao
interface HistoryDao {
    @Query("SELECT m.* FROM media m INNER JOIN history h ON m.id = h.mediaId ORDER BY h.lastPlayed DESC LIMIT 40")
    fun recentAll(): Flow<List<MediaEntity>>

    @Query("SELECT m.* FROM media m INNER JOIN history h ON m.id = h.mediaId WHERE m.mediaType = 'video' AND h.playbackPosition > 5000 AND h.playbackPosition < m.duration - 10000 ORDER BY h.lastPlayed DESC LIMIT 20")
    fun continueWatching(): Flow<List<MediaEntity>>

    @Query("SELECT playbackPosition FROM history WHERE mediaId = :id")
    suspend fun position(id: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(h: PlaybackHistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}
