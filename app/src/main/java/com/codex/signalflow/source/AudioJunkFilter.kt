package com.codex.signalflow.source

/**
 * Filter that detects audio alterations, spam, bot signatures, snippet cuts, and unwanted modifications
 * (e.g. sped up, slowed, reverb, bassboost, @nightcorebot, snippets, trailers, intros, covers).
 */
object AudioJunkFilter {

    data class JunkRule(
        val category: String,
        val pattern: Regex,
    )

    val RULES: List<JunkRule> = listOf(
        // 1. Social watermarks & Telegram/TikTok bot tags
        JunkRule("bot_watermark", Regex("""(?i)(?:@\w+|t\.me/\S+|vk\.com/\S+|\b(?:tiktok|tik\s*tok|тикток|reels|shorts|vkmusic|вк\s*музыка)\b)""")),

        // 2. Sped Up & Nightcore modifications
        JunkRule("sped_up", Regex("""(?i)\b(?:sped\s*up|speed\s*up|speedup|speed-up|nightcore|найткор|ускоренн(?:ая|ый|о|ые|ую)|ускорено|fast\s*ver(?:sion)?|быстрая\s*версия)\b""")),

        // 3. Slowed & Daycore modifications
        JunkRule("slowed", Regex("""(?i)\b(?:slowed(?:\s*(?:\+|and|&)?\s*reverb)?|slowed\s*down|slow\s*down|slow-down|daycore|дейкор|замедленн(?:ая|ый|о|ые|ую)|замедлено)\b""")),

        // 4. Reverb modifications
        JunkRule("reverb", Regex("""(?i)\b(?:reverb|реверб|reverbed|with\s*reverb|\+\s*reverb|эхо)\b""")),

        // 5. Bassboost & Distortion
        JunkRule("bassboost", Regex("""(?i)\b(?:bass\s*boosted|bassboosted|bass\s*boost|басс\s*буст|бассбуст(?:ед)?|бас\s*буст|boosted\s*bass|глубокий\s*бас|distorted|дисторт(?:ед)?)\b""")),

        // 6. Snippets, Trailers, Teasers, Previews, Ringtones, Cuts
        JunkRule("snippet_trailer", Regex("""(?i)\b(?:snippet|сниппет|отрывок|кусок|trailer|трейлер|teaser|тизер|preview|превью|ringtone|рингтон|на\s*звонок|short\s*ver(?:sion)?|короткая\s*версия)\b""")),

        // 7. Intros & Outros (when original track is not intro/outro)
        JunkRule("intro_outro", Regex("""(?i)\b(?:intro|интро|outro|аутро|opening|опенинг|ending|эндинг|skit|скит)\b""")),

        // 8. Audio gimmicks (8D audio, pitch shift)
        JunkRule("audio_gimmick", Regex("""(?i)\b(?:8d(?:\s*audio)?|16d|3d\s*audio|pitch\s*up|pitch\s*down|pitched|high\s*pitch|low\s*pitch)\b""")),

        // 9. Covers, Karaoke, Minuses, Instrumentals (when target is not cover/instrumental)
        JunkRule("cover_karaoke", Regex("""(?i)\b(?:cover|кавер|караоке|karaoke|tribute|трибьют|пародия|parody|минус(?:овка)?|minus|instrumental|инструментал)\b""")),

        // 10. Unofficial remixes, bootlegs, mashups, club edits (when target is not remix)
        JunkRule("remix_mashup", Regex("""(?i)\b(?:remix|ремикс|bootleg|бутлег|mashup|мэшап|flip|club\s*mix|vip\s*edit)\b""")),
    )

