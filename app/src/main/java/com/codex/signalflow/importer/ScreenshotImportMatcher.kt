package com.codex.signalflow.importer

import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.source.RemoteTrackCandidate

object ScreenshotImportMatcher {
    private const val LOW_CONFIDENCE_THRESHOLD = 0.46f
    private const val MATCH_THRESHOLD = 0.66f

    fun deduplicate(candidates: List<RecognizedTrackCandidate>): List<RecognizedTrackCandidate> {
        return candidates
            .groupBy { uniqueImportKey(it) }
            .values
            .map { group -> group.maxByOrNull { it.confidence } ?: group.first() }
            .sortedWith(compareBy<RecognizedTrackCandidate> { it.screenshotIndex }.thenBy { normalizeLoose(it.rawText) })
    }

    fun buildSearchQueries(candidate: RecognizedTrackCandidate): List<String> {
        val titleExact = normalizeForSearch(candidate.titleGuess)
        val artistExact = normalizeForSearch(effectiveArtist(candidate))
        val rawSegments = extractOrderedRawSegments(candidate.rawText)
        val rawJoined = rawSegments.joinToString(" ").trim()
        val titleFromRaw = rawSegments.firstOrNull().orEmpty()
        val artistFromRaw = rawSegments.drop(1).firstOrNull().orEmpty()

        return listOf(
            titleExact,
            listOf(titleExact, artistExact).joinToString(" ").trim(),
            listOf(titleFromRaw, artistExact).filter { it.isNotBlank() }.joinToString(" ").trim(),
            listOf(titleExact, artistFromRaw).filter { it.isNotBlank() }.joinToString(" ").trim(),
            rawJoined,
            listOf(artistExact, titleExact).joinToString(" ").trim(),
            normalizeForSearch(candidate.rawText.replace("|", " ")),
        )
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
    }

    fun pickBestMatch(
        candidate: RecognizedTrackCandidate,
        results: List<RemoteTrackCandidate>,
        playlistTracks: List<TrackRecord>,
    ): MatchedTrackCandidate {
        if (results.isEmpty()) {
            return MatchedTrackCandidate(
                recognized = candidate,
                bestMatch = null,
                matchScore = 0f,
                status = ScreenshotImportItemStatus.NOT_FOUND,
                message = "Совпадений в источнике не найдено.",
            )
        }

        val scored = results
            .distinctBy { it.stableKey() }
            .map { track -> track to score(candidate, track) }
            .sortedByDescending { it.second }

        val (bestMatch, bestScore) = scored.first()
        val alreadyInPlaylist = playlistTracks.any { track ->
            titleSimilarity(normalizedTitle(candidate), normalizeLoose(track.title)) >= 0.90f &&
                artistSimilarity(normalizedArtist(candidate), normalizeLoose(track.artist)) >= 0.72f
        }

        return when {
            alreadyInPlaylist -> MatchedTrackCandidate(
                recognized = candidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST,
                message = "Трек уже есть в плейлисте.",
            )

            bestScore >= MATCH_THRESHOLD -> MatchedTrackCandidate(
                recognized = candidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.MATCHED,
            )

            bestScore >= LOW_CONFIDENCE_THRESHOLD -> MatchedTrackCandidate(
                recognized = candidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.LOW_CONFIDENCE_MATCH,
                message = "Совпадение найдено с низкой уверенностью.",
            )

            else -> MatchedTrackCandidate(
                recognized = candidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.OCR_ONLY,
                message = "OCR сработал, но совпадение слишком слабое.",
            )
        }
    }

