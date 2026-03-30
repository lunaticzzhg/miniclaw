package com.lunatic.miniclaw.data.remote.model.minimax.mapper

import com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxErrorDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MiniMaxErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseError(raw: String?): MiniMaxErrorDto {
        if (raw.isNullOrBlank()) return MiniMaxErrorDto()
        return runCatching {
            val parsed = json.parseToJsonElement(raw)
            extractError(parsed)
        }.getOrDefault(MiniMaxErrorDto(message = raw.take(MAX_ERROR_MESSAGE_LEN)))
    }

    private fun extractError(element: JsonElement): MiniMaxErrorDto {
        val obj = element as? JsonObject ?: return MiniMaxErrorDto(message = element.toString())
        val errorObj = obj["error"]?.jsonObject ?: obj
        return MiniMaxErrorDto(
            code = errorObj["code"]?.jsonPrimitive?.contentOrNull(),
            message = errorObj["message"]?.jsonPrimitive?.contentOrNull()
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? {
        return if (isString) content else content
    }

    private companion object {
        private const val MAX_ERROR_MESSAGE_LEN = 300
    }
}
