package com.dualframe.prototype.camera

import android.content.Context
import android.os.Environment
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.dualframe.prototype.model.CaptureMode
import com.dualframe.prototype.model.DualEncodeSupport
import com.dualframe.prototype.model.RecordingStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Approach A capture: one ImageAnalysis stream → crop/scale → one or two 1080p files.
 */
class ApproachARecorder(
    private val context: Context,
    private val encodeSupport: DualEncodeSupport,
) {
    private val tag = "ApproachARecorder"
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val recording = AtomicBoolean(false)
    private val framesSubmitted = AtomicLong(0)
    private val framesDropped = AtomicLong(0)
    private val active = AtomicReference<ActiveSession?>(null)

    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1440),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .build()
        .also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { image ->
                onFrame(image)
            }
        }

    fun canRecord(mode: CaptureMode): Pair<Boolean, String?> {
        return when (mode) {
            CaptureMode.DUAL -> {
                if (encodeSupport.dualSupported) true to null
                else false to (encodeSupport.reasons.firstOrNull()
                    ?: "Dual encode unsupported on this device")
            }
            CaptureMode.PORTRAIT, CaptureMode.LANDSCAPE -> {
                if (encodeSupport.singleSupported) true to null
                else false to (encodeSupport.reasons.firstOrNull()
                    ?: "1080p encode unsupported on this device")
            }
        }
    }

    fun start(mode: CaptureMode): Result<String> {
        if (recording.get()) return Result.failure(IllegalStateException("Already recording"))
        val (ok, reason) = canRecord(mode)
        if (!ok) return Result.failure(IllegalStateException(reason))

        val mime = encodeSupport.preferredMime
            ?: return Result.failure(IllegalStateException("No video MIME available"))

        val pairId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = outputDir()
        dir.mkdirs()

        val landscapeFile = File(dir, "DF_${pairId}_L.mp4")
        val portraitFile = File(dir, "DF_${pairId}_P.mp4")

        val encoders = mutableListOf<VideoTrackEncoder>()
        var landscapeEnc: VideoTrackEncoder? = null
        var portraitEnc: VideoTrackEncoder? = null

        try {
            if (mode == CaptureMode.LANDSCAPE || mode == CaptureMode.DUAL) {
                landscapeEnc = VideoTrackEncoder(
                    outputFile = landscapeFile,
                    mime = mime,
                    width = encodeSupport.landscapeWidth,
                    height = encodeSupport.landscapeHeight,
                    bitrate = DualEncodeCapability.bitrateFor(
                        encodeSupport.landscapeWidth,
                        encodeSupport.landscapeHeight,
                    ),
                    frameRate = encodeSupport.targetFps,
                ).also {
                    it.start()
                    encoders += it
                }
            }
            if (mode == CaptureMode.PORTRAIT || mode == CaptureMode.DUAL) {
                portraitEnc = VideoTrackEncoder(
                    outputFile = portraitFile,
                    mime = mime,
                    width = encodeSupport.portraitWidth,
                    height = encodeSupport.portraitHeight,
                    bitrate = DualEncodeCapability.bitrateFor(
                        encodeSupport.portraitWidth,
                        encodeSupport.portraitHeight,
                    ),
                    frameRate = encodeSupport.targetFps,
                ).also {
                    it.start()
                    encoders += it
                }
            }

            val audio = SharedAacEncoder(encoders)
            val audioOk = audio.start()
            if (!audioOk) {
                Log.w(tag, "Continuing without shared audio")
            } else {
                audio.awaitReady()
            }

            framesSubmitted.set(0)
            framesDropped.set(0)
            val session = ActiveSession(
                pairId = pairId,
                mode = mode,
                mime = mime,
                startEpochMs = System.currentTimeMillis(),
                landscape = landscapeEnc,
                portrait = portraitEnc,
                audio = audio,
                landscapeNv12 = landscapeEnc?.let {
                    ByteArray(
                        YuvCropScaler.nv12ByteSize(
                            encodeSupport.landscapeWidth,
                            encodeSupport.landscapeHeight,
                        ),
                    )
                },
                portraitNv12 = portraitEnc?.let {
                    ByteArray(
                        YuvCropScaler.nv12ByteSize(
                            encodeSupport.portraitWidth,
                            encodeSupport.portraitHeight,
                        ),
                    )
                },
            )
            active.set(session)
            recording.set(true)

            val outputs = buildList {
                landscapeEnc?.let { add("L=${it.path}") }
                portraitEnc?.let { add("P=${it.path}") }
            }.joinToString(",")
            RecordingLabLog.sessionStart(
                pairId = pairId,
                mode = mode.name,
                mime = mime,
                analysisSize = "pending",
                outputs = outputs,
            )
            return Result.success(pairId)
        } catch (t: Throwable) {
            encoders.forEach { runCatching { it.finish() } }
            recording.set(false)
            active.set(null)
            return Result.failure(t)
        }
    }

    fun stop(): RecordingStats {
        val session = active.getAndSet(null)
        recording.set(false)
        if (session == null) {
            return RecordingStats(
                mode = CaptureMode.DUAL,
                pairId = "none",
                startEpochMs = 0,
                stopEpochMs = System.currentTimeMillis(),
                landscapePath = null,
                portraitPath = null,
                landscapeResolution = null,
                portraitResolution = null,
                videoMime = null,
                framesSubmitted = 0,
                framesDropped = 0,
                audioPacketsMuxed = 0,
                analysisResolution = null,
                errorMessage = "Not recording",
            )
        }

        // Brief pause so last frames drain; audio stop before video finish.
        runCatching { session.audio.stop() }
        runCatching { session.landscape?.finish() }
        runCatching { session.portrait?.finish() }

        val stopMs = System.currentTimeMillis()
        val stats = RecordingStats(
            mode = session.mode,
            pairId = session.pairId,
            startEpochMs = session.startEpochMs,
            stopEpochMs = stopMs,
            landscapePath = session.landscape?.path,
            portraitPath = session.portrait?.path,
            landscapeResolution = session.landscape?.resolutionLabel,
            portraitResolution = session.portrait?.resolutionLabel,
            videoMime = session.mime,
            framesSubmitted = framesSubmitted.get(),
            framesDropped = framesDropped.get(),
            audioPacketsMuxed = session.audio.muxedPacketCount,
            analysisResolution = session.analysisResolution,
            errorMessage = null,
        )
        RecordingLabLog.sessionStop(stats)
        return stats
    }

    fun isRecording(): Boolean = recording.get()

    fun liveCounters(): Pair<Long, Long> = framesSubmitted.get() to framesDropped.get()

    fun shutdown() {
        if (recording.get()) runCatching { stop() }
        runCatching { imageAnalysis.clearAnalyzer() }
        analysisExecutor.shutdownNow()
    }

    private fun onFrame(image: ImageProxy) {
        val session = active.get()
        if (session == null || !recording.get()) {
            image.close()
            return
        }
        try {
            val w = image.width
            val h = image.height
            if (session.analysisResolution == null) {
                session.analysisResolution = "${w}×${h}"
            }
            val epochMs = System.currentTimeMillis()
            var submittedAny = false

            session.landscape?.let { enc ->
                val crop = CropGeometry.landscape16x9(w, h)
                val buf = session.landscapeNv12!!
                YuvCropScaler.imageToNv12CropScaled(
                    image,
                    crop,
                    encodeSupport.landscapeWidth,
                    encodeSupport.landscapeHeight,
                    buf,
                )
                val ok = enc.queueNv12Frame(buf, epochMs)
                if (ok) submittedAny = true else {
                    val d = framesDropped.incrementAndGet()
                    if (d % 30L == 1L) {
                        RecordingLabLog.frameDrop(session.pairId, d, framesSubmitted.get(), "L-encoder-busy")
                    }
                }
            }
            session.portrait?.let { enc ->
                val crop = CropGeometry.portrait9x16(w, h)
                val buf = session.portraitNv12!!
                YuvCropScaler.imageToNv12CropScaled(
                    image,
                    crop,
                    encodeSupport.portraitWidth,
                    encodeSupport.portraitHeight,
                    buf,
                )
                val ok = enc.queueNv12Frame(buf, epochMs)
                if (ok) submittedAny = true else {
                    val d = framesDropped.incrementAndGet()
                    if (d % 30L == 1L) {
                        RecordingLabLog.frameDrop(session.pairId, d, framesSubmitted.get(), "P-encoder-busy")
                    }
                }
            }
            if (submittedAny) framesSubmitted.incrementAndGet()
        } catch (t: Throwable) {
            framesDropped.incrementAndGet()
            Log.e(tag, "Frame processing failed", t)
        } finally {
            image.close()
        }
    }

    private fun outputDir(): File {
        val movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(movies ?: context.filesDir, "DualFrame")
    }

    private data class ActiveSession(
        val pairId: String,
        val mode: CaptureMode,
        val mime: String,
        val startEpochMs: Long,
        val landscape: VideoTrackEncoder?,
        val portrait: VideoTrackEncoder?,
        val audio: SharedAacEncoder,
        val landscapeNv12: ByteArray?,
        val portraitNv12: ByteArray?,
        @Volatile var analysisResolution: String? = null,
    )
}
