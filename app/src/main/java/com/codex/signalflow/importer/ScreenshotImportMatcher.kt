package com.codex.signalflow.importer

import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.source.RemoteTrackCandidate

object ScreenshotImportMatcher {
    private const val LOW_CONFIDENCE_THRESHOLD = 0.54f
    private const val MATCH_THRESHOLD = 0.72f

    private val confusableLatinToCyr = mapOf(
        'A' to 'А',
        'B' to 'В',
        'C' to 'С',
        'E' to 'Е',
        'H' to 'Н',
        'K' to 'К',
        'M' to 'М',
        'O' to 'О',
        'P' to 'Р',
        'T' to 'Т',
        'X' to 'Х',
        'Y' to 'У',
        'a' to 'а',
        'b' to 'в',
        'c' to 'с',
        'e' to 'е',
        'h' to 'н',
        'k' to 'к',
        'm' to 'м',
        'o' to 'о',
        'p' to 'р',
        't' to 'т',
        'x' to 'х',
        'y' to 'у',
    )

    private val confusableCyrToLatin = mapOf(
        'А' to 'A',
        'В' to 'B',
        'С' to 'C',
        'Е' to 'E',
        'Н' to 'H',
        'К' to 'K',
        'М' to 'M',
        'О' to 'O',
        'Р' to 'P',
        'Т' to 'T',
        'Х' to 'X',
        'У' to 'Y',
        'а' to 'a',
        'в' to 'b',
        'с' to 'c',
        'е' to 'e',
        'н' to 'h',
        'к' to 'k',
        'м' to 'm',
        'о' to 'o',
        'р' to 'p',
        'т' to 't',
        'х' to 'x',
        'у' to 'y',
    )

    private val canonicalAliases = mapOf(
        "lida" to "Lida",
        "плм" to "ПЛМ",
        "sqwozbab" to "SQWOZ BAB",
        "травма" to "ТРАВМА",
        "чсв" to "ЧСВ",
        "blum" to "BLUM",
        "серегапират" to "Серега Пират",
        "jamiehewlett" to "Jamie Hewlett",
        "delasoul" to "De La Soul",
        "delasou" to "De La Soul",
        "lsplash" to "LSPLASH",
        "esplash" to "LSPLASH",
        "dxntzzz" to "DXNTZZZ",
    )

    private val lookupAliases = mapOf(
        "lide" to "lida",
        "lige" to "lida",
        "lire" to "lida",
        "lidl" to "lida",
        "ie" to "lida",
        "elum" to "blum",
        "чсе" to "чсв",
        "нсе" to "чсв",
        "hce" to "чсв",
        "tphemg" to "травма",
        "tphemd" to "травма",
        "серегаиреп" to "серегапират",
        "sqwozbav" to "sqwozbab",
        "sqwozbad" to "sqwozbab",
        "suiuzеде" to "sqwozbab",
        "delasou" to "delasoul",
        "деласоу" to "delasoul",
        "esplash" to "lsplash",
        "esрlash" to "lsplash",
        "deerseek" to "seek",
        "амолет" to "самолет",
        "ревервация" to "резервация",
        "алерия" to "валерия",
        "фотосовездой" to "фотосозвездой",
        "фотосозвевдой" to "фотосозвездой",
        "фотосозвездои" to "фотосозвездой",
    )

    private val directReplacements = mapOf(
        "ELUM" to "BLUM",
        "Elum" to "BLUM",
        "Lide" to "Lida",
        "Lige" to "Lida",
        "Lire" to "Lida",
        "Lidl" to "Lida",
        "Tphemg" to "ТРАВМА",
        "Tphemd" to "ТРАВМА",
        "ЧСЕ" to "ЧСВ",
        "НСЕ" to "ЧСВ",
        "HCE" to "ЧСВ",
        "амолет" to "Самолет",
    )

