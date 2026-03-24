package com.codex.signalflow.importer

import com.codex.signalflow.source.RemoteTrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotImportNoiseCorrectionTest {

    @Test
    fun `pickBestMatch recovers blum from noisy OCR`() {
        val candidate = RecognizedTrackCandidate(
            candidateId = "1",
            screenshotIndex = 0,
            rawText = "ELUM | Lige.",
            artistGuess = "Lige.",
            titleGuess = "ELUM",
            confidence = 0.74f,
        )
        val correct = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "BLUM",
            artist = "Lida, SQWOZ BAB",
            detailUrl = "https://web.ligaudio.ru/mp3/lida-sqwoz-bab-blum",
            downloadUrl = "https://storage.lightaudio.ru/blum.mp3",
        )
        val wrong = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "Elum",
            artist = "DNSGMDMANN",
            detailUrl = "https://web.ligaudio.ru/mp3/dnsgmdmann-elum",
            downloadUrl = "https://storage.lightaudio.ru/elum.mp3",
        )

        val matched = ScreenshotImportMatcher.pickBestMatch(candidate, listOf(wrong, correct), emptyList())

        assertEquals(ScreenshotImportItemStatus.MATCHED, matched.status)
        assertEquals(correct, matched.bestMatch)
        assertTrue(matched.matchScore >= 0.72f)
    }

    @Test
    fun `pickBestMatch recovers csv from noisy OCR`() {
        val candidate = RecognizedTrackCandidate(
            candidateId = "2",
            screenshotIndex = 0,
            rawText = "ЧСЕ | чс | Lire Сеpега иреп",
            artistGuess = "Чс",
            titleGuess = "ЧСЕ",
            confidence = 0.71f,
        )
        val correct = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "ЧСВ",
            artist = "Lida, Серега Пират",
            detailUrl = "https://web.ligaudio.ru/mp3/lida-serega-pirat-csv",
            downloadUrl = "https://storage.lightaudio.ru/csv.mp3",
        )
        val wrong = RemoteTrackCandidate(
            sourceId = "ligaudio",
            title = "НСЕ",
            artist = "014 меж",
            detailUrl = "https://web.ligaudio.ru/mp3/014-mezh-nse",
            downloadUrl = "https://storage.lightaudio.ru/nse.mp3",
        )

        val matched = ScreenshotImportMatcher.pickBestMatch(candidate, listOf(wrong, correct), emptyList())

        assertEquals(ScreenshotImportItemStatus.MATCHED, matched.status)
        assertEquals(correct, matched.bestMatch)
        assertTrue(matched.matchScore >= 0.72f)
    }
}
