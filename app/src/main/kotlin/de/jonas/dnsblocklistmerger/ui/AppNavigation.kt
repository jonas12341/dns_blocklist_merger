package de.jonas.dnsblocklistmerger.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.jonas.dnsblocklistmerger.AppContainer
import de.jonas.dnsblocklistmerger.ui.home.HomeActions
import de.jonas.dnsblocklistmerger.ui.home.HomeScreen
import de.jonas.dnsblocklistmerger.ui.home.HomeViewModel

object Routes {
    const val HOME = "home"
    const val DOMAINS = "domains"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.controller.messages.collect { snackbar.showSnackbar(it) }
    }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm = viewModel { HomeViewModel(container.blocklists, container.settings, container.controller) }
            val state by vm.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                snackbar = snackbar,
                actions = HomeActions(
                    onRefresh = vm::refresh,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenDomains = { nav.navigate(Routes.DOMAINS) },
                    onAddList = vm::addList,
                    onClearAddError = vm::clearAddError,
                    onRemoveList = vm::removeList,
                    onChangePriority = vm::changePriority,
                ),
            )
        }
        composable(Routes.DOMAINS) {
            PlaceholderScreen("Domains & Ausnahmen") { nav.popBackStack() }
        }
        composable(Routes.SETTINGS) {
            PlaceholderScreen("Einstellungen") { nav.popBackStack() }
        }
    }
}
