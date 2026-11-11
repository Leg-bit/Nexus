package com.nexus.media.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.media.data.local.dao.HistoryDao
import com.nexus.media.data.local.dao.MediaDao
import com.nexus.media.data.local.dao.PlaylistDao
import com.nexus.media.data.local.entities.*

@Database(
    entities = [MediaEntity::class, PlaylistEntity::class, PlaylistMediaCrossRef::class, PlaybackHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun playlists(): PlaylistDao
    abstract fun history(): HistoryDao
}
