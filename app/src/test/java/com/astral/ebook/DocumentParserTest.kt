package com.astral.ebook

import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.repository.DocumentParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentParserTest {
    @Test
    fun testParseParagraphMarkupCenter() {
        val paragraph = DocumentParser.parseParagraphMarkup("[center]Paragraf ini rata tengah[/center]")
        assertEquals(ParagraphAlignment.Center, paragraph.alignment)
        assertEquals("Paragraf ini rata tengah", paragraph.plainText())
    }

    @Test
    fun testParseParagraphMarkupRight() {
        val paragraph = DocumentParser.parseParagraphMarkup("[align=right]Paragraf ini rata kanan[/align]")
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
    fun testParseStrikethroughWithSquareBrackets() {
        val paragraph = DocumentParser.parseParagraphMarkup("Ini [s]dicoret[/s] biasa")
        assertEquals(3, paragraph.runs.size)
        assertEquals("Ini ", paragraph.runs[0].text)
        assertEquals(false, paragraph.runs[0].strikeThrough)

        assertEquals("dicoret", paragraph.runs[1].text)
        assertEquals(true, paragraph.runs[1].strikeThrough)

        assertEquals(" biasa", paragraph.runs[2].text)
        assertEquals(false, paragraph.runs[2].strikeThrough)
    }

    @Test
    fun testTildeIsParsedAsPlaintext() {
        val paragraph = DocumentParser.parseParagraphMarkup("Ini ~tidak dicoret~ biasa")
        assertEquals(1, paragraph.runs.size)
        assertEquals("Ini ~tidak dicoret~ biasa", paragraph.runs[0].text)
        assertEquals(false, paragraph.runs[0].strikeThrough)
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
}
