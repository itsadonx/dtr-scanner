package com.dtr.scanner.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dtr.scanner.camera.FaceAnalyzer
import com.dtr.scanner.face.FaceLivenessTracker
import java.util.concurrent.Executors

@Composable
fun FaceRecognitionScreen(
    statusText: String,
    onFaceSignature: (FloatArray) -> Unit,
    onLivenessHint: (String) -> Unit = {},
    livenessSessionKey: Int = 0,
    consumeLivenessAfterCapture: Boolean = false,
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val livenessTracker = remember { FaceLivenessTracker() }
    var liveHint by remember { mutableStateOf<String?>(null) }

    val lensFacing = if (useFrontCamera) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }

    val faceAnalyzer = remember(livenessTracker) {
        FaceAnalyzer(
            livenessTracker = livenessTracker,
            onLivenessHint = { hint ->
                liveHint = hint
                onLivenessHint(hint)
            },
            onLiveFaceSignature = onFaceSignature,
            consumeLivenessAfterCapture = consumeLivenessAfterCapture,
        )
    }

    DisposableEffect(livenessSessionKey) {
        livenessTracker.resetChallenge()
        liveHint = null
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val bannerText = liveHint ?: statusText

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, faceAnalyzer)
                            }

                        val selector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                analysis,
                            )
                        } catch (_: Exception) {
                            // Camera bind can fail if permission was revoked mid-session.
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = bannerText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
