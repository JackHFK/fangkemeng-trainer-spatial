package com.example.fangkemeng.model

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 仅替代系统KV，不替代迁移、序列化和游戏状态逻辑。 */
class SaveStoreTest {
    private val values = mutableMapOf<String, Any>()
    private val field = SaveStore::class.java.getDeclaredField("prefs").apply { isAccessible = true }
    private var previous: Any? = null

    @Before fun attachPreferences() {
        previous = field.get(null)
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "apply" -> null
                "commit" -> true
                "remove" -> { values.remove(args!![0] as String); proxy }
                "clear" -> { values.clear(); proxy }
                else -> { if (method.name.startsWith("put")) {
                    if (args!![1] == null) values.remove(args[0] as String) else values[args[0] as String] = args[1]
                }; proxy }
            }
        }
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "contains" -> values.containsKey(args!![0] as String)
                "getAll" -> values.toMap()
                else -> if (method.name.startsWith("get")) values[args!![0] as String] ?: args[1] else null
            }
        }
        field.set(null, prefs)
    }

    @After fun detachPreferences() { field.set(null, previous) }

    @Test fun legacyGoldMigrationPreservesProgressAndRoundTripsAsOneWallet() {
        values.putAll(mapOf("runGold" to 247, "runLayer" to 4, "runId" to "legacy-run",
            "potions" to 3, "revives" to 2, "runRelics" to Relic.LEAF_CREST.id))
        val id = DemoData.defaultParty.first()
        val custom = DemoData.movesFor(id).takeLast(3).map { it.name }
        values["loadouts"] = "${id.name.lowercase()}:${custom.joinToString(",")}"
        val loaded = SaveStore.load()
        assertEquals(247, loaded.gold)
        assertEquals(4, loaded.run.layer)
        assertEquals(3, loaded.run.node(loaded.run.pendingRewardNode!!)!!.depth)
        assertEquals("legacy-run", loaded.run.id)
        assertEquals(custom, loaded.loadouts[id])
        assertEquals(3, loaded.potions)
        assertEquals(2, loaded.revives)
        assertEquals(listOf(Relic.LEAF_CREST.id), loaded.run.relics)
        assertEquals(3, values["runSchema"])
        assertEquals(loaded.run, RunCodec.decode(values["runState"] as String))
        assertEquals(loaded, SaveStore.load())
        assertEquals(247, values["walletGold"])
        assertFalse(values.containsKey("runGold"))
        val again = SaveStore.load()
        assertEquals(loaded, again)
        assertTrue(CampaignFlow.loseRun(again, again.run.id))
        again.startNewRun()
        SaveStore.save(again)
        val freshRun = SaveStore.load()
        assertEquals(247, freshRun.gold)
        assertEquals(custom, freshRun.loadouts[id])
        assertEquals(0, freshRun.potions)
        assertEquals(0, freshRun.revives)
        assertTrue(freshRun.run.relics.isEmpty())
        assertNull(freshRun.run.pendingRewardNode)
    }

    @Test fun walletKeyWinsOverLegacyAndNewInstallReceivesInitialGoldOnlyOnce() {
        assertEquals(80, SaveStore.load().gold)
        values.putAll(mapOf("walletGold" to 390, "runGold" to 999))
        assertEquals(390, SaveStore.load().gold)
        val p = SaveStore.load()
        p.gold = 0
        p.startNewRun()
        SaveStore.save(p)
        assertEquals(0, SaveStore.load().gold)
        assertFalse(values.containsKey("runGold"))
    }

    @Test fun trainingRecruitmentAndOldLevelsRoundTripWithoutResettingProgress() {
        val p = PlayerProfile(gold = 1000)
        p.levels[CreatureId.SEED_CUB] = 100
        p.levels[CreatureId.WATER_TURTLE] = 15
        p.levels[CreatureId.BIRD] = 12
        p.exp[CreatureId.BIRD] = 470
        assertTrue(CampaignFlow.trainPartner(p, CreatureId.BIRD, 12))
        assertTrue(CampaignFlow.recruitPartner(p, "partner:${CreatureId.PUFF}"))
        SaveStore.save(p)
        val loaded = SaveStore.load()
        assertEquals(p, loaded)
        assertEquals(100, loaded.levelOf(CreatureId.SEED_CUB))
        assertEquals(13, loaded.levelOf(CreatureId.PUFF))
        loaded.startNewRun()
        SaveStore.save(loaded)
        val again = SaveStore.load()
        assertEquals(p.levels, again.levels)
        assertEquals(p.gold, again.gold)
        assertEquals(470, again.exp[CreatureId.BIRD])
        assertEquals(p.unlocked, again.unlocked)
    }

    @Test fun legacyEveryDepthAndBossRewardPreservePermanentFieldsWithoutReissuingGold() {
        val expected = mapOf(1 to 0, 2 to 1, 3 to 2, 4 to 3, 5 to 3, 6 to 4, 7 to 5, 8 to 6, 9 to 6, 10 to 7)
        expected.forEach { (layer, count) ->
            values.clear()
            values.putAll(mapOf("walletGold" to 350, "runLayer" to layer, "runId" to "legacy-run",
                "levels" to "seed_cub:100;water_turtle:15;bird:12", "exp" to "bird:470",
                "rewardRelic" to Relic.ECHO_FANG.id))
            val p = SaveStore.load()
            assertEquals(count, p.run.path.size)
            assertEquals(350, p.gold)
            assertEquals(0, p.run.gold)
            assertEquals(100, p.levelOf(CreatureId.SEED_CUB))
            assertEquals(470, p.exp[CreatureId.BIRD])
            p.run.path.zipWithNext().forEach { (a, b) -> assertTrue(b in p.run.node(a)!!.next) }
            SaveStore.save(p)
            assertEquals(p, SaveStore.load())
        }
        values.clear()
        values.putAll(mapOf("walletGold" to 350, "runLayer" to 10, "runId" to "boss-pending",
            "pendingRewardNode" to 9, "rewardRelic" to Relic.ECHO_FANG.id))
        val boss = SaveStore.load()
        assertEquals(RunPhase.RUNNING, boss.run.phase)
        assertEquals(70, boss.run.pendingRewardNode)
        assertTrue(boss.run.rewardChoices.any { it.relicId == Relic.ECHO_FANG.id })
        assertTrue(CampaignFlow.pickReward(boss, boss.run.id, 70, "recover"))
        assertEquals(RunPhase.WON, boss.run.phase)
        assertEquals(350, boss.gold)
    }

    @Test fun frozenGraphChoicesSnapshotAndEachPhaseRoundTripThroughPreferences() {
        val p = PlayerProfile(gold = 123)
        for (phase in RunPhase.entries) {
            p.run = RunState(seed = 67, phase = phase, selectedNode = 21, path = listOf(1), gold = 87,
                hp = p.party.associateWith { 17 }, relics = listOf(Relic.ECHO_FANG.id),
                pendingRewardNode = 1, rewardChoices = listOf(RewardChoice("recover", "休整", "全队回复 35% 生命")),
                battle = SavedBattle(List(3) { MonBattleState(10, 2, StatusEffect.POISON) },
                    List(3) { MonBattleState(7, 1, StatusEffect.PARALYZE) }, 1, 2, 4, true, .75f))
            SaveStore.save(p)
            assertEquals(p, SaveStore.load())
        }
    }

    @Test fun crossedGraphsMigrateOnceAndPreservePendingOrSelectedBattles() {
        val oldNodes = CampaignMap.generate(42).map { node ->
            if (node.depth == 2) node.copy(next = listOf(30, 31, 32)) else node
        }
        val snapshot = SavedBattle(List(3) { MonBattleState(17, 2, StatusEffect.POISON) },
            List(3) { MonBattleState(7, 1, StatusEffect.PARALYZE) }, 1, 2, 4, true, .75f)
        for ((path, selected) in listOf(listOf(1, 20) to null, listOf(1, 20) to 32, listOf(1, 20, 32) to null)) {
            val p = PlayerProfile(gold = 287, potions = 2, revives = 1,
                run = RunState(seed = 42, nodes = oldNodes, phase = RunPhase.RUNNING, path = path,
                    selectedNode = selected, gold = 65, hp = DemoData.defaultParty.associateWith { 17 },
                    battle = snapshot.takeIf { selected != null },
                    pendingRewardNode = path.last().takeIf { selected == null },
                    rewardChoices = listOf(RewardChoice("recover", "休整", "全队回复 35% 生命"))))
            SaveStore.save(p)
            values["runSchema"] = 2
            val loaded = SaveStore.load()
            assertEquals(p.copy(run = loaded.run), loaded)
            assertEquals(p.run.copy(nodes = loaded.run.nodes), loaded.run)
            assertEquals(listOf(if (selected == 32 || path.last() == 32) 32 else 30), loaded.run.node(20)!!.next)
            assertTrue(loaded.run.nodes.filter { it.depth == 2 }.all { it.next.size == 1 })
            assertEquals(p.run.nodes.map { it.copy(next = emptyList()) }, loaded.run.nodes.map { it.copy(next = emptyList()) })
            assertEquals(3, values["runSchema"])
            assertEquals(loaded, SaveStore.load())
            assertEquals(loaded.run, RunCodec.decode(values["runState"] as String))
        }
    }
}
