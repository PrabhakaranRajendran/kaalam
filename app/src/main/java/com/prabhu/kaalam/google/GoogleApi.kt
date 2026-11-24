package com.prabhu.kaalam.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.prabhu.kaalam.security.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val code: Int, message: String) : Exception(message)
class NotConnected : Exception("Google account not connected")
class NeedsConsent(val intent: Intent?) : Exception("Google needs you to allow access again. Open Kaalam › Admin › Google Drive and connect again.")

/** Google sign-in with the two permissions Kaalam needs. */
@Suppress("DEPRECATION")
object GoogleAuth {
    /** Only files Kaalam itself creates in your Drive (not your other files). */
    const val DRIVE = "https://www.googleapis.com/auth/drive.file"
    const val CALENDAR = "https://www.googleapis.com/auth/calendar"

    private fun options(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE), Scope(CALENDAR))
            .build()

    fun client(ctx: Context): GoogleSignInClient = GoogleSignIn.getClient(ctx, options())

    fun account(ctx: Context): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(ctx)

    fun isConnected(ctx: Context): Boolean =
        account(ctx)?.let { GoogleSignIn.hasPermissions(it, Scope(DRIVE), Scope(CALENDAR)) } == true

    fun email(ctx: Context): String? = account(ctx)?.email ?: Prefs.googleEmail

    /** Reads the result of the sign-in screen. Returns the email, or throws with a friendly message. */
    fun handleResult(data: Intent?): String {
        try {
            val acc = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val email = acc.email ?: ""
            Prefs.googleEmail = email
            return email
        } catch (e: ApiException) {
            throw Exception(errorMessage(e.statusCode))
        }
    }

    fun errorMessage(code: Int): String = when (code) {
        10 -> "Google sign-in isn't set up yet (error 10). In Google Cloud, the Android OAuth client must use package com.prabhu.kaalam and this app's SHA-1."
        12500 -> "Google sign-in failed (12500). Check the OAuth consent screen in Google Cloud and that your email is added as a test user."
        12501 -> "Sign-in cancelled."
        7 -> "No internet connection."
        else -> "Google sign-in failed (code $code)."
    }

    fun signOut(ctx: Context, done: () -> Unit = {}) {
        Prefs.googleEmail = null
        client(ctx).signOut().addOnCompleteListener { done() }
    }

    suspend fun token(ctx: Context): String = withContext(Dispatchers.IO) {
        val acc = account(ctx)?.account ?: throw NotConnected()
        try {
            GoogleAuthUtil.getToken(ctx, acc, "oauth2:$DRIVE $CALENDAR")
        } catch (e: UserRecoverableAuthException) {
            throw NeedsConsent(e.intent)
        }
    }

    fun invalidate(ctx: Context, token: String) {
        runCatching { GoogleAuthUtil.clearToken(ctx, token) }
    }
}

/** Tiny REST helper for the Drive and Calendar APIs. */
object Api {
    suspend fun call(
        ctx: Context,
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): String = withContext(Dispatchers.IO) {
        var token = GoogleAuth.token(ctx)
        var res = exec(method, url, token, body, contentType)
        if (res.first == 401) {
            GoogleAuth.invalidate(ctx, token)
            token = GoogleAuth.token(ctx)
            res = exec(method, url, token, body, contentType)
        }
        if (res.first !in 200..299) throw ApiError(res.first, message(res.first, res.second))
        res.second
    }

    suspend fun json(ctx: Context, method: String, url: String, body: JSONObject? = null): JSONObject {
        val text = call(ctx, method, url, body?.toString()?.toByteArray(Charsets.UTF_8), "application/json; charset=UTF-8")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun exec(method: String, url: String, token: String, body: ByteArray?, contentType: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection has no PATCH; Google APIs accept this override header.
            c.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    private fun message(code: Int, body: String): String {
        val m = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        return when {
            code == 403 && m?.contains("has not been used", true) == true ->
                "This Google API isn't enabled yet. In Google Cloud, enable both the Google Drive API and the Google Calendar API."
            m != null -> "Google error $code: $m"
            else -> "Google error $code"
        }
    }

    /** Turns any exception into a short sentence for the screen. */
    fun friendly(e: Throwable): String = when (e) {
        is NotConnected -> "Google account not connected."
        is NeedsConsent -> e.message ?: "Please connect Google again."
        is ApiError -> e.message ?: "Google error ${e.code}"
        is IOException -> "No internet connection. It will try again later."
        else -> e.message ?: e.javaClass.simpleName
    }
}
