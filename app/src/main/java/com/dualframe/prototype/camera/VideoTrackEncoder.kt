package com.dualframe.prototype.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One H.264/HEVC encode + muxer sink for a single orientation file.
 */
class VideoTrackEncoder(
    private val outputFile: File,
    private val mime: String,
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val frameRate: Int = 30,
) {
    private val tag = "VideoTrackEnc"
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val running = AtomicBoolean(false)
    private var presentationBaseUs = -1L
    private var frameIndex = 0L

    val path: String get() = outputFile.absolutePath
    val resolutionLabel: String get() = "${width}×${height}"

    @Volatile
    var audioTrackIndex: Int = -1
        private set

    fun start() {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
        }
        val c = MediaCodec.createEncoderByType(mime)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        running.set(true)
        presentationBaseUs = -1L
        frameIndex = 0L
    }

    fun queueNv12Frame(nv12: ByteArray, epochMs: Long): Boolean {
        val c = codec ?: return false
        if (!running.get()) return false
        val inIndex = c.dequeueInputBuffer(2_000)
        if (inIndex < 0) return false
        val input = c.getInputBuffer(inIndex) ?: return false
        input.clear()
        if (nv12.size > input.capacity()) {
            Log.w(tag, "Frame larger than input buffer (${nv12.size}>${input.capacity()})")
            c.queueInputBuffer(inIndex, 0, 0, 0, 0)
            return false
        }
        input.put(nv12)
        if (presentationBaseUs < 0) presentationBaseUs = epochMs * 1000L
        val pts = presentationBaseUs + frameIndex * (1_000_000L / frameRate)
        frameIndex++
        c.queueInputBuffer(inIndex, 0, nv12.size, pts, 0)
        drain(endOfStream = false)
        return true
    }

    fun addAudioTrack(format: MediaFormat): Int {
        val m = muxer ?: error("Muxer not started")
        synchronized(this) {
            check(!muxerStarted) { "Cannot add audio track after muxer start" }
            audioTrackIndex = m.addTrack(format)
            maybeStartMuxerLocked()
            return audioTrackIndex
        }
    }

    fun writeAudioSample(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        val index = audioTrackIndex
        if (index < 0) return
        synchronized(this) {
            if (!muxerStarted) return
            m.writeSampleData(index, data, info)
        }
    }

    fun finish() {
        running.set(false)
        val c = codec
        if (c != null) {
            try {
                val inIndex = c.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    c.queueInputBuffer(
                        inIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                }
                drain(endOfStream = true)
            } catch (t: Throwable) {
                Log.w(tag, "finish drain error", t)
            }
        }
        release()
    }

    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var idleTries = 0
        while (true) {
            val outIndex = c.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    idleTries++
                    if (idleTries > 30) break
                    continue
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val m = muxer ?: break
                    synchronized(this) {
                        trackIndex = m.addTrack(c.outputFormat)
                        maybeStartMuxerLocked()
                    }
                }
                outIndex >= 0 -> {
                    val encoded = c.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        synchronized(this) {
                            if (muxerStarted && trackIndex >= 0) {
                                muxer?.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                        }
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    private fun maybeStartMuxerLocked() {
        val m = muxer ?: return
        if (muxerStarted) return
        // Start when video track is ready; audio may join if already added, or later before samples.
        if (trackIndex >= 0) {
            // If audio format not yet known, still start — SharedAacEncoder adds track before writing
            // Prefer waiting for audio when recording with mic; ApproachARecorder coordinates.
            if (audioTrackIndex >= 0 || audioOptional) {
                m.start()
                muxerStarted = true
            }
        }
    }

    /** When true, muxer can start without an audio track (mic denied / audio failure). */
    @Volatile
    var audioOptional: Boolean = false

    fun notifyAudioReady() {
        synchronized(this) {
            maybeStartMuxerLocked()
        }
    }

    fun forceStartWithoutAudio() {
        synchronized(this) {
            audioOptional = true
            maybeStartMuxerLocked()
        }
    }

    private fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        synchronized(this) {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null
            muxerStarted = false
        }
    }
}
