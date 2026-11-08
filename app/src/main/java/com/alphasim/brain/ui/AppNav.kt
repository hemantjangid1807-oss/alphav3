package com.alphasim.brain.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alphasim.brain.AppContainer
import com.alphasim.brain.ui.screens.AlphaDetailScreen
import com.alphasim.brain.ui.screens.HomeScreen
import com.alphasim.brain.ui.screens.ImportScreen
import com.alphasim.brain.ui.screens.ListScreen
import com.alphasim.brain.ui.screens.LoginScreen
import com.alphasim.brain.ui.screens.SettingsScreen

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                container = container,
                onOpenList = { nav.navigate("list/$it") },
                onImport = { nav.navigate("import") },
                onLogin = { nav.navigate("login") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("login") {
            LoginScreen(container = container, onBack = { nav.popBackStack() })
        }
        composable("import") {
            ImportScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onImported = { id ->
                    nav.navigate("list/$id") { popUpTo("home") }
                },
            )
        }
        composable("settings") {
            SettingsScreen(container = container, onBack = { nav.popBackStack() }, onLogin = { nav.navigate("login") })
        }
        composable("list/{listId}", arguments = listOf(navArgument("listId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("listId") ?: 0L
            ListScreen(
                container = container,
                listId = id,
                onBack = { nav.popBackStack() },
                onOpenAlpha = { nav.navigate("alpha/$it") },
                onLogin = { nav.navigate("login") },
            )
        }
        composable("alpha/{alphaId}", arguments = listOf(navArgument("alphaId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("alphaId") ?: 0L
            AlphaDetailScreen(container = container, alphaRowId = id, onBack = { nav.popBackStack() }, onLogin = { nav.navigate("login") })
        }
    }
}
