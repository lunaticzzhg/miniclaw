package com.lunatic.miniclaw.domain.model.model

sealed interface ModelAvailability {
    data object Available : ModelAvailability
    data object NotConfigured : ModelAvailability
    data object Validating : ModelAvailability
    data object AuthFailed : ModelAvailability
    data object NetworkUnavailable : ModelAvailability
    data object ServiceUnavailable : ModelAvailability
    data object Unknown : ModelAvailability
}