    /**
     * Checks if candidate title or artist contains unwanted modifications/junk
     * that are NOT present in the genuine target track metadata.
     */
    fun hasUnwantedJunk(
        candidateTitle: String,
        candidateArtist: String,
        targetTitle: String,
        targetArtist: String,
    ): Boolean {
        val candidateText = "$candidateTitle $candidateArtist"
        val targetText = "$targetTitle $targetArtist"

        for (rule in RULES) {
            if (rule.pattern.containsMatchIn(candidateText)) {
                // If candidate matches the pattern, verify if target also legitimately contains it
                if (!rule.pattern.containsMatchIn(targetText)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns a list of detected junk rule categories for debugging/logging purposes.
     */
    fun detectJunkReasons(
        candidateTitle: String,
        candidateArtist: String,
        targetTitle: String,
        targetArtist: String,
    ): List<String> {
        val candidateText = "$candidateTitle $candidateArtist"
        val targetText = "$targetTitle $targetArtist"
        val detected = mutableListOf<String>()

        for (rule in RULES) {
            if (rule.pattern.containsMatchIn(candidateText) && !rule.pattern.containsMatchIn(targetText)) {
                detected.add(rule.category)
            }
        }
        return detected
    }

    /**
     * Finds the cleanest, highest-scoring genuine candidate matching target metadata,
     * or returns null if all candidates are junk or below match confidence.
     */
    fun findBestCandidate(
        targetTitle: String,
        targetArtist: String,
        candidates: List<RemoteTrackCandidate>,
    ): RemoteTrackCandidate? {
        if (candidates.isEmpty()) return null

        // 1. Strict filter against unwanted audio alterations, bots, snippets, etc.
        val cleanCandidates = candidates.filterNot { candidate ->
            hasUnwantedJunk(
                candidateTitle = candidate.title,
                candidateArtist = candidate.artist,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
            )
        }

        if (cleanCandidates.isEmpty()) {
            return null
        }

        // 2. Score remaining clean candidates
        val targetTokens = tokenize(normalizeForComparison(targetTitle))
        val targetArtistTokens = tokenize(normalizeForComparison(targetArtist))

        val scored = cleanCandidates.map { candidate ->
            val score = computeMatchScore(
                candidateTitle = candidate.title,
                candidateArtist = candidate.artist,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetTokens = targetTokens,
                targetArtistTokens = targetArtistTokens,
            )
            candidate to score
        }

        return scored
            .filter { it.second >= 0.55 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun computeMatchScore(
        candidateTitle: String,
        candidateArtist: String,
        targetTitle: String,
        targetArtist: String,
        targetTokens: Set<String> = tokenize(normalizeForComparison(targetTitle)),
        targetArtistTokens: Set<String> = tokenize(normalizeForComparison(targetArtist)),
    ): Double {
        val cTitleNorm = normalizeForComparison(candidateTitle)
        val tTitleNorm = normalizeForComparison(targetTitle)
        val cArtistNorm = normalizeForComparison(candidateArtist)
        val tArtistNorm = normalizeForComparison(targetArtist)

        val cTitleTokens = tokenize(cTitleNorm)
        val titleIntersection = targetTokens.intersect(cTitleTokens).size
        val titleUnion = targetTokens.union(cTitleTokens).size.coerceAtLeast(1)
        val titleJaccard = titleIntersection.toDouble() / titleUnion.toDouble()
        val allTargetTokensPresent = targetTokens.isNotEmpty() && targetTokens.all { it in cTitleTokens }
        val exactTitle = cTitleNorm == tTitleNorm

        var titleScore = when {
            exactTitle -> 1.0
            allTargetTokensPresent -> (0.80 + 0.20 * titleJaccard).coerceAtMost(0.98)
            else -> titleJaccard * 0.85
        }

        if (cTitleNorm.length > tTitleNorm.length && tTitleNorm.isNotEmpty()) {
            val ratio = tTitleNorm.length.toDouble() / cTitleNorm.length.toDouble()
            if (ratio < 0.6) {
                titleScore *= (0.5 + 0.5 * ratio)
            }
        }

        val cArtistTokens = tokenize(cArtistNorm)
        val artistIntersection = targetArtistTokens.intersect(cArtistTokens).size
        val artistOverlap = artistIntersection > 0
        val exactArtist = cArtistNorm == tArtistNorm
        val substringArtist = cArtistNorm.contains(tArtistNorm) || tArtistNorm.contains(cArtistNorm)

        val artistScore = when {
            exactArtist -> 1.0
            substringArtist -> 0.90
            artistOverlap -> 0.75
            candidateArtist.isBlank() || candidateArtist.contains("неизвестный", ignoreCase = true) -> 0.40
            else -> 0.0
        }

        if (artistScore == 0.0) return 0.0

        return titleScore * 0.65 + artistScore * 0.35
    }

    fun normalizeForComparison(str: String): String =
        str.replace('ё', 'е').lowercase().replace(Regex("[^a-zа-я0-9\\s]"), " ").trim().replace(Regex("\\s+"), " ")

    fun tokenize(str: String): Set<String> =
        str.split(" ").filter { it.isNotBlank() }.toSet()
}
