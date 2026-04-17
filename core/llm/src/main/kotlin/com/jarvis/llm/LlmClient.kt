package com.jarvis.llm

data class GenOptions(
    val maxTokens: Int = 512,
    val temperature: Float = 0.4f,
    val topK: Int = 40,
)

interface LlmClient {
    val id: String
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String
}
