package com.nexus.media.player.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nexus.media.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DacStatus(val dacName: String? = null, val active: Boolean = false, val message: String = "Mati")

/**
 * USB DAC bit-perfect support.
 *  - Android 14+: asks the system mixer for a BIT_PERFECT mixer behaviour on the USB DAC, matching the track's sample rate.
 *  - Older Android: the system mixer cannot be bypassed by apps, so only the "high quality" settings apply
 *    (no effects, 100% digital volume, no ReplayGain, float output for hi-res) - see PlayerManager/EqualizerManager.
 */
class UsbDacManager(ctx: Context, private val prefs: UserPreferences) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _status = MutableStateFlow(DacStatus())
    val status: StateFlow<DacStatus> = _status.asStateFlow()

    private var lastRate = -1
    private var appliedDevice: AudioDeviceInfo? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { apply(lastRate) }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { apply(lastRate) }
    }

    fun start() {
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        apply(-1)
    }

    private fun usbDac(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    private fun clear() {
        val d = appliedDevice ?: return
        if (Build.VERSION.SDK_INT >= 34) runCatching { am.clearPreferredMixerAttributes(attrs, d) }
        appliedDevice = null
    }

    /** Call when the mode changes, a device appears/disappears, or the track's sample rate changes. */
    fun apply(sampleRate: Int) {
        if (sampleRate > 0) lastRate = sampleRate
        val dev = usbDac()
        val name = dev?.productName?.toString()
        if (!prefs.bitPerfect) {
            clear()
            _status.value = DacStatus(name, false, if (name != null) "Mati ($name terdeteksi)" else "Mati")
            return
        }
        if (dev == null) {
            clear()
            _status.value = DacStatus(null, false, "Aktif. Colok USB DAC untuk memakai bit-perfect. Efek dimatikan dan volume digital 100%.")
            return
        }
        if (Build.VERSION.SDK_INT < 34) {
            _status.value = DacStatus(
                name, false,
                "$name terdeteksi. Android ${Build.VERSION.RELEASE} tidak mengizinkan bit-perfect lewat sistem (baru ada di Android 14). Mode kualitas tinggi aktif: tanpa efek, volume digital 100%."
            )
            return
        }
        runCatching {
            val supported = am.getSupportedMixerAttributes(dev)
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            if (supported.isEmpty()) {
                clear()
                _status.value = DacStatus(name, false, "$name tidak mengiklankan mode bit-perfect ke Android. Mode kualitas tinggi aktif.")
                return
            }
            val matching = if (lastRate > 0) supported.filter { it.format.sampleRate == lastRate } else supported
            if (matching.isEmpty()) {
                clear()
                _status.value = DacStatus(name, false, "$name belum mendukung bit-perfect pada $lastRate Hz. Mode kualitas tinggi aktif.")
                return
            }
            // prefer 16-bit (what a normal FLAC/MP3 outputs), then wider formats
            val order = listOf(
                AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED,
                AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT
            )
            val pick = matching.minByOrNull { order.indexOf(it.format.encoding).let { i -> if (i < 0) 99 else i } }!!
            val ok = am.setPreferredMixerAttributes(attrs, dev, pick)
            if (ok) {
                appliedDevice = dev
                _status.value = DacStatus(name, true, "Bit-perfect aktif ke $name (${pick.format.sampleRate} Hz).")
            } else {
                _status.value = DacStatus(name, false, "Android menolak mode bit-perfect untuk $name. Mode kualitas tinggi aktif.")
            }
        }.onFailure {
            _status.value = DacStatus(name, false, "Gagal mengatur bit-perfect (${it.javaClass.simpleName}). Mode kualitas tinggi aktif.")
        }
    }
}
