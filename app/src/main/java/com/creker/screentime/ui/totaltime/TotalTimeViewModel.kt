package com.creker.screentime.ui.totaltime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creker.screentime.AppContainer
import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.PeriodSelection
import com.creker.screentime.core.StatsPeriod
import com.creker.screentime.core.resolve
import com.creker.screentime.core.shiftBy
import com.creker.screentime.core.usageChange
import com.creker.screentime.data.UsageRepository
import com.creker.screentime.data.system.AppInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * The "all apps at once" screen: a ring chart of the period's usage split by app, each
 * app's own change vs. the previous equally-long period, and how much less was used
 * overall than that previous period, when it was in fact less.
 */
class TotalTimeViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
) : ViewModel() {

    private val selection = MutableStateFlow<PeriodSelection>(PeriodSelection.Preset(StatsPeriod.Day))

    private data class ScreenData(
        val range: DayRange,
        val rows: List<AppUsageTotal>,
        val previousRows: List<AppUsageTotal>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenData: Flow<ScreenData> =
        selection.flatMapLatest { currentSelection ->
            val range = currentSelection.resolve(repository.today())
            val previousRange = range.shiftBy(-range.dayCount)
            combine(
                repository.observeTotals(range),
                repository.observeTotals(previousRange),
            ) { rows, previousRows -> ScreenData(range, rows, previousRows) }
        }

    val uiState: StateFlow<TotalTimeUiState> =
        screenData
            .map { data ->
                val today = repository.today()
                val previousByPackage = data.previousRows.associate { it.packageName to it.usageMillis }
                val totalMillis = data.rows.sumOf { it.usageMillis }
                val previousTotalMillis = data.previousRows.sumOf { it.usageMillis }
                val apps = data.rows
                    .sortedByDescending { it.usageMillis }
                    .map { row ->
                        val info = appInfoProvider.get(row.packageName)
                        TotalTimeAppUi(
                            packageName = row.packageName,
                            label = info.label,
                            icon = info.icon,
                            usageMillis = row.usageMillis,
                            change = usageChange(row.usageMillis, previousByPackage[row.packageName] ?: 0L, data.range.dayCount),
                            shareOfTotal = if (totalMillis > 0L) row.usageMillis.toFloat() / totalMillis else 0f,
                        )
                    }
                TotalTimeUiState(
                    range = data.range,
                    canGoForward = !data.range.shiftBy(data.range.dayCount).to.isAfter(today),
                    totalMillis = totalMillis,
                    apps = apps,
                    savedMillis = (previousTotalMillis - totalMillis).takeIf { it > 0L },
                    isInitialLoading = false,
                )
            }
            // Resolving labels and decoding icons must not happen on the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TotalTimeUiState(),
            )

    fun selectPeriod(newPeriod: StatsPeriod) {
        selection.value = PeriodSelection.Preset(newPeriod)
    }

    fun selectCustomRange(from: LocalDate, to: LocalDate) {
        selection.value = PeriodSelection.Fixed(if (from.isAfter(to)) DayRange(to, from) else DayRange(from, to))
    }

    fun goToPreviousPeriod() {
        val current = selection.value.resolve(repository.today())
        selection.value = PeriodSelection.Fixed(current.shiftBy(-current.dayCount))
    }

    fun goToNextPeriod() {
        val today = repository.today()
        val current = selection.value.resolve(today)
        val next = current.shiftBy(current.dayCount)
        if (!next.to.isAfter(today)) {
            selection.value = PeriodSelection.Fixed(next)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TotalTimeViewModel(container.usageRepository, container.appInfoProvider)
            }
        }
    }
}
