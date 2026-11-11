package com.example.fangkemeng.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleEngineTest {

    private fun mon(
        name: String = "测试兽",
        element: Element = Element.NORMAL,
        level: Int = 10,
        hp: Int = 60,
        speed: Int = 15,
        energy: Int = 6,
        status: StatusEffect? = null,
        moves: List<Move> = listOf(Move("撞击", 40, "clip", Element.NORMAL, 1, 100)),
    ) = Fangkemeng(
        species = Species(name, CreatureId.PUFF, element),
        level = level,
        maxHp = hp,
        hp = hp,
        speed = speed,
        moves = moves,
        energy = energy,
        status = status,
    )

    private fun state(
        player: Fangkemeng = mon(),
        enemy: Fangkemeng = mon(name = "对手", hp = 60),
        relics: Set<String> = emptySet(),
    ) = BattleState(
        playerTeam = mutableListOf(player, mon(name = "替补1"), mon(name = "替补2")),
        enemyTeam = mutableListOf(enemy, mon(name = "敌替1"), mon(name = "敌替2")),
        relics = relics,
    )

    @Test
    fun typeChart_strongAndWeak() {
        assertEquals(2f, TypeChart.multiplier(Element.GRASS, Element.WATER))
        assertEquals(2f, TypeChart.multiplier(Element.WATER, Element.FIRE))
        assertEquals(2f, TypeChart.multiplier(Element.FIRE, Element.GRASS))
        assertEquals(2f, TypeChart.multiplier(Element.ELECTRIC, Element.WATER))
        assertEquals(0.5f, TypeChart.multiplier(Element.NORMAL, Element.ROCK))
        assertEquals(1f, TypeChart.multiplier(Element.NORMAL, Element.WATER))
    }

    @Test
    fun damage_appliesTypeStabAndRelic() {
        val attacker = mon(element = Element.GRASS)
        val defender = mon(name = "水兽", element = Element.WATER)
        val engine = BattleEngine(state(attacker, defender), Random(7))
        val grassMove = Move("藤鞭", 40, "clip", Element.GRASS, 3, 100)

        val plain = engine.computeDamage(attacker, grassMove, defender)
        val boosted = BattleEngine(
            state(attacker, defender, relics = setOf(Relic.LEAF_CREST.id)),
            Random(7),
        ).computeDamage(attacker, grassMove, defender)

        assertTrue("克制+STAB 应高于普通伤害", plain > 10)
        assertTrue("草之徽应进一步提高伤害", boosted > plain)
    }

    @Test
    fun burn_ticksSixteenthAndShowsInDamage() {
        val burner = mon(element = Element.FIRE, hp = 64, status = StatusEffect.BURN)
        val target = mon(name = "目标", element = Element.WATER, hp = 64)
        val st = state(burner, target)
        val engine = BattleEngine(st, Random(3))
        val ticks = engine.endTurn()
        val burn = ticks.first { it.effect == StatusEffect.BURN }
        assertEquals(4, burn.damage) // 64 / 16
        assertEquals(60, burn.remainingHp)
    }

    @Test
    fun poison_ticksEighth() {
        val poisoned = mon(hp = 64, status = StatusEffect.POISON)
        val st = state(poisoned, mon(name = "对手", hp = 64))
        val engine = BattleEngine(st, Random(3))
        val tick = engine.endTurn().first { it.effect == StatusEffect.POISON && it.isPlayer }
        assertEquals(8, tick.damage) // 64 / 8
    }

    @Test
    fun energy_regenCapsAtMax() {
        val p = mon(energy = 5)
        val st = state(p)
        BattleEngine(st, Random(1)).endTurn()
        assertEquals(Fangkemeng.MAX_ENERGY, st.player.energy)
    }

    @Test
    fun twoEnergy_recoversFromActualCostAndOnlyForActiveMembers() {
        listOf(
            Triple(1, 1f, 3),
            Triple(1, 1.5f, 2),
            Triple(2, 1f, 2),
            Triple(2, 1.5f, 2),
        ).forEach { (baseCost, charge, nextEnergy) ->
            val st = state(mon(energy = 2))
            st.playerTeam[1].energy = 1
            val engine = BattleEngine(st, Random(7))
            val move = Move("测试招式", 20, "clip", cost = baseCost)
            assertEquals(4 - nextEnergy, BattleEngine.energyCost(move, engine.affordableCharge(move, charge)))
            engine.applyAttack(true, move, charge)
            assertEquals(nextEnergy - BattleEngine.ENERGY_REGEN, st.player.energy)
            engine.endTurn()
            assertEquals("基础消耗=$baseCost 倍率=$charge", nextEnergy, st.player.energy)
            assertEquals(1, st.playerTeam[1].energy)
        }
    }

    @Test
    fun energyTank_bonusIsAppliedAtBattleStartNotPerTurn() {
        val team = DemoData.newPlayerTeam(relics = setOf(Relic.ENERGY_TANK.id))
        assertTrue(team.all { it.energy == 8 && it.maxEnergy == 8 })
        val tankState = BattleState(team, DemoData.newPlayerTeam())
        tankState.player.energy = 7
        BattleEngine(tankState).endTurn()
        assertEquals(8, tankState.player.energy)
        val p = mon(energy = 2)
        val st = state(p, relics = setOf(Relic.ENERGY_TANK.id))
        BattleEngine(st, Random(1)).endTurn()
        assertEquals(4, st.player.energy)
    }

    @Test
    fun canUse_requiresEnoughEnergy() {
        val p = mon(energy = 1)
        val st = state(p)
        val engine = BattleEngine(st, Random(1))
        assertTrue(engine.canUse(true, Move("小招", 20, "clip", Element.NORMAL, 1, 100)))
        assertFalse(engine.canUse(true, Move("大招", 60, "clip", Element.NORMAL, 3, 100)))
    }

    @Test
    fun switch_changesActiveAndRejectsFainted() {
        val st = state()
        val engine = BattleEngine(st, Random(1))
        st.playerTeam[1].hp = 0
        engine.applySwitch(true, 1)
        assertEquals(0, st.playerActive)
        engine.applySwitch(true, 2)
        assertEquals(2, st.playerActive)
    }

    @Test
    fun forcedSwitch_whenActiveFainted() {
        val st = state()
        val engine = BattleEngine(st, Random(1))
        assertFalse(engine.needsForcedSwitch())
        st.player.hp = 0
        assertTrue(engine.needsForcedSwitch())
    }

    @Test
    fun heal_relicIncreasesAmount() {
        val wounded = mon(hp = 60).copy(hp = 20, maxHp = 60)
        val st = state(wounded, relics = setOf(Relic.MED_BAG.id))
        val engine = BattleEngine(st, Random(1))
        val healed = engine.useHpPotion(Item("伤药", ItemKind.HP_POTION, 20))
        assertEquals(30, healed) // 20 + 10（药品袋）
    }

    @Test
    fun enemySwitches_whenLowHpAndHasAdvantage() {
        val player = mon(element = Element.WATER, hp = 60)
        val enemy = mon(name = "弱势", element = Element.FIRE, hp = 60)
        val bench = mon(name = "克制", element = Element.ELECTRIC, hp = 60)
        enemy.hp = 10
        val st = BattleState(
            playerTeam = mutableListOf(player),
            enemyTeam = mutableListOf(enemy, bench, mon(name = "敌替2")),
        )
        val engine = BattleEngine(st, Random(5))
        val plan = engine.planTurn(Move("撞击", 40, "clip", Element.NORMAL, 1, 100))
        assertTrue(plan.any { it.isSwitch && !it.attackerIsPlayer && it.switchTo == 1 })
    }

    @Test
    fun winner_detectsTeamWipe() {
        val st = state()
        val engine = BattleEngine(st, Random(1))
        assertNotEquals(true, engine.winner())
        st.enemyTeam.forEach { it.hp = 0 }
        assertEquals(true, engine.winner())

        val st2 = state()
        val engine2 = BattleEngine(st2, Random(1))
        st2.playerTeam.forEach { it.hp = 0 }
        assertEquals(false, engine2.winner())
    }

    @Test
    fun revive_restoresFirstFainted() {
        val st = state()
        val engine = BattleEngine(st, Random(1))
        st.playerTeam[1].hp = 0
        val ok = engine.useRevive(Item("复活符", ItemKind.REVIVE, 50))
        assertTrue(ok)
        assertTrue(st.playerTeam[1].hp > 0)
    }

    @Test
    fun replacement_continuesUntilWholeEnemyTeamFaints() {
        repeat(2) {
            val st = state(player = mon(level = 100))
            val engine = BattleEngine(st, Random(7))
            st.enemyTeam.forEach { it.hp = 1 }
            for (index in 0..2) {
                assertEquals(index, st.enemyActive)
                engine.applyAttack(true, st.player.moves.first())
                if (index < 2) {
                    assertEquals(null, engine.winner())
                    assertTrue(engine.replaceFaintedEnemy())
                    assertEquals(1, st.enemy.hp)
                } else {
                    assertEquals(true, engine.winner())
                    assertFalse(engine.replaceFaintedEnemy())
                }
            }
        }
    }

    @Test
    fun replacement_rejectsLivingActiveAndSkipsFaintedBench() {
        val st = state()
        val engine = BattleEngine(st, Random(1))
        assertFalse(engine.replaceFaintedEnemy())
        st.enemyTeam[0].hp = 0
        st.enemyTeam[1].hp = 0
        assertTrue(engine.replaceFaintedEnemy())
        assertEquals(2, st.enemyActive)
    }

    @Test
    fun replacement_handlesPoisonKnockoutAndForcedPlayerSwitch() {
        val st = state()
        st.enemy.hp = 1
        st.enemy.status = StatusEffect.POISON
        st.player.hp = 1
        st.player.status = StatusEffect.POISON
        val engine = BattleEngine(st, Random(1))
        val ticks = engine.endTurn()
        assertTrue(ticks.all { it.fainted })
        assertTrue(engine.needsForcedSwitch())
        assertTrue(engine.replaceFaintedEnemy())
        assertEquals(1, st.enemyActive)
        engine.applySwitch(true, 1)
        assertFalse(engine.needsForcedSwitch())
    }

    @Test
    fun replacement_doesNotSpawnAfterPlayerWipe() {
        val st = state()
        st.playerTeam.forEach { it.hp = 0 }
        st.enemy.hp = 0
        val engine = BattleEngine(st, Random(1))
        assertEquals(false, engine.winner())
        assertFalse(engine.replaceFaintedEnemy())
    }

    @Test
    fun planTurn_bothParalyzedProducesNoAttack() {
        val st = state()
        st.player.status = StatusEffect.PARALYZE
        st.enemy.status = StatusEffect.PARALYZE
        val zero = object : Random() { override fun nextBits(bitCount: Int): Int = 0 }
        val plan = BattleEngine(st, zero).planTurn(st.player.moves.first())
        assertTrue(plan.isEmpty())
    }

    @Test fun enemyDoesNotReceivePlayerDamageRelic() {
        val enemy = mon(element = Element.GRASS)
        val player = mon(element = Element.WATER)
        val move = Move("藤鞭", 55, "clip", Element.GRASS)
        val base = BattleEngine(state(player, enemy), Random(7)).computeDamage(enemy, move, player)
        val withRelic = BattleEngine(state(player, enemy, setOf(Relic.LEAF_CREST.id)), Random(7)).computeDamage(enemy, move, player)
        assertEquals(base, withRelic)
    }

    @Test fun chargedAttackRequiresTheExtraEnergy() {
        val move = Move("冲撞", 55, "clip", cost = 3)
        val st = state(mon(energy = 3))
        val actual = BattleEngine(st, Random(9)).applyAttack(true, move, 1.5f)
        val plain = BattleEngine(state(mon(energy = 3)), Random(9)).applyAttack(true, move)
        assertEquals(plain.damage, actual.damage)
        assertEquals(0, st.player.energy)
        val chargedState = state(mon(energy = 4))
        val charged = BattleEngine(chargedState, Random(9)).applyAttack(true, move, 1.5f)
        assertTrue(charged.damage > plain.damage)
        assertEquals(0, chargedState.player.energy)
    }

    @Test fun supportResponseRespectsEnemyParalysisAndSwitchDecision() {
        val st = state()
        st.enemy.status = StatusEffect.PARALYZE
        val zero = object : Random() { override fun nextBits(bitCount: Int): Int = 0 }
        assertTrue(BattleEngine(st, zero).planEnemyResponse().isEmpty())
        assertTrue(st.log.last().contains("麻痹无法行动"))
        val other = state(mon(element = Element.WATER), mon(element = Element.FIRE).copy(hp = 10))
        other.enemyTeam[1] = mon(element = Element.ELECTRIC)
        val action = BattleEngine(other, Random(5)).planEnemyResponse().single()
        assertTrue(action.isSwitch)
        assertEquals(1, action.switchTo)
    }
}
