package com.riftaddons.util.helper

object AngleUtil {
    fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360.0f
        if (normalized >= 180.0f) {
            normalized -= 360.0f
        }
        if (normalized < -180.0f) {
            normalized += 360.0f
        }
        return normalized
    }

    fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized >= 180.0) {
            normalized -= 360.0
        }
        if (normalized < -180.0) {
            normalized += 360.0
        }
        return normalized
    }

    fun getAngleDifference(angle1: Float, angle2: Float): Float {
        return normalizeAngle(angle1 - angle2)
    }

    fun getAngleDifference(angle1: Double, angle2: Double): Double {
        return normalizeAngle(angle1 - angle2)
    }
}
