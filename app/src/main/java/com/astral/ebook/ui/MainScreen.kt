package com.astral.ebook.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astral.ebook.EbookUiState
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.FontFamilyOption
import com.astral.ebook.model.FooterOptions
import com.astral.ebook.model.Margins
import com.astral.ebook.model.Metadata
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.OutputFormat
import com.astral.ebook.model.Presets
import com.astral.ebook.model.ThemeOptions
import com.astral.ebook.model.ParagraphAlignment
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: EbookUiState,
    onMetadataChange: (Metadata.() -> Metadata) -> Unit,
    onSettingsChange: (EbookSettings.() -> EbookSettings) -> Unit,
    onPickBody: (Uri?) -> Unit,
    onPickCover: (Uri?) -> Unit,
    onGenerate: () -> Unit,
    onSaveDefaults: () -> Unit
) {
    val bodyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPickBody(uri)
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPickCover(uri)
    }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage.isNotBlank()) {
            scope.launch { snackbarHostState.showSnackbar(uiState.statusMessage) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AstralEbook") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle("Source Files")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = {
                    bodyPicker.launch(arrayOf("text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                }) {
                    Text(if (uiState.bodyUri == null) "Pick text / docx" else "Change body file")
                }
                FilledTonalButton(onClick = {
                    coverPicker.launch(arrayOf("image/*"))
                }) {
                    Text(if (uiState.coverUri == null) "Cover image" else "Change cover")
                }
            }

            SectionTitle("Metadata")
            MetadataFields(uiState.settings.metadata, onMetadataChange)

            SectionTitle("Output format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(OutputFormat.EPUB, OutputFormat.PDF).forEach { format ->
                    FilterChip(
                        selected = uiState.settings.outputFormat == format,
                        onClick = { onSettingsChange { copy(outputFormat = format) } },
                        label = { Text(format.name) }
                    )
                }
            }

            SectionTitle("Page preset")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.presets.forEach { preset ->
                    FilterChip(
                        selected = uiState.settings.pagePreset.name == preset.name,
                        onClick = {
                            onSettingsChange {
                                copy(
                                    pagePreset = preset,
                                    margins = com.astral.ebook.model.Margins(
                                        preset.marginTop,
                                        preset.marginBottom,
                                        preset.marginStart,
                                        preset.marginEnd
                                    )
                                )
                            }
                        },
                        label = { Text(preset.name) }
                    )
                }
            }

            SectionTitle("Orientation")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Orientation.values().forEach { orientation ->
                    FilterChip(
                        selected = uiState.settings.orientation == orientation,
                        onClick = { onSettingsChange { copy(orientation = orientation) } },
                        label = { Text(orientation.name) }
                    )
                }
            }

            SectionTitle("Margins")
            MarginFields(uiState.settings.margins) { margins ->
                onSettingsChange { copy(margins = margins) }
            }

            SectionTitle("Theme colors")
            ThemeSection(uiState.settings.themeOptions) { updated ->
                onSettingsChange { copy(themeOptions = updated) }
            }

            SectionTitle("Fonts")
            FontFamilySelector(
                label = "Title",
                selected = uiState.settings.fonts.titleFamily,
                onChange = { onSettingsChange { copy(fonts = fonts.copy(titleFamily = it)) } }
            )
            FontFamilySelector(
                label = "Heading",
                selected = uiState.settings.fonts.headingFamily,
                onChange = { onSettingsChange { copy(fonts = fonts.copy(headingFamily = it)) } }
            )
            FontFamilySelector(
                label = "Body",
                selected = uiState.settings.fonts.bodyFamily,
                onChange = { onSettingsChange { copy(fonts = fonts.copy(bodyFamily = it)) } }
            )
            NumberField("Title size (pt)", uiState.settings.fonts.titleSize) {
                onSettingsChange { copy(fonts = fonts.copy(titleSize = it)) }
            }
            NumberField("Subtitle size (pt)", uiState.settings.fonts.subtitleSize) {
                onSettingsChange { copy(fonts = fonts.copy(subtitleSize = it)) }
            }
            NumberField("Heading size (pt)", uiState.settings.fonts.headingSize) {
                onSettingsChange { copy(fonts = fonts.copy(headingSize = it)) }
            }
            NumberField("Body size (pt)", uiState.settings.fonts.bodySize) {
                onSettingsChange { copy(fonts = fonts.copy(bodySize = it)) }
            }
            NumberField("Line spacing", uiState.settings.fonts.lineHeight) {
                onSettingsChange { copy(fonts = fonts.copy(lineHeight = it)) }
            }

            SectionTitle("Paragraphs")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParagraphAlignment.values().forEach { align ->
                    AssistChip(
                        onClick = { onSettingsChange { copy(paragraphOptions = paragraphOptions.copy(alignment = align)) } },
                        label = { Text(align.name) }
                    )
                }
            }
            NumberField("First line indent (em)", uiState.settings.paragraphOptions.firstLineIndentEm) {
                onSettingsChange { copy(paragraphOptions = paragraphOptions.copy(firstLineIndentEm = it)) }
            }
            NumberField("Extra paragraph spacing (px)", uiState.settings.paragraphOptions.extraParagraphSpacing) {
                onSettingsChange { copy(paragraphOptions = paragraphOptions.copy(extraParagraphSpacing = it)) }
            }

            SectionTitle("Footer")
            FooterControls(uiState.settings.footerOptions) { updated ->
                onSettingsChange { copy(footerOptions = updated) }
            }

            if (uiState.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.bodyUri != null && !uiState.isGenerating,
                onClick = onGenerate
            ) {
                Text("Generate ${uiState.settings.outputFormat.name}")
            }

            TextButton(onClick = onSaveDefaults) {
                Text("Save as new default")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MetadataFields(metadata: Metadata, onChange: (Metadata.() -> Metadata) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Title" to metadata.title,
            "Subtitle" to metadata.subtitle,
            "Author" to metadata.author,
            "Translator" to metadata.translator,
            "Publisher" to metadata.publisher,
            "Year" to metadata.publicationYear,
            "Language" to metadata.language,
            "Notes" to metadata.notes
        ).forEach { (label, value) ->
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = { newValue ->
                    onChange {
                        when (label) {
                            "Title" -> copy(title = newValue)
                            "Subtitle" -> copy(subtitle = newValue)
                            "Author" -> copy(author = newValue)
                            "Translator" -> copy(translator = newValue)
                            "Publisher" -> copy(publisher = newValue)
                            "Year" -> copy(publicationYear = newValue)
                            "Language" -> copy(language = newValue)
                            else -> copy(notes = newValue)
                        }
                    }
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun FooterControls(footer: FooterOptions, onChange: (FooterOptions) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show footer", modifier = Modifier.weight(1f))
            Switch(checked = footer.showFooter, onCheckedChange = { onChange(footer.copy(showFooter = it)) })
        }
        if (footer.showFooter) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Title", modifier = Modifier.weight(1f))
                Switch(checked = footer.showTitle, onCheckedChange = { onChange(footer.copy(showTitle = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Subtitle", modifier = Modifier.weight(1f))
                Switch(checked = footer.showSubtitle, onCheckedChange = { onChange(footer.copy(showSubtitle = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page number", modifier = Modifier.weight(1f))
                Switch(checked = footer.showPageNumber, onCheckedChange = { onChange(footer.copy(showPageNumber = it)) })
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Float, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun MarginFields(margins: Margins, onChange: (Margins) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Top (px)", margins.top) { onChange(margins.copy(top = it)) }
        NumberField("Bottom (px)", margins.bottom) { onChange(margins.copy(bottom = it)) }
        NumberField("Left (px)", margins.start) { onChange(margins.copy(start = it)) }
        NumberField("Right (px)", margins.end) { onChange(margins.copy(end = it)) }
    }
}

@Composable
private fun ThemeSection(options: ThemeOptions, onChange: (ThemeOptions) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "System" to null,
            "Light" to false,
            "Dark" to true
        ).forEach { (label, value) ->
            FilterChip(
                selected = options.useDark == value,
                onClick = { onChange(options.copy(useDark = value)) },
                label = { Text(label) }
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    ColorField("Page background", options.pageBackground) {
        onChange(options.copy(pageBackground = it))
    }
    ColorField("Text color", options.textColor) {
        onChange(options.copy(textColor = it))
    }
}

@Composable
private fun ColorField(label: String, color: androidx.compose.ui.graphics.Color, onChange: (androidx.compose.ui.graphics.Color) -> Unit) {
    var text by remember(color) { mutableStateOf(color.toHexString()) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = text,
        onValueChange = {
            text = it
            parseColor(it)?.let(onChange)
        },
        label = { Text(label) }
    )
}

@Composable
private fun FontFamilySelector(label: String, selected: FontFamilyOption, onChange: (FontFamilyOption) -> Unit) {
    Column {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontFamilyOption.values().forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onChange(option) },
                    label = { Text(option.name) }
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun parseColor(input: String): androidx.compose.ui.graphics.Color? {
    return try {
        val clean = input.removePrefix("#")
        val color = clean.toLong(16).toInt()
        androidx.compose.ui.graphics.Color(color or (0xFF shl 24))
    } catch (t: Throwable) {
        null
    }
}
