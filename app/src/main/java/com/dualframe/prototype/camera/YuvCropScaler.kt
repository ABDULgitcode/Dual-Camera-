package com.dualframe.prototype.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * CPU crop/scale from CameraX YUV_420_888 into NV12 for MediaCodec byte-buffer input.
 * Lab quality: nearest-neighbor. Expect frame drops on mid-range SoCs — logged intentionally.
 */
object YuvCropScaler {
    fun imageToNv12CropScaled(
        image: ImageProxy,
        crop: Rect,
        outWidth: Int,
        outHeight: Int,
        out: ByteArray,
    ) {
        require(image.format == ImageFormat.YUV_420_888) {
            "Expected YUV_420_888, got ${image.format}"
        }
        require(out.size >= outWidth * outHeight * 3 / 2) { "NV12 buffer too small" }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val yBuffer = yPlane.buffer.duplicateSafe()
        val uBuffer = uPlane.buffer.duplicateSafe()
        val vBuffer = vPlane.buffer.duplicateSafe()

        val cropW = crop.width().coerceAtLeast(1)
        val cropH = crop.height().coerceAtLeast(1)

        // Y plane
        for (outY in 0 until outHeight) {
            val srcY = crop.top + (outY * cropH) / outHeight
            for (outX in 0 until outWidth) {
                val srcX = crop.left + (outX * cropW) / outWidth
                val yIndex = srcY * yRowStride + srcX * yPixelStride
                out[outY * outWidth + outX] = yBuffer.getOrZero(yIndex)
            }
        }

        // NV12 UV interleaved (VU or UV depending on device — use U then V as NV12)
        val uvOutBase = outWidth * outHeight
        val outUvHeight = outHeight / 2
        val outUvWidth = outWidth / 2
        for (outY in 0 until outUvHeight) {
            val srcY = (crop.top + (outY * 2 * cropH) / outHeight) / 2
            for (outX in 0 until outUvWidth) {
                val srcX = (crop.left + (outX * 2 * cropW) / outWidth) / 2
                val uIndex = srcY * uRowStride + srcX * uPixelStride
                val vIndex = srcY * vRowStride + srcX * vPixelStride
                val outIndex = uvOutBase + outY * outWidth + outX * 2
                out[outIndex] = uBuffer.getOrZero(uIndex)
                out[outIndex + 1] = vBuffer.getOrZero(vIndex)
            }
        }
    }

    fun nv12ByteSize(width: Int, height: Int): Int = width * height * 3 / 2

    private fun ByteBuffer.duplicateSafe(): ByteBuffer = this.duplicate().apply { rewind() }

    private fun ByteBuffer.getOrZero(index: Int): Byte {
        return if (index in 0 until limit()) get(index) else 0
    }
}
