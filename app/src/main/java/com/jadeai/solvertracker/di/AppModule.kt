package com.jadeai.solvertracker.di

import android.content.Context
import androidx.room.Room
import com.jadeai.solvertracker.data.local.AppDatabase
import com.jadeai.solvertracker.data.local.dao.AnalysisDao
import com.jadeai.solvertracker.data.local.dao.SolutionStepDao
import com.jadeai.solvertracker.data.local.dao.TaskDao
import com.jadeai.solvertracker.data.remote.DeepSeekApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "solver-tracker.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideSolutionStepDao(db: AppDatabase): SolutionStepDao = db.solutionStepDao()
    @Provides fun provideAnalysisDao(db: AppDatabase): AnalysisDao = db.analysisDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Provides
    @Singleton
    fun provideDeepSeekApiService(
        json: Json,
        okHttpClient: OkHttpClient
    ): DeepSeekApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(DeepSeekApiService::class.java)
    }
}
