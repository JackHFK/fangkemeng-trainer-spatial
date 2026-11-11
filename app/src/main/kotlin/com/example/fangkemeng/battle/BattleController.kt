package com.example.fangkemeng.battle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.fangkemeng.input.GestureChargeTracker
import com.example.fangkemeng.input.HandGestureDetector
import com.example.fangkemeng.input.HandPose
import com.example.fangkemeng.input.HandSide
import com.example.fangkemeng.model.BattleEngine
import com.example.fangkemeng.model.BattleSessionConfig
import com.example.fangkemeng.model.BattleState
import com.example.fangkemeng.model.CampaignMap
import com.example.fangkemeng.model.CampaignFlow
import com.example.fangkemeng.model.CreatureId
import com.example.fangkemeng.model.DemoData
import com.example.fangkemeng.model.Element
import com.example.fangkemeng.model.Item
import com.example.fangkemeng.model.ItemKind
import com.example.fangkemeng.model.MapNode
import com.example.fangkemeng.model.Move
import com.example.fangkemeng.model.PlayerState
import com.example.fangkemeng.model.Fangkemeng
import com.example.fangkemeng.model.Relic
import com.example.fangkemeng.model.StatusEffect
import com.example.fangkemeng.model.SavedBattle
import com.example.fangkemeng.model.newBattle
import kotlin.random.Random
import com.pico.spatial.core.math.Vector3

/** 顶层游戏阶段。 */
enum class GamePhase { ARENA_IDLE, BATTLE_SETUP, BATTLE_TURN, RESULT }

/** 出招触发来源。 */
enum class MoveSource { BUTTON, VOICE, GESTURE }

/** 队伍槽位视图数据（供 HUD 画 3 个队伍点与换人列表）。 */
data class PartySlotView(
    val index: Int,
    val name: String,
    val element: Element,
    val hp: Int,
    val maxHp: Int,
    val level: Int,
    val fainted: Boolean,
    val active: Boolean,
)

/**
 * 战斗编排器：连接场景（[BattleScene]）、回合引擎（[BattleEngine]）、手势（[HandGestureDetector]）。
 *
 * UI 面向的状态用 Compose `mutableStateOf` 暴露，update/手势回调里改这些字段会触发面板重组。
 * 所有动作都提供方法入口：手势触发与面板按钮（手柄射线）共用同一套逻辑。
 */
class BattleController {

    private var scene: BattleScene? = null
    private var engine: BattleEngine? = null
    var battle: BattleState? = null
        private set

    val gesture = HandGestureDetector()
    val charge = GestureChargeTracker()

    var phase by mutableStateOf(GamePhase.ARENA_IDLE)
        private set

    var rightMenuVisible by mutableStateOf(false)
    var leftMenuVisible by mutableStateOf(false)
    var switchMenuVisible by mutableStateOf(false)
        private set
    var exitConfirmationVisible by mutableStateOf(false)
        private set
    /** 当前出战精灵已倒下，必须先换人才能继续。 */
    var forceSwitch by mutableStateOf(false)
        private set

    // ─── HUD 状态（读战斗状态派生）───
    var playerHp by mutableStateOf(0)
        private set
    var playerMaxHp by mutableStateOf(1)
        private set
    var enemyHp by mutableStateOf(0)
        private set
    var enemyMaxHp by mutableStateOf(1)
        private set
    var playerName by mutableStateOf("")
        private set
    var enemyName by mutableStateOf("")
        private set
    var playerLevel by mutableStateOf(10)
        private set
    var enemyLevel by mutableStateOf(9)
        private set
    var playerElement by mutableStateOf(Element.GRASS)
        private set
    var enemyElement by mutableStateOf(Element.WATER)
        private set
    var playerEnergy by mutableStateOf(0)
        private set
    var playerMaxEnergy by mutableStateOf(Fangkemeng.MAX_ENERGY)
    var playerStatus by mutableStateOf<StatusEffect?>(null)
        private set
    var enemyStatus by mutableStateOf<StatusEffect?>(null)
        private set
    var playerParty by mutableStateOf<List<PartySlotView>>(emptyList())
        private set
    var enemyParty by mutableStateOf<List<PartySlotView>>(emptyList())
        private set
    var playerActiveIndex by mutableStateOf(0)
        private set

