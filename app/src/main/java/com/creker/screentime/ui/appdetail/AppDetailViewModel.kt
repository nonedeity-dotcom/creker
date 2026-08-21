package com.creker.screentime.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creker.screentime.AppContainer
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.UsageStreaks
import com.creker.screentime.core.shiftBy
import com.creker.screentime.data.UsageRepository
import com.creker.screentime.data.system.AppInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * One app's detail screen: usage and launches over a steppable date window, plus
 * streak and peak stats drawn from the app's whole locally retained history.
 */
class AppDetailViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
    private val packageName: String,
    initialRange: DayRange,
) : ViewModel() {

    private val range = MutableStateFlow(initialRange)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val periodTotal = range.flatMapLatest { r -> repository.observeAppPeriodTotal(packageName, r) }

    val uiState: StateFlow<AppDetailUiState> =
        combine(range, periodTotal, repository.observeAppHistory(packageName)) { currentRange, total, history ->
            val today = repository.today()
            val info = appInfoProvider.get(packageName)
            AppDetailUiState(
                packageName = packageName,
                label = info.label,
                icon = info.icon,
                installedAtMs = info.installedAtMs,
                range = currentRange,
                canGoForward = !currentRange.shiftBy(currentRange.dayCount).to.isAfter(today),
                usageMillis = total.usageMillis,
                launchCount = total.launchCount,
                averagePerDayMillis = total.usageMillis / currentRange.dayCount,
                currentStreakDays = UsageStreaks.currentStreak(history, today),
                longestStreakDays = UsageStreaks.longestStreak(history),
                maxDayMillis = UsageStreaks.maxDayUsage(history),
                isLoading = false,
            )
        }
            // Streak/average math and label resolution must not happen on the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AppDetailUiState(packageName = packageName, range = initialRange),
            )

    fun goToPreviousPeriod() {
        range.update { it.shiftBy(-it.dayCount) }
    }

    fun goToNextPeriod() {
        range.update { current ->
            val next = current.shiftBy(current.dayCount)
            if (next.to.isAfter(repository.today())) current else next
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(
            container: AppContainer,
            packageName: String,
            initialRange: DayRange,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppDetailViewModel(container.usageRepository, container.appInfoProvider, packageName, initialRange)
            }
        }
    }
}
