package com.dtr.scanner.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.data.ScanRecord
import com.dtr.scanner.data.ScanType
import com.dtr.scanner.ui.components.DevelopedByFooter
import com.dtr.scanner.ui.theme.RoyalBlue
import com.dtr.scanner.ui.theme.ScreenBackground
import com.dtr.scanner.ui.theme.SuccessGreen
import com.dtr.scanner.ui.theme.SuccessGreenBg
import com.dtr.scanner.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val dayFormatter = DateTimeFormatter.ofPattern("EEEE")
private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
private val scanTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun DashboardScreen(
    guardName: String,
    records: List<ScanRecord>,
    onScanQr: () -> Unit,
    onScanFace: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(30_000)
        }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayRecords = records.filter {
        Instant.ofEpochMilli(it.scannedAtEpochMs).atZone(zone).toLocalDate() == today
    }
    val qrToday = todayRecords.count { it.scanType == ScanType.QR }
    val faceToday = todayRecords.count { it.scanType == ScanType.FACE }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
        contentPadding = contentPadding,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RoyalBlue)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = guardName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.guard_role),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            text = stringResource(R.string.guard_location),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoChip(
                    icon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = RoyalBlue) },
                    title = now.atZone(zone).format(dateFormatter),
                    subtitle = now.atZone(zone).format(dayFormatter),
                    modifier = Modifier.weight(1f),
                )
                InfoChip(
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = RoyalBlue) },
                    title = now.atZone(zone).format(timeFormatter),
                    subtitle = stringResource(R.string.local_time),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionCard(
                    title = stringResource(R.string.scan_qr_title),
                    subtitle = stringResource(R.string.employee_attendance),
                    icon = Icons.Default.QrCodeScanner,
                    onClick = onScanQr,
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    title = stringResource(R.string.scan_face_title),
                    subtitle = stringResource(R.string.employee_attendance),
                    icon = Icons.Default.Face,
                    onClick = onScanFace,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.todays_activity),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("${todayRecords.size}", stringResource(R.string.total_scans), Modifier.weight(1f))
                StatCard("$qrToday", stringResource(R.string.qr_scans), Modifier.weight(1f))
                StatCard("$faceToday", stringResource(R.string.face_scans), Modifier.weight(1f))
            }
        }

        item {
            Text(
                text = stringResource(R.string.recent_scans),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (records.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_recent_scans),
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(records.take(8), key = { it.id }) { record ->
                RecentScanRow(record = record, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        item {
            DevelopedByFooter(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun InfoChip(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalBlue),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = RoyalBlue)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun RecentScanRow(record: ScanRecord, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(record.scannedAtEpochMs).atZone(zone).format(scanTimeFormatter)
    val displayName = record.content
        .removePrefix("Face check-in: ")
        .take(40)
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SuccessGreenBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = SuccessGreen, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (record.scanType == ScanType.FACE) "Face attendance" else "QR attendance",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time, style = MaterialTheme.typography.labelMedium)
                Surface(color = SuccessGreenBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "IN",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = SuccessGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
