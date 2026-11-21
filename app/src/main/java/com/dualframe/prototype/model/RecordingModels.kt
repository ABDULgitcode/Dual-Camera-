package com.dualframe.prototype.model

/**
 * Result of probing whether Approach A dual 1080p MediaCodec sessions can be configured.
 */
data class DualEncodeSupport(
    val dualSupported: Boolean,
    val singleSupported: Boolean,
    val preferredMime: String?,
    val landscapeWidth: Int = 1920,
    val landscapeHeight: Int = 1080,
    val portraitWidth: Int = 1080,
    val portraitHeight: Int = 1920,
    val targetFps: Int = 30,
    val reasons: List<String>,
    val notes: List<String>,
)

data class RecordingStats(
    val mode: CaptureMode,
    val pairId: String,
    val startEpochMs: Long,
    val stopEpochMs: Long,
    val landscapePath: String?,
    val portraitPath: String?,
    val landscapeResolution: String?,
    val portraitResolution: String?,
    val videoMime: String?,
    val framesSubmitted: Long,
    val framesDropped: Long,
    val audioPacketsMuxed: Long,
    val analysisResolution: String?,
    val errorMessage: String?,
) {
    val durationMs: Long get() = (stopEpochMs - startEpochMs).coerceAtLeast(0)
}

sealed class RecordingUiState {
    data object Idle : RecordingUiState()
    data class Recording(
        val pairId: String,
        val startedAtEpochMs: Long,
        val framesSubmitted: Long,
        val framesDropped: Long,
    ) : RecordingUiState()
    data class Finished(val stats: RecordingStats) : RecordingUiState()
    data class Failed(val message: String) : RecordingUiState()
}
