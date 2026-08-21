package com.creker.screentime.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creker.screentime.AppContainer
import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.StatsPeriod
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
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
) : ViewModel() {

    private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Day)
    private val refreshing = MutableStateFlow(false)

    /**
     * Bumped after every sync. "Today" is relative to the wall clock, so the day range
     * has to be resolved again rather than reused from when the period was picked.
     */
    private val resolveTicket = MutableStateFlow(0)

    private data class Totals(
        val period: StatsPeriod,
        val range: DayRange,
        val rows: List<AppUsageTotal>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val totals: Flow<Totals> =
        combine(period, resolveTicket) { selected, _ -> selected }
            .flatMapLatest { selected ->
                val range = selected.resolve(repository.today())
                repository.observeTotals(range).map { Totals(selected, range, it) }
            }

    val uiState: StateFlow<StatsUiState> =
        combine(totals, repository.observeEarliestDay(), refreshing) { current, earliest, isRefreshing ->
            StatsUiState(
                period = current.period,
                range = current.range,
                totalMillis = current.rows.sumOf { it.usageMillis },
                apps = current.rows.toUiRows(),
                earliestStoredDay = earliest,
                isInitialLoading = false,
                isRefreshing = isRefreshing,
            )
        }
            // Resolving labels and decoding icons must not happen on the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StatsUiState(),
            )

    fun selectPeriod(newPeriod: StatsPeriod) {
        period.value = newPeriod
    }

    fun selectCustomRange(from: LocalDate, to: LocalDate) {
        period.value = StatsPeriod.Custom(from, to)
    }

    /** Pulls fresh events from the system into the local database. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.sync()
                resolveTicket.value += 1
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun List<AppUsageTotal>.toUiRows(): List<AppUsageUi> {
        val top = maxOfOrNull { it.usageMillis } ?: 0L
        return map { total ->
            val info = appInfoProvider.get(total.packageName)
            AppUsageUi(
                packageName = total.packageName,
                label = info.label,
                icon = info.icon,
                usageMillis = total.usageMillis,
                shareOfTop = if (top > 0L) total.usageMillis.toFloat() / top else 0f,
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatsViewModel(container.usageRepository, container.appInfoProvider)
            }
        }
    }
}
