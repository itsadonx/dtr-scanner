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
    private var lastScanned: String? = null

    @Volatile
    private var lastScanTimeMs: Long = 0L

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
                val value = barcodes.firstOrNull()?.rawValue?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val isDuplicate = value == lastScanned && now - lastScanTimeMs < DUPLICATE_COOLDOWN_MS
                    if (!isDuplicate) {
                        lastScanned = value
                        lastScanTimeMs = now
                        onQrDetected(value)
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    companion object {
        private const val DUPLICATE_COOLDOWN_MS = 2_500L
    }
}
