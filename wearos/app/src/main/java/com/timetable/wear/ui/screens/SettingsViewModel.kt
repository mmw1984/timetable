package com.timetable.wear.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import com.timetable.wear.data.local.DenseLayoutMode
import com.timetable.wear.data.local.UiPreferences
import com.timetable.wear.data.repository.TimetableRepository
import com.timetable.wear.engine.TimetableEngine
import com.timetable.wear.tiles.FullScheduleTileService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val engine: TimetableEngine,
    private val uiPreferences: UiPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing

    val denseMode: StateFlow<String> = uiPreferences.denseMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DenseLayoutMode.AUTO)

    val mergeConsecutive: StateFlow<Boolean> = uiPreferences.mergeConsecutive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        viewModelScope.launch {
            engine.ensureReady()
            repository.urlConfig.collectLatest { config ->
                _urlInput.value = config.base
            }
        }
    }

    fun updateUrl(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun saveUrl() {
        viewModelScope.launch {
            val result = engine.refreshFromRemote(_urlInput.value)
            _message.value = if (result.isSuccess) "更新成功" else "更新失敗：${result.exceptionOrNull()?.message}"
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val result = engine.refreshFromRemote()
            _message.value = if (result.isSuccess) "更新成功" else "更新失敗：${result.exceptionOrNull()?.message}"
        }
    }

    /** Cycle 自動 → 密集 → 標準 and refresh the schedule tile immediately. */
    fun cycleDenseMode() {
        viewModelScope.launch {
            uiPreferences.setDenseMode(DenseLayoutMode.next(denseMode.value))
            requestScheduleTileUpdate()
        }
    }

    fun setMergeConsecutive(merge: Boolean) {
        viewModelScope.launch {
            uiPreferences.setMergeConsecutive(merge)
            requestScheduleTileUpdate()
        }
    }

    private fun requestScheduleTileUpdate() {
        runCatching {
            TileService.requestUpdate(appContext, FullScheduleTileService::class.java)
        }
    }
}
