package com.dtr.scanner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dtr.scanner.ui.HistoryScreen
import com.dtr.scanner.ui.PeopleScreen
import com.dtr.scanner.ui.ScanHubScreen
import com.dtr.scanner.ui.theme.DtrScannerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

private const val TAB_SCAN = 0
private const val TAB_PEOPLE = 1
private const val TAB_HISTORY = 2

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DtrScannerTheme {
                val viewModel: ScanViewModel = viewModel()
                val records by viewModel.records.collectAsStateWithLifecycle()
                val faceProfiles by viewModel.faceProfiles.collectAsStateWithLifecycle()
                var selectedTab by remember { mutableIntStateOf(TAB_SCAN) }
                var showClearDialog by remember { mutableStateOf(false) }
                var faceStatus by remember { mutableStateOf("Blink once, turn your head, then check in.") }
                var enrollStatus by remember { mutableStateOf("Blink once and turn your head to enroll.") }
                var faceLivenessSession by remember { mutableIntStateOf(0) }
                var enrollLivenessSession by remember { mutableIntStateOf(0) }

                val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = {
                                if (selectedTab == TAB_HISTORY && records.isNotEmpty()) {
                                    TextButton(onClick = { showClearDialog = true }) {
                                        Text("Clear all")
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == TAB_SCAN,
                                onClick = { selectedTab = TAB_SCAN },
                                icon = {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                                },
                                label = { Text("Scan") },
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_PEOPLE,
                                onClick = { selectedTab = TAB_PEOPLE },
                                icon = {
                                    Icon(Icons.Default.Face, contentDescription = null)
                                },
                                label = { Text("People") },
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_HISTORY,
                                onClick = { selectedTab = TAB_HISTORY },
                                icon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                label = { Text("History") },
                            )
                        }
                    },
                ) { innerPadding ->
                    when (selectedTab) {
                        TAB_SCAN -> {
                            if (cameraPermission.status.isGranted) {
                                ScanHubScreen(
                                    faceProfiles = faceProfiles,
                                    faceStatus = faceStatus,
                                    faceLivenessSession = faceLivenessSession,
                                    onLivenessHint = { hint -> faceStatus = hint },
                                    onQrScanned = { content ->
                                        viewModel.saveScan(content)
                                        Toast.makeText(
                                            this,
                                            "QR saved at ${formatNow()}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    onFaceRecognized = { name ->
                                        if (viewModel.saveFaceRecognition(name)) {
                                            faceLivenessSession++
                                            faceStatus = "Recognized: $name — live check required for next check-in."
                                            Toast.makeText(
                                                this,
                                                "$name checked in at ${formatNow()}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onFaceUnknown = {
                                        faceStatus = "Face not recognized — complete live check and enroll on People tab."
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                )
                            } else {
                                CameraPermissionContent(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                    showRationale = cameraPermission.status.shouldShowRationale,
                                    onRequest = { cameraPermission.launchPermissionRequest() },
                                )
                            }
                        }

                        TAB_PEOPLE -> {
                            if (cameraPermission.status.isGranted) {
                                PeopleScreen(
                                    profiles = faceProfiles,
                                    enrollStatus = enrollStatus,
                                    onEnroll = { name, signature ->
                                        viewModel.enrollFace(name, signature)
                                        enrollLivenessSession++
                                        enrollStatus = "Saved $name. Live check again for the next person."
                                        Toast.makeText(
                                            this,
                                            "Enrolled $name",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    onFaceSampled = {
                                        enrollStatus = "Live face captured — enter a name and tap Save."
                                    },
                                    onLivenessHint = { hint -> enrollStatus = hint },
                                    enrollLivenessSession = enrollLivenessSession,
                                    onDeleteProfile = viewModel::deleteFaceProfile,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = innerPadding,
                                )
                            } else {
                                CameraPermissionContent(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                    showRationale = cameraPermission.status.shouldShowRationale,
                                    onRequest = { cameraPermission.launchPermissionRequest() },
                                )
                            }
                        }

                        else -> HistoryScreen(
                            records = records,
                            onDelete = viewModel::deleteRecord,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = innerPadding,
                        )
                    }
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("Clear all records?") },
                        text = { Text("This removes every saved QR scan and face check-in from this device.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.clearAll()
                                    showClearDialog = false
                                },
                            ) {
                                Text("Clear")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionContent(
    modifier: Modifier = Modifier,
    showRationale: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (showRationale) {
                "Camera access is required for QR scanning and face check-in."
            } else {
                "Allow camera permission to start scanning."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onRequest,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Grant camera access")
        }
    }
}

private fun formatNow(): String {
    return java.time.Instant.now()
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}
