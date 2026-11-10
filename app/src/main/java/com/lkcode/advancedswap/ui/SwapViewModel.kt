package com.lkcode.advancedswap.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lkcode.advancedswap.data.RepositorySnapshot
import com.lkcode.advancedswap.data.SwapRepository
import com.lkcode.advancedswap.domain.OperationResult
import com.lkcode.advancedswap.domain.SwapOperationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SwapUiState(
    val loading: Boolean = true,
    val snapshot: RepositorySnapshot? = null,
    val error: String? = null,
    val operationRunning: Boolean = false,
    val operationLabel: String? = null,
    val lastResult: OperationResult? = null,
    val sizeText: String = "128",
)

class SwapViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SwapRepository(application)
    private val mutableState = MutableStateFlow(SwapUiState())
    val state: StateFlow<SwapUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun setSizeText(value: String) {
        if (value.length <= 4 && value.all(Char::isDigit)) mutableState.update { it.copy(sizeText = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching { repository.refresh() }
                .onSuccess { data -> mutableState.update { it.copy(loading = false, snapshot = data, error = data.swap.readError) } }
                .onFailure { error -> mutableState.update { it.copy(loading = false, error = error.message ?: "Falha ao ler o estado da swap.") } }
        }
    }

    fun perform(request: SwapOperationRequest) {
        if (mutableState.value.operationRunning) return
        viewModelScope.launch {
            mutableState.update { it.copy(operationRunning = true, operationLabel = request.operation.name) }
            val result = repository.perform(request)
            mutableState.update { it.copy(operationRunning = false, operationLabel = null, lastResult = result) }
            refresh()
        }
    }
}

