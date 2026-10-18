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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val enrolledFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun PeopleScreen(
    profiles: List<FaceProfile>,
    enrollStatus: String,
    onEnroll: (displayName: String, signature: FloatArray) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onFaceSampled: () -> Unit = {},
    onLivenessHint: (String) -> Unit = {},
    enrollLivenessSession: Int = 0,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var name by remember { mutableStateOf("") }
    var pendingSignature by remember { mutableStateOf<FloatArray?>(null) }

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
                        text = "Complete the live check (blink + head turn), then enter a name and save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    FaceRecognitionScreen(
                        statusText = enrollStatus,
                        livenessSessionKey = enrollLivenessSession,
                        onLivenessHint = onLivenessHint,
                        onFaceSignature = { signature ->
                            pendingSignature = signature
                            onFaceSampled()
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
                            val signature = pendingSignature
                            if (trimmed.isNotEmpty() && signature != null) {
                                onEnroll(trimmed, signature)
                                name = ""
                                pendingSignature = null
                            }
                        },
                        enabled = name.isNotBlank() && pendingSignature != null,
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

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Enrolled $enrolledAt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { onDelete(profile.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove profile")
            }
        }
    }
}