    // ─── 结算 ───
    var resultPlayerWon by mutableStateOf(false)
        private set
    var rewardGold by mutableStateOf(0)
        private set
    var unlockedText by mutableStateOf("")
        private set
    var runCleared by mutableStateOf(false)
        private set

    val battleLog = mutableStateListOf<String>()

    val items: MutableList<Item> = mutableListOf()

    /** 永久钱包与本轮遗物，分别与 PlayerState.profile 同步。 */
    var walletGold by mutableStateOf(PlayerState.profile.gold)
        private set
    var runRelics by mutableStateOf<List<String>>(PlayerState.profile.run.relics)
        private set

    private var prevRightPose: HandPose = HandPose.UNKNOWN
    private var ballArmed = false

    // ─── 蓄力放招 ───
    var charging by mutableStateOf(false)
        private set
    var chargeProgress by mutableStateOf(0f)
        private set
    var pendingMove: Move? = null
        private set
    val canBoostCharge: Boolean get() = pendingMove?.let { playerEnergy >= it.cost + 1 } ?: false
    val currentCastEnergyCost: Int get() = pendingMove?.let { move ->
        val multiplier = 1f + chargeProgress * (GestureChargeTracker.MAX_MULTIPLIER - 1f)
        BattleEngine.energyCost(move, requireNotNull(engine).affordableCharge(move, multiplier))
    } ?: 0
    private var pendingSource: MoveSource = MoveSource.BUTTON

    // ─── 伴随猫援助 ───
    var companionEnergy by mutableStateOf(0f)
        private set
    var companionAssistReady by mutableStateOf(false)
        private set
    var lastVoiceHeard by mutableStateOf("")
        private set

    // ─── 回合分步演出 ───
    var resolving by mutableStateOf(false)
        private set
    private val plannedTurns = mutableListOf<BattleEngine.PlannedAttack>()
    private var turnIdx = 0
    private var stepTime = 0f
    private var stepAnimPlayed = false
    private var stepCommandCued = false
    private var stepDamageApplied = false
    private var pendingWin: Boolean? = null
    private var playerChargeMultiplier = 1f
    private var statusTickDamage = 0

    val currentNode: MapNode = PlayerState.profile.run.let { run ->
        requireNotNull(run.currentNode)
    }
    private val sessionRunId = PlayerState.profile.run.id
    private var battleTurn = PlayerState.profile.run.battle?.turn ?: 0
    val canReturnToMap: Boolean get() = !charging && !resolving && !enemyReplacing
    private fun turnRandom() = Random(PlayerState.profile.run.seed xor currentNode.index * 7919 xor battleTurn * 104729)
    private var replacementTime = 0f
    var enemyReplacing by mutableStateOf(false)
        private set

    fun attachScene(scene: BattleScene) {
        this.scene = scene
        scene.onBallHitArena = { onBallLanded() }
    }

    // ─── 每帧 ───

    fun tick(
        dtSec: Float,
        rightJoints: Map<String, Vector3>?,
        leftJoints: Map<String, Vector3>?,
    ) {
        scene?.tick(dtSec)

        val rightPose = gesture.update(HandSide.RIGHT, rightJoints, dtSec)
        val leftPose = gesture.update(HandSide.LEFT, leftJoints, dtSec)

        if (exitConfirmationVisible) {
            prevRightPose = rightPose
            return
        }

        if (leftJoints != null && !resolving && !forceSwitch) {
            when (leftPose) {
                HandPose.OPEN -> leftMenuVisible = true
                HandPose.FIST -> leftMenuVisible = false
                else -> { /* 保持 */ }
            }
        }
        if (resolving) leftMenuVisible = false
        rightMenuVisible = false

        if (phase == GamePhase.BATTLE_SETUP) {
            handleThrow(rightPose, rightJoints != null)
        }

        if (charging) {
            val handPos = gesture.anchorPosition(HandSide.RIGHT) ?: gesture.palmPosition(HandSide.RIGHT)
            chargeProgress = charge.update(handPos, dtSec)
            val timeout = charge.elapsed >= GestureChargeTracker.MAX_WINDOW_SEC
            val fistRelease = rightPose == HandPose.FIST && prevRightPose != HandPose.FIST
            if (timeout || fistRelease) commitCast()
        }

        if (phase == GamePhase.BATTLE_TURN && !companionAssistReady) {
            updateCompanionCharging(dtSec, rightJoints, leftJoints)
        }

        if (resolving) advanceResolution(dtSec)
        if (enemyReplacing) {
            replacementTime -= dtSec
            if (replacementTime <= 0f) {
                enemyReplacing = false
                leftMenuVisible = !forceSwitch
            }
        }

        prevRightPose = rightPose
    }

