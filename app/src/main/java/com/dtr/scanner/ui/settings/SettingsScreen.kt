package com.dtr.scanner.ui.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.data.FaceDataBackup
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.speech.AttendanceSpeechAnnouncer
import com.dtr.scanner.ui.PeopleScreen
import com.dtr.scanner.ui.theme.ScreenBackground

private const val TAB_FACES = 0
private const val TAB_VOICE = 1
private const val TAB_DATABASE = 2

@Composable
fun SettingsScreen(
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
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_FACES) }
    val tabTitles = listOf(
        stringResource(R.string.settings_tab_faces),
        stringResource(R.string.settings_tab_voice),
        stringResource(R.string.settings_tab_database),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 12.dp,
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            TAB_FACES -> PeopleScreen(
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

            TAB_VOICE -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                VoiceAnnouncementSettings(announcer = speechAnnouncer)
            }

            TAB_DATABASE -> DatabaseSettingsSection(
                onClearHistory = onClearHistory,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            )
        }
    }
}
