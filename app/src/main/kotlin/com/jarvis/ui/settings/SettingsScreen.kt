package com.jarvis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Models", style = MaterialTheme.typography.titleMedium)

        ModelRow(
            name = "Gemma 3 (on-device)",
            status = state.gemmaStatus,
            progress = state.gemmaDownloadProgress,
            onDownload = viewModel::downloadGemma,
            onRemove = viewModel::removeGemma,
        )
        ModelRow(
            name = "Whisper (on-device)",
            status = state.whisperStatus,
            progress = state.whisperDownloadProgress,
            onDownload = viewModel::downloadWhisper,
            onRemove = viewModel::removeWhisper,
        )
        ModelRow(
            name = "Embedding model (ONNX)",
            status = state.embeddingStatus,
            progress = state.embeddingDownloadProgress,
            onDownload = viewModel::downloadEmbedding,
            onRemove = viewModel::removeEmbedding,
        )

        HorizontalDivider()
        Text("OpenRouter", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.openRouterKey,
            onValueChange = viewModel::setOpenRouterKey,
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )

        HorizontalDivider()
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-backup to Google Drive", modifier = Modifier.weight(1f))
            Switch(checked = state.autoBackupEnabled, onCheckedChange = viewModel::setAutoBackup)
        }
        Text(
            text = state.lastBackupSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            Button(onClick = viewModel::signInWithGoogle) {
                Text(if (state.googleAccount == null) "Connect Google" else "Connected: ${state.googleAccount}")
            }
            Spacer(Modifier.size(12.dp))
            OutlinedButton(onClick = viewModel::backupNow) { Text("Back up now") }
        }

        HorizontalDivider()
        Text("Security", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Biometric unlock", modifier = Modifier.weight(1f))
            Switch(checked = state.biometricEnabled, onCheckedChange = viewModel::setBiometric)
        }
    }
}

@Composable
private fun ModelRow(
    name: String,
    status: String,
    progress: Float?,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress == null) {
                OutlinedButton(onClick = onDownload) { Text("Download") }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
        if (progress != null) {
            Spacer(Modifier.size(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
