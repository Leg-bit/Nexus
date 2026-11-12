package com.nexus.media.player.audio

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** FFT spectrum of the audio session (needs RECORD_AUDIO). Emits [BARS] values in 0..1. */
class VisualizerManager {
    private var viz: Visualizer? = null
    private val _bars = MutableStateFlow(FloatArray(BARS))
    val bars: StateFlow<FloatArray> = _bars.asStateFlow()

    fun start(session: Int) {
        stop()
        if (session <= 0) return
        runCatching {
            val v = Visualizer(session)
            v.captureSize = min(1024, Visualizer.getCaptureSizeRange()[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) _bars.value = toBars(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true
            viz = v
        }
    }

    fun stop() {
        runCatching { viz?.enabled = false }
        runCatching { viz?.release() }
        viz = null
        _bars.value = FloatArray(BARS)
    }

    private fun toBars(fft: ByteArray): FloatArray {
        val n = fft.size / 2
        val out = FloatArray(BARS)
        for (b in 0 until BARS) {
            val start = (1 + (n - 1) * (b.toDouble() / BARS).pow(2.0)).toInt()
            val end = max(start + 1, (1 + (n - 1) * ((b + 1).toDouble() / BARS).pow(2.0)).toInt())
            var m = 0f
            for (k in start until min(end, n)) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                m = max(m, hypot(re, im))
            }
            out[b] = (m / 90f).coerceIn(0f, 1f)
        }
        return out
    }

    companion object { const val BARS = 32 }
}
