// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors

package com.github.muelli.kabelwacht.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.muelli.kabelwacht.ui.conditions.RunConditionsScreen
import com.github.muelli.kabelwacht.ui.edit.EditTunnelScreen
import com.github.muelli.kabelwacht.ui.list.TunnelListScreen

object Routes {
    const val LIST = "list"
    const val CREATE = "edit"
    const val EDIT = "edit/{name}"
    fun edit(name: String) = "edit/$name"
    const val CONDITIONS = "conditions/{name}"
    fun conditions(name: String) = "conditions/$name"
}

@Composable
fun KabelWachtNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            TunnelListScreen(
                onCreate = { navController.navigate(Routes.CREATE) },
                onEdit = { name -> navController.navigate(Routes.edit(name)) },
                onImport = { navController.navigate(Routes.CREATE) },
                onConditions = { name -> navController.navigate(Routes.conditions(name)) },
            )
        }
        composable(Routes.CREATE) {
            EditTunnelScreen(
                editName = null,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStackEntry ->
            EditTunnelScreen(
                editName = backStackEntry.arguments?.getString("name"),
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onConditions = { tunnelName -> navController.navigate(Routes.conditions(tunnelName)) },
            )
        }
        composable(
            route = Routes.CONDITIONS,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            RunConditionsScreen(
                tunnelName = name,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
