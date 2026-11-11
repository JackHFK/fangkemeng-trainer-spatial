package com.example.fangkemeng.model

/**
 * 招式。
 *
 * [element] 决定克制倍率与粒子表现；[cost] 为能量消耗；[accuracy] 为命中率（0-100）；
 * [status]/[statusChance] 描述命中后附加的状态与概率。
 */
data class Move(
    val name: String,
    val power: Int,
    val animClip: String,
    val element: Element = Element.NORMAL,
    val cost: Int = 1,
    val accuracy: Int = 100,
    val status: StatusEffect? = null,
    val statusChance: Float = 0f,
)

/** 战斗场景里使用的程序化动物类型。 */
enum class CreatureId {
    SEED_CUB,
    VINE_CUB,
    WATER_TURTLE,
    TIDE_TURTLE,
    BIRD,
    FISH,
    LION,
    DRAGON,
    CAT,
    GOLEM,
    SPARK_BUG,
    PUFF,
}

/** 物种：由场景层根据 [creatureId] 生成对应的程序化动物。 */
data class Species(
    val name: String,
    val creatureId: CreatureId,
    val element: Element = Element.NORMAL,
)

/**
 * 精灵实例。[hp]/[energy]/[status]/[exp] 可变，其余出战期间只读。
 *
 * 数值基线由 [CreatureGrowth] 按等级生成。
 */
data class Fangkemeng(
    val species: Species,
    val level: Int,
    val maxHp: Int,
    var hp: Int,
    val speed: Int,
    val moves: List<Move>,
    val maxEnergy: Int = MAX_ENERGY,
    var energy: Int = MAX_ENERGY,
    var status: StatusEffect? = null,
    var exp: Int = 0,
) {
    val isFainted: Boolean get() = hp <= 0

    val element: Element get() = species.element

    /** 回合开始前的实际速度：麻痹减半。 */
    val effectiveSpeed: Int
        get() = if (status == StatusEffect.PARALYZE) (speed / 2).coerceAtLeast(1) else speed

    /** 是否可用该招式（能量足够）。 */
    fun canUse(move: Move): Boolean = energy >= move.cost

    companion object {
        const val MAX_ENERGY = 6
    }
}

enum class ItemKind { HP_POTION, POKEBALL, REVIVE }

data class Item(
    val name: String,
    val kind: ItemKind,
    val amount: Int,
)

enum class BattlePhase { SETUP, PLAYER_TURN, RESOLVING, RESULT }

data class MonBattleState(val hp: Int, val energy: Int, val status: StatusEffect?)

/** 只保存完整回合边界；生命、能量、异常和敌方换人次数一起恢复。 */
data class SavedBattle(
    val player: List<MonBattleState>,
    val enemy: List<MonBattleState>,
    val playerActive: Int,
    val enemyActive: Int,
    val turn: Int,
    val enemyHasSwitched: Boolean,
    val companionEnergy: Float = 0f,
) {
    companion object {
        fun capture(state: BattleState, turn: Int, switched: Boolean, companionEnergy: Float = 0f) = SavedBattle(
            state.playerTeam.map { MonBattleState(it.hp, it.energy, it.status) },
            state.enemyTeam.map { MonBattleState(it.hp, it.energy, it.status) },
            state.playerActive, state.enemyActive, turn, switched, companionEnergy)
    }
}

fun RunState.newBattle(profile: PlayerProfile, node: MapNode): BattleState {
    val player = DemoData.newPlayerTeam(profile.party, profile.levels, profile.loadouts, relicSet)
    player.forEach { it.hp = requireNotNull(hp[it.species.creatureId]) }
    val state = BattleState(player, CampaignMap.enemyTeam(node), relics = relicSet, layer = node.depth,
        elite = node.type != NodeType.BATTLE, playerActive = player.indexOfFirst { !it.isFainted }.coerceAtLeast(0))
    battle?.let { saved ->
        state.playerTeam.zip(saved.player).forEach { (mon, value) -> mon.hp = value.hp; mon.energy = value.energy; mon.status = value.status }
        state.enemyTeam.zip(saved.enemy).forEach { (mon, value) -> mon.hp = value.hp; mon.energy = value.energy; mon.status = value.status }
        state.playerActive = saved.playerActive
        state.enemyActive = saved.enemyActive
    }
    return state
}

/** 一场战斗的可变状态。3v3，双方各有 active 索引。 */
class BattleState(
    val playerTeam: MutableList<Fangkemeng>,
    val enemyTeam: MutableList<Fangkemeng>,
    var playerActive: Int = 0,
    var enemyActive: Int = 0,
    var phase: BattlePhase = BattlePhase.SETUP,
    val log: MutableList<String> = mutableListOf(),
    /** 本轮肉鸽遗物 id 集合，参与伤害与治疗计算。 */
    val relics: Set<String> = emptySet(),
    /** 主线稳定节点标识，用于结算与奖励。 */
    var layer: Int = 1,
    /** 该关卡是否为精英（HP +20%）。 */
    var elite: Boolean = false,
) {
    val player: Fangkemeng get() = playerTeam[playerActive]
    val enemy: Fangkemeng get() = enemyTeam[enemyActive]

    val playerWiped: Boolean get() = playerTeam.all { it.isFainted }
    val enemyWiped: Boolean get() = enemyTeam.all { it.isFainted }

    fun playerSwappable(): List<Int> = playerTeam.indices.filter { it != playerActive && !playerTeam[it].isFainted }

    fun enemySwappable(): List<Int> = enemyTeam.indices.filter { it != enemyActive && !enemyTeam[it].isFainted }
}
