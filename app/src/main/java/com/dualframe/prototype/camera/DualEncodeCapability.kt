package com.dualframe.prototype.camera

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.dualframe.prototype.model.DualEncodeSupport

/**
 * Empirically probes whether two concurrent 1080p video encoders can be configured.
 * Camera2 does not expose a reliable "max concurrent encoders" key.
 */
object DualEncodeCapability {
    private val mimeCandidates = listOf(
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        MediaFormat.MIMETYPE_VIDEO_AVC,
    )

    fun probe(context: Context): DualEncodeSupport {
        val reasons = mutableListOf<String>()
        val notes = mutableListOf<String>()

        notes += "Approach A dual encode uses two MediaCodec sessions (L 1920×1080 + P 1080×1920)."
        notes += "This probe configures codecs only; sustained thermal/drop proof needs a phone run."

        val cameraOk = runCatching {
            val probe = CameraCapabilityProbe(context).probe()
            val back = probe.cameras.firstOrNull { it.facing == "BACK" } ?: probe.cameras.firstOrNull()
            if (back == null) {
                reasons += "No cameras found."
                false
            } else {
                val yuv = back.outputSizesByFormat["YUV_420_888"].orEmpty()
                val usable = yuv.any { it.width * it.height >= 1280 * 720 }
                if (!usable) {
                    reasons += "No YUV_420_888 size ≥720p for analysis/crop source."
                } else {
                    notes += "YUV analysis candidate present on camera ${back.cameraId}."
                }
                usable
            }
        }.getOrElse {
            reasons += "Camera probe failed: ${it.message}"
            false
        }

        var chosenMime: String? = null
        var dualOk = false
        var singleOk = false

        for (mime in mimeCandidates) {
            val single = tryConfigure(mime, dual = false)
            if (single == null) {
                notes += "$mime: single 1080p configure failed."
                continue
            }
            singleOk = true
            notes += "$mime: single 1080p configure OK (${single})."
            val dual = tryConfigure(mime, dual = true)
            if (dual != null) {
                dualOk = true
                chosenMime = mime
                notes += "$mime: dual concurrent configure OK (${dual})."
                break
            } else {
                reasons += "$mime: could not configure two concurrent 1080p encoders."
                if (chosenMime == null) chosenMime = mime
            }
        }

        if (!singleOk) {
            reasons += "No MIME could configure even a single 1080p encoder (HEVC/AVC)."
        }
        if (dualOk) {
            reasons.clear()
            notes += "Dual mode offered. Single-orientation modes also available."
        } else if (singleOk) {
            notes += "Degraded: Dual mode disabled; Portrait/Landscape single-file record still available."
        }

        val support = DualEncodeSupport(
            dualSupported = dualOk && cameraOk,
            singleSupported = singleOk && cameraOk,
            preferredMime = chosenMime,
            reasons = if (dualOk && cameraOk) {
                listOf("Dual 1080p MediaCodec configure succeeded (${chosenMime}).")
            } else {
                reasons.distinct()
            },
            notes = notes,
        )
        RecordingLabLog.gate(
            "dual=${support.dualSupported} single=${support.singleSupported} " +
                "mime=${support.preferredMime} reasons=${support.reasons.joinToString(" | ")}",
        )
        return support
    }

    private fun tryConfigure(mime: String, dual: Boolean): String? {
        val codecs = mutableListOf<MediaCodec>()
        return try {
            val landscape = createConfigured(mime, 1920, 1080) ?: return null
            codecs += landscape
            if (dual) {
                val portrait = createConfigured(mime, 1080, 1920) ?: return null
                codecs += portrait
            }
            val name = codecs.joinToString("+") { it.name }
            name
        } catch (t: Throwable) {
            null
        } finally {
            codecs.forEach { runCatching { it.stop() }; runCatching { it.release() } }
        }
    }

    private fun createConfigured(mime: String, width: Int, height: Int): MediaCodec? {
        if (!isMimeEncodable(mime)) return null
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
        }
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        // createInputSurface path not used in probe; bytebuffer path validated by configure.
        return codec
    }

    private fun isMimeEncodable(mime: String): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }

    fun bitrateFor(width: Int, height: Int): Int {
        val pixels = width * height
        return when {
            pixels >= 1920 * 1080 -> 8_000_000
            else -> 4_000_000
        }
    }
}
