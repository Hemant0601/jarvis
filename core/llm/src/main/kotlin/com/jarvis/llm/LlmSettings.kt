package com.jarvis.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ThemeMode { AUTO, LIGHT, DARK }

@Singleton
class LlmSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "jarvis-llm",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun openRouterKey(): String = prefs.getString(OPENROUTER, "").orEmpty()
    fun setOpenRouterKey(value: String) { prefs.edit().putString(OPENROUTER, value).apply() }

    fun biometricEnabled(): Boolean = prefs.getBoolean(BIOMETRIC, false)
    fun setBiometricEnabled(value: Boolean) { prefs.edit().putBoolean(BIOMETRIC, value).apply() }

    fun themeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(THEME, ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
    }.getOrDefault(ThemeMode.DARK)

    fun setThemeMode(mode: ThemeMode) { prefs.edit().putString(THEME, mode.name).apply() }

    /** Hot flow that emits the current ThemeMode on subscribe and on every change. */
    fun themeModeFlow(): Flow<ThemeMode> = callbackFlow {
        trySend(themeMode())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME) trySend(themeMode())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val OPENROUTER = "openrouter.api-key"
        const val BIOMETRIC = "security.biometric"
        const val THEME = "ui.theme-mode"
    }
}
