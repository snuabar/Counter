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

    override fun isRunning(): Boolean = isRunning

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

    // Adaptive noise floor estimation
    private val noiseHistorySize = 30
    private val noiseEnergyHistory = FloatArray(noiseHistorySize)
    private var noiseHistoryIndex = 0
    private var noiseHistoryCount = 0
    private val noiseThresholdFactor = 1.5f

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
        require(n and (n - 1) == 0) { "FFT size must be a power of 2, got $n" }

        val real = FloatArray(n)
        val imag = FloatArray(n)
        System.arraycopy(input, 0, real, 0, n)

        fft(real, imag)

        // Compute magnitude spectrum for first half + 1
        val output = FloatArray(n / 2 + 1)
        for (i in output.indices) {
            output[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        return output
    }

    /**
     * Cooley-Tukey radix-2 in-place FFT.
     * On entry: real[] and imag[] contain the time-domain samples.
     * On exit:  real[] and imag[] contain the frequency-domain components.
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Butterfly stages
        var step = 2
        while (step <= n) {
            val halfStep = step shr 1
            val angleStep = -2.0 * PI / step
            for (i in 0 until n step step) {
                for (m in 0 until halfStep) {
                    val angle = angleStep * m
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()
                    val idx1 = i + m
                    val idx2 = i + m + halfStep
                    val tr = wr * real[idx2] - wi * imag[idx2]
                    val ti = wr * imag[idx2] + wi * real[idx2]
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] = real[idx1] + tr
                    imag[idx1] = imag[idx1] + ti
                }
            }
            step = step shl 1
        }
    }

    private fun detectPeak(spectrum: FloatArray) {
        // Dynamic frequency band calculation based on sampleRate and fftSize
        val binWidth = sampleRate.toFloat() / fftSize  // Hz per bin
        val lowFreqStart = (50f / binWidth).toInt().coerceAtLeast(1)    // 50Hz
        val lowFreqEnd = (500f / binWidth).toInt().coerceAtMost(spectrum.size - 1)   // 500Hz
        val highFreqStart = (500f / binWidth).toInt().coerceAtLeast(1)
        val highFreqEnd = (2000f / binWidth).toInt().coerceAtMost(spectrum.size - 1)  // 2000Hz

        // Calculate energy in frequency ranges
        val lowFreqEnergy = if (lowFreqEnd > lowFreqStart)
            spectrum.sliceArray(lowFreqStart..lowFreqEnd).average().toFloat() else 0f
        val highFreqEnergy = if (highFreqEnd > highFreqStart)
            spectrum.sliceArray(highFreqStart..highFreqEnd).average().toFloat() else 0f
        val totalEnergy = spectrum.average().toFloat()

        // Normalize energy
        val normalizedEnergy = if (totalEnergy > 0) lowFreqEnergy / totalEnergy else 0f

        // Update adaptive noise floor
        noiseEnergyHistory[noiseHistoryIndex] = normalizedEnergy
        noiseHistoryIndex = (noiseHistoryIndex + 1) % noiseHistorySize
        if (noiseHistoryCount < noiseHistorySize) noiseHistoryCount++

        // Compute noise floor as median * factor
        val noiseFloor = computeNoiseFloor()

        // Detect peak based on threshold and adaptive noise floor
        val currentTime = System.currentTimeMillis()
        val effectiveThreshold = max(threshold, noiseFloor)
        if (normalizedEnergy > effectiveThreshold &&
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

    /**
     * Computes the adaptive noise floor from the energy history.
     * noise floor = median(energy history) * noiseThresholdFactor
     */
    private fun computeNoiseFloor(): Float {
        if (noiseHistoryCount == 0) return 0f
        val sorted = noiseEnergyHistory.copyOf(noiseHistoryCount).sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        } else {
            sorted[sorted.size / 2]
        }
        return median * noiseThresholdFactor
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
