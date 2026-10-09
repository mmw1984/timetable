package com.timetable.wear.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetable.wear.data.local.DenseLayoutMode
import com.timetable.wear.data.local.UiPreferences
import com.timetable.wear.engine.CountdownState
import com.timetable.wear.engine.HomeScheduleState
import com.timetable.wear.engine.TimetableEngine
import com.timetable.wear.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: TimetableEngine,
    private val repository: TimetableRepository,
    uiPreferences: UiPreferences
) : ViewModel() {

    val scheduleState: StateFlow<HomeScheduleState> = engine.scheduleState
    val countdownState: StateFlow<CountdownState> = engine.countdownState
    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing
    val denseMode: StateFlow<String> = uiPreferences.denseMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DenseLayoutMode.AUTO)
    val mergeConsecutive: StateFlow<Boolean> = uiPreferences.mergeConsecutive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun start() {
        viewModelScope.launch { engine.start() }
    }

    fun refresh() {
        viewModelScope.launch {
            engine.refreshFromRemote()
        }
    }

    fun prevDay() {
        viewModelScope.launch { engine.previousSchoolDay() }
    }

    fun nextDay() {
        viewModelScope.launch { engine.nextSchoolDay() }
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}
