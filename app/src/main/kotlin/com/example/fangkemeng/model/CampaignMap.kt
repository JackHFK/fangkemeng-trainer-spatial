package com.example.fangkemeng.model

import kotlin.random.Random

enum class NodeType(val displayName: String) { BATTLE("战斗"), ELITE("精英"), BOSS("首领") }
enum class RunPhase { READY, RUNNING, WON, LOST }

/** 每局冻结的节点与出边；编号是身份，depth 才是战斗层数。 */
data class MapNode(
    val index: Int,
    val type: NodeType,
    val title: String,
    val enemyLevel: Int = 0,
    val rewardGold: Int = 0,
    val roster: List<CreatureId> = emptyList(),
    val depth: Int = 1,
    val lane: Int = 0,
    val next: List<Int> = emptyList(),
    val arena: Int = 1,
    val hpScale: Float = .65f,
) {
    val ordinal: Int get() = depth
    val subtitle: String get() = "Lv.$enemyLevel · " +
        roster.mapNotNull { DemoData.speciesOf(it.name)?.element?.displayName }.distinct().joinToString(" / ") +
        if (type != NodeType.BATTLE) " · 生命 +20%" else ""
}

data class RewardChoice(val id: String, val name: String, val description: String, val relicId: String? = null)

/** 本局路径、预算和生命只有一个权威来源。 */
data class RunState(
    val seed: Int = Random.nextInt(),
    val nodes: List<MapNode> = CampaignMap.generate(seed),
    val phase: RunPhase = RunPhase.READY,
    val selectedNode: Int? = null,
    val path: List<Int> = emptyList(),
    val gold: Int = 0,
    val hp: Map<CreatureId, Int> = emptyMap(),
    val relics: List<String> = emptyList(),
    val id: String = java.util.UUID.randomUUID().toString(),
    val pendingRewardNode: Int? = null,
    val rewardChoices: List<RewardChoice> = emptyList(),
    val battle: SavedBattle? = null,
    val settledGold: Int = 0,
) {
    val relicSet: Set<String> get() = relics.toSet()
    val active: Boolean get() = phase == RunPhase.RUNNING
    val completed: Boolean get() = phase == RunPhase.WON || phase == RunPhase.LOST
    val completedCount: Int get() = path.size
    val layer: Int get() = (path.size + 1).coerceAtMost(CampaignMap.DEPTHS)
    fun node(index: Int): MapNode? = nodes.firstOrNull { it.index == index }
    val currentNode: MapNode? get() = selectedNode?.let(::node)
    val availableNodes: List<MapNode> get() = when {
        !active || pendingRewardNode != null -> emptyList()
        selectedNode != null -> listOfNotNull(currentNode)
        path.isEmpty() -> nodes.filter { it.depth == 1 }
        else -> node(path.last())?.next.orEmpty().mapNotNull(::node)
    }
    val shopAvailable: Boolean get() = active && path.lastOrNull() == 40 &&
        selectedNode == null && pendingRewardNode == null

    fun withRelic(id: String): RunState =
        if (id in relics || relics.size >= Relic.MAX_EQUIPPED) this else copy(relics = relics + id)
}

data class ShopEntry(
    val id: String, val name: String, val description: String, val price: Int,
    val available: Boolean = true, val unavailableReason: String = "",
)

object CampaignMap {
    const val DEPTHS = 7

