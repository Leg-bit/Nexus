package com.nexus.media.media.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.nexus.media.data.local.entities.MediaEntity
import java.io.File
import android.provider.MediaStore.Audio.Media as AM
import android.provider.MediaStore.Video.Media as VM

/** Reads real media from Android storage: MediaStore + Storage Access Framework trees. */
class MediaScanner(private val ctx: Context) {

    private fun clean(s: String?, fallback: String) =
        if (s.isNullOrBlank() || s == "<unknown>") fallback else s

    fun scanAudio(): List<MediaEntity> {
        val base = AM.EXTERNAL_CONTENT_URI
        val proj = mutableListOf(
            AM._ID, AM.TITLE, AM.ARTIST, AM.ALBUM, AM.ALBUM_ID, AM.DURATION,
            AM.DATE_ADDED, AM.SIZE, AM.MIME_TYPE, AM.DATA, AM.IS_MUSIC
        )
        if (Build.VERSION.SDK_INT >= 30) proj.add("genre")
        val sel = "${AM.IS_RINGTONE}=0 AND ${AM.IS_NOTIFICATION}=0 AND ${AM.IS_ALARM}=0 AND ${AM.DURATION}>1000"
        val out = ArrayList<MediaEntity>()
        ctx.contentResolver.query(base, proj.toTypedArray(), sel, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(AM._ID)
            val iTitle = c.getColumnIndexOrThrow(AM.TITLE)
            val iArtist = c.getColumnIndexOrThrow(AM.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(AM.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(AM.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(AM.DURATION)
            val iAdded = c.getColumnIndexOrThrow(AM.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(AM.SIZE)
            val iMime = c.getColumnIndexOrThrow(AM.MIME_TYPE)
            val iData = c.getColumnIndexOrThrow(AM.DATA)
            val iGenre = c.getColumnIndex("genre")
            val iMusic = c.getColumnIndexOrThrow(AM.IS_MUSIC)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out.add(
                    MediaEntity(
                        id = "a_$id",
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        title = clean(c.getString(iTitle), "Tanpa judul"),
                        artist = clean(c.getString(iArtist), "Artis tidak diketahui"),
                        album = clean(c.getString(iAlbum), "Album tidak diketahui"),
                        duration = c.getLong(iDur),
                        mediaType = "audio",
                        artworkUri = "content://media/external/audio/albumart/${c.getLong(iAlbumId)}",
                        folderPath = c.getString(iData)?.let { File(it).parent } ?: "Lainnya",
                        dateAdded = c.getLong(iAdded),
                        size = c.getLong(iSize),
                        mimeType = c.getString(iMime) ?: "",
                        genre = if (iGenre >= 0) c.getString(iGenre) ?: "" else "",
                        isMusic = c.getInt(iMusic) != 0
                    )
                )
            }
        }
        return out
    }

    fun scanVideo(): List<MediaEntity> {
        val base = VM.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            VM._ID, VM.TITLE, VM.DURATION, VM.SIZE, VM.WIDTH, VM.HEIGHT,
            VM.MIME_TYPE, VM.DATE_ADDED, VM.DATA
        )
        val out = ArrayList<MediaEntity>()
        ctx.contentResolver.query(base, proj, "${VM.DURATION}>0", null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(VM._ID)
            val iTitle = c.getColumnIndexOrThrow(VM.TITLE)
            val iDur = c.getColumnIndexOrThrow(VM.DURATION)
            val iSize = c.getColumnIndexOrThrow(VM.SIZE)
            val iW = c.getColumnIndexOrThrow(VM.WIDTH)
            val iH = c.getColumnIndexOrThrow(VM.HEIGHT)
            val iMime = c.getColumnIndexOrThrow(VM.MIME_TYPE)
            val iAdded = c.getColumnIndexOrThrow(VM.DATE_ADDED)
            val iData = c.getColumnIndexOrThrow(VM.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out.add(
                    MediaEntity(
                        id = "v_$id",
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        title = clean(c.getString(iTitle), "Video"),
                        artist = "",
                        album = "",
                        duration = c.getLong(iDur),
                        mediaType = "video",
                        artworkUri = null,
                        folderPath = c.getString(iData)?.let { File(it).parent } ?: "Lainnya",
                        dateAdded = c.getLong(iAdded),
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        mimeType = c.getString(iMime) ?: ""
                    )
                )
            }
        }
        return out
    }

    /** Scan a folder the user picked through the Storage Access Framework. */
    fun scanTree(tree: Uri): List<MediaEntity> {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyList()
        val out = ArrayList<MediaEntity>()
        fun walk(dir: DocumentFile, path: String) {
            for (f in dir.listFiles()) {
                if (f.isDirectory) {
                    walk(f, path + "/" + (f.name ?: "?"))
                } else {
                    val mime = f.type ?: continue
                    if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                        extract(f, mime, path)?.let { out.add(it) }
                    }
                }
            }
        }
        walk(root, root.name ?: "Folder")
        return out
    }

    private fun extract(f: DocumentFile, mime: String, path: String): MediaEntity? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, f.uri)
            val isVideo = mime.startsWith("video/")
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (dur <= 0) {
                null
            } else {
                val name = (f.name ?: "Media").substringBeforeLast('.')
                MediaEntity(
                    id = "s_" + Integer.toHexString(f.uri.toString().hashCode()),
                    uri = f.uri.toString(),
                    title = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), name),
                    artist = if (isVideo) "" else clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), "Artis tidak diketahui"),
                    album = if (isVideo) "" else clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), "Album tidak diketahui"),
                    duration = dur,
                    mediaType = if (isVideo) "video" else "audio",
                    artworkUri = null,
                    folderPath = path,
                    dateAdded = f.lastModified() / 1000,
                    size = f.length(),
                    width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    mimeType = mime,
                    genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
                )
            }
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }
}
