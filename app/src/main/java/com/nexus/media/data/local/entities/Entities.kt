package com.nexus.media.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "media", indices = [Index("mediaType"), Index("folderPath")])
data class MediaEntity(
    @PrimaryKey val id: String,          // a_<id>, v_<id> (MediaStore) or s_<hash> (SAF)
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val mediaType: String,               // "audio" | "video"
    val artworkUri: String?,
    val folderPath: String,
    val dateAdded: Long,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val genre: String = "",
    val isMusic: Boolean = true
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "playlist_media", primaryKeys = ["playlistId", "mediaId"])
data class PlaylistMediaCrossRef(
    val playlistId: Long,
    val mediaId: String,
    val position: Int
)

@Entity(tableName = "history", indices = [Index("mediaId", unique = true)])
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val playbackPosition: Long,
    val lastPlayed: Long
)
