package com.dualframe.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualframe.prototype.model.CaptureMode

@Composable
fun CaptureModeSelector(
    selected: CaptureMode,
    onSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
    dualEnabled: Boolean = true,
    dualDisabledReason: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Capture mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CaptureMode.entries.forEach { mode ->
                val enabled = mode != CaptureMode.DUAL || dualEnabled
                FilterChip(
                    selected = selected == mode,
                    onClick = { if (enabled) onSelected(mode) },
                    enabled = enabled,
                    label = { Text(mode.shortLabel) },
                )
            }
        }
        Text(
            text = when {
                selected == CaptureMode.DUAL && !dualEnabled ->
                    dualDisabledReason ?: "Dual encode unavailable — use Portrait or Landscape."
                selected == CaptureMode.PORTRAIT ->
                    "Records one 1080×1920 file (DF_*_P.mp4) from the 9:16 crop."
                selected == CaptureMode.LANDSCAPE ->
                    "Records one 1920×1080 file (DF_*_L.mp4) from the 16:9 crop."
                else ->
                    "Records paired DF_*_L.mp4 + DF_*_P.mp4 from one stream (Approach A)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
