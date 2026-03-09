package com.codex.signalflow.player

import android.media.audiofx.Visualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class VisualizerUiState(
    val bars: List<Float> = List(BAR_COUNT) { 0.16f },
    val isUsingFallback: Boolean = true,
    val isActive: Boolean = false,
) {
    companion object {
        const val BAR_COUNT = 20
    }
}

class AudioVisualizerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(VisualizerUiState())
    val state: StateFlow<VisualizerUiState> = _state.asStateFlow()

    private var visualizer: Visualizer? = null
    private var fallbackJob: Job? = null
    private var sessionId: Int = 0
    private var lastBars: List<Float> = _state.value.bars

    fun update(
        audioSessionId: Int,
        isPlaying: Boolean,
        isActive: Boolean,
        permissionGranted: Boolean,
        progressFraction: Float,
    ) {
        if (!isActive) {
            stopRealtime()
            stopFallback()
            publishBars(List(VisualizerUiState.BAR_COUNT) { 0.14f }, usingFallback = true, active = false)
            return
        }

        if (permissionGranted && isPlaying && audioSessionId > 0) {
            stopFallback()
            ensureVisualizer(audioSessionId)
        } else {
            stopRealtime()
            ensureFallback(progressFraction = progressFraction, isPlaying = isPlaying)
        }
    }

    fun release() {
        stopRealtime()
        stopFallback()
        scope.cancel()
    }

    private fun ensureVisualizer(audioSessionId: Int) {
        if (visualizer != null && sessionId == audioSessionId) return
        stopRealtime()
        sessionId = audioSessionId

        val captureRate = (Visualizer.getMaxCaptureRate() / 5).coerceAtLeast(8_000)
        val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(256)

        runCatching {
            Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int,
                        ) {
                            publishBars(waveformToBars(waveform), usingFallback = false, active = true)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int,
                        ) = Unit
                    },
                    captureRate,
                    true,
                    false,
                )
                enabled = true
            }
        }.onSuccess {
            visualizer = it
        }.onFailure {
            stopRealtime()
            ensureFallback(progressFraction = 0f, isPlaying = true)
        }
    }

    private fun ensureFallback(
        progressFraction: Float,
        isPlaying: Boolean,
    ) {
        if (fallbackJob != null) return
        fallbackJob = scope.launch {
            while (true) {
                publishBars(
                    bars = fallbackBars(progressFraction = progressFraction, isPlaying = isPlaying),
                    usingFallback = true,
                    active = true,
                )
                delay(if (isPlaying) 70L else 220L)
            }
        }
    }

    private fun stopRealtime() {
        visualizer?.runCatching {
            enabled = false
            release()
        }
        visualizer = null
        sessionId = 0
    }

    private fun stopFallback() {
        fallbackJob?.cancel()
        fallbackJob = null
    }

    private fun waveformToBars(waveform: ByteArray): List<Float> {
        if (waveform.isEmpty()) return lastBars
        val chunkSize = (waveform.size / VisualizerUiState.BAR_COUNT).coerceAtLeast(1)
        val bars = MutableList(VisualizerUiState.BAR_COUNT) { index ->
            val start = index * chunkSize
            val end = (start + chunkSize).coerceAtMost(waveform.size)
            if (start >= end) return@MutableList 0.16f
            val normalizedAverage = waveform
                .copyOfRange(start, end)
                .map { abs((it.toInt() and 0xFF) - 128) / 128f }
                .average()
                .toFloat()
            normalizedAverage.coerceIn(0.12f, 1f)
        }
        return smoothBars(bars)
    }

    private fun fallbackBars(
        progressFraction: Float,
        isPlaying: Boolean,
    ): List<Float> {
        val timeSlice = System.currentTimeMillis() / if (isPlaying) 140.0 else 900.0
        val bars = List(VisualizerUiState.BAR_COUNT) { index ->
            val wave = sin((timeSlice + index) * PI / 3.4).toFloat()
            val pulse = sin((progressFraction * 8f + index * 0.6f) * PI).toFloat()
            val base = if (isPlaying) 0.32f else 0.18f
            (base + abs(wave) * 0.34f + abs(pulse) * 0.18f).coerceIn(0.12f, 0.82f)
        }
        return smoothBars(bars)
    }

    private fun smoothBars(nextBars: List<Float>): List<Float> {
        val smoothed = nextBars.mapIndexed { index, next ->
            val previous = lastBars.getOrNull(index) ?: next
            (previous * 0.55f + next * 0.45f).coerceIn(0.12f, 1f)
        }
        lastBars = smoothed
        return smoothed
    }

    private fun publishBars(
        bars: List<Float>,
        usingFallback: Boolean,
        active: Boolean,
    ) {
        _state.value = VisualizerUiState(
            bars = bars,
            isUsingFallback = usingFallback,
            isActive = active,
        )
    }
}
