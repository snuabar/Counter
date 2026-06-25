package com.snuabar.counter.core.detection.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.snuabar.counter.core.detection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.*

class AudioDetectionEngine @Inject constructor(
    private val context: Context
) : DetectionEngine {

    private val _countEvents = MutableStateFlow(CountEvent(count = 0, confidence = 0f))
    override val countEvents: Flow<CountEvent> = _countEvents.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var threshold = 0.7f
    private var isRunning = false
    private var isPaused = false
    private var count = 0

    // FFT parameters
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val fftSize = 1024
    private val hopSize = 512

    // Peak detection
    private var lastPeakTime = 0L
    private val minIntervalMs = 300  // Minimum interval between counts

    override fun start(config: DetectionConfig) {
        if (isRunning) return
        isRunning = true
        isPaused = false
        threshold = config.threshold
        count = 0
        startRecording()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (audioRecord != null) {
            audioRecord?.release()
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord?.startRecording()
            recordingJob = scope.launch {
                processAudioStream()
            }
        }
    }

    private suspend fun processAudioStream() {
        val buffer = ShortArray(fftSize)
        val fftBuffer = FloatArray(fftSize)

        while (isRunning && !isPaused) {
            val read = audioRecord?.read(buffer, 0, fftSize) ?: 0
            if (read > 0) {
                // Convert to float for FFT
                for (i in buffer.indices) {
                    fftBuffer[i] = buffer[i] / 32768.0f
                }

                // Compute FFT
                val spectrum = computeFFT(fftBuffer)

                // Detect peak
                detectPeak(spectrum)
            }
        }
    }

    private fun computeFFT(input: FloatArray): FloatArray {
        val n = input.size
        val output = FloatArray(n / 2 + 1)

        // Simple DFT for demonstration (can be replaced with optimized FFT)
        for (k in 0 until n / 2 + 1) {
            var real = 0.0f
            var imag = 0.0f
            for (t in input.indices) {
                val angle = -2.0 * Math.PI * k * t / n
                real += input[t] * cos(angle).toFloat()
                imag += input[t] * sin(angle).toFloat()
            }
            output[k] = sqrt(real * real + imag * imag)
        }

        return output
    }

    private fun detectPeak(spectrum: FloatArray) {
        // Calculate energy in low-frequency range (typical for clapping, jumping)
        val lowFreqEnergy = spectrum.sliceArray(10 until 100).average().toFloat()
        val highFreqEnergy = spectrum.sliceArray(100 until 200).average().toFloat()
        val totalEnergy = spectrum.average().toFloat()

        // Normalize energy
        val normalizedEnergy = if (totalEnergy > 0) lowFreqEnergy / totalEnergy else 0f

        // Detect peak based on threshold
        val currentTime = System.currentTimeMillis()
        if (normalizedEnergy > threshold &&
            currentTime - lastPeakTime > minIntervalMs
        ) {
            lastPeakTime = currentTime
            count++
            _countEvents.value = CountEvent(
                count = count,
                confidence = normalizedEnergy.coerceIn(0f, 1f)
            )
        }
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        isRunning = false
        isPaused = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun setThreshold(newThreshold: Float) {
        threshold = newThreshold
    }
}
