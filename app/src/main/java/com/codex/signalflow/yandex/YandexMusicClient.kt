package com.codex.signalflow.yandex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Direct client for Yandex Music API and Track Downloader.
 * Based on official / mobile REST endpoints and the llistochek/yandex-music-downloader algorithm.
 */
class YandexMusicClient {

    companion object {
        private const val TAG = "YandexMusicClient"
        private const val BASE_API = "https://api.music.yandex.net"
        private const val USER_AGENT = "YandexMusicAndroid/2024.08.1 (Android 14; Mobile)"
        // Standard secret salt used by Yandex Music mobile/API manifests
        private const val DOWNLOAD_SECRET_SALT = "XGRlBW9FXlekgbPrRHuSiA"
    }

    /**
     * Validates personal token and returns account info (UID, login).
     */
    suspend fun validateToken(token: String): Result<YandexAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val response = executeGet("$BASE_API/account/status", token)
            val json = JSONObject(response)
            val result = json.getJSONObject("result")
            val account = result.getJSONObject("account")
            val uid = account.getLong("uid")
            val login = account.optString("login", "user_$uid")
            val displayName = account.optString("displayName", account.optString("fullName", login))
            YandexAccountInfo(uid = uid, login = login, displayName = displayName)
        }
    }

    /**
     * Fetches all liked tracks from user's account and returns detailed metadata.
     */
    suspend fun fetchLikedTracks(token: String, uid: Long): Result<List<YandexTrackMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Get list of liked track IDs
            val likesResponse = executeGet("$BASE_API/users/$uid/likes/tracks", token)
            val likesJson = JSONObject(likesResponse)
            val library = likesJson.getJSONObject("result").getJSONObject("library")
            val tracksArray = library.getJSONArray("tracks")

            val idTuples = mutableListOf<Pair<String, String?>>()
            for (i in 0 until tracksArray.length()) {
                val item = tracksArray.getJSONObject(i)
                val id = item.getString("id")
                val albumId = item.optString("albumId").takeIf { it.isNotBlank() }
                idTuples.add(id to albumId)
            }

            Log.d(TAG, "Found ${idTuples.size} liked tracks in Yandex Music library")
            if (idTuples.isEmpty()) return@runCatching emptyList()

            // 2. Batch fetch track metadata (chunks of 100)
            val resultTracks = mutableListOf<YandexTrackMeta>()
            val chunks = idTuples.chunked(100)
            for (chunk in chunks) {
                val batchResult = fetchTrackBatch(token, chunk)
                resultTracks.addAll(batchResult)
            }

            resultTracks
        }
    }

    /**
     * Resolves direct download URL (MP3 320k/192k) using yandex-music-downloader algorithm.
     */
    suspend fun resolveDirectDownloadUrl(token: String, trackId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Request download info
            val infoResponse = executeGet("$BASE_API/tracks/$trackId/download-info", token)
            val json = JSONObject(infoResponse)
            val result = json.getJSONArray("result")

            var bestUrl: String? = null
            var bestBitrate = -1

            for (i in 0 until result.length()) {
                val item = result.getJSONObject(i)
                val codec = item.optString("codec", "")
                val bitrate = item.optInt("bitrateInKbps", 0)
                val downloadInfoUrl = item.optString("downloadInfoUrl", "")

                if (codec == "mp3" && bitrate >= bestBitrate && downloadInfoUrl.isNotBlank()) {
                    bestBitrate = bitrate
                    bestUrl = downloadInfoUrl
                }
            }

            val downloadInfoUrl = bestUrl ?: error("Не найдена ссылка на скачивание MP3 для трека $trackId")

            // 2. Fetch download info XML
            val xmlContent = Jsoup.connect(downloadInfoUrl)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .execute()
                .body()

            val doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser())
            val host = doc.selectFirst("host")?.text()?.trim() ?: error("В XML отсутствует host")
            val path = doc.selectFirst("path")?.text()?.trim() ?: error("В XML отсутствует path")
            val ts = doc.selectFirst("ts")?.text()?.trim() ?: error("В XML отсутствует ts")
            val s = doc.selectFirst("s")?.text()?.trim() ?: error("В XML отсутствует s")

            // 3. Compute MD5 signature
            val cleanPath = path.removePrefix("/")
            val sign = md5("$DOWNLOAD_SECRET_SALT$cleanPath$s")

            // 4. Final direct media link
            "https://$host/get-mp3/$sign/$ts$path"
        }
    }

    /**
     * Searches Yandex Music for tracks by title/artist.
     */
    suspend fun searchTracks(token: String, query: String): Result<List<YandexTrackMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
            val response = executeGet("$BASE_API/search?text=$encodedQuery&type=track&page=0", token)
            val json = JSONObject(response)
            val result = json.getJSONObject("result")
            if (!result.has("tracks")) return@runCatching emptyList()

            val tracksObj = result.getJSONObject("tracks")
            val resultsArray = tracksObj.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<YandexTrackMeta>()

            for (i in 0 until resultsArray.length()) {
                val trackObj = resultsArray.getJSONObject(i)
                list.add(parseTrackObject(trackObj))
            }
            list
        }
    }

    private suspend fun fetchTrackBatch(
        token: String,
        batch: List<Pair<String, String?>>,
    ): List<YandexTrackMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val trackIdsParam = batch.joinToString(",") { (id, albumId) ->
                if (albumId != null) "$id:$albumId" else id
            }

            val body = "track-ids=${URLEncoder.encode(trackIdsParam, StandardCharsets.UTF_8.toString())}"
            val response = executePost("$BASE_API/tracks", token, body)
            val json = JSONObject(response)
            val resultArray = json.optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<YandexTrackMeta>()

            for (i in 0 until resultArray.length()) {
                val trackObj = resultArray.getJSONObject(i)
                list.add(parseTrackObject(trackObj))
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun parseTrackObject(trackObj: JSONObject): YandexTrackMeta {
        val id = trackObj.getString("id")
        val title = trackObj.optString("title", "Без названия")
        val artistsArray = trackObj.optJSONArray("artists") ?: JSONArray()
        val artists = mutableListOf<String>()
        for (a in 0 until artistsArray.length()) {
            artists.add(artistsArray.getJSONObject(a).optString("name", ""))
        }
        val artist = artists.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Неизвестный исполнитель" }

        val albumsArray = trackObj.optJSONArray("albums") ?: JSONArray()
        var albumTitle: String? = null
        var albumId: String? = null
        var coverUrl: String? = null

        if (albumsArray.length() > 0) {
            val firstAlbum = albumsArray.getJSONObject(0)
            albumId = firstAlbum.optString("id", "")
            albumTitle = firstAlbum.optString("title", "")
            val coverUri = firstAlbum.optString("coverUri", "")
            if (coverUri.isNotBlank()) {
                coverUrl = "https://" + coverUri.replace("%%", "400x400")
            }
        }

        val durationMs = trackObj.optLong("durationMs", 0L)
        return YandexTrackMeta(
            id = id,
            albumId = albumId,
            title = title,
            artist = artist,
            albumTitle = albumTitle,
            durationMs = durationMs,
            coverUrl = coverUrl,
        )
    }

    private fun executeGet(urlStr: String, token: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Authorization", "OAuth $token")
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).readText() }
                error("HTTP $responseCode: $errorStream")
            }

            return BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun executePost(urlStr: String, token: String, body: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Authorization", "OAuth $token")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json")

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).readText() }
                error("HTTP $responseCode: $errorStream")
            }

            return BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
