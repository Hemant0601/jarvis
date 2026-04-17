package com.jarvis.data.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a random per-install passphrase for SQLCipher, stored in
 * EncryptedSharedPreferences (which is itself backed by the Android Keystore).
 * The passphrase never leaves the device and is not included in Drive backups
 * — users restore the encrypted DB + a second user-provided passphrase for
 * cross-device restores (see BackupManager).
 */
@Singleton
class DatabaseKey @Inject constructor(
    context: Context,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis-secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun obtain(): CharArray {
        val existing = prefs.getString(KEY, null)
        if (existing != null) return existing.toCharArray()
        val fresh = generate()
        prefs.edit().putString(KEY, String(fresh)).apply()
        return fresh
    }

    private fun generate(): CharArray {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.toCharArray()
    }

    private companion object { const val KEY = "db-passphrase" }
}
