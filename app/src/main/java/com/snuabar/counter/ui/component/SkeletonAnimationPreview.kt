package com.snuabar.counter.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.snuabar.counter.core.template.KeypointSequenceCodec
import kotlinx.coroutines.delay

/**
 * Skeleton animation preview that loops through keypoint frames.
 * Draws a simplified stick figure using COCO keypoint format.
 */
@Composable
fun SkeletonAnimationPreview(
    keypointSequence: ByteArray?,
    modifier: Modifier = Modifier,
    frameDelayMs: Long = 100
) {
    if (keypointSequence == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            // Show a placeholder or nothing
        }
        return
    }

    val sequence = remember(keypointSequence) {
        KeypointSequenceCodec.decode(keypointSequence)
    }

    if (sequence == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            // Invalid data
        }
        return
    }

    val frames = sequence.frames
    var currentFrameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(frames, frameDelayMs) {
        while (true) {
            delay(frameDelayMs)
            currentFrameIndex = (currentFrameIndex + 1) % frames.size
        }
    }

    val currentFrame = frames.getOrNull(currentFrameIndex) ?: return

    Canvas(modifier = modifier) {
        if (currentFrame.isEmpty()) return@Canvas

        val pointRadius = 5f
        val lineWidth = 4f
        val minConfidence = 0.3f

        // COCO skeleton connections grouped by body part
        val armConnections = listOf(5 to 7, 7 to 9, 6 to 8, 8 to 10)
        val legConnections = listOf(11 to 13, 13 to 15, 12 to 14, 14 to 16)

        // Scale keypoints to canvas size (normalize to [0,1] first)
        // Use min dimension to keep aspect ratio, center in canvas
        val minSize = kotlin.math.min(size.width, size.height)
        val startX = (size.width - minSize) / 2
        val startY = (size.height - minSize) / 2
        val scaledKps = currentFrame.map { kp ->
            floatArrayOf(
                startX + kp[0] * minSize,
                startY + kp[1] * minSize,
                kp[2]
            )
        }

        // Black background
        drawRect(color = Color.Black, size = this.size)

        // Draw body as white filled polygon (shoulders to hips)
        if (scaledKps.size > 12) {
            val leftShoulder = scaledKps[5]
            val rightShoulder = scaledKps[6]
            val leftHip = scaledKps[11]
            val rightHip = scaledKps[12]
            if (leftShoulder[2] > minConfidence && rightShoulder[2] > minConfidence &&
                leftHip[2] > minConfidence && rightHip[2] > minConfidence
            ) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(leftShoulder[0], leftShoulder[1])
                    lineTo(rightShoulder[0], rightShoulder[1])
                    lineTo(rightHip[0], rightHip[1])
                    lineTo(leftHip[0], leftHip[1])
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }

        // Draw arm connections (blue)
        armConnections.forEach { (i, j) ->
            if (i < scaledKps.size && j < scaledKps.size) {
                if (scaledKps[i][2] > minConfidence && scaledKps[j][2] > minConfidence) {
                    drawLine(
                        color = Color(0xFF0000FF),
                        start = Offset(scaledKps[i][0], scaledKps[i][1]),
                        end = Offset(scaledKps[j][0], scaledKps[j][1]),
                        strokeWidth = lineWidth
                    )
                }
            }
        }

        // Draw leg connections (green)
        legConnections.forEach { (i, j) ->
            if (i < scaledKps.size && j < scaledKps.size) {
                if (scaledKps[i][2] > minConfidence && scaledKps[j][2] > minConfidence) {
                    drawLine(
                        color = Color(0xFF00FF00),
                        start = Offset(scaledKps[i][0], scaledKps[i][1]),
                        end = Offset(scaledKps[j][0], scaledKps[j][1]),
                        strokeWidth = lineWidth
                    )
                }
            }
        }

        // Draw head as white filled ellipse (keypoint 0)
        if (scaledKps.isNotEmpty() && scaledKps[0][2] > minConfidence) {
            val headX = scaledKps[0][0]
            val headY = scaledKps[0][1]
            val headRadiusX = minSize * 0.06f
            val headRadiusY = minSize * 0.075f
            drawOval(
                color = Color.White,
                topLeft = Offset(headX - headRadiusX, headY - headRadiusY),
                size = androidx.compose.ui.geometry.Size(headRadiusX * 2, headRadiusY * 2)
            )
        }

        // Draw keypoints (joints) as red circles
        scaledKps.forEach { kp ->
            if (kp[2] > minConfidence) {
                drawCircle(
                    color = Color(0xFFFF0000),
                    radius = pointRadius,
                    center = Offset(kp[0], kp[1])
                )
            }
        }
    }
}
