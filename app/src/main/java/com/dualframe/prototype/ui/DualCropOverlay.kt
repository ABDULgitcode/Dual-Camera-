package com.dualframe.prototype.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dualframe.prototype.model.CaptureMode
import com.dualframe.prototype.ui.theme.DualFrameLandscapeStroke
import com.dualframe.prototype.ui.theme.DualFramePortraitStroke

/**
 * Composition-only crop overlays on the viewfinder.
 * Landscape = 16:9, Portrait = 9:16, both centered on the preview plane.
 */
@Composable
fun DualCropOverlay(
    mode: CaptureMode,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

        val landscape = centeredAspectRect(size, aspect = 16f / 9f, fill = 0.92f)
        val portrait = centeredAspectRect(size, aspect = 9f / 16f, fill = 0.92f)

        fun drawFrame(rect: Rect, color: Color, solid: Boolean) {
            drawRect(
                color = color.copy(alpha = 0.12f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
            )
            drawRect(
                color = color,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = if (solid) null else dash,
                ),
            )
        }

        when (mode) {
            CaptureMode.LANDSCAPE -> drawFrame(landscape, DualFrameLandscapeStroke, solid = true)
            CaptureMode.PORTRAIT -> drawFrame(portrait, DualFramePortraitStroke, solid = true)
            CaptureMode.DUAL -> {
                drawFrame(landscape, DualFrameLandscapeStroke, solid = true)
                drawFrame(portrait, DualFramePortraitStroke, solid = false)
            }
        }
    }
}

/**
 * Largest rectangle of [aspect] (width/height) that fits inside [viewport],
 * scaled by [fill] and centered.
 */
internal fun centeredAspectRect(
    viewport: Size,
    aspect: Float,
    fill: Float,
): Rect {
    val maxW = viewport.width * fill
    val maxH = viewport.height * fill
    val width: Float
    val height: Float
    if (maxW / maxH > aspect) {
        height = maxH
        width = height * aspect
    } else {
        width = maxW
        height = width / aspect
    }
    val left = (viewport.width - width) / 2f
    val top = (viewport.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}
