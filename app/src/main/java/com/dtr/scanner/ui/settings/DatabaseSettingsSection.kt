package com.dtr.scanner.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtr.scanner.BuildConfig
import com.dtr.scanner.R
import com.dtr.scanner.data.FaceDataBackup
import com.dtr.scanner.ui.components.DevelopedByFooter
import com.dtr.scanner.ui.theme.TextMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DatabaseSettingsSection(
    onClearHistory: () -> Unit,
    onExportBackup: (Uri, (Result<FaceDataBackup.ImportSummary>) -> Unit) -> Unit,
    onImportBackup: (Uri, (Result<FaceDataBackup.ImportSummary>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(FaceDataBackup.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            onExportBackup(uri) { }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.settings_backup_section),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Text(
            text = stringResource(R.string.export_database_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedButton(
            onClick = {
                val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                exportLauncher.launch("secureattend-backup-$date.dtr.zip")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.export_database))
        }
        OutlinedButton(
            onClick = {
                importLauncher.launch(arrayOf(FaceDataBackup.MIME_TYPE, "application/octet-stream", "*/*"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.import_database))
        }
        TextButton(
            onClick = onClearHistory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        DevelopedByFooter()
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.import_database_confirm_title)) },
            text = { Text(stringResource(R.string.import_database_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportBackup(uri) { }
                        pendingImportUri = null
                    },
                ) {
                    Text(stringResource(R.string.import_database_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
