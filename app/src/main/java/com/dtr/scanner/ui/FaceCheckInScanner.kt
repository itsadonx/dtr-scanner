package com.dtr.scanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.face.EnrolledFaceTemplate
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode
import com.dtr.scanner.ui.components.FaceScanMatchFeedback
import com.dtr.scanner.ui.components.FaceScanOutcome
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceCheckInScanner(
    enrolled: List<EnrolledFaceTemplate>,
    hasLegacyProfiles: Boolean,
    statusText: String,
    livenessSessionKey: Int,
    onLivenessHint: (String) -> Unit,
    onRecognized: (String) -> Unit,
    onFaceUnknown: () -> Unit,
    onFaceAmbiguous: () -> Unit,
    onNeedsReEnroll: () -> Unit,
    onRescanReady: () -> Unit = {},
    isDuplicateCheckIn: (String) -> Boolean = { false },
    onDuplicateCheckIn: (String) -> Unit = {},
    useFrontCamera: Boolean = true,
    onUseFrontCameraChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var outcome by remember(livenessSessionKey) { mutableStateOf<FaceScanOutcome?>(null) }
    var afterFailure by remember(livenessSessionKey) { mutableStateOf<(() -> Unit)?>(null) }
    var consensusName by remember(livenessSessionKey) { mutableStateOf<String?>(null) }
    var consensusCount by remember(livenessSessionKey) { mutableIntStateOf(0) }
    var unknownStreak by remember(livenessSessionKey) { mutableIntStateOf(0) }

    val matchTemplateFloatCount = remember(enrolled) {
        val sizes = enrolled.map { it.template.size }.toSet()
        when {
            sizes.contains(FaceSignature.v5SignatureFloatCount) -> FaceSignature.v5SignatureFloatCount
            sizes.contains(FaceSignature.signatureFloatCount) -> FaceSignature.signatureFloatCount
            else -> FaceSignature.v5SignatureFloatCount
        }
    }

    LaunchedEffect(outcome) {
        when (val current = outcome) {
            is FaceScanOutcome.Recognized -> {
                delay(SUCCESS_DISPLAY_MS)
                onRecognized(current.displayName)
                outcome = null
                onRescanReady()
            }

            is FaceScanOutcome.RecentDuplicate -> {
                delay(DUPLICATE_DISPLAY_MS)
                onDuplicateCheckIn(current.displayName)
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
            useFrontCamera = useFrontCamera,
            matchTemplateFloatCount = matchTemplateFloatCount,
            onFaceSignature = { probe ->
                if (outcome != null) return@FaceRecognitionScreen
                if (hasLegacyProfiles && enrolled.isEmpty()) {
                    onNeedsReEnroll()
                    return@FaceRecognitionScreen
                }
                if (enrolled.isEmpty()) return@FaceRecognitionScreen
                val result = FaceSignature.match(
                    probe,
                    enrolled.map {
                        FaceSignature.MatchCandidate(
                            profileId = it.profileId,
                            displayName = it.displayName,
                            template = it.template,
                        )
                    },
                )
                when {
                    result.ambiguous -> {
                        consensusName = null
                        consensusCount = 0
                        unknownStreak = 0
                        afterFailure = onFaceAmbiguous
                        outcome = FaceScanOutcome.NotRecognized
                    }

                    result.displayName != null -> {
                        unknownStreak = 0
                        if (consensusName == result.displayName) {
                            consensusCount++
                        } else {
                            consensusName = result.displayName
                            consensusCount = 1
                        }
                        if (consensusCount < MATCH_CONSENSUS_FRAMES) return@FaceRecognitionScreen
                        consensusName = null
                        consensusCount = 0
                        outcome = when {
                            isDuplicateCheckIn(result.displayName) ->
                                FaceScanOutcome.RecentDuplicate(result.displayName)

                            else -> FaceScanOutcome.Recognized(result.displayName)
                        }
                    }

                    else -> {
                        consensusName = null
                        consensusCount = 0
                        unknownStreak++
                        if (unknownStreak >= UNKNOWN_STREAK_LIMIT) {
                            unknownStreak = 0
                            afterFailure = onFaceUnknown
                            outcome = FaceScanOutcome.NotRecognized
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        FaceScanMatchFeedback(
            outcome = outcome,
            modifier = Modifier.zIndex(1f),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
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

private const val SUCCESS_DISPLAY_MS = 1_600L
private const val FAIL_DISPLAY_MS = 1_400L
private const val DUPLICATE_DISPLAY_MS = 1_600L

/** Require repeated agreement before check-in to reduce one-frame mis-identification. */
private const val MATCH_CONSENSUS_FRAMES = 5

private const val UNKNOWN_STREAK_LIMIT = 5
