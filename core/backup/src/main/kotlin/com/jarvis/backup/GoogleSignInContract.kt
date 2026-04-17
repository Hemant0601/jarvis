package com.jarvis.backup

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Activity-result contract that launches the Google sign-in intent and returns
 * only the signed-in account's email. Callers don't need play-services-auth
 * on their classpath — the String return keeps the boundary narrow.
 */
class GoogleSignInContract : ActivityResultContract<Unit, String?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        GoogleSignIn.getClient(context, buildOptions()).signInIntent

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        return try {
            GoogleSignIn.getSignedInAccountFromIntent(intent)
                .getResult(ApiException::class.java)
                ?.email
        } catch (_: ApiException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
}
