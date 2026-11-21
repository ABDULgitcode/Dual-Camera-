package com.dualframe.prototype.camera

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * One mic capture → one AAC encoder → identical packets fanned out to every video muxer.
 */
class SharedAacEncoder(
    private val sinks: List<VideoTrackEncoder>,
) {
    private val tag = "SharedAac"
    private val running = AtomicBoolean(false)
    private val packetsMuxed = AtomicLong(0)
    private val readyLatch = CountDownLatch(1)
    private var audioThread: Thread? = null

    val muxedPacketCount: Long get() = packetsMuxed.get()

    /**
     * Starts capture. Returns true if audio path is live.
     * Call [awaitReady] before submitting video frames so muxers can start with both tracks.
     */
    fun start(): Boolean {
        if (sinks.isEmpty()) {
            readyLatch.countDown()
            return false
        }
        val sampleRate = 48_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf <= 0) {
            Log.e(tag, "Invalid AudioRecord buffer size")
            sinks.forEach { it.forceStartWithoutAudio() }
            readyLatch.countDown()
            return false
        }
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                sampleRate,
                channelConfig,
                audioFormat,
                minBuf * 2,
            )
        } catch (t: SecurityException) {
            Log.e(tag, "RECORD_AUDIO not granted", t)
            sinks.forEach { it.forceStartWithoutAudio() }
            readyLatch.countDown()
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(tag, "AudioRecord not initialized")
            recorder.release()
            sinks.forEach { it.forceStartWithoutAudio() }
            readyLatch.countDown()
            return false
        }

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuf * 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        running.set(true)
        audioThread = thread(name = "DualFrame-AAC", isDaemon = true) {
            try {
                recorder.startRecording()
                val pcm = ByteArray(minBuf)
                val bufferInfo = MediaCodec.BufferInfo()
                var tracksAdded = false
                while (running.get()) {
                    val read = recorder.read(pcm, 0, pcm.size)
                    if (read > 0) {
                        val inIndex = codec.dequeueInputBuffer(5_000)
                        if (inIndex >= 0) {
                            val input = codec.getInputBuffer(inIndex)
                            input?.clear()
                            input?.put(pcm, 0, read)
                            val pts = System.nanoTime() / 1000L
                            codec.queueInputBuffer(inIndex, 0, read, pts, 0)
                        }
                    }
                    while (true) {
                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                        when {
                            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (!tracksAdded) {
                                    val outFormat = codec.outputFormat
                                    sinks.forEach { it.addAudioTrack(outFormat) }
                                    sinks.forEach { it.notifyAudioReady() }
                                    tracksAdded = true
                                    readyLatch.countDown()
                                }
                            }
                            outIndex >= 0 -> {
                                val encoded = codec.getOutputBuffer(outIndex)
                                if (encoded != null &&
                                    bufferInfo.size > 0 &&
                                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                                    tracksAdded
                                ) {
                                    sinks.forEach { sink ->
                                        val dup = encoded.asReadOnlyBuffer()
                                        val copy = ByteBuffer.allocate(bufferInfo.size)
                                        dup.position(bufferInfo.offset)
                                        dup.limit(bufferInfo.offset + bufferInfo.size)
                                        copy.put(dup)
                                        copy.flip()
                                        val infoCopy = MediaCodec.BufferInfo().apply {
                                            set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                                        }
                                        sink.writeAudioSample(copy, infoCopy)
                                    }
                                    packetsMuxed.incrementAndGet()
                                }
                                codec.releaseOutputBuffer(outIndex, false)
                            }
                        }
                    }
                }
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            } catch (t: Throwable) {
                Log.e(tag, "Audio loop failed", t)
                sinks.forEach { it.forceStartWithoutAudio() }
                readyLatch.countDown()
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        }
        return true
    }

    fun awaitReady(timeoutMs: Long = 1_500): Boolean {
        val ok = readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!ok) {
            Log.w(tag, "Audio track setup timed out; continuing without audio")
            sinks.forEach { it.forceStartWithoutAudio() }
            readyLatch.countDown()
        }
        return ok
    }

    fun stop() {
        running.set(false)
        audioThread?.join(2_000)
        audioThread = null
    }
}
