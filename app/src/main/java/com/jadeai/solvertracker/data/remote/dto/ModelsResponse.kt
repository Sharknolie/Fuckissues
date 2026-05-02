package com.jadeai.solvertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponse(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String = ""
)
