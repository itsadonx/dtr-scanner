package com.dtr.scanner.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import com.dtr.scanner.R
import com.dtr.scanner.camera.QrAnalyzer
import com.dtr.scanner.ui.components.ScanFrameOverlay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onQrScanned: (String) -> Unit,
    useFrontCamera: Boolean,
    onUseFrontCameraChange: (Boolean) -> Unit,
    continuousScan: Boolean,
    onContinuousScanChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val onQrScannedState = rememberUpdatedState(onQrScanned)
    val analyzer = remember { QrAnalyzer { value -> onQrScannedState.value(value) } }
    val lensFacing = if (useFrontCamera) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(lensFacing) {
            var bound by remember { mutableStateOf(false) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    if (bound) return@AndroidView
                    bound = true
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
                                    it.setAnalyzer(cameraExecutor, analyzer)
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
        }

        ScanFrameOverlay(
            instruction = stringResource(R.string.scan_qr_instruction),
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.qr_continuous_scan),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = continuousScan,
                    onCheckedChange = onContinuousScanChange,
                )
            }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                SegmentedButton(
                    selected = useFrontCamera,
                    onClick = { if (!useFrontCamera) onUseFrontCameraChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White.copy(alpha = 0.22f),
                        activeContentColor = Color.White,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = Color.White.copy(alpha = 0.75f),
                    ),
                ) {
                    Text(stringResource(R.string.camera_front))
                }
                SegmentedButton(
                    selected = !useFrontCamera,
                    onClick = { if (useFrontCamera) onUseFrontCameraChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White.copy(alpha = 0.22f),
                        activeContentColor = Color.White,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = Color.White.copy(alpha = 0.75f),
                    ),
                ) {
                    Text(stringResource(R.string.camera_back))
                }
            }
        }
    }
}
