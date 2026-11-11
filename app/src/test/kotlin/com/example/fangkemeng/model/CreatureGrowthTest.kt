package com.example.fangkemeng.model

import org.junit.Assert.*
import org.junit.Test

class CreatureGrowthTest {
    @Test fun trainingHasFiniteCostAndStatBudget() {
        assertEquals(760, (10 until 20).sumOf { CreatureGrowth.trainingCost(it)!! })
        assertEquals(60, CreatureGrowth.maxHp(10))
        assertEquals(69, CreatureGrowth.maxHp(20))
        assertEquals(15, CreatureGrowth.speed(10))
        assertEquals(17, CreatureGrowth.speed(20))
        assertEquals(1.2f, CreatureGrowth.damageMultiplier(20), .0001f)
        for (level in listOf(21, 100, Int.MAX_VALUE)) {
            assertEquals(CreatureGrowth.maxHp(20), CreatureGrowth.maxHp(level))
            assertEquals(CreatureGrowth.speed(20), CreatureGrowth.speed(level))
            assertEquals(CreatureGrowth.damageMultiplier(20), CreatureGrowth.damageMultiplier(level))
            assertNull(CreatureGrowth.trainingCost(level))
        }
    }

    @Test fun recruitUsesThirdHighestOwnedIncludingBenchAndTies() {
        val p = PlayerProfile(gold = 1000)
        p.levels.putAll(mapOf(CreatureId.SEED_CUB to 20, CreatureId.WATER_TURTLE to 17, CreatureId.BIRD to 14))
        p.acquirePartner(CreatureId.DRAGON)
        p.levels[CreatureId.DRAGON] = 18
        assertEquals(17, p.recruitmentLevel)
        val party = p.party.toList()
        assertTrue(CampaignFlow.recruitPartner(p, "partner:${CreatureId.PUFF}"))
        assertEquals(17, p.levelOf(CreatureId.PUFF))
        assertEquals(party, p.party)
        assertFalse(CampaignFlow.recruitPartner(p, "partner:${CreatureId.PUFF}"))
        assertEquals(880, p.gold)
        p.levels[CreatureId.WATER_TURTLE] = 18
        assertEquals(18, p.recruitmentLevel)
        assertTrue(p.acquirePartner(CreatureId.CAT))
        assertEquals(18, p.levelOf(CreatureId.CAT))
    }

    @Test fun trainingRejectsStaleQuoteUnaffordableLockedAndMaxLevel() {
        val p = PlayerProfile(gold = 40)
        assertFalse(CampaignFlow.trainPartner(p, CreatureId.PUFF, 10))
        assertFalse(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, 11))
        assertTrue(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, 10))
        assertEquals(0, p.gold)
        assertEquals(11, p.levelOf(CreatureId.SEED_CUB))
        assertFalse(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, 10))
        assertFalse(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, 11))
        p.gold = 720
        for (level in 11 until 20) assertTrue(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, level))
        assertEquals(0, p.gold)
        p.gold = 999
        assertFalse(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, 20))
        p.levels[CreatureId.SEED_CUB] = 100
        assertFalse(CampaignFlow.trainPartner(p, CreatureId.SEED_CUB, 100))
        assertEquals(100, p.levelOf(CreatureId.SEED_CUB))
        assertEquals(999, p.gold)
    }

    @Test fun victoryDoesNotAutoLevelAndMilestoneUsesRecruitmentLevel() {
        val p = PlayerProfile(gold = 0)
        p.levels.putAll(mapOf(CreatureId.SEED_CUB to 20, CreatureId.WATER_TURTLE to 18, CreatureId.BIRD to 15))
        p.exp[CreatureId.SEED_CUB] = 9999
        assertTrue(CampaignFlow.beginRun(p))
        repeat(3) {
            if (p.run.selectedNode == null) assertTrue(CampaignFlow.selectNextNode(p, p.run.id, p.run.availableNodes.first().index))
            val node = p.run.currentNode!!
            val b = p.run.newBattle(p, node)
            b.enemyTeam.forEach { it.hp = 0 }
            assertTrue(CampaignFlow.finishBattle(p, p.run.id, node, b))
            assertTrue(CampaignFlow.pickReward(p, p.run.id, node.index, "recover"))
        }
        assertEquals(15, p.levelOf(CreatureId.LION))
        assertEquals(20, p.levelOf(CreatureId.SEED_CUB))
        assertEquals(9999, p.exp[CreatureId.SEED_CUB])
    }
}
