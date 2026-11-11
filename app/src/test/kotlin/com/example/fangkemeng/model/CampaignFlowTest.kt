package com.example.fangkemeng.model

import org.junit.Assert.*
import org.junit.Test

class CampaignFlowTest {
    private fun profile(seed: Int = 42) = PlayerProfile(gold = 500, run = RunState(seed = seed, id = "run-a")).also {
        assertTrue(CampaignFlow.beginRun(it))
    }
    private fun win(p: PlayerProfile, hp: Int = 30): MapNode {
        val node = requireNotNull(p.run.currentNode)
        val battle = p.run.newBattle(p, node)
        battle.playerTeam.forEach { it.hp = hp }
        battle.enemyTeam.forEach { it.hp = 0 }
        assertTrue(CampaignFlow.finishBattle(p, p.run.id, node, battle))
        return node
    }
    private fun advance(p: PlayerProfile, count: Int) {
        repeat(count) {
            if (p.run.selectedNode == null) assertTrue(CampaignFlow.selectNextNode(p, p.run.id, p.run.availableNodes.first().index))
            val node = win(p)
            assertTrue(CampaignFlow.pickReward(p, p.run.id, node.index, "recover"))
        }
    }

    @Test fun thousandGeneratedGraphsHaveLockedCorridorsMergesAndSevenBattlePaths() {
        val shapes = mutableSetOf<List<List<Int>>>()
        val rosters = mutableSetOf<List<List<CreatureId>>>()
        repeat(1000) { seed ->
            val run = RunState(seed = seed)
            assertEquals(listOf(1, 3, 3, 1, 3, 1, 1), (1..7).map { d -> run.nodes.count { it.depth == d } })
            assertEquals(run.nodes, CampaignMap.generate(seed))
            assertEquals(3, run.node(1)!!.next.size)
            assertEquals(3, run.node(40)!!.next.size)
            assertEquals(1, run.nodes.count { it.depth == 7 && it.type == NodeType.BOSS })
            run.nodes.forEach { node ->
                assertEquals(3, node.roster.distinct().size)
                node.next.forEach { assertEquals(node.depth + 1, run.node(it)!!.depth) }
                if (node.depth > 1) assertTrue(run.nodes.any { node.index in it.next })
            }
            assertTrue(run.nodes.filter { it.depth == 2 }.all { n ->
                n.next.size == 1 && run.node(n.next.single())!!.lane == n.lane
            })
            assertTrue(run.nodes.filter { it.depth !in listOf(1, 4) }.all { it.next.size <= 1 })
            fun walk(id: Int, length: Int) {
                val node = run.node(id)!!
                if (node.next.isEmpty()) assertEquals(7, length)
                else node.next.forEach { walk(it, length + 1) }
            }
            walk(1, 1)
            shapes += run.nodes.map { it.next }
            rosters += run.nodes.map { it.roster }
        }
        assertEquals(1, shapes.size)
        assertTrue(rosters.size > 900)
    }

    @Test fun everyLegalPathUsesRealSelectVictoryRewardAndSettlesOnce() {
        val graph = RunState(seed = 42)
        fun paths(id: Int): List<List<Int>> = graph.node(id)!!.next.let { next ->
            if (next.isEmpty()) listOf(listOf(id)) else next.flatMap { paths(it).map { path -> listOf(id) + path } }
        }
        assertEquals(9, paths(1).size)
        paths(1).forEach { path ->
            val p = profile()
            path.forEach { id ->
                if (p.run.selectedNode == null) assertTrue(CampaignFlow.selectNextNode(p, p.run.id, id))
                val node = win(p)
                assertEquals(500, p.gold)
                assertTrue(p.run.availableNodes.isEmpty())
                val frozen = p.run
                assertFalse(CampaignFlow.pickReward(p, "old-run", id, "recover"))
                assertEquals(frozen, p.run)
                assertTrue(CampaignFlow.pickReward(p, p.run.id, id, "recover"))
                assertFalse(CampaignFlow.pickReward(p, p.run.id, id, "recover"))
                if (id != 70) assertEquals(500, p.gold)
            }
            assertEquals(path, p.run.path)
            assertEquals(RunPhase.WON, p.run.phase)
            assertEquals(7, p.totalWins)
            assertEquals(1, p.runsCleared)
            assertEquals(7, p.bestLayer)
            assertEquals(path.sumOf { graph.node(it)!!.rewardGold }, p.run.settledGold)
            assertEquals(500 + p.run.settledGold, p.gold)
            assertEquals(0, p.run.gold)
            assertFalse(CampaignFlow.loseRun(p, p.run.id))
        }
    }

