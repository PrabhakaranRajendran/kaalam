package com.prabhu.kaalam.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * App password + encryption.
 * - The PIN is never stored, only a salted PBKDF2 hash.
 * - The Drive file is AES-256-GCM encrypted with a key made from the PIN, so it can be
 *   opened on a new phone with the same PIN, but is unreadable inside Google Drive.
 * - On this phone that key is kept wrapped by the Android Keystore, so backups can run
 *   in the background after a fingerprint unlock.
 */
object Vault {
    private const val ITER = 40_000
    private const val ALIAS = "kaalam_wrap"
    private val rnd = SecureRandom()

    fun hasPin(): Boolean = Prefs.pinHash != null && Prefs.wrappedKey != null

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
    private fun random(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITER, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Sets (or changes) the app password. Slow (~0.5 s): call off the main thread. */
    fun setPin(pin: String) {
        val pinSalt = random(16)
        val dataSalt = random(16)
        val key = pbkdf2(pin, dataSalt)
        Prefs.pinSalt = b64(pinSalt)
        Prefs.pinHash = b64(pbkdf2(pin, pinSalt))
        Prefs.dataSalt = b64(dataSalt)
        Prefs.wrappedKey = wrap(key)
        Prefs.failedAttempts = 0
        Prefs.lockoutUntil = 0
        Prefs.pinChangedAt = System.currentTimeMillis()
    }

    /** Slow: call off the main thread. */
    fun checkPin(pin: String): Boolean {
        val salt = Prefs.pinSalt ?: return false
        val hash = Prefs.pinHash ?: return false
        return MessageDigest.isEqual(pbkdf2(pin, unb64(salt)), unb64(hash))
    }

    // ---------- Drive file ----------

    fun encryptForDrive(json: String): String {
        val salt = unb64(Prefs.dataSalt ?: error("No password set"))
        val key = unwrap(Prefs.wrappedKey ?: error("No password set"))
        val iv = random(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(json.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "kaalam").put("v", 1)
            .put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(ct))
            .toString()
    }

    /** Decrypts a Drive file with the password that was used when it was saved. Throws if wrong. */
    fun decryptFromDrive(text: String, pin: String): String {
        val o = JSONObject(text)
        require(o.optString("app") == "kaalam") { "This is not a Kaalam backup file" }
        val key = pbkdf2(pin, unb64(o.getString("salt")))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(o.getString("iv"))))
        return String(c.doFinal(unb64(o.getString("data"))), Charsets.UTF_8)
    }

    // ---------- Android Keystore wrapping ----------

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun wrap(raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val ct = c.doFinal(raw)
        return b64(c.iv) + ":" + b64(ct)
    }

    private fun unwrap(s: String): ByteArray {
        val (iv, ct) = s.split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, unb64(iv)))
        return c.doFinal(unb64(ct))
    }
}

/** Fingerprint (or face, if the phone offers it as a biometric) unlock. */
object Bio {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(activity: android.content.Context): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFallback()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Touch the fingerprint sensor")
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(AUTH)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onFallback() }
    }
}
