package com.dualframe.prototype.model

/**
 * Capture mode selector stub (PRD FR-1).
 * Week 1: UI only — Dual does not encode two files yet.
 */
enum class CaptureMode(
    val label: String,
    val shortLabel: String,
) {
    PORTRAIT("Portrait only (9:16)", "Portrait"),
    LANDSCAPE("Landscape only (16:9)", "Landscape"),
    DUAL("Both at once (Dual)", "Dual"),
}
