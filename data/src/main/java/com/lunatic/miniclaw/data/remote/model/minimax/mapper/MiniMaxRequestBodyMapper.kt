package com.lunatic.miniclaw.data.remote.model.minimax.mapper

import com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxChatRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class MiniMaxRequestBodyMapper {
    private val json = Json { prettyPrint = false }

    fun toRequestBody(dto: MiniMaxChatRequestDto): RequestBody {
        val payload = JsonObject(
            mapOf(
                "model" to JsonPrimitive(dto.model),
                "stream" to JsonPrimitive(dto.stream),
                "messages" to JsonArray(
                    dto.messages.map { message ->
                        JsonObject(
                            mapOf(
                                "role" to JsonPrimitive(message.role),
                                "content" to JsonPrimitive(message.content)
                            )
                        )
                    }
                )
            )
        )
        val body = json.encodeToString(JsonObject.serializer(), payload)
        return body.toRequestBody(CONTENT_TYPE_JSON)
    }

    private companion object {
        val CONTENT_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
