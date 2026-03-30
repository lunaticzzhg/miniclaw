package com.lunatic.miniclaw.domain.model.model

sealed interface ModelCallError {
    data object NotConfigured : ModelCallError
    data object AuthFailed : ModelCallError
    data object NetworkUnavailable : ModelCallError
    data object RequestTimeout : ModelCallError
    data object ServiceUnavailable : ModelCallError
    data object Unknown : ModelCallError
}
