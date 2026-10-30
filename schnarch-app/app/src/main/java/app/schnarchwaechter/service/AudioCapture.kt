package app.schnarchwaechter.service

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import app.schnarchwaechter.core.dsp.powerToDb

/**
 * Opens the microphone with the lowest sample rate the phone supports.
 *
 * 8 kHz is enough: snoring lies mainly in 100-1000 Hz, 8 kHz captures up to 4 kHz. Fewer samples =
 * less work for the CPU. VOICE_RECOGNITION is used because Android requires automatic gain control
 * to be off for this source, so the measured levels are not "pumped" by the phone.
 */
object AudioCapture {
    private const val TAG = "AudioCapture"
    val PREFERRED_RATES = intArrayOf(8000, 16000, 11025, 22050, 44100, 48000)

    class Opened(val record: AudioRecord, private val effects: List<AudioEffect>) {
        val sampleRate: Int get() = record.sampleRate

        fun release() {
            effects.forEach { runCatching { it.release() } }
            runCatching { record.release() }
        }
    }

    /** Needs RECORD_AUDIO (checked by the caller). Returns null if no configuration works. */
    @SuppressLint("MissingPermission")
    fun open(rates: IntArray = PREFERRED_RATES, bufferSeconds: Int = 6): Opened? {
        for (rate in rates) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            val size = maxOf(min, rate * 2 * bufferSeconds) // large internal buffer, see README
            val record = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord $rate Hz failed", e)
                continue
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            return Opened(record, disableProcessing(record.audioSessionId))
        }
        return null
    }

    /** Switches off gain control / noise suppression if the phone applies them anyway. */
    private fun disableProcessing(session: Int): List<AudioEffect> {
        val effects = mutableListOf<AudioEffect>()
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.let { effects += it } }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.let { effects += it } }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.let { effects += it } }
        effects.forEach { runCatching { it.setEnabled(false) } }
        return effects
    }

    /** Average level in dBFS over [seconds] (for the optional calibration). Blocking. */
    fun measureLevelDb(seconds: Int = 5): Double? {
        val opened = open() ?: return null
        try {
            val record = opened.record
            val rate = opened.sampleRate
            val buf = ShortArray(rate / 2)
            record.startRecording()
            var sum = 0.0
            var count = 0L
            var blocks = 0
            while (blocks < seconds * 2 + 1) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) return null
                if (blocks > 0) { // skip the first half second (microphone settling)
                    for (i in 0 until n) {
                        val x = buf[i] / 32768.0
                        sum += x * x
                    }
                    count += n
                }
                blocks++
            }
            record.stop()
            return if (count > 0) powerToDb(sum / count) else null
        } finally {
            opened.release()
        }
    }
}

/**
 * Reads audio in large blocks (2 s) and hands them to [onBlock]. Between two blocks this thread
 * sleeps inside AudioRecord.read(), so the CPU is not kept busy by the app.
 */
class CaptureThread(
    private var opened: AudioCapture.Opened,
    private val blockSamples: Int,
    private val onBlock: (ShortArray, Int) -> Unit,
) : Thread("NightAudio") {
    @Volatile
    private var running = true

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buf = ShortArray(blockSamples)
        val rate = opened.sampleRate
        opened.record.startRecording()
        while (running) {
            var filled = 0
            var dead = false
            while (running && filled < blockSamples) {
                val n = opened.record.read(buf, filled, blockSamples - filled)
                if (n > 0) {
                    filled += n
                } else if (n < 0) {
                    Log.w("CaptureThread", "read error $n")
                    dead = n == AudioRecord.ERROR_DEAD_OBJECT || n == AudioRecord.ERROR_INVALID_OPERATION
                    break
                }
            }
            if (filled > 0) onBlock(buf, filled)
            if (dead && running) reopen(rate)
        }
        runCatching { opened.record.stop() }
    }

    /** The audio system was restarted (rare): open the microphone again with the same rate. */
    private fun reopen(rate: Int) {
        opened.release()
        while (running) {
            runCatching { Thread.sleep(5_000) }
            val again = AudioCapture.open(intArrayOf(rate)) ?: continue
            opened = again
            runCatching { opened.record.startRecording() }
            return
        }
    }

    /** Stops recording and waits for the thread. The AudioRecord is released afterwards. */
    fun shutdown() {
        running = false
        runCatching { opened.record.stop() } // unblocks read()
        join(5_000)
        opened.release()
    }
}
