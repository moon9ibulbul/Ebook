package com.astral.ebook.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.ui.theme.AstralEbookTheme

class MarkupVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val N = original.length
        val builder = AnnotatedString.Builder()
        val origToTrans = IntArray(N + 1)
        val transToOrigList = mutableListOf<Int>()

        val paragraphs = original.split('\n')
        var currentParagraphStart = 0

        for (pText in paragraphs) {
            val pEnd = currentParagraphStart + pText.length
            var working = pText
            var alignment: TextAlign? = null
            var tagStartLen = 0
            var tagEndLen = 0

            val bracketAlign = Regex("^\\[(left|right|center|justify)](.*)\\[/\\1]$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val bracketMatch = bracketAlign.find(working)
            if (bracketMatch != null) {
                val alignStr = bracketMatch.groupValues[1].lowercase()
                alignment = when (alignStr) {
                    "left" -> TextAlign.Left
                    "right" -> TextAlign.Right
                    "center" -> TextAlign.Center
                    "justify" -> TextAlign.Justify
                    else -> null
                }
                tagStartLen = alignStr.length + 2
                tagEndLen = alignStr.length + 3
                working = bracketMatch.groupValues[2]
            } else {
                val attrAlign = Regex("^\\[align=(left|right|center|justify)](.*)\\[/align]$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val attrMatch = attrAlign.find(working)
                if (attrMatch != null) {
                    val alignStr = attrMatch.groupValues[1].lowercase()
                    alignment = when (alignStr) {
                        "left" -> TextAlign.Left
                        "right" -> TextAlign.Right
                        "center" -> TextAlign.Center
                        "justify" -> TextAlign.Justify
                        else -> null
                    }
                    tagStartLen = alignStr.length + 8
                    tagEndLen = 8
                    working = attrMatch.groupValues[2]
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

            fun toggleBold(transIdx: Int) {
                if (boldStart == null) {
                    boldStart = transIdx
                } else {
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStart!!, transIdx)
                    boldStart = null
                }
            }

            fun toggleItalic(transIdx: Int) {
                if (italicStart == null) {
                    italicStart = transIdx
                } else {
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart!!, transIdx)
                    italicStart = null
                }
            }

            fun toggleUnderline(transIdx: Int) {
                if (underlineStart == null) {
                    underlineStart = transIdx
                } else {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStart!!, transIdx)
                    underlineStart = null
                }
            }

            fun toggleStrike(transIdx: Int) {
                if (strikeStart == null) {
                    strikeStart = transIdx
                } else {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), strikeStart!!, transIdx)
                    strikeStart = null
                }
            }

            var i = 0
            while (i < working.length) {
                val origIdx = currentParagraphStart + tagStartLen + i

                if (working[i] == '\\') {
                    val escaped = working.getOrNull(i + 1)
                    if (escaped != null && escaped in setOf('*', '_', '~', '[', '\\')) {
                        origToTrans[origIdx] = builder.length
                        val transIdx = builder.length
                        builder.append(escaped)
                        transToOrigList.add(origIdx + 1)
                        origToTrans[origIdx + 1] = transIdx
                        i += 2
                        continue
                    }
                }

                when {
                    working.startsWith("***", i) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        origToTrans[origIdx + 2] = builder.length
                        toggleBold(builder.length)
                        toggleItalic(builder.length)
                        i += 3
                    }
                    working.startsWith("**", i) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        toggleBold(builder.length)
                        i += 2
                    }
                    working.startsWith("__", i) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        toggleUnderline(builder.length)
                        i += 2
                    }
                    working.startsWith("~~", i) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        toggleStrike(builder.length)
                        i += 2
                    }
                    working.regionMatches(i, "[u]", 0, 3, ignoreCase = true) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        origToTrans[origIdx + 2] = builder.length
                        if (underlineStart == null) underlineStart = builder.length
                        i += 3
                    }
                    working.regionMatches(i, "[/u]", 0, 4, ignoreCase = true) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        origToTrans[origIdx + 2] = builder.length
                        origToTrans[origIdx + 3] = builder.length
                        if (underlineStart != null) {
                            builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStart!!, builder.length)
                            underlineStart = null
                        }
                        i += 4
                    }
                    working.regionMatches(i, "[s]", 0, 3, ignoreCase = true) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        origToTrans[origIdx + 2] = builder.length
                        if (strikeStart == null) strikeStart = builder.length
                        i += 3
                    }
                    working.regionMatches(i, "[/s]", 0, 4, ignoreCase = true) -> {
                        origToTrans[origIdx] = builder.length
                        origToTrans[origIdx + 1] = builder.length
                        origToTrans[origIdx + 2] = builder.length
                        origToTrans[origIdx + 3] = builder.length
                        if (strikeStart != null) {
                            builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), strikeStart!!, builder.length)
                            strikeStart = null
                        }
                        i += 4
                    }
                    working[i] == '_' -> {
                        origToTrans[origIdx] = builder.length
                        toggleUnderline(builder.length)
                        i++
                    }
                    working[i] == '~' -> {
                        origToTrans[origIdx] = builder.length
                        toggleStrike(builder.length)
                        i++
                    }
                    working[i] == '*' -> {
                        origToTrans[origIdx] = builder.length
                        toggleItalic(builder.length)
                        i++
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
            if (boldStart != null) {
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStart!!, pTransEnd)
            }
            if (italicStart != null) {
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart!!, pTransEnd)
            }
            if (underlineStart != null) {
                builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStart!!, pTransEnd)
            }
            if (strikeStart != null) {
                builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), strikeStart!!, pTransEnd)
            }

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

            if (alignment != null) {
                builder.addStyle(
                    ParagraphStyle(textAlign = alignment),
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
}

class VisualEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialContent = intent.getStringExtra("content") ?: ""

        setContent {
            AstralEbookTheme {
                VisualEditorScreen(
                    initialContent = initialContent,
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
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialContent, TextRange(initialContent.length)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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

                    // 1. Find paragraph boundaries
                    var paraStart = start
                    while (paraStart > 0 && text[paraStart - 1] != '\n') {
                        paraStart--
                    }
                    var paraEnd = end
                    while (paraEnd < text.length && text[paraEnd] != '\n') {
                        paraEnd++
                    }

                    val paraText = text.substring(paraStart, paraEnd)

                    // 2. Strip existing alignment tags
                    val bracketAlign = Regex("^\\[(left|right|center|justify)](.*)\\[/\\1]$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val attrAlign = Regex("^\\[align=(left|right|center|justify)](.*)\\[/align]$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

                    var workingPara = paraText.trim()
                    var existingAlign: String? = null

                    var matched = true
                    while (matched) {
                        val bracketMatch = bracketAlign.find(workingPara)
                        if (bracketMatch != null) {
                            existingAlign = bracketMatch.groupValues[1].lowercase()
                            workingPara = bracketMatch.groupValues[2].trim()
                            continue
                        }
                        val attrMatch = attrAlign.find(workingPara)
                        if (attrMatch != null) {
                            existingAlign = attrMatch.groupValues[1].lowercase()
                            workingPara = attrMatch.groupValues[2].trim()
                            continue
                        }
                        matched = false
                    }

                    // 3. Apply new alignment or toggle if same alignment is clicked
                    val newParaText = if (existingAlign == tag) {
                        workingPara
                    } else {
                        "[$tag]$workingPara[/$tag]"
                    }

                    // 4. Construct the new text and set the selection
                    val newText = text.substring(0, paraStart) + newParaText + text.substring(paraEnd)
                    val newSelection = TextRange(paraStart, paraStart + newParaText.length)
                    textFieldValue = TextFieldValue(newText, newSelection)
                }
            )

            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                visualTransformation = MarkupVisualTransformation(),
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

@Composable
fun FormattingToolbar(
    onApplyFormatting: (String, String) -> Unit,
    onSetAlignment: (ParagraphAlignment) -> Unit
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
            ToolbarButton(Icons.Default.FormatBold, "Bold") { onApplyFormatting("**", "**") }
            ToolbarButton(Icons.Default.FormatItalic, "Italic") { onApplyFormatting("*", "*") }
            ToolbarButton(Icons.Default.FormatUnderlined, "Underline") { onApplyFormatting("__", "__") }
            ToolbarButton(Icons.Default.FormatStrikethrough, "Strikethrough") { onApplyFormatting("~~", "~~") }
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
