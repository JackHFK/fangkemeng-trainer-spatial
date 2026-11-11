package com.example.fangkemeng.input

import com.pico.spatial.core.math.Vector3
import kotlin.math.sqrt

/** 手势姿态。 */
enum class HandPose { OPEN, FIST, UNKNOWN }

enum class HandSide { LEFT, RIGHT }

/**
 * 手势识别（纯几何，不依赖 SDK hand 类型，只用关节坐标 Map）。
 *
 * 调用方每帧把某只手的 26 关节坐标（`HandJoint.Index.name -> position`）传进来：
 * - 张开/握拳：用四指指尖到手腕的平均距离判定 + N 帧防抖。
 * - 投掷：对掌心/手腕位置做指数平滑求速度；当「握→张」切换且速度超阈值时认为投出。
 *
 * 关节名匹配用子串（兼容不同 SDK 版本的枚举命名）：
 * - 手腕：name 含 "WRIST"（回退 "PALM"）
 * - 指尖：name 含 "TIP" 且不含 "THUMB"（四指）
 */
class HandGestureDetector {

    private class HandState {
        var rawPose: HandPose = HandPose.UNKNOWN
        var stablePose: HandPose = HandPose.UNKNOWN
        var sameCount: Int = 0
        var wrist: Vector3? = null
        // 掌心（PALM 关节）：速度参考点。
        var palm: Vector3? = null
        // 手心锚点：四指掌指关节(MCP)中点 —— 真正"握球/托菜单"的位置，在手掌靠手指一侧。
        var anchor: Vector3? = null
        var vel: Vector3 = Vector3(0f, 0f, 0f)
        var hasVel: Boolean = false
        var lastExtension: Float = 0f
    }

    private val left = HandState()
    private val right = HandState()

    private fun state(side: HandSide) = if (side == HandSide.LEFT) left else right

    /**
     * 喂入一帧。joints 为 null 表示该手当前不可见（重置该手状态）。
     * 返回该手当前稳定姿态。
     */
    fun update(side: HandSide, joints: Map<String, Vector3>?, dtSec: Float): HandPose {
        val st = state(side)
        if (joints == null || joints.isEmpty()) {
            st.rawPose = HandPose.UNKNOWN
            st.stablePose = HandPose.UNKNOWN
            st.sameCount = 0
            st.hasVel = false
            st.wrist = null
            st.palm = null
            st.anchor = null
            return HandPose.UNKNOWN
        }

        val wrist = findWrist(joints)
        val palm = findPalm(joints) ?: wrist
        val tips = findFingerTips(joints)
        // 手心锚点 = 四指掌指关节(MCP)中点；取不到则回退到指根附近（PALM 与指尖中点）。
        st.anchor = findFingerMcpCenter(joints)
            ?: if (palm != null && tips.isNotEmpty()) {
                val tipC = avg(tips)
                Vector3((palm.x + tipC.x) / 2f, (palm.y + tipC.y) / 2f, (palm.z + tipC.z) / 2f)
            } else palm

        // 速度估计（掌心位置差，指数平滑）—— 作为投掷初速度参考。
        if (palm != null) {
            val prev = st.palm
            if (prev != null && dtSec > 1e-4f) {
                val instVx = (palm.x - prev.x) / dtSec
                val instVy = (palm.y - prev.y) / dtSec
                val instVz = (palm.z - prev.z) / dtSec
                if (st.hasVel) {
                    st.vel = Vector3(
                        lerp(st.vel.x, instVx, VEL_SMOOTHING),
                        lerp(st.vel.y, instVy, VEL_SMOOTHING),
                        lerp(st.vel.z, instVz, VEL_SMOOTHING),
                    )
                } else {
                    st.vel = Vector3(instVx, instVy, instVz)
                    st.hasVel = true
                }
            }
            st.palm = palm
        }
        st.wrist = wrist

        // 姿态判定。
        val raw = if (wrist != null && tips.isNotEmpty()) {
            val avgExt = tips.map { dist(it, wrist) }.average().toFloat()
            st.lastExtension = avgExt
            when {
                avgExt > OPEN_THRESHOLD_M -> HandPose.OPEN
                avgExt < FIST_THRESHOLD_M -> HandPose.FIST
                else -> st.rawPose  // 中间地带保持上一帧，避免抖动
            }
        } else {
            HandPose.UNKNOWN
        }

        // N 帧防抖：连续 DEBOUNCE_FRAMES 帧同一 raw 才更新 stable。
        if (raw == st.rawPose) {
            st.sameCount++
        } else {
            st.rawPose = raw
            st.sameCount = 1
        }
        if (st.sameCount >= DEBOUNCE_FRAMES && raw != HandPose.UNKNOWN) {
            st.stablePose = raw
        }
        return st.stablePose
    }

