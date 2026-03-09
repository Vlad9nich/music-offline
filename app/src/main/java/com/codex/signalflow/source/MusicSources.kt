package com.codex.signalflow.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
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
)

data class DownloadBlueprint(
    val title: String,
    val artist: String,
    val url: String,
    val suggestedFilename: String,
    val refererUrl: String? = null,
)

data class HtmlTrackSelectorBlueprint(
    val itemSelector: String,
    val titleSelector: String,
    val artistSelector: String,
    val linkSelector: String,
    val downloadSelector: String? = null,
)

interface MusicSource {
    val descriptor: SourceDescriptor
    suspend fun search(query: String): List<RemoteTrackCandidate>
    suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint
}

open class SelectorBasedHtmlMusicSource(
    override val descriptor: SourceDescriptor,
    private val blueprint: HtmlTrackSelectorBlueprint,
    private val searchUrlBuilder: (String) -> String,
) : MusicSource {
    override suspend fun search(query: String): List<RemoteTrackCandidate> = withContext(Dispatchers.IO) {
        val url = searchUrlBuilder(query.trim())
        val document = baseRequest(url)
            .get()
        parseDocument(document)
    }

    fun parseHtml(html: String, baseUrl: String): List<RemoteTrackCandidate> {
        return parseDocument(Jsoup.parse(html, baseUrl))
    }

    override suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint {
        val downloadUrl = track.downloadUrl ?: error("Не удалось определить ссылку на загрузку для ${track.title}")
        return DownloadBlueprint(
            title = track.title,
            artist = track.artist,
            url = downloadUrl,
            suggestedFilename = "${track.artist} - ${track.title}.mp3",
            refererUrl = track.detailUrl,
        )
    }

    private fun parseDocument(document: Document): List<RemoteTrackCandidate> {
        return document.select(blueprint.itemSelector).mapNotNull { element ->
            val title = element.selectFirst(blueprint.titleSelector)?.text()?.trim().orEmpty()
            val artist = element.selectFirst(blueprint.artistSelector)?.text()?.trim().orEmpty()
            val detailUrl = element.selectFirst(blueprint.linkSelector)?.absUrl("href").orEmpty()
            val downloadUrl = blueprint.downloadSelector
                ?.let { selector -> element.selectFirst(selector)?.absUrl("href") }
                ?.takeIf { it.isNotBlank() }

            if (title.isBlank() || detailUrl.isBlank()) {
                null
            } else {
                RemoteTrackCandidate(
                    sourceId = descriptor.id,
                    title = title,
                    artist = artist.ifBlank { "Неизвестный исполнитель" },
                    detailUrl = detailUrl,
                    downloadUrl = downloadUrl,
                )
            }
        }
    }
}

abstract class HtmlMusicSourceTemplate(
    override val descriptor: SourceDescriptor,
) : MusicSource {
    override suspend fun search(query: String): List<RemoteTrackCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val document = requestDocument(buildSearchUrl(query.trim()))
        mapSearchDocument(document)
    }

    override suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint = withContext(Dispatchers.IO) {
        val document = requestDocument(track.detailUrl, referer = "https://poiskm.net/")
        mapTrackDetails(track, document)
    }

    protected abstract fun buildSearchUrl(query: String): String

    protected abstract fun mapSearchDocument(document: Document): List<RemoteTrackCandidate>

    protected abstract fun mapTrackDetails(
        track: RemoteTrackCandidate,
        document: Document,
    ): DownloadBlueprint

    protected fun requestDocument(url: String, referer: String? = null): Document {
        return baseRequest(url, referer).get()
    }

    protected fun Element.textOrEmpty(selector: String): String {
        return selectFirst(selector)?.text()?.trim().orEmpty()
    }

    protected fun Element.absoluteHref(selector: String): String {
        return selectFirst(selector)?.absUrl("href").orEmpty()
    }
}

