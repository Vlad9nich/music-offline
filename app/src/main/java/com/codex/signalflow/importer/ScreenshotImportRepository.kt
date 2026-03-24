package com.codex.signalflow.importer

import android.content.Context
import android.provider.DocumentsContract
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScreenshotImportRepository(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun getServerUrl(): String = preferences.getString(KEY_SERVER_URL, "").orEmpty()
    fun getAuthToken(): String = preferences.getString(KEY_AUTH_TOKEN, "").orEmpty()

    fun setServerUrl(url: String) {
        preferences.edit().putString(KEY_SERVER_URL, url.trim()).apply()
    }

    fun setAuthToken(token: String) {
        preferences.edit().putString(KEY_AUTH_TOKEN, token.trim()).apply()
    }

    suspend fun submitJob(
        serverUrl: String,
        uris: List<Uri>,
    ): OcrCreateJobResponse = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val preparedFiles = uris.mapIndexedNotNull { index, uri ->
            prepareImage(uri = uri, index = index)
        }

        if (preparedFiles.isEmpty()) {
            error("Не удалось подготовить изображения для распознавания.")
        }

        try {
            val boundary = "----YaNeoDex${UUID.randomUUID()}"
            val connection = openConnection("$normalizedUrl/v1/playlist-import/jobs").apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(connection.outputStream).use { output ->
                preparedFiles.forEach { prepared ->
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"files\"; filename=\"${prepared.file.name}\"\r\n")
                    output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    prepared.file.inputStream().use { input -> input.copyTo(output) }
                    output.writeBytes("\r\n")
                }
                output.writeBytes("--$boundary--\r\n")
                output.flush()
            }

            val payload = readResponseBody(connection)
            if (connection.responseCode !in 200..299) {
                error("OCR сервер вернул HTTP ${connection.responseCode}: ${payload.take(180)}")
            }
            json.decodeFromString(OcrCreateJobResponse.serializer(), payload)
        } finally {
            preparedFiles.forEach { prepared -> prepared.file.delete() }
        }
    }

    suspend fun submitSingleImage(
        serverUrl: String,
        uri: Uri,
    ): OcrSingleResponse = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val prepared = prepareImage(uri = uri, index = 0)
            ?: error("Не удалось подготовить изображение для распознавания.")

        try {
            val boundary = "----YaNeoDex${UUID.randomUUID()}"
            val connection = openConnection("$normalizedUrl/v1/ocr-image").apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${prepared.file.name}\"\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                prepared.file.inputStream().use { input -> input.copyTo(output) }
                output.writeBytes("\r\n")
                output.writeBytes("--$boundary--\r\n")
                output.flush()
            }

            val payload = readResponseBody(connection)
            if (connection.responseCode !in 200..299) {
                error("OCR сервер вернул HTTP ${connection.responseCode}: ${payload.take(180)}")
            }
            json.decodeFromString(OcrSingleResponse.serializer(), payload)
        } finally {
            prepared.file.delete()
        }
    }

    suspend fun pollJob(
        serverUrl: String,
        jobId: String,
        onProgress: (OcrJobResponse) -> Unit = {},
    ): OcrJobResponse = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            val connection = openConnection("$normalizedUrl/v1/playlist-import/jobs/$jobId")
            val payload = readResponseBody(connection)
            if (connection.responseCode !in 200..299) {
                error("OCR polling вернул HTTP ${connection.responseCode}: ${payload.take(180)}")
            }
            val response = json.decodeFromString(OcrJobResponse.serializer(), payload)
            onProgress(response)
            when (response.status.lowercase()) {
                "completed" -> return@withContext response
                "failed" -> error(response.error ?: "OCR сервис завершил задачу с ошибкой.")
            }
            delay(POLL_DELAY_MS)
        }
        error("OCR сервис слишком долго обрабатывает скриншоты.")
    }

    suspend fun normalizeCandidates(
        serverUrl: String,
        candidates: List<OcrCandidatePayload>,
    ): OcrSingleResponse = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val connection = openConnection("$normalizedUrl/v1/ocr").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(
                json.encodeToString(
                    OcrNormalizeRequest.serializer(),
                    OcrNormalizeRequest(candidates = candidates),
                ),
            )
            writer.flush()
        }

        val payload = readResponseBody(connection)
        if (connection.responseCode !in 200..299) {
            error("OCR сервер вернул HTTP ${connection.responseCode}: ${payload.take(180)}")
        }
        json.decodeFromString(OcrSingleResponse.serializer(), payload)
    }

    private fun prepareImage(uri: Uri, index: Int): PreparedImage? {
        return runCatching {
            val rawBitmap = decodeScaledBitmap(uri)
                ?: error("BitmapFactory не смог декодировать изображение ${describeUri(uri)}")
            val oriented = applyExifOrientation(uri, rawBitmap)
            if (oriented !== rawBitmap) rawBitmap.recycle()
            val trimmed = trimDarkBorders(oriented)
            if (trimmed !== oriented) oriented.recycle()
            val finalBitmap = downscale(trimmed)
            if (finalBitmap !== trimmed) trimmed.recycle()

            val directory = File(appContext.cacheDir, "ocr-import").apply { mkdirs() }
            val file = File(directory, "playlist-shot-$index-${UUID.randomUUID()}.jpg")
            file.outputStream().use { output ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                output.flush()
            }
            finalBitmap.recycle()
            PreparedImage(file = file)
        }.onFailure { error ->
            Log.e(TAG, "prepareImage failed uri=${describeUri(uri)} reason=${error::class.java.name}: ${error.message}", error)
        }.getOrNull()
    }

    private fun decodeScaledBitmap(uri: Uri): Bitmap? {
        val bytes = readImageBytes(uri) ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            Log.w(TAG, "decodeScaledBitmap bounds failed uri=${describeUri(uri)} size=${bytes.size}")
            return null
        }

        val maxSize = max(options.outWidth, options.outHeight).coerceAtLeast(1)
        val sampleSize = when {
            maxSize > 3200 -> 4
            maxSize > 2200 -> 2
            else -> 1
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun trimDarkBorders(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 40 || height < 40) return bitmap

        var top = 0
        var bottom = height - 1
        var left = 0
        var right = width - 1

        while (top < bottom && isMostlyUniformRow(bitmap, top, width)) top++
        while (bottom > top && isMostlyUniformRow(bitmap, bottom, width)) bottom--
        while (left < right && isMostlyUniformColumn(bitmap, left, height)) left++
        while (right > left && isMostlyUniformColumn(bitmap, right, height)) right--

        val trimmedWidth = min((right - left + 1).coerceAtLeast(width / 2), width - left)
        val trimmedHeight = min((bottom - top + 1).coerceAtLeast(height / 2), height - top)
        if (trimmedWidth == width && trimmedHeight == height) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, trimmedWidth, trimmedHeight)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val targetWidth = min(bitmap.width, MAX_PREPARED_WIDTH)
        if (targetWidth == bitmap.width) return bitmap
        val targetHeight = (bitmap.height * (targetWidth / bitmap.width.toFloat())).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaled)
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, targetWidth, targetHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        return scaled
    }

    private fun isMostlyUniformRow(bitmap: Bitmap, y: Int, width: Int): Boolean {
        val step = max(1, width / 24)
        var darkOrBrightSamples = 0
        var total = 0
        for (x in 0 until width step step) {
            val pixel = bitmap.getPixel(x, y)
            val luminance = luminance(pixel)
            if (luminance < 10 || luminance > 248) darkOrBrightSamples++
            total++
        }
        return total > 0 && darkOrBrightSamples.toFloat() / total.toFloat() > 0.9f
    }

    private fun isMostlyUniformColumn(bitmap: Bitmap, x: Int, height: Int): Boolean {
        val step = max(1, height / 24)
        var darkOrBrightSamples = 0
        var total = 0
        for (y in 0 until height step step) {
            val pixel = bitmap.getPixel(x, y)
            val luminance = luminance(pixel)
            if (luminance < 10 || luminance > 248) darkOrBrightSamples++
            total++
        }
        return total > 0 && darkOrBrightSamples.toFloat() / total.toFloat() > 0.9f
    }

    private fun luminance(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return ((red * 299) + (green * 587) + (blue * 114)) / 1000
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            // Free-tier multimodal OCR models can take 1-2+ minutes to answer.
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("User-Agent", "YaNeoDex-OCR-Import/1.0")
            setRequestProperty("Accept", "application/json")
            getAuthToken().takeIf { it.isNotBlank() }?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
            doInput = true
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = when (connection.responseCode) {
            in 200..299 -> connection.inputStream
            else -> connection.errorStream ?: connection.inputStream
        }
        return stream.use { input ->
            input.bufferedReader().readText()
        }
    }

    private fun normalizeServerUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "Укажите адрес OCR сервера." }
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Адрес OCR сервера должен начинаться с http:// или https://"
        }
        return normalized
    }

    private fun readImageBytes(uri: Uri): ByteArray? {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.isNotEmpty()) return bytes
        }
        resolveRawDocumentFile(uri)?.takeIf { it.isFile }?.let { file ->
            return FileInputStream(file).use { input -> input.readBytes() }
        }
        return null
    }

    private fun resolveRawDocumentFile(uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let(::File)
        if (uri.scheme != "content") return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        if (!documentId.startsWith("raw:")) return null
        return File(documentId.removePrefix("raw:"))
    }

    private fun describeUri(uri: Uri): String {
        val rawFile = resolveRawDocumentFile(uri)?.absolutePath
        return buildString {
            append(uri.toString())
            if (!rawFile.isNullOrBlank()) {
                append(" rawFile=")
                append(rawFile)
            }
        }
    }

    private data class PreparedImage(
        val file: File,
    )

    companion object {
        private const val PREFS_NAME = "yaneodex_ocr"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val POLL_DELAY_MS = 1_200L
        private const val MAX_POLL_ATTEMPTS = 60
        private const val MAX_PREPARED_WIDTH = 1600
        private const val TAG = "YaNeoDexOcr"
    }
}
