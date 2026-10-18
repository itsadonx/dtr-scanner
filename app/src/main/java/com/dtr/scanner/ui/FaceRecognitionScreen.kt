package com.dtr.scanner.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dtr.scanner.R
import com.dtr.scanner.ui.components.ScanFrameOverlay
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
import com.dtr.scanner.face.FaceCapture
import com.dtr.scanner.face.FaceSignature.ExtractMode
import com.dtr.scanner.face.FaceLivenessTracker.Mode as LivenessMode
import java.util.concurrent.Executors

@Composable
fun FaceRecognitionScreen(
    statusText: String,
    onFaceSignature: (FloatArray) -> Unit,
    onFaceCapture: ((FaceCapture) -> Unit)? = null,
    captureFaceThumbnail: Boolean = false,
    onLivenessHint: (String) -> Unit = {},
    livenessSessionKey: Int = 0,
    consumeLivenessAfterCapture: Boolean = false,
    extractMode: ExtractMode = ExtractMode.MATCH,
    livenessMode: LivenessMode = LivenessMode.CHECK_IN,
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

    val faceAnalyzer = remember(livenessTracker, livenessMode, extractMode, captureFaceThumbnail) {
        FaceAnalyzer(
            livenessTracker = livenessTracker,
            onLivenessHint = { hint ->
                liveHint = hint
                onLivenessHint(hint)
            },
            onLiveFaceCapture = { capture ->
                onFaceCapture?.invoke(capture) ?: onFaceSignature(capture.signature)
            },
            consumeLivenessAfterCapture = consumeLivenessAfterCapture,
            extractMode = extractMode,
            livenessMode = livenessMode,
            captureFaceThumbnail = captureFaceThumbnail,
            mirrorForFrontCamera = useFrontCamera,
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

        ScanFrameOverlay(
            instruction = stringResource(R.string.scan_face_instruction),
            statusLine = bannerText,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
