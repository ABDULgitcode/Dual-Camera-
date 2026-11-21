package com.dualframe.prototype.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import com.dualframe.prototype.model.CameraCapability
import com.dualframe.prototype.model.DeviceCapabilityReport
import com.dualframe.prototype.model.SizeFpsHint

/**
 * Lists cameras and Camera2-exposed sizes / FPS / concurrent limits.
 * Approach A Week 1: probe only — no dual encode yet.
 */
class CameraCapabilityProbe(
    private val context: Context,
) {
    fun probe(): DeviceCapabilityReport {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val notes = mutableListOf<String>()
        val concurrentSets = mutableListOf<Set<String>>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val combinations = manager.concurrentCameraIds
                combinations.forEach { set -> concurrentSets += set }
                if (combinations.isEmpty()) {
                    notes += "CameraManager.concurrentCameraIds is empty — " +
                        "OS does not advertise multi-camera concurrent sessions."
                } else {
                    notes += "Concurrent camera ID sets reported by CameraManager (API 30+)."
                }
            } catch (t: Throwable) {
                notes += "concurrentCameraIds unavailable: ${t.message}"
            }
        } else {
            notes += "concurrentCameraIds requires API 30+; this device is API ${Build.VERSION.SDK_INT}."
        }

        notes += "Approach A uses a single camera stream with dual crop — " +
            "concurrent multi-camera is optional (Approach B), not required for Dual files."
        notes += "Max concurrent hardware encoders are not exposed as a single Camera2 key; " +
            "Week 2 will stress dual MediaCodec sessions empirically."

        val cameras = manager.cameraIdList.map { id ->
            probeCamera(manager, id)
        }

        return DeviceCapabilityReport(
            androidSdkInt = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            cameras = cameras,
            concurrentCameraIds = concurrentSets,
            concurrentSessionNotes = notes,
        )
    }

    private fun probeCamera(manager: CameraManager, cameraId: String): CameraCapability {
        val chars = manager.getCameraCharacteristics(cameraId)
        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraMetadata.LENS_FACING_BACK -> "BACK"
            CameraMetadata.LENS_FACING_FRONT -> "FRONT"
            CameraMetadata.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
        val level = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
        val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { rangeLabel(it) }
            .orEmpty()

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizesByFormat = linkedMapOf<String, List<SizeFpsHint>>()
        val cameraNotes = mutableListOf<String>()

        if (map == null) {
            cameraNotes += "SCALER_STREAM_CONFIGURATION_MAP missing"
        } else {
            sizesByFormat["MediaRecorder"] = sizesFor(map, MediaRecorder::class.java)
            sizesByFormat["YUV_420_888"] = sizesFor(map, ImageFormat.YUV_420_888)
            sizesByFormat["PRIVATE"] = sizesFor(map, ImageFormat.PRIVATE)
            sizesByFormat["JPEG"] = sizesFor(map, ImageFormat.JPEG)
            highSpeedHints(map)?.let { sizesByFormat["HighSpeedVideo"] = it }

            val tallest = sizesByFormat.values.flatten()
                .maxByOrNull { it.width * it.height }
            if (tallest != null) {
                cameraNotes += "Largest listed size: ${tallest.width}×${tallest.height} (${tallest.aspectLabel})"
            }
            val nonWide = sizesByFormat["MediaRecorder"].orEmpty()
                .filter { size ->
                    val aspect = size.width.toFloat() / size.height.toFloat()
                    aspect in 0.9f..1.4f // ~1:1 to ~4:3
                }
            if (nonWide.isNotEmpty()) {
                val best = nonWide.maxBy { it.width * it.height }
                cameraNotes += "Tall/square-ish MediaRecorder candidate for Approach A: " +
                    "${best.width}×${best.height} (${best.aspectLabel})"
            } else {
                cameraNotes += "No ~4:3/1:1 MediaRecorder size found in map — " +
                    "portrait crop may need a larger PRIVATE/YUV stream or accept 16:9 source quality loss."
            }
        }

        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)

        return CameraCapability(
            cameraId = cameraId,
            facing = facing,
            lensFacingLabel = facing,
            sensorOrientation = orientation,
            hardwareLevel = level,
            supportedAeTargetFpsRanges = fpsRanges,
            outputSizesByFormat = sizesByFormat,
            scalerStreamConfigurationMapPresent = map != null,
            maxZoomRatio = maxZoom,
            notes = cameraNotes,
        )
    }

    private fun sizesFor(map: StreamConfigurationMap, klass: Class<*>): List<SizeFpsHint> {
        return try {
            map.getOutputSizes(klass)
                ?.sortedByDescending { it.width * it.height }
                ?.take(24)
                ?.map { SizeFpsHint(it.width, it.height) }
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun sizesFor(map: StreamConfigurationMap, format: Int): List<SizeFpsHint> {
        return try {
            map.getOutputSizes(format)
                ?.sortedByDescending { it.width * it.height }
                ?.take(24)
                ?.map { SizeFpsHint(it.width, it.height) }
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun highSpeedHints(map: StreamConfigurationMap): List<SizeFpsHint>? {
        return try {
            val sizes = map.highSpeedVideoSizes ?: return null
            sizes.flatMap { size: Size ->
                val ranges = map.getHighSpeedVideoFpsRangesFor(size) ?: emptyArray()
                if (ranges.isEmpty()) {
                    listOf(SizeFpsHint(size.width, size.height, highSpeedFpsRange = "n/a"))
                } else {
                    ranges.map { range ->
                        SizeFpsHint(
                            width = size.width,
                            height = size.height,
                            highSpeedFpsRange = rangeLabel(range),
                        )
                    }
                }
            }.take(24)
        } catch (_: Throwable) {
            null
        }
    }

    private fun rangeLabel(range: Range<Int>): String = "${range.lower}–${range.upper} fps"
}
