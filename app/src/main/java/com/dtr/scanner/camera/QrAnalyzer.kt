package com.dtr.scanner.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrAnalyzer(
    private val onQrDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @Volatile
    private var heldValue: String? = null

    @Volatile
    private var lastSeenMs: Long = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val now = System.currentTimeMillis()
                val value = barcodes.firstOrNull()?.rawValue?.trim().orEmpty()
                if (value.isEmpty()) {
                    if (heldValue != null && now - lastSeenMs >= ABSENT_RESET_MS) {
                        heldValue = null
                    }
                    return@addOnSuccessListener
                }
                lastSeenMs = now
                if (value == heldValue) return@addOnSuccessListener
                heldValue = value
                onQrDetected(value)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    companion object {
        /** Same code is read once until it leaves the camera, so continuous scan does not beep again while it stays in frame. */
        private const val ABSENT_RESET_MS = 1_200L
    }
}
