package com.dtr.scanner.ui.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dtr.scanner.data.FaceDataBackup
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.speech.AttendanceSpeechAnnouncer
import com.dtr.scanner.ui.PeopleScreen
import com.dtr.scanner.ui.theme.ScreenBackground

const val SETTINGS_SECTION_FACES = 0
const val SETTINGS_SECTION_VOICE = 1
const val SETTINGS_SECTION_DATABASE = 2

@Composable
fun SettingsScreen(
    selectedSection: Int,
    profiles: List<FaceProfile>,
    enrollStatus: String,
    enrollLivenessSession: Int,
    onEnroll: (FaceEnrollmentInput, onSaved: (FaceProfile) -> Unit) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onFaceSampleProgress: (Int, Int) -> Unit,
    onLivenessHint: (String) -> Unit,
    onStartCaptureSession: () -> Unit = {},
    onClearHistory: () -> Unit,
    onExportBackup: (Uri, (Result<FaceDataBackup.ImportSummary>) -> Unit) -> Unit,
    onImportBackup: (Uri, (Result<FaceDataBackup.ImportSummary>) -> Unit) -> Unit,
    speechAnnouncer: AttendanceSpeechAnnouncer,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        when (selectedSection) {
            SETTINGS_SECTION_FACES -> PeopleScreen(
                profiles = profiles,
                enrollStatus = enrollStatus,
                onEnroll = onEnroll,
                onDeleteProfile = onDeleteProfile,
                onFaceSampleProgress = onFaceSampleProgress,
                onLivenessHint = onLivenessHint,
                enrollLivenessSession = enrollLivenessSession,
                onStartCaptureSession = onStartCaptureSession,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            )

            SETTINGS_SECTION_VOICE -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                VoiceAnnouncementSettings(announcer = speechAnnouncer)
            }

            SETTINGS_SECTION_DATABASE -> DatabaseSettingsSection(
                onClearHistory = onClearHistory,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            )
        }
    }
}
