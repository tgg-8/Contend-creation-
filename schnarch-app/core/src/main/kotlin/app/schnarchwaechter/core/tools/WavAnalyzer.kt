package app.schnarchwaechter.core.tools

import app.schnarchwaechter.core.AnomalyMonitor
import app.schnarchwaechter.core.BreathingPause
import app.schnarchwaechter.core.ClipConfig
import app.schnarchwaechter.core.DetectionConfig
import app.schnarchwaechter.core.DetectorListener
import app.schnarchwaechter.core.SnoreBurst
import app.schnarchwaechter.core.SnoreDetector
import app.schnarchwaechter.core.SnoreEpisode
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal reader for 16-bit PCM WAV files (mono or multi channel, which is mixed down). */
class WavFile(val sampleRate: Int, val samples: ShortArray) {
    companion object {
        fun read(file: File): WavFile = file.inputStream().buffered().use { read(it) }

        fun read(input: InputStream): WavFile {
            val data = DataInputStream(input).readBytes()
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            require(String(data, 0, 4) == "RIFF" && String(data, 8, 4) == "WAVE") { "Keine WAV-Datei" }
            var pos = 12
            var channels = 1
            var rate = 0
            var bits = 0
            while (pos + 8 <= data.size) {
                val id = String(data, pos, 4)
                val size = bb.getInt(pos + 4)
                val body = pos + 8
                when (id) {
                    "fmt " -> {
                        val format = bb.getShort(body).toInt()
                        require(format == 1 || format == -2) { "Nur unkomprimiertes PCM wird unterstützt" }
                        channels = bb.getShort(body + 2).toInt()
                        rate = bb.getInt(body + 4)
                        bits = bb.getShort(body + 14).toInt()
                    }
                    "data" -> {
                        require(bits == 16) { "Nur 16-Bit-WAV wird unterstützt (ist: $bits Bit)" }
                        val len = minOf(size, data.size - body)
                        val frames = len / (2 * channels)
                        val out = ShortArray(frames)
                        for (f in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += bb.getShort(body + (f * channels + c) * 2)
                            out[f] = (sum / channels).toShort()
                        }
                        return WavFile(rate, out)
                    }
                }
                pos = body + size + (size and 1)
            }
            error("WAV-Datei enthält keine Audiodaten")
        }
    }
}

/**
 * Command line tool to tune the detection with real recordings:
 *   ./gradlew :core:analyzeWav --args="aufnahme.wav [empfindlichkeit 1-5]"
 * Prints every detected snore, episode and silence, plus counters of rejected sounds.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Aufruf: analyzeWav <datei.wav> [Empfindlichkeit 1-5]")
        return
    }
    val wav = WavFile.read(File(args[0]))
    val sensitivity = args.getOrNull(1)?.toIntOrNull() ?: DetectionConfig.DEFAULT_SENSITIVITY
    val config = DetectionConfig.forSensitivity(sensitivity)
    println("Datei: ${args[0]}  (${wav.sampleRate} Hz, ${"%.1f".format(wav.samples.size / wav.sampleRate / 60.0)} min, Empfindlichkeit $sensitivity)")
    println("Konfiguration: $config")
    fun t(ms: Long) = "%02d:%02d:%04.1f".format(ms / 3_600_000, ms / 60_000 % 60, ms % 60_000 / 1000.0)
    val monitor = AnomalyMonitor(config, ClipConfig())
    val episodes = mutableListOf<SnoreEpisode>()
    val detector = SnoreDetector(wav.sampleRate, config, 0, object : DetectorListener {
        override fun onCalibrated(floorDb: Double) = println("Grundrauschen gemessen: ${"%.1f".format(floorDb)} dBFS")
        override fun onEpisodeStarted(startMs: Long) = println("${t(startMs)}  Episode beginnt")
        override fun onSnoreBurst(burst: SnoreBurst, episodeStartMs: Long) {
            println("${t(burst.startMs)}    Schnarcher ${"%.1f".format((burst.endMs - burst.startMs) / 1000.0)} s, +${"%.1f".format(burst.peakAboveFloorDb)} dB")
            monitor.onSnoreBurst(burst, episodeStartMs)?.let { println("              -> Clip-Kandidat ${it.reason} (Wert ${"%.1f".format(it.value)})") }
        }
        override fun onEpisodeEnded(episode: SnoreEpisode) {
            episodes += episode
            println("${t(episode.endMs)}  Episode endet: ${"%.0f".format(episode.durationMs / 1000.0)} s, ${episode.burstCount} Schnarcher")
        }
        override fun onEpisodeDiscarded(startMs: Long) = println("              (Episode ab ${t(startMs)} zu kurz, verworfen)")
        override fun onPause(pause: BreathingPause) =
            println("${t(pause.startMs)}  Auffällige Stille ${"%.1f".format(pause.durationSec)} s nach lautem Schnarchen")
    })
    val block = wav.sampleRate
    val t0 = System.nanoTime()
    var i = 0
    while (i < wav.samples.size) {
        val n = minOf(block, wav.samples.size - i)
        detector.process(wav.samples, i, n)
        i += n
    }
    detector.finish()
    val cpuMs = (System.nanoTime() - t0) / 1e6
    val s = detector.stats
    println()
    println("Ergebnis: ${episodes.size} Episoden, ${"%.1f".format(episodes.sumOf { it.durationMs } / 60_000.0)} min Schnarchen")
    println("Geräusche (Stufe 1): ${s.bursts}, davon verworfen: Dauer ${s.rejectedDuration}, Frequenzband ${s.rejectedBand}, Spektrum ${s.rejectedSpectrum}")
    println("Stufe-2-Analysen: ${s.stage2Runs} bei ${s.frames} Frames (${"%.2f".format(100.0 * s.stage2Runs / maxOf(1, s.frames))} %)")
    val audioMin = wav.samples.size / wav.sampleRate / 60.0
    println("Rechenzeit (dieser PC): ${"%.0f".format(cpuMs)} ms für ${"%.0f".format(audioMin)} min Audio = ${"%.1f".format(cpuMs / audioMin * 480 / 1000)} s pro 8-Stunden-Nacht")
}
