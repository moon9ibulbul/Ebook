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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
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
                    settings = savedSettings

                    if (bodyUri != null) {
                        try {
                            val content = DocumentParser.readBody(context, bodyUri)
                            val engine = EbookLayoutEngine(context, savedSettings)
                            pages = engine.layoutPages(content)
                        } catch (_: Exception) {}
                    } else {
                        // If no body, maybe just show cover/title?
                        val engine = EbookLayoutEngine(context, savedSettings)
                        pages = engine.layoutPages(com.astral.ebook.repository.DocumentContent(emptyList()))
                    }
                    isLoading = false
                }
            }

            AstralEbookTheme(useDarkTheme = settings?.themeOptions?.useDark ?: false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black.copy(alpha = 0.9f)
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (settings != null) {
                            PreviewPager(settings!!, pages, coverUri)
                        }

                        IconButton(
                            onClick = { finish() },
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
                Canvas(
                    modifier = Modifier
                        .padding(16.dp)
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
}
