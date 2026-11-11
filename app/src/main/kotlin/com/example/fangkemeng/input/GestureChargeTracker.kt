package com.example.fangkemeng.input

import com.pico.spatial.core.math.Vector3
import kotlin.math.sqrt

/**
 * 手势蓄力：选招后进入蓄力窗口，玩家在空中持续画动右手（划圈/挥动）累积蓄力进度。
 *
 * 设计为纯几何、无 SDK 依赖，便于在控制器里每帧驱动：
 * - 每帧喂入右手锚点（content/追踪坐标皆可，只用相邻帧位移量）。
 * - 位移速度越快累积越快；停手则缓慢衰减，鼓励持续比划。
 * - [握拳] 由控制器单独检测，用于提前释放；本类只负责 0..1 的蓄力进度。
 */
class GestureChargeTracker {

    var active: Boolean = false
        private set

    /** 0..1 蓄力进度。 */
    var progress: Float = 0f
        private set

    /** 蓄力窗口已持续时间（秒），用于超时自动释放。 */
    var elapsed: Float = 0f
        private set

    private var lastPos: Vector3? = null

    fun begin() {
        active = true
        progress = 0f
        elapsed = 0f
        lastPos = null
    }

    fun cancel() {
        active = false
        progress = 0f
        elapsed = 0f
        lastPos = null
    }

    /**
     * 每帧推进。返回当前进度。
     * [handPos] 为右手锚点（不可见时传 null，只做衰减）。
     */
    fun update(handPos: Vector3?, dtSec: Float): Float {
        if (!active) return progress
        elapsed += dtSec

        val prev = lastPos
        var gained = 0f
        if (handPos != null && prev != null && dtSec > 1e-4f) {
            val d = distance(handPos, prev)
            val speed = d / dtSec
            // 只在挥动速度超过死区时累积，避免静止误触。
            if (speed > MOVE_DEADZONE) {
                gained = (speed - MOVE_DEADZONE) * CHARGE_GAIN * dtSec
            }
        }
        if (handPos != null) lastPos = handPos

        progress = if (gained > 0f) {
            (progress + gained).coerceAtMost(1f)
        } else {
            // 停手缓慢衰减。
            (progress - DECAY_RATE * dtSec).coerceAtLeast(0f)
        }
        return progress
    }

    /** 蓄力进度映射到伤害/粒子倍率：0→1.0 倍，1→[MAX_MULTIPLIER] 倍。 */
    fun multiplier(): Float = 1f + progress * (MAX_MULTIPLIER - 1f)

    private fun distance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        /** 蓄力窗口最长时长（秒）：超时自动以当前进度释放。 */
        const val MAX_WINDOW_SEC = 3.0f
        /** 最大蓄力倍率。 */
        const val MAX_MULTIPLIER = 2.0f

        private const val MOVE_DEADZONE = 0.20f
        private const val CHARGE_GAIN = 0.55f
        private const val DECAY_RATE = 0.35f
    }
}
