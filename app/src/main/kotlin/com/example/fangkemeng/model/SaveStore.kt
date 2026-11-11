package com.example.fangkemeng.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 永久进度 + 本轮 roguelike 状态的本地存档（SharedPreferences 字符串 KV）。 */
data class PlayerProfile(
    val party: MutableList<CreatureId> = DemoData.defaultParty.toMutableList(),
    val loadouts: MutableMap<CreatureId, List<String>> = mutableMapOf(),
    val unlocked: MutableSet<CreatureId> = DemoData.starters.toMutableSet(),
    val levels: MutableMap<CreatureId, Int> = DemoData.starters.associateWith { 10 }.toMutableMap(),
    val exp: MutableMap<CreatureId, Int> = DemoData.starters.associateWith { 0 }.toMutableMap(),
    var bestLayer: Int = 0,
    var runsCleared: Int = 0,
    var totalWins: Int = 0,
    /** 永久金币钱包，开新一轮不重置。 */
    var gold: Int = START_GOLD,
    var run: RunState = RunState(),
    /** 补给/商店获得的道具库存（跨战斗，本轮有效）。 */
    var potions: Int = 0,
    var revives: Int = 0,
) {
    fun levelOf(id: CreatureId): Int = levels[id] ?: 10

    val recruitmentLevel: Int
        get() = unlocked.map(::levelOf).sortedDescending().take(3).lastOrNull() ?: CreatureGrowth.BASE_LEVEL

    /** 在解锁前取第三高等级；招募不替玩家更改编队。 */
    fun acquirePartner(id: CreatureId): Boolean {
        if (isUnlocked(id)) return false
        levels[id] = recruitmentLevel
        unlocked += id
        return true
    }

    fun loadoutOf(id: CreatureId): List<Move> =
        DemoData.loadoutFor(id, loadouts[id] ?: emptyList())

    /** 整份草稿先验证再提交，拒绝部分写入和静默补默认招式。 */
    fun commitPreparation(newParty: List<CreatureId>, newLoadouts: Map<CreatureId, List<String>>): Boolean {
        if (run.active) return false
        val acceptedParty = newParty.toList()
        val acceptedLoadouts = newLoadouts.mapValues { it.value.toList() }
        if (PreparationDraft.validationIssue(this, acceptedParty, acceptedLoadouts) != null) return false
        party.clear()
        party.addAll(acceptedParty)
        loadouts.putAll(acceptedLoadouts)
        return true
    }

    fun isUnlocked(id: CreatureId): Boolean = id in unlocked

    /** 新一轮保留金币、伙伴、等级和配招，清空本轮道具与遗物。 */
    fun startNewRun() {
        if (run.active) return
        run = RunState()
        potions = 0
        revives = 0
    }

    companion object { const val START_GOLD = 80 }
}

/** 进程内共享的玩家状态：首页与 Stage 共用同一份，改动后立即落盘。 */
object PlayerState {
    var revision by mutableStateOf(0)
        private set
    var profile: PlayerProfile = try {
        SaveStore.load()
    } catch (_: Throwable) {
        PlayerProfile()
    }

    fun reload() {
        profile = SaveStore.load()
        revision++
    }

    fun persist() {
        SaveStore.save(profile)
        revision++
    }
}

object SaveStore {
    private const val FILE = "fangkemeng.save"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun load(): PlayerProfile {
        val p = prefs ?: return PlayerProfile()
        val profile = PlayerProfile(
            party = readCreatureList(p, "party", DemoData.defaultParty).toMutableList(),
            unlocked = readCreatureList(p, "unlocked", DemoData.starters).toMutableSet(),
            potions = p.getInt("potions", 0),
            revives = p.getInt("revives", 0),
            bestLayer = p.getInt("bestLayer", 0),
            runsCleared = p.getInt("runsCleared", 0),
            totalWins = p.getInt("totalWins", 0),
            gold = p.getInt("walletGold", p.getInt("runGold", PlayerProfile.START_GOLD)).coerceAtLeast(0),
        )
        // 配招
        p.getString("loadouts", null)?.split(';')?.forEach { entry ->
            val parts = entry.split(':')
            if (parts.size == 2) {
                val id = DemoData.availableSpecies().firstOrNull {
                    it.creatureId.name.equals(parts[0], ignoreCase = true)
                }?.creatureId
                if (id != null) profile.loadouts[id] = parts[1].split(',').filter { it.isNotBlank() }
            }
        }
        // 等级 / 经验
        p.getString("levels", null)?.split(';')?.forEach { entry ->
            val parts = entry.split(':')
            if (parts.size == 2) {
                DemoData.availableSpecies().firstOrNull {
                    it.creatureId.name.equals(parts[0], ignoreCase = true)
                }?.let { profile.levels[it.creatureId] = parts[1].toIntOrNull() ?: 10 }
            }
        }
        p.getString("exp", null)?.split(';')?.forEach { entry ->
            val parts = entry.split(':')
            if (parts.size == 2) {
                DemoData.availableSpecies().firstOrNull {
                    it.creatureId.name.equals(parts[0], ignoreCase = true)
                }?.let { profile.exp[it.creatureId] = parts[1].toIntOrNull() ?: 0 }
            }
        }
        val schema = p.getInt("runSchema", 0)
        profile.run = if (schema in 2..3) RunCodec.decode(requireNotNull(p.getString("runState", null)))
            else RunCodec.migrateLegacy(profile, p.getString("runId", null), p.getInt("runLayer", 1),
                p.getString("runRelics", "").orEmpty().split(',').filter { it.isNotBlank() },
                p.getInt("pendingRewardNode", 0).takeIf { it > 0 }, p.getString("rewardRelic", null))
        if (schema == 2) profile.run = RunCodec.migrateBranchRoutes(profile.run)
        if (schema != 3) save(profile)
        return profile
    }

    fun save(profile: PlayerProfile) {
        val p = prefs ?: return
        p.edit().apply {
            putString("party", profile.party.joinToString(",") { it.name.lowercase() })
            putString("unlocked", profile.unlocked.joinToString(",") { it.name.lowercase() })
            putString(
                "loadouts",
                profile.loadouts.entries.joinToString(";") { (id, names) ->
                    "${id.name.lowercase()}:${names.joinToString(",")}"
                },
            )
            putString(
                "levels",
                profile.levels.entries.joinToString(";") { (id, lv) -> "${id.name.lowercase()}:$lv" },
            )
            putString(
                "exp",
                profile.exp.entries.joinToString(";") { (id, e) -> "${id.name.lowercase()}:$e" },
            )
            putInt("potions", profile.potions)
            putInt("revives", profile.revives)
            putInt("bestLayer", profile.bestLayer)
            putInt("runsCleared", profile.runsCleared)
            putInt("totalWins", profile.totalWins)
            putInt("walletGold", profile.gold)
            remove("runGold")
            putInt("runSchema", 3)
            putString("runState", RunCodec.encode(profile.run))
            listOf("runActive", "runLayer", "runRelics", "runId", "pendingRewardNode", "rewardRelic").forEach(::remove)
        }.apply()
    }

    private fun readCreatureList(
        p: SharedPreferences,
        key: String,
        fallback: List<CreatureId>,
    ): List<CreatureId> {
        val raw = p.getString(key, null) ?: return fallback
        val ids = raw.split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { name -> CreatureId.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        return ids.ifEmpty { fallback }
    }
}