    private fun handleThrow(rightPose: HandPose, rightVisible: Boolean) {
        if (!rightVisible || scene?.ballInFlight == true) return
        val anchor = gesture.anchorPosition(HandSide.RIGHT)
            ?: gesture.palmPosition(HandSide.RIGHT) ?: return

        if (rightPose == HandPose.FIST) {
            ballArmed = true
        }
        if (ballArmed) {
            scene?.holdBallAt(anchor)
        }
        if (ballArmed) {
            val vel = gesture.wristVelocity(HandSide.RIGHT)
            val speed = gesture.speed(HandSide.RIGHT)
            if (speed >= HandGestureDetector.THROW_SPEED_THRESHOLD) {
                ballArmed = false
                scene?.throwBall(anchor, vel)
            }
        }
    }

    // ─── 动作入口 ───

    fun requestExitBattle() {
        if (canReturnToMap) exitConfirmationVisible = true
    }

    fun cancelExitBattle() {
        exitConfirmationVisible = false
    }

    fun confirmExitBattle(onExit: () -> Unit) {
        if (!exitConfirmationVisible || !canReturnToMap) return
        exitConfirmationVisible = false
        onExit()
    }

    /** 召唤当前节点的对手训练师，进入布置阶段（准备投球）。 */
    fun chooseNpcOpponent() {
        if (phase != GamePhase.ARENA_IDLE) return
        scene?.spawnEnemyTrainer()
        phase = GamePhase.BATTLE_SETUP
        rightMenuVisible = false
    }

    fun resumeBattleIfSaved() {
        if (PlayerState.profile.run.battle != null) onBallLanded()
    }

    /** 手柄兜底：直接投球。 */
    fun throwBallFallback() {
        if (phase != GamePhase.BATTLE_SETUP || scene?.ballInFlight == true) return
        ballArmed = false
        scene?.throwBallLocal(Vector3(0f, 1.2f, -0.3f), Vector3(0f, 3.0f, -6.0f))
    }

    /** 精灵球命中：按当前节点与存档队伍开战。 */
    private fun onBallLanded() {
        if (phase != GamePhase.BATTLE_SETUP) return
        val profile = PlayerState.profile
        val node = currentNode
        val b = profile.run.newBattle(profile, node)
        battle = b
        engine = BattleEngine(b, initialEnemySwitched = profile.run.battle?.enemyHasSwitched ?: false)
            .also { it.startPlayerTurn(turnRandom()) }
        // 背包来自存档：补给/商店买入的道具在战斗中使用并即时扣减。
        items.clear()
        repeat(profile.potions) { items += Item(name = "伤药", kind = ItemKind.HP_POTION, amount = 35) }
        repeat(profile.revives) { items += Item(name = "复活符", kind = ItemKind.REVIVE, amount = 50) }
        scene?.spawnFangkemengs(b.player.species, b.enemy.species)
        syncAll()
        companionEnergy = profile.run.battle?.companionEnergy ?: 0f
        companionAssistReady = companionEnergy >= 1f
        phase = GamePhase.BATTLE_TURN
        leftMenuVisible = true
        forceSwitch = requireNotNull(engine).needsForcedSwitch()
        switchMenuVisible = forceSwitch
        leftMenuVisible = !forceSwitch
        persistBattle()
    }

    fun selectMove(move: Move, source: MoveSource = MoveSource.BUTTON) {
        val b = battle ?: return
        if (engine == null) return
        if (phase != GamePhase.BATTLE_TURN || resolving || charging || enemyReplacing) return
        if (forceSwitch) return
        if (!b.player.canUse(move)) {
            battleLog.clear()
            battleLog.addAll(b.log.takeLast(4))
            b.log += "能量不足，无法使用 ${move.name}"
            syncLog()
            persistBattle()
            return
        }
        pendingMove = move
        pendingSource = source
        charging = true
        chargeProgress = 0f
        charge.begin()
        leftMenuVisible = false
    }

