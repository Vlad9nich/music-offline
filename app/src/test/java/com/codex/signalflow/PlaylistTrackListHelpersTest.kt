package com.codex.signalflow

import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.ui.playlistTrackItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaylistTrackListHelpersTest {
    @Test
    fun buildSelectedPlaylistTracksDeduplicatesLegacyTrackIds() {
        val tracks = listOf(
            sampleTrack(id = "1", title = "Alpha"),
            sampleTrack(id = "2", title = "Beta"),
        )

        val selected = buildSelectedPlaylistTracks(
            trackIds = listOf("2", "1", "2"),
            allTracks = tracks,
        )

        // Order follows playlist trackIds (first occurrence after distinct), not title sort.
        assertEquals(listOf("2", "1"), selected.map { it.id })
    }

    @Test
    fun playlistTrackItemKeyStaysUniqueWhenTrackRepeats() {
        val repeated = sampleTrack(id = "track-1", title = "Repeated")

        val firstKey = playlistTrackItemKey(0, repeated)
        val secondKey = playlistTrackItemKey(1, repeated)

        assertNotEquals(firstKey, secondKey)
    }

    private fun sampleTrack(id: String, title: String): TrackRecord {
        return TrackRecord(
            id = id,
            uri = "content://media/$id",
            title = title,
            artist = "Artist $id",
            durationMs = 180_000L,
            importedAtEpochMs = 1_700_000_000_000L,
        )
    }
}
