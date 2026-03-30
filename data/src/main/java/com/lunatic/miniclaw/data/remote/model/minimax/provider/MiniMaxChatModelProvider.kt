package com.lunatic.miniclaw.data.remote.model.minimax.provider

import com.lunatic.miniclaw.data.remote.model.minimax.api.MiniMaxApiServiceFactory
import com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxMessageDto
import com.lunatic.miniclaw.data.remote.model.minimax.mapper.MiniMaxErrorMapper
import com.lunatic.miniclaw.data.remote.model.minimax.mapper.MiniMaxRequestBodyMapper
import com.lunatic.miniclaw.data.remote.model.minimax.mapper.MiniMaxRequestMapper
import com.lunatic.miniclaw.data.remote.model.provider.ProviderHttpException
import com.lunatic.miniclaw.domain.model.model.ChatModelRequest
import com.lunatic.miniclaw.domain.model.model.ChatStreamEvent
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelCallError
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import com.lunatic.miniclaw.domain.model.provider.ChatModelProvider
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MiniMaxChatModelProvider(
    private val apiServiceFactory: MiniMaxApiServiceFactory
) : ChatModelProvider {
    override val providerId: ModelProviderId = ModelProviderId.MINIMAX

    private val requestMapper = MiniMaxRequestMapper()
    private val requestBodyMapper = MiniMaxRequestBodyMapper()
    private val errorMapper = MiniMaxErrorMapper()

    override suspend fun validate(config: ModelProviderConfig): ModelAvailability {
        if (!isConfigReady(config)) return ModelAvailability.NotConfigured
        val request = buildValidationRequest(config)
        return runCatching { execute(config, request, stream = false) }
            .map { ModelAvailability.Available }
            .getOrElse { throwable -> throwable.toAvailability() }
    }

    override fun streamChat(config: ModelProviderConfig, request: ChatModelRequest): Flow<ChatStreamEvent> = flow {
        if (!isConfigReady(config)) {
            emit(ChatStreamEvent.Failed(ModelCallError.NotConfigured))
            return@flow
        }

        emit(ChatStreamEvent.Started)

        val rawBody = runCatching {
            execute(config, requestMapper.toRequestDto(config, request), stream = true)
        }.getOrElse { throwable ->
            emit(ChatStreamEvent.Failed(throwable.toCallError()))
            return@flow
        }

        if (rawBody.isNotBlank()) {
            emit(ChatStreamEvent.Delta(rawBody))
        }
        emit(ChatStreamEvent.Completed)
    }

    private suspend fun execute(
        config: ModelProviderConfig,
        request: com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxChatRequestDto,
        stream: Boolean
    ): String {
        val api = apiServiceFactory.get(config.baseUrl ?: DEFAULT_BASE_URL)
        val requestBody = requestBodyMapper.toRequestBody(request.copy(stream = stream))
        val response = api.chatCompletions(
            url = buildEndpointUrl(config),
            authorization = buildAuthHeader(config.apiKey.orEmpty()),
            body = requestBody
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            val error = errorMapper.parseError(errorBody)
            throw ProviderHttpException(
                statusCode = response.code(),
                message = error.message ?: "MiniMax call failed: ${response.code()}"
            )
        }

        return response.body()?.string().orEmpty()
    }

    private fun buildValidationRequest(config: ModelProviderConfig):
        com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxChatRequestDto {
        return com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxChatRequestDto(
            model = config.modelName,
            messages = listOf(MiniMaxMessageDto(role = "user", content = "ping")),
            stream = false
        )
    }

    private fun buildEndpointUrl(config: ModelProviderConfig): String {
        val base = apiServiceFactory.normalizeBaseUrl(config.baseUrl ?: DEFAULT_BASE_URL)
        return base + ENDPOINT_PATH
    }

    private fun buildAuthHeader(apiKey: String): String = "Bearer $apiKey"

    private fun isConfigReady(config: ModelProviderConfig): Boolean {
        return config.isConfigured && config.modelName.isNotBlank() && !config.apiKey.isNullOrBlank()
    }

    private fun Throwable.toAvailability(): ModelAvailability {
        return when (this) {
            is ProviderHttpException -> when (statusCode) {
                401, 403 -> ModelAvailability.AuthFailed
                in 500..599 -> ModelAvailability.ServiceUnavailable
                else -> ModelAvailability.Unknown
            }

            is SocketTimeoutException -> ModelAvailability.NetworkUnavailable
            is IOException -> ModelAvailability.NetworkUnavailable
            else -> ModelAvailability.Unknown
        }
    }

    private fun Throwable.toCallError(): ModelCallError {
        return when (this) {
            is ProviderHttpException -> when (statusCode) {
                401, 403 -> ModelCallError.AuthFailed
                in 500..599 -> ModelCallError.ServiceUnavailable
                else -> ModelCallError.Unknown
            }

            is SocketTimeoutException -> ModelCallError.RequestTimeout
            is IOException -> ModelCallError.NetworkUnavailable
            else -> ModelCallError.Unknown
        }
    }

    private companion object {
        private const val DEFAULT_BASE_URL = "https://api.minimax.io/"
        private const val ENDPOINT_PATH = "v1/text/chatcompletion_v2"
    }
}
