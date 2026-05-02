package com.jadeai.solvertracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseContent
import com.jadeai.solvertracker.domain.model.SolutionStep
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.coffeeCauseCacheDataStore by preferencesDataStore(name = "coffee_cause_cache")

data class CachedCoffeeCauseResult(
    val result: CoffeeCauseContent,
    val analyzedAt: Long
)

@Serializable
private data class CoffeeCauseCacheEntry(
    val taskId: Long,
    val signature: String,
    val result: CoffeeCauseContent,
    val analyzedAt: Long
)

@Singleton
class CoffeeCauseCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun signatureFor(steps: List<SolutionStep>): String {
        val source = steps.joinToString(separator = "\u001F") { step ->
            listOf(
                step.id.toString(),
                step.order.toString(),
                step.problem.trim(),
                step.solution.trim()
            ).joinToString(separator = "\u001E")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    suspend fun get(taskId: Long, signature: String): CachedCoffeeCauseResult? {
        val raw = context.coffeeCauseCacheDataStore.data.first()[cacheKey(taskId)] ?: return null
        val entry = try {
            json.decodeFromString(CoffeeCauseCacheEntry.serializer(), raw)
        } catch (_: Exception) {
            return null
        }
        if (entry.taskId != taskId || entry.signature != signature) return null
        return CachedCoffeeCauseResult(
            result = entry.result,
            analyzedAt = entry.analyzedAt
        )
    }

    suspend fun save(taskId: Long, signature: String, result: CoffeeCauseContent): Long {
        val analyzedAt = System.currentTimeMillis()
        val entry = CoffeeCauseCacheEntry(
            taskId = taskId,
            signature = signature,
            result = result,
            analyzedAt = analyzedAt
        )
        context.coffeeCauseCacheDataStore.edit { preferences ->
            preferences[cacheKey(taskId)] = json.encodeToString(CoffeeCauseCacheEntry.serializer(), entry)
        }
        return analyzedAt
    }

    suspend fun delete(taskId: Long) {
        context.coffeeCauseCacheDataStore.edit { preferences ->
            preferences.remove(cacheKey(taskId))
        }
    }

    private fun cacheKey(taskId: Long) = stringPreferencesKey("task_$taskId")
}
