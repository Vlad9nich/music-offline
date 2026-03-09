package com.codex.signalflow.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectorBasedHtmlMusicSourceTest {
    private val blueprint = HtmlTrackSelectorBlueprint(
        itemSelector = ".track",
        titleSelector = ".title",
        artistSelector = ".artist",
        linkSelector = ".title-link",
        downloadSelector = ".download",
    )

    private val source = SelectorBasedHtmlMusicSource(
        descriptor = SourceDescriptor(
            id = "sample",
            name = "Sample",
            status = "ready",
            description = "Test source",
            isEnabled = true,
        ),
        blueprint = blueprint,
        searchUrlBuilder = { "https://example.com/search?q=${MusicSourceCatalog.encodedQuery(it)}" },
    )

    @Test
    fun parsesTrackCandidatesFromSelectorBlueprint() {
        val html = """
            <div class="track">
              <a class="title-link" href="/song/1"><span class="title">Night Circuit</span></a>
              <span class="artist">Delta Echo</span>
              <a class="download" href="/download/1.mp3">download</a>
            </div>
            <div class="track">
              <a class="title-link" href="/song/2"><span class="title">Signal Rain</span></a>
              <span class="artist">Core Static</span>
            </div>
        """.trimIndent()

        val results = source.parseHtml(html, "https://example.com")

        assertEquals(2, results.size)
        assertEquals("Night Circuit", results.first().title)
        assertEquals("Delta Echo", results.first().artist)
        assertEquals("https://example.com/song/1", results.first().detailUrl)
        assertEquals("https://example.com/download/1.mp3", results.first().downloadUrl)
        assertNull(results.last().downloadUrl)
    }
}
