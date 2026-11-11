package com.example.fangkemeng.model

object CampaignFlow {
    fun beginRun(profile: PlayerProfile): Boolean {
        if (profile.run.phase != RunPhase.READY ||
            PreparationDraft.validationIssue(profile, profile.party, profile.party.associateWith { profile.loadoutOf(it).map(Move::name) }) != null) return false
        profile.run = profile.run.copy(phase = RunPhase.RUNNING, selectedNode = 1,
            hp = profile.party.associateWith { CreatureGrowth.maxHp(profile.levelOf(it)) })
        return true
    }

    fun selectNextNode(profile: PlayerProfile, runId: String, nodeId: Int): Boolean {
        val run = profile.run
        if (run.id != runId || !run.active || run.selectedNode != null ||
            run.availableNodes.none { it.index == nodeId }) return false
        profile.run = run.copy(selectedNode = nodeId)
        return true
    }

    fun finishBattle(profile: PlayerProfile, runId: String, node: MapNode, battle: BattleState): Boolean {
        val run = profile.run
        if (run.id != runId || !run.active || run.selectedNode != node.index ||
            run.node(node.index) != node || run.pendingRewardNode != null || !battle.enemyWiped || battle.playerWiped) return false
        val next = run.copy(path = run.path + node.index, selectedNode = null, battle = null,
            hp = battle.playerTeam.associate { it.species.creatureId to it.hp },
            gold = run.gold + node.rewardGold, pendingRewardNode = node.index)
        profile.run = next.copy(rewardChoices = CampaignMap.rewards(next, node.index))
        profile.totalWins++
        profile.bestLayer = maxOf(profile.bestLayer, node.depth)
        if (node.depth >= 3) profile.acquirePartner(CreatureId.LION)
        if (node.depth >= 6) profile.acquirePartner(CreatureId.DRAGON)
        if (node.type == NodeType.BOSS) {
            profile.acquirePartner(CreatureId.CAT)
            profile.acquirePartner(CreatureId.FISH)
        }
        return true
    }

    fun pickReward(profile: PlayerProfile, runId: String, rewardNode: Int, choiceId: String, replace: String? = null): Boolean {
        val run = profile.run
        if (run.id != runId || !run.active || run.pendingRewardNode != rewardNode) return false
        val choice = run.rewardChoices.firstOrNull { it.id == choiceId } ?: return false
        if (choice.relicId != null) {
            if (choice.relicId in run.relics) return false
            val relics = if (run.relics.size == Relic.MAX_EQUIPPED) {
                if (replace !in run.relics) return false
                run.relics.map { if (it == replace) choice.relicId else it }
            } else run.relics + choice.relicId
            profile.run = run.copy(relics = relics)
        } else {
            val down = profile.party.firstOrNull { run.hp[it] == 0 }
            profile.run = run.copy(hp = if (down != null) run.hp + (down to CreatureGrowth.maxHp(profile.levelOf(down)) / 2)
                else healTeam(profile, .35f, 0))
        }
        profile.run = profile.run.copy(pendingRewardNode = null, rewardChoices = emptyList())
        if (profile.run.path.size == CampaignMap.DEPTHS) settle(profile, RunPhase.WON)
        return true
    }

    fun loseRun(profile: PlayerProfile, runId: String, battle: BattleState? = null): Boolean {
        if (profile.run.id != runId || !profile.run.active) return false
        val savedHp = battle?.playerTeam?.associate { it.species.creatureId to it.hp }
            ?: profile.run.battle?.player?.let { profile.party.zip(it.map(MonBattleState::hp)).toMap() }
        if (savedHp != null) profile.run = profile.run.copy(hp = savedHp)
        settle(profile, RunPhase.LOST)
        return true
    }

    private fun settle(profile: PlayerProfile, phase: RunPhase) {
        profile.gold += profile.run.gold
        if (phase == RunPhase.WON) profile.runsCleared++
        profile.run = profile.run.copy(phase = phase, gold = 0, settledGold = profile.run.gold,
            selectedNode = null, battle = null, pendingRewardNode = null, rewardChoices = emptyList())
    }

    fun recruitPartner(profile: PlayerProfile, sku: String): Boolean {
        if (profile.run.active) return false
        val entry = CampaignMap.partnerStock(profile).firstOrNull { it.id == sku } ?: return false
        if (!entry.available || profile.gold < entry.price) return false
        profile.acquirePartner(CreatureId.valueOf(sku.substringAfter(':')))
        profile.gold -= entry.price
        return true
    }

    fun trainPartner(profile: PlayerProfile, id: CreatureId, quotedLevel: Int): Boolean {
        if (profile.run.active || !profile.isUnlocked(id) || profile.levelOf(id) != quotedLevel) return false
        val price = CreatureGrowth.trainingCost(quotedLevel) ?: return false
        if (profile.gold < price) return false
        profile.gold -= price
        profile.levels[id] = quotedLevel + 1
        return true
    }

    /** 补给不消耗战斗层，只在中部合流后的战间开放。 */
    fun purchaseSupply(profile: PlayerProfile, runId: String, sku: String): Boolean {
        if (profile.run.id != runId || !profile.run.shopAvailable) return false
        val entry = CampaignMap.supplyStock(profile).firstOrNull { it.id == sku } ?: return false
        if (!entry.available || profile.run.gold < entry.price) return false
        when (sku) {
            "potion" -> profile.potions++
            "revive" -> profile.revives++
            "rest" -> profile.run = profile.run.copy(hp = healTeam(profile, 0f, 20))
            "rescue" -> {
                val down = profile.party.first { profile.run.hp[it] == 0 }
                profile.run = profile.run.copy(hp = profile.run.hp + (down to CreatureGrowth.maxHp(profile.levelOf(down)) / 2))
            }
            else -> profile.run = profile.run.withRelic(sku.substringAfter(':'))
        }
        profile.run = profile.run.copy(gold = profile.run.gold - entry.price)
        return true
    }

    private fun healTeam(profile: PlayerProfile, fraction: Float, amount: Int): Map<CreatureId, Int> =
        profile.run.hp.mapValues { (id, hp) ->
            val max = CreatureGrowth.maxHp(profile.levelOf(id))
            if (hp == 0) 0 else (hp + (max * fraction).toInt() + amount).coerceAtMost(max)
        }
}
