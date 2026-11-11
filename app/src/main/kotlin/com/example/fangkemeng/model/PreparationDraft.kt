package com.example.fangkemeng.model

/** 离开编辑器前只改草稿；玩家显式取消招式后才腾出名额。 */
class PreparationDraft(private val profile: PlayerProfile) {
    val party = profile.party.toMutableList()
    val loadouts = DemoData.availableSpecies().filter { profile.isUnlocked(it.creatureId) }
        .associate { it.creatureId to profile.loadoutOf(it.creatureId).map { move -> move.name } }.toMutableMap()

    fun moves(id: CreatureId): List<String> = loadouts[id].orEmpty()

    fun toggleMove(id: CreatureId, name: String): Boolean {
        if (!profile.isUnlocked(id) || DemoData.movesFor(id).none { it.name == name }) return false
        val current = moves(id)
        loadouts[id] = when {
            name in current -> current - name
            current.size < MOVE_COUNT -> current + name
            else -> return false
        }
        return true
    }

    fun togglePartner(id: CreatureId): Boolean {
        if (!profile.isUnlocked(id)) return false
        when {
            id in party && party.size > 1 -> party.remove(id)
            id !in party && party.size < 3 -> party.add(id)
            else -> return false
        }
        return true
    }

    val issue: String? get() = validationIssue(profile, party, loadouts)

    companion object {
        const val MOVE_COUNT = 3

        fun validMoves(id: CreatureId, names: List<String>): Boolean =
            names.size == MOVE_COUNT && names.distinct().size == MOVE_COUNT &&
                names.all { name -> DemoData.movesFor(id).any { it.name == name } }

        fun validationIssue(profile: PlayerProfile, party: List<CreatureId>, loadouts: Map<CreatureId, List<String>>): String? {
            if (party.size !in 1..3 || party.distinct().size != party.size || party.any { !profile.isUnlocked(it) })
                return "请选择 1–3 位伙伴"
            val invalid = (party + loadouts.keys).distinct().firstOrNull { id ->
                !profile.isUnlocked(id) || !validMoves(id, loadouts[id] ?: profile.loadoutOf(id).map { it.name })
            }
            return invalid?.let { "${DemoData.speciesOf(it.name)?.name ?: it.name}：需选满 3 招" }
        }
    }
}
