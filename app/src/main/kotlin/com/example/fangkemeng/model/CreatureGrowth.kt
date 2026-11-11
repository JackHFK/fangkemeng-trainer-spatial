package com.example.fangkemeng.model

import kotlin.math.roundToInt

/** 金币训练与战斗数值共用的成长预算；旧存档等级保留，数值收益封顶。 */
object CreatureGrowth {
    const val BASE_LEVEL = 10
    const val MAX_LEVEL = 20

    private fun growthSteps(level: Int) = level.coerceIn(1, MAX_LEVEL) - BASE_LEVEL

    fun damageMultiplier(level: Int): Float = 1f + .02f * growthSteps(level)
    fun maxHp(level: Int): Int = (60 * (1f + .015f * growthSteps(level))).roundToInt()
    fun speed(level: Int): Int = 15 + Math.floorDiv(growthSteps(level), 5)
    fun trainingCost(level: Int): Int? =
        if (level >= MAX_LEVEL) null else 40 + 8 * (level - BASE_LEVEL).coerceAtLeast(0)
}
