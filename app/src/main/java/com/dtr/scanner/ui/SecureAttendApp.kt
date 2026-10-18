package com.dtr.scanner.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dtr.scanner.R
import com.dtr.scanner.ScanViewModel
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode
import com.dtr.scanner.ui.components.SecureTopBar
import com.dtr.scanner.ui.dashboard.DashboardScreen
import com.dtr.scanner.ui.login.LoginScreen
import com.dtr.scanner.ui.settings.SettingsScreen
import com.dtr.scanner.ui.theme.RoyalBlue
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.time.format.DateTimeFormatter

private const val TAB_DASHBOARD = 0
private const val TAB_HISTORY = 1
private const val TAB_SETTINGS = 2

private enum class ActiveScan { QR, FACE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SecureAttendApp() {
    val context = LocalContext.current
    val viewModel: ScanViewModel = viewModel()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val faceProfiles by viewModel.faceProfiles.collectAsStateWithLifecycle()

    var loggedIn by rememberSaveable { mutableStateOf(false) }
    var guardName by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_DASHBOARD) }
    var activeScan by remember { mutableStateOf<ActiveScan?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    var faceStatus by rememberSaveable { mutableStateOf("Blink once to check in.") }
    var enrollStatus by rememberSaveable { mutableStateOf("Blink and move your head to start enrollment.") }
    var faceLivenessSession by rememberSaveable { mutableIntStateOf(0) }
    var enrollLivenessSession by rememberSaveable { mutableIntStateOf(0) }

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (!loggedIn) {
        LoginScreen(onLogin = { id ->
            guardName = id
            loggedIn = true
        })
        return
    }

    if (activeScan != null && cameraPermission.status.isGranted) {
        val onBack = { activeScan = null }
        Scaffold(
            topBar = {
                SecureTopBar(
                    title = when (activeScan) {
                        ActiveScan.QR -> stringResource(R.string.scan_qr_title)
                        ActiveScan.FACE -> stringResource(R.string.scan_face_title)
                        null -> ""
                    },
                    showBack = true,
                    onBack = onBack,
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.Black,
        ) { padding ->
            when (activeScan) {
                ActiveScan.QR -> ScannerScreen(
                    onQrScanned = { content ->
                        viewModel.saveScan(content)
                        Toast.makeText(
                            context,
                            "Saved at ${formatNow()}",
                            Toast.LENGTH_SHORT,
                        ).show()
                        activeScan = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )

                ActiveScan.FACE -> FaceScanContent(
                    faceProfiles = faceProfiles,
                    faceStatus = faceStatus,
                    faceLivenessSession = faceLivenessSession,
                    onLivenessHint = { faceStatus = it },
                    onRecognized = { name ->
                        if (viewModel.saveFaceRecognition(name)) {
                            faceLivenessSession++
                            Toast.makeText(
                                context,
                                "$name checked in at ${formatNow()}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            activeScan = null
                        }
                    },
                    onFaceUnknown = {
                        faceStatus = "Not recognized — enroll on Settings."
                    },
                    onFaceAmbiguous = {
                        faceStatus = "Look straight at the camera and try again."
                    },
                    onNeedsReEnroll = {
                        faceStatus = "Re-enroll faces in Settings (profiles upgraded)."
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )

                null -> Unit
            }
        }
        return
    }

    if (activeScan != null && !cameraPermission.status.isGranted) {
        Scaffold(
            topBar = { SecureTopBar(title = "Camera", showBack = true, onBack = { activeScan = null }) },
        ) { padding ->
            CameraPermissionContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                showRationale = cameraPermission.status.shouldShowRationale,
                onRequest = { cameraPermission.launchPermissionRequest() },
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SecureTopBar(
                title = when (selectedTab) {
                    TAB_DASHBOARD -> stringResource(R.string.nav_dashboard)
                    TAB_HISTORY -> stringResource(R.string.nav_history)
                    else -> stringResource(R.string.nav_settings)
                },
                showMenu = selectedTab == TAB_DASHBOARD,
                onLogout = {
                    loggedIn = false
                    guardName = ""
                    selectedTab = TAB_DASHBOARD
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                NavigationBarItem(
                    selected = selectedTab == TAB_DASHBOARD,
                    onClick = { selectedTab = TAB_DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_dashboard)) },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_HISTORY,
                    onClick = { selectedTab = TAB_HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_SETTINGS,
                    onClick = { selectedTab = TAB_SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = navColors(),
                )
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            TAB_DASHBOARD -> DashboardScreen(
                guardName = guardName,
                records = records,
                onScanQr = { activeScan = ActiveScan.QR },
                onScanFace = { activeScan = ActiveScan.FACE },
                onViewAllHistory = { selectedTab = TAB_HISTORY },
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            )

            TAB_HISTORY -> HistoryScreen(
                records = records,
                onDelete = viewModel::deleteRecord,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            )

            TAB_SETTINGS -> {
                if (cameraPermission.status.isGranted) {
                    SettingsScreen(
                        profiles = faceProfiles,
                        enrollStatus = enrollStatus,
                        enrollLivenessSession = enrollLivenessSession,
                        onEnroll = { name, signatures ->
                            viewModel.enrollFace(name, signatures)
                            enrollLivenessSession++
                            enrollStatus = "Saved $name."
                            Toast.makeText(context, "Enrolled $name", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteProfile = viewModel::deleteFaceProfile,
                        onFaceSampleProgress = { captured, total ->
                            enrollStatus = "Sample $captured of $total — enter name and save."
                        },
                        onLivenessHint = { enrollStatus = it },
                        onClearHistory = { showClearDialog = true },
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

@Composable
private fun FaceScanContent(
    faceProfiles: List<com.dtr.scanner.data.FaceProfile>,
    faceStatus: String,
    faceLivenessSession: Int,
    onLivenessHint: (String) -> Unit,
    onRecognized: (String) -> Unit,
    onFaceUnknown: () -> Unit,
    onFaceAmbiguous: () -> Unit,
    onNeedsReEnroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enrolled = androidx.compose.runtime.remember(faceProfiles) {
        faceProfiles.flatMap { profile ->
            if (!FaceSignature.isCurrentFormat(profile.signature)) return@flatMap emptyList()
            FaceSignature.decodeAll(profile.signature).map { profile.displayName to it }
        }
    }
    val hasLegacy = faceProfiles.any { !FaceSignature.isCurrentFormat(it.signature) }
    val status = when {
        hasLegacy && enrolled.isEmpty() -> "Re-enroll everyone in Settings."
        enrolled.isEmpty() -> "Enroll faces in Settings first."
        else -> faceStatus
    }

    FaceRecognitionScreen(
        statusText = status,
        livenessSessionKey = faceLivenessSession,
        onLivenessHint = onLivenessHint,
        consumeLivenessAfterCapture = true,
        extractMode = ExtractMode.MATCH,
        onFaceSignature = { probe ->
            if (hasLegacy && enrolled.isEmpty()) {
                onNeedsReEnroll()
                return@FaceRecognitionScreen
            }
            if (enrolled.isEmpty()) return@FaceRecognitionScreen
            val result = FaceSignature.match(probe, enrolled)
            when {
                result.displayName != null -> onRecognized(result.displayName)
                result.ambiguous -> onFaceAmbiguous()
                else -> onFaceUnknown()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun navColors() = androidx.compose.material3.NavigationBarItemDefaults.colors(
    selectedIconColor = RoyalBlue,
    selectedTextColor = RoyalBlue,
    indicatorColor = RoyalBlue.copy(alpha = 0.12f),
)

@Composable
private fun CameraPermissionContent(
    modifier: Modifier = Modifier,
    showRationale: Boolean,
    onRequest: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = if (showRationale) {
                "Camera access is required for QR scanning and face check-in."
            } else {
                "Allow camera permission to start scanning."
            },
        )
        androidx.compose.material3.Button(
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
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
