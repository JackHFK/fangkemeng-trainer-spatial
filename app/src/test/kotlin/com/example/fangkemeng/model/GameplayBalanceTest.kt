package com.example.fangkemeng.model

import java.io.File
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** 整局真实回合模拟：固定队伍、损伤、遗物、预算和路线一起参与。 */
class GameplayBalanceTest {
    private data class Outcome(val won: Boolean, val depth: Int, val turns: Int, val hp: Int, val gold: Int, val spent: Int)

    private fun simulate(level: Int, tactical: Boolean, charge: Float, build: Boolean, shop: Boolean, risky: Boolean, seed: Int): Outcome {
        val p = PlayerProfile(gold = 0, run = RunState(seed = seed))
        p.party.forEach { p.levels[it] = level }
        check(CampaignFlow.beginRun(p))
        var totalTurns = 0
        var spent = 0
        while (p.run.active) {
            if (p.run.selectedNode == null) {
                val candidates = p.run.availableNodes
                val selected = if (risky) candidates.maxBy { if (it.type == NodeType.ELITE) 2 else 1 }
                    else candidates.minBy { n ->
                        (if (n.type == NodeType.ELITE) 4f else 0f) + n.roster.sumOf { id ->
                            val element = DemoData.speciesOf(id.name)!!.element
                            (p.party.minOf { TypeChart.multiplier(element, DemoData.speciesOf(it.name)!!.element) } * 10).toInt()
                        }
                    }
                check(CampaignFlow.selectNextNode(p, p.run.id, selected.index))
            }
            val node = p.run.currentNode!!
            val state = p.run.newBattle(p, node)
            val engine = BattleEngine(state)
            var turn = 0
            while (engine.winner() == null && turn < 150) {
                engine.startPlayerTurn(Random(seed xor node.index * 7919 xor turn * 104729))
                if (engine.needsForcedSwitch()) engine.applySwitch(true, state.playerSwappable().first())
                fun score(mon: Fangkemeng): Float = mon.moves.filter(mon::canUse).maxOf { m ->
                    m.power * TypeChart.multiplier(m.element, state.enemy.element) * m.accuracy *
                        if (m.element == mon.element) 1.2f else 1f
                }
                val best = if (tactical) (listOf(state.playerActive) + state.playerSwappable()).maxBy { score(state.playerTeam[it]) } else state.playerActive
                val plan = when {
                    tactical && p.revives > 0 && state.playerTeam.any { it.isFainted } -> {
                        check(engine.useRevive(Item("复活符", ItemKind.REVIVE, 50))); p.revives--; engine.planEnemyResponse()
                    }
                    tactical && p.potions > 0 && state.player.hp < state.player.maxHp * .45f -> {
                        engine.useHpPotion(Item("伤药", ItemKind.HP_POTION, 35)); p.potions--; engine.planEnemyResponse()
                    }
                    best != state.playerActive && score(state.playerTeam[best]) > score(state.player) * 1.5f ->
                        listOf(BattleEngine.PlannedAttack(true, state.player.moves.first(), true, best)) + engine.planEnemyResponse()
                    else -> {
                        val move = state.player.moves.filter(state.player::canUse).maxBy { m ->
                            if (tactical) m.power * TypeChart.multiplier(m.element, state.enemy.element) * m.accuracy *
                                if (m.element == state.player.element) 1.2f else 1f
                            else m.power.toFloat()
                        }
                        engine.planTurn(move)
                    }
                }
                for (action in plan) {
                    if (engine.isFainted(action.attackerIsPlayer)) continue
                    if (action.isSwitch) engine.applySwitch(action.attackerIsPlayer, action.switchTo)
                    else engine.applyAttack(action.attackerIsPlayer, action.move, if (action.attackerIsPlayer) charge else 1f)
                    if (engine.winner() != null) break
                }
                if (engine.winner() == null) engine.endTurn()
                engine.replaceFaintedEnemy()
                turn++
            }
            check(turn < 150) { "Non-terminating battle seed=$seed node=${node.index}" }
            totalTurns += turn
            if (engine.winner() != true) { check(CampaignFlow.loseRun(p, p.run.id, state)); break }
            check(CampaignFlow.finishBattle(p, p.run.id, node, state))
            val health = p.run.hp.values.sum().toFloat() / p.party.sumOf { CreatureGrowth.maxHp(p.levelOf(it)) }
            fun value(id: String): Int = when (id) {
                Relic.ECHO_FANG.id -> 4
                Relic.LEAF_CREST.id, Relic.TIDE_CREST.id -> 3
                Relic.FIRST_MOVE.id, Relic.ENERGY_TANK.id, Relic.LAST_STAND.id -> 2
                else -> 1
            }
            val selected = if (build && health > .6f && p.run.hp.values.none { it == 0 })
                p.run.rewardChoices.filter { it.relicId != null }.maxBy { value(it.relicId!!) }
                else p.run.rewardChoices.first()
            val replacement = if (selected.relicId != null && p.run.relics.size == 3) p.run.relics.minBy(::value) else null
            check(CampaignFlow.pickReward(p, p.run.id, node.index, selected.id, replacement))
            if (shop && p.run.shopAvailable) {
                val before = p.run.gold
                if (p.run.hp.values.any { it == 0 }) CampaignFlow.purchaseSupply(p, p.run.id, "rescue")
                CampaignFlow.purchaseSupply(p, p.run.id, "rest")
                repeat(2) { CampaignFlow.purchaseSupply(p, p.run.id, "potion") }
                spent += before - p.run.gold
            }
        }
        return Outcome(p.run.phase == RunPhase.WON, p.run.path.size, totalTurns, p.run.hp.values.sum(), p.gold, spent)
    }

    @Test fun seededFullRunBalanceAndBoundedGrowth() {
        val rows = mutableListOf("level,policy,charge,rewards,shop,route,seeds,wins,meanDepth,meanTurns,meanHp,meanGold,meanSpent")
        val results = mutableMapOf<String, List<Outcome>>()
        for (level in listOf(10, 15, 20, 100)) for (tactical in listOf(false, true)) for (charge in listOf(1f, 1.5f, 2f))
            for (build in listOf(false, true)) for (shop in listOf(false, true)) for (risky in listOf(false, true)) {
                val outcomes = (0 until 100).map { simulate(level, tactical, charge, build, shop, risky, it) }
                val key = "$tactical/$charge/$build/$shop/$risky"
                if (level == 20) results[key] = outcomes
                if (level == 100) assertEquals(results[key], outcomes)
                rows += "$level,${if (tactical) "tactical" else "power"},$charge,${if (build) "build" else "recovery"},$shop," +
                    "${if (risky) "elite" else "safer"},100,${outcomes.count { it.won }},${outcomes.map { it.depth }.average()}," +
                    "${outcomes.map { it.turns }.average()},${outcomes.map { it.hp }.average()},${outcomes.map { it.gold }.average()}," +
                    outcomes.map { it.spent }.average()
            }
        System.getenv("FANGKEMENG_BALANCE_OUTPUT")?.let { File(it).writeText(rows.joinToString("\n") + "\n") }
        println(rows.joinToString("\n"))
    }
}
