package com.dtr.scanner.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dtr.scanner.BuildConfig
import com.dtr.scanner.R
import com.dtr.scanner.ui.theme.CardWhite
import com.dtr.scanner.ui.theme.RoyalBlue
import com.dtr.scanner.ui.theme.TextMuted

@Composable
fun SettingsSectionNav(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = listOf(
        stringResource(R.string.settings_tab_faces),
        stringResource(R.string.settings_tab_voice),
        stringResource(R.string.settings_tab_database),
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CardWhite,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_sections_title),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = RoyalBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.settings_installed_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
                textAlign = TextAlign.Center,
            )
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardWhite,
                contentColor = RoyalBlue,
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onSelectTab(index) },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selectedTab == index) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
