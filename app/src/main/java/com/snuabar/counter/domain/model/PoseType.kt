package com.snuabar.counter.domain.model

/**
 * Body pose types detected from keypoints.
 * Used to validate that the user is in the correct position for an action.
 */
enum class PoseType {
    /** Standing upright (e.g., squat, jump, standing curl) */
    STANDING,

    /** Prone position: face down, body horizontal (e.g., push-up, plank) */
    PRONE,

    /** Supine position: lying on back, face up (e.g., lying crunch, bench press) */
    SUPINE,

    /** Unknown or unsupported pose */
    UNKNOWN
}
