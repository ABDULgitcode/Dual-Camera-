package com.dualframe.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dualframe.prototype.camera.CameraCapabilityProbe
import com.dualframe.prototype.model.CameraCapability
import com.dualframe.prototype.model.DeviceCapabilityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CapabilityProbeScreen(
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<DeviceCapabilityReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.Default) {
                CameraCapabilityProbe(context.applicationContext).probe()
            }
        }.onSuccess {
            report = it
            loading = false
        }.onFailure {
            error = it.message ?: it.toString()
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "DualFrame capability probe",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Week 1 lab stub — Approach A (single-stream dual crop). " +
                "Lists Camera2 sizes/FPS and concurrent session hints.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Button(
            onClick = onOpenPreview,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open dual preview stub")
        }

        when {
            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Probing cameras…", modifier = Modifier.padding(top = 12.dp))
                }
            }

            error != null -> {
                Text(
                    text = "Probe failed: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            report != null -> {
                val r = report!!
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        InfoCard(title = "Device") {
                            Text("${r.manufacturer} ${r.deviceModel}")
                            Text("Android API ${r.androidSdkInt}")
                            Text("${r.cameras.size} camera id(s)")
                        }
                    }
                    item {
                        InfoCard(title = "Concurrent / session limits") {
                            r.concurrentSessionNotes.forEach { Text("• $it") }
                            if (r.concurrentCameraIds.isNotEmpty()) {
                                Text(
                                    "Concurrent sets:",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                r.concurrentCameraIds.forEach { set ->
                                    Text("  {${set.joinToString()}}")
                                }
                            }
                        }
                    }
                    items(r.cameras, key = { it.cameraId }) { camera ->
                        CameraCard(camera)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CameraCard(camera: CameraCapability) {
    InfoCard(title = "Camera ${camera.cameraId} · ${camera.lensFacingLabel}") {
        Text("Hardware level: ${camera.hardwareLevel}")
        Text("Sensor orientation: ${camera.sensorOrientation}°")
        camera.maxZoomRatio?.let { Text("Max digital zoom: ×$it") }
        Text(
            "AE target FPS ranges: " +
                camera.supportedAeTargetFpsRanges.joinToString().ifEmpty { "(none listed)" },
        )
        camera.notes.forEach { Text("• $it") }
        camera.outputSizesByFormat.forEach { (format, sizes) ->
            if (sizes.isEmpty()) return@forEach
            Text(
                "$format (${sizes.size} shown):",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            sizes.take(12).forEach { size ->
                val fps = size.highSpeedFpsRange?.let { " @ $it" }.orEmpty()
                Text("  ${size.width}×${size.height} (${size.aspectLabel})$fps")
            }
            if (sizes.size > 12) {
                Text("  … +${sizes.size - 12} more")
            }
        }
    }
}
