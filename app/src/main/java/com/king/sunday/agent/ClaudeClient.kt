package com.king.sunday.agent

import com.king.sunday.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal Anthropic Messages API client (non-streaming, tool use). */
class ClaudeClient(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun send(system: String, messages: JSONArray, tools: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            if (prefs.apiKey.isBlank()) throw IOException("API key set nahi hai. Settings mein daalo.")

            // System block is cached so repeated tool-loop calls stay cheap and fast.
            val systemBlocks = JSONArray().put(
                JSONObject()
                    .put("type", "text")
                    .put("text", system)
                    .put("cache_control", JSONObject().put("type", "ephemeral"))
            )

            val body = JSONObject()
                .put("model", prefs.model)
                .put("max_tokens", 1024)
                .put("system", systemBlocks)
                .put("tools", tools)
                .put("messages", messages)

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", prefs.apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val hint = when (r.code) {
                        401 -> "API key galat lag rahi hai."
                        429 -> "Rate limit / credits ka issue."
                        else -> ""
                    }
                    throw IOException("API ${r.code}. $hint ${text.take(200)}".trim())
                }
                JSONObject(text)
            }
        }
}
