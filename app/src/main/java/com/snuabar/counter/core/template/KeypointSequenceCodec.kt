package com.snuabar.counter.core.template

import kotlin.math.PI
import kotlin.math.atan2

/**
 * Utility for encoding/decoding keypoint sequences.
 * Format: [4 bytes frame count][4 bytes keypoint count per frame][keypoint data...]
 * Each keypoint: [x, y, confidence] as 3 floats (12 bytes)
 */
object KeypointSequenceCodec {

    data class KeypointSequence(
        val frames: List<List<FloatArray>>
    )

    fun decode(bytes: ByteArray): KeypointSequence? {
        if (bytes.size < 8) return null

        val frameCount = readInt(bytes, 0)
        val keypointCount = readInt(bytes, 4)

        if (frameCount <= 0 || keypointCount <= 0) return null

        val expectedSize = 8 + frameCount * keypointCount * 3 * 4
        if (bytes.size < expectedSize) return null

        val frames = mutableListOf<List<FloatArray>>()
        var offset = 8

        for (f in 0 until frameCount) {
            val keypoints = mutableListOf<FloatArray>()
            for (k in 0 until keypointCount) {
                val x = readFloat(bytes, offset)
                val y = readFloat(bytes, offset + 4)
                val confidence = readFloat(bytes, offset + 8)
                keypoints.add(floatArrayOf(x, y, confidence))
                offset += 12
            }
            frames.add(keypoints)
        }

        return KeypointSequence(frames)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readFloat(bytes: ByteArray, offset: Int): Float {
        val bits = readInt(bytes, offset)
        return Float.fromBits(bits)
    }
}