    @Test fun choosingCannotJumpAcrossBranchesOrAbandonAnUnfinishedFight() {
        val p = profile()
        assertFalse(CampaignFlow.selectNextNode(p, p.run.id, 70))
        assertFalse(CampaignFlow.selectNextNode(p, "old", 1))
        advance(p, 1)
        assertEquals(3, p.run.availableNodes.size)
        assertTrue(CampaignFlow.selectNextNode(p, p.run.id, 20))
        assertFalse(CampaignFlow.selectNextNode(p, p.run.id, 21))
        val node = p.run.node(21)!!
        val b = p.run.newBattle(p, node)
        b.enemyTeam.forEach { it.hp = 0 }
        assertFalse(CampaignFlow.finishBattle(p, p.run.id, node, b))
        win(p)
        assertFalse(CampaignFlow.selectNextNode(p, p.run.id, 30))
    }

    @Test fun victoryCannotBeSubmittedTwiceOrBeforeEnemyDefeat() {
        val p = profile()
        val node = p.run.currentNode!!
        val b = p.run.newBattle(p, node)
        assertFalse(CampaignFlow.finishBattle(p, p.run.id, node, b))
        b.enemyTeam.forEach { it.hp = 0 }
        assertTrue(CampaignFlow.finishBattle(p, p.run.id, node, b))
        val frozen = p.run
        assertFalse(CampaignFlow.finishBattle(p, p.run.id, node, b))
        assertEquals(frozen, p.run)
        assertEquals(1, p.totalWins)
    }

    @Test fun branchChoiceStaysLockedThroughRewardsUntilTheMerge() {
        val p = profile()
        advance(p, 1)
        assertEquals(listOf(20, 21, 22), p.run.availableNodes.map { it.index })
        assertTrue(CampaignFlow.selectNextNode(p, p.run.id, 20))
        val second = win(p)
        assertTrue(CampaignFlow.pickReward(p, p.run.id, second.index, "recover"))
        assertEquals(listOf(30), p.run.availableNodes.map { it.index })
        assertFalse(CampaignFlow.selectNextNode(p, p.run.id, 31))
        assertFalse(CampaignFlow.selectNextNode(p, p.run.id, 32))
        assertTrue(CampaignFlow.selectNextNode(p, p.run.id, 30))
        val third = win(p)
        assertTrue(CampaignFlow.pickReward(p, p.run.id, third.index, "recover"))
        assertEquals(listOf(40), p.run.availableNodes.map { it.index })
        assertFalse(CampaignFlow.selectNextNode(p, p.run.id, 50))
        advance(p, 1)
        assertEquals(listOf(50, 51, 52), p.run.availableNodes.map { it.index })
    }

    @Test fun healthAndFaintedMembersCarryButEnergyAndStatusResetBetweenBattles() {
        val p = profile()
        val node = p.run.currentNode!!
        val b = p.run.newBattle(p, node)
        b.playerTeam[0].hp = 0
        b.playerTeam[1].hp = 12
        b.playerTeam[1].energy = 0
        b.playerTeam[1].status = StatusEffect.POISON
        b.enemyTeam.forEach { it.hp = 0 }
        assertTrue(CampaignFlow.finishBattle(p, p.run.id, node, b))
        val relic = p.run.rewardChoices.first { it.relicId != null }
        assertTrue(CampaignFlow.pickReward(p, p.run.id, node.index, relic.id))
        assertTrue(CampaignFlow.selectNextNode(p, p.run.id, 20))
        val next = p.run.newBattle(p, p.run.currentNode!!)
        assertEquals(0, next.playerTeam[0].hp)
        assertEquals(12, next.playerTeam[1].hp)
        assertEquals(1, next.playerActive)
        assertNull(next.player.status)
        assertEquals(next.player.maxEnergy, next.player.energy)
    }

