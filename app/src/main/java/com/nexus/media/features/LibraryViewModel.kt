package com.nexus.media.features

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(private val c: AppContainer) : ViewModel() {
    private fun <T> Flow<T>.hot(init: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)

    val music = c.repository.music.hot(emptyList())
    val videos = c.repository.videos.hot(emptyList())
    val all = c.repository.all.hot(emptyList())
    val duplicates = c.repository.duplicates.hot(emptyList())
    val playlists = c.repository.playlists().hot(emptyList())
    val recent = c.db.history().recentAll().hot(emptyList())
    val continueWatching = c.db.history().continueWatching().hot(emptyList())

    fun search(q: String): Flow<List<MediaEntity>> = c.repository.search(q)
    fun playlistMedia(id: Long) = c.repository.playlistMedia(id)

    fun createPlaylist(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { c.repository.createPlaylist(name) }
    }
    fun deletePlaylist(id: Long) { viewModelScope.launch { c.repository.deletePlaylist(id) } }
    fun removeFromPlaylist(pid: Long, mid: String) { viewModelScope.launch { c.repository.removeFromPlaylist(pid, mid) } }
    fun clearHistory() { viewModelScope.launch { c.db.history().clear() } }
}
