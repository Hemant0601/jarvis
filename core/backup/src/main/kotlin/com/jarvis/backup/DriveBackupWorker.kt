package com.jarvis.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.client.http.FileContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.Collections

/**
 * Periodic worker that:
 *   1. Copies the encrypted SQLCipher DB file.
 *   2. Uploads/updates it in Drive's appDataFolder (invisible to the user,
 *      only this app can read it back).
 *   3. Records success/failure + timestamp in BackupManager.
 *
 * The DB is already encrypted at rest by SQLCipher, so Drive only sees ciphertext.
 */
@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            ?: return Result.retry()

        val credential = GoogleAccountCredential.usingOAuth2(
            applicationContext,
            Collections.singleton(DriveScopes.DRIVE_APPDATA),
        ).apply { selectedAccount = account.account }

        val drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("Jarvis").build()

        val dbFile = File(applicationContext.getDatabasePath("jarvis.db").absolutePath)
        val name = "jarvis.db"

        val existing = drive.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id,name)")
            .execute().files?.firstOrNull { it.name == name }

        val content = FileContent("application/octet-stream", dbFile)
        if (existing == null) {
            val metadata = DriveFile().apply {
                this.name = name
                parents = listOf("appDataFolder")
            }
            drive.files().create(metadata, content).setFields("id").execute()
        } else {
            drive.files().update(existing.id, DriveFile().apply { this.name = name }, content).execute()
        }
        manager.markBackupCompleted()
        Result.success()
    }.getOrElse { Result.retry() }
}
