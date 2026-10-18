package com.dtr.scanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.face.FaceLivenessTracker.Mode as LivenessMode
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val enrolledFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun PeopleScreen(
    profiles: List<FaceProfile>,
    enrollStatus: String,
    onEnroll: (displayName: String, signatures: List<FloatArray>) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onFaceSampleProgress: (Int, Int) -> Unit = { _, _ -> },
    onLivenessHint: (String) -> Unit = {},
    enrollLivenessSession: Int = 0,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var name by remember { mutableStateOf("") }
    var samples by remember(enrollLivenessSession) { mutableStateOf(listOf<FloatArray>()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Enroll a face",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Blink, turn left/right, nod up/down, then hold still while we capture " +
                            "${FaceSignature.ENROLLMENT_SAMPLES} face samples for this person.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    FaceRecognitionScreen(
                        statusText = enrollStatus,
                        livenessSessionKey = enrollLivenessSession,
                        onLivenessHint = onLivenessHint,
                        extractMode = ExtractMode.ENROLL,
                        livenessMode = LivenessMode.ENROLLMENT,
                        onFaceSignature = { signature ->
                            if (samples.size >= FaceSignature.ENROLLMENT_SAMPLES) return@FaceRecognitionScreen
                            val consistent = samples.isEmpty() ||
                                FaceSignature.distance(samples.last(), signature) < 0.11f
                            if (!consistent) return@FaceRecognitionScreen
                            samples = samples + signature
                            onFaceSampleProgress(samples.size, FaceSignature.ENROLLMENT_SAMPLES)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isNotEmpty() && samples.size >= FaceSignature.ENROLLMENT_SAMPLES) {
                                onEnroll(trimmed, samples)
                                name = ""
                                samples = emptyList()
                            }
                        },
                        enabled = name.isNotBlank() && samples.size >= FaceSignature.ENROLLMENT_SAMPLES,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Save face profile")
                    }
                }
            }
        }

        item {
            Text(
                text = "Enrolled people (${profiles.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (profiles.isEmpty()) {
            item {
                Text(
                    text = "No faces enrolled yet. Add at least one person before using face check-in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(profiles, key = { it.id }) { profile ->
                FaceProfileRow(
                    profile = profile,
                    onDelete = onDeleteProfile,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun FaceProfileRow(
    profile: FaceProfile,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enrolledAt = Instant.ofEpochMilli(profile.enrolledAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(enrolledFormatter)
    val needsReEnroll = !FaceSignature.isCurrentFormat(profile.signature)
    val templateCount = FaceSignature.decodeAll(profile.signature).size

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Enrolled $enrolledAt · $templateCount face template(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (needsReEnroll) {
                Text(
                    text = "Re-enroll this person for improved matching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = { onDelete(profile.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove profile")
            }
        }
    }
}
