package com.dtr.scanner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.ui.theme.TextMuted

@Composable
fun DevelopedByFooter(
    modifier: Modifier = Modifier,
    lightOnDark: Boolean = false,
) {
    Text(
        text = stringResource(R.string.developed_by),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = if (lightOnDark) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
        } else {
            TextMuted
        },
    )
}
