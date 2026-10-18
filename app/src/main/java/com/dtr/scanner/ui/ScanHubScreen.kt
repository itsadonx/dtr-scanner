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
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode

private const val MODE_QR = 0
private const val MODE_FACE = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHubScreen(
    faceProfiles: List<FaceProfile>,
    faceStatus: String,
    faceLivenessSession: Int,
    onQrScanned: (String) -> Unit,
    onFaceRecognized: (String) -> Unit,
    onFaceUnknown: () -> Unit,
    onFaceAmbiguous: () -> Unit,
    onNeedsReEnroll: () -> Unit,
    onLivenessHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableIntStateOf(MODE_QR) }

    val enrolled = remember(faceProfiles) {
        faceProfiles.mapNotNull { profile ->
            if (!FaceSignature.isCurrentFormat(profile.signature)) return@mapNotNull null
            val signature = FaceSignature.decode(profile.signature) ?: return@mapNotNull null
            profile.displayName to signature
        }
    }

    val hasLegacyProfiles = remember(faceProfiles) {
        faceProfiles.any { !FaceSignature.isCurrentFormat(it.signature) }
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
                val status = when {
                    hasLegacyProfiles && enrolled.isEmpty() ->
                        "Re-enroll everyone on People (profiles were upgraded for accuracy)."

                    enrolled.isEmpty() ->
                        "Enroll faces on the People tab first, then return here."

                    else -> faceStatus
                }
                FaceRecognitionScreen(
                    statusText = status,
                    livenessSessionKey = faceLivenessSession,
                    onLivenessHint = onLivenessHint,
                    consumeLivenessAfterCapture = true,
                    extractMode = ExtractMode.MATCH,
                    onFaceSignature = { probe ->
                        if (hasLegacyProfiles && enrolled.isEmpty()) {
                            onNeedsReEnroll()
                            return@FaceRecognitionScreen
                        }
                        if (enrolled.isEmpty()) return@FaceRecognitionScreen
                        val result = FaceSignature.match(probe, enrolled)
                        when {
                            result.ambiguous -> onFaceAmbiguous()
                            result.displayName != null -> onFaceRecognized(result.displayName)
                            else -> onFaceUnknown()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
