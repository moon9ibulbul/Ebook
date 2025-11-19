package com.astral.ebook

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astral.ebook.datastore.SettingsStore
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.Metadata
import com.astral.ebook.repository.DocumentParser
import com.astral.ebook.repository.EbookGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EbookViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(EbookUiState())
    val uiState: StateFlow<EbookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { saved ->
                _uiState.update { it.copy(settings = saved) }
            }
        }
    }

    fun updateMetadata(block: Metadata.() -> Metadata) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(metadata = _uiState.value.settings.metadata.block())
        )
    }

    fun updateSettings(block: EbookSettings.() -> EbookSettings) {
        _uiState.value = _uiState.value.copy(settings = _uiState.value.settings.block())
    }

    fun updateBodyUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(bodyUri = uri)
    }

    fun updateCoverUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(coverUri = uri)
    }

    fun saveDefaults() {
        viewModelScope.launch {
            settingsStore.save(_uiState.value.settings)
        }
    }

    fun generate() {
        val ctx = getApplication<Application>()
        val current = _uiState.value
        if (current.bodyUri == null) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isGenerating = true, statusMessage = "") }
                val latest = _uiState.value
                val body = DocumentParser.readBody(ctx, latest.bodyUri!!)
                val file = EbookGenerator.generate(ctx, latest.settings, body, latest.coverUri)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedFile = file,
                        statusMessage = "Generated at ${file.absolutePath}"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isGenerating = false, statusMessage = t.message ?: "Error") }
            }
        }
    }
}

data class EbookUiState(
    val settings: EbookSettings = EbookSettings(),
    val bodyUri: Uri? = null,
    val coverUri: Uri? = null,
    val isGenerating: Boolean = false,
    val generatedFile: java.io.File? = null,
    val statusMessage: String = ""
)

