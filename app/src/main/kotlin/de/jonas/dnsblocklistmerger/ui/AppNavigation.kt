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
import androidx.paging.compose.collectAsLazyPagingItems
import de.jonas.dnsblocklistmerger.AppContainer
import de.jonas.dnsblocklistmerger.ui.domains.DomainsActions
import de.jonas.dnsblocklistmerger.ui.domains.DomainsScreen
import de.jonas.dnsblocklistmerger.ui.domains.DomainsViewModel
import de.jonas.dnsblocklistmerger.ui.home.HomeActions
import de.jonas.dnsblocklistmerger.ui.home.HomeScreen
import de.jonas.dnsblocklistmerger.ui.home.HomeViewModel
import de.jonas.dnsblocklistmerger.ui.settings.SettingsActions
import de.jonas.dnsblocklistmerger.ui.settings.SettingsScreen
import de.jonas.dnsblocklistmerger.ui.settings.SettingsViewModel

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
            val vm = viewModel { DomainsViewModel(container.blocklists, container.controller) }
            val query by vm.query.collectAsStateWithLifecycle()
            val total by vm.totalDomains.collectAsStateWithLifecycle()
            val exceptions by vm.exceptions.collectAsStateWithLifecycle()
            val exceptionDomains by vm.exceptionDomains.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            DomainsScreen(
                query = query,
                totalDomains = total,
                exceptions = exceptions,
                exceptionDomains = exceptionDomains,
                domains = vm.domains.collectAsLazyPagingItems(),
                busy = busy,
                snackbar = snackbar,
                actions = DomainsActions(
                    onBack = { nav.popBackStack() },
                    onQuery = vm::setQuery,
                    onExclude = vm::exclude,
                    onAllow = vm::allow,
                    onAddException = vm::addException,
                ),
            )
        }
        composable(Routes.SETTINGS) {
            val vm = viewModel { SettingsViewModel(container.settings, container.controller) }
            val settings by vm.settings.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                busy = busy,
                snackbar = snackbar,
                actions = SettingsActions(
                    onBack = { nav.popBackStack() },
                    onTheme = vm::setTheme,
                    onSaveOutput = vm::saveOutput,
                    onOmitSubdomains = vm::setOmitSubdomains,
                    onDailyRunMinutes = vm::setDailyRunMinutes,
                    onRefreshNow = vm::refreshNow,
                    onSaveSmb = vm::saveSmbTarget,
                ),
            )
        }
    }
}
