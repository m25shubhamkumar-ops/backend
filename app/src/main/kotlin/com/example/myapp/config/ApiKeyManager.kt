package com.example.myapp.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages secure storage of the OpenAI API key using [EncryptedSharedPreferences].
 *
 * All data is encrypted at rest using AES-256-GCM (values) and AES-256-SIV (keys)
 * via the Android Security library.
 *
 * ## Typical usage
 * ```kotlin
 * val apiKeyManager = ApiKeyManager(context)
 *
 * // Save once (e.g., from a settings screen)
 * apiKeyManager.saveApiKey("sk-proj-...")
 *
 * // Retrieve for use
 * val apiKey = apiKeyManager.getApiKey() ?: error("No API key stored")
 *
 * // Delete when no longer needed
 * apiKeyManager.deleteApiKey()
 * ```
 *
 * @param context Application or Activity context.
 */
class ApiKeyManager(context: Context) {

    companion object {
        private const val TAG = "ApiKeyManager"
        private const val PREFS_FILE = "secret_shared_prefs"
        private const val KEY_OPENAI = "openai_api_key"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Encrypts and persists the given API [key] to secure storage.
     *
     * @param key The OpenAI API key to store.
     */
    fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_OPENAI, key).apply()
        Log.d(TAG, "API key saved to encrypted storage")
    }

    /**
     * Retrieves the stored API key, or `null` if none has been saved yet.
     *
     * @return The stored API key, or `null`.
     */
    fun getApiKey(): String? = encryptedPrefs.getString(KEY_OPENAI, null)

    /**
     * Removes the stored API key from encrypted storage.
     */
    fun deleteApiKey() {
        encryptedPrefs.edit().remove(KEY_OPENAI).apply()
        Log.d(TAG, "API key deleted from encrypted storage")
    }
}
