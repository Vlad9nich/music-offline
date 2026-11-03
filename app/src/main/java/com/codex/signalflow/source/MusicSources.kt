package com.codex.signalflow.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class SourceDescriptor(
    val id: String,
    val name: String,
    val status: String,
    val description: String,
    val isEnabled: Boolean,
)

data class RemoteTrackCandidate(
    val sourceId: String,
    val title: String,
    val artist: String,
    val detailUrl: String,
    val downloadUrl: String? = null,
    val coverUrl: String? = null,
)

data class DownloadBlueprint(
    val title: String,
    val artist: String,
    val url: String,
    val suggestedFilename: String,
    val refererUrl: String? = null,
    val coverUrl: String? = null,
)

interface MusicSource {
    val descriptor: SourceDescriptor
    suspend fun search(query: String): List<RemoteTrackCandidate>
    suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint
}

abstract class HtmlMusicSourceTemplate(
    override val descriptor: SourceDescriptor,
) : MusicSource {
    override suspend fun search(query: String): List<RemoteTrackCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val searchUrl = buildSearchUrl(query.trim())
        debugLog("search start source=${descriptor.id} query=$query url=$searchUrl")
        val document = requestDocument(searchUrl)
        val results = mapSearchDocument(document)
        debugLog("search done source=${descriptor.id} query=$query results=${results.size} location=${document.location()}")
        results
    }

    override suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint = withContext(Dispatchers.IO) {
        debugLog("resolve start source=${descriptor.id} detail=${track.detailUrl} direct=${track.downloadUrl}")
        val blueprint = mapTrackDetails(track, requestDocument(track.detailUrl, referer = "https://web.ligaudio.ru/"))
        debugLog("resolve done source=${descriptor.id} url=${blueprint.url}")
        blueprint
    }

    protected abstract fun buildSearchUrl(query: String): String
    protected abstract fun mapSearchDocument(document: Document): List<RemoteTrackCandidate>
    protected abstract fun mapTrackDetails(track: RemoteTrackCandidate, document: Document): DownloadBlueprint

    protected fun requestDocument(url: String, referer: String? = null): Document = baseRequest(url, referer).get()
    protected fun Element.textOrEmpty(selector: String): String = selectFirst(selector)?.text()?.trim().orEmpty()

    companion object {
        private const val TAG = "YaNeoDexSource"
        protected const val DEFAULT_REFERER = "https://web.ligaudio.ru/"

        private fun debugLog(message: String) {
            runCatching { Log.d(TAG, message) }
        }
    }
}

