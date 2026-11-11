package com.nexus.media.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.nexus.media.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqState(
    val available: Boolean = false,
    val levels: List<Int> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int = 0,
    val freqsHz: List<Int> = emptyList(),
    val bass: Int = 0,
    val virt: Int = 0,
    val presets: List<String> = emptyList(),
    val profiles: List<String> = emptyList()
)

/** Wraps android.media.audiofx effects attached to the player's audio session. */
class EqualizerManager(private val prefs: UserPreferences) {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null

    private val _state = MutableStateFlow(EqState(profiles = prefs.profiles().keys.sorted()))
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(session: Int) {
        release()
        if (session <= 0) return
        try {
            eq = Equalizer(0, session).also { it.enabled = true }
        } catch (t: Throwable) {
            _state.value = _state.value.copy(available = false)
            return
        }
        bass = runCatching { BassBoost(0, session).also { it.enabled = true } }.getOrNull()
        virt = runCatching { Virtualizer(0, session).also { it.enabled = true } }.getOrNull()
        if (prefs.eqProfile.isNotEmpty()) applyEncoded(prefs.eqProfile)
        refresh()
    }

    private fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        eq = null; bass = null; virt = null
    }

    private fun refresh() {
        val e = eq ?: return
        val n = e.numberOfBands.toInt()
        val r = e.bandLevelRange
        _state.value = _state.value.copy(
            available = true,
            levels = (0 until n).map { e.getBandLevel(it.toShort()).toInt() },
            minLevel = r[0].toInt(),
            maxLevel = r[1].toInt(),
            freqsHz = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 },
            bass = runCatching { bass?.roundedStrength?.toInt() ?: 0 }.getOrDefault(0),
            virt = runCatching { virt?.roundedStrength?.toInt() ?: 0 }.getOrDefault(0),
            presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        )
    }

    private fun encode(): String {
        val s = _state.value
        return s.levels.joinToString(",") + ";" + s.bass + ";" + s.virt
    }

    private fun applyEncoded(s: String) {
        val p = s.split(";")
        if (p.size < 3) return
        val lv = p[0].split(",").mapNotNull { it.toIntOrNull() }
        eq?.let { e ->
            if (lv.size == e.numberOfBands.toInt()) {
                val lo = e.bandLevelRange[0].toInt()
                val hi = e.bandLevelRange[1].toInt()
                lv.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.coerceIn(lo, hi).toShort()) }
            }
        }
        runCatching { bass?.setStrength((p[1].toIntOrNull() ?: 0).toShort()) }
        runCatching { virt?.setStrength((p[2].toIntOrNull() ?: 0).toShort()) }
    }

    private fun persist() { prefs.eqProfile = encode() }

    fun setBand(i: Int, level: Int) { eq?.setBandLevel(i.toShort(), level.toShort()); refresh(); persist() }
    fun usePreset(i: Int) { eq?.usePreset(i.toShort()); refresh(); persist() }
    fun setBass(v: Int) { runCatching { bass?.setStrength(v.toShort()) }; refresh(); persist() }
    fun setVirt(v: Int) { runCatching { virt?.setStrength(v.toShort()) }; refresh(); persist() }

    fun saveProfile(name: String) {
        prefs.saveProfile(name.replace("=", "-"), encode())
        _state.value = _state.value.copy(profiles = prefs.profiles().keys.sorted())
    }

    fun loadProfile(name: String) {
        prefs.profiles()[name]?.let { applyEncoded(it); refresh(); persist() }
    }

    fun deleteProfile(name: String) {
        prefs.deleteProfile(name)
        _state.value = _state.value.copy(profiles = prefs.profiles().keys.sorted())
    }
}
