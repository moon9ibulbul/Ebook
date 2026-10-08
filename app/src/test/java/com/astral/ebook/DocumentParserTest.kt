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
    fun testCleanUpMarkupRemovesIncompleteTagFragments() {
        val brokenInput = "<strong>Bab 12</strong"
        val cleaned = com.astral.ebook.ui.cleanUpMarkup(brokenInput)
        assertEquals("<strong>Bab 12</strong>", cleaned)
    }

    @Test
    fun testCleanUpMarkupRemovesEmptyTagsAndUnclosedTags() {
        val emptyTagText = "Kata <b></b> normal <i></i>"
        val cleanedEmpty = com.astral.ebook.ui.cleanUpMarkup(emptyTagText)
        assertEquals("Kata  normal ", cleanedEmpty)

        val unclosedTagText = "Kata <b>bold tanpa tutup"
        val cleanedUnclosed = com.astral.ebook.ui.cleanUpMarkup(unclosedTagText)
        assertEquals("Kata <b>bold tanpa tutup</b>", cleanedUnclosed)

        val strayFragment = "Kata <b>bold</b>> fragmen"
        val cleanedStray = com.astral.ebook.ui.cleanUpMarkup(strayFragment)
        assertEquals("Kata <b>bold</b> fragmen", cleanedStray)
    }

    @Test
    fun testMultiParagraphAlignmentTransformation() {
        val input = "Paragraf 1\nParagraf 2\n<p align=\"center\">Paragraf 3</p>"
        val lines = input.split('\n')
        val tag = "center"

        val htmlPAlign = Regex("^<(?:p|div)\\s+(?:align=\"([a-zA-Z]+)\"|style=\"[^\"]*text-align:\\s*([a-zA-Z]+)[^\"]*\")\\s*>(.*)</(?:p|div)>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val centerTag = Regex("^<center>(.*)</center>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        val transformed = lines.map { line ->
            var workingPara = line.trim()
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

            if (existingAlign == tag) {
                workingPara
            } else {
                "<p align=\"$tag\">$workingPara</p>"
            }
        }.joinToString("\n")

        val expected = "<p align=\"center\">Paragraf 1</p>\n<p align=\"center\">Paragraf 2</p>\nParagraf 3"
        assertEquals(expected, transformed)
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
}
