package com.codex.signalflow.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PoiskmNetSourceTest {
    @Test
    fun parsesPoiskmSearchCardsIntoRemoteCandidates() {
        val html = """
            <div class="title_wrap" data-songid="321">
                <a class="artist-name" href="/artist/test">Test Artist</a>
                <span class="title-name">Test Song</span>
                <span class="play" data-number="7"></span>
            </div>
        """.trimIndent()

        val source = PoiskmNetSource()
        val results = source.parseSearchHtml(html, "https://poiskm.net/show/test-song")

        assertEquals(1, results.size)
        assertEquals("Test Song", results.first().title)
        assertEquals("Test Artist", results.first().artist)
        assertEquals("https://poiskm.net/show/test-song", results.first().detailUrl)
        assertNotNull(results.first().downloadUrl)
        assertEquals("https://poiskm.net/?do=getById&id=321&n=7", results.first().downloadUrl)
    }

    @Test
    fun fallsBackToPlainTextTrackParsingWhenTitleSelectorIsMissing() {
        val html = """
            <div id="song-987">
                <div class="title_wrap">
                    <a href="/artist/test-artist">Test Artist</a> - Test Song
                    <span class="play" data-number="5"></span>
                </div>
            </div>
        """.trimIndent()

        val source = PoiskmNetSource()
        val results = source.parseSearchHtml(html, "https://poiskm.net/show/test-artist/test-song")

        assertEquals(1, results.size)
        assertEquals("Test Song", results.first().title)
        assertEquals("Test Artist", results.first().artist)
        assertEquals("https://poiskm.net/?do=getById&id=987&n=5", results.first().downloadUrl)
    }
}
