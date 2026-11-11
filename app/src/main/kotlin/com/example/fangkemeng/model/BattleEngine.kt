package com.example.fangkemeng.model

import kotlin.random.Random

/**
 * 纯逻辑回合引擎（无 Spatial 依赖，便于单测）。
 *
 * 规则：6 属性克制 + STAB、3 种状态异常、能量消耗、3v3 换人、遗物倍率。
 * 玩家选招后 [planTurn] 给出双方出手顺序（含敌方换人），控制器逐拍演出并调用 [applyAttack]。
 */
class BattleEngine(
    private val state: BattleState,
    private var rng: Random = Random.Default,
    initialEnemySwitched: Boolean = false,
) {
    /** 一次攻击的结算结果。 */
    data class AttackResult(
        val attackerIsPlayer: Boolean,
        val move: Move,
        val damage: Int,
        val targetRemainingHp: Int,
        val targetFainted: Boolean,
        val missed: Boolean = false,
        val appliedStatus: StatusEffect? = null,
    )

    /** 计划中的一次出手：普通出招或换人。 */
    data class PlannedAttack(
        val attackerIsPlayer: Boolean,
        val move: Move,
        val isSwitch: Boolean = false,
        val switchTo: Int = -1,
    )

    data class StatusTick(
        val isPlayer: Boolean,
        val effect: StatusEffect,
        val damage: Int,
        val remainingHp: Int,
        val fainted: Boolean,
    )

    var enemySwitchedThisBattle = initialEnemySwitched
        private set

    fun startPlayerTurn(random: Random = rng) {
        rng = random
        state.phase = BattlePhase.PLAYER_TURN
    }

    // ─── 计算 ───

    private fun relicMultiplier(element: Element): Float = when {
        element == Element.GRASS && Relic.LEAF_CREST.id in state.relics -> 1.2f
        element == Element.WATER && Relic.TIDE_CREST.id in state.relics -> 1.2f
        element == Element.FIRE && Relic.EMBER_CREST.id in state.relics -> 1.2f
        element == Element.ELECTRIC && Relic.VOLT_CREST.id in state.relics -> 1.2f
        else -> 1f
    }

    /** 基础招式伤害 × 有界成长 × 克制 × 本系 × 异常 × 我方遗物 × 随机 × 蓄力。 */
    fun computeDamage(
        attacker: Fangkemeng,
        move: Move,
        defender: Fangkemeng,
        chargeMultiplier: Float = 1f,
    ): Int {
        val type = TypeChart.multiplier(move.element, defender.element)
        val stab = if (move.element == attacker.element) 1.2f else 1f
        val burn = if (attacker.status == StatusEffect.BURN) 0.85f else 1f
        val variance = 0.85f + rng.nextFloat() * 0.15f
        val relic = if (attacker === state.player) relicMultiplier(move.element) *
            if (Relic.LAST_STAND.id in state.relics && attacker.hp < attacker.maxHp * .35f) 1.15f else 1f else 1f
        val raw = move.power * CreatureGrowth.BASE_LEVEL / 50f * CreatureGrowth.damageMultiplier(attacker.level) *
            type * stab * burn * relic * variance * chargeMultiplier
        return raw.toInt().coerceAtLeast(1)
    }

    fun canUse(isPlayer: Boolean, move: Move): Boolean =
        (if (isPlayer) state.player else state.enemy).canUse(move)

    fun affordableCharge(move: Move, multiplier: Float): Float =
        if (state.player.energy < energyCost(move, multiplier)) 1f else multiplier

    /** 敌方选招：期望伤害最高且能量足够。 */
    fun chooseEnemyMove(): Move {
        val enemy = state.enemy
        val player = state.player
        return enemy.moves
            .filter { enemy.canUse(it) }
            .maxByOrNull { it.power * TypeChart.multiplier(it.element, player.element) }
            ?: enemy.moves.first()
    }

    /** 敌方是否应该换人：血量低于 35% 且替补对该敌人有属性优势，每场最多一次。 */
    private fun shouldEnemySwitch(): Int {
        if (enemySwitchedThisBattle) return -1
        val enemy = state.enemy
        if (enemy.hp > enemy.maxHp * 0.35f) return -1
        val playerElement = state.player.element
        return state.enemySwappable().maxByOrNull { idx ->
            TypeChart.multiplier(state.enemyTeam[idx].element, playerElement) -
                TypeChart.multiplier(enemy.element, playerElement)
        }?.takeIf { idx ->
            TypeChart.multiplier(state.enemyTeam[idx].element, playerElement) >
                TypeChart.multiplier(enemy.element, playerElement)
        } ?: -1
    }

    private fun enemyAction(): PlannedAttack? {
        if (rng.nextFloat() < PARALYZE_SKIP_CHANCE && state.enemy.status == StatusEffect.PARALYZE) {
            state.log += "对方 ${state.enemy.species.name} 因麻痹无法行动"
            return null
        }
        val switchTo = shouldEnemySwitch()
        return if (switchTo >= 0) {
            enemySwitchedThisBattle = true
            PlannedAttack(false, state.enemy.moves.first(), isSwitch = true, switchTo = switchTo)
        } else PlannedAttack(false, chooseEnemyMove())
    }

    /** 换人、道具和援助后的敌方决策沿用攻击回合的麻痹与换人规则。 */
    fun planEnemyResponse(): List<PlannedAttack> {
        state.phase = BattlePhase.RESOLVING
        return listOfNotNull(enemyAction())
    }

    /**
     * 规划本回合：玩家用 [playerMove]，敌方自动决策（可能换人），按有效速度排序（不在此处扣血）。
     * 麻痹方有 25% 概率本回合无法行动，直接记日志并从计划中移除。
     */
    fun planTurn(playerMove: Move): List<PlannedAttack> {
        state.phase = BattlePhase.RESOLVING

        val playerActs = rng.nextFloat() >= PARALYZE_SKIP_CHANCE || state.player.status != StatusEffect.PARALYZE

        if (!playerActs) {
            state.log += "我方 ${state.player.species.name} 因麻痹无法行动"
        }
        val enemyAction = enemyAction()

        val playerAttacks = playerActs && playerMove.power > 0
        val playerAction: PlannedAttack? = if (playerAttacks || playerActs) {
            PlannedAttack(true, playerMove)
        } else {
            null
        }

        val playerSpeed = state.player.effectiveSpeed +
            if (Relic.FIRST_MOVE.id in state.relics) 3 else 0
        val enemySpeed = state.enemy.effectiveSpeed
        val playerFirst = playerSpeed >= enemySpeed

        val first = if (playerFirst) listOfNotNull(playerAction, enemyAction) else listOfNotNull(enemyAction, playerAction)
        return first
    }

    /** 真正结算一次攻击：命中判定 → 能量消耗 → 伤害 → 状态施加。 */
    fun applyAttack(
        attackerIsPlayer: Boolean,
        move: Move,
        chargeMultiplier: Float = 1f,
    ): AttackResult {
        val attacker = if (attackerIsPlayer) state.player else state.enemy
        val target = if (attackerIsPlayer) state.enemy else state.player
        val effectiveCharge = if (attackerIsPlayer) affordableCharge(move, chargeMultiplier) else chargeMultiplier
        attacker.energy = (attacker.energy - energyCost(move, effectiveCharge)).coerceAtLeast(0)

        if (rng.nextInt(100) >= move.accuracy) {
            state.log += "${if (attackerIsPlayer) "我方" else "对方"} ${move.name} 未命中"
            return AttackResult(attackerIsPlayer, move, 0, target.hp, target.isFainted, missed = true)
        }

        val dmg = computeDamage(attacker, move, target, effectiveCharge)
        target.hp = (target.hp - dmg).coerceAtLeast(0)
        if (attackerIsPlayer && Relic.ECHO_FANG.id in state.relics)
            attacker.hp = (attacker.hp + 2).coerceAtMost(attacker.maxHp)

        var applied: StatusEffect? = null
        if (move.status != null && target.status == null && rng.nextFloat() < move.statusChance) {
            target.status = move.status
            applied = move.status
        }

        val typeNote = when (TypeChart.multiplier(move.element, target.element)) {
            2f -> "（效果拔群）"
            0.5f -> "（效果不佳）"
            else -> ""
        }
        val chargeNote = if (effectiveCharge > 1.05f) "（蓄力 x%.1f）".format(effectiveCharge) else ""
        val statusNote = applied?.let { "，${it.displayName}" } ?: ""
        state.log += "${if (attackerIsPlayer) "我方" else "对方"} 使用 ${move.name}$chargeNote$typeNote，造成 $dmg 点伤害$statusNote"

        if (target.isFainted) {
            state.log += "${if (attackerIsPlayer) "对方" else "我方"} ${target.species.name} 倒下"
        }

        return AttackResult(
            attackerIsPlayer = attackerIsPlayer,
            move = move,
            damage = dmg,
            targetRemainingHp = target.hp,
            targetFainted = target.isFainted,
            appliedStatus = applied,
        )
    }

    /** 执行换人（不消耗额外资源，回合由控制器标注）。 */
    fun applySwitch(isPlayer: Boolean, index: Int) {
        if (isPlayer) {
            if (index !in state.playerTeam.indices) return
            if (state.playerTeam[index].isFainted) return
            state.playerActive = index
            state.log += "我方换上 ${state.player.species.name}"
        } else {
            if (index !in state.enemyTeam.indices) return
            if (state.enemyTeam[index].isFainted) return
            state.enemyActive = index
            state.log += "对方换上 ${state.enemy.species.name}"
        }
    }

    fun needsForcedSwitch(): Boolean =
        state.player.isFainted && state.playerTeam.any { !it.isFainted }

    /** 击倒替补在回合末执行，替补不会继承倒下精灵本回合的攻击。 */
    fun replaceFaintedEnemy(): Boolean {
        if (!state.enemy.isFainted || state.enemyWiped || state.playerWiped) return false
        val next = state.enemySwappable().firstOrNull() ?: return false
        applySwitch(false, next)
        return true
    }

    /** 回合结束：能量回复 + 状态伤害。返回状态伤害结果供 UI 演出。 */
    fun endTurn(): List<StatusTick> {
        val ticks = mutableListOf<StatusTick>()
        listOf(true, false).forEach { isPlayer ->
            val mon = if (isPlayer) state.player else state.enemy
            val effect = mon.status
            if (effect != null && !mon.isFainted) {
                val dmg = when (effect) {
                    StatusEffect.BURN -> (mon.maxHp / 16).coerceAtLeast(1)
                    StatusEffect.POISON -> (mon.maxHp / 8).coerceAtLeast(1)
                    StatusEffect.PARALYZE -> 0
                }
                if (dmg > 0) {
                    mon.hp = (mon.hp - dmg).coerceAtLeast(0)
                    state.log += "${if (isPlayer) "我方" else "对方"} ${mon.species.name} 受到${effect.displayName}伤害 $dmg"
                    ticks += StatusTick(isPlayer, effect, dmg, mon.hp, mon.isFainted)
                }
            }
            mon.energy = (mon.energy + ENERGY_REGEN).coerceAtMost(mon.maxEnergy)
        }
        return ticks
    }

    /** 伴随宠物援助：治疗玩家出战方可梦，返回实际回复量。 */
    fun healPlayer(amount: Int): Int {
        val bonus = if (Relic.CAT_COLLAR.id in state.relics) 10 else 0
        return heal(amount + bonus, "伙伴猫使出援助技")
    }

    /** 使用伤药：恢复 HP。 */
    fun useHpPotion(item: Item): Int {
        val bonus = if (Relic.MED_BAG.id in state.relics) 10 else 0
        return heal(item.amount + bonus, "我方使用 ${item.name}")
    }

    /** 使用复活符：复活队伍中第一只倒下的精灵，回复 50% HP。 */
    fun useRevive(item: Item): Boolean {
        val idx = state.playerTeam.indexOfFirst { it.isFainted }
        if (idx < 0) return false
        val mon = state.playerTeam[idx]
        mon.hp = (mon.maxHp * item.amount / 100).coerceAtLeast(1)
        mon.status = null
        state.log += "使用 ${item.name}，${mon.species.name} 重新站起"
        return true
    }

    private fun heal(amount: Int, source: String): Int {
        val before = state.player.hp
        state.player.hp = (state.player.hp + amount).coerceAtMost(state.player.maxHp)
        val healed = state.player.hp - before
        state.log += "$source，回复 $healed 点 HP"
        return healed
    }

    /** 当前胜负：true=玩家胜，false=玩家负，null=继续。 */
    fun winner(): Boolean? = when {
        state.enemyWiped -> true
        state.playerWiped -> false
        else -> null
    }

    /** 某一方当前是否已倒下（用于跳过其出手）。 */
    fun isFainted(isPlayer: Boolean): Boolean =
        if (isPlayer) state.player.isFainted else state.enemy.isFainted

    companion object {
        private const val PARALYZE_SKIP_CHANCE = 0.25f
        const val ENERGY_REGEN = 2

        fun energyCost(move: Move, chargeMultiplier: Float): Int =
            move.cost + if (chargeMultiplier > 1.05f) 1 else 0
    }
}
