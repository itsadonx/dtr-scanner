package com.dtr.scanner.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.speech.AttendancePhrase
import com.dtr.scanner.speech.AttendanceSpeechAnnouncer
import com.dtr.scanner.speech.SpeechPreferences
import com.dtr.scanner.ui.theme.TextMuted

@Composable
fun VoiceAnnouncementSettings(
    announcer: AttendanceSpeechAnnouncer,
    modifier: Modifier = Modifier,
) {
    val prefs = remember { announcer.preferences() }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var rate by remember { mutableFloatStateOf(prefs.speechRate) }
    var pitch by remember { mutableFloatStateOf(prefs.pitch) }
    var phrase by remember { mutableStateOf(prefs.attendancePhrase) }
    var thankYou by remember { mutableStateOf(prefs.sayThankYou) }
    var use24Hour by remember { mutableStateOf(prefs.use24HourTime) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_voice_section),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
        )
        Text(
            text = stringResource(R.string.settings_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        VoiceToggleRow(
            label = stringResource(R.string.settings_voice_enabled),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                prefs.enabled = it
            },
        )

        VoiceSliderRow(
            label = stringResource(R.string.settings_voice_speed),
            value = rate,
            valueRange = SpeechPreferences.MIN_RATE..SpeechPreferences.MAX_RATE,
            onValueChange = { rate = it },
            onValueChangeFinished = { prefs.speechRate = rate },
            valueLabel = "${(rate * 100).toInt()}%",
        )

        VoiceSliderRow(
            label = stringResource(R.string.settings_voice_pitch),
            value = pitch,
            valueRange = SpeechPreferences.MIN_PITCH..SpeechPreferences.MAX_PITCH,
            onValueChange = { pitch = it },
            onValueChangeFinished = { prefs.pitch = pitch },
            valueLabel = "${(pitch * 100).toInt()}%",
        )

        Text(
            text = stringResource(R.string.settings_voice_phrase_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            AttendancePhrase.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = phrase == option,
                    onClick = {
                        phrase = option
                        prefs.attendancePhrase = option
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AttendancePhrase.entries.size,
                    ),
                ) {
                    Text(
                        text = when (option) {
                            AttendancePhrase.TIME_IN -> stringResource(R.string.settings_voice_phrase_time_in)
                            AttendancePhrase.TIME_OUT -> stringResource(R.string.settings_voice_phrase_time_out)
                            AttendancePhrase.CHECKED_IN -> stringResource(R.string.settings_voice_phrase_checked_in)
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        VoiceToggleRow(
            label = stringResource(R.string.settings_voice_thank_you),
            checked = thankYou,
            onCheckedChange = {
                thankYou = it
                prefs.sayThankYou = it
            },
        )

        VoiceToggleRow(
            label = stringResource(R.string.settings_voice_time_24h),
            checked = use24Hour,
            onCheckedChange = {
                use24Hour = it
                prefs.use24HourTime = it
            },
        )

        val previewText = remember(phrase, thankYou, use24Hour, rate, pitch) {
            prefs.buildAnnouncement("Adonnis Pama")
        }
        Text(
            text = stringResource(R.string.settings_voice_preview_sample, previewText),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        OutlinedButton(
            onClick = { announcer.previewSample("Adonnis Pama") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.settings_voice_test))
        }
    }
}

@Composable
private fun VoiceToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VoiceSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = valueLabel, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}