class PoiskmNetSource : HtmlMusicSourceTemplate(
    descriptor = SourceDescriptor(
        id = "poiskm",
        name = "Poiskm.net",
        status = "Активен",
        description = "Поиск и разбор карточек треков с несколькими стратегиями URL и fallback-селекторами.",
        isEnabled = true,
    ),
) {
    override suspend fun search(query: String): List<RemoteTrackCandidate> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        if (normalizedQuery.isBlank()) return@withContext emptyList()

        val uniqueResults = linkedMapOf<String, RemoteTrackCandidate>()
        buildSearchUrls(normalizedQuery).forEach { url ->
            val results = runCatching {
                mapSearchDocument(requestDocument(url, referer = "https://poiskm.net/"))
            }.getOrDefault(emptyList())

            results.forEach { candidate ->
                uniqueResults.putIfAbsent(candidate.uniqueKey(), candidate)
            }
        }

        uniqueResults.values.toList()
    }

    override fun buildSearchUrl(query: String): String {
        val slug = tokenize(query).joinToString("-")
        return "https://poiskm.net/show/$slug"
    }

    override fun mapSearchDocument(document: Document): List<RemoteTrackCandidate> {
        return parseTrackCards(document).map { listing ->
            RemoteTrackCandidate(
                sourceId = descriptor.id,
                title = listing.title,
                artist = listing.artist,
                detailUrl = listing.detailUrl,
                downloadUrl = listing.songId?.takeIf { listing.number != null }?.let { songId ->
                    buildDownloadUrl(songId, listing.number!!)
                },
            )
        }
    }

    override fun mapTrackDetails(
        track: RemoteTrackCandidate,
        document: Document,
    ): DownloadBlueprint {
        val resolvedUrl = track.downloadUrl ?: parseTrackCards(document)
            .firstOrNull { candidate -> candidate.matches(track) }
            ?.let { candidate ->
                val songId = candidate.songId
                val number = candidate.number
                if (songId != null && number != null) buildDownloadUrl(songId, number) else null
            }
            ?: error("Ссылка на загрузку не найдена для ${track.artist} - ${track.title}")

        return DownloadBlueprint(
            title = track.title,
            artist = track.artist,
            url = resolvedUrl,
            suggestedFilename = "${track.artist} - ${track.title}.mp3",
            refererUrl = track.detailUrl,
        )
    }

    fun parseSearchHtml(html: String, baseUrl: String): List<RemoteTrackCandidate> {
        return mapSearchDocument(Jsoup.parse(html, baseUrl))
    }

    private fun buildSearchUrls(query: String): List<String> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val urls = linkedSetOf<String>()
        urls += "https://poiskm.net/show/${tokens.joinToString("-")}"
        for (splitIndex in 1 until tokens.size) {
            val left = tokens.take(splitIndex).joinToString("-")
            val right = tokens.drop(splitIndex).joinToString("-")
            if (left.isNotBlank() && right.isNotBlank()) {
                urls += "https://poiskm.net/show/$left/$right"
            }
        }

        val encodedQuery = MusicSourceCatalog.encodedQuery(query)
        urls += "https://poiskm.net/?do=search&subaction=search&story=$encodedQuery"
        urls += "https://poiskm.net/index.php?do=search&subaction=search&story=$encodedQuery"
        return urls.toList()
    }

    private fun parseTrackCards(document: Document): List<PoiskmListing> {
        val baseUrl = document.location().ifBlank { document.baseUri() }
        val listings = linkedMapOf<String, PoiskmListing>()

        document.select("div.title_wrap, [data-songid], [id^=song-]").forEach { item ->
            val container = item.closest("[data-songid], [id^=song-]") ?: item
            val title = extractTitle(item, container)
            if (title.isBlank()) return@forEach

            val artist = extractArtist(item, container)
            val songId = firstNotBlank(
                container.attr("data-songid"),
                item.attr("data-songid"),
                container.id().removePrefix("song-").takeIf { it != container.id() },
                item.id().removePrefix("song-").takeIf { it != item.id() },
                extractAttr(container, "data-songid"),
                extractAttr(item, "data-songid"),
            )
            val number = firstNotBlank(
                item.selectFirst("span.play")?.attr("data-number"),
                container.selectFirst("span.play")?.attr("data-number"),
                extractAttr(container, "data-number"),
                extractAttr(item, "data-number"),
            )
            val detailUrl = firstNotBlank(
                item.absoluteHref("a[href*=/show/]"),
                container.absoluteHref("a[href*=/show/]"),
                baseUrl,
            ).orEmpty()

            val listing = PoiskmListing(
                title = title,
                artist = artist.ifBlank { "Неизвестный исполнитель" },
                detailUrl = detailUrl,
                songId = songId,
                number = number,
            )
            listings.putIfAbsent(listing.uniqueKey(), listing)
        }

        return listings.values.toList()
    }

    private fun extractTitle(item: Element, container: Element): String {
        val titledLink = item.select("a")
            .firstOrNull { anchor ->
                val text = anchor.text().trim()
                text.isNotBlank() &&
                    !anchor.classNames().contains("artist-name") &&
                    !anchor.attr("href").contains("/artist/")
            }
            ?.text()
            .orEmpty()
            .trim()

        val fallbackText = parseArtistTitlePair(container.text()).second
        return firstNotBlank(
            item.textOrEmpty("span.title-name"),
            container.textOrEmpty("span.title-name"),
            titledLink,
            fallbackText,
        ).orEmpty()
    }

    private fun extractArtist(item: Element, container: Element): String {
        val artistLink = item.textOrEmpty("a.artist-name")
            .ifBlank { container.textOrEmpty("a.artist-name") }
            .ifBlank {
                item.selectFirst("a[href*=/artist/]")?.text()?.trim().orEmpty()
            }
            .ifBlank {
                container.selectFirst("a[href*=/artist/]")?.text()?.trim().orEmpty()
            }

        val fallbackArtist = parseArtistTitlePair(container.text()).first
        return firstNotBlank(artistLink, fallbackArtist).orEmpty()
    }

    private fun parseArtistTitlePair(rawText: String): Pair<String, String> {
        val cleaned = rawText
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), "")
            .trim(' ', '-', '–', '—', '•')

        if (cleaned.isBlank()) return "" to ""

        listOf(" - ", " – ", " — ").forEach { delimiter ->
            val index = cleaned.indexOf(delimiter)
            if (index > 0) {
                val artist = cleaned.substring(0, index).trim()
                val title = cleaned.substring(index + delimiter.length).trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return artist to title
                }
            }
        }

        return "" to cleaned
    }

    private fun normalizeQuery(query: String): String {
        return query
            .replace('ё', 'е')
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun tokenize(query: String): List<String> {
        return normalizeQuery(query)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { token -> token.trim('-') }
            .filter { token -> token.isNotBlank() }
    }

    private fun buildDownloadUrl(songId: String, number: String): String {
        return "https://poiskm.net/?do=getById&id=$songId&n=$number"
    }

    private fun extractAttr(element: Element, name: String): String {
        val regex = Regex("""$name\\s*=\\s*[\"']?([A-Za-z0-9_-]+)""")
        return regex.find(element.outerHtml())?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun firstNotBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun PoiskmListing.matches(track: RemoteTrackCandidate): Boolean {
        val titleMatches = normalizeQuery(title) == normalizeQuery(track.title)
        val artistMatches = normalizeQuery(artist) == normalizeQuery(track.artist)
        return titleMatches && (artistMatches || track.artist.isBlank() || artist == "Неизвестный исполнитель")
    }

    private fun RemoteTrackCandidate.uniqueKey(): String {
        return "$sourceId|${normalizeQuery(artist)}|${normalizeQuery(title)}|${downloadUrl ?: detailUrl}"
    }

    private data class PoiskmListing(
        val title: String,
        val artist: String,
        val detailUrl: String,
        val songId: String?,
        val number: String?,
    ) {
        fun uniqueKey(): String {
            return "${artist.lowercase()}|${title.lowercase()}|${songId.orEmpty()}|${number.orEmpty()}"
        }
    }
}

class PendingHtmlSourceTemplate(
    name: String = "Черновик HTML-источника",
) : HtmlMusicSourceTemplate(
    descriptor = SourceDescriptor(
        id = "pending-html-template",
        name = name,
        status = "Только каркас",
        description = "Универсальный каркас HTML-парсера с переопределяемым поиском и разбором страницы трека.",
        isEnabled = false,
    ),
) {
    override fun buildSearchUrl(query: String): String {
        error("Переопределите buildSearchUrl() конкретным адресом поиска.")
    }

    override fun mapSearchDocument(document: Document): List<RemoteTrackCandidate> {
        return emptyList()
    }

    override fun mapTrackDetails(
        track: RemoteTrackCandidate,
        document: Document,
    ): DownloadBlueprint {
        error("Переопределите mapTrackDetails() разбором страницы трека конкретного источника.")
    }
}

class UnconfiguredHtmlSource : MusicSource {
    override val descriptor = SourceDescriptor(
        id = "html-blueprint",
        name = "HTML-шаблон",
        status = "Адаптер готов",
        description = "Универсальный CSS-selector парсер для будущих источников. Конкретный провайдер не настроен.",
        isEnabled = false,
    )

    override suspend fun search(query: String): List<RemoteTrackCandidate> = emptyList()

    override suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint {
        error("Активный источник не настроен.")
    }
}

class MusicSourceCatalog(
    private val sources: List<MusicSource> = listOf(
        PoiskmNetSource(),
        UnconfiguredHtmlSource(),
        PendingHtmlSourceTemplate(),
    ),
) {
    val descriptors: List<SourceDescriptor>
        get() = sources.map { it.descriptor }

    suspend fun searchAll(query: String): List<RemoteTrackCandidate> {
        if (query.isBlank()) return emptyList()
        return sources
            .filter { it.descriptor.isEnabled }
            .flatMap { source ->
                runCatching { source.search(query) }.getOrDefault(emptyList())
            }
    }

    suspend fun resolve(track: RemoteTrackCandidate): DownloadBlueprint {
        val source = sources.firstOrNull { it.descriptor.id == track.sourceId }
            ?: error("Источник ${track.sourceId} не найден.")
        return source.resolve(track)
    }

    fun hasEnabledSources(): Boolean = sources.any { it.descriptor.isEnabled }

    companion object {
        fun encodedQuery(query: String): String = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
    }
}

private fun baseRequest(url: String, referer: String? = null) = Jsoup.connect(url)
    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36 YaNeoDex/1.0")
    .referrer(referer ?: "https://poiskm.net/")
    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
    .timeout(15_000)
    .followRedirects(true)

