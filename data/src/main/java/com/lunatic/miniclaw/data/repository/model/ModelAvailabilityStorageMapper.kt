package com.lunatic.miniclaw.data.repository.model

import com.lunatic.miniclaw.domain.model.model.ModelAvailability

object ModelAvailabilityStorageMapper {
    fun toStorageValue(availability: ModelAvailability): String {
        return when (availability) {
            ModelAvailability.Available -> "available"
            ModelAvailability.NotConfigured -> "not_configured"
            ModelAvailability.Validating -> "validating"
            ModelAvailability.AuthFailed -> "auth_failed"
            ModelAvailability.NetworkUnavailable -> "network_unavailable"
            ModelAvailability.ServiceUnavailable -> "service_unavailable"
            ModelAvailability.Unknown -> "unknown"
        }
    }

    fun fromStorageValue(value: String?): ModelAvailability? {
        return when (value) {
            "available" -> ModelAvailability.Available
            "not_configured" -> ModelAvailability.NotConfigured
            "validating" -> ModelAvailability.Validating
            "auth_failed" -> ModelAvailability.AuthFailed
            "network_unavailable" -> ModelAvailability.NetworkUnavailable
            "service_unavailable" -> ModelAvailability.ServiceUnavailable
            "unknown" -> ModelAvailability.Unknown
            else -> null
        }
    }
}
