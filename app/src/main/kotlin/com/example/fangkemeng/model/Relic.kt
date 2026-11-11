package com.example.fangkemeng.model

/** 本轮肉鸽遗物：只在本轮有效，最多同时装备 3 个。 */
enum class Relic(
    val id: String,
    val displayName: String,
    val description: String,
) {
    LEAF_CREST("LEAF_CREST", "草之徽", "草系招式伤害 +20%"),
    TIDE_CREST("TIDE_CREST", "潮汐徽", "水系招式伤害 +20%"),
    EMBER_CREST("EMBER_CREST", "火种徽", "火系招式伤害 +20%"),
    VOLT_CREST("VOLT_CREST", "电池徽", "电系招式伤害 +20%"),
    FIRST_MOVE("FIRST_MOVE", "先手护符", "速度 +3"),
    ENERGY_TANK("ENERGY_TANK", "能量罐", "初始能量与上限 +2"),
    MED_BAG("MED_BAG", "药品袋", "伤药回复量 +10"),
    CAT_COLLAR("CAT_COLLAR", "伙伴项圈", "伙伴猫治疗 +10"),
    ECHO_FANG("ECHO_FANG", "回响牙", "命中后回复 2 生命"),
    LAST_STAND("LAST_STAND", "背水徽", "生命低于 35% 时伤害 +15%"),
    ;

    companion object {
        fun of(id: String): Relic? = entries.firstOrNull { it.id == id }

        const val MAX_EQUIPPED = 3
    }
}