    @Test fun recoveryRevivesOneMemberAndOtherwiseHealsTheTeam() {
        val p = profile()
        val node = win(p, 10)
        p.run = p.run.copy(hp = p.run.hp + (p.party[0] to 0))
        assertTrue(CampaignFlow.pickReward(p, p.run.id, node.index, "recover"))
        assertEquals(30, p.run.hp[p.party[0]])
        assertEquals(10, p.run.hp[p.party[1]])
        CampaignFlow.selectNextNode(p, p.run.id, 20)
        val second = win(p, 50)
        assertTrue(CampaignFlow.pickReward(p, p.run.id, second.index, "recover"))
        assertTrue(p.run.hp.values.all { it == 60 })
    }

    @Test fun fullRelicsRequirePlayerToNameTheReplacement() {
        val p = profile()
        p.run = p.run.copy(relics = Relic.entries.take(3).map { it.id })
        val node = win(p)
        val candidate = p.run.rewardChoices.first { it.relicId != null }
        val original = p.run
        assertFalse(CampaignFlow.pickReward(p, p.run.id, node.index, candidate.id))
        assertFalse(CampaignFlow.pickReward(p, p.run.id, node.index, candidate.id, "not-equipped"))
        assertEquals(original, p.run)
        val old = p.run.relics[1]
        assertTrue(CampaignFlow.pickReward(p, p.run.id, node.index, candidate.id, old))
        assertEquals(3, p.run.relics.size)
        assertEquals(listOf(original.relics[0], candidate.relicId, original.relics[2]), p.run.relics)
    }

    @Test fun optionalShopHasOwnBudgetAndDoesNotAdvancePath() {
        val p = profile()
        assertFalse(CampaignFlow.purchaseSupply(p, p.run.id, "potion"))
        advance(p, 4)
        assertTrue(p.run.shopAvailable)
        val path = p.run.path
        val before = p.run.gold
        assertTrue(CampaignFlow.purchaseSupply(p, p.run.id, "potion"))
        assertEquals(before - 40, p.run.gold)
        assertEquals(500, p.gold)
        assertEquals(path, p.run.path)
        assertEquals(3, p.run.availableNodes.size)
        assertFalse(CampaignFlow.purchaseSupply(p, "old", "potion"))
        p.run = p.run.copy(gold = 0)
        assertFalse(CampaignFlow.purchaseSupply(p, p.run.id, "potion"))
        assertTrue(CampaignFlow.selectNextNode(p, p.run.id, 50))
        assertFalse(CampaignFlow.purchaseSupply(p, p.run.id, "potion"))
    }

    @Test fun shopServicesMustHaveAnEffectBeforeCharging() {
        val p = profile()
        advance(p, 4)
        val wallet = p.run.gold
        p.run = p.run.copy(hp = p.party.associateWith { 60 })
        assertFalse(CampaignFlow.purchaseSupply(p, p.run.id, "rest"))
        assertFalse(CampaignFlow.purchaseSupply(p, p.run.id, "rescue"))
        assertEquals(wallet, p.run.gold)
        p.run = p.run.copy(hp = p.run.hp + (p.party[0] to 0) + (p.party[1] to 15))
        assertTrue(CampaignFlow.purchaseSupply(p, p.run.id, "rest"))
        assertEquals(0, p.run.hp[p.party[0]])
        assertEquals(35, p.run.hp[p.party[1]])
        assertTrue(CampaignFlow.purchaseSupply(p, p.run.id, "rescue"))
        assertEquals(30, p.run.hp[p.party[0]])
    }

