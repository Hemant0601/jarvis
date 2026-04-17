package com.jarvis.llm

import kotlinx.coroutines.flow.Flow

data class GenOptions(
    val maxTokens: Int = 512,
    val temperature: Float = 0.4f,
    val topK: Int = 40,
)

interface LlmClient {
    val id: String
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String
    fun stream(prompt: String, options: GenOptions = GenOptions()): Flow<String>
}
