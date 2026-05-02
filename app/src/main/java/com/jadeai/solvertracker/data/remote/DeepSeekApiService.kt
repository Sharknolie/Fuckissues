package com.jadeai.solvertracker.data.remote

import com.jadeai.solvertracker.data.remote.dto.AnalysisRequest
import com.jadeai.solvertracker.data.remote.dto.AnalysisResponse
import com.jadeai.solvertracker.data.remote.dto.ChatRequest
import com.jadeai.solvertracker.data.remote.dto.ModelsResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface DeepSeekApiService {
    @POST
    suspend fun analyze(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: AnalysisRequest
    ): AnalysisResponse

    @POST
    suspend fun chat(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): AnalysisResponse

    @Streaming
    @POST
    suspend fun chatStream(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ResponseBody

    @GET
    suspend fun models(
        @Url url: String,
        @Header("Authorization") auth: String
    ): ModelsResponse
}
