package com.codex.signalflow.player

import com.codex.signalflow.data.TrackRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackManagerTest {
    @Test
    fun buildPlaybackQueueKeepsAllTracksAndPinsCurrentTrackWhenShuffleEnabled() {
        val tracks = sampleTracks()

        val queue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = "track-2",
            shuffleEnabled = true,
            random = Random(42),
        )

        assertEquals(tracks.size, queue.size)
        assertEquals("track-2", queue.first().id)
        assertEquals(tracks.map { it.id }.sorted(), queue.map { it.id }.sorted())
    }

    @Test
    fun buildPlaybackQueueReturnsBaseOrderWhenShuffleDisabled() {
        val tracks = sampleTracks()

        val queue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = "track-3",
            shuffleEnabled = false,
            random = Random(42),
        )

        assertEquals(tracks, queue)
    }

    @Test
    fun buildPlaybackQueueCanCreateNewOrderAfterPlaylistEnds() {
        val tracks = sampleTracks()

        val firstQueue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = null,
            shuffleEnabled = true,
            random = Random(5),
        )
        val secondQueue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = null,
            shuffleEnabled = true,
            random = Random(99),
        )

        assertEquals(tracks.map { it.id }.sorted(), firstQueue.map { it.id }.sorted())
        assertEquals(tracks.map { it.id }.sorted(), secondQueue.map { it.id }.sorted())
        assertNotEquals(firstQueue.map { it.id }, secondQueue.map { it.id })
    }

    @Test
    fun buildPlaybackQueueFallsBackToAnyTrackWhenPinnedTrackMissing() {
        val tracks = sampleTracks()

        val queue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = "missing",
            shuffleEnabled = true,
            random = Random(7),
        )

        assertEquals(tracks.size, queue.size)
        assertTrue(queue.first().id in tracks.map { it.id })
    }

    private fun sampleTracks(): List<TrackRecord> {
        return List(4) { index ->
            TrackRecord(
                id = "track-$index",
                uri = "content://track/$index",
                title = "Track $index",
                artist = "Artist $index",
                durationMs = 180_000L + index,
                importedAtEpochMs = 1_700_000_000_000L + index,
            )
        }
    }
}
