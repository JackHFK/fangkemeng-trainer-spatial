package com.example.fangkemeng.battle

import kotlin.math.min
import kotlin.math.sqrt

/**
 * 接受的用户位置和整体朝向只在持续位移后更新。
 * 头显没有身体朝向数据：使用入场朝向和移动期间的非注视朝向估计，
 * 看猫、原地转头和短暂探身都不会改变地面锚点。
 */
internal class CompanionFollowAnchor {
    var x = 0f
        private set
    var z = 0f
        private set
    var forwardX = 0f
        private set
    var forwardZ = -1f
        private set
    private var initialized = false
    private var dwell = 0f
    private var headingX = 0f
    private var headingZ = 0f
    private var headingWeight = 0f

    fun update(dt: Float, userX: Float, userZ: Float, lookX: Float, lookZ: Float, lookingAtCat: Boolean) {
        if (!initialized) {
            x = userX; z = userZ
            setHeading(lookX, lookZ)
            initialized = true
            return
        }
        val dx = userX - x; val dz = userZ - z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < .35f) {
            dwell = 0f; headingX = 0f; headingZ = 0f; headingWeight = 0f
            return
        }
        if (distance < .45f) return
        val elapsed = min(dt.coerceAtLeast(0f), .1f)
        dwell += elapsed
        if (!lookingAtCat) {
            headingX += lookX * elapsed; headingZ += lookZ * elapsed
            headingWeight += elapsed
        }
        if (dwell < 1.5f) return
        x = userX; z = userZ
        if (headingWeight > .3f) setHeading(headingX, headingZ)
        dwell = 0f; headingX = 0f; headingZ = 0f; headingWeight = 0f
    }

    private fun setHeading(hx: Float, hz: Float) {
        val length = sqrt(hx * hx + hz * hz)
        if (length > .01f) { forwardX = hx / length; forwardZ = hz / length }
    }
}
