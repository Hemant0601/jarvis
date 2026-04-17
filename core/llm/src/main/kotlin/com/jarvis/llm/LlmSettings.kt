package com.jarvis.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    private companion object {
        const val OPENROUTER = "openrouter.api-key"
        const val BIOMETRIC = "security.biometric"
    }
}
