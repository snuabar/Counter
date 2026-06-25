package com.snuabar.counter.core.template

import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Records keypoints over a duration and builds a template feature vector.
 *
 * Usage:
 * 1. Call startRecording(durationSeconds)
 * 2. Feed keypoints via addKeypoints() for each frame
 * 3. Call stopAndBuildTemplate() to compute and return the feature vector
 */
class TemplateRecorder {

    private var isRecording = false
    private var targetFrames = 0
    private val collectedFeatures = mutableListOf<FloatArray>()

    // Progress callback: (currentFrame, targetFrames)
    var onProgressUpdate: ((Int, Int) -> Unit)? = null

    fun startRecording(durationSeconds: Int, fps: Int = 10) {
        collectedFeatures.clear()
        targetFrames = durationSeconds * fps
        isRecording = true
    }

    /**
     * Add a frame of keypoints (17 COCO keypoints, each [x, y, confidence]).
     */
    fun addKeypoints(keypoints: Array<FloatArray>) {
        if (!isRecording) return
        if (collectedFeatures.size >= targetFrames) return

        val features = extractAngleFeatures(keypoints) ?: return
        collectedFeatures.add(features)
        onProgressUpdate?.invoke(collectedFeatures.size, targetFrames)
    }

    /**
     * Stop recording and build the template feature vector (as ByteArray).
     * Returns null if not enough frames were collected.
     */
    fun stopAndBuildTemplate(
        name: String,
        userId: Long? = null,
        threshold: Float = 0.7f
    ): Template? {
        isRecording = false

        if (collectedFeatures.isEmpty()) return null

        // Compute average feature vector
        val featureDim = collectedFeatures.first().size
        val avgFeatures = FloatArray(featureDim)
        for (features in collectedFeatures) {
            for (i in features.indices) {
                avgFeatures[i] += features[i]
            }
        }
        for (i in avgFeatures.indices) {
            avgFeatures[i] = avgFeatures[i] / collectedFeatures.size.toFloat()
        }

        // Encode as ByteArray
        val featureBytes = encodeFeatureVector(avgFeatures)

        return Template(
            userId = userId,
            name = name,
            type = TemplateType.CUSTOM,
            sensorType = SensorType.VISION,
            featureVector = featureBytes,
            threshold = threshold
        )
    }

    fun cancelRecording() {
        isRecording = false
        collectedFeatures.clear()
    }

    val recordedFrames: Int get() = collectedFeatures.size
    val totalTargetFrames: Int get() = targetFrames
    val recording: Boolean get() = isRecording

    /**
     * Extract 8 angle features from 17 COCO keypoints.
     * Same logic as CustomPoseActionDetector.extractAngleFeatures.
     */
    private fun extractAngleFeatures(keypoints: Array<FloatArray>): FloatArray? {
        if (keypoints.size < 17) return null

        val minConfidence = 0.3f
        val leftShoulder = keypoints[5]
        val rightShoulder = keypoints[6]
        val leftElbow = keypoints[7]
        val rightElbow = keypoints[8]
        val leftHip = keypoints[11]
        val rightHip = keypoints[12]
        val leftKnee = keypoints[13]
        val rightKnee = keypoints[14]

        val required = arrayOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHip, rightHip, leftKnee, rightKnee)
        if (required.any { it.size < 3 || it[2] < minConfidence }) return null

        // Check ankles for knee angle
        val leftAnkle = if (keypoints[15].size >= 3 && keypoints[15][2] >= minConfidence) keypoints[15] else leftKnee
        val rightAnkle = if (keypoints[16].size >= 3 && keypoints[16][2] >= minConfidence) keypoints[16] else rightKnee
        val leftWrist = if (keypoints[9].size >= 3 && keypoints[9][2] >= minConfidence) keypoints[9] else leftElbow
        val rightWrist = if (keypoints[10].size >= 3 && keypoints[10][2] >= minConfidence) keypoints[10] else rightElbow

        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val leftShoulderAngle = calculateAngle(leftElbow, leftShoulder, leftHip)
        val rightShoulderAngle = calculateAngle(rightElbow, rightShoulder, rightHip)
        val leftHipAngle = calculateAngle(leftShoulder, leftHip, leftKnee)
        val rightHipAngle = calculateAngle(rightShoulder, rightHip, rightKnee)
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)

        return floatArrayOf(
            leftElbowAngle / 180f,
            rightElbowAngle / 180f,
            leftShoulderAngle / 180f,
            rightShoulderAngle / 180f,
            leftHipAngle / 180f,
            rightHipAngle / 180f,
            leftKneeAngle / 180f,
            rightKneeAngle / 180f
        )
    }

    private fun calculateAngle(p1: FloatArray, center: FloatArray, p2: FloatArray): Float {
        val angle1 = atan2(p1[1] - center[1], p1[0] - center[0])
        val angle2 = atan2(p2[1] - center[1], p2[0] - center[0])
        var angle = abs(angle1 - angle2) * 180f / PI.toFloat()
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    private fun encodeFeatureVector(features: FloatArray): ByteArray {
        val bytes = ByteArray(features.size * 4)
        for (i in features.indices) {
            val bits = features[i].toBits()
            bytes[i * 4] = (bits and 0xFF).toByte()
            bytes[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        return bytes
    }
}
