package com.dualframe.prototype.camera

import android.util.Log
import com.dualframe.prototype.model.RecordingStats

/**
 * Structured log hooks for Phase 0 device-lab runs (adb logcat -s DualFrameLab).
 */
object RecordingLabLog {
    const val TAG = "DualFrameLab"

    fun sessionStart(
        pairId: String,
        mode: String,
        mime: String,
        analysisSize: String,
        outputs: String,
    ) {
        Log.i(
            TAG,
            "START pair=$pairId mode=$mode mime=$mime analysis=$analysisSize outputs=$outputs " +
                "t_ms=${System.currentTimeMillis()}",
        )
    }

    fun frameDrop(pairId: String, dropped: Long, submitted: Long, reason: String) {
        Log.w(TAG, "DROP pair=$pairId dropped=$dropped submitted=$submitted reason=$reason")
    }

    fun sessionStop(stats: RecordingStats) {
        Log.i(
            TAG,
            "STOP pair=${stats.pairId} mode=${stats.mode} duration_ms=${stats.durationMs} " +
                "submitted=${stats.framesSubmitted} dropped=${stats.framesDropped} " +
                "audio_packets=${stats.audioPacketsMuxed} " +
                "L=${stats.landscapePath} P=${stats.portraitPath} " +
                "Lres=${stats.landscapeResolution} Pres=${stats.portraitResolution} " +
                "mime=${stats.videoMime} analysis=${stats.analysisResolution} " +
                "error=${stats.errorMessage} " +
                "start_ms=${stats.startEpochMs} stop_ms=${stats.stopEpochMs}",
        )
    }

    fun gate(resultSummary: String) {
        Log.i(TAG, "GATE $resultSummary")
    }
}
