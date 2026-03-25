package com.example.myapp.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages secure, encrypted storage of the OpenAI API key using
 * [EncryptedSharedPreferences] backed by an AES-256-GCM master key stored in
 * the Android Keystore.
 *
 * ## Usage
 * ```kotlin
 * val manager = ApiKeyManager(context)
 * manager.saveApiKey("sk-proj-...")
 * val key = manager.getApiKey()
 * ```
 *
 * @param context Application or Activity context used to create the keystore
 *                master key and open the encrypted preferences file.
 */
class ApiKeyManager(context: Context) {

    private companion object {
        const val PREFS_FILE = "secure_api_prefs"
        const val KEY_OPENAI = "openai_api_key"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Saves [apiKey] to encrypted storage, replacing any previously stored key.
     *
     * @param apiKey The OpenAI API key (e.g. `"sk-proj-..."`).
     */
    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENAI, apiKey).apply()
    }

    /**
     * Returns the stored OpenAI API key, or `null` if none has been saved.
     */
    fun getApiKey(): String? = prefs.getString(KEY_OPENAI, null)

    /**
     * Removes the stored API key from encrypted storage.
     */
    fun deleteApiKey() {
        prefs.edit().remove(KEY_OPENAI).apply()
    }
}