    fun castBySpokenText(text: String) {
        if (phase != GamePhase.BATTLE_TURN || resolving || charging || forceSwitch) return
        lastVoiceHeard = text
        val move = matchMoveByVoice(text) ?: return
        selectMove(move, MoveSource.VOICE)
    }

    private fun matchMoveByVoice(text: String): Move? {
        val moves = playerMoves()
        val norm = text.trim().replace("\\s".toRegex(), "")
        if (norm.isEmpty()) return null
        moves.firstOrNull { norm.contains(it.name) || it.name.contains(norm) }?.let { return it }
        return moves.maxByOrNull { m -> m.name.count { norm.contains(it) } }
            ?.takeIf { m -> m.name.any { norm.contains(it) } }
    }

    fun releaseChargeNow() {
        if (charging) commitCast()
    }

    private fun commitCast() {
        val eng = engine ?: return
        val move = pendingMove ?: return
        charging = false
        playerChargeMultiplier = eng.affordableCharge(move, charge.multiplier())
        charge.cancel()
        pendingMove = null

        startResolving(eng.planTurn(move))
    }

    /** 主动换人占一回合；倒下后的强制换人接续下一回合。 */
    fun switchMember(index: Int) {
        val b = battle ?: return
        val eng = engine ?: return
        if (index == b.playerActive || index !in b.playerTeam.indices) return
        if (b.playerTeam[index].isFainted) return
        if (resolving || charging || enemyReplacing) return
        if (phase != GamePhase.BATTLE_TURN) return
        switchMenuVisible = false
        if (forceSwitch) {
            eng.applySwitch(true, index)
            scene?.swapCreature(true, b.player.species)
            forceSwitch = false
            leftMenuVisible = true
            syncAll()
            syncLog()
            return
        }
        forceSwitch = false
        val plan = listOf(
            BattleEngine.PlannedAttack(true, b.player.moves.first(), isSwitch = true, switchTo = index),
        ) + eng.planEnemyResponse()
        startResolving(plan)
    }

    fun openSwitchMenu() {
        if (phase != GamePhase.BATTLE_TURN || resolving) return
        switchMenuVisible = true
        leftMenuVisible = false
    }

    fun closeSwitchMenu() {
        if (!forceSwitch) switchMenuVisible = false
    }

    private fun startResolving(plan: List<BattleEngine.PlannedAttack>) {
        plannedTurns.clear()
        plannedTurns.addAll(plan)
        turnIdx = 0
        stepTime = 0f
        stepAnimPlayed = false
        stepCommandCued = false
        stepDamageApplied = false
        pendingWin = null
        resolving = true
        leftMenuVisible = false
        switchMenuVisible = false
    }

    private fun advanceResolution(dtSec: Float) {
        val eng = engine ?: return
        if (turnIdx >= plannedTurns.size) {
            finishResolution()
            return
        }
        val planned = plannedTurns[turnIdx]

        // 换人拍：立即执行，不占用演出时长。
        if (planned.isSwitch) {
            eng.applySwitch(planned.attackerIsPlayer, planned.switchTo)
            val mon = if (planned.attackerIsPlayer) battle?.player else battle?.enemy
            if (mon != null) scene?.swapCreature(planned.attackerIsPlayer, mon.species)
            syncAll()
            syncLog()
            turnIdx++
            stepTime = 0f; stepAnimPlayed = false; stepCommandCued = false; stepDamageApplied = false
            return
        }

        if (eng.isFainted(planned.attackerIsPlayer)) {
            turnIdx++
            stepTime = 0f; stepAnimPlayed = false; stepCommandCued = false; stepDamageApplied = false
            return
        }

        stepTime += dtSec
        val isPlayer = planned.attackerIsPlayer
        val intensity = if (isPlayer) playerChargeMultiplier else 1f
        // 敌方出招前先给训练家一个指挥动作，再让精灵释放技能。
        if (!isPlayer && !stepCommandCued) {
            scene?.playTrainerCommand()
            stepCommandCued = true
        }
        val attackStart = if (isPlayer) 0f else TRAINER_CUE_DELAY
        if (!stepAnimPlayed && stepTime >= attackStart) {
            scene?.playAttack(isPlayer, planned.move, intensity)
            stepAnimPlayed = true
        }
        if (!stepDamageApplied && stepTime >= attackStart + SkillVfx.impactDelay(planned.move)) {
            val res = eng.applyAttack(isPlayer, planned.move, if (isPlayer) playerChargeMultiplier else 1f)
            scene?.resolveSkillImpact(planned.move, !res.missed)
            stepDamageApplied = true
            syncAll()
            syncLog()
            pendingWin = eng.winner()
        }
        if (stepAnimPlayed) scene?.advanceSkillTime((stepTime - attackStart).coerceAtLeast(0f))
        if (stepTime >= attackStart + SkillVfx.duration(planned.move)) {
            if (eng.isFainted(!isPlayer)) scene?.faint(!isPlayer)
            if (pendingWin != null) { finishResolution(); return }
            turnIdx++
            stepTime = 0f; stepAnimPlayed = false; stepCommandCued = false; stepDamageApplied = false
        }
    }

