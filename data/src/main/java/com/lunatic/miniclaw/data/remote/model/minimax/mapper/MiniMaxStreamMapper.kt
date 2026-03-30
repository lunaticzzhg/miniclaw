package com.lunatic.miniclaw.data.remote.model.minimax.mapper

import com.lunatic.miniclaw.domain.model.model.ChatStreamEvent
import com.lunatic.miniclaw.domain.model.model.ModelCallError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MiniMaxStreamMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toEvents(rawBody: String): List<ChatStreamEvent> {
        if (rawBody.isBlank()) return listOf(ChatStreamEvent.Completed)

        val events = mutableListOf<ChatStreamEvent>()
        val ssePayloads = rawBody.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(SSE_PREFIX) }
            .map { it.removePrefix(SSE_PREFIX).trim() }
            .toList()

        if (ssePayloads.isNotEmpty()) {
            ssePayloads.forEach { payload ->
                parsePayload(payload, events)
            }
            return finalizeEvents(events)
        }

        parsePayload(rawBody.trim(), events)
        return finalizeEvents(events)
    }

    private fun parsePayload(payload: String, events: MutableList<ChatStreamEvent>) {
        if (payload.isBlank()) return
        if (payload == DONE_MARKER) {
            events += ChatStreamEvent.Completed
            return
        }

        val jsonElement = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        if (jsonElement == null) {
            events += ChatStreamEvent.Delta(payload)
            return
        }

        val jsonObject = jsonElement as? JsonObject
        if (jsonObject == null) {
            events += ChatStreamEvent.Delta(payload)
            return
        }

        val error = extractError(jsonObject)
        if (error != null) {
            events += ChatStreamEvent.Failed(error)
            return
        }

        val delta = extractDelta(jsonObject)
        if (!delta.isNullOrBlank()) {
            events += ChatStreamEvent.Delta(delta)
        }
    }

    private fun finalizeEvents(events: List<ChatStreamEvent>): List<ChatStreamEvent> {
        if (events.none { it is ChatStreamEvent.Completed || it is ChatStreamEvent.Failed }) {
            return events + ChatStreamEvent.Completed
        }
        return events
    }

    private fun extractDelta(jsonObject: JsonObject): String? {
        jsonObject["delta"]?.jsonPrimitive?.contentOrNull?.let { return it }
        jsonObject["reply"]?.jsonPrimitive?.contentOrNull?.let { return it }
        jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let { return it }

        val choices = jsonObject["choices"] as? JsonArray ?: return null
        val firstChoice = choices.firstOrNull()?.jsonObject ?: return null
        firstChoice["delta"]?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { return it }
        return firstChoice["message"]?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
    }

    private fun extractError(jsonObject: JsonObject): ModelCallError? {
        val errorObject = jsonObject["error"]?.let { asJsonObject(it) }
        val code = errorObject?.get("code")?.jsonPrimitive?.contentOrNull
            ?: jsonObject["code"]?.jsonPrimitive?.contentOrNull
        val message = errorObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: jsonObject["message"]?.jsonPrimitive?.contentOrNull
        if (code == null && message == null) return null

        val merged = "${code.orEmpty()} ${message.orEmpty()}".lowercase()
        return when {
            merged.contains("401") || merged.contains("403") || merged.contains("auth") ||
                merged.contains("forbidden") || merged.contains("unauthorized") -> {
                ModelCallError.AuthFailed
            }

            merged.contains("timeout") -> ModelCallError.RequestTimeout
            merged.contains("network") || merged.contains("connection") -> ModelCallError.NetworkUnavailable
            merged.contains("500") || merged.contains("502") || merged.contains("503") ||
                merged.contains("504") || merged.contains("service") || merged.contains("overload") -> {
                ModelCallError.ServiceUnavailable
            }

            else -> ModelCallError.Unknown
        }
    }

    private fun asJsonObject(element: JsonElement): JsonObject? = runCatching { element.jsonObject }.getOrNull()

    private companion object {
        private const val SSE_PREFIX = "data:"
        private const val DONE_MARKER = "[DONE]"
    }
}
