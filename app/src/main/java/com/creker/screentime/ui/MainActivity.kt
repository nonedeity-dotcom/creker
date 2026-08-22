package com.creker.screentime.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creker.screentime.AppContainer
import com.creker.screentime.R
import com.creker.screentime.ScreenTimeApplication
import com.creker.screentime.core.UsageCsvReader
import com.creker.screentime.core.UsageCsvWriter
import com.creker.screentime.data.system.UsageAccess
import com.creker.screentime.ui.appdetail.AppDetailScreen
import com.creker.screentime.ui.appdetail.AppDetailViewModel
import com.creker.screentime.ui.permission.PermissionScreen
import com.creker.screentime.ui.stats.StatsScreen
import com.creker.screentime.ui.stats.StatsViewModel
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import com.creker.screentime.ui.totaltime.TotalTimeScreen
import com.creker.screentime.ui.totaltime.TotalTimeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val scope = rememberCoroutineScope()
    val statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val statsState by statsViewModel.uiState.collectAsStateWithLifecycle()

    var hasAccess by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var totalTimeOpen by remember { mutableStateOf(false) }

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

    // "Save all data": the user picks a destination via the system file picker, and
    // the whole local history is written there as CSV. Nothing is sent anywhere —
    // this is the one file I/O operation in the app, and it only ever writes to a
    // location the user explicitly chose.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = UsageCsvWriter.write(container.usageRepository.exportRows())
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    // "Load data": the reverse of export, for moving to a new phone -- pick the CSV a
    // previous export wrote, and merge whatever rows this device doesn't already have
    // into the local database. "*/*" rather than "text/csv" because many file pickers
    // don't tag a CSV with that MIME type reliably; content is validated by the parser
    // itself instead.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) {
                Toast.makeText(context, R.string.import_error, Toast.LENGTH_LONG).show()
                return@launch
            }
            val rows = UsageCsvReader.parse(text)
            if (rows.isEmpty()) {
                Toast.makeText(context, R.string.import_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            val imported = container.usageRepository.importRows(rows)
            Toast.makeText(context, context.getString(R.string.import_success, imported), Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = selectedPackage != null || totalTimeOpen) {
        if (selectedPackage != null) selectedPackage = null else totalTimeOpen = false
    }

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
            onSelectRange = detailViewModel::selectRange,
            onSelectMetric = detailViewModel::selectMetric,
        )
    } else if (totalTimeOpen) {
        val totalTimeViewModel: TotalTimeViewModel = viewModel(factory = TotalTimeViewModel.factory(container))
        val totalTimeState by totalTimeViewModel.uiState.collectAsStateWithLifecycle()
        TotalTimeScreen(
            state = totalTimeState,
            today = LocalDate.now(),
            onBack = { totalTimeOpen = false },
            onPrevious = totalTimeViewModel::goToPreviousPeriod,
            onNext = totalTimeViewModel::goToNextPeriod,
            onSelectPeriod = totalTimeViewModel::selectPeriod,
            onSelectCustomRange = totalTimeViewModel::selectCustomRange,
            onAppClick = { selectedPackage = it },
        )
    } else {
        StatsScreen(
            state = statsState,
            today = LocalDate.now(),
            onSelectPeriod = statsViewModel::selectPeriod,
            onSelectCustomRange = statsViewModel::selectCustomRange,
            onPrevious = statsViewModel::goToPreviousPeriod,
            onNext = statsViewModel::goToNextPeriod,
            onRefresh = statsViewModel::refresh,
            onAppClick = { selectedPackage = it },
            onSelectMetric = statsViewModel::selectMetric,
            onExport = { exportLauncher.launch("screen_time_${LocalDate.now().format(EXPORT_FILE_DATE_FORMATTER)}.csv") },
            onImport = { importLauncher.launch("*/*") },
            onOpenTotalTime = { totalTimeOpen = true },
        )
    }
}

private val EXPORT_FILE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
