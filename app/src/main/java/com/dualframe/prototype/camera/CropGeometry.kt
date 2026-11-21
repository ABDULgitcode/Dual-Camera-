package com.dualframe.prototype.camera

import android.graphics.Rect

/**
 * Centered crop windows inside a source frame for Approach A composition.
 * Aspects match the Week 1 overlays: landscape 16:9, portrait 9:16.
 */
object CropGeometry {
    fun landscape16x9(sourceWidth: Int, sourceHeight: Int): Rect =
        centeredAspect(sourceWidth, sourceHeight, 16f / 9f)

    fun portrait9x16(sourceWidth: Int, sourceHeight: Int): Rect =
        centeredAspect(sourceWidth, sourceHeight, 9f / 16f)

    fun centeredAspect(sourceWidth: Int, sourceHeight: Int, aspect: Float): Rect {
        val srcAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val width: Int
        val height: Int
        if (srcAspect > aspect) {
            height = sourceHeight
            width = (height * aspect).toInt().coerceAtMost(sourceWidth)
        } else {
            width = sourceWidth
            height = (width / aspect).toInt().coerceAtMost(sourceHeight)
        }
        val left = ((sourceWidth - width) / 2).coerceAtLeast(0)
        val top = ((sourceHeight - height) / 2).coerceAtLeast(0)
        return Rect(left, top, left + width, top + height)
    }
}
