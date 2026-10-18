package com.dtr.scanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.dtr.scanner.ui.components.DevelopedByFooter
import com.dtr.scanner.ui.theme.RoyalBlue
import com.dtr.scanner.ui.theme.ScreenBackground
import com.dtr.scanner.ui.theme.SuccessGreen
import com.dtr.scanner.ui.theme.SuccessGreenBg
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dtr.scanner.data.ScanRecord
import com.dtr.scanner.data.ScanType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dtrTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun HistoryScreen(
    records: List<ScanRecord>,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (records.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No scans yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "QR scans and face check-ins appear here with the exact time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.id }) { record ->
            ScanRecordRow(
                record = record,
                onDelete = onDelete,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            DevelopedByFooter(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun ScanRecordRow(
    record: ScanRecord,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localTime = Instant.ofEpochMilli(record.scannedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(dtrTimeFormatter)

    val typeLabel = if (record.scanType == ScanType.FACE) "Face" else "QR"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$localTime · $typeLabel",
                style = MaterialTheme.typography.labelLarge,
                color = RoyalBlue,
            )
            Text(
                text = record.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            IconButton(
                onClick = { onDelete(record.id) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete scan",
                )
            }
        }
    }
}
