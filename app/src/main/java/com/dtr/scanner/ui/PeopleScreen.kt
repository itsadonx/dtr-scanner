package com.dtr.scanner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.data.FaceTemplateStore
import com.dtr.scanner.face.FaceLivenessTracker.Mode as LivenessMode
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode
import com.dtr.scanner.ui.components.FaceTemplatePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val enrolledFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun PeopleScreen(
    profiles: List<FaceProfile>,
    enrollStatus: String,
    onEnroll: (FaceEnrollmentInput, onSaved: (FaceProfile) -> Unit) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onFaceSampleProgress: (Int, Int) -> Unit = { _, _ -> },
    onLivenessHint: (String) -> Unit = {},
    enrollLivenessSession: Int = 0,
    onStartCaptureSession: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var businessUnit by rememberSaveable { mutableStateOf("") }
    var showCaptureCamera by rememberSaveable(enrollLivenessSession) { mutableStateOf(false) }
    var samples by remember(enrollLivenessSession) { mutableStateOf(listOf<FloatArray>()) }
    var templateJpegs by remember(enrollLivenessSession) { mutableStateOf(listOf<ByteArray>()) }
    var templatesDialogProfile by remember { mutableStateOf<FaceProfile?>(null) }

    val detailsComplete = fullName.isNotBlank() &&
        area.isNotBlank() &&
        businessUnit.isNotBlank()

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
                        text = "Register face",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Enter employee details first. Employee ID is assigned when you save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("Area *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = businessUnit,
                        onValueChange = { businessUnit = it },
                        label = { Text("Business unit (BU) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Button(
                        onClick = {
                            showCaptureCamera = true
                            onStartCaptureSession()
                        },
                        enabled = detailsComplete && !showCaptureCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text("Start face capture")
                    }
                    if (showCaptureCamera) {
                        Text(
                            text = "After the live check, look straight at the camera for " +
                                "${FaceSignature.ENROLLMENT_SAMPLES} samples (blink/head motion first).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                        )
                        FaceRecognitionScreen(
                            statusText = enrollStatus,
                            livenessSessionKey = enrollLivenessSession,
                            onLivenessHint = onLivenessHint,
                            extractMode = ExtractMode.ENROLL,
                            livenessMode = LivenessMode.ENROLLMENT,
                            useDedicatedHintBar = false,
                            captureFaceThumbnail = true,
                            onFaceCapture = { capture ->
                                if (samples.size >= FaceSignature.ENROLLMENT_SAMPLES) {
                                    return@FaceRecognitionScreen
                                }
                                val signature = capture.signature
                                val consistent = samples.isEmpty() ||
                                    FaceSignature.distance(samples.last(), signature) < 0.22f
                                if (!consistent) return@FaceRecognitionScreen
                                samples = samples + signature
                                capture.thumbnailJpeg?.let { jpeg ->
                                    templateJpegs = templateJpegs + jpeg
                                }
                                onFaceSampleProgress(samples.size, FaceSignature.ENROLLMENT_SAMPLES)
                            },
                            onFaceSignature = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(bottom = 8.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                showCaptureCamera = false
                                samples = emptyList()
                                templateJpegs = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cancel capture")
                        }
                    }
                    Button(
                        onClick = {
                            val trimmed = fullName.trim()
                            if (trimmed.isNotEmpty() && samples.size >= FaceSignature.ENROLLMENT_SAMPLES) {
                                val input = FaceEnrollmentInput(
                                    fullName = trimmed,
                                    area = area.trim(),
                                    businessUnit = businessUnit.trim(),
                                    signatures = samples,
                                    templateJpegs = templateJpegs,
                                )
                                onEnroll(input) {
                                    fullName = ""
                                    area = ""
                                    businessUnit = ""
                                    samples = emptyList()
                                    templateJpegs = emptyList()
                                    showCaptureCamera = false
                                }
                            }
                        },
                        enabled = detailsComplete &&
                            samples.size >= FaceSignature.ENROLLMENT_SAMPLES,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text("Save registration")
                    }
                }
            }
        }

        item {
            Text(
                text = "Registered people (${profiles.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (profiles.isEmpty()) {
            item {
                Text(
                    text = "No faces registered yet. Add at least one person before using face check-in.",
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
                    onViewTemplates = { templatesDialogProfile = profile },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    templatesDialogProfile?.let { profile ->
        FaceTemplatesDialog(
            profile = profile,
            onDismiss = { templatesDialogProfile = null },
        )
    }
}

@Composable
private fun FaceTemplatesDialog(
    profile: FaceProfile,
    onDismiss: () -> Unit,
) {
    val templates = remember(profile.signature) { FaceSignature.decodeAll(profile.signature) }
    val imagePaths = remember(profile.templateImagePaths) {
        FaceTemplateStore.decodePaths(profile.templateImagePaths)
    }
    val enrolledAt = Instant.ofEpochMilli(profile.enrolledAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(enrolledFormatter)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Face templates — ${profile.fullName}") },
        text = {
            Column {
                Text(
                    text = "ID ${profile.id} · ${profile.area.ifBlank { "—" }} · " +
                        "BU ${profile.businessUnit.ifBlank { "—" }} · Registered $enrolledAt",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val slotCount = max(templates.size, imagePaths.size)
                if (slotCount == 0) {
                    Text("No readable templates. Re-register this person.")
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        repeat(slotCount) { index ->
                            FaceTemplatePreview(
                                signature = templates.getOrNull(index) ?: FloatArray(0),
                                label = FaceSignature.templateLabel(
                                    index = index,
                                    totalTemplates = max(templates.size, slotCount),
                                ),
                                imagePath = imagePaths.getOrNull(index),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun FaceProfileRow(
    profile: FaceProfile,
    onDelete: (Long) -> Unit,
    onViewTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enrolledAt = Instant.ofEpochMilli(profile.enrolledAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(enrolledFormatter)
    val needsReEnroll = !FaceSignature.isCurrentFormat(profile.signature)
    val templateCount = FaceSignature.decodeAll(profile.signature).size

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ID ${profile.id} · ${profile.fullName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Area: ${profile.area.ifBlank { "—" }} · BU: ${profile.businessUnit.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Registered $enrolledAt · $templateCount template(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (needsReEnroll) {
                Text(
                    text = "Re-register this person for improved matching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row {
                IconButton(onClick = onViewTemplates) {
                    Icon(Icons.Default.Visibility, contentDescription = "View face templates")
                }
                IconButton(onClick = { onDelete(profile.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove profile")
                }
            }
        }
    }
}