    private fun finishResolution() {
        val eng = engine ?: return
        // 回合末：状态伤害 + 能量回复。
        // 终局攻击之后不再结算状态伤害，避免胜利被末尾中毒覆盖。
        val ticks = if (eng.winner() == null) eng.endTurn() else emptyList()
        statusTickDamage = ticks.sumOf { it.damage }
        ticks.forEach {
            if (it.fainted) scene?.faint(it.isPlayer)
        }
        syncAll()
        syncLog()
        resolving = false
        playerChargeMultiplier = 1f

        val win = eng.winner() ?: pendingWin
        when (win) {
            true -> { endBattle(true); return }
            false -> { endBattle(false); return }
            null -> Unit
        }

        if (eng.replaceFaintedEnemy()) {
            scene?.swapCreature(false, requireNotNull(battle).enemy.species)
            enemyReplacing = true
            replacementTime = 1.0f
            syncAll()
            syncLog()
        }
        battleTurn++
        eng.startPlayerTurn(turnRandom())
        if (eng.needsForcedSwitch()) {
            forceSwitch = true
            switchMenuVisible = true
            leftMenuVisible = false
        } else {
            leftMenuVisible = !enemyReplacing
        }
        persistBattle()
    }

    // ─── 伴随猫援助 ───

    private fun updateCompanionCharging(
        dtSec: Float,
        rightJoints: Map<String, Vector3>?,
        leftJoints: Map<String, Vector3>?,
    ) {
        val sc = scene ?: return
        val catPos = sc.companionPosition()
        val rightHand = gesture.anchorPosition(HandSide.RIGHT) ?: gesture.palmPosition(HandSide.RIGHT)
        val leftHand = gesture.anchorPosition(HandSide.LEFT) ?: gesture.palmPosition(HandSide.LEFT)
        val near = listOfNotNull(rightHand, leftHand).any { hand ->
            val local = sc.toContent(hand)
            horizontalDistance(local, catPos) < COMPANION_PET_RADIUS
        }
        if (near) {
            companionEnergy = (companionEnergy + dtSec / COMPANION_CHARGE_SEC).coerceAtMost(1f)
            if (companionEnergy >= 1f) companionAssistReady = true
        }
    }

    fun triggerCompanionAssist() {
        val eng = engine ?: return
        if (phase != GamePhase.BATTLE_TURN || !companionAssistReady || resolving || charging || forceSwitch || enemyReplacing) return
        if (eng.healPlayer(COMPANION_HEAL_AMOUNT) == 0) return
        syncAll()
        scene?.playCompanionAssist()
        syncLog()
        companionEnergy = 0f
        companionAssistReady = false
        startResolving(eng.planEnemyResponse())
    }

    // ─── 道具 ───

    /** 使用伤药（消耗一个）。 */
    fun useFirstPotion() {
        val eng = engine ?: return
        if (phase != GamePhase.BATTLE_TURN || resolving || charging || forceSwitch || enemyReplacing) return
        val potion = items.firstOrNull { it.kind == ItemKind.HP_POTION && it.amount > 0 } ?: return
        if (eng.useHpPotion(potion) == 0) return
        items.remove(potion)
        syncInventory()
        syncAll()
        syncLog()
        startResolving(eng.planEnemyResponse())
    }

    /** 使用复活符（消耗一个）。 */
    fun useRevive() {
        val eng = engine ?: return
        if (phase != GamePhase.BATTLE_TURN || resolving || charging || forceSwitch || enemyReplacing) return
        val rev = items.firstOrNull { it.kind == ItemKind.REVIVE } ?: return
        if (!eng.useRevive(rev)) return
        items.remove(rev)
        syncInventory()
        syncAll()
        syncLog()
        startResolving(eng.planEnemyResponse())
    }

