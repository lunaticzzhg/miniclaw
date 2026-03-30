package com.lunatic.miniclaw.app.navigation

object NavRoute {
    const val SessionList = "session_list"
    const val Chat = "chat/{sessionId}"
    const val ModelConfig = "model_config?returnToSessionId={returnToSessionId}"

    fun chat(sessionId: String): String = "chat/$sessionId"

    fun modelConfig(returnToSessionId: String? = null): String {
        return if (returnToSessionId.isNullOrBlank()) {
            "model_config"
        } else {
            "model_config?returnToSessionId=$returnToSessionId"
        }
    }
}