    fun normalizeLoose(value: String): String {
        return normalizeForSearch(value)
            .replace(Regex("""\b(feat|ft|prod|remix|version|edit|single|explicit)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun uniqueImportKey(candidate: RecognizedTrackCandidate): String {
        return listOf(
            normalizeLoose(candidate.artistGuess),
            normalizeLoose(candidate.titleGuess),
            normalizeLoose(candidate.rawText),
        ).joinToString("|")
    }

    private fun normalizedArtist(candidate: RecognizedTrackCandidate): String {
        val guessed = normalizeLoose(effectiveArtist(candidate))
        if (guessed.isNotBlank()) return guessed
        return extractOrderedRawSegments(candidate.rawText).drop(1).joinToString(" ").trim()
    }

    private fun normalizedTitle(candidate: RecognizedTrackCandidate): String {
        val guessed = normalizeLoose(candidate.titleGuess)
        if (guessed.isNotBlank()) return guessed
        return extractOrderedRawSegments(candidate.rawText).firstOrNull().orEmpty()
    }

    private fun score(candidate: RecognizedTrackCandidate, track: RemoteTrackCandidate): Float {
        val candidateTitle = normalizedTitle(candidate)
        val candidateArtist = normalizedArtist(candidate)
        val trackTitle = normalizeLoose(track.title)
        val trackArtist = normalizeLoose(track.artist)
        val candidateRaw = normalizeLoose(candidate.rawText.replace("|", " "))
        val combinedCandidateTitleFirst = listOf(candidateTitle, candidateArtist).filter { it.isNotBlank() }.joinToString(" ")
        val combinedTrackTitleFirst = listOf(trackTitle, trackArtist).filter { it.isNotBlank() }.joinToString(" ")

        val titleScore = titleSimilarity(candidateTitle, trackTitle)
        val artistScore = artistSimilarity(candidateArtist, trackArtist)
        val orderedTitleScore = orderSimilarity(candidateTitle, trackTitle)
        val combinedOrderScore = orderSimilarity(combinedCandidateTitleFirst, combinedTrackTitleFirst)
        val rawScore = containsSimilarity(candidateRaw, listOf(track.title, track.artist).joinToString(" "))
        val confidenceBoost = candidate.confidence.coerceIn(0f, 1f) * 0.05f

        return (
            titleScore * 0.42f +
                orderedTitleScore * 0.22f +
                artistScore * 0.16f +
                combinedOrderScore * 0.14f +
                rawScore * 0.06f +
                confidenceBoost
            ).coerceIn(0f, 1f)
    }

    private fun titleSimilarity(left: String, right: String): Float {
        if (left.isBlank() || right.isBlank()) return 0f
        return maxOf(
            tokenSimilarity(left, right),
            containsSimilarity(left, right),
            orderSimilarity(left, right),
        )
    }

    private fun artistSimilarity(left: String, right: String): Float {
        if (left.isBlank() || right.isBlank()) return 0.18f
        return maxOf(
            tokenSimilarity(left, right),
            containsSimilarity(left, right),
            orderSimilarity(left, right),
        )
    }

    private fun containsSimilarity(left: String, right: String): Float {
        val normalizedLeft = normalizeLoose(left)
        val normalizedRight = normalizeLoose(right)
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return 0f
        return when {
            normalizedLeft == normalizedRight -> 1f
            normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft) ->
                minOf(normalizedLeft.length, normalizedRight.length).toFloat() /
                    maxOf(normalizedLeft.length, normalizedRight.length).toFloat()
            else -> 0f
        }
    }

    private fun tokenSimilarity(left: String, right: String): Float {
        val leftTokens = tokenize(left).toSet()
        val rightTokens = tokenize(right).toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        val intersection = leftTokens.intersect(rightTokens).size.toFloat()
        val union = leftTokens.union(rightTokens).size.toFloat().coerceAtLeast(1f)
        return intersection / union
    }

    private fun orderSimilarity(left: String, right: String): Float {
        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        if (leftTokens == rightTokens) return 1f
        val lcsLength = longestCommonSubsequence(leftTokens, rightTokens)
        val exactPrefix = commonOrderedPrefix(leftTokens, rightTokens)
        val lcsScore = lcsLength.toFloat() / maxOf(leftTokens.size, rightTokens.size).toFloat()
        val prefixBonus = exactPrefix.toFloat() / maxOf(1, minOf(leftTokens.size, rightTokens.size)).toFloat()
        return (lcsScore * 0.75f + prefixBonus * 0.25f).coerceIn(0f, 1f)
    }

    private fun longestCommonSubsequence(left: List<String>, right: List<String>): Int {
        val dp = Array(left.size + 1) { IntArray(right.size + 1) }
        for (i in left.indices) {
            for (j in right.indices) {
                dp[i + 1][j + 1] = if (left[i] == right[j]) {
                    dp[i][j] + 1
                } else {
                    maxOf(dp[i][j + 1], dp[i + 1][j])
                }
            }
        }
        return dp[left.size][right.size]
    }

    private fun commonOrderedPrefix(left: List<String>, right: List<String>): Int {
        val limit = minOf(left.size, right.size)
        var prefix = 0
        while (prefix < limit && left[prefix] == right[prefix]) {
            prefix++
        }
        return prefix
    }

    private fun tokenize(value: String): List<String> {
        return normalizeLoose(value)
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 2 }
    }

    private fun extractOrderedRawSegments(rawText: String): List<String> {
        return rawText
            .split('|', '\n')
            .map { it.trim() }
            .filterNot(::looksLikeTimeTokenRaw)
            .map(::normalizeForSearch)
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.length >= 2 }
            .distinct()
    }

    private fun effectiveArtist(candidate: RecognizedTrackCandidate): String {
        val rawGuess = candidate.artistGuess.trim()
        if (rawGuess.isNotBlank() && !looksLikeTimeTokenRaw(rawGuess)) {
            val guessed = normalizeForSearch(rawGuess)
            if (guessed.isNotBlank()) return guessed
        }
        return extractOrderedRawSegments(candidate.rawText).drop(1).firstOrNull().orEmpty()
    }

    private fun looksLikeTimeToken(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.matches(Regex("""^\d{1,2}:\d{2}$"""))
    }

    private fun looksLikeTimeTokenRaw(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.matches(Regex("""^\d{1,2}:\d{2}$""")) ||
            trimmed.matches(Regex("""^\d{1,2}\s+\d{2}$"""))
    }

    private fun normalizeForSearch(value: String): String {
        val normalizedWhitespace = value
            .replace("\u00a0", " ")
            .replace("ё", "е")
            .replace("Ё", "Е")
            .replace(Regex("""[|/\\]+"""), " ")
            .replace(Regex("""[(){}\[\]]"""), " ")
            .replace(Regex("""[,&]+"""), " ")
            .replace(Regex("""[.!?;:+*=_~`"«»”“„]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val latinCount = normalizedWhitespace.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cyrillicCount = normalizedWhitespace.count { it in 'а'..'я' || it in 'А'..'Я' }

        return normalizedWhitespace
            .map { char ->
                when {
                    latinCount >= cyrillicCount -> cyrToLatinConfusable(char)
                    else -> latinToCyrConfusable(char)
                }
            }
            .joinToString("")
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cyrToLatinConfusable(char: Char): Char = when (char) {
        'А' -> 'A'
        'В' -> 'B'
        'Е' -> 'E'
        'К' -> 'K'
        'М' -> 'M'
        'Н' -> 'H'
        'О' -> 'O'
        'Р' -> 'P'
        'С' -> 'C'
        'Т' -> 'T'
        'У' -> 'Y'
        'Х' -> 'X'
        'а' -> 'a'
        'е' -> 'e'
        'о' -> 'o'
        'р' -> 'p'
        'с' -> 'c'
        'у' -> 'y'
        'х' -> 'x'
        'к' -> 'k'
        'м' -> 'm'
        'т' -> 't'
        else -> char
    }

    private fun latinToCyrConfusable(char: Char): Char = when (char) {
        'A' -> 'А'
        'B' -> 'В'
        'C' -> 'С'
        'E' -> 'Е'
        'H' -> 'Н'
        'K' -> 'К'
        'M' -> 'М'
        'O' -> 'О'
        'P' -> 'Р'
        'T' -> 'Т'
        'X' -> 'Х'
        'Y' -> 'У'
        'a' -> 'а'
        'c' -> 'с'
        'e' -> 'е'
        'o' -> 'о'
        'p' -> 'р'
        'x' -> 'х'
        'y' -> 'у'
        'k' -> 'к'
        'm' -> 'м'
        't' -> 'т'
        else -> char
    }
}

private fun RemoteTrackCandidate.stableKey(): String = "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"
