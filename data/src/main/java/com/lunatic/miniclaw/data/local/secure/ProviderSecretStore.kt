package com.lunatic.miniclaw.data.local.secure

import android.content.Context
import android.content.SharedPreferences

class ProviderSecretStore(
    context: Context
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun saveApiKey(providerId: String, apiKey: String?) {
        val key = buildApiKeyKey(providerId)
        sharedPreferences.edit().putString(key, apiKey).apply()
    }

    fun getApiKey(providerId: String): String? {
        return sharedPreferences.getString(buildApiKeyKey(providerId), null)
    }

    private fun buildApiKeyKey(providerId: String): String = "api_key_$providerId"

    private companion object {
        private const val FILE_NAME = "provider_secret_store"
    }
}
