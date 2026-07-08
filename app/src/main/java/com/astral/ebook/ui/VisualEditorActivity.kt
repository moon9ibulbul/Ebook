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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.ui.theme.AstralEbookTheme

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

                    // Check if it's already aligned
                    val prefix = "[$tag]"
                    val suffix = "[/$tag]"

                    val newText = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end)
                    textFieldValue = TextFieldValue(newText, TextRange(start + prefix.length, end + prefix.length))
                }
            )

            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
