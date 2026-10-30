package app.schnarchwaechter.service

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Compresses short PCM clips to AAC (.m4a, ~16 kbit/s = ~40 KB for 20 s). WAV as fallback. */
object ClipEncoder {
    private const val TAG = "ClipEncoder"

    fun writeM4a(pcm: ShortArray, sampleRate: Int, file: File): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (sampleRate <= 8000) 16_000 else 24_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var inputPos = 0
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone) {
                if (++guard > 100_000) error("encoder does not finish")
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        val n = minOf(buf.remaining() / 2, pcm.size - inputPos)
                        val ptsUs = inputPos * 1_000_000L / sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, inputPos, n)
                            codec.queueInputBuffer(idx, 0, n * 2, ptsUs, 0)
                            inputPos += n
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (out >= 0) {
                    val data = codec.getOutputBuffer(out)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            return muxerStarted
        } catch (e: Exception) {
            Log.w(TAG, "AAC encoding failed, falling back to WAV", e)
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!muxerStarted) file.delete()
        }
    }

    fun writeWav(pcm: ShortArray, sampleRate: Int, file: File): Boolean = runCatching {
        val dataLen = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate)
            putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }
        val body = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        body.asShortBuffer().put(pcm)
        RandomAccessFile(file, "rw").use {
            it.setLength(0)
            it.write(header.array())
            it.write(body.array())
        }
        true
    }.getOrElse {
        Log.w(TAG, "WAV writing failed", it)
        file.delete()
        false
    }
}