    /** 战斗内消耗同步回存档，避免道具在返回地图后“复活”。 */
    private fun syncInventory() {
        val profile = PlayerState.profile
        profile.potions = items.count { it.kind == ItemKind.HP_POTION }
        profile.revives = items.count { it.kind == ItemKind.REVIVE }
    }

    fun persistBattle() {
        val b = battle ?: return
        val profile = PlayerState.profile
        if (!profile.run.active || profile.run.id != sessionRunId || profile.run.selectedNode != currentNode.index) return
        syncInventory()
        profile.run = profile.run.copy(battle = SavedBattle.capture(b, battleTurn,
            requireNotNull(engine).enemySwitchedThisBattle, companionEnergy))
        PlayerState.persist()
    }

    private fun endBattle(playerWon: Boolean) {
        if (phase == GamePhase.RESULT) return
        if (battle == null) return
        val profile = PlayerState.profile
        resultPlayerWon = playerWon
        phase = GamePhase.RESULT
        leftMenuVisible = false
        switchMenuVisible = false
        forceSwitch = false
        enemyReplacing = false
        resolving = false
        charging = false
        charge.cancel()
        pendingMove = null

        val node = currentNode
        val ownedBefore = profile.unlocked.toSet()
        syncInventory()
        if (playerWon && CampaignFlow.finishBattle(profile, sessionRunId, node, requireNotNull(battle))) {
            rewardGold = node.rewardGold
            unlockedText = (profile.unlocked - ownedBefore).mapNotNull { DemoData.speciesOf(it.name)?.name }.joinToString("，")
            runCleared = node.depth == CampaignMap.DEPTHS
            walletGold = profile.run.gold
        } else {
            if (!playerWon) CampaignFlow.loseRun(profile, sessionRunId, battle)
            rewardGold = 0
            unlockedText = ""
            runCleared = false
        }
        runRelics = profile.run.relics
        PlayerState.persist()
    }

    private fun syncLog() {
        battle?.let { b ->
            battleLog.clear()
            battleLog.addAll(b.log.takeLast(5))
        }
    }

    private fun syncAll() {
        val b = battle ?: return
        val p = b.player
        val e = b.enemy
        playerHp = p.hp
        playerMaxHp = p.maxHp
        enemyHp = e.hp
        enemyMaxHp = e.maxHp
        playerName = p.species.name
        enemyName = e.species.name
        playerLevel = p.level
        enemyLevel = e.level
        playerElement = p.element
        enemyElement = e.element
        playerEnergy = p.energy
        playerMaxEnergy = p.maxEnergy
        playerStatus = p.status
        enemyStatus = e.status
        playerActiveIndex = b.playerActive
        playerParty = b.playerTeam.mapIndexed { i, mon ->
            PartySlotView(i, mon.species.name, mon.element, mon.hp, mon.maxHp, mon.level, mon.isFainted, i == b.playerActive)
        }
        enemyParty = b.enemyTeam.mapIndexed { i, mon ->
            PartySlotView(i, mon.species.name, mon.element, mon.hp, mon.maxHp, mon.level, mon.isFainted, i == b.enemyActive)
        }
        walletGold = PlayerState.profile.run.gold
        runRelics = PlayerState.profile.run.relics
    }

    fun playerMoves(): List<Move> = battle?.player?.moves ?: emptyList()

    fun leftMenuAnchor(): Vector3? {
        val a = gesture.anchorPosition(HandSide.LEFT) ?: gesture.palmPosition(HandSide.LEFT) ?: return null
        return Vector3(a.x, a.y + MENU_UP_OFFSET, a.z - MENU_FWD_OFFSET)
    }

    companion object {
        private const val DAMAGE_DELAY = 1.45f
        private const val TRAINER_CUE_DELAY = 0.4f
        private const val STEP_DURATION = 3.15f
        private const val MENU_UP_OFFSET = 0.20f
        private const val MENU_FWD_OFFSET = 0.10f
        private const val COMPANION_PET_RADIUS = 0.28f
        private const val COMPANION_CHARGE_SEC = 2.5f
        private const val COMPANION_HEAL_AMOUNT = 22
    }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
