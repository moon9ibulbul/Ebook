package com.astral.ebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.astral.ebook.ui.MainScreen
import com.astral.ebook.ui.theme.AstralEbookTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EbookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsState()
            AstralEbookTheme(useDarkTheme = state.settings.themeOptions.useDark ?: false) {
                MainScreen(
                    uiState = state,
                    onMetadataChange = viewModel::updateMetadata,
                    onSettingsChange = viewModel::updateSettings,
                    onPickBody = viewModel::updateBodyUri,
                    onPickCover = viewModel::updateCoverUri,
                    onGenerate = { viewModel.generate() },
                    onSaveDefaults = { viewModel.saveDefaults() }
                )
            }
        }
    }
}
