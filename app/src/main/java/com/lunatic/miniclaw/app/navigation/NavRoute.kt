package com.lunatic.miniclaw.app.navigation

object NavRoute {
    const val SessionList = "session_list"
    const val Chat = "chat/{sessionId}"

    fun chat(sessionId: String): String = "chat/$sessionId"
}
