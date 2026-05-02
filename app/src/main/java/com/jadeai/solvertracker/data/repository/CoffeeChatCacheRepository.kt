package com.jadeai.solvertracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.coffeeChatCacheDataStore by preferencesDataStore(name = "coffee_chat_cache")

data class CachedCoffeeChat(
    val messages: List<CachedCoffeeChatMessage>,
    val selectedStepIndex: Int?,
    val updatedAt: Long
)

data class CachedCoffeeChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class CoffeeChatCacheEntry(
    val taskId: Long,
    val messages: List<CoffeeChatCacheMessageEntry>,
    val selectedStepIndex: Int? = null,
    val updatedAt: Long
)

@Serializable
private data class CoffeeChatCacheMessageEntry(
    val role: String,
    val content: String
)

@Singleton
class CoffeeChatCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    suspend fun get(taskId: Long): CachedCoffeeChat? {
        val raw = context.coffeeChatCacheDataStore.data.first()[cacheKey(taskId)] ?: return null
        val entry = try {
            json.decodeFromString(CoffeeChatCacheEntry.serializer(), raw)
        } catch (_: Exception) {
            return null
        }
        if (entry.taskId != taskId) return null
        return CachedCoffeeChat(
            messages = entry.messages.map { message ->
                CachedCoffeeChatMessage(role = message.role, content = message.content)
            },
            selectedStepIndex = entry.selectedStepIndex,
            updatedAt = entry.updatedAt
        )
    }

    suspend fun save(
        taskId: Long,
        messages: List<CachedCoffeeChatMessage>,
        selectedStepIndex: Int?
    ): Long {
        val updatedAt = System.currentTimeMillis()
        val entry = CoffeeChatCacheEntry(
            taskId = taskId,
            messages = messages
                .filter { it.content.isNotBlank() }
                .takeLast(MAX_MESSAGES)
                .map { message ->
                    CoffeeChatCacheMessageEntry(role = message.role, content = message.content)
                },
            selectedStepIndex = selectedStepIndex,
            updatedAt = updatedAt
        )
        context.coffeeChatCacheDataStore.edit { preferences ->
            preferences[cacheKey(taskId)] = json.encodeToString(CoffeeChatCacheEntry.serializer(), entry)
        }
        return updatedAt
    }

    suspend fun delete(taskId: Long) {
        context.coffeeChatCacheDataStore.edit { preferences ->
            preferences.remove(cacheKey(taskId))
        }
    }

    private fun cacheKey(taskId: Long) = stringPreferencesKey("task_$taskId")

    private companion object {
        const val MAX_MESSAGES = 60
    }
}
