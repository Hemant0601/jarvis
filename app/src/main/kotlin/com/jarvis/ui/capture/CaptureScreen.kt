package com.jarvis.ui.capture

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
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Input view. Single screen holds:
 *   • Big one-tap microphone button (tap to record, tap again to stop).
 *   • Free-form text field + send.
 *   • Live waveform / transcript preview while recording.
 */
@Composable
fun CaptureScreen(
    onFinished: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.committed) {
        if (state.committed) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top: live transcript / partials while recording.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.recording) "Listening…" else "What's on your mind?",
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
        }

        // Middle: the record button.
        RecordButton(
            recording = state.recording,
            amplitude = state.amplitude,
            onClick = { viewModel.toggleRecording() },
        )

        // Bottom: text input + send.
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
                    Icon(Icons.Rounded.Send, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
) {
    val pulse by animateFloatAsState(
        targetValue = if (recording) 1f + amplitude.coerceIn(0f, 1f) * 0.25f else 1f,
        label = "pulse"
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
            modifier = Modifier.size(140.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) Color(0xFFFF6E6E) else MaterialTheme.colorScheme.primary
            ),
        ) {
            Icon(
                imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (recording) "Stop" else "Record",
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
