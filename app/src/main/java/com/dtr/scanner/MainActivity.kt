package com.dtr.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dtr.scanner.ui.SecureAttendApp
import com.dtr.scanner.ui.theme.DtrScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DtrScannerTheme {
                SecureAttendApp()
            }
        }
    }
}
