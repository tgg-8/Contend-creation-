package app.schnarchwaechter.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.schnarchwaechter.service.NightService
import app.schnarchwaechter.service.Notifications
import app.schnarchwaechter.ui.screens.HistoryScreen
import app.schnarchwaechter.ui.screens.NightDetailScreen
import app.schnarchwaechter.ui.screens.NightModeScreen
import app.schnarchwaechter.ui.screens.SettingsScreen
import app.schnarchwaechter.ui.screens.StartScreen
import app.schnarchwaechter.ui.theme.AppColors
import app.schnarchwaechter.ui.theme.SchnarchTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Night to open, e.g. from the morning notification. */
    private val openNight = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            SchnarchTheme {
                AppRoot(openNight)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifications.EXTRA_NIGHT_ID, -1L) ?: -1L
        if (id > 0) openNight.value = id
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    NIGHT("Nacht", Icons.Filled.Home),
    HISTORY("Verlauf", Icons.Filled.DateRange),
    SETTINGS("Einstellungen", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(openNight: MutableStateFlow<Long?>, vm: AppViewModel = viewModel()) {
    val service by vm.serviceState.collectAsStateWithLifecycle()
    val requested by openNight.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.NIGHT) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(requested) {
        requested?.let {
            detailId = it
            openNight.value = null
        }
    }
    LaunchedEffect(service) {
        val s = service
        if (s is NightService.State.Finished) {
            detailId = s.nightId // morning summary right after "Nacht beenden"
            NightService.acknowledge()
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        val s = service
        val currentDetail = detailId
        when {
            s is NightService.State.Starting || s is NightService.State.Running || s is NightService.State.Finishing ->
                NightModeScreen(s, vm)

            currentDetail != null -> {
                BackHandler { detailId = null }
                NightDetailScreen(currentDetail, vm, onBack = { detailId = null })
            }

            else -> Scaffold(
                containerColor = AppColors.Background,
                bottomBar = {
                    NavigationBar(containerColor = AppColors.Surface) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppColors.Background,
                                    indicatorColor = AppColors.Primary,
                                    unselectedIconColor = AppColors.Muted,
                                    unselectedTextColor = AppColors.Muted,
                                    selectedTextColor = AppColors.Text,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                val open: (Long) -> Unit = { detailId = it }
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        Tab.NIGHT -> StartScreen(vm, s, onOpenNight = open)
                        Tab.HISTORY -> HistoryScreen(vm, onOpenNight = open)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
