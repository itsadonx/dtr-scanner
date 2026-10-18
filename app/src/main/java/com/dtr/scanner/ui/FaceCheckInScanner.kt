package com.dtr.scanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode
import com.dtr.scanner.ui.components.FaceScanMatchFeedback
import com.dtr.scanner.ui.components.FaceScanOutcome
import kotlinx.coroutines.delay

@Composable
fun FaceCheckInScanner(
    enrolled: List<Pair<String, FloatArray>>,
    hasLegacyProfiles: Boolean,
    statusText: String,
    livenessSessionKey: Int,
    onLivenessHint: (String) -> Unit,
    onRecognized: (String) -> Unit,
    onFaceUnknown: () -> Unit,
    onFaceAmbiguous: () -> Unit,
    onNeedsReEnroll: () -> Unit,
    onRescanReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var outcome by remember(livenessSessionKey) { mutableStateOf<FaceScanOutcome?>(null) }
    var afterFailure by remember(livenessSessionKey) { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(outcome) {
        when (val current = outcome) {
            is FaceScanOutcome.Recognized -> {
                delay(SUCCESS_DISPLAY_MS)
                onRecognized(current.displayName)
                outcome = null
                onRescanReady()
            }

            FaceScanOutcome.NotRecognized -> {
                delay(FAIL_DISPLAY_MS)
                afterFailure?.invoke()
                afterFailure = null
                outcome = null
                onRescanReady()
            }

            null -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        FaceRecognitionScreen(
            statusText = statusText,
            livenessSessionKey = livenessSessionKey,
            onLivenessHint = onLivenessHint,
            consumeLivenessAfterCapture = true,
            extractMode = ExtractMode.MATCH,
            onFaceSignature = { probe ->
                if (outcome != null) return@FaceRecognitionScreen
                if (hasLegacyProfiles && enrolled.isEmpty()) {
                    onNeedsReEnroll()
                    return@FaceRecognitionScreen
                }
                if (enrolled.isEmpty()) return@FaceRecognitionScreen
                val result = FaceSignature.match(probe, enrolled)
                outcome = when {
                    result.displayName != null -> FaceScanOutcome.Recognized(result.displayName)
                    result.ambiguous -> {
                        afterFailure = onFaceAmbiguous
                        FaceScanOutcome.NotRecognized
                    }

                    else -> {
                        afterFailure = onFaceUnknown
                        FaceScanOutcome.NotRecognized
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        FaceScanMatchFeedback(outcome = outcome)
    }
}

private const val SUCCESS_DISPLAY_MS = 1_600L
private const val FAIL_DISPLAY_MS = 1_400L
