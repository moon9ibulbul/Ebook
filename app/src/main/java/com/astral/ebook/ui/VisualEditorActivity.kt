package com.astral.ebook.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.model.toEbookSettings
import com.astral.ebook.ui.theme.AstralEbookTheme

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
        val transToOrigList = mutableListOf<Int>()

        val paragraphs = original.split('\n')
        var currentParagraphStart = 0

        val defaultAlignment = when (settings.paragraphOptions.alignment) {
            ParagraphAlignment.Left -> TextAlign.Left
            ParagraphAlignment.Center -> TextAlign.Center
            ParagraphAlignment.Right -> TextAlign.Right
            ParagraphAlignment.Justify -> TextAlign.Justify
        }

        val htmlPAlign = Regex("^<(?:p|div)\\s+(?:align=\"([a-zA-Z]+)\"|style=\"[^\"]*text-align:\\s*([a-zA-Z]+)[^\"]*\")\\s*>(.*)</(?:p|div)>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val centerTag = Regex("^<center>(.*)</center>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val imgRegex = Regex("^<img\\s+[^>]*src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)

        for ((index, pText) in paragraphs.withIndex()) {
            val pEnd = currentParagraphStart + pText.length
            var working = pText
            var alignment: TextAlign? = null
            var tagStartLen = 0
            var tagEndLen = 0

            val htmlPMatch = htmlPAlign.find(working)
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
                val centerMatch = centerTag.find(working)
                if (centerMatch != null) {
                    alignment = TextAlign.Center
                    tagStartLen = 8
                    tagEndLen = 9
                    working = centerMatch.groupValues[1]
                }
            }

            val pTransStart = builder.length

            for (origIdx in currentParagraphStart until (currentParagraphStart + tagStartLen)) {
                origToTrans[origIdx] = pTransStart
            }

            var boldStart: Int? = null
            var italicStart: Int? = null
            var underlineStart: Int? = null
            var strikeStart: Int? = null

            var i = 0
            while (i < working.length) {
                val origIdx = currentParagraphStart + tagStartLen + i

                if (working[i] == '\\') {
                    val escaped = working.getOrNull(i + 1)
                    if (escaped != null && escaped in setOf('<', '\\')) {
                        origToTrans[origIdx] = builder.length
                        val transIdx = builder.length
                        builder.append(escaped)
                        transToOrigList.add(origIdx + 1)
                        origToTrans[origIdx + 1] = transIdx
                        i += 2
                        continue
                    }
                }

                val imgMatch = imgRegex.find(working.substring(i))
                if (imgMatch != null && imgMatch.range.first == 0) {
                    val matchLen = imgMatch.value.length
                    for (k in 0 until matchLen) {
                        origToTrans[origIdx + k] = builder.length
                    }
                    val placeholder = "[Gambar]"
                    val imgTransStart = builder.length
                    builder.append(placeholder)
                    builder.addStyle(SpanStyle(color = Color(0xFF1976D2), fontWeight = FontWeight.Bold), imgTransStart, builder.length)
                    for (k in 0 until placeholder.length) {
                        transToOrigList.add(origIdx)
                    }
                    i += matchLen
                    continue
                }

                when {
                    // HTML tags
                    working.regionMatches(i, "<b>", 0, 3, ignoreCase = true) -> {
                        for (k in 0 until 3) origToTrans[origIdx + k] = builder.length
                        if (boldStart == null) boldStart = builder.length
                        i += 3
                    }
                    working.regionMatches(i, "<strong>", 0, 8, ignoreCase = true) -> {
                        for (k in 0 until 8) origToTrans[origIdx + k] = builder.length
                        if (boldStart == null) boldStart = builder.length
                        i += 8
                    }
                    working.regionMatches(i, "</b>", 0, 4, ignoreCase = true) -> {
                        for (k in 0 until 4) origToTrans[origIdx + k] = builder.length
                        boldStart?.let {
                            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), it, builder.length)
                            boldStart = null
                        }
                        i += 4
                    }
                    working.regionMatches(i, "</strong>", 0, 9, ignoreCase = true) -> {
                        for (k in 0 until 9) origToTrans[origIdx + k] = builder.length
                        boldStart?.let {
                            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), it, builder.length)
                            boldStart = null
                        }
                        i += 9
                    }
                    working.regionMatches(i, "<i>", 0, 3, ignoreCase = true) -> {
                        for (k in 0 until 3) origToTrans[origIdx + k] = builder.length
                        if (italicStart == null) italicStart = builder.length
                        i += 3
                    }
                    working.regionMatches(i, "<em>", 0, 4, ignoreCase = true) -> {
                        for (k in 0 until 4) origToTrans[origIdx + k] = builder.length
                        if (italicStart == null) italicStart = builder.length
                        i += 4
                    }
                    working.regionMatches(i, "</i>", 0, 4, ignoreCase = true) -> {
                        for (k in 0 until 4) origToTrans[origIdx + k] = builder.length
                        italicStart?.let {
                            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), it, builder.length)
                            italicStart = null
                        }
                        i += 4
                    }
                    working.regionMatches(i, "</em>", 0, 5, ignoreCase = true) -> {
                        for (k in 0 until 5) origToTrans[origIdx + k] = builder.length
                        italicStart?.let {
                            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), it, builder.length)
                            italicStart = null
                        }
                        i += 5
                    }
                    working.regionMatches(i, "<u>", 0, 3, ignoreCase = true) -> {
                        for (k in 0 until 3) origToTrans[origIdx + k] = builder.length
                        if (underlineStart == null) underlineStart = builder.length
                        i += 3
                    }
                    working.regionMatches(i, "</u>", 0, 4, ignoreCase = true) -> {
                        for (k in 0 until 4) origToTrans[origIdx + k] = builder.length
                        underlineStart?.let {
                            builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it, builder.length)
                            underlineStart = null
                        }
                        i += 4
                    }
                    working.regionMatches(i, "<s>", 0, 3, ignoreCase = true) ||
                    working.regionMatches(i, "<del>", 0, 5, ignoreCase = true) ||
                    working.regionMatches(i, "<strike>", 0, 8, ignoreCase = true) -> {
                        val len = when {
                            working.regionMatches(i, "<s>", 0, 3, ignoreCase = true) -> 3
                            working.regionMatches(i, "<del>", 0, 5, ignoreCase = true) -> 5
                            else -> 8
                        }
                        for (k in 0 until len) origToTrans[origIdx + k] = builder.length
                        if (strikeStart == null) strikeStart = builder.length
                        i += len
                    }
                    working.regionMatches(i, "</s>", 0, 4, ignoreCase = true) ||
                    working.regionMatches(i, "</del>", 0, 6, ignoreCase = true) ||
                    working.regionMatches(i, "</strike>", 0, 9, ignoreCase = true) -> {
                        val len = when {
                            working.regionMatches(i, "</s>", 0, 4, ignoreCase = true) -> 4
                            working.regionMatches(i, "</del>", 0, 6, ignoreCase = true) -> 6
                            else -> 9
                        }
                        for (k in 0 until len) origToTrans[origIdx + k] = builder.length
                        strikeStart?.let {
                            builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), it, builder.length)
                            strikeStart = null
                        }
                        i += len
                    }
                    else -> {
                        val transIdx = builder.length
                        builder.append(working[i])
                        transToOrigList.add(origIdx)
                        origToTrans[origIdx] = transIdx
                        i++
                    }
                }
            }

            val pTransEnd = builder.length
            boldStart?.let { builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), it, pTransEnd) }
            italicStart?.let { builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), it, pTransEnd) }
            underlineStart?.let { builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it, pTransEnd) }
            strikeStart?.let { builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), it, pTransEnd) }

            for (origIdx in (pEnd - tagEndLen) until pEnd) {
                origToTrans[origIdx] = pTransEnd
            }

            if (pEnd < N) {
                val transIdx = builder.length
                builder.append('\n')
                transToOrigList.add(pEnd)
                origToTrans[pEnd] = transIdx
            }

            val pTransEndForPara = builder.length

            val actualAlignment = alignment ?: defaultAlignment
            val allowsIndent = actualAlignment == TextAlign.Left || actualAlignment == TextAlign.Justify
            val applyIndent = !(index == 0 && settings.paragraphOptions.skipIndentAfterHeading) && allowsIndent

            val textIndent = if (applyIndent) {
                TextIndent(firstLine = settings.paragraphOptions.firstLineIndentEm.em)
            } else {
                null
            }

            if (alignment != null || textIndent != null) {
                builder.addStyle(
                    ParagraphStyle(
                        textAlign = alignment ?: TextAlign.Unspecified,
                        textIndent = textIndent
                    ),
                    pTransStart,
                    pTransEndForPara
                )
            }

            currentParagraphStart = pEnd + 1
        }

        origToTrans[N] = builder.length
        transToOrigList.add(N)
        val transToOrigArray = transToOrigList.toIntArray()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, N)
                return origToTrans[clamped]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, builder.length)
                return transToOrigArray[clamped]
            }
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }

    private fun parseTextAlign(str: String): TextAlign? = when (str.lowercase()) {
        "left" -> TextAlign.Left
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        "justify" -> TextAlign.Justify
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
            AstralEbookTheme {
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
        mutableStateOf(TextFieldValue(initialContent, TextRange(initialContent.length)))
    }
    var isCodeMode by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
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
                title = { Text(if (isCodeMode) "Code Editor" else "Visual Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isCodeMode = !isCodeMode }) {
                        Icon(
                            imageVector = if (isCodeMode) Icons.Default.Visibility else Icons.Default.Code,
                            contentDescription = if (isCodeMode) "Visual Mode" else "Code Mode"
                        )
                    }
                    IconButton(onClick = { onSave(textFieldValue.text) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
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
                    imagePickerLauncher.launch("image/*")
                }
            )

            val visualTransform = remember(settings) { MarkupVisualTransformation(settings) }

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
                    .weight(1f),
                visualTransformation = if (isCodeMode) VisualTransformation.None else visualTransform,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                placeholder = { Text("Mulai menulis...") }
            )
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

    return sb.toString()
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

@Composable
fun FormattingToolbar(
    onApplyFormatting: (String, String) -> Unit,
    onSetAlignment: (ParagraphAlignment) -> Unit,
    onAddImage: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarButton(Icons.Default.FormatBold, "Bold") { onApplyFormatting("<b>", "</b>") }
            ToolbarButton(Icons.Default.FormatItalic, "Italic") { onApplyFormatting("<i>", "</i>") }
            ToolbarButton(Icons.Default.FormatUnderlined, "Underline") { onApplyFormatting("<u>", "</u>") }
            ToolbarButton(Icons.Default.FormatStrikethrough, "Strikethrough") { onApplyFormatting("<s>", "</s>") }
            ToolbarButton(Icons.Default.Image, "Image") { onAddImage() }
            ToolbarButton(Icons.Default.FormatAlignLeft, "Left") { onSetAlignment(ParagraphAlignment.Left) }
            ToolbarButton(Icons.Default.FormatAlignCenter, "Center") { onSetAlignment(ParagraphAlignment.Center) }
            ToolbarButton(Icons.Default.FormatAlignRight, "Right") { onSetAlignment(ParagraphAlignment.Right) }
            ToolbarButton(Icons.Default.FormatAlignJustify, "Justify") { onSetAlignment(ParagraphAlignment.Justify) }
        }
    }
}

@Composable
fun ToolbarButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription)
    }
}