    @Test fun ongoingRunLocksMetaProgressAndRestartUntilExplicitEnd() {
        val p = profile()
        val run = p.run
        val party = p.party.toList()
        assertFalse(CampaignFlow.recruitPartner(p, "partner:${CreatureId.PUFF}"))
        assertFalse(CampaignFlow.trainPartner(p, p.party.first(), 10))
        assertFalse(p.commitPreparation(party.reversed(), party.associateWith { p.loadoutOf(it).map(Move::name) }))
        p.startNewRun()
        assertEquals(run, p.run)
        advance(p, 1)
        val earned = p.run.gold
        assertTrue(CampaignFlow.loseRun(p, p.run.id))
        assertEquals(500 + earned, p.gold)
        assertFalse(CampaignFlow.loseRun(p, p.run.id))
        assertEquals(500 + earned, p.gold)
        assertEquals(RunPhase.LOST, p.run.phase)
        p.potions = 2
        val oldId = p.run.id
        p.startNewRun()
        assertNotEquals(oldId, p.run.id)
        assertEquals(RunPhase.READY, p.run.phase)
        assertTrue(p.run.hp.isEmpty())
        assertEquals(0, p.potions)
        assertTrue(CampaignFlow.trainPartner(p, p.party.first(), 10))
    }

    @Test fun battleSnapshotsRestoreAllTurnStateAndDeterministicNextTurn() {
        val p = profile()
        val node = p.run.currentNode!!
        val b = p.run.newBattle(p, node)
        b.playerTeam[0].hp = 0
        b.playerActive = 1
        b.enemyActive = 2
        b.player.hp = 17
        b.player.energy = 2
        b.player.status = StatusEffect.POISON
        b.enemy.hp = 6
        b.enemy.status = StatusEffect.PARALYZE
        val snapshot = SavedBattle.capture(b, 4, true, .75f)
        p.run = p.run.copy(battle = snapshot)
        val roundtrip = RunCodec.decode(RunCodec.encode(p.run))
        assertEquals(p.run, roundtrip)
        val restored = roundtrip.newBattle(p, node)
        assertEquals(snapshot, SavedBattle.capture(restored, 4, true, .75f))
        assertEquals(17, restored.player.hp)
        val seed = p.run.seed xor node.index * 7919 xor 4 * 104729
        val a = BattleEngine(b, kotlin.random.Random(seed), true)
        val c = BattleEngine(restored, kotlin.random.Random(seed), snapshot.enemyHasSwitched)
        assertEquals(a.planTurn(b.player.moves.first()), c.planTurn(restored.player.moves.first()))
        assertEquals(a.applyAttack(true, b.player.moves.first()), c.applyAttack(true, restored.player.moves.first()))
    }

    @Test fun emptyBattleSnapshotAcceptsAndroidXmlClosingIndentation() {
        val p = profile()
        val pending = win(p)
        val states = listOf(RunState(seed = 42), p.run)
        assertTrue(CampaignFlow.pickReward(p, p.run.id, pending.index, "recover"))
        (states + p.run).forEach { run ->
            // Android XML serializer appends the closing tag's indentation after a terminal newline.
            assertEquals(run, RunCodec.decode(RunCodec.encode(run) + "    "))
        }
    }

    @Test fun newRelicsOnlyHealOnPlayerHitAndOnlyBoostLowHpPlayer() {
        val state = BattleState(DemoData.newPlayerTeam(), DemoData.newPlayerTeam(),
            relics = setOf(Relic.ECHO_FANG.id, Relic.LAST_STAND.id))
        state.player.hp = 10
        val e = BattleEngine(state, kotlin.random.Random(0))
        val move = state.player.moves.first().copy(accuracy = 0)
        e.applyAttack(true, move)
        assertEquals(10, state.player.hp)
        e.applyAttack(true, move.copy(accuracy = 100))
        assertEquals(12, state.player.hp)
        val control = BattleState(DemoData.newPlayerTeam(), DemoData.newPlayerTeam())
        val expected = BattleEngine(control, kotlin.random.Random(1)).computeDamage(control.enemy, move, control.player)
        val enemy = BattleEngine(state, kotlin.random.Random(1)).computeDamage(state.enemy, move, state.player)
        assertEquals(expected, enemy)
    }
}
