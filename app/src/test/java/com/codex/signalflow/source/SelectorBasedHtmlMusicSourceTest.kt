package com.codex.signalflow.source

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSourceCatalogEncodingTest {
    @Test
    fun encodesSpacesAsPercent20ForPathSearches() {
        assertEquals("aleks%20ataman", MusicSourceCatalog.encodedPathSegment("aleks ataman"))
    }

    @Test
    fun skipsCardsWithoutDownloadButton() {
        val html = """
            <div id="result">
                <div class="item" itemprop="track">
                    <div>
                        <div class="title">
                            <span class="title" itemprop="name">Тест</span>
                            <span class="autor" itemprop="byArtist"><a href="/mp3/test">Артист</a></span>
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val results = LigaudioSource().parseSearchHtml(html, "https://lightaudio.ru/mp3/test")
        assertEquals(0, results.size)
    }
}
