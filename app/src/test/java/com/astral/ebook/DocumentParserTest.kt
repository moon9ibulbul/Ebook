package com.astral.ebook

import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.repository.DocumentParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentParserTest {
    @Test
    fun testParseParagraphMarkupCenter() {
        val paragraph = DocumentParser.parseParagraphMarkup("<center>Paragraf ini rata tengah</center>")
        assertEquals(ParagraphAlignment.Center, paragraph.alignment)
        assertEquals("Paragraf ini rata tengah", paragraph.plainText())
    }

    @Test
    fun testParseParagraphMarkupRight() {
        val paragraph = DocumentParser.parseParagraphMarkup("<p align=\"right\">Paragraf ini rata kanan</p>")
        assertEquals(ParagraphAlignment.Right, paragraph.alignment)
        assertEquals("Paragraf ini rata kanan", paragraph.plainText())
    }

    @Test
    fun testParseParagraphMarkupNoAlignment() {
        val paragraph = DocumentParser.parseParagraphMarkup("Paragraf biasa tanpa alignment")
        assertEquals(null, paragraph.alignment)
        assertEquals("Paragraf biasa tanpa alignment", paragraph.plainText())
    }

    @Test
    fun testParseStrikethroughWithHtml() {
        val paragraph = DocumentParser.parseParagraphMarkup("Ini <s>dicoret</s> biasa")
        assertEquals(3, paragraph.runs.size)
        assertEquals("Ini ", paragraph.runs[0].text)
        assertEquals(false, paragraph.runs[0].strikeThrough)

        assertEquals("dicoret", paragraph.runs[1].text)
        assertEquals(true, paragraph.runs[1].strikeThrough)

        assertEquals(" biasa", paragraph.runs[2].text)
        assertEquals(false, paragraph.runs[2].strikeThrough)
    }

    @Test
    fun testParseHtmlTags() {
        val paragraph = DocumentParser.parseParagraphMarkup("<p align=\"center\">Ini <b>tebal</b> dan <i>miring</i> serta <u>garis bawah</u> dan <s>coret</s></p>")
        assertEquals(ParagraphAlignment.Center, paragraph.alignment)
        val textRuns = paragraph.runs.filter { it.text.isNotEmpty() }
        assertEquals("Ini ", textRuns[0].text)
        assertEquals("tebal", textRuns[1].text)
        assertEquals(true, textRuns[1].bold)
        assertEquals(" dan ", textRuns[2].text)
        assertEquals("miring", textRuns[3].text)
        assertEquals(true, textRuns[3].italic)
        assertEquals(" serta ", textRuns[4].text)
        assertEquals("garis bawah", textRuns[5].text)
        assertEquals(true, textRuns[5].underline)
        assertEquals(" dan ", textRuns[6].text)
        assertEquals("coret", textRuns[7].text)
        assertEquals(true, textRuns[7].strikeThrough)
    }

    @Test
    fun testParseImageTag() {
        val paragraph = DocumentParser.parseParagraphMarkup("Teks sebelum <img src=\"content://media/external/images/media/123\"/> teks sesudah")
        assertEquals(3, paragraph.runs.size)
        assertEquals("Teks sebelum ", paragraph.runs[0].text)
        assertEquals("[Gambar]", paragraph.runs[1].text)
        assertEquals("content://media/external/images/media/123", paragraph.runs[1].imageUri)
        assertEquals(" teks sesudah", paragraph.runs[2].text)
    }

    @Test
    fun testSanitizePastedHtmlStripsUnsupportedTags() {
        val dirtyHtml = "<div style=\"color: red;\"><h1>Title</h1><p align=\"center\">Hello <script>alert('xss')</script><b>World</b> <a href=\"http://example.com\">link</a></p></div>"
        val sanitized = com.astral.ebook.ui.sanitizePastedHtml(dirtyHtml)
        assertEquals("Title\n<p align=\"center\">Hello <b>World</b> link</p>\n\n", sanitized)
    }

    @Test
    fun testMarkupVisualTransformationFormattingAndPerformance() {
        val transformation = com.astral.ebook.ui.MarkupVisualTransformation()

        val textWithMarkup = "Paragraf biasa.\n<b>Tebal</b> dan <i>miring</i>\n<p align=\"center\">Tengah</p>"
        val result = transformation.filter(androidx.compose.ui.text.AnnotatedString(textWithMarkup))
        assertEquals("Paragraf biasa.\nTebal dan miring\nTengah", result.text.text)

        // Performance test on large text (e.g. 50,000 lines)
        val sb = StringBuilder()
        for (i in 0 until 10000) {
            sb.append("Ini adalah paragraf biasa nomor ").append(i).append(" tanpa tag HTML.\n")
        }
        val largeText = sb.toString()

        val startTime = System.currentTimeMillis()
        val largeResult = transformation.filter(androidx.compose.ui.text.AnnotatedString(largeText))
        val duration = System.currentTimeMillis() - startTime

        assertEquals(largeText, largeResult.text.text)
        org.junit.Assert.assertTrue("Transformation duration should be under 500ms for 10,000 lines, was $duration ms", duration < 500)
        // Paragraphs get 0 paragraphStyles since default indent is now applied to all paragraphs (skipIndentAfterHeading = false)
        assertEquals(0, largeResult.text.paragraphStyles.size)
    }

    @Test
    fun testFindAndReplaceMatches() {
        val originalText = "Kata pertama, KATA kedua, kata ketiga."
        val searchQuery = "kata"
        val replaceQuery = "teks"

        val regex = Regex.escape(searchQuery).toRegex(RegexOption.IGNORE_CASE)
        val matches = regex.findAll(originalText).map { it.range }.toList()

        assertEquals(3, matches.size)

        val replacedAll = originalText.replace(regex, replaceQuery)
        assertEquals("teks pertama, teks kedua, teks ketiga.", replacedAll)
    }

    @Test
    fun testDefaultParagraphAlignmentIsLeft() {
        val defaultOptions = com.astral.ebook.model.ParagraphOptions()
        assertEquals(ParagraphAlignment.Left, defaultOptions.alignment)
    }

    @Test
    fun testParseParagraphMarkupJustify() {
        val paragraph = DocumentParser.parseParagraphMarkup("<p align=\"justify\">Paragraf ini rata kanan kiri</p>")
        assertEquals(ParagraphAlignment.Justify, paragraph.alignment)
        assertEquals("Paragraf ini rata kanan kiri", paragraph.plainText())
    }

    @Test
    fun testLineContentTextIsLastInParagraph() {
        val line1 = com.astral.ebook.repository.LineContent.Text(
            segments = emptyList(),
            indent = 0f,
            alignment = ParagraphAlignment.Justify,
            isLastInParagraph = false
        )
        val line2 = line1.copy(isLastInParagraph = true)

        assertEquals(false, line1.isLastInParagraph)
        assertEquals(true, line2.isLastInParagraph)
        assertEquals(ParagraphAlignment.Justify, line2.alignment)
    }

    @Test
    fun testStripIncompleteHtmlUnclosedOpeningTag() {
        val input = "<b>Kemana perginya kamu selama ini"
        val stripped = com.astral.ebook.repository.stripIncompleteHtml(input)
        assertEquals("Kemana perginya kamu selama ini", stripped)

        val paragraph = DocumentParser.parseParagraphMarkup(input)
        assertEquals("Kemana perginya kamu selama ini", paragraph.plainText())
        assertEquals(false, paragraph.runs.first().bold)
    }

    @Test
    fun testStripIncompleteHtmlUnopenedClosingTag() {
        val input = "Kemana perginya </b> kamu selama ini"
        val stripped = com.astral.ebook.repository.stripIncompleteHtml(input)
        assertEquals("Kemana perginya  kamu selama ini", stripped)

        val paragraph = DocumentParser.parseParagraphMarkup(input)
        assertEquals("Kemana perginya  kamu selama ini", paragraph.plainText())
        assertEquals(false, paragraph.runs.first().bold)
    }

    @Test
    fun testStripIncompleteHtmlTagFragments() {
        val inputs = listOf(
            "Kemana <stron perginya kamu" to "Kemana  perginya kamu",
            "Kemana <str perginya kamu" to "Kemana  perginya kamu",
            "Kemana <st perginya kamu" to "Kemana  perginya kamu",
            "Kemana </stron perginya kamu" to "Kemana  perginya kamu",
            "Kemana <b perginya kamu" to "Kemana  perginya kamu",
            "Kemana </b perginya kamu" to "Kemana  perginya kamu"
        )

        for ((input, expected) in inputs) {
            val stripped = com.astral.ebook.repository.stripIncompleteHtml(input)
            assertEquals(expected, stripped)
        }
    }

    @Test
    fun testStripIncompleteHtmlUnsupportedTags() {
        val input = "Kemana <stron>perginya</stron> kamu"
        val stripped = com.astral.ebook.repository.stripIncompleteHtml(input)
        assertEquals("Kemana perginya kamu", stripped)
    }

    @Test
    fun testStripIncompleteHtmlPreservesValidMatchedTagsAndEscapes() {
        val input = "<b>Kemana</b> perginya <i>kamu</i> \\<b> selama ini"
        val stripped = com.astral.ebook.repository.stripIncompleteHtml(input)
        assertEquals("<b>Kemana</b> perginya <i>kamu</i> \\<b> selama ini", stripped)

        val paragraph = DocumentParser.parseParagraphMarkup(input)
        assertEquals("Kemana perginya kamu <b> selama ini", paragraph.plainText())
        assertEquals(true, paragraph.runs[0].bold)
        assertEquals(false, paragraph.runs[1].bold)
        assertEquals(true, paragraph.runs[2].italic)
    }

    @Test
    fun testMarkupVisualTransformationStripsIncompleteTags() {
        val transformation = com.astral.ebook.ui.MarkupVisualTransformation()
        val text = "<b>Kemana perginya kamu <stron selama ini"
        val result = transformation.filter(androidx.compose.ui.text.AnnotatedString(text))

        assertEquals("Kemana perginya kamu  selama ini", result.text.text)
        assertEquals(0, result.text.spanStyles.size)
    }

    @Test
    fun testToggleFormattingTag() {
        val initialText = "Halo Dunia"
        val tfv = androidx.compose.ui.text.input.TextFieldValue(initialText, androidx.compose.ui.text.TextRange(0, 4)) // "Halo"

        // First application: adds <b>Halo</b>
        val formatted = com.astral.ebook.ui.toggleInlineFormat(
            tfv,
            "<b>",
            "</b>",
            com.astral.ebook.ui.TagType.BOLD_OPEN,
            com.astral.ebook.ui.TagType.BOLD_CLOSE
        )
        assertEquals("<b>Halo</b> Dunia", formatted.text)
        assertEquals(androidx.compose.ui.text.TextRange(3, 7), formatted.selection)

        // Active state check on formatted text
        val isActive = com.astral.ebook.ui.isInlineFormatActive(
            formatted.text,
            formatted.selection,
            com.astral.ebook.ui.TagType.BOLD_OPEN,
            com.astral.ebook.ui.TagType.BOLD_CLOSE
        )
        assertEquals(true, isActive)

        // Second application (toggle off): cancels bold format without duplicating tags
        val toggledOff = com.astral.ebook.ui.toggleInlineFormat(
            formatted,
            "<b>",
            "</b>",
            com.astral.ebook.ui.TagType.BOLD_OPEN,
            com.astral.ebook.ui.TagType.BOLD_CLOSE
        )
        assertEquals("Halo Dunia", toggledOff.text)
        assertEquals(androidx.compose.ui.text.TextRange(0, 4), toggledOff.selection)
    }

    @Test
    fun testUndoRedoHistoryStack() {
        val undoStack = mutableListOf<androidx.compose.ui.text.input.TextFieldValue>()
        val redoStack = mutableListOf<androidx.compose.ui.text.input.TextFieldValue>()

        var state = androidx.compose.ui.text.input.TextFieldValue("Versi 1")

        fun updateState(newState: androidx.compose.ui.text.input.TextFieldValue) {
            if (newState.text != state.text) {
                undoStack.add(state)
                redoStack.clear()
            }
            state = newState
        }

        updateState(androidx.compose.ui.text.input.TextFieldValue("Versi 2"))
        updateState(androidx.compose.ui.text.input.TextFieldValue("Versi 3"))

        assertEquals("Versi 3", state.text)
        assertEquals(2, undoStack.size)

        // Undo
        val prev1 = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(state)
        state = prev1
        assertEquals("Versi 2", state.text)

        // Undo again
        val prev2 = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(state)
        state = prev2
        assertEquals("Versi 1", state.text)

        // Redo
        val next1 = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(state)
        state = next1
        assertEquals("Versi 2", state.text)
    }
}
