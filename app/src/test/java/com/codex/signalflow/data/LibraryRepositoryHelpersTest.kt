package com.codex.signalflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryHelpersTest {
    @Test
    fun extractsDirectMediaUrlFromHtmlWrapper() {
        val html = """
            <html>
              <body>
                <script>
                  window.player = { file: '/uploads/music/test-track.mp3?play=1' };
                </script>
              </body>
            </html>
        """.trimIndent()

        val resolved = LibraryRepository.extractMediaUrlFromBody(
            body = html,
            baseUrl = "https://poiskm.net/show/test-artist/test-track",
        )

        assertEquals("https://poiskm.net/uploads/music/test-track.mp3?play=1", resolved)
    }

    @Test
    fun buildsFallbackTrackWhenMetadataIsUnavailable() {
        val track = LibraryRepository.buildFallbackTrackRecord(
            playbackUri = "content://media/external/audio/media/42",
            sourceUri = "https://poiskm.net/?do=getById&id=42&n=9",
            title = "Тестовый трек",
            artist = "Тестовый артист",
        )

        assertEquals("content://media/external/audio/media/42", track.uri)
        assertEquals("https://poiskm.net/?do=getById&id=42&n=9", track.sourceUri)
        assertEquals("Тестовый трек", track.title)
        assertEquals("Тестовый артист", track.artist)
        assertEquals(0L, track.durationMs)
        assertTrue(track.id.isNotBlank())
    }

    @Test
    fun userFacingMessageFallsBackToCauseOrDefault() {
        val nested = IllegalStateException("", RuntimeException("HTTP 403"))
        val fromCause = LibraryRepository.toUserFacingMessage(nested, "Не удалось скачать трек.")
        assertEquals("HTTP 403", fromCause)

        val withoutMessage = RuntimeException()
        val fallback = LibraryRepository.toUserFacingMessage(withoutMessage, "Не удалось скачать трек.")
        assertEquals("Не удалось скачать трек.", fallback)
    }

    @Test
    fun describeThrowableAlwaysContainsClassName() {
        val description = LibraryRepository.describeThrowable(IllegalArgumentException())
        assertNotNull(description)
        assertTrue(description.contains("IllegalArgumentException"))
        assertTrue(description.contains("без сообщения"))
    }
}
