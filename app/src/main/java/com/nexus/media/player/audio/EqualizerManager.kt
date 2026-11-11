package com.nexus.media.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nexus.media.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val levels: List<Int> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int = 0,
    val freqsHz: List<Int> = emptyList(),
    val bass: Int = 0,
    val virt: Int = 0,
    val presets: List<String> = emptyList(),
    val profiles: List<String> = emptyList()
)

/**
 * Wraps android.media.audiofx effects attached to a player's audio session.
 *
 * Loudness safety:
 *  - the equalizer is OFF by default (master switch), so saved settings never change the sound at startup;
 *  - boost is capped at +6 dB per band;
 *  - a headroom factor (<= 1f) is reported through [onHeadroom] so boosting never raises the overall level;
 *  - while effects are switched on/off the output is muted for a moment (headroom 0f) to hide the jump
 *    some devices produce when the effect chain turns on;
 *  - a limiter (DynamicsProcessing, Android 9+) catches any remaining peaks.
 */
class EqualizerManager(private val prefs: UserPreferences) {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var limiter: DynamicsProcessing? = null

    var onHeadroom: ((Float) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var muteUntil = 0L
    private var lastOn = false

    private val _state = MutableStateFlow(EqState(enabled = prefs.eqEnabled, profiles = prefs.profiles().keys.sorted()))
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(session: Int) {
        detach()
        if (session <= 0) return
        try {
            eq = Equalizer(0, session)
        } catch (t: Throwable) {
            eq = null
            return
        }
        bass = runCatching { BassBoost(0, session) }.getOrNull()
        virt = runCatching { Virtualizer(0, session) }.getOrNull()
        limiter = runCatching { makeLimiter(session) }.getOrNull()
        if (prefs.eqProfile.isNotEmpty()) applyEncoded(prefs.eqProfile)
        refresh()
    }

    private fun makeLimiter(session: Int): DynamicsProcessing? {
        if (Build.VERSION.SDK_INT < 28) return null
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, false, 0, false, 0, true
        ).build()
        val d = DynamicsProcessing(0, session, cfg)
        d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 80f, 10f, -3f, 0f))
        d.enabled = false
        return d
    }

    fun detach() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        runCatching { limiter?.release() }
        eq = null
        bass = null
        virt = null
        limiter = null
        lastOn = false
        muteUntil = 0L
        _state.value = _state.value.copy(available = false)
        onHeadroom?.invoke(1f)
    }

    /** Reads the real values back from the effects (and caps boost at +6 dB). */
    private fun refresh() {
        val e = eq ?: return
        val n = e.numberOfBands.toInt()
        val r = e.bandLevelRange
        val cap = minOf(r[1].toInt(), MAX_BOOST_MB)
        for (i in 0 until n) {
            if (e.getBandLevel(i.toShort()) > cap) runCatching { e.setBandLevel(i.toShort(), cap.toShort()) }
        }
        _state.value = _state.value.copy(
            available = true,
            levels = (0 until n).map { e.getBandLevel(it.toShort()).toInt() },
            minLevel = r[0].toInt(),
            maxLevel = cap,
            freqsHz = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 },
            bass = runCatching { bass?.roundedStrength?.toInt() ?: 0 }.getOrDefault(0),
            virt = runCatching { virt?.roundedStrength?.toInt() ?: 0 }.getOrDefault(0),
            presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        )
        sync()
    }

    private fun deliver(f: Float) {
        val now = SystemClock.uptimeMillis()
        if (now < muteUntil) {
            pending?.let { handler.removeCallbacks(it) }
            val r = Runnable { pending = null; onHeadroom?.invoke(f) }
            pending = r
            handler.postDelayed(r, muteUntil - now)
        } else {
            onHeadroom?.invoke(f)
        }
    }

    /** Turns effects on only when needed, hides the on/off jump and reports the headroom gain. */
    private fun sync() {
        val s = _state.value
        val useEq = s.enabled && s.levels.any { it != 0 }
        val useBass = s.enabled && s.bass > 0
        val useVirt = s.enabled && s.virt > 0
        val anyOn = useEq || useBass || useVirt
        val peak = maxOf(0, s.levels.maxOrNull() ?: 0)
        val millibels = if (s.enabled) peak + s.bass * 3 / 5 else 0
        val factor = Math.pow(10.0, -millibels / 2000.0).toFloat().coerceIn(0.1f, 1f)

        if (anyOn != lastOn) {
            lastOn = anyOn
            muteUntil = SystemClock.uptimeMillis() + 250
            pending?.let { handler.removeCallbacks(it) }
            onHeadroom?.invoke(0f)   // mute while the effect chain switches
        }
        runCatching { eq?.enabled = useEq }
        runCatching { bass?.enabled = useBass }
        runCatching { virt?.enabled = useVirt }
        runCatching { limiter?.enabled = anyOn }
        deliver(factor)
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
                val hi = minOf(e.bandLevelRange[1].toInt(), MAX_BOOST_MB)
                lv.forEachIndexed { i, l -> runCatching { e.setBandLevel(i.toShort(), l.coerceIn(lo, hi).toShort()) } }
            }
        }
        runCatching { bass?.setStrength((p[1].toIntOrNull() ?: 0).coerceIn(0, 1000).toShort()) }
        runCatching { virt?.setStrength((p[2].toIntOrNull() ?: 0).coerceIn(0, 1000).toShort()) }
    }

    private fun persist() { prefs.eqProfile = encode() }

    /** Master switch. Off = no effect at all and normal volume. */
    fun setEnabled(on: Boolean) {
        prefs.eqEnabled = on
        _state.value = _state.value.copy(enabled = on)
        sync()
    }

    /** Called while a slider moves: cheap, no disk write. */
    fun setBand(i: Int, level: Int) {
        val e = eq ?: return
        val s = _state.value
        val lv = level.coerceIn(s.minLevel, s.maxLevel)
        runCatching { e.setBandLevel(i.toShort(), lv.toShort()) }
        _state.value = s.copy(levels = s.levels.toMutableList().also { if (i in it.indices) it[i] = lv })
        sync()
    }

    fun setBass(v: Int) {
        val x = v.coerceIn(0, 1000)
        runCatching { bass?.setStrength(x.toShort()) }
        _state.value = _state.value.copy(bass = x)
        sync()
    }

    fun setVirt(v: Int) {
        val x = v.coerceIn(0, 1000)
        runCatching { virt?.setStrength(x.toShort()) }
        _state.value = _state.value.copy(virt = x)
        sync()
    }

    /** Called when a slider is released: save the setting. */
    fun commit() { persist() }

    fun usePreset(i: Int) {
        runCatching { eq?.usePreset(i.toShort()) }
        refresh()
        persist()
    }

    fun reset() {
        val e = eq ?: return
        for (i in 0 until e.numberOfBands.toInt()) runCatching { e.setBandLevel(i.toShort(), 0.toShort()) }
        runCatching { bass?.setStrength(0.toShort()) }
        runCatching { virt?.setStrength(0.toShort()) }
        refresh()
        persist()
    }

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

    private companion object {
        const val MAX_BOOST_MB = 600   // +6 dB
    }
}
