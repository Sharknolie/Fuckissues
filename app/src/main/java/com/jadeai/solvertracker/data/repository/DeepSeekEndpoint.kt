package com.jadeai.solvertracker.data.repository

fun buildDeepSeekChatUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().ifBlank { SettingsRepository.DEFAULT_BASE_URL }
    val withoutTrailingSlash = trimmed.trimEnd('/')
    return if (withoutTrailingSlash.endsWith("/chat/completions")) {
        withoutTrailingSlash
    } else {
        "$withoutTrailingSlash/chat/completions"
    }
}

fun buildDeepSeekModelsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().ifBlank { SettingsRepository.DEFAULT_BASE_URL }
    val withoutTrailingSlash = trimmed.trimEnd('/')
    return when {
        withoutTrailingSlash.endsWith("/models") -> withoutTrailingSlash
        withoutTrailingSlash.endsWith("/chat/completions") -> withoutTrailingSlash.removeSuffix("/chat/completions") + "/models"
        else -> "$withoutTrailingSlash/models"
    }
}
