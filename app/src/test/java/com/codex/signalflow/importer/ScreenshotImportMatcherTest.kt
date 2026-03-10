package com.codex.signalflow.importer

import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.source.RemoteTrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotImportMatcherTest {

    @Test
    fun `deduplicate keeps highest confidence candidate`() {
        val input = listOf(
            RecognizedTrackCandidate(
                candidateId = "1",
                screenshotIndex = 0,
                rawText = "Кино | Пачка сигарет",
                artistGuess = "Кино",
                titleGuess = "Пачка сигарет",
                confidence = 0.51f,
            ),
            RecognizedTrackCandidate(
                candidateId = "2",
                screenshotIndex = 1,
                rawText = "Кино | Пачка сигарет",
                artistGuess = "Кино",
                titleGuess = "Пачка сигарет",
                confidence = 0.93f,
            ),
        )

        val deduplicated = ScreenshotImportMatcher.deduplicate(input)

        assertEquals(1, deduplicated.size)
        assertEquals("2", deduplicated.first().candidateId)
    }

    @Test
    fun `buildSearchQueries keeps title first order`() {
        val candidate = RecognizedTrackCandidate(
            candidateId = "1",
            screenshotIndex = 0,
            rawText = "Feel Good Inc. Single Edit | Gorillaz Jamie Hewlett De La Soul",
            artistGuess = "Gorillaz Jamie Hewlett De La Soul",
            titleGuess = "Feel Good Inc. Single Edit",
            confidence = 0.95f,
        )

        val queries = ScreenshotImportMatcher.buildSearchQueries(candidate)

        assertEquals("feel good inc single edit", queries.first())
        assertTrue(queries.contains("feel good inc single edit gorillaz jamie hewlett de la soul"))
        assertTrue(queries.contains("gorillaz jamie hewlett de la soul feel good inc single edit"))
    }

    @Test
    fun `pickBestMatch favors exact ordered english title`() {
        val candidate = RecognizedTrackCandidate(
            candidateId = "1",
            screenshotIndex = 0,
            rawText = "Inc. | Feel | Goоd",
            artistGuess = "Gorillaz Jamie Hewlett De La Soul",
            titleGuess = "Feel Goоd Inc",
            confidence = 0.94f,
        )
        val exact = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Feel Good Inc",
            artist = "Gorillaz",
            detailUrl = "https://web.ligaudio.ru/mp3/gorillaz-feel-good-inc",
            downloadUrl = "https://storage.lightaudio.ru/feel-good-inc.mp3",
        )
        val wrong = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Inc",
            artist = "Random Artist",
            detailUrl = "https://web.ligaudio.ru/mp3/random-inc",
            downloadUrl = "https://storage.lightaudio.ru/random-inc.mp3",
        )

        val matched = ScreenshotImportMatcher.pickBestMatch(candidate, listOf(wrong, exact), emptyList())

        assertEquals(ScreenshotImportItemStatus.MATCHED, matched.status)
        assertEquals(exact, matched.bestMatch)
        assertTrue(matched.matchScore >= 0.66f)
    }

    @Test
    fun `pickBestMatch marks cyrillic track already in playlist`() {
        val candidate = RecognizedTrackCandidate(
            candidateId = "1",
            screenshotIndex = 0,
            rawText = "Кино | Пачка сигарет",
            artistGuess = "Кино",
            titleGuess = "Пачка сигарет",
            confidence = 0.88f,
        )
        val results = listOf(
            RemoteTrackCandidate(
                sourceId = "ligaudio",
                title = "Пачка сигарет",
                artist = "Кино",
                detailUrl = "https://web.ligaudio.ru/mp3/kino-pachka-sigaret",
                downloadUrl = "https://storage.lightaudio.ru/test.mp3",
            ),
        )
        val playlistTracks = listOf(
            TrackRecord(
                id = "local-1",
                uri = "content://media/test",
                sourceUri = "https://storage.lightaudio.ru/test.mp3",
                title = "Пачка сигарет",
                artist = "Кино",
                durationMs = 0L,
                importedAtEpochMs = 0L,
            ),
        )

        val matched = ScreenshotImportMatcher.pickBestMatch(candidate, results, playlistTracks)

        assertEquals(ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST, matched.status)
        assertTrue(matched.matchScore > 0.7f)
    }

    @Test
    fun `buildSearchQueries ignores timecode artist and falls back to next raw segment`() {
        val candidate = RecognizedTrackCandidate(
            candidateId = "1",
            screenshotIndex = 0,
            rawText = "неловко | 02:21 | плм",
            artistGuess = "02:21",
            titleGuess = "неловко",
            confidence = 0.87f,
        )

        val queries = ScreenshotImportMatcher.buildSearchQueries(candidate)

        assertEquals("неловко", queries.first())
        assertTrue(queries.contains("неловко плм"))
        assertTrue(queries.none { it.contains("02:21") })
    }
}
