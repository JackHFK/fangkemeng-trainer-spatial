package com.example.fangkemeng.battle

import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandTrackingData

/** 把 SDK 的单手追踪数据转成「关节名 -> 世界坐标」Map，喂给纯逻辑手势识别。 */
object HandJointMapper {

    fun rightJoints(data: HandTrackingData): Map<String, Vector3>? =
        data.right?.let { hand -> toMap { jointIndex -> hand[jointIndex].position } }

    fun leftJoints(data: HandTrackingData): Map<String, Vector3>? =
        data.left?.let { hand -> toMap { jointIndex -> hand[jointIndex].position } }

    private inline fun toMap(posOf: (HandJoint.Index) -> Vector3): Map<String, Vector3> {
        val map = HashMap<String, Vector3>(26)
        HandJoint.Index.values().forEach { jointIndex ->
            map[jointIndex.name] = posOf(jointIndex)
        }
        return map
    }
}