    fun stablePose(side: HandSide): HandPose = state(side).stablePose

    fun wristPosition(side: HandSide): Vector3? = state(side).wrist

    /** 掌心位置（速度参考）。不可见时为 null。 */
    fun palmPosition(side: HandSide): Vector3? = state(side).palm

    /** 手心锚点（四指 MCP 中点）：球吸附 / 菜单跟随用，落在手掌靠手指一侧。不可见时 null。 */
    fun anchorPosition(side: HandSide): Vector3? = state(side).anchor

    fun wristVelocity(side: HandSide): Vector3 = state(side).vel

    /** 速度标量（米/秒），用于投掷阈值判断。 */
    fun speed(side: HandSide): Float {
        val v = state(side).vel
        return sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    }

    /** 调参辅助：当前四指平均伸展度（米）。 */
    fun lastExtension(side: HandSide): Float = state(side).lastExtension

    private fun findWrist(joints: Map<String, Vector3>): Vector3? {
        joints.entries.firstOrNull { it.key.contains("WRIST", ignoreCase = true) }?.let { return it.value }
        joints.entries.firstOrNull { it.key.contains("PALM", ignoreCase = true) }?.let { return it.value }
        return null
    }

    private fun findPalm(joints: Map<String, Vector3>): Vector3? {
        joints.entries.firstOrNull { it.key.contains("PALM", ignoreCase = true) }?.let { return it.value }
        joints.entries.firstOrNull { it.key.contains("WRIST", ignoreCase = true) }?.let { return it.value }
        return null
    }

    private fun findFingerTips(joints: Map<String, Vector3>): List<Vector3> =
        joints.entries
            .filter { it.key.contains("TIP", ignoreCase = true) && !it.key.contains("THUMB", ignoreCase = true) }
            .map { it.value }

    /** 四指掌指关节(MCP/METACARPAL)中点 = 手心靠手指一侧的中心。取不到返回 null。 */
    private fun findFingerMcpCenter(joints: Map<String, Vector3>): Vector3? {
        val mcps = joints.entries.filter { e ->
            e.key.contains("METACARPAL", ignoreCase = true) &&
                !e.key.contains("THUMB", ignoreCase = true)
        }.map { it.value }
        return if (mcps.isEmpty()) null else avg(mcps)
    }

    private fun avg(points: List<Vector3>): Vector3 {
        var sx = 0f; var sy = 0f; var sz = 0f
        for (p in points) { sx += p.x; sy += p.y; sz += p.z }
        val n = points.size.toFloat()
        return Vector3(sx / n, sy / n, sz / n)
    }

    private fun dist(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        // 实机标定：张开 ext≈0.155，握拳 ext≈0.05。取带迟滞的两段阈值。
        const val OPEN_THRESHOLD_M = 0.115f
        const val FIST_THRESHOLD_M = 0.085f
        const val DEBOUNCE_FRAMES = 5
        const val VEL_SMOOTHING = 0.5f

        /** 投掷触发的最小掌心速度（米/秒）。 */
        const val THROW_SPEED_THRESHOLD = 1.0f
    }
}