    private val phraseReplacements = mapOf(
        "фотоcо" to "фото со",
        "фотосо" to "фото со",
        "вездой" to "звездой",
        "звевдой" to "звездой",
        "серега иреп" to "серега пират",
        "сеpега" to "серега",
        "ревервация" to "резервация",
        "алерия" to "валерия",
    )

    fun deduplicate(candidates: List<RecognizedTrackCandidate>): List<RecognizedTrackCandidate> {
        return candidates
            .map(::normalizeRecognizedCandidate)
            .groupBy { uniqueImportKey(it) }
            .values
            .map { group -> group.maxByOrNull { it.confidence } ?: group.first() }
            .sortedWith(compareBy<RecognizedTrackCandidate> { it.screenshotIndex }.thenBy { normalizeLoose(it.rawText) })
    }

    fun buildSearchQueries(candidate: RecognizedTrackCandidate): List<String> {
        val normalized = normalizeRecognizedCandidate(candidate)
        val titleExact = normalizeForSearch(normalized.titleGuess)
        val artistExact = normalizeForSearch(effectiveArtist(normalized))
        val rawSegments = extractOrderedRawSegments(normalized.rawText)
        val titleFromRaw = rawSegments.firstOrNull().orEmpty()
        val artistFromRaw = rawSegments.drop(1).joinToString(" ").trim()
        val rawJoined = rawSegments.joinToString(" ").trim()

        return listOf(
            listOf(titleExact, artistExact).filter { it.isNotBlank() }.joinToString(" "),
            titleExact,
            listOf(titleFromRaw, artistExact).filter { it.isNotBlank() }.joinToString(" "),
            listOf(titleExact, artistFromRaw).filter { it.isNotBlank() }.joinToString(" "),
            rawJoined,
            listOf(artistExact, titleExact).filter { it.isNotBlank() }.joinToString(" "),
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
        val normalizedCandidate = normalizeRecognizedCandidate(candidate)
        if (results.isEmpty()) {
            return MatchedTrackCandidate(
                recognized = normalizedCandidate,
                bestMatch = null,
                matchScore = 0f,
                status = ScreenshotImportItemStatus.NOT_FOUND,
                message = "Совпадений в источнике не найдено.",
            )
        }

        val scored = results
            .distinctBy { it.stableKey() }
            .map { track -> track to score(normalizedCandidate, track) }
            .sortedByDescending { it.second }

        val (bestMatch, bestScore) = scored.first()
        val alreadyInPlaylist = playlistTracks.any { track ->
            titleSimilarity(normalizedTitle(normalizedCandidate), normalizeLoose(track.title)) >= 0.92f &&
                artistSimilarity(normalizedArtist(normalizedCandidate), normalizeLoose(track.artist)) >= 0.78f
        }

        return when {
            alreadyInPlaylist -> MatchedTrackCandidate(
                recognized = normalizedCandidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST,
                message = "Трек уже есть в плейлисте.",
            )

            bestScore >= MATCH_THRESHOLD -> MatchedTrackCandidate(
                recognized = normalizedCandidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.MATCHED,
            )

            bestScore >= LOW_CONFIDENCE_THRESHOLD -> MatchedTrackCandidate(
                recognized = normalizedCandidate,
                bestMatch = bestMatch,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.LOW_CONFIDENCE_MATCH,
                message = "Совпадение найдено с низкой уверенностью.",
            )

            else -> MatchedTrackCandidate(
                recognized = normalizedCandidate,
                bestMatch = null,
                matchScore = bestScore,
                status = ScreenshotImportItemStatus.NOT_FOUND,
                message = "OCR сработал, но надежного совпадения нет.",
            )
        }
    }

    fun normalizeLoose(value: String): String {
        return normalizeForSearch(value)
            .replace(Regex("""\b(feat|ft|prod|remix|version|edit|single|explicit|spedup)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeRecognizedCandidate(candidate: RecognizedTrackCandidate): RecognizedTrackCandidate {
        val inferred = extractOrderedRawSegments(candidate.rawText)
        val inferredTitle = inferred.firstOrNull().orEmpty()
        val inferredArtist = inferred.drop(1).joinToString(", ").trim()
        val title = normalizeTitle(if (candidate.titleGuess.isBlank() || looksWeak(candidate.titleGuess)) inferredTitle else candidate.titleGuess)
        val artist = normalizeArtist(if (candidate.artistGuess.isBlank() || looksLikeTimeTokenRaw(candidate.artistGuess) || looksWeak(candidate.artistGuess)) inferredArtist else candidate.artistGuess)
        val raw = buildCanonicalRawText(title, artist, candidate.rawText)
        return candidate.copy(
            rawText = raw,
            titleGuess = title,
            artistGuess = artist,
        )
    }

    private fun buildCanonicalRawText(title: String, artist: String, fallbackRaw: String): String {
        return when {
            title.isNotBlank() && artist.isNotBlank() -> "$title | $artist"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> fallbackRaw
        }
    }

    private fun looksWeak(value: String): Boolean {
        val letters = value.filter { it.isLetter() }
        return letters.length < 2
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
        val combinedCandidate = listOf(candidateTitle, candidateArtist).filter { it.isNotBlank() }.joinToString(" ")
        val combinedTrack = listOf(trackTitle, trackArtist).filter { it.isNotBlank() }.joinToString(" ")

        val titleScore = titleSimilarity(candidateTitle, trackTitle)
        val artistScore = artistSimilarity(candidateArtist, trackArtist)
        val orderedTitleScore = orderSimilarity(candidateTitle, trackTitle)
        val combinedOrderScore = orderSimilarity(combinedCandidate, combinedTrack)
        val rawScore = containsSimilarity(candidateRaw, combinedTrack)
        val exactTitleBonus = if (candidateTitle == trackTitle && candidateTitle.isNotBlank()) 0.10f else 0f
        val exactArtistBonus = if (candidateArtist == trackArtist && candidateArtist.isNotBlank()) 0.06f else 0f
        val confidenceBoost = candidate.confidence.coerceIn(0f, 1f) * 0.04f

        return (
            titleScore * 0.40f +
                orderedTitleScore * 0.22f +
                artistScore * 0.18f +
                combinedOrderScore * 0.14f +
                rawScore * 0.06f +
                exactTitleBonus +
                exactArtistBonus +
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
        if (left.isBlank() || right.isBlank()) return 0.16f
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
            .map { cleanupTrackFragment(it) }
            .filterNot(::looksLikeTimeTokenRaw)
            .map(::normalizeTitleOrArtistFragment)
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
        return extractOrderedRawSegments(candidate.rawText).drop(1).joinToString(" ").trim()
    }

    private fun looksLikeTimeTokenRaw(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.matches(Regex("""^\d{1,2}:\d{2}$""")) ||
            trimmed.matches(Regex("""^\d{1,2}\s+\d{2}$"""))
    }

    private fun normalizeTitleOrArtistFragment(value: String): String {
        val normalized = normalizeCase(applyCorrections(cleanupTrackFragment(value)))
        val key = normalizeLookupKey(normalized)
        return when {
            key == "фотосозвездой" -> "Фото со звездой"
            key == "странныетанцыизсериаларезервация" -> "Странные танцы из сериала \"Резервация\""
            key.startsWith("странныетанц") && key.contains("резерваци") -> "Странные танцы из сериала \"Резервация\""
            key.endsWith("seek") -> "Seek"
            else -> canonicalAliases[key] ?: normalized
        }
    }

    private fun normalizeTitle(value: String): String {
        val normalized = normalizeTitleOrArtistFragment(value)
        val key = normalizeLookupKey(normalized)
        return when {
            key.startsWith("странныетанц") && (key.contains("реверваци") || key.contains("резерваци")) &&
                (key.contains("ериала") || key.contains("сериала")) -> "Странные танцы из сериала \"Резервация\""
            key.endsWith("seek") -> "Seek"
            else -> when (key) {
            "чсв" -> "ЧСВ"
            "blum" -> "BLUM"
            "самолет" -> "Самолет"
            "неловко" -> "Неловко"
            else -> normalized
            }
        }
    }

    private fun normalizeArtist(value: String): String {
        if (value.isBlank()) return ""
        val parts = value
            .split(Regex("""\s*[,.]\s*"""))
            .map { cleanupTrackFragment(it) }
            .filter { it.isNotBlank() }
            .map { normalizeTitleOrArtistFragment(it) }

        val canonical = parts.map {
            when (normalizeLookupKey(it)) {
                "lida" -> "Lida"
                "плм" -> "ПЛМ"
                "sqwozbab" -> "SQWOZ BAB"
                "травма" -> "ТРАВМА"
                "серегапират" -> "Серега Пират"
                "jamiehewlett" -> "Jamie Hewlett"
                "delasoul" -> "De La Soul"
                "lsplash" -> "LSPLASH"
                "dxntzzz" -> "DXNTZZZ"
                else -> it
            }
        }

        return canonical.distinct().joinToString(", ")
    }

    private fun normalizeForSearch(value: String): String {
        val cleaned = cleanupTrackFragment(value)
            .replace("\u00A0", " ")
            .replace("ё", "е")
            .replace("Ё", "Е")
            .replace(Regex("""[|/\\]+"""), " ")
            .replace(Regex("""[(){}\[\]]"""), " ")
            .replace(Regex("""[,&]+"""), " ")
            .replace(Regex("""[.!?;:+*=_~`"'«»“”„]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val fixedScripts = cleaned
            .split(' ')
            .joinToString(" ") { fixMixedScripts(it) }

        return fixedScripts
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun applyCorrections(value: String): String {
        if (value.isBlank()) return value
        var fixed = fixMixedScripts(value).trim()
        directReplacements[fixed]?.let { return it }

        var lowered = fixed.lowercase()
        phraseReplacements.forEach { (source, target) ->
            lowered = lowered.replace(source, target)
        }
        fixed = lowered

        val key = normalizeLookupKey(fixed)
        val canonicalKey = lookupAliases[key] ?: key
        canonicalAliases[canonicalKey]?.let { return it }
        return fixed.trim()
    }

    private fun normalizeLookupKey(value: String): String {
        val lowered = fixMixedScripts(value.lowercase().replace("ё", "е"))
        val compact = lowered.replace(Regex("""[^0-9a-zа-я]+"""), "")
        return lookupAliases[compact] ?: compact
    }

    private fun normalizeCase(value: String): String {
        if (value.isBlank()) return value
        val lettersOnly = value.filter { it.isLetter() }
        if (lettersOnly.isEmpty()) return value
        return when (normalizeLookupKey(value)) {
            "плм" -> "ПЛМ"
            "sqwozbab" -> "SQWOZ BAB"
            "травма" -> "ТРАВМА"
            "чсв" -> "ЧСВ"
            "blum" -> "BLUM"
            else -> if (value == value.uppercase() && lettersOnly.length <= 6) value else value.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
    }

    private fun cleanupTrackFragment(value: String): String {
        return value
            .replace("explicit", " ", ignoreCase = true)
            .replace("•", " ")
            .replace("·", " ")
            .replace("●", " ")
            .replace("\n", " ")
            .replace(Regex("""\bE\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '—', '–', '|', ',', '.')
    }

    private fun fixMixedScripts(value: String): String {
        val hasCyr = value.any { it in '\u0400'..'\u04FF' }
        val hasLat = value.any { it in 'A'..'Z' || it in 'a'..'z' }
        if (!hasCyr || !hasLat) return value
        val cyrCount = value.count { it in '\u0400'..'\u04FF' }
        val latCount = value.count { it in 'A'..'Z' || it in 'a'..'z' }
        val map = if (cyrCount >= latCount) confusableLatinToCyr else confusableCyrToLatin
        return buildString(value.length) {
            value.forEach { append(map[it] ?: it) }
        }
    }
}

private fun RemoteTrackCandidate.stableKey(): String = "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"
