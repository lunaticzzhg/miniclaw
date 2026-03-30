package com.lunatic.miniclaw.data.repository.model

import com.lunatic.miniclaw.data.remote.model.provider.ProviderHttpException
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelCallError
import java.io.IOException
import java.net.SocketTimeoutException

class ModelErrorMapper {
    fun toAvailability(throwable: Throwable): ModelAvailability {
        return when (throwable) {
            is ProviderHttpException -> when (throwable.statusCode) {
                401, 403 -> ModelAvailability.AuthFailed
                in 500..599 -> ModelAvailability.ServiceUnavailable
                else -> ModelAvailability.Unknown
            }

            is SocketTimeoutException -> ModelAvailability.NetworkUnavailable
            is IOException -> ModelAvailability.NetworkUnavailable
            else -> ModelAvailability.Unknown
        }
    }

    fun toCallError(throwable: Throwable): ModelCallError {
        return when (throwable) {
            is ProviderHttpException -> when (throwable.statusCode) {
                401, 403 -> ModelCallError.AuthFailed
                in 500..599 -> ModelCallError.ServiceUnavailable
                else -> ModelCallError.Unknown
            }

            is SocketTimeoutException -> ModelCallError.RequestTimeout
            is IOException -> ModelCallError.NetworkUnavailable
            else -> ModelCallError.Unknown
        }
    }
}
