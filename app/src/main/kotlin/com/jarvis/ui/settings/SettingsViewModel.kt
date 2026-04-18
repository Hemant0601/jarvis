package com.jarvis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.backup.BackupManager
import com.jarvis.backup.GoogleAuthController
import com.jarvis.graph.GraphRepository
import com.jarvis.llm.GemmaClient
import com.jarvis.llm.LlmSettings
import com.jarvis.llm.ModelCatalog
import com.jarvis.llm.ModelKind
import com.jarvis.llm.ThemeMode
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
    val gemmaLoadedLabel: String = "Not loaded",
    val gemmaVerifying: Boolean = false,
    val gemmaVerifyResult: String? = null,
    val embeddingStatus: String = "Not installed",
    val embeddingDownloadProgress: Float? = null,
    val openRouterKey: String = "",
    val autoBackupEnabled: Boolean = false,
    val googleAccount: String? = null,
    val lastBackupSummary: String = "No backups yet.",
    val biometricEnabled: Boolean = false,
    val themeMode: com.jarvis.llm.ThemeMode = com.jarvis.llm.ThemeMode.DARK,
    val lastErrorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val models: ModelCatalog,
    private val gemma: GemmaClient,
    private val auth: GoogleAuthController,
    private val backup: BackupManager,
    private val graph: GraphRepository,
    private val llmSettings: LlmSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { refresh() }

    private fun refresh() = viewModelScope.launch {
        _state.update {
            it.copy(
                gemmaStatus = models.statusOf(ModelKind.Gemma),
                gemmaLoadedLabel = when {
                    gemma.isLoaded() -> "Loaded · ready to answer"
                    gemma.isInstalled() -> "Installed · will load on first use"
                    else -> "Not installed"
                },
                embeddingStatus = models.statusOf(ModelKind.Embedding),
                openRouterKey = models.openRouterKey(),
                autoBackupEnabled = backup.autoBackupEnabled(),
                googleAccount = auth.currentAccountEmail(),
                lastBackupSummary = backup.lastBackupSummary(),
                biometricEnabled = models.biometricEnabled(),
                themeMode = llmSettings.themeMode(),
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        llmSettings.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun verifyGemma() {
        _state.update { it.copy(gemmaVerifying = true, gemmaVerifyResult = null) }
        viewModelScope.launch {
            val result = runCatching { gemma.verify() }
                .getOrElse { err -> "Failed: ${err.message ?: err.javaClass.simpleName}" }
            _state.update {
                it.copy(
                    gemmaVerifying = false,
                    gemmaVerifyResult = result,
                    gemmaLoadedLabel = when {
                        gemma.isLoaded() -> "Loaded · ready to answer"
                        gemma.isInstalled() -> "Installed · load failed"
                        else -> "Not installed"
                    },
                )
            }
        }
    }

    fun dismissError() = _state.update { it.copy(lastErrorMessage = null) }

    fun downloadGemma() {
        _state.update { it.copy(gemmaDownloadProgress = 0f, lastErrorMessage = null) }
        models.download(
            kind = ModelKind.Gemma,
            scope = viewModelScope,
            onProgress = { p ->
                _state.update { it.copy(gemmaDownloadProgress = p.takeUnless { it >= 1f }) }
                if (p >= 1f) {
                    runCatching { gemma.load(models.fileOf(ModelKind.Gemma)) }
                        .onFailure { Timber.e(it, "Gemma load after download failed") }
                    refresh()
                }
            },
            onError = { msg ->
                _state.update { it.copy(gemmaDownloadProgress = null, lastErrorMessage = "Gemma download failed: $msg") }
            },
        )
    }

    fun removeGemma() { models.remove(ModelKind.Gemma); refresh() }

    fun downloadEmbedding() {
        _state.update { it.copy(embeddingDownloadProgress = 0f, lastErrorMessage = null) }
        models.download(
            kind = ModelKind.Embedding,
            scope = viewModelScope,
            onProgress = { p ->
                _state.update { it.copy(embeddingDownloadProgress = p.takeUnless { it >= 1f }) }
                if (p >= 1f) refresh()
            },
            onError = { msg ->
                _state.update { it.copy(embeddingDownloadProgress = null, lastErrorMessage = "Embedding download failed: $msg") }
            },
        )
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

    fun nukeMemories() = viewModelScope.launch {
        runCatching { graph.clearAll() }
            .onSuccess { _state.update { it.copy(lastErrorMessage = "All memories cleared.") } }
            .onFailure { err ->
                _state.update { it.copy(lastErrorMessage = "Could not clear memories: ${err.message}") }
            }
    }
}
