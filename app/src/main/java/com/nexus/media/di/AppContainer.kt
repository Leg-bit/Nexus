package com.nexus.media.di

import android.app.Application
import androidx.room.Room
import com.nexus.media.data.local.database.NexusDatabase
import com.nexus.media.data.preferences.UserPreferences
import com.nexus.media.data.repository.MediaRepository
import com.nexus.media.media.scanner.MediaScanner
import com.nexus.media.player.PlayerManager
import com.nexus.media.player.audio.EqualizerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (can be migrated to Hilt later). */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = UserPreferences(app)
    val db: NexusDatabase = Room.databaseBuilder(app, NexusDatabase::class.java, "nexus.db").fallbackToDestructiveMigration().build()
    val scanner = MediaScanner(app)
    val repository = MediaRepository(app, db, prefs, scanner, appScope)
    val equalizer = EqualizerManager(prefs)
    val visualizer = com.nexus.media.player.audio.VisualizerManager()
    val player: PlayerManager by lazy { PlayerManager(app, db, prefs, equalizer, appScope) }
}
