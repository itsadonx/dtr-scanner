package com.dtr.scanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dtr.scanner.ui.theme.RoyalBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureTopBar(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onLogout: (() -> Unit)? = null,
    showMenu: Boolean = false,
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            when {
                showBack -> {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                }
                showMenu -> {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White)
                    }
                }
            }
        },
        actions = {
            if (onLogout != null) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = RoyalBlue,
            titleContentColor = Color.White,
        ),
    )
}
