package com.jarvis.llm

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class ChatMessage(val role: String, val content: String)

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.4,
    val stream: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ChatChoice(val message: ChatMessage)

@JsonClass(generateAdapter = true)
data class ChatResponse(val choices: List<ChatChoice>)

@Singleton
class OpenRouterClient @Inject constructor(
    private val http: OkHttpClient,
    private val settings: LlmSettings,
) : LlmClient {

    override val id: String = "openrouter"
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val reqAdapter = moshi.adapter(ChatRequest::class.java)
    private val resAdapter = moshi.adapter(ChatResponse::class.java)

    private val defaultModel = "google/gemini-flash-1.5"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun generate(prompt: String, options: GenOptions): String = withContext(Dispatchers.IO) {
        val key = settings.openRouterKey().takeIf { it.isNotBlank() }
            ?: error("OpenRouter API key not set")
        val body = reqAdapter.toJson(
            ChatRequest(
                model = defaultModel,
                messages = listOf(ChatMessage("user", prompt)),
                temperature = options.temperature.toDouble(),
            )
        ).toRequestBody(jsonMedia)

        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("HTTP-Referer", "https://github.com/hemant0601/jarvis")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("OpenRouter error ${resp.code}")
            val payload = resp.body?.string().orEmpty()
            val parsed = resAdapter.fromJson(payload) ?: error("Empty OpenRouter response")
            parsed.choices.firstOrNull()?.message?.content.orEmpty()
        }
    }
}
