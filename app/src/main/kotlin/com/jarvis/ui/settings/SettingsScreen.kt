package com.jarvis.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.jarvis.llm.ModelKind
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.jarvis.backup.GoogleSignInContract
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
    val context = LocalContext.current
    val signInLauncher = rememberLauncherForActivityResult(GoogleSignInContract()) { email ->
        viewModel.onSignInResult(email)
    }
    val importGemmaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) viewModel.importGemma(uri) }
    val importEmbeddingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) viewModel.importEmbedding(uri) }

    fun openLanding(kind: ModelKind) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.landingPageUrl(kind)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.jarvis.ui.theme.LocalJarvisGradient.current.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.lastErrorMessage?.let { msg ->
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = viewModel::dismissError) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        Text("Models", style = MaterialTheme.typography.titleMedium)

        ModelRow(
            name = "Gemma 3 1B INT4 (on-device)",
            status = state.gemmaStatus,
            progress = state.gemmaDownloadProgress,
            gated = true,
            onGetPage = { openLanding(ModelKind.Gemma) },
            onImport = { importGemmaLauncher.launch(arrayOf("*/*")) },
            onRemove = viewModel::removeGemma,
            extraContent = {
                Column {
                    Text(
                        state.gemmaLoadedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    state.gemmaVerifyResult?.let { result ->
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Jarvis says: $result",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Gemma is gated by Google. Tap \"Get model\" to open HuggingFace, accept the licence, " +
                            "download \"gemma3-1b-it-int4.task\", then tap \"Import file\" to load it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::verifyGemma,
                            enabled = !state.gemmaVerifying,
                        ) {
                            Text(if (state.gemmaVerifying) "Testing…" else "Verify it works")
                        }
                    }
                }
            },
        )
        ModelRow(
            name = "Embedding model (ONNX)",
            status = state.embeddingStatus,
            progress = state.embeddingDownloadProgress,
            gated = false,
            onDownload = viewModel::downloadEmbedding,
            onImport = { importEmbeddingLauncher.launch(arrayOf("*/*")) },
            onRemove = viewModel::removeEmbedding,
        )

        HorizontalDivider()
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        ThemePicker(
            current = state.themeMode,
            onPick = viewModel::setThemeMode,
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
            Button(onClick = { signInLauncher.launch(Unit) }) {
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

        HorizontalDivider()
        DangerZone(onNuke = viewModel::nukeMemories)
    }
}

@Composable
private fun ThemePicker(current: com.jarvis.llm.ThemeMode, onPick: (com.jarvis.llm.ThemeMode) -> Unit) {
    val options = listOf(
        com.jarvis.llm.ThemeMode.AUTO to "Auto",
        com.jarvis.llm.ThemeMode.LIGHT to "Light",
        com.jarvis.llm.ThemeMode.DARK to "Dark",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (mode, label) ->
            val selected = current == mode
            if (selected) {
                androidx.compose.material3.Button(
                    onClick = { onPick(mode) },
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onPick(mode) },
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun DangerZone(onNuke: () -> Unit) {
    val showConfirm = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Column {
        Text(
            "Danger zone",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Clearing memories deletes every note, extracted entity, edge, chunk, " +
                "embedding, and chat message. You'll be left with just the Jarvis root.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = { showConfirm.value = true },
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            ),
        ) {
            Text("Clear all memories")
        }
    }
    if (showConfirm.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm.value = false },
            title = { Text("Clear all memories?") },
            text = {
                Text(
                    "This cannot be undone. Your entire graph will be wiped and " +
                        "a fresh Jarvis root will be created.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirm.value = false
                        onNuke()
                    },
                ) {
                    Text("Nuke it", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirm.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ModelRow(
    name: String,
    status: String,
    progress: Float?,
    gated: Boolean,
    onDownload: (() -> Unit)? = null,
    onGetPage: (() -> Unit)? = null,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Column {
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress != null) {
            Spacer(Modifier.size(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(2.dp))
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gated) {
                    Button(onClick = { onGetPage?.invoke() }) { Text("Get model") }
                } else {
                    Button(onClick = { onDownload?.invoke() }) { Text("Download") }
                }
                OutlinedButton(onClick = onImport) { Text("Import") }
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
        if (extraContent != null) {
            Spacer(Modifier.size(8.dp))
            extraContent()
        }
    }
}
