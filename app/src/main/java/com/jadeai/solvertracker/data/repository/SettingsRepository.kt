package com.jadeai.solvertracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL = stringPreferencesKey("model")

        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { it[KEY_API_KEY] }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }
    val model: Flow<String> = context.dataStore.data.map { it[KEY_MODEL] ?: DEFAULT_MODEL }

    suspend fun getApiKey(): String? = context.dataStore.data.first()[KEY_API_KEY]
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    suspend fun getBaseUrl(): String = context.dataStore.data.first()[KEY_BASE_URL]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_BASE_URL

    suspend fun getModel(): String = context.dataStore.data.first()[KEY_MODEL]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_MODEL

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit {
            val trimmed = key.trim()
            if (trimmed.isBlank()) it.remove(KEY_API_KEY) else it[KEY_API_KEY] = trimmed
        }
    }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trim().ifBlank { DEFAULT_BASE_URL } }
    }

    suspend fun saveModel(model: String) {
        context.dataStore.edit { it[KEY_MODEL] = model.trim().ifBlank { DEFAULT_MODEL } }
    }

}
