package com.example.fangkemeng.model

/** 内部字段无分隔符；保存实体图而非重新运行生成器。 */
object RunCodec {
    fun encode(run: RunState): String = listOf(
        run.id, run.seed.toString(), run.phase.name, run.selectedNode?.toString().orEmpty(),
        run.path.joinToString(","), run.gold.toString(), run.settledGold.toString(),
        run.hp.entries.joinToString(";") { "${it.key.name},${it.value}" }, run.relics.joinToString(","),
        run.pendingRewardNode?.toString().orEmpty(),
        run.rewardChoices.joinToString(";") { listOf(it.id, it.name, it.description, it.relicId.orEmpty()).joinToString("|") },
        run.nodes.joinToString(";") { node -> listOf(node.index, node.type.name, node.title, node.enemyLevel, node.rewardGold,
            node.roster.joinToString(",") { it.name }, node.depth, node.lane, node.next.joinToString(","), node.arena, node.hpScale).joinToString("|") },
        run.battle?.let { battle ->
            fun team(values: List<MonBattleState>) = values.joinToString(";") { "${it.hp},${it.energy},${it.status?.name.orEmpty()}" }
            listOf(team(battle.player), team(battle.enemy), battle.playerActive, battle.enemyActive,
                battle.turn, battle.enemyHasSwitched, battle.companionEnergy).joinToString("|")
        }.orEmpty(),
    ).joinToString("\n")

    fun decode(raw: String): RunState {
        val p = raw.split('\n')
        fun ints(raw: String) = raw.split(',').filter(String::isNotEmpty).map(String::toInt)
        val nodes = p[11].split(';').map { row ->
            val n = row.split('|')
            MapNode(n[0].toInt(), NodeType.valueOf(n[1]), n[2], n[3].toInt(), n[4].toInt(),
                n[5].split(',').map(CreatureId::valueOf), n[6].toInt(), n[7].toInt(), ints(n[8]), n[9].toInt(), n[10].toFloat())
        }
        val saved = p[12].takeIf(String::isNotBlank)?.split('|')?.let { b ->
            fun team(raw: String) = raw.split(';').map { row ->
                val m = row.split(',')
                MonBattleState(m[0].toInt(), m[1].toInt(), m[2].takeIf(String::isNotEmpty)?.let(StatusEffect::valueOf))
            }
            SavedBattle(team(b[0]), team(b[1]), b[2].toInt(), b[3].toInt(), b[4].toInt(), b[5].toBoolean(), b[6].toFloat())
        }
        return RunState(seed = p[1].toInt(), nodes = nodes, phase = RunPhase.valueOf(p[2]), selectedNode = p[3].toIntOrNull(),
            path = ints(p[4]), gold = p[5].toInt(), settledGold = p[6].toInt(),
            hp = p[7].split(';').filter(String::isNotEmpty).associate { it.split(',').let { v -> CreatureId.valueOf(v[0]) to v[1].toInt() } },
            relics = p[8].split(',').filter(String::isNotEmpty), id = p[0], pendingRewardNode = p[9].toIntOrNull(),
            rewardChoices = p[10].split(';').filter(String::isNotEmpty).map { row ->
                row.split('|').let { RewardChoice(it[0], it[1], it[2], it[3].takeIf(String::isNotEmpty)) }
            }, battle = saved)
    }

    /** 旧交叉图收为单路，已走或已确认的下一战保持原节点。 */
    fun migrateBranchRoutes(run: RunState): RunState {
        val chosen = (run.path + listOfNotNull(run.selectedNode)).zipWithNext().toMap()
        return run.copy(nodes = run.nodes.map { node ->
            if (node.depth == 2) node.copy(next = listOf(chosen[node.index] ?: (31 + node.lane)))
            else node
        })
    }

    /** 旧奖励已经进永久钱包，迁移只映射路径，不重复发金币。 */
    fun migrateLegacy(profile: PlayerProfile, id: String?, oldLayer: Int, relics: List<String>,
        pending: Int?, oldRelic: String?): RunState {
        if (id == null) return RunState()
        val seed = id.hashCode()
        var run = RunState(seed = seed, id = id, phase = RunPhase.RUNNING, relics = relics,
            hp = profile.party.associateWith { CreatureGrowth.maxHp(profile.levelOf(it)) })
        fun depth(index: Int) = when (index) { 1, 2, 3 -> index; 4, 5 -> 4; 6 -> 5; 7 -> 6; else -> 7 }
        val completed = when {
            oldLayer == 4 -> 3
            oldLayer >= 10 -> 7
            else -> depth(oldLayer) - 1
        }
        val path = mutableListOf<Int>()
        var next = 1
        repeat(completed) {
            path += next
            val children = run.node(next)?.next.orEmpty()
            next = children.firstOrNull { run.node(it)?.lane == 0 } ?: children.firstOrNull() ?: 0
        }
        val pendingDepth = if (oldLayer == 4) 3 else pending?.let(::depth)
        if (pendingDepth != null && path.size < pendingDepth) { path += next; next = run.node(next)?.next?.firstOrNull() ?: 0 }
        run = run.copy(path = path)
        val rewardNode = if (pendingDepth != null) path.getOrNull(pendingDepth - 1) else null
        if (rewardNode != null) {
            val choices = CampaignMap.rewards(run, rewardNode).toMutableList()
            oldRelic?.let(Relic::of)?.takeIf { it.id !in relics }?.let { relic ->
                choices[1] = RewardChoice("relic:${relic.id}", relic.displayName, relic.description, relic.id)
                if (choices[2].id == choices[1].id) choices[2] = Relic.entries.first { it.id !in relics && it.id != relic.id }
                    .let { RewardChoice("relic:${it.id}", it.displayName, it.description, it.id) }
            }
            run = run.copy(pendingRewardNode = rewardNode, rewardChoices = choices)
        } else if (path.size == CampaignMap.DEPTHS) run = run.copy(phase = RunPhase.WON)
        return run
    }
}