class LigaudioSource : HtmlMusicSourceTemplate(
    descriptor = SourceDescriptor(
        id = "ligaudio",
        name = "Ligaudio",
        status = "Активен",
        description = "Поиск и скачивание с web.ligaudio.ru.",
        isEnabled = true,
    ),
) {
    override fun buildSearchUrl(query: String): String {
        return "https://web.ligaudio.ru/mp3/${MusicSourceCatalog.encodedPathSegment(query)}"
    }

    override fun mapSearchDocument(document: Document): List<RemoteTrackCandidate> {
        return parseTrackCards(document).map { listing ->
            RemoteTrackCandidate(
                sourceId = descriptor.id,
                title = listing.title,
                artist = listing.artist,
                detailUrl = listing.detailUrl,
                downloadUrl = listing.directDownloadUrl,
            )
        }
    }

    internal fun parseSearchHtml(html: String, baseUrl: String): List<RemoteTrackCandidate> {
        return mapSearchDocument(Jsoup.parse(html, baseUrl))
    }

    override fun mapTrackDetails(track: RemoteTrackCandidate, document: Document): DownloadBlueprint {
        val directUrl = track.downloadUrl?.takeIf { looksLikeDownloadUrl(it) }
        if (!directUrl.isNullOrBlank()) {
            return DownloadBlueprint(
                title = track.title,
                artist = track.artist,
                url = directUrl,
                suggestedFilename = "${track.artist} - ${track.title}.mp3",
                refererUrl = "https://web.ligaudio.ru/",
            )
        }

        val pageBaseUrl = document.location().ifBlank { document.baseUri() }.ifBlank { track.detailUrl }
        val listing = parseTrackCards(document).firstOrNull { it.matches(track) }
        val resolvedUrl = firstNotBlank(
            listing?.directDownloadUrl?.takeIf { looksLikeDownloadUrl(it) },
            extractDirectDownloadUrl(document.body(), document.body(), pageBaseUrl),
        )?.takeIf { looksLikeDownloadUrl(it) }
            ?: error("Не удалось найти прямую ссылку на скачивание для ${track.artist} - ${track.title}.")

        return DownloadBlueprint(
            title = track.title,
            artist = track.artist,
            url = resolvedUrl,
            suggestedFilename = "${track.artist} - ${track.title}.mp3",
            refererUrl = "https://web.ligaudio.ru/",
        )
    }

    private fun parseTrackCards(document: Document): List<LigaudioListing> {
        val items = document.select("#result .item[itemprop='track']").ifEmpty { document.select("#result .item") }
        if (items.isEmpty()) return emptyList()
        val baseUrl = document.location().ifBlank { document.baseUri() }.ifBlank { "https://web.ligaudio.ru/" }
        val listings = linkedMapOf<String, LigaudioListing>()

        for (item in items) {
            val container = item.selectFirst("div") ?: item
            val title = extractTitle(item, container)
            val artist = extractArtist(item, container)
            val directDownloadUrl = extractDirectDownloadUrl(item, container, baseUrl)
            if (title.isBlank() || directDownloadUrl.isNullOrBlank()) continue

            val detailUrl = normalizeUrlCandidate(
                item.selectFirst(".autor a[href*='/mp3/']")?.attr("href"),
                baseUrl,
            ) ?: "https://web.ligaudio.ru/"

            val listing = LigaudioListing(
                title = title,
                artist = artist.ifBlank { "Неизвестный исполнитель" },
                detailUrl = detailUrl,
                directDownloadUrl = directDownloadUrl,
            )
            listings.putIfAbsent(listing.uniqueKey(), listing)
        }

        return listings.values.toList()
    }

    private fun extractTitle(item: Element, container: Element): String {
        return firstNotBlank(
            item.textOrEmpty(".title .title"),
            item.textOrEmpty("span.title[itemprop='name']"),
            container.textOrEmpty(".title"),
            parseArtistTitlePair(container.text()).second,
        ).orEmpty()
    }

    private fun extractArtist(item: Element, container: Element): String {
        return firstNotBlank(
            item.textOrEmpty(".title .autor"),
            item.textOrEmpty("span.autor[itemprop='byArtist']"),
            item.select(".autor a[href*='/mp3/']").joinToString(", ") { it.text().trim() }.takeIf { it.isNotBlank() },
            parseArtistTitlePair(container.text()).first,
        ).orEmpty()
    }

    private fun extractDirectDownloadUrl(item: Element, container: Element, baseUrl: String): String? {
        val selectorCandidates = listOf(
            "a.down[href]",
            "a.download[href]",
            "a[href*='storage'][href]",
            "a[href*='.mp3'][href]",
            "a[href*='.m4a'][href]",
            "a[href*='.aac'][href]",
            "a[href*='.ogg'][href]",
            "a[download]",
            "[data-url]",
            "[data-file]",
            "[data-download]",
        )
        selectorCandidates.forEach { selector ->
            val direct = listOf(item, container).firstNotNullOfOrNull { element ->
                element.selectFirst(selector)?.let { candidate ->
                    normalizeUrlCandidate(
                        firstNotBlank(
                            candidate.absUrl("href"),
                            candidate.attr("href"),
                            candidate.attr("data-url"),
                            candidate.attr("data-download"),
                            candidate.attr("data-file"),
                            candidate.attr("download"),
                        ),
                        baseUrl,
                    )?.takeIf { looksLikeDownloadUrl(it) }
                }
            }
            if (!direct.isNullOrBlank()) return direct
        }

        val attributeCandidates = listOf(item.outerHtml(), container.outerHtml(), item.attr("onclick"), container.attr("onclick"))
        val regexCandidates = listOf(
            Regex("""https?://[^\"'\s<>]+?(?:\.mp3|\.m4a|\.aac|\.ogg|\.wav|\.flac)(?:\?[^\"'\s<>]*)?""", RegexOption.IGNORE_CASE),
            Regex("""//[^\"'\s<>]+?(?:\.mp3|\.m4a|\.aac|\.ogg|\.wav|\.flac)(?:\?[^\"'\s<>]*)?""", RegexOption.IGNORE_CASE),
            Regex("""(?:href|src|data-url|data-file|data-download)=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE),
        )
        attributeCandidates.forEach { raw ->
            regexCandidates.forEach { regex ->
                val match = regex.find(raw) ?: return@forEach
                val candidate = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
                val normalized = normalizeUrlCandidate(candidate, baseUrl)
                if (!normalized.isNullOrBlank() && looksLikeDownloadUrl(normalized)) return normalized
            }
        }
        return null
    }

    private fun normalizeUrlCandidate(value: String?, baseUrl: String): String? {
        val trimmed = value?.trim().orEmpty().removePrefix("javascript:").trim('"', '\'', ' ').replace("\\/", "/").replace("&amp;", "&")
        if (trimmed.isBlank()) return null
        val normalized = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") || trimmed.startsWith("?") -> URL(URL(baseUrl), trimmed).toString()
            else -> URL(URL(baseUrl), trimmed).toString()
        }
        return normalized.takeUnless { isFalseDownloadPage(it) }
    }

    private fun looksLikeDownloadUrl(url: String): Boolean {
        val lowered = url.lowercase()
        if (isFalseDownloadPage(lowered)) return false
        return lowered.contains("storage.lightaudio.ru") || lowered.contains("storage1.lightaudio.ru") ||
            lowered.contains("storage2.lightaudio.ru") || lowered.contains("storage3.lightaudio.ru") ||
            lowered.contains("storage4.lightaudio.ru") || lowered.contains("storage5.lightaudio.ru") ||
            lowered.contains("storage6.lightaudio.ru") || lowered.contains("storage7.lightaudio.ru") ||
            lowered.contains("storage8.lightaudio.ru") || lowered.contains("storage9.lightaudio.ru") ||
            lowered.contains("/download/") || lowered.contains(".mp3") || lowered.contains(".m4a") ||
            lowered.contains(".aac") || lowered.contains(".ogg") || lowered.contains(".wav") || lowered.contains(".flac")
    }

    private fun isFalseDownloadPage(url: String?): Boolean {
        val lowered = url?.trim()?.lowercase().orEmpty()
        if (lowered.isBlank()) return false
        return ((lowered.contains("lightaudio.ru/mp3") || lowered.contains("web.ligaudio.ru/mp3")) && !lowered.contains(".mp3")) ||
            lowered.endsWith("/mp3") || lowered.endsWith("/mp3/")
    }

    private fun parseArtistTitlePair(rawText: String): Pair<String, String> {
        val cleaned = rawText.replace(Regex("\\s+"), " ").replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), "").trim(' ', '-', '–', '—', '•')
        if (cleaned.isBlank()) return "" to ""
        listOf(" - ", " – ", " — ").forEach { delimiter ->
            val index = cleaned.indexOf(delimiter)
            if (index > 0) {
                val artist = cleaned.substring(0, index).trim()
                val title = cleaned.substring(index + delimiter.length).trim()
                if (artist.isNotBlank() && title.isNotBlank()) return artist to title
            }
        }
        return "" to cleaned
    }

    private fun normalizeQuery(query: String): String = query.replace('ё', 'е').trim().replace(Regex("\\s+"), " ").lowercase()
    private fun firstNotBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun LigaudioListing.matches(track: RemoteTrackCandidate): Boolean {
        val titleMatches = normalizeQuery(title) == normalizeQuery(track.title)
        val artistMatches = normalizeQuery(artist) == normalizeQuery(track.artist)
        return titleMatches && (artistMatches || track.artist.isBlank() || artist == "Неизвестный исполнитель")
    }

    private data class LigaudioListing(
        val title: String,
        val artist: String,
        val detailUrl: String,
        val directDownloadUrl: String?,
    ) {
        fun uniqueKey(): String = "${artist.lowercase()}|${title.lowercase()}|${directDownloadUrl.orEmpty()}|${detailUrl}"
    }
}

class YandexDirectSource(
    private val client: com.codex.signalflow.yandex.YandexMusicClient,
    private val tokenProvider: () -> String?,
    private val isEnabledProvider: () -> Boolean,
) : MusicSource {
    override val descriptor: SourceDescriptor
        get() {
            val enabled = isEnabledProvider() && !tokenProvider().isNullOrBlank()
            return SourceDescriptor(
                id = "yandex_direct",
                name = "Яндекс Музыка (Direct)",
                status = if (enabled) "Активен (320 kbps)" else "Отключен",
                description = "Прямой поиск и загрузка треков из каталога Яндекс Музыки (llistochek/yandex-music-downloader).",
                isEnabled = enabled,
            )
        }

    override suspend fun search(query: String): List<RemoteTrackCandidate> = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext emptyList()
        if (!isEnabledProvider()) return@withContext emptyList()
        val results = client.searchTracks(token, query).getOrDefault(emptyList())
        results.map { meta ->
            RemoteTrackCandidate(
                sourceId = descriptor.id,
                title = meta.title,
                artist = meta.artist,
                detailUrl = meta.id,
                coverUrl = meta.coverUrl,
            )
        }
    }

    override suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: error("Токен Яндекс Музыки не задан.")
        val directUrl = client.resolveDirectDownloadUrl(token, track.detailUrl).getOrThrow()
        val filename = "${track.artist} - ${track.title}.mp3".replace(Regex("[/\\\\:*?\"<>|]"), "_")
        DownloadBlueprint(
            title = track.title,
            artist = track.artist,
            url = directUrl,
            suggestedFilename = filename,
            coverUrl = track.coverUrl,
        )
    }
}

class MusicSourceCatalog(
    private val sources: List<MusicSource> = listOf(LigaudioSource()),
) {
    val descriptors: List<SourceDescriptor>
        get() = sources.map { it.descriptor }

    suspend fun searchAll(query: String): List<RemoteTrackCandidate> {
        if (query.isBlank()) return emptyList()
        return sources.filter { it.descriptor.isEnabled }.flatMap { source ->
            runCatching { source.search(query) }
                .onFailure { error ->
                    runCatching {
                        Log.e("YaNeoDexSource", "search failed source=${source.descriptor.id} query=$query", error)
                    }
                }
                .getOrDefault(emptyList())
        }
    }

    suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint {
        val source = sources.firstOrNull { it.descriptor.id == track.sourceId }
            ?: error("Источник ${track.sourceId} не найден.")
        return source.resolve(track)
    }

    fun hasEnabledSources(): Boolean = sources.any { it.descriptor.isEnabled }

    companion object {
        fun encodedPathSegment(query: String): String = URLEncoder.encode(query, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }
}

private fun baseRequest(url: String, referer: String? = null) = Jsoup.connect(url)
    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36 YaNeoDex/1.0")
    .referrer(referer ?: "https://web.ligaudio.ru/")
    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
    .timeout(15_000)
    .followRedirects(true)


