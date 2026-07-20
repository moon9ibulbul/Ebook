package com.astral.ebook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.astral.ebook.model.toBundle
import com.astral.ebook.repository.DocumentParser
import com.astral.ebook.ui.MainScreen
import com.astral.ebook.ui.PreviewActivity
import com.astral.ebook.ui.VisualEditorActivity
import com.astral.ebook.ui.theme.AstralEbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: EbookViewModel by viewModels()

    private val visualEditorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val content = result.data?.getStringExtra("content") ?: ""
            saveVisualEditorContent(content)
        }
    }

    private fun saveVisualEditorContent(content: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(cacheDir, "editor_content.txt")
            file.writeText(content)
            withContext(Dispatchers.Main) {
                viewModel.updateBodyUri(Uri.fromFile(file))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val editorFile = File(cacheDir, "editor_content.txt")
        if (!editorFile.exists()) {
            try {
                editorFile.createNewFile()
            } catch (_: Exception) {}
        }
        viewModel.updateBodyUri(Uri.fromFile(editorFile))

        setContent {
            val state by viewModel.uiState.collectAsState()
            AstralEbookTheme(useDarkTheme = state.settings.themeOptions.useDark ?: false) {
                MainScreen(
                    uiState = state,
                    onMetadataChange = viewModel::updateMetadata,
                    onSettingsChange = viewModel::updateSettings,
                    onPickBody = viewModel::updateBodyUri,
                    onPickCover = viewModel::updateCoverUri,
                    onVisualEditor = {
                        lifecycleScope.launch {
                            val currentContent = state.bodyUri?.let { uri ->
                                try {
                                    withContext(Dispatchers.IO) {
                                        DocumentParser.readRawText(this@MainActivity, uri)
                                    }
                                } catch (_: Exception) { "" }
                            } ?: ""
                            val intent = Intent(this@MainActivity, VisualEditorActivity::class.java).apply {
                                putExtra("content", currentContent)
                            }
                            visualEditorLauncher.launch(intent)
                        }
                    },
                    onPreview = {
                        val intent = Intent(this@MainActivity, PreviewActivity::class.java).apply {
                            putExtra("bodyUri", state.bodyUri?.toString())
                            putExtra("coverUri", state.coverUri?.toString())
                            putExtra("settings", state.settings.toBundle())
                        }
                        startActivity(intent)
                    },
                    onGenerate = { viewModel.generate() },
                    onSaveDefaults = { viewModel.saveDefaults() }
                )
            }
        }
    }
}
