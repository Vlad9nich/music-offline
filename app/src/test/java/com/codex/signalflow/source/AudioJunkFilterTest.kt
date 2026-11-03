package com.codex.signalflow.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioJunkFilterTest {

    @Test
    fun `detects sped up nightcore and bot tags`() {
        val targetTitle = "Яхта, Парус"
        val targetArtist = "Валентин Стрыкало"

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Яхта, Парус (sped up @nightcorebot)",
                candidateArtist = "Валентин Стрыкало",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Яхта, Парус (ускоренная версия)",
                candidateArtist = "Валентин Стрыкало",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `detects slowed and reverb`() {
        val targetTitle = "Numb"
        val targetArtist = "Linkin Park"

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Numb (slowed + reverb)",
                candidateArtist = "Linkin Park",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Numb (замедленная + реверб)",
                candidateArtist = "Linkin Park",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `detects bassboost and distortion`() {
        val targetTitle = "Cadillac"
        val targetArtist = "MORGENSHTERN"

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Cadillac (bassboosted)",
                candidateArtist = "MORGENSHTERN",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Cadillac [басс буст]",
                candidateArtist = "MORGENSHTERN",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `detects snippets trailers and teasers`() {
        val targetTitle = "Властелин Калек"
        val targetArtist = "SALUKI"

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Властелин Калек (сниппет)",
                candidateArtist = "SALUKI",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Властелин Калек (трейлер альбома)",
                candidateArtist = "SALUKI",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `detects unwanted intro and outro when target is regular track`() {
        val targetTitle = "Smells Like Teen Spirit"
        val targetArtist = "Nirvana"

        assertTrue(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Smells Like Teen Spirit (Intro)",
                candidateArtist = "Nirvana",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `permits intro when target legitimately is intro`() {
        val targetTitle = "Intro"
        val targetArtist = "The xx"

        assertFalse(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "Intro",
                candidateArtist = "The xx",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `permits legitimate remasters and album versions`() {
        val targetTitle = "In Bloom"
        val targetArtist = "Nirvana"

        assertFalse(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "In Bloom (Remastered 2021)",
                candidateArtist = "Nirvana",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )

        assertFalse(
            AudioJunkFilter.hasUnwantedJunk(
                candidateTitle = "In Bloom (Album Version)",
                candidateArtist = "Nirvana",
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        )
    }

    @Test
    fun `findBestCandidate picks clean version over sped up or bot versions`() {
        val targetTitle = "Cadillac"
        val targetArtist = "MORGENSHTERN & Элджей"

        val junkSpedUp = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Cadillac (sped up @nightcorebot)",
            artist = "MORGENSHTERN",
            detailUrl = "https://web.ligaudio.ru/1",
        )
        val junkBassboost = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Cadillac [bassboosted]",
            artist = "MORGENSHTERN",
            detailUrl = "https://web.ligaudio.ru/2",
        )
        val cleanGenuine = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Cadillac",
            artist = "MORGENSHTERN & Элджей",
            detailUrl = "https://web.ligaudio.ru/3",
        )

        val selected = AudioJunkFilter.findBestCandidate(
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            candidates = listOf(junkSpedUp, junkBassboost, cleanGenuine),
        )

        org.junit.Assert.assertEquals(cleanGenuine, selected)
    }

    @Test
    fun `findBestCandidate returns null when all candidates are junk`() {
        val targetTitle = "Властелин Калек"
        val targetArtist = "SALUKI"

        val junkSnippet = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Властелин Калек (сниппет)",
            artist = "SALUKI",
            detailUrl = "https://web.ligaudio.ru/1",
        )
        val junkSlowed = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Властелин Калек (slowed + reverb)",
            artist = "SALUKI",
            detailUrl = "https://web.ligaudio.ru/2",
        )
        val junkTrailer = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Властелин Калек (трейлер)",
            artist = "SALUKI",
            detailUrl = "https://web.ligaudio.ru/3",
        )

        val selected = AudioJunkFilter.findBestCandidate(
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            candidates = listOf(junkSnippet, junkSlowed, junkTrailer),
        )

        org.junit.Assert.assertNull(selected)
    }
}
