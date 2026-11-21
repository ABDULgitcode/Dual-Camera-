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
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.unit.dp
import com.dualframe.prototype.model.CaptureMode

@Composable
fun CaptureModeSelector(
    selected: CaptureMode,
    onSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
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
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(mode.shortLabel) },
                )
            }
        }
        Text(
            text = when (selected) {
                CaptureMode.PORTRAIT -> "Shows 9:16 crop only. Week 1: composition stub."
                CaptureMode.LANDSCAPE -> "Shows 16:9 crop only. Week 1: composition stub."
                CaptureMode.DUAL -> "Both overlays. Dual encode lands in Week 2."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
