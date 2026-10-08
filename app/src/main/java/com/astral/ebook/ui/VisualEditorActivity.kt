package com.astral.ebook.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.model.toEbookSettings
import com.astral.ebook.repository.openImageStream
import com.astral.ebook.ui.theme.AstralEbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HTML_P_ALIGN_REGEX = Regex(
    "^<(?:p|div)\\s+(?:align=\"([a-zA-Z]+)\"|style=\"[^\"]*text-align:\\s*([a-zA-Z]+)[^\"]*\")\\s*>(.*)</(?:p|div)>$",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val CENTER_TAG_REGEX = Regex(
    "^<center>(.*)</center>$",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val IMG_TAG_REGEX = Regex(
    "<img\\s+[^>]*src=[\"']([^\"']+)[\"'][^>]*>",
    RegexOption.IGNORE_CASE
)

private enum class TagType {
    BOLD_OPEN, BOLD_CLOSE,
    ITALIC_OPEN, ITALIC_CLOSE,
    UNDERLINE_OPEN, UNDERLINE_CLOSE,
    STRIKE_OPEN, STRIKE_CLOSE,
    IMG
}

private data class ValidTagInfo(
    val range: IntRange,
    val type: TagType,
    val imgUri: String? = null
)

private fun findValidMatchedTags(text: String): Map<Int, ValidTagInfo> {
    data class CandidateTag(
        val range: IntRange,
        val type: TagType,
        val imgUri: String? = null
    )

    val candidates = mutableListOf<CandidateTag>()
    var i = 0
    val N = text.length

    while (i < N) {
        if (text[i] == '\\') {
            val escaped = text.getOrNull(i + 1)
            if (escaped != null && escaped in setOf('<', '\\')) {
                i += 2
                continue
            }
        }
        if (text[i] == '<') {
            val endAngle = text.indexOf('>', i)
            val nextStartAngle = text.indexOf('<', i + 1)
            val hasValidEnd = endAngle != -1 && (nextStartAngle == -1 || endAngle < nextStartAngle)

            if (hasValidEnd) {
                val tagStr = text.substring(i, endAngle + 1)
                val tagRange = i..endAngle

                if (text.regionMatches(i, "<img", 0, 4, ignoreCase = true)) {
                    val imgMatch = IMG_TAG_REGEX.find(text, i)
                    if (imgMatch != null && imgMatch.range.first == i) {
                        candidates.add(CandidateTag(imgMatch.range, TagType.IMG, imgMatch.groupValues[1]))
                        i = imgMatch.range.last + 1
                        continue
                    }
                }

                val type = when {
                    tagStr.equals("<b>", ignoreCase = true) || tagStr.equals("<strong>", ignoreCase = true) -> TagType.BOLD_OPEN
                    tagStr.equals("</b>", ignoreCase = true) || tagStr.equals("</strong>", ignoreCase = true) -> TagType.BOLD_CLOSE
                    tagStr.equals("<i>", ignoreCase = true) || tagStr.equals("<em>", ignoreCase = true) -> TagType.ITALIC_OPEN
                    tagStr.equals("</i>", ignoreCase = true) || tagStr.equals("</em>", ignoreCase = true) -> TagType.ITALIC_CLOSE
                    tagStr.equals("<u>", ignoreCase = true) -> TagType.UNDERLINE_OPEN
                    tagStr.equals("</u>", ignoreCase = true) -> TagType.UNDERLINE_CLOSE
                    tagStr.equals("<s>", ignoreCase = true) || tagStr.equals("<del>", ignoreCase = true) || tagStr.equals("<strike>", ignoreCase = true) -> TagType.STRIKE_OPEN
                    tagStr.equals("</s>", ignoreCase = true) || tagStr.equals("</del>", ignoreCase = true) || tagStr.equals("</strike>", ignoreCase = true) -> TagType.STRIKE_CLOSE
                    else -> null
                }

                if (type != null) {
                    candidates.add(CandidateTag(tagRange, type))
                }
                i = endAngle + 1
                continue
            }
        }
        i++
    }

    val resultMap = mutableMapOf<Int, ValidTagInfo>()

    fun matchCategory(openType: TagType, closeType: TagType) {
        val stack = java.util.ArrayDeque<CandidateTag>()
        for (c in candidates.filter { it.type == openType || it.type == closeType }) {
            if (c.type == openType) {
                stack.push(c)
            } else if (c.type == closeType) {
                if (stack.isNotEmpty()) {
                    val openCandidate = stack.pop()
                    resultMap[openCandidate.range.first] = ValidTagInfo(openCandidate.range, openCandidate.type)
                    resultMap[c.range.first] = ValidTagInfo(c.range, c.type)
                }
            }
        }
    }

    matchCategory(TagType.BOLD_OPEN, TagType.BOLD_CLOSE)
    matchCategory(TagType.ITALIC_OPEN, TagType.ITALIC_CLOSE)
    matchCategory(TagType.UNDERLINE_OPEN, TagType.UNDERLINE_CLOSE)
    matchCategory(TagType.STRIKE_OPEN, TagType.STRIKE_CLOSE)

    candidates.filter { it.type == TagType.IMG }.forEach {
        resultMap[it.range.first] = ValidTagInfo(it.range, it.type, it.imgUri)
    }

    return resultMap
}

class MarkupVisualTransformation(
    private val settings: EbookSettings = EbookSettings()
) : VisualTransformation {
    private var cachedText: String? = null
    private var cachedTransformed: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original == cachedText && cachedTransformed != null) {
            return cachedTransformed!!
        }

        val result = transform(original)
        cachedText = original
        cachedTransformed = result
        return result
    }

    private fun transform(original: String): TransformedText {
        val N = original.length
        val builder = AnnotatedString.Builder()
        val origToTrans = IntArray(N + 1)
        val transToOrig = IntArray(N + 1)

        val defaultAlignment = when (settings.paragraphOptions.alignment) {
            ParagraphAlignment.Left, ParagraphAlignment.Justify -> TextAlign.Left
            ParagraphAlignment.Center -> TextAlign.Center
            ParagraphAlignment.Right -> TextAlign.Right
        }

        val defaultAllowsIndent = defaultAlignment == TextAlign.Left
        val defaultIndentEm = settings.paragraphOptions.firstLineIndentEm
        val defaultHasIndent = defaultAllowsIndent && defaultIndentEm > 0f

        var currentParagraphStart = 0
        var paragraphIndex = 0

        while (currentParagraphStart <= N) {
            var nextNewline = original.indexOf('\n', currentParagraphStart)
            if (nextNewline == -1) {
                nextNewline = N
            }

            val pText = original.substring(currentParagraphStart, nextNewline)
            val pLen = pText.length
            val pEnd = currentParagraphStart + pLen

            val hasTagOrEscape = pText.contains('<') || pText.contains('\\')

            if (!hasTagOrEscape) {
                val pTransStart = builder.length
                builder.append(pText)

                for (i in 0 until pLen) {
                    val origIdx = currentParagraphStart + i
                    val transIdx = pTransStart + i
                    origToTrans[origIdx] = transIdx
                    transToOrig[transIdx] = origIdx
                }

                if (pEnd < N) {
                    val transIdx = builder.length
                    builder.append('\n')
                    transToOrig[transIdx] = pEnd
                    origToTrans[pEnd] = transIdx
                }

                val pTransEndForPara = builder.length

                val shouldSkipIndent = paragraphIndex == 0 && settings.paragraphOptions.skipIndentAfterHeading && defaultHasIndent

                if (shouldSkipIndent) {
                    builder.addStyle(
                        ParagraphStyle(
                            textAlign = defaultAlignment,
                            textIndent = TextIndent.None
                        ),
                        pTransStart,
                        pTransEndForPara
                    )
                }
            } else {
                var working = pText
                var alignment: TextAlign? = null
                var tagStartLen = 0
                var tagEndLen = 0

                if (working.startsWith('<')) {
                    val htmlPMatch = HTML_P_ALIGN_REGEX.find(working)
                    if (htmlPMatch != null) {
                        val alignStr = htmlPMatch.groupValues[1].ifEmpty { htmlPMatch.groupValues[2] }.lowercase()
                        alignment = parseTextAlign(alignStr)
                        val contentGroup = htmlPMatch.groups[3]
                        if (contentGroup != null) {
                            tagStartLen = contentGroup.range.first
                            tagEndLen = working.length - (contentGroup.range.last + 1)
                            working = contentGroup.value
                        } else {
                            working = htmlPMatch.groupValues[3]
                        }
                    } else {
                        val centerMatch = CENTER_TAG_REGEX.find(working)
                        if (centerMatch != null) {
                            alignment = TextAlign.Center
                            tagStartLen = 8
                            tagEndLen = 9
                            working = centerMatch.groupValues[1]
                        }
                    }
                }

                val pTransStart = builder.length

                for (origIdx in currentParagraphStart until (currentParagraphStart + tagStartLen)) {
                    origToTrans[origIdx] = pTransStart
                }

                val validTagsMap = findValidMatchedTags(working)

                var boldStart: Int? = null
                var italicStart: Int? = null
                var underlineStart: Int? = null
                var strikeStart: Int? = null

                var i = 0
                while (i < working.length) {
                    val origIdx = currentParagraphStart + tagStartLen + i
                    val ch = working[i]

                    if (ch != '<' && ch != '\\') {
                        val transIdx = builder.length
                        builder.append(ch)
                        transToOrig[transIdx] = origIdx
                        origToTrans[origIdx] = transIdx
                        i++
                        continue
                    }

                    if (ch == '\\') {
                        val escaped = working.getOrNull(i + 1)
                        if (escaped != null && escaped in setOf('<', '\\')) {
                            origToTrans[origIdx] = builder.length
                            val transIdx = builder.length
                            builder.append(escaped)
                            transToOrig[transIdx] = origIdx + 1
                            origToTrans[origIdx + 1] = transIdx
                            i += 2
                            continue
                        }
                    }

                    val validTag = validTagsMap[i]
                    if (validTag != null) {
                        val matchLen = validTag.range.last - validTag.range.first + 1
                        for (k in 0 until matchLen) {
                            origToTrans[origIdx + k] = builder.length
                        }

                        when (validTag.type) {
                            TagType.IMG -> {
                                val placeholder = "🖼️ [Gambar]"
                                val imgTransStart = builder.length
                                builder.append(placeholder)
                                builder.addStyle(SpanStyle(color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold), imgTransStart, builder.length)
                                for (k in 0 until placeholder.length) {
                                    transToOrig[imgTransStart + k] = origIdx
                                }
                            }
                            TagType.BOLD_OPEN -> {
                                if (boldStart == null) boldStart = builder.length
                            }
                            TagType.BOLD_CLOSE -> {
                                boldStart?.let {
                                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), it, builder.length)
                                    boldStart = null
                                }
                            }
                            TagType.ITALIC_OPEN -> {
                                if (italicStart == null) italicStart = builder.length
                            }
                            TagType.ITALIC_CLOSE -> {
                                italicStart?.let {
                                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), it, builder.length)
                                    italicStart = null
                                }
                            }
                            TagType.UNDERLINE_OPEN -> {
                                if (underlineStart == null) underlineStart = builder.length
                            }
                            TagType.UNDERLINE_CLOSE -> {
                                underlineStart?.let {
                                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it, builder.length)
                                    underlineStart = null
                                }
                            }
                            TagType.STRIKE_OPEN -> {
                                if (strikeStart == null) strikeStart = builder.length
                            }
                            TagType.STRIKE_CLOSE -> {
                                strikeStart?.let {
                                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), it, builder.length)
                                    strikeStart = null
                                }
                            }
                        }
                        i += matchLen
                        continue
                    }

                    val endAngle = working.indexOf('>', i)
                    val nextStartAngle = working.indexOf('<', i + 1)
                    val hasValidEnd = endAngle != -1 && (nextStartAngle == -1 || endAngle < nextStartAngle)
                    val invalidLen = if (hasValidEnd) {
                        endAngle - i + 1
                    } else {
                        val fragmentMatch = Regex("^</?[a-zA-Z0-9_/-]+(?:\\s+[a-zA-Z0-9_/-]+=(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))*", RegexOption.IGNORE_CASE).find(working.substring(i))
                        fragmentMatch?.value?.length ?: 1
                    }

                    for (k in 0 until invalidLen) {
                        origToTrans[origIdx + k] = builder.length
                    }
                    i += invalidLen
                }

                val pTransEnd = builder.length

                for (origIdx in (pEnd - tagEndLen) until pEnd) {
                    origToTrans[origIdx] = pTransEnd
                }

                if (pEnd < N) {
                    val transIdx = builder.length
                    builder.append('\n')
                    transToOrig[transIdx] = pEnd
                    origToTrans[pEnd] = transIdx
                }

                val pTransEndForPara = builder.length

                val actualAlignment = alignment ?: defaultAlignment
                val allowsIndent = actualAlignment == TextAlign.Left
                val applyIndent = !(paragraphIndex == 0 && settings.paragraphOptions.skipIndentAfterHeading) && allowsIndent

                val textIndent = if (applyIndent) {
                    if (defaultHasIndent) null else TextIndent(firstLine = defaultIndentEm.em)
                } else {
                    if (defaultHasIndent) TextIndent.None else null
                }

                val isDifferentAlignment = alignment != null && alignment != defaultAlignment
                val isDifferentIndent = textIndent != null

                if (isDifferentAlignment || isDifferentIndent) {
                    builder.addStyle(
                        ParagraphStyle(
                            textAlign = alignment ?: defaultAlignment,
                            textIndent = textIndent ?: if (allowsIndent && defaultHasIndent) TextIndent(firstLine = defaultIndentEm.em) else TextIndent.None
                        ),
                        pTransStart,
                        pTransEndForPara
                    )
                }
            }

            currentParagraphStart = pEnd + 1
            paragraphIndex++
        }

        origToTrans[N] = builder.length
        transToOrig[builder.length] = N

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, N)
                return origToTrans[clamped]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, builder.length)
                return transToOrig[clamped]
            }
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }

    private fun parseTextAlign(str: String): TextAlign? = when (str.lowercase()) {
        "left", "justify" -> TextAlign.Left
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> null
    }
}

class VisualEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialContent = intent.getStringExtra("content") ?: ""
        val settingsBundle = intent.getBundleExtra("settings")
        val settings = settingsBundle?.toEbookSettings() ?: EbookSettings()

        setContent {
            AstralEbookTheme(useDarkTheme = settings.themeOptions.useDark ?: false) {
                VisualEditorScreen(
                    initialContent = initialContent,
                    settings = settings,
                    onSave = { content ->
                        val data = Intent().apply {
                            putExtra("content", content)
                        }
                        setResult(RESULT_OK, data)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualEditorScreen(
    initialContent: String,
    settings: EbookSettings = EbookSettings(),
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var textFieldValue by remember {
        val initialCleaned = com.astral.ebook.repository.stripIncompleteHtml(initialContent)
        mutableStateOf(TextFieldValue(initialCleaned, TextRange(initialCleaned.length)))
    }
    var isCodeMode by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val matches = remember(textFieldValue.text, searchQuery) {
        if (searchQuery.isEmpty()) {
            emptyList()
        } else {
            try {
                Regex.escape(searchQuery).toRegex(RegexOption.IGNORE_CASE)
                    .findAll(textFieldValue.text)
                    .map { it.range }
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun highlightMatch(index: Int) {
        if (matches.isNotEmpty() && index in matches.indices) {
            val range = matches[index]
            textFieldValue = textFieldValue.copy(
                selection = TextRange(range.first, range.last + 1)
            )
        }
    }

    LaunchedEffect(matches, currentMatchIndex) {
        if (matches.isNotEmpty()) {
            val safeIdx = currentMatchIndex.coerceIn(0, matches.lastIndex)
            if (safeIdx != currentMatchIndex) {
                currentMatchIndex = safeIdx
            }
            highlightMatch(safeIdx)
        }
    }

    fun scrollToTop() {
        coroutineScope.launch {
            scrollState.animateScrollTo(0)
        }
        textFieldValue = textFieldValue.copy(selection = TextRange(0))
    }

    fun scrollToBottom() {
        coroutineScope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val start = textFieldValue.selection.min
            val end = textFieldValue.selection.max
            val text = textFieldValue.text
            val imgTag = "<img src=\"$it\"/>"
            val newText = text.substring(0, start) + imgTag + text.substring(end)
            val newSelection = TextRange(start + imgTag.length)
            textFieldValue = TextFieldValue(newText, newSelection)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isCodeMode) Icons.Default.Code else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { scrollToTop() }) {
                        Icon(Icons.Default.VerticalAlignTop, contentDescription = "Ke Atas")
                    }
                    IconButton(onClick = { scrollToBottom() }) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = "Ke Bawah")
                    }
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(Icons.Default.FindReplace, contentDescription = "Cari & Ganti")
                    }
                    IconButton(onClick = {
                        val cleaned = com.astral.ebook.repository.stripIncompleteHtml(textFieldValue.text)
                        if (cleaned != textFieldValue.text) {
                            val newStart = textFieldValue.selection.start.coerceAtMost(cleaned.length)
                            val newEnd = textFieldValue.selection.end.coerceAtMost(cleaned.length)
                            textFieldValue = textFieldValue.copy(
                                text = cleaned,
                                selection = TextRange(newStart, newEnd)
                            )
                        }
                        isCodeMode = !isCodeMode
                    }) {
                        Icon(
                            imageVector = if (isCodeMode) Icons.Default.Visibility else Icons.Default.Code,
                            contentDescription = if (isCodeMode) "Mode Visual" else "Mode Kode"
                        )
                    }
                    IconButton(onClick = {
                        val cleaned = com.astral.ebook.repository.stripIncompleteHtml(textFieldValue.text)
                        onSave(cleaned)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Simpan", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            AnimatedVisibility(visible = isSearchVisible) {
                FindReplaceBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = {
                        searchQuery = it
                        currentMatchIndex = 0
                    },
                    replaceQuery = replaceQuery,
                    onReplaceQueryChange = { replaceQuery = it },
                    matchCount = matches.size,
                    currentMatchIndex = currentMatchIndex,
                    onNextMatch = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % matches.size
                        }
                    },
                    onPrevMatch = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = if (currentMatchIndex - 1 < 0) matches.lastIndex else currentMatchIndex - 1
                        }
                    },
                    onReplace = {
                        if (matches.isNotEmpty() && currentMatchIndex in matches.indices) {
                            val range = matches[currentMatchIndex]
                            val origText = textFieldValue.text
                            val newText = origText.substring(0, range.first) + replaceQuery + origText.substring(range.last + 1)
                            val newSelectionStart = (range.first + replaceQuery.length).coerceAtMost(newText.length)
                            textFieldValue = TextFieldValue(newText, TextRange(newSelectionStart))
                        }
                    },
                    onReplaceAll = {
                        if (searchQuery.isNotEmpty() && matches.isNotEmpty()) {
                            val newText = textFieldValue.text.replace(searchQuery, replaceQuery, ignoreCase = true)
                            textFieldValue = TextFieldValue(newText, TextRange(newText.length.coerceAtMost(textFieldValue.selection.start)))
                            currentMatchIndex = 0
                        }
                    },
                    onClose = { isSearchVisible = false }
                )
            }

            FormattingToolbar(
                onApplyFormatting = { prefix, suffix ->
                    val start = textFieldValue.selection.min
                    val end = textFieldValue.selection.max
                    val text = textFieldValue.text
                    val selectedText = text.substring(start, end)

                    val newText = text.substring(0, start) + prefix + selectedText + suffix + text.substring(end)
                    val newSelection = TextRange(start + prefix.length, start + prefix.length + selectedText.length)
                    textFieldValue = TextFieldValue(newText, newSelection)
                },
                onSetAlignment = { align ->
                    val start = textFieldValue.selection.min
                    val end = textFieldValue.selection.max
                    val text = textFieldValue.text
                    val tag = align.name.lowercase()

                    var paraStart = start
                    while (paraStart > 0 && text[paraStart - 1] != '\n') {
                        paraStart--
                    }
                    var paraEnd = end
                    while (paraEnd < text.length && text[paraEnd] != '\n') {
                        paraEnd++
                    }

                    val paraText = text.substring(paraStart, paraEnd)

                    val htmlPAlign = Regex("^<(?:p|div)\\s+(?:align=\"([a-zA-Z]+)\"|style=\"[^\"]*text-align:\\s*([a-zA-Z]+)[^\"]*\")\\s*>(.*)</(?:p|div)>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val centerTag = Regex("^<center>(.*)</center>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

                    var workingPara = paraText.trim()
                    var existingAlign: String? = null

                    var matched = true
                    while (matched) {
                        val htmlPMatch = htmlPAlign.find(workingPara)
                        if (htmlPMatch != null) {
                            existingAlign = htmlPMatch.groupValues[1].ifEmpty { htmlPMatch.groupValues[2] }.lowercase()
                            workingPara = htmlPMatch.groupValues[3].trim()
                            continue
                        }
                        val centerMatch = centerTag.find(workingPara)
                        if (centerMatch != null) {
                            existingAlign = "center"
                            workingPara = centerMatch.groupValues[1].trim()
                            continue
                        }
                        matched = false
                    }

                    val newParaText = if (existingAlign == tag) {
                        workingPara
                    } else {
                        "<p align=\"$tag\">$workingPara</p>"
                    }

                    val newText = text.substring(0, paraStart) + newParaText + text.substring(paraEnd)
                    val newSelection = TextRange(paraStart, paraStart + newParaText.length)
                    textFieldValue = TextFieldValue(newText, newSelection)
                },
                onAddImage = {
                    imagePickerLauncher.launch(arrayOf("image/*"))
                }
            )

            val visualTransform = remember(settings) { MarkupVisualTransformation(settings) }

            val defaultAlignment = when (settings.paragraphOptions.alignment) {
                ParagraphAlignment.Left, ParagraphAlignment.Justify -> TextAlign.Left
                ParagraphAlignment.Center -> TextAlign.Center
                ParagraphAlignment.Right -> TextAlign.Right
            }
            val defaultAllowsIndent = defaultAlignment == TextAlign.Left
            val defaultIndentEm = settings.paragraphOptions.firstLineIndentEm
            val defaultTextIndent = if (defaultAllowsIndent && defaultIndentEm > 0f) {
                TextIndent(firstLine = defaultIndentEm.em)
            } else {
                null
            }

            val editorTextStyle = LocalTextStyle.current.copy(
                textAlign = defaultAlignment,
                textIndent = defaultTextIndent,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )

            val imageUris = remember(textFieldValue.text) {
                IMG_TAG_REGEX.findAll(textFieldValue.text).map { it.groupValues[1] }.distinct().toList()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                if (!isCodeMode && imageUris.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Gambar dalam Dokumen (${imageUris.size}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        imageUris.forEach { uriStr ->
                            ImagePreviewCard(
                                uriString = uriStr,
                                onDelete = {
                                    val tagRegex = Regex("<img\\s+[^>]*src=[\"']${Regex.escape(uriStr)}[\"'][^>]*>", RegexOption.IGNORE_CASE)
                                    val newText = textFieldValue.text.replace(tagRegex, "")
                                    textFieldValue = TextFieldValue(newText, TextRange(textFieldValue.selection.start.coerceAtMost(newText.length)))
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = textFieldValue,
                    onValueChange = { incoming ->
                        val insertedLength = incoming.text.length - textFieldValue.text.length
                        if (insertedLength > 1) {
                            val clipboardHtml = getClipboardHtml(context)
                            if (!clipboardHtml.isNullOrBlank()) {
                                val selectionLen = (textFieldValue.selection.max - textFieldValue.selection.min).coerceAtLeast(0)
                                val pasteStart = textFieldValue.selection.min.coerceIn(0, textFieldValue.text.length)
                                val pasteEndInOrig = (pasteStart + selectionLen).coerceIn(0, textFieldValue.text.length)
                                val newText = textFieldValue.text.substring(0, pasteStart) + clipboardHtml + textFieldValue.text.substring(pasteEndInOrig)
                                textFieldValue = TextFieldValue(newText, TextRange(pasteStart + clipboardHtml.length))
                                return@TextField
                            }
                        }
                        textFieldValue = incoming
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textStyle = if (isCodeMode) LocalTextStyle.current.copy(fontSize = 14.sp) else editorTextStyle,
                    visualTransformation = if (isCodeMode) VisualTransformation.None else visualTransform,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = { Text("Mulai menulis naskah Anda di sini...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
    }
}

@Composable
fun ImagePreviewCard(
    uriString: String,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                openImageStream(context, uriString)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        bitmap = decoded.asImageBitmap()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Preview Gambar",
                    modifier = Modifier
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .weight(1f, fill = false),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .weight(1f, fill = false)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[Gambar: ${uriString.takeLast(25)}]", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus Gambar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun FindReplaceBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Cari kata...", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp)
                )

                Text(
                    text = if (searchQuery.isEmpty()) "" else if (matchCount > 0) "${currentMatchIndex + 1}/$matchCount" else "0/0",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(onClick = onPrevMatch, enabled = matchCount > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Sebelumnya")
                }
                IconButton(onClick = onNextMatch, enabled = matchCount > 0) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Berikutnya")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ganti dengan...", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = onReplace,
                    enabled = matchCount > 0,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Ganti", fontSize = 12.sp)
                }

                Button(
                    onClick = onReplaceAll,
                    enabled = matchCount > 0,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Semua", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun getClipboardHtml(context: Context): String? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        val htmlText = item?.htmlText
        if (!htmlText.isNullOrBlank()) {
            val bodyMatch = Regex("<body[^>]*>(.*?)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(htmlText)
            val extracted = bodyMatch?.groupValues?.get(1)?.trim() ?: htmlText
            sanitizePastedHtml(extracted)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun sanitizePastedHtml(html: String): String {
    if (html.isBlank()) return ""

    var clean = html.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")

    clean = clean.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

    val tagRegex = Regex("</?([a-zA-Z1-6]+)(?:\\s+[^>]*)?>")
    val alignRegex = Regex("(?:align=[\"']?([a-zA-Z]+)[\"']?|style=[\"'][^\"']*text-align:\\s*([a-zA-Z]+))", RegexOption.IGNORE_CASE)
    val srcRegex = Regex("src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    val sb = StringBuilder()
    var lastIdx = 0
    val blockStack = java.util.ArrayDeque<Boolean>()

    for (match in tagRegex.findAll(clean)) {
        sb.append(clean.substring(lastIdx, match.range.first))
        lastIdx = match.range.last + 1

        val fullTag = match.value
        val tagName = match.groupValues[1].lowercase()
        val isClosing = fullTag.startsWith("</")

        if (isClosing) {
            when (tagName) {
                "b", "strong", "i", "em", "u", "s", "del", "strike" -> {
                    sb.append("</$tagName>")
                }
                "center" -> {
                    sb.append("</center>\n")
                }
                "p", "div" -> {
                    val isAligned = if (blockStack.isNotEmpty()) blockStack.pop() else false
                    if (isAligned) {
                        sb.append("</$tagName>\n")
                    } else {
                        sb.append("\n")
                    }
                }
                "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr" -> {
                    sb.append("\n")
                }
            }
        } else {
            when (tagName) {
                "b", "strong", "i", "em", "u", "s", "del", "strike" -> {
                    sb.append("<$tagName>")
                }
                "center" -> {
                    sb.append("<center>")
                }
                "img" -> {
                    val srcMatch = srcRegex.find(fullTag)
                    if (srcMatch != null) {
                        val src = srcMatch.groupValues[1]
                        sb.append("<img src=\"$src\"/>")
                    }
                }
                "p", "div" -> {
                    val alignMatch = alignRegex.find(fullTag)
                    val alignVal = alignMatch?.let { m ->
                        m.groupValues[1].ifEmpty { m.groupValues[2] }.lowercase()
                    }
                    if (alignVal != null && alignVal in setOf("left", "center", "right", "justify")) {
                        blockStack.push(true)
                        sb.append("<$tagName align=\"$alignVal\">")
                    } else {
                        blockStack.push(false)
                    }
                }
            }
        }
    }
    sb.append(clean.substring(lastIdx))

    return com.astral.ebook.repository.stripIncompleteHtml(
        sb.toString()
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    )
}

@Composable
fun FormattingToolbar(
    onApplyFormatting: (String, String) -> Unit,
    onSetAlignment: (ParagraphAlignment) -> Unit,
    onAddImage: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(Icons.Default.FormatBold, "Tebal") { onApplyFormatting("<b>", "</b>") }
            ToolbarButton(Icons.Default.FormatItalic, "Miring") { onApplyFormatting("<i>", "</i>") }
            ToolbarButton(Icons.Default.FormatUnderlined, "Garis Bawah") { onApplyFormatting("<u>", "</u>") }
            ToolbarButton(Icons.Default.FormatStrikethrough, "Coret") { onApplyFormatting("<s>", "</s>") }
            ToolbarButton(Icons.Default.Image, "Gambar") { onAddImage() }
            ToolbarButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "Kiri") { onSetAlignment(ParagraphAlignment.Left) }
            ToolbarButton(Icons.Default.FormatAlignCenter, "Tengah") { onSetAlignment(ParagraphAlignment.Center) }
            ToolbarButton(Icons.AutoMirrored.Filled.FormatAlignRight, "Kanan") { onSetAlignment(ParagraphAlignment.Right) }
            ToolbarButton(Icons.Default.FormatAlignJustify, "Rata Kanan Kiri") { onSetAlignment(ParagraphAlignment.Justify) }
        }
    }
}

@Composable
fun ToolbarButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}
