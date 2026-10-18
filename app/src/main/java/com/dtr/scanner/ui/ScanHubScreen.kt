package com.dtr.scanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val MODE_QR = 0
private const val MODE_FACE = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHubScreen(
    faceProfiles: List<com.dtr.scanner.data.FaceProfile>,
    faceStatus: String,
    onQrScanned: (String) -> Unit,
    onFaceRecognized: (String) -> Unit,
    onFaceUnknown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableIntStateOf(MODE_QR) }

    val enrolled = remember(faceProfiles) {
        faceProfiles.mapNotNull { profile ->
            val signature = com.dtr.scanner.face.FaceSignature.decode(profile.signature)
                ?: return@mapNotNull null
            Triple(profile.id, profile.displayName, signature)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = mode == MODE_QR,
                onClick = { mode = MODE_QR },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("QR code")
            }
            SegmentedButton(
                selected = mode == MODE_FACE,
                onClick = { mode = MODE_FACE },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Face")
            }
        }

        when (mode) {
            MODE_QR -> ScannerScreen(
                onQrScanned = onQrScanned,
                modifier = Modifier.fillMaxSize(),
            )

            else -> {
                val status = if (enrolled.isEmpty()) {
                    "Enroll faces on the People tab first, then return here."
                } else {
                    faceStatus
                }
                FaceRecognitionScreen(
                    statusText = status,
                    onFaceSignature = { probe ->
                        if (enrolled.isEmpty()) return@FaceRecognitionScreen
                        var bestName: String? = null
                        var bestDistance = Float.MAX_VALUE
                        for ((_, name, signature) in enrolled) {
                            val distance = com.dtr.scanner.face.FaceSignature.distance(probe, signature)
                            if (distance < bestDistance) {
                                bestDistance = distance
                                bestName = name
                            }
                        }
                        if (bestName != null && bestDistance <= com.dtr.scanner.face.FaceSignature.MATCH_THRESHOLD) {
                            onFaceRecognized(bestName)
                        } else {
                            onFaceUnknown()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