    fun generate(seed: Int): List<MapNode> {
        val rng = Random(seed)
        val widths = listOf(1, 3, 3, 1, 3, 1, 1)
        return widths.flatMapIndexed { row, width ->
            val depth = row + 1
            (0 until width).map { lane ->
                val id = if (depth == 1) 1 else depth * 10 + lane
                val type = when {
                    depth == 7 -> NodeType.BOSS
                    depth == 4 || depth == 6 || (width == 3 && lane == (seed ushr depth).mod(3)) -> NodeType.ELITE
                    else -> NodeType.BATTLE
                }
                val next = when (depth) {
                    1 -> listOf(20, 21, 22)
                    2 -> listOf(30 + lane)
                    3 -> listOf(40)
                    4 -> listOf(50, 51, 52)
                    5 -> listOf(60)
                    6 -> listOf(70)
                    else -> emptyList()
                }
                val pool = DemoData.availableSpecies().filter {
                    depth >= 4 || it.creatureId !in listOf(CreatureId.DRAGON, CreatureId.GOLEM, CreatureId.TIDE_TURTLE, CreatureId.VINE_CUB)
                }
                val roster = if (depth == 1) listOf(CreatureId.BIRD, CreatureId.FISH, CreatureId.WATER_TURTLE)
                    else pool.shuffled(rng).take(3).map { it.creatureId }
                val arena = if (depth == 7) 9 else if (depth == 1) 1 else when (DemoData.speciesOf(roster.first().name)?.element) {
                    Element.GRASS -> 2; Element.FIRE -> 5; Element.WATER -> 3
                    Element.ELECTRIC -> 6; else -> 7
                }
                MapNode(id, type, when (type) {
                    NodeType.BOSS -> "训练营首领"; NodeType.ELITE -> "精英守关"; else -> listOf("林间遭遇", "岩脊对峙", "深林伏击").random(rng)
                }, 8 + depth, if (type == NodeType.BOSS) 100 else if (type == NodeType.ELITE) 65 else 40,
                    roster, depth, if (width == 1) 0 else lane - 1, next, arena,
                    when (depth) { 1, 2, 3 -> .65f; 4, 5 -> .7f; 6 -> .8f; else -> .9f })
            }
        }
    }

    fun rewards(run: RunState, nodeId: Int): List<RewardChoice> {
        val down = run.hp.values.any { it <= 0 }
        val recovery = if (down) RewardChoice("recover", "复苏", "复活一位伙伴，回复 50% 生命")
            else RewardChoice("recover", "休整", "全队回复 35% 生命")
        return listOf(recovery) + Relic.entries.filter { it.id !in run.relics }
            .shuffled(Random(run.seed xor nodeId)).take(2)
            .map { RewardChoice("relic:${it.id}", it.displayName, it.description, it.id) }
    }

    fun enemyTeam(node: MapNode): MutableList<Fangkemeng> = node.roster.map { id ->
        val mon = DemoData.buildFangkemeng(requireNotNull(DemoData.speciesOf(id.name)), node.enemyLevel)
        (mon.maxHp * node.hpScale * if (node.type != NodeType.BATTLE) 1.2f else 1f).toInt()
            .let { mon.copy(maxHp = it, hp = it) }
    }.toMutableList()

    fun partnerStock(profile: PlayerProfile): List<ShopEntry> = DemoData.availableSpecies().map { species ->
        val owned = profile.isUnlocked(species.creatureId)
        ShopEntry("partner:${species.creatureId.name}", species.name,
            "${species.element.displayName} · Lv.${if (owned) profile.levelOf(species.creatureId) else profile.recruitmentLevel}",
            PRICE_CREATURE, !owned, if (owned) "已拥有" else "")
    }

    fun supplyStock(profile: PlayerProfile): List<ShopEntry> {
        val run = profile.run
        val damaged = profile.party.any { (run.hp[it] ?: 0) in 1 until CreatureGrowth.maxHp(profile.levelOf(it)) }
        val down = profile.party.any { run.hp[it] == 0 }
        return listOf(
            ShopEntry("potion", "伤药 ×1", "回复 35 生命", PRICE_POTION),
            ShopEntry("revive", "复活符 ×1", "复活伙伴，回复 50% 生命", PRICE_REVIVE),
            ShopEntry("rest", "休整", "全队回复 20 生命", 50, damaged, if (damaged) "" else "无需恢复"),
            ShopEntry("rescue", "复苏", "复活一位伙伴，回复 50% 生命", 80, down, if (down) "" else "无需复活"),
        ) + Relic.entries.map { relic ->
            val reason = when {
                relic.id in run.relics -> "已携带"
                run.relics.size >= Relic.MAX_EQUIPPED -> "已满"
                else -> ""
            }
            ShopEntry("relic:${relic.id}", relic.displayName, relic.description, PRICE_RELIC, reason.isEmpty(), reason)
        }
    }

    const val PRICE_POTION = 40
    const val PRICE_RELIC = 90
    const val PRICE_CREATURE = 120
    const val PRICE_REVIVE = 150
}
