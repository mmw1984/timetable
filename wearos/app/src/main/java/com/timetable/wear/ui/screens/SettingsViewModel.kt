package com.timetable.wear.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetable.wear.data.repository.TimetableRepository
import com.timetable.wear.engine.TimetableEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val engine: TimetableEngine
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing

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
}
