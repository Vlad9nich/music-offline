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

    @Test
    fun buildPlaybackQueueAvoidsAdjacentSameArtistWhenAlternativesExist() {
        val tracks = listOf(
            sampleTrack(id = "a-1", artist = "Artist A"),
            sampleTrack(id = "a-2", artist = "Artist A"),
            sampleTrack(id = "b-1", artist = "Artist B"),
            sampleTrack(id = "c-1", artist = "Artist C"),
            sampleTrack(id = "d-1", artist = "Artist D"),
        )

        val queue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = "a-1",
            shuffleEnabled = true,
            random = Random(42),
        )

        assertEquals("a-1", queue.first().id)
        assertNoAdjacentArtists(queue)
    }

    @Test
    fun buildPlaybackQueueAllowsAdjacentSameArtistWhenUnavoidable() {
        val tracks = listOf(
            sampleTrack(id = "a-1", artist = "Artist A"),
            sampleTrack(id = "a-2", artist = "Artist A"),
            sampleTrack(id = "a-3", artist = "Artist A"),
            sampleTrack(id = "b-1", artist = "Artist B"),
        )

        val queue = buildPlaybackQueue(
            tracks = tracks,
            currentTrackId = "a-1",
            shuffleEnabled = true,
            random = Random(7),
        )

        assertEquals(tracks.size, queue.size)
        assertEquals(tracks.map { it.id }.sorted(), queue.map { it.id }.sorted())
    }

    private fun sampleTracks(): List<TrackRecord> {
        return List(4) { index ->
            sampleTrack(
                id = "track-$index",
                title = "Track $index",
                artist = "Artist $index",
            )
        }
    }

    private fun sampleTrack(
        id: String,
        title: String = id,
        artist: String,
    ): TrackRecord {
        return TrackRecord(
            id = id,
            uri = "content://$id",
            title = title,
            artist = artist,
            durationMs = 180_000L,
            importedAtEpochMs = 1_700_000_000_000L,
        )
    }

    private fun assertNoAdjacentArtists(queue: List<TrackRecord>) {
        queue.zipWithNext().forEach { (left, right) ->
            assertNotEquals(left.artist, right.artist)
        }
    }
}
