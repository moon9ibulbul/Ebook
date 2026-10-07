package com.astral.ebook.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astral.ebook.datastore.SettingsStore
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.toEbookSettings
import com.astral.ebook.repository.DocumentParser
import com.astral.ebook.repository.EbookLayoutEngine
import com.astral.ebook.repository.PageContent
import com.astral.ebook.repository.PageRenderer
import com.astral.ebook.ui.theme.AstralEbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bodyUriStr = intent.getStringExtra("bodyUri")
        val coverUriStr = intent.getStringExtra("coverUri")
        val bodyUri = bodyUriStr?.let { Uri.parse(it) }
        val coverUri = coverUriStr?.let { Uri.parse(it) }

        setContent {
            var settings by remember { mutableStateOf<EbookSettings?>(null) }
            var pages by remember { mutableStateOf<List<PageContent>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            val context = LocalContext.current

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val store = SettingsStore(context)
                    val intentSettingsBundle = intent.getBundleExtra("settings")
                    val savedSettings = intentSettingsBundle?.toEbookSettings() ?: store.settings.first()

                    val meta = savedSettings.metadata
                    val dummyMetadata = meta.copy(
                        title = meta.title.ifBlank { "Judul Buku Dummy" },
                        subtitle = meta.subtitle.ifBlank { "Subjudul Naskah Contoh" },
                        chapter = meta.chapter.ifBlank { "Bab 1: Permulaan" },
                        author = meta.author.ifBlank { "Nama Penulis Dummy" },
                        translator = meta.translator.ifBlank { "Nama Penerjemah Dummy" },
                        publisher = meta.publisher.ifBlank { "Penerbit Astral Press" },
                        publicationYear = meta.publicationYear.ifBlank { "2025" },
                        notes = meta.notes.ifBlank { "Catatan dummy untuk naskah contoh pratinjau." },
                        language = meta.language.ifBlank { "Indonesia" }
                    )
                    val previewSettings = savedSettings.copy(metadata = dummyMetadata)
                    settings = previewSettings

                    val dummyParagraphs = listOf(
                        com.astral.ebook.repository.FormattedParagraph(
                            runs = listOf(
                                com.astral.ebook.repository.TextRun(
                                    text = "Ini adalah paragraf pertama naskah contoh dummy. Halaman ini digunakan untuk melihat bagaimana layout, tipografi, dan gaya formatting dokumen Anda akan ditampilkan dalam bentuk e-book.",
                                    bold = false,
                                    italic = false,
                                    underline = false,
                                    strikeThrough = false
                                )
                            )
                        ),
                        com.astral.ebook.repository.FormattedParagraph(
                            runs = listOf(
                                com.astral.ebook.repository.TextRun(
                                    text = "Anda dapat mengatur gaya teks seperti ",
                                    bold = false,
                                    italic = false,
                                    underline = false,
                                    strikeThrough = false
                                ),
                                com.astral.ebook.repository.TextRun(
                                    text = "tebal",
                                    bold = true,
                                    italic = false,
                                    underline = false,
                                    strikeThrough = false
                                ),
                                com.astral.ebook.repository.TextRun(
                                    text = ", ",
                                    bold = false,
                                    italic = false,
                                    underline = false,
                                    strikeThrough = false
                                ),
                                com.astral.ebook.repository.TextRun(
                                    text = "miring",
                                    bold = false,
                                    italic = true,
                                    underline = false,
                                    strikeThrough = false
                                ),
                                com.astral.ebook.repository.TextRun(
                                    text = ", dan ",
                                    bold = false,
                                    italic = false,
                                    underline = false,
                                    strikeThrough = false
                                ),
                                com.astral.ebook.repository.TextRun(
                                    text = "garis bawah",
                                    bold = false,
                                    italic = false,
                                    underline = true,
                                    strikeThrough = false
                                ),
                                com.astral.ebook.repository.TextRun(
                                    text = " pada visual editor.",
                                    bold = false,
                                    italic = false,
                                    underline = false,
                                    strikeThrough = false
                                )
                            )
                        )
                    )

                    var docContent: com.astral.ebook.repository.DocumentContent? = null
                    if (bodyUri != null) {
                        try {
                            val parsed = DocumentParser.readBody(context, bodyUri)
                            if (parsed.paragraphs.isNotEmpty() && parsed.paragraphs.any { p -> p.runs.any { r -> r.text.isNotBlank() } }) {
                                docContent = parsed
                            }
                        } catch (_: Exception) {}
                    }

                    val finalContent = docContent ?: com.astral.ebook.repository.DocumentContent(dummyParagraphs)
                    val engine = EbookLayoutEngine(context, previewSettings)
                    pages = engine.layoutPages(finalContent)
                    isLoading = false
                }
            }

            AstralEbookTheme(useDarkTheme = settings?.themeOptions?.useDark ?: false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F172A)
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (settings != null) {
                            PreviewPager(settings!!, pages, coverUri)
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                                }
                                Text(
                                    text = "Pratinjau Ebook",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewPager(settings: EbookSettings, pages: List<PageContent>, coverUri: Uri?) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val context = LocalContext.current
    val engine = remember(settings) { EbookLayoutEngine(context, settings) }
    val renderer = remember(engine) { PageRenderer(context, settings, engine) }

    val (pageWidth, pageHeight) = if (settings.orientation == Orientation.Portrait) {
        settings.pagePreset.widthPx to settings.pagePreset.heightPx
    } else {
        settings.pagePreset.heightPx to settings.pagePreset.widthPx
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { pageIndex ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val page = pages[pageIndex]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 12.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .aspectRatio(pageWidth.toFloat() / pageHeight.toFloat())
                            .fillMaxSize()
                            .background(settings.themeOptions.pageBackground)
                    ) {
                        val scale = size.width / pageWidth.toFloat()
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.save()
                            canvas.nativeCanvas.scale(scale, scale)
                            renderer.drawPage(canvas.nativeCanvas, page, pageWidth, pageHeight, coverUri)
                            canvas.nativeCanvas.restore()
                        }
                    }
                }
            }
        }

        if (pages.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${pages.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
