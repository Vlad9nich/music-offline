package com.codex.signalflow.importer

import com.codex.signalflow.source.RemoteTrackCandidate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ScreenshotImportPhase {
    IDLE,
    UPLOADING,
    RECOGNIZING,
    MATCHING,
    COMPLETED,
    FAILED,
}

enum class ScreenshotImportItemStatus {
    RECOGNIZED,
    MATCHED,
    LOW_CONFIDENCE_MATCH,
    OCR_ONLY,
    NOT_FOUND,
    ALREADY_IN_PLAYLIST,
    ADDING,
    ADDED,
    ADD_FAILED,
}

data class RecognizedTrackCandidate(
    val candidateId: String,
    val screenshotIndex: Int,
    val rawText: String,
    val artistGuess: String,
    val titleGuess: String,
    val confidence: Float,
)

data class MatchedTrackCandidate(
    val recognized: RecognizedTrackCandidate,
    val bestMatch: RemoteTrackCandidate?,
    val matchScore: Float,
    val status: ScreenshotImportItemStatus,
    val message: String? = null,
)

data class ScreenshotImportSession(
    val playlistId: String,
    val playlistName: String,
    val phase: ScreenshotImportPhase = ScreenshotImportPhase.IDLE,
    val totalScreenshots: Int = 0,
    val processedScreenshots: Int = 0,
    val statusMessage: String = "",
    val items: List<MatchedTrackCandidate> = emptyList(),
    val errorMessage: String? = null,
) {
    val recognizedCount: Int
        get() = items.count { it.status != ScreenshotImportItemStatus.NOT_FOUND }

    val matchedCount: Int
        get() = items.count {
            it.status in setOf(
                ScreenshotImportItemStatus.MATCHED,
                ScreenshotImportItemStatus.LOW_CONFIDENCE_MATCH,
                ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST,
                ScreenshotImportItemStatus.ADDING,
                ScreenshotImportItemStatus.ADDED,
            )
        }

    val addedCount: Int
        get() = items.count { it.status == ScreenshotImportItemStatus.ADDED }
}

@Serializable
data class OcrCreateJobResponse(
    val jobId: String,
)

@Serializable
data class OcrJobResponse(
    val jobId: String,
    val status: String,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val error: String? = null,
    val candidates: List<OcrCandidatePayload> = emptyList(),
)

@Serializable
data class OcrSingleResponse(
    val candidates: List<OcrCandidatePayload> = emptyList(),
)

@Serializable
data class OcrNormalizeRequest(
    val candidates: List<OcrCandidatePayload> = emptyList(),
)

@Serializable
data class OcrCandidatePayload(
    val candidateId: String,
    val screenshotIndex: Int,
    val rawText: String,
    val artistGuess: String = "",
    val titleGuess: String = "",
    val confidence: Float = 0f,
    @SerialName("bbox") val boundingBox: List<Int>? = null,
)

internal fun OcrCandidatePayload.toRecognizedCandidate(): RecognizedTrackCandidate {
    return RecognizedTrackCandidate(
        candidateId = candidateId,
        screenshotIndex = screenshotIndex,
        rawText = rawText,
        artistGuess = artistGuess,
        titleGuess = titleGuess,
        confidence = confidence,
    )
}
