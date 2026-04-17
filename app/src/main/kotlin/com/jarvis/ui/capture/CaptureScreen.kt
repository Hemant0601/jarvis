package com.jarvis.ui.capture

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CaptureScreen(
    onFinished: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingRecord by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingRecord) viewModel.toggleRecording()
        pendingRecord = false
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional — foreground-service recording nicety */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.committed) {
        if (state.committed) onFinished()
    }

    val micGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    state.errorMessage != null -> "Microphone error"
                    state.recording -> "Listening…"
                    else -> "What's on your mind?"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(visible = state.partialTranscript.isNotBlank()) {
                Text(
                    text = state.partialTranscript,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.errorMessage?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }

        RecordButton(
            recording = state.recording,
            enabled = micGranted || !state.recording,
            amplitude = state.amplitude,
            onClick = {
                if (!micGranted) {
                    pendingRecord = true
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.toggleRecording()
                }
            },
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Or type a thought…") },
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = viewModel::commitDraft,
                    enabled = state.draft.isNotBlank() && !state.recording,
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
) {
    val pulse by animateFloatAsState(
        targetValue = if (recording) 1f + amplitude.coerceIn(0f, 1f) * 0.25f else 1f,
        label = "pulse",
    )
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(
                Modifier
                    .size(220.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6E6E).copy(alpha = 0.18f))
            )
        }
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(140.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) Color(0xFFFF6E6E) else MaterialTheme.colorScheme.primary
            ),
        ) {
            Icon(
                imageVector = when {
                    !enabled -> Icons.Rounded.MicOff
                    recording -> Icons.Rounded.Stop
                    else -> Icons.Rounded.Mic
                },
                contentDescription = if (recording) "Stop" else "Record",
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
