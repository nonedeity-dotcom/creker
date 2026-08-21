package com.creker.screentime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creker.screentime.AppContainer
import com.creker.screentime.ScreenTimeApplication
import com.creker.screentime.data.system.UsageAccess
import com.creker.screentime.ui.appdetail.AppDetailScreen
import com.creker.screentime.ui.appdetail.AppDetailViewModel
import com.creker.screentime.ui.permission.PermissionScreen
import com.creker.screentime.ui.stats.StatsScreen
import com.creker.screentime.ui.stats.StatsViewModel
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ScreenTimeApplication).container

        setContent {
            CrekerScreenTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ScreenTimeApp(container)
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeApp(container: AppContainer) {
    val context = LocalContext.current
    val statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val statsState by statsViewModel.uiState.collectAsStateWithLifecycle()

    var hasAccess by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }

    // The permission is granted on a system screen, so the only reliable moment to
    // re-check it is when this activity comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = UsageAccess.isGranted(context)
                if (hasAccess) statsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectedPackage != null) { selectedPackage = null }

    if (!hasAccess) {
        PermissionScreen(
            onOpenSettings = {
                settingsUnavailable = !UsageAccess.openSettings(context)
            },
            onRecheck = {
                hasAccess = UsageAccess.isGranted(context)
                if (hasAccess) statsViewModel.refresh()
            },
            settingsUnavailable = settingsUnavailable,
        )
        return
    }

    val openPackage = selectedPackage
    if (openPackage != null) {
        // Keyed on the package name so navigating from one app's detail straight to
        // another's (without going back to the list first) starts a fresh view model
        // instead of reusing the previous app's state.
        val detailViewModel: AppDetailViewModel = viewModel(
            key = openPackage,
            factory = AppDetailViewModel.factory(container, openPackage, statsState.range),
        )
        val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
        AppDetailScreen(
            state = detailState,
            today = LocalDate.now(),
            onBack = { selectedPackage = null },
            onPrevious = detailViewModel::goToPreviousPeriod,
            onNext = detailViewModel::goToNextPeriod,
        )
    } else {
        StatsScreen(
            state = statsState,
            today = LocalDate.now(),
            onSelectPeriod = statsViewModel::selectPeriod,
            onSelectCustomRange = statsViewModel::selectCustomRange,
            onRefresh = statsViewModel::refresh,
            onAppClick = { selectedPackage = it },
        )
    }
}
