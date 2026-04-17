package com.jarvis.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade around Google Sign-In. The actual sign-in UI intent is triggered
 * from an Activity-scoped caller (see UI layer). Stores account metadata only;
 * tokens are managed by Play Services.
 */
@Singleton
class GoogleAuthController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()

    fun currentAccountEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    suspend fun signIn() {
        // Actual sign-in flow must be launched from an Activity via the
        // GoogleSignInClient.signInIntent + ActivityResultContracts. The UI
        // layer handles that and stores nothing here directly; this method
        // is the coroutine entry-point other layers call.
    }

    fun signOut() {
        GoogleSignIn.getClient(context, signInOptions).signOut()
    }
}
