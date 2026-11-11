package com.nexus.media

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexus.media.data.local.database.NexusDatabase
import com.nexus.media.data.local.entities.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaDaoTest {
    private lateinit var db: NexusDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NexusDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private fun m(id: String, title: String, dur: Long = 1000L, type: String = "audio") =
        MediaEntity(id, "content://x/$id", title, "A", "B", dur, type, null, "/Music", 0)

    @Test fun searchFindsTitle() = runBlocking<Unit> {
        db.media().replaceAll(listOf(m("a_1", "Hello"), m("a_2", "World")))
        assertEquals(listOf("a_1"), db.media().search("hell").first().map { it.id })
    }

    @Test fun detectsDuplicates() = runBlocking<Unit> {
        db.media().replaceAll(listOf(m("a_1", "Same", 5000), m("a_2", "Same", 5000), m("a_3", "Other", 5000)))
        assertEquals(2, db.media().duplicates().first().size)
    }

    @Test fun playlistKeepsOrder() = runBlocking<Unit> {
        db.media().replaceAll(listOf(m("a_1", "One"), m("a_2", "Two")))
        val pid = db.playlists().insert(PlaylistEntity(name = "p", createdAt = 0, updatedAt = 0))
        db.playlists().addRef(PlaylistMediaCrossRef(pid, "a_2", 0))
        db.playlists().addRef(PlaylistMediaCrossRef(pid, "a_1", 1))
        assertEquals(listOf("a_2", "a_1"), db.playlists().observeMedia(pid).first().map { it.id })
    }

    @Test fun continueWatchingNeedsPartialProgress() = runBlocking<Unit> {
        db.media().replaceAll(listOf(m("v_1", "Film", 600_000, "video")))
        db.history().upsert(PlaybackHistoryEntity(mediaId = "v_1", playbackPosition = 120_000, lastPlayed = 1))
        assertEquals(1, db.history().continueWatching().first().size)
        db.history().upsert(PlaybackHistoryEntity(mediaId = "v_1", playbackPosition = 599_000, lastPlayed = 2))
        assertEquals(0, db.history().continueWatching().first().size)
    }
}
