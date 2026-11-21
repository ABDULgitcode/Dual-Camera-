package com.dualframe.prototype.model

/**
 * Snapshot of Camera2 / CameraX-exposed capability for the Phase 0 probe screen.
 */
data class DeviceCapabilityReport(
    val androidSdkInt: Int,
    val deviceModel: String,
    val manufacturer: String,
    val cameras: List<CameraCapability>,
    val concurrentCameraIds: List<Set<String>>,
    val concurrentSessionNotes: List<String>,
    val dualEncodeSupport: DualEncodeSupport? = null,
)

data class CameraCapability(
    val cameraId: String,
    val facing: String,
    val lensFacingLabel: String,
    val sensorOrientation: Int,
    val hardwareLevel: String,
    val supportedAeTargetFpsRanges: List<String>,
    val outputSizesByFormat: Map<String, List<SizeFpsHint>>,
    val scalerStreamConfigurationMapPresent: Boolean,
    val maxZoomRatio: Float?,
    val notes: List<String>,
)

data class SizeFpsHint(
    val width: Int,
    val height: Int,
    /** High-speed video FPS when available; null for standard output sizes. */
    val highSpeedFpsRange: String? = null,
) {
    val aspectLabel: String
        get() {
            val g = gcd(width, height)
            return "${width / g}:${height / g}"
        }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0) 1 else x
    }
}
