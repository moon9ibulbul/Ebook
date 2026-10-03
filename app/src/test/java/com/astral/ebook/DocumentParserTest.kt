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
        // Paragraph 0 gets 1 style to skip indent after heading, remaining 9,999 paragraphs get 0 styles
        assertEquals(1, largeResult.text.paragraphStyles.size)
    }
}
