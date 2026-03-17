package dev.gotlou.bettertrophies

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class NpssoTokenStore(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun readToken(): String? = sharedPreferences.getString(KEY_NPSSO, null)?.takeIf { it.isNotBlank() }

    fun writeToken(token: String) {
        sharedPreferences.edit().putString(KEY_NPSSO, token).apply()
    }

    fun clearToken() {
        sharedPreferences.edit().remove(KEY_NPSSO).apply()
    }

    private companion object {
        const val PREFS_NAME = "secure_npsso"
        const val KEY_NPSSO = "npsso_token"
    }
}
