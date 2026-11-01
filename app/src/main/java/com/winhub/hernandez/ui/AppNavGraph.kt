package com.winhub.hernandez.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument


import com.winhub.hernandez.ui.screens.AdrenoToolsScreen
import com.winhub.hernandez.ui.screens.AppearanceScreen
import com.winhub.hernandez.ui.screens.ContainerDetailScreen
import com.winhub.hernandez.ui.screens.ContainersScreen
import com.winhub.hernandez.ui.screens.contents.ContentsHubScreen
import com.winhub.hernandez.ui.screens.FileManagerScreen
import com.winhub.hernandez.ui.screens.FragmentScreen
import com.winhub.hernandez.ui.screens.SavesScreen
import com.winhub.hernandez.ui.screens.InputControlsScreen
import com.winhub.hernandez.ui.screens.DeckModeScreen
import com.winhub.hernandez.ui.screens.GamesWallScreen
import com.winhub.hernandez.ui.screens.SettingsScreen
import com.winhub.hernandez.ui.screens.WrapperManagerScreen
import com.winhub.hernandez.store.SaveManagerScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startRoute: String = Screen.Games.route,
    modifier: Modifier = Modifier,
) {

    // A screen change tears down the focused node, leaving the window with no focus target at all
    // — after which no key reaches a modifier. Park focus on the nav root so the first D-pad press
    // re-enters the tree.
    val navRootFocus = LocalControllerRootFocus.current
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) { navRootFocus.requestFocus() }

    NavHost(navController = navController, startDestination = startRoute, modifier = modifier) {


        composable(Screen.Containers.route) {
            ContainersScreen(
                onOpenDeck = { navController.navigate(Screen.Deck.route) },
                onNavigateToDetail = { containerId ->
                    val route = if (containerId != null) {
                        "container_detail?id=$containerId"
                    } else {
                        "container_detail?id=-1"
                    }
                    navController.navigate(route)
                },
            )
        }

        composable(
            route = "container_detail?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: -1
            ContainerDetailScreen(
                containerId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Games.route) {
            // The legacy phone-grid library is gone. Anything that still navigates here lands on Deck.
            DeckModeScreen()
        }

        composable(Screen.Contents.route) {
            ContentsHubScreen()
        }

        composable(Screen.BigPicture.route) {
            // Big Picture couch mode now renders the games wall. The old BigPictureScreen composable is
            // retired (no longer routed); GamesWallScreen still reuses its loadCover/launchShortcut helpers.
            GamesWallScreen(navController = navController)
        }

        composable(Screen.Deck.route) {
            // DroidDeck front end — full-bleed (MainActivity drops the bar on this route).
            // Deck is the only UI: leaving it closes the app instead of dropping into a legacy screen.
            DeckModeScreen()
        }

        composable(Screen.InputControls.route) {
            InputControlsScreen()
        }

        composable(Screen.AdrenoTools.route) {
            AdrenoToolsScreen()
        }

        composable(Screen.Wrappers.route) {
            WrapperManagerScreen()
        }

        composable(Screen.FileManager.route) {
            FileManagerScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onSaved = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Games.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(Screen.Appearance.route) {
            AppearanceScreen()
        }

        composable(Screen.Saves.route) {
            SavesScreen()
        }

        composable(Screen.SaveManager.route) {
            SaveManagerScreen()
        }

        composable(Screen.SocialHub.route) {
            SocialHubScreen()
        }
    }
}
