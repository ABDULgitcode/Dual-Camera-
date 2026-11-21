package com.dualframe.prototype.ui

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dualframe.prototype.model.CaptureMode
import com.dualframe.prototype.ui.theme.DualFrameLandscapeStroke
import com.dualframe.prototype.ui.theme.DualFramePortraitStroke
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DualPreview"

@Composable
fun DualPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(CaptureMode.DUAL) }
    var useFront by remember { mutableStateOf(false) }
    var lensEpoch by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC121820))
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = "Dual preview stub",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    useFront = !useFront
                    lensEpoch += 1
                },
            ) {
                Icon(
                    Icons.Filled.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CameraPreviewHost(
                useFront = useFront,
                lensEpoch = lensEpoch,
                cameraProviderRef = cameraProviderRef,
                onError = { cameraError = it },
                onBound = { cameraError = null },
            )
            DualCropOverlay(mode = mode)
            LegendChip(
                mode = mode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
            if (cameraError != null) {
                Text(
                    text = cameraError!!,
                    color = Color(0xFFFFB4A8),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
        }

        Surface(
            color = Color(0xFF1A2332),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CaptureModeSelector(
                    selected = mode,
                    onSelected = { mode = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = { /* Week 1: record UI stub only */ },
                        enabled = false,
                    ) {
                        Icon(
                            Icons.Filled.FiberManualRecord,
                            contentDescription = "Record (Week 2)",
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Record disabled until Week 2 dual 1080p encode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef.get()?.unbindAll()
        }
    }
}

@Composable
private fun LegendChip(
    mode: CaptureMode,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0x99000000),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (mode == CaptureMode.LANDSCAPE || mode == CaptureMode.DUAL) {
                Text("■ 16:9 landscape", color = DualFrameLandscapeStroke)
            }
            if (mode == CaptureMode.PORTRAIT || mode == CaptureMode.DUAL) {
                Text("▨ 9:16 portrait", color = DualFramePortraitStroke)
            }
        }
    }
}

@Composable
private fun CameraPreviewHost(
    useFront: Boolean,
    lensEpoch: Int,
    cameraProviderRef: AtomicReference<ProcessCameraProvider?>,
    onError: (String) -> Unit,
    onBound: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        val provider = future.get()
                        cameraProviderRef.set(provider)
                        provider.unbindAll()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = view.surfaceProvider
                        }
                        val selector = if (useFront) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                        onBound()
                        Log.d(TAG, "Preview bound front=$useFront epoch=$lensEpoch")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to bind preview", t)
                        onError(t.message ?: "Camera bind failed")
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}
