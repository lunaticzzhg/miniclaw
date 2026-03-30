package com.lunatic.miniclaw.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ProviderPreferencesStore(
    context: Context
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun observe(): Flow<ProviderPreferences> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        trySend(read())
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun read(): ProviderPreferences {
        return ProviderPreferences(
            currentProviderId = sharedPreferences.getString(KEY_CURRENT_PROVIDER_ID, null),
            modelName = sharedPreferences.getString(KEY_MODEL_NAME, null),
            baseUrl = sharedPreferences.getString(KEY_BASE_URL, null),
            isConfigured = sharedPreferences.getBoolean(KEY_IS_CONFIGURED, false),
            lastValidationStatus = sharedPreferences.getString(KEY_LAST_VALIDATION_STATUS, null),
            updatedAt = if (sharedPreferences.contains(KEY_UPDATED_AT)) {
                sharedPreferences.getLong(KEY_UPDATED_AT, 0L)
            } else {
                null
            }
        )
    }

    fun save(value: ProviderPreferences) {
        sharedPreferences.edit()
            .putString(KEY_CURRENT_PROVIDER_ID, value.currentProviderId)
            .putString(KEY_MODEL_NAME, value.modelName)
            .putString(KEY_BASE_URL, value.baseUrl)
            .putBoolean(KEY_IS_CONFIGURED, value.isConfigured)
            .putString(KEY_LAST_VALIDATION_STATUS, value.lastValidationStatus)
            .putLong(KEY_UPDATED_AT, value.updatedAt ?: System.currentTimeMillis())
            .apply()
    }

    fun updateLastValidationStatus(status: String?) {
        sharedPreferences.edit()
            .putString(KEY_LAST_VALIDATION_STATUS, status)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    private companion object {
        private const val FILE_NAME = "provider_preferences"
        private const val KEY_CURRENT_PROVIDER_ID = "current_provider_id"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_LAST_VALIDATION_STATUS = "last_validation_status"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
