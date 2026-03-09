package com.codex.signalflow.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LigaudioSourceTest {
    @Test
    fun parsesLightaudioSearchCardsIntoRemoteCandidates() {
        val html = """
            <div id="result">
                <div class="item" itemprop="track">
                    <div>
                        <div class="title">
                            <span class="title" itemprop="name">Юра Шатунов</span>
                            <i class="m"> &mdash; </i>
                            <span class="autor" itemprop="byArtist">
                                <a href="/mp3/aleks%20ataman">ALEKS ATAMAN</a>,
                                <a href="/mp3/finik">FINIK</a>
                            </span>
                        </div>
                        <a class="down" href="//storage1.lightaudio.ru/dm/path/song.mp3?d=123"></a>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val source = LigaudioSource()
        val results = source.parseSearchHtml(html, "https://lightaudio.ru/mp3/aleks%20ataman")

        assertEquals(1, results.size)
        assertEquals("Юра Шатунов", results.first().title)
        assertEquals("ALEKS ATAMAN, FINIK", results.first().artist)
        assertEquals("https://storage1.lightaudio.ru/dm/path/song.mp3?d=123", results.first().downloadUrl)
    }

    @Test
    fun resolvesDirectDownloadWithoutDetailPageLookup() {
        val track = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Юра Шатунов",
            artist = "ALEKS ATAMAN, FINIK",
            detailUrl = "https://lightaudio.ru/mp3/aleks%20ataman",
            downloadUrl = "https://storage1.lightaudio.ru/dm/path/song.mp3?d=123",
        )

        val source = LigaudioSource()
        val blueprint = runBlocking { source.resolve(track) }

        assertEquals("https://storage1.lightaudio.ru/dm/path/song.mp3?d=123", blueprint.url)
        assertEquals("https://web.ligaudio.ru/", blueprint.refererUrl)
    }

    @Test
    fun encodesSearchQueryForPathSegments() {
        val encoded = MusicSourceCatalog.encodedPathSegment("aleks ataman")
        assertEquals("aleks%20ataman", encoded)
        assertTrue(!encoded.contains("+"))
    }
}
