package com.dtr.scanner.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object FaceThumbnailCropper {

    fun cropToJpeg(
        imageProxy: ImageProxy,
        face: Face,
        mirrorForFrontCamera: Boolean,
    ): ByteArray? {
        val upright = yuvToUprightBitmap(imageProxy) ?: return null

        val box = face.boundingBox
        val padX = (box.width() * 0.22f).roundToInt()
        val padY = (box.height() * 0.28f).roundToInt()
        val left = (box.left - padX).coerceIn(0, upright.width - 1)
        val top = (box.top - padY).coerceIn(0, upright.height - 1)
        val right = (box.right + padX).coerceIn(left + 1, upright.width)
        val bottom = (box.bottom + padY).coerceIn(top + 1, upright.height)
        val cropW = right - left
        val cropH = bottom - top
        if (cropW < 8 || cropH < 8) {
            upright.recycle()
            return null
        }

        var cropped = Bitmap.createBitmap(upright, left, top, cropW, cropH)
        if (upright !== cropped) {
            upright.recycle()
        }

        if (mirrorForFrontCamera) {
            val mirrored = mirrorHorizontal(cropped)
            if (mirrored !== cropped) {
                cropped.recycle()
            }
            cropped = mirrored
        }

        val scaled = scaleDown(cropped, MAX_EDGE_PX)
        if (scaled !== cropped) {
            cropped.recycle()
        }

        return ByteArrayOutputStream().use { stream ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                scaled.recycle()
                null
            } else {
                scaled.recycle()
                stream.toByteArray()
            }
        }
    }

    private fun yuvToUprightBitmap(imageProxy: ImageProxy): Bitmap? {
        val nv21 = yuv420888ToNv21(imageProxy) ?: return null
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null,
        )
        val jpegBytes = ByteArrayOutputStream().use { stream ->
            if (!yuvImage.compressToJpeg(
                    Rect(0, 0, imageProxy.width, imageProxy.height),
                    100,
                    stream,
                )
            ) {
                return null
            }
            stream.toByteArray()
        }
        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val rotated = rotate(bitmap, rotation)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            bitmap = rotated
        }
        return bitmap
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray? {
        val image = imageProxy.image ?: return null
        if (image.format != ImageFormat.YUV_420_888) return null

        val width = imageProxy.width
        val height = imageProxy.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()

        var output = 0
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            var input = row * yRowStride
            for (col in 0 until width) {
                nv21[output++] = yBuffer.get(input)
                input += yPixelStride
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        var uvOutput = ySize
        for (row in 0 until chromaHeight) {
            var uInput = row * uRowStride
            var vInput = row * vRowStride
            for (col in 0 until chromaWidth) {
                nv21[uvOutput++] = vBuffer.get(vInput)
                nv21[uvOutput++] = uBuffer.get(uInput)
                uInput += uPixelStride
                vInput += vPixelStride
            }
        }
        return nv21
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun mirrorHorizontal(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdge) return source
        val scale = maxEdge.toFloat() / longest
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private const val MAX_EDGE_PX = 320
    private const val JPEG_QUALITY = 88
}
