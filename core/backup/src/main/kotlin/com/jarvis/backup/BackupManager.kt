package com.jarvis.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "jarvis-backup",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun autoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO, true)

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        if (enabled) schedulePeriodic() else cancelPeriodic()
    }

    fun lastBackupSummary(): String {
        val at = prefs.getLong(KEY_LAST_AT, 0L)
        return if (at == 0L) "No backups yet." else "Last backup: ${java.util.Date(at)}"
    }

    fun markBackupCompleted() {
        prefs.edit().putLong(KEY_LAST_AT, System.currentTimeMillis()).apply()
    }

    suspend fun runBackupNow() {
        // The heavy lifting lives in DriveBackupWorker; this just triggers it
        // immediately by enqueuing a one-off work request.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "drive-backup-oneoff",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveBackupWorker>(Duration.ofHours(6))
                .setInitialDelay(Duration.ZERO)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        )
    }

    private fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "drive-backup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveBackupWorker>(Duration.ofHours(12))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .build()
                )
                .build()
        )
    }

    private fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork("drive-backup")
    }

    private companion object {
        const val KEY_AUTO = "backup.auto"
        const val KEY_LAST_AT = "backup.last-at"
    }
}
