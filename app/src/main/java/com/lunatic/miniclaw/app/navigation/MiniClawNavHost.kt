package com.lunatic.miniclaw.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lunatic.miniclaw.feature.chat.ui.ChatRoute
import com.lunatic.miniclaw.feature.sessionlist.ui.SessionListRoute

@Composable
fun MiniClawNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoute.SessionList
    ) {
        composable(route = NavRoute.SessionList) {
            SessionListRoute(
                onOpenChat = { sessionId ->
                    navController.navigate(NavRoute.chat(sessionId))
                }
            )
        }

        composable(
            route = NavRoute.Chat,
            arguments = listOf(navArgument(SESSION_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(SESSION_ID_ARG).orEmpty()
            ChatRoute(
                sessionId = sessionId,
                onBackClicked = { navController.popBackStack() }
            )
        }
    }
}

private const val SESSION_ID_ARG = "sessionId"
