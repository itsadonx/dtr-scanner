package com.dtr.scanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtr.scanner.BuildConfig
import com.dtr.scanner.R
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.ui.theme.TextMuted
import com.dtr.scanner.ui.PeopleScreen
import com.dtr.scanner.ui.components.DevelopedByFooter
import com.dtr.scanner.ui.theme.ScreenBackground

@Composable
fun SettingsScreen(
    profiles: List<FaceProfile>,
    enrollStatus: String,
    enrollLivenessSession: Int,
    onEnroll: (FaceEnrollmentInput, onSaved: (FaceProfile) -> Unit) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onFaceSampleProgress: (Int, Int) -> Unit,
    onLivenessHint: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        Text(
            text = stringResource(R.string.settings_face_enrollment),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        PeopleScreen(
            profiles = profiles,
            enrollStatus = enrollStatus,
            onEnroll = onEnroll,
            onDeleteProfile = onDeleteProfile,
            onFaceSampleProgress = onFaceSampleProgress,
            onLivenessHint = onLivenessHint,
            enrollLivenessSession = enrollLivenessSession,
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
        )
        TextButton(
            onClick = onClearHistory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.clear_all_history))
        }
        Text(
            text = stringResource(
                R.string.app_version_label,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        DevelopedByFooter()
    }
}
