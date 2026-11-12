package com.nexus.media.data.preferences

import android.content.Context
import com.nexus.media.data.repository.LibraryFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {
    private val sp = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)

    private val _hidden = MutableStateFlow(sp.getStringSet("hidden", emptySet())!!.toSet())
    val hiddenFolders: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _custom = MutableStateFlow(sp.getStringSet("custom", emptySet())!!.toSet())
    val customFolders: StateFlow<Set<String>> = _custom.asStateFlow()

    fun setHidden(path: String, hide: Boolean) {
        val n = if (hide) _hidden.value + path else _hidden.value - path
        _hidden.value = n
        sp.edit().putStringSet("hidden", n).apply()
    }

    fun setCustom(uri: String, add: Boolean) {
        val n = if (add) _custom.value + uri else _custom.value - uri
        _custom.value = n
        sp.edit().putStringSet("custom", n).apply()
    }

    private val _hiddenIds = MutableStateFlow(sp.getStringSet("hidden_ids", emptySet())!!.toSet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    fun setHiddenId(id: String, hide: Boolean) {
        val n = if (hide) _hiddenIds.value + id else _hiddenIds.value - id
        _hiddenIds.value = n
        sp.edit().putStringSet("hidden_ids", n).apply()
    }

    fun clearHiddenIds() {
        _hiddenIds.value = emptySet()
        sp.edit().remove("hidden_ids").apply()
    }

    val filter = MutableStateFlow(
        LibraryFilter(
            minDurationSec = sp.getInt("f_min", LibraryFilter().minDurationSec),
            excludeKeywords = sp.getString("f_kw", LibraryFilter().excludeKeywords)!!,
            onlyMusic = sp.getBoolean("f_music", true)
        )
    )

    fun setFilter(f: LibraryFilter) {
        filter.value = f
        sp.edit().putInt("f_min", f.minDurationSec).putString("f_kw", f.excludeKeywords).putBoolean("f_music", f.onlyMusic).apply()
    }

    var onlineLyrics: Boolean
        get() = sp.getBoolean("online_lyrics", true)
        set(v) = sp.edit().putBoolean("online_lyrics", v).apply()

    var bitPerfect: Boolean
        get() = sp.getBoolean("bit_perfect", false)
        set(v) = sp.edit().putBoolean("bit_perfect", v).apply()

    data class TranslateSetting(val on: Boolean = false, val target: String = "id")

    val translate = MutableStateFlow(TranslateSetting(sp.getBoolean("tr_on", false), sp.getString("tr_target", "id")!!))

    fun setTranslate(s: TranslateSetting) {
        translate.value = s
        sp.edit().putBoolean("tr_on", s.on).putString("tr_target", s.target).apply()
    }

    var replayGain: Boolean
        get() = sp.getBoolean("replay_gain", true)
        set(v) = sp.edit().putBoolean("replay_gain", v).apply()

    var eqEnabled: Boolean
        get() = sp.getBoolean("eq_enabled", false)
        set(v) = sp.edit().putBoolean("eq_enabled", v).apply()

    var playerVolume: Float
        get() = sp.getFloat("player_volume", 0.5f)
        set(v) = sp.edit().putFloat("player_volume", v).apply()

    var playerMode: String
        get() = sp.getString("mode", "artwork")!!
        set(v) = sp.edit().putString("mode", v).apply()
    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()
    var repeatMode: Int
        get() = sp.getInt("repeat", 0)
        set(v) = sp.edit().putInt("repeat", v).apply()
    var eqProfile: String
        get() = sp.getString("eq", "")!!
        set(v) = sp.edit().putString("eq", v).apply()
    var lastQueue: String
        get() = sp.getString("last_queue", "")!!
        set(v) = sp.edit().putString("last_queue", v).apply()
    var lastIndex: Int
        get() = sp.getInt("last_index", 0)
        set(v) = sp.edit().putInt("last_index", v).apply()
    var lastPosition: Long
        get() = sp.getLong("last_pos", 0L)
        set(v) = sp.edit().putLong("last_pos", v).apply()

    var videoBgAudio: Boolean
        get() = sp.getBoolean("video_bg_audio", false)
        set(v) = sp.edit().putBoolean("video_bg_audio", v).apply()

    fun lyricsUri(id: String): String? = sp.getString("lyr_$id", null)
    fun setLyricsUri(id: String, uri: String) = sp.edit().putString("lyr_$id", uri).apply()

    fun profiles(): Map<String, String> =
        (sp.getStringSet("eq_profiles", emptySet()) ?: emptySet()).mapNotNull {
            val i = it.indexOf('=')
            if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()

    fun saveProfile(name: String, data: String) = writeProfiles(profiles() + (name to data))
    fun deleteProfile(name: String) = writeProfiles(profiles() - name)
    private fun writeProfiles(m: Map<String, String>) {
        sp.edit().putStringSet("eq_profiles", m.map { "${it.key}=${it.value}" }.toSet()).apply()
    }
}
