package com.jarvis.backup

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes

class GoogleSignInContract : ActivityResultContract<Unit, GoogleSignInAccount?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        GoogleSignIn.getClient(context, buildOptions()).signInIntent

    override fun parseResult(resultCode: Int, intent: Intent?): GoogleSignInAccount? {
        return try {
            GoogleSignIn.getSignedInAccountFromIntent(intent).getResult(ApiException::class.java)
        } catch (_: ApiException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_APPDATA),
                Scope(GmailScopes.GMAIL_SEND),
            )
            .build()
}
