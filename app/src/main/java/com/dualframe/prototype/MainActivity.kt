package com.dualframe.prototype

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualframe.prototype.ui.CapabilityProbeScreen
import com.dualframe.prototype.ui.DualPreviewScreen
import com.dualframe.prototype.ui.theme.DualFrameTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DualFrameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DualFrameApp()
                }
            }
        }
    }
}

private enum class AppScreen {
    Probe,
    Preview,
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun DualFrameApp() {
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        ),
    )
    var screen by remember { mutableStateOf(AppScreen.Probe) }

    when {
        !permissions.allPermissionsGranted -> {
            PermissionGate(
                onRequest = { permissions.launchMultiplePermissionRequest() },
            )
        }

        screen == AppScreen.Probe -> {
            CapabilityProbeScreen(onOpenPreview = { screen = AppScreen.Preview })
        }

        else -> {
            DualPreviewScreen(onBack = { screen = AppScreen.Probe })
        }
    }
}

@Composable
private fun PermissionGate(
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera and microphone required",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "DualFrame Lab needs the camera for preview/encode and the microphone " +
                "so both output files can share the same AAC audio track.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onRequest) {
            Text("Grant permissions")
        }
    }
}
