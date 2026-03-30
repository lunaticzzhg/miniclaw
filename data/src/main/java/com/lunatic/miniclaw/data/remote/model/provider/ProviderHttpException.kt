package com.lunatic.miniclaw.data.remote.model.provider

class ProviderHttpException(
    val statusCode: Int,
    override val message: String? = null
) : RuntimeException(message)
