package com.astral.ebook.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astral.ebook.EbookUiState
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.FontFamilyOption
import com.astral.ebook.model.FontTarget
import com.astral.ebook.model.FooterOptions
import com.astral.ebook.model.Margins
import com.astral.ebook.model.Metadata
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.model.Presets
import com.astral.ebook.model.ThemeOptions
import com.astral.ebook.repository.openImageStream
import com.astral.ebook.ui.theme.IndigoDark
import com.astral.ebook.ui.theme.IndigoPrimary
import com.astral.ebook.ui.theme.IndigoPrimaryVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: EbookUiState,
    onMetadataChange: (Metadata.() -> Metadata) -> Unit,
    onSettingsChange: (EbookSettings.() -> EbookSettings) -> Unit,
    onPickBody: (Uri?) -> Unit,
    onPickCover: (Uri?) -> Unit,
    onVisualEditor: () -> Unit,
    onPreview: () -> Unit,
    onGenerate: () -> Unit,
    onSaveDefaults: () -> Unit
) {
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPickCover(uri)
    }
    val context = LocalContext.current
    val fontMimeTypes = arrayOf(
        "font/ttf",
        "font/otf",
        "application/x-font-ttf",
        "application/x-font-otf"
    )

    fun applyCustomFont(target: FontTarget, uri: Uri?) {
        val uriString = uri?.toString()
        onSettingsChange {
            copy(
                fonts = when (target) {
                    FontTarget.Title -> fonts.copy(
                        titleFamily = if (uri != null) FontFamilyOption.Custom else FontFamilyOption.Serif,
                        titleFontUri = uriString
                    )
                    FontTarget.Heading -> fonts.copy(
                        headingFamily = if (uri != null) FontFamilyOption.Custom else FontFamilyOption.Serif,
                        headingFontUri = uriString
                    )
                    FontTarget.Body -> fonts.copy(
                        bodyFamily = if (uri != null) FontFamilyOption.Custom else FontFamilyOption.Serif,
                        bodyFontUri = uriString
                    )
                }
            )
        }
    }

    fun persistFont(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    val titleFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistFont(uri)
            applyCustomFont(FontTarget.Title, uri)
        }
    }
    val headingFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistFont(uri)
            applyCustomFont(FontTarget.Heading, uri)
        }
    }
    val bodyFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistFont(uri)
            applyCustomFont(FontTarget.Body, uri)
        }
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
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "AstralEbook",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    if (uiState.isGenerating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            onClick = onPreview
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pratinjau")
                        }

                        Button(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = uiState.bodyUri != null && !uiState.isGenerating,
                            onClick = onGenerate
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate PDF", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Workspace Card
            HeroWorkspaceCard(
                coverUri = uiState.coverUri,
                onVisualEditor = onVisualEditor,
                onPickCover = { coverPicker.launch(arrayOf("image/*")) }
            )

            // Cover Preview Card (When loaded)
            if (uiState.coverUri != null) {
                CoverPreviewCard(
                    coverUri = uiState.coverUri,
                    onRemoveCover = { onPickCover(null) }
                )
            }

            // Section 1: Metadata
            CollapsibleStudioCard(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "Metadata Buku"
            ) {
                MetadataFields(uiState.settings.metadata, onMetadataChange)
            }

            // Section 2: Sampul & Cover Options
            CollapsibleStudioCard(
                icon = Icons.Default.Image,
                title = "Opsi Sampul"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !uiState.settings.coverOptions.fullBleed,
                        onClick = { onSettingsChange { copy(coverOptions = coverOptions.copy(fullBleed = false)) } },
                        label = { Text("Dengan Margin") }
                    )
                    FilterChip(
                        selected = uiState.settings.coverOptions.fullBleed,
                        onClick = { onSettingsChange { copy(coverOptions = coverOptions.copy(fullBleed = true)) } },
                        label = { Text("Full Bleed") }
                    )
                }
            }

            // Section 3: Preset Halaman & Orientasi
            CollapsibleStudioCard(
                icon = Icons.Default.AutoAwesome,
                title = "Halaman & Orientasi"
            ) {
                Text("Preset Halaman", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Presets.presets.forEach { preset ->
                        FilterChip(
                            selected = uiState.settings.pagePreset.name == preset.name,
                            onClick = {
                                onSettingsChange {
                                    copy(
                                        pagePreset = preset,
                                        margins = Margins(
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

                Spacer(modifier = Modifier.height(8.dp))

                Text("Orientasi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Orientation.values().forEach { orientation ->
                        FilterChip(
                            selected = uiState.settings.orientation == orientation,
                            onClick = { onSettingsChange { copy(orientation = orientation) } },
                            label = { Text(if (orientation == Orientation.Portrait) "Potret" else "Lanskap") }
                        )
                    }
                }
            }

            // Section 4: Margin
            CollapsibleStudioCard(
                icon = Icons.Default.Straighten,
                title = "Margin Halaman (px)"
            ) {
                MarginFields(uiState.settings.margins) { margins ->
                    onSettingsChange { copy(margins = margins) }
                }
            }

            // Section 5: Tema & Warna
            CollapsibleStudioCard(
                icon = Icons.Default.Palette,
                title = "Tema & Warna Halaman"
            ) {
                ThemeSection(uiState.settings.themeOptions) { updated ->
                    onSettingsChange { copy(themeOptions = updated) }
                }
            }

            // Section 6: Font & Tipografi
            CollapsibleStudioCard(
                icon = Icons.Default.TextFields,
                title = "Tipografi & Font"
            ) {
                FontFamilySelector(
                    label = "Font Judul Utama",
                    selected = uiState.settings.fonts.titleFamily,
                    customFontUri = uiState.settings.fonts.titleFontUri,
                    onChange = { option ->
                        onSettingsChange {
                            copy(
                                fonts = fonts.copy(
                                    titleFamily = option,
                                    titleFontUri = if (option == FontFamilyOption.Custom) fonts.titleFontUri else null
                                )
                            )
                        }
                    },
                    onPickCustomFont = { titleFontPicker.launch(fontMimeTypes) },
                    onClearCustomFont = { applyCustomFont(FontTarget.Title, null) }
                )

                FontFamilySelector(
                    label = "Font Sub-Judul / Bab",
                    selected = uiState.settings.fonts.headingFamily,
                    customFontUri = uiState.settings.fonts.headingFontUri,
                    onChange = { option ->
                        onSettingsChange {
                            copy(
                                fonts = fonts.copy(
                                    headingFamily = option,
                                    headingFontUri = if (option == FontFamilyOption.Custom) fonts.headingFontUri else null
                                )
                            )
                        }
                    },
                    onPickCustomFont = { headingFontPicker.launch(fontMimeTypes) },
                    onClearCustomFont = { applyCustomFont(FontTarget.Heading, null) }
                )

                FontFamilySelector(
                    label = "Font Teks Isi",
                    selected = uiState.settings.fonts.bodyFamily,
                    customFontUri = uiState.settings.fonts.bodyFontUri,
                    onChange = { option ->
                        onSettingsChange {
                            copy(
                                fonts = fonts.copy(
                                    bodyFamily = option,
                                    bodyFontUri = if (option == FontFamilyOption.Custom) fonts.bodyFontUri else null
                                )
                            )
                        }
                    },
                    onPickCustomFont = { bodyFontPicker.launch(fontMimeTypes) },
                    onClearCustomFont = { applyCustomFont(FontTarget.Body, null) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        NumberField("Judul (pt)", uiState.settings.fonts.titleSize) { value ->
                            onSettingsChange { copy(fonts = fonts.copy(titleSize = value)) }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        NumberField("Bab (pt)", uiState.settings.fonts.chapterSize) { value ->
                            onSettingsChange { copy(fonts = fonts.copy(chapterSize = value)) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        NumberField("Heading (pt)", uiState.settings.fonts.headingSize) { value ->
                            onSettingsChange { copy(fonts = fonts.copy(headingSize = value)) }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        NumberField("Isi (pt)", uiState.settings.fonts.bodySize) { value ->
                            onSettingsChange { copy(fonts = fonts.copy(bodySize = value)) }
                        }
                    }
                }

                NumberField("Jarak Baris (Line Spacing)", uiState.settings.fonts.lineHeight) { value ->
                    onSettingsChange { copy(fonts = fonts.copy(lineHeight = value)) }
                }
            }

            // Section 7: Paragraf
            CollapsibleStudioCard(
                icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
                title = "Pengaturan Paragraf"
            ) {
                Text("Rata Teks Default", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParagraphAlignment.values().forEach { align ->
                        val alignIcon = when (align) {
                            ParagraphAlignment.Left -> Icons.AutoMirrored.Filled.FormatAlignLeft
                            ParagraphAlignment.Center -> Icons.Default.FormatAlignCenter
                            ParagraphAlignment.Right -> Icons.AutoMirrored.Filled.FormatAlignRight
                            ParagraphAlignment.Justify -> Icons.Default.FormatAlignJustify
                        }
                        FilterChip(
                            selected = uiState.settings.paragraphOptions.alignment == align,
                            onClick = { onSettingsChange { copy(paragraphOptions = paragraphOptions.copy(alignment = align)) } },
                            label = { Icon(alignIcon, contentDescription = align.name, modifier = Modifier.size(20.dp)) }
                        )
                    }
                }

                NumberField("Indentasi Baris Pertama (em)", uiState.settings.paragraphOptions.firstLineIndentEm) { value ->
                    onSettingsChange { copy(paragraphOptions = paragraphOptions.copy(firstLineIndentEm = value)) }
                }
                NumberField("Jarak Antar Paragraf (px)", uiState.settings.paragraphOptions.extraParagraphSpacing) { value ->
                    onSettingsChange { copy(paragraphOptions = paragraphOptions.copy(extraParagraphSpacing = value)) }
                }
            }

            // Section 8: Footer
            CollapsibleStudioCard(
                icon = Icons.Default.ViewHeadline,
                title = "Pengaturan Footer"
            ) {
                FooterControls(uiState.settings.footerOptions) { updated ->
                    onSettingsChange { copy(footerOptions = updated) }
                }
            }

            // Action Save Default
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Simpan Pengaturan Default", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Gunakan konfigurasi saat ini untuk dokumen baru", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(
                        shape = RoundedCornerShape(10.dp),
                        onClick = onSaveDefaults
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroWorkspaceCard(
    coverUri: Uri?,
    onVisualEditor: () -> Unit,
    onPickCover: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(IndigoDark, IndigoPrimary, IndigoPrimaryVariant)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "Ruang Kerja Dokumen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        onClick = onVisualEditor,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = IndigoDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Editor", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        onClick = onPickCover,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (coverUri == null) "Sampul" else "Ganti Sampul",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverPreviewCard(
    coverUri: Uri,
    onRemoveCover: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(coverUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(coverUri) {
        withContext(Dispatchers.IO) {
            try {
                openImageStream(context, coverUri.toString())?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        bitmap = decoded.asImageBitmap()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Preview Sampul",
                        modifier = Modifier
                            .height(80.dp)
                            .width(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .width(60.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Column {
                    Text("Gambar Sampul Terpasang", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = coverUri.lastPathSegment ?: "Sampul Ebook",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onRemoveCover) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus Sampul", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CollapsibleStudioCard(
    icon: ImageVector,
    title: String,
    initialExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Tutup" else "Buka",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun MetadataFields(metadata: Metadata, onChange: (Metadata.() -> Metadata) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            "Judul Utama" to metadata.title,
            "Subjudul" to metadata.subtitle,
            "Bab / Bagian" to metadata.chapter,
            "Penulis / Pengarang" to metadata.author,
            "Penerjemah" to metadata.translator,
            "Penerbit" to metadata.publisher,
            "Tahun Terbit" to metadata.publicationYear,
            "Bahasa" to metadata.language,
            "Catatan TAMBAHAN" to metadata.notes
        ).forEach { pair ->
            val fieldLabel = pair.first
            val fieldValue = pair.second
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = fieldValue,
                onValueChange = { newValue ->
                    onChange {
                        when (fieldLabel) {
                            "Judul Utama" -> copy(title = newValue)
                            "Subjudul" -> copy(subtitle = newValue)
                            "Bab / Bagian" -> copy(chapter = newValue)
                            "Penulis / Pengarang" -> copy(author = newValue)
                            "Penerjemah" -> copy(translator = newValue)
                            "Penerbit" -> copy(publisher = newValue)
                            "Tahun Terbit" -> copy(publicationYear = newValue)
                            "Bahasa" -> copy(language = newValue)
                            else -> copy(notes = newValue)
                        }
                    }
                },
                label = { Text(fieldLabel) },
                singleLine = fieldLabel != "Catatan TAMBAHAN",
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun FooterControls(footer: FooterOptions, onChange: (FooterOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tampilkan Footer", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Switch(checked = footer.showFooter, onCheckedChange = { onChange(footer.copy(showFooter = it)) })
        }
        AnimatedVisibility(visible = footer.showFooter) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tampilkan Judul", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = footer.showTitle, onCheckedChange = { onChange(footer.copy(showTitle = it)) })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tampilkan Bab", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = footer.showChapter, onCheckedChange = { onChange(footer.copy(showChapter = it)) })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tampilkan Nomor Halaman", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = footer.showPageNumber, onCheckedChange = { onChange(footer.copy(showPageNumber = it)) })
                }
                NumberField("Ukuran Font Footer (pt)", footer.fontSize) {
                    onChange(footer.copy(fontSize = it))
                }
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun MarginFields(margins: Margins, onChange: (Margins) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                NumberField("Atas", margins.top) { onChange(margins.copy(top = it)) }
            }
            Box(modifier = Modifier.weight(1f)) {
                NumberField("Bawah", margins.bottom) { onChange(margins.copy(bottom = it)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                NumberField("Kiri", margins.start) { onChange(margins.copy(start = it)) }
            }
            Box(modifier = Modifier.weight(1f)) {
                NumberField("Kanan", margins.end) { onChange(margins.copy(end = it)) }
            }
        }
    }
}

@Composable
private fun ThemeSection(options: ThemeOptions, onChange: (ThemeOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mode Tampilan Aplikasi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Sistem" to null,
                "Terang" to false,
                "Gelap" to true
            ).forEach { pair ->
                val modeLabel = pair.first
                val modeVal = pair.second
                FilterChip(
                    selected = options.useDark == modeVal,
                    onClick = { onChange(options.copy(useDark = modeVal)) },
                    label = { Text(modeLabel) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        ColorPickerField("Warna Latar Halaman PDF", options.pageBackground) {
            onChange(options.copy(pageBackground = it))
        }
        ColorPickerField("Warna Teks PDF", options.textColor) {
            onChange(options.copy(textColor = it))
        }
    }
}

@Composable
private fun ColorPickerField(
    label: String,
    color: Color,
    onChange: (Color) -> Unit
) {
    var text by remember(color) { mutableStateOf(color.toHexString()) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = text,
        onValueChange = {
            text = it
            parseColor(it)?.let(onChange)
        },
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun FontFamilySelector(
    label: String,
    selected: FontFamilyOption,
    customFontUri: String?,
    onChange: (FontFamilyOption) -> Unit,
    onPickCustomFont: () -> Unit,
    onClearCustomFont: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontFamilyOption.values().forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onChange(option) },
                    label = { Text(option.name) }
                )
            }
        }
        if (selected == FontFamilyOption.Custom) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    shape = RoundedCornerShape(8.dp),
                    onClick = onPickCustomFont
                ) {
                    Text(if (customFontUri == null) "Pilih File Font" else "Ganti Font")
                }
                if (customFontUri != null) {
                    val fontName = try {
                        Uri.parse(customFontUri).lastPathSegment ?: customFontUri
                    } catch (_: Throwable) {
                        customFontUri
                    }
                    Text(
                        text = fontName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = onClearCustomFont) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus Custom Font", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun Color.toHexString(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun parseColor(input: String): Color? {
    val hexRegex = Regex("^#?[0-9a-fA-F]{6}$")
    if (!hexRegex.matches(input)) return null
    val clean = input.removePrefix("#")
    val color = clean.toLong(16).toInt()
    return Color(color or (0xFF shl 24))
}
