package com.que.aimbot

import kotlin.math.abs
import kotlin.math.sqrt

class AimController(private val screenW: Int, private val screenH: Int) {

    private val headOffsetRatio = 0.15f
    private val sensitivity = 0.35f
    private val deadZone = 0.02f
    private val smoothing = 0.6f

    private var lastRx = 0f
    private var lastRy = 0f

    data class StickOutput(val rx: Float, val ry: Float)

    operator fun StickOutput.component1() = rx
    operator fun StickOutput.component2() = ry

    fun selectTarget(detections: List<Detection>): Detection {
        val centerX = screenW / 2f
        val centerY = screenH / 2f

        return detections.minByOrNull { det ->
            val tx = det.bbox.centerX()
            val ty = det.bbox.top + det.bbox.height() * headOffsetRatio
            val dx = tx - centerX
            val dy = ty - centerY
            sqrt(dx * dx + dy * dy)
        }!!
    }

    fun computeStickDelta(target: Detection): StickOutput {
        val centerX = screenW / 2f
        val centerY = screenH / 2f

        val targetX = target.bbox.centerX()
        val targetY = target.bbox.top + target.bbox.height() * headOffsetRatio

        val dx = (targetX - centerX) / (screenW / 2f)
        val dy = (targetY - centerY) / (screenH / 2f)

        var rx = (dx * sensitivity).coerceIn(-1f, 1f)
        var ry = (dy * sensitivity).coerceIn(-1f, 1f)

        if (abs(rx) < deadZone) rx = 0f
        if (abs(ry) < deadZone) ry = 0f

        rx = lastRx * smoothing + rx * (1f - smoothing)
        ry = lastRy * smoothing + ry * (1f - smoothing)

        lastRx = rx
        lastRy = ry

        return StickOutput(rx, ry)
    }

    fun reset() {
        lastRx = 0f
        lastRy = 0f
    }
}
