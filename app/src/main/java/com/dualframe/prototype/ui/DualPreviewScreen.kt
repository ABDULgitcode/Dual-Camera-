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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dualframe.prototype.camera.ApproachARecorder
import com.dualframe.prototype.camera.DualEncodeCapability
import com.dualframe.prototype.model.CaptureMode
import com.dualframe.prototype.model.DualEncodeSupport
import com.dualframe.prototype.model.RecordingUiState
import com.dualframe.prototype.ui.theme.DualFrameLandscapeStroke
import com.dualframe.prototype.ui.theme.DualFramePortraitStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DualPreview"

@Composable
fun DualPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(CaptureMode.DUAL) }
    var useFront by remember { mutableStateOf(false) }
    var lensEpoch by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var encodeSupport by remember { mutableStateOf<DualEncodeSupport?>(null) }
    var recorder by remember { mutableStateOf<ApproachARecorder?>(null) }
    var uiState by remember { mutableStateOf<RecordingUiState>(RecordingUiState.Idle) }
    var submitted by remember { mutableLongStateOf(0) }
    var dropped by remember { mutableLongStateOf(0) }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val support = withContext(Dispatchers.Default) {
            DualEncodeCapability.probe(context.applicationContext)
        }
        encodeSupport = support
        if (!support.dualSupported && mode == CaptureMode.DUAL) {
            mode = CaptureMode.LANDSCAPE
        }
        val rec = ApproachARecorder(context.applicationContext, support)
        recorder = rec
        // Rebind when recorder is ready (analysis use case).
        lensEpoch += 1
    }

    LaunchedEffect(uiState) {
        if (uiState is RecordingUiState.Recording) {
            while (isActive && uiState is RecordingUiState.Recording) {
                val counts = recorder?.liveCounters()
                if (counts != null) {
                    submitted = counts.first
                    dropped = counts.second
                    val current = uiState as RecordingUiState.Recording
                    uiState = current.copy(
                        framesSubmitted = counts.first,
                        framesDropped = counts.second,
                    )
                }
                delay(500)
            }
        }
    }

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
            IconButton(
                onClick = {
                    if (recorder?.isRecording() == true) return@IconButton
                    onBack()
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = "Approach A capture",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (recorder?.isRecording() == true) return@IconButton
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
                recorder = recorder,
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
            val recording = uiState as? RecordingUiState.Recording
            if (recording != null) {
                Text(
                    text = "REC ${recording.pairId}  sub=${recording.framesSubmitted} drop=${recording.framesDropped}",
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
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
                val support = encodeSupport
                CaptureModeSelector(
                    selected = mode,
                    onSelected = {
                        if (recorder?.isRecording() != true) mode = it
                    },
                    dualEnabled = support?.dualSupported != false,
                    dualDisabledReason = support
                        ?.takeIf { !it.dualSupported }
                        ?.reasons
                        ?.firstOrNull(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isRecording = recorder?.isRecording() == true
                    val (canRecord, blockReason) = recorder?.canRecord(mode) ?: (false to "Probing…")
                    FilledIconButton(
                        onClick = {
                            val rec = recorder ?: return@FilledIconButton
                            if (isRecording) {
                                scope.launch(Dispatchers.Default) {
                                    val stats = rec.stop()
                                    withContext(Dispatchers.Main) {
                                        uiState = RecordingUiState.Finished(stats)
                                    }
                                }
                            } else {
                                scope.launch(Dispatchers.Default) {
                                    val result = rec.start(mode)
                                    withContext(Dispatchers.Main) {
                                        result.onSuccess { pairId ->
                                            uiState = RecordingUiState.Recording(
                                                pairId = pairId,
                                                startedAtEpochMs = System.currentTimeMillis(),
                                                framesSubmitted = 0,
                                                framesDropped = 0,
                                            )
                                        }.onFailure {
                                            uiState = RecordingUiState.Failed(
                                                it.message ?: "Record failed",
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        enabled = isRecording || canRecord,
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop" else "Record",
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (val s = uiState) {
                                is RecordingUiState.Recording ->
                                    "Recording… submitted=$submitted dropped=$dropped"
                                is RecordingUiState.Finished ->
                                    "Saved pair ${s.stats.pairId} " +
                                        "(${s.stats.durationMs} ms, drop=${s.stats.framesDropped})"
                                is RecordingUiState.Failed -> "Failed: ${s.message}"
                                RecordingUiState.Idle -> when {
                                    !canRecord -> blockReason ?: "Unavailable"
                                    mode == CaptureMode.DUAL ->
                                        "Tap to record dual 1080p (DF_*_L + DF_*_P)"
                                    else -> "Tap to record single 1080p file"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                        val finished = uiState as? RecordingUiState.Finished
                        if (finished != null) {
                            Text(
                                text = listOfNotNull(
                                    finished.stats.landscapePath?.let { "L: $it" },
                                    finished.stats.portraitPath?.let { "P: $it" },
                                ).joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.shutdown()
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
    recorder: ApproachARecorder?,
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
            if (recorder == null) return@AndroidView
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
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            recorder.imageAnalysis,
                        )
                        onBound()
                        Log.d(TAG, "Preview+analysis bound front=$useFront epoch=$lensEpoch")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to bind camera use cases", t)
                        onError(t.message ?: "Camera bind failed")
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}
