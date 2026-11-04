package com.dtr.scanner.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.ui.components.DevelopedByFooter
import com.dtr.scanner.ui.theme.NavyDeep
import com.dtr.scanner.ui.theme.NavyMid
import com.dtr.scanner.ui.theme.RoyalBlue
import com.dtr.scanner.ui.theme.SkyAccent

@Composable
fun LoginScreen(
    serverUrl: String,
    deviceKey: String,
    syncStatus: String,
    onSaveServer: (String, String) -> Unit,
    onLogin: (guardId: String, password: String, onFinished: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var guardId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var url by rememberSaveable { mutableStateOf(serverUrl) }
    var key by rememberSaveable { mutableStateOf(deviceKey) }
    var showServer by rememberSaveable { mutableStateOf(serverUrl.isBlank()) }
    var busy by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyMid, NavyDeep, Color(0xFF06102E)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = SkyAccent,
                    modifier = Modifier.size(44.dp),
                )
            }
            Text(
                text = stringResource(R.string.brand_secure),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.brand_attend),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = SkyAccent,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.guard_login_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = RoyalBlue,
                    )
                    Text(
                        text = stringResource(R.string.guard_login_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = guardId,
                        onValueChange = { guardId = it; error = null },
                        label = { Text(stringResource(R.string.guard_id_label)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(stringResource(R.string.password_label)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showServer = !showServer }) {
                        Text(if (showServer) "Hide companion server" else "Companion server")
                    }
                    if (showServer) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Website address") },
                            placeholder = { Text("http://192.168.1.10:8787") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text("Device key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { onSaveServer(url, key) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save and download accounts")
                        }
                        Text(
                            text = syncStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            when {
                                guardId.isBlank() || password.isBlank() ->
                                    error = "Enter Guard ID and password."
                                else -> {
                                    busy = true
                                    error = null
                                    onLogin(guardId.trim(), password) { message ->
                                        busy = false
                                        error = message
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.login_button))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.authorized_personnel),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            DevelopedByFooter(lightOnDark = true)
        }
    }
}
