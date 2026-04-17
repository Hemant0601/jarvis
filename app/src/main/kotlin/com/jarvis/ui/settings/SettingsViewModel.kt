package com.jarvis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.backup.BackupManager
import com.jarvis.backup.GoogleAuthController
import com.jarvis.llm.GemmaClient
import com.jarvis.llm.ModelCatalog
import com.jarvis.llm.ModelKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingsUiState(
    val gemmaStatus: String = "Not installed",
    val gemmaDownloadProgress: Float? = null,
    val whisperStatus: String = "Not installed",
    val whisperDownloadProgress: Float? = null,
    val embeddingStatus: String = "Not installed",
    val embeddingDownloadProgress: Float? = null,
    val openRouterKey: String = "",
    val autoBackupEnabled: Boolean = false,
    val googleAccount: String? = null,
    val lastBackupSummary: String = "No backups yet.",
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val models: ModelCatalog,
    private val gemma: GemmaClient,
    private val auth: GoogleAuthController,
    private val backup: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { refresh() }

    private fun refresh() = viewModelScope.launch {
        _state.update {
            it.copy(
                gemmaStatus = models.statusOf(ModelKind.Gemma),
                whisperStatus = models.statusOf(ModelKind.Whisper),
                embeddingStatus = models.statusOf(ModelKind.Embedding),
                openRouterKey = models.openRouterKey(),
                autoBackupEnabled = backup.autoBackupEnabled(),
                googleAccount = auth.currentAccountEmail(),
                lastBackupSummary = backup.lastBackupSummary(),
                biometricEnabled = models.biometricEnabled(),
            )
        }
    }

    fun downloadGemma() {
        models.download(ModelKind.Gemma, viewModelScope) { p ->
            _state.update { it.copy(gemmaDownloadProgress = p) }
            if (p >= 1f) {
                runCatching { gemma.load(models.fileOf(ModelKind.Gemma)) }
                    .onFailure { Timber.e(it, "Gemma load after download failed") }
                _state.update { it.copy(gemmaDownloadProgress = null) }
                refresh()
            }
        }
    }

    fun removeGemma() { models.remove(ModelKind.Gemma); refresh() }

    fun downloadWhisper() {
        models.download(ModelKind.Whisper, viewModelScope) { p ->
            _state.update { it.copy(whisperDownloadProgress = p) }
            if (p >= 1f) {
                _state.update { it.copy(whisperDownloadProgress = null) }
                refresh()
            }
        }
    }

    fun removeWhisper() { models.remove(ModelKind.Whisper); refresh() }

    fun downloadEmbedding() {
        models.download(ModelKind.Embedding, viewModelScope) { p ->
            _state.update { it.copy(embeddingDownloadProgress = p) }
            if (p >= 1f) {
                _state.update { it.copy(embeddingDownloadProgress = null) }
                refresh()
            }
        }
    }

    fun removeEmbedding() { models.remove(ModelKind.Embedding); refresh() }

    fun setOpenRouterKey(k: String) {
        models.setOpenRouterKey(k)
        _state.update { it.copy(openRouterKey = k) }
    }

    fun setAutoBackup(v: Boolean) {
        backup.setAutoBackupEnabled(v)
        _state.update { it.copy(autoBackupEnabled = v) }
    }

    fun onSignInResult(email: String?) {
        _state.update { it.copy(googleAccount = email ?: it.googleAccount) }
    }

    fun backupNow() = viewModelScope.launch {
        backup.runBackupNow()
        refresh()
    }

    fun setBiometric(v: Boolean) {
        models.setBiometricEnabled(v)
        _state.update { it.copy(biometricEnabled = v) }
    }
}
