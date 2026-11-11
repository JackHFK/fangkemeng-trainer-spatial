package com.example.fangkemeng.battle

import com.example.fangkemeng.model.CreatureId
import com.example.fangkemeng.model.BattleSessionConfig
import com.example.fangkemeng.model.Move
import com.example.fangkemeng.model.Species
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 战斗 3D 场景管理器（1:1 世界坐标，原点在玩家脚下，-Z 为前方）。
 *
 * 负责：战斗矩形平面、地面、光照；程序化动物的创建与摆位；
 * 精灵球抛物线物理；程序动画驱动。
 *
 * 实体初始化在 SpatialView 内容回调执行；连续动画由注册的 GameFrameSystem 逐帧驱动。
 */
class BattleScene(
    private val content: SpatialViewContent,
) {
    /** 精灵球飞行阶段。 */
    enum class BallPhase { IDLE, HELD, THROWN, LANDED }

    var ballPhase: BallPhase = BallPhase.IDLE
        private set

    private var ballVel = Vector3(0f, 0f, 0f)
    private var ballPos = Vector3(0f, 1.0f, -0.3f)
    private var ballPeakY = 0f

    private var light: Entity? = null
    private var playerCreature: BattleCreature? = null
    private var enemyCreature: BattleCreature? = null
    private var companionCreature: BattleCreature? = null
    private var voxelStage: VoxelStageRuntime? = null
    private var trainer: VoxelTrainer? = null
    private var ball: Entity? = null
    private val attackParticles = AttackParticleEngine(content)
    private val skillImpacts = SkillEffectEngine(content)
    private var playerCreatureId: CreatureId? = null
    private var enemyCreatureId: CreatureId? = null

    /** 程序化动物在场景中的用途：我方出战 / 敌方出战 / 跟随宠物。 */
    private enum class CreatureSlot { PLAYER, ENEMY, COMPANION }

    private data class BuiltCreature(
        val slot: CreatureSlot,
        val creatureId: CreatureId,
        val creature: BattleCreature,
    )

    /**
     * 程序化动物构建只允许在 Android 主线程执行（`Entity` 构造有线程校验），
     * 因此用主线程作用域在帧间隙构建，构建完成后由渲染帧挂载（[flushBuiltCreatures]）。
     */
    private val buildScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val builtCreatures = ConcurrentLinkedQueue<BuiltCreature>()
    private var companionRequested = false

    private var companionPos = Vector3(0f, 0f, -0.3f)
    private var companionYawDeg = 180f
    private var companionScale = 1f
    private var companionInitialized = false
    private var followStart = companionPos
    private var followTarget = companionPos
    private var followProgress = 1f
    private var followDuration = 0.8f
    private var companionSitWeight = 1f
    private val companionAnchor = CompanionFollowAnchor()
    private val petTouch=PetTouchTimer()
    private val companionHearts=CompanionHearts(content)
    private var companionViewer=Vector3(0f,1.6f,0f)

    // 坐标转换锚：手部追踪给的是「追踪世界坐标」，需转成 content 局部坐标才能与场景/手对齐。
    private val converter: Entity = Entity().also { content.addEntity(it) }

    /** 把追踪世界坐标转成 content 局部坐标（手部关节定位用）。 */
    fun toContent(worldPos: Vector3): Vector3 = converter.convertPositionFrom(worldPos, null)

    /** 把追踪世界方向（速度）转成 content 局部方向。 */
    fun dirToContent(worldFrom: Vector3, worldVel: Vector3): Vector3 {
        val a = toContent(worldFrom)
        val b = toContent(Vector3(worldFrom.x + worldVel.x, worldFrom.y + worldVel.y, worldFrom.z + worldVel.z))
        return Vector3(b.x - a.x, b.y - a.y, b.z - a.z)
    }

    /** 命中敌方半场后回调（触发方可梦入场 / 进入回合）。 */
    var onBallHitArena: () -> Unit = {}

    /** 同步初始化：地面 + 战斗矩形 + 光照。 */
    fun setupStaticScene() {
        light = Entity().apply {
            components.set(DirectionalLightComponent(Color4.WHITE, 800f))
        }.also { content.addEntity(it) }
        addBall()
    }

    /** 按当前关卡创建程序化擂台。 */
    fun buildSelectedStage() {
        val stage = BattleSessionConfig.selectedStage
        voxelStage?.root?.let { runCatching { it.destroy(true) } }
        val built = VoxelStages.build(stage)
        voxelStage = built
        content.addEntity(built.root)
    }

    /** 体素训练师入场：固定站在战场左后侧外围，面向玩家；外观随关卡变化。 */
    private fun spawnVoxelTrainer() {
        if (trainer != null) return
        val level = BattleSessionConfig.currentNode?.index ?: 1
        val t = VoxelTrainer(TrainerLooks.forLevel(level))
        t.place(ENEMY_TRAINER_POS, ENEMY_TRAINER_YAW, NPC_TARGET_HEIGHT_M / t.nominalHeightM)
        content.addEntity(t.root)
        trainer = t
    }

    /**
     * 身边宠物：地面上、以 HMD xz 为用户位置，放在用户前右 45°、半径约 0.3m。
     *
     * - 用户看向宠物且夹角 < 15° 时，猫头和身体看向用户；
     * - 位移超过 45cm 且持续 1.5 秒后，猫用 smoothstep 慢-快-慢跟随。
     * - 原地转头只影响猫的注视；右前方位置由接受的整体朝向决定。
     */
    fun updateCompanionCat(dtSec: Float, hmdWorldPos: Vector3, hmdRotation: Quat) {
        skillImpacts.viewPosition = hmdWorldPos.point()
        val hmdPos = toContent(hmdWorldPos)
        companionViewer=hmdPos
        val forwardWorld = hmdRotation.rotate(Vector3(0f, 0f, -1f))
        val forward = horizontalNormalized(dirToContent(hmdWorldPos, forwardWorld), Vector3(0f, 0f, -1f))
        val toCat = horizontalNormalized(Vector3(companionPos.x - hmdPos.x, 0f, companionPos.z - hmdPos.z), forward)
        val lookingAtCat = companionInitialized && angleDeg(forward, toCat) < COMPANION_USER_LOOK_DEG
        companionAnchor.update(dtSec, hmdPos.x, hmdPos.z, forward.x, forward.z, lookingAtCat)
        val overallForward = Vector3(companionAnchor.forwardX, 0f, companionAnchor.forwardZ)
        val right = Vector3(-overallForward.z, 0f, overallForward.x)
        val targetDir = horizontalNormalized(Vector3(overallForward.x + right.x, 0f, overallForward.z + right.z), overallForward)
        val target = Vector3(
            companionAnchor.x + targetDir.x * COMPANION_RADIUS,
            0f,
            companionAnchor.z + targetDir.z * COMPANION_RADIUS,
        )

        ensureCompanionCat(target, yawDeg(overallForward))
        updateCompanionBehavior(dtSec, target, yawDeg(overallForward), hmdPos, forward)
    }

    /** 手部关节与猫头/身体的实际局部包围盒接触，连续三秒触发爱心。 */
    fun updateCompanionTouch(dtSec: Float,right: Map<String,Vector3>?,left: Map<String,Vector3>?) {
        val cat=companionCreature
        val hands=listOf(right,left)
        var touching=false
        if(cat!=null) {
            val margin=.03f/companionScale.coerceAtLeast(.01f)
            for(name in listOf("head","body")) {
                val part=cat.joints[name]?.entity?:continue
                val bounds=part.getVisualBounds(part,recursive=true,enabledOnly=true)
                if(bounds.isEmpty()) continue
                val c=bounds.center;val half=bounds.size
                for(hand in hands) {
                    for(world in hand?.values.orEmpty()) {
                        if(!world.x.isFinite()||!world.y.isFinite()||!world.z.isFinite()) continue
                        val p=part.convertPositionFrom(world,null)
                        val dx=max(0f,abs(p.x-c.x)-half.x*.5f)
                        val dy=max(0f,abs(p.y-c.y)-half.y*.5f)
                        val dz=max(0f,abs(p.z-c.z)-half.z*.5f)
                        if(dx*dx+dy*dy+dz*dz<=margin*margin) { touching=true;break }
                    }
                    if(touching) break
                }
                if(touching) break
            }
        }
        if(petTouch.update(dtSec,touching)) {
            cat?.joints?.get("head")?.entity?.let { head ->
                val bounds=head.getVisualBounds(converter,recursive=true,enabledOnly=true)
                if(!bounds.isEmpty()) {
                    companionHearts.play(Vector3(bounds.center.x,bounds.center.y+bounds.size.y*.5f+.045f,bounds.center.z))
                }
            }
        }
        companionHearts.tick(dtSec,companionViewer)
    }

    private fun addBall() {
        val mesh = MeshResource.createSphere(BALL_RADIUS)
        val mat = PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4.fromSRGBHex(BALL_COLOR_HEX))
            setRoughness(0.3f)
            setMetallic(0.1f)
        }
        ball = Entity().apply {
            components.set(ModelComponent(mesh, mat))
            components.get(TransformComponent::class.java)?.setPosition(ballPos)
            enabled = false
        }.also { content.addEntity(it) }
    }

    /** 飞行期间保持弹道，持续握拳和重复按钮都不能重新抓取或投出。 */
    val ballInFlight: Boolean get() = ballPhase == BallPhase.THROWN

    /** 把精灵球吸附到给定（右手掌心）追踪世界坐标，进入 HELD。 */
    fun holdBallAt(worldPos: Vector3) {
        if (ballInFlight) return
        ballPhase = BallPhase.HELD
        ballPos = toContent(worldPos)
        ball?.apply {
            enabled = true
            components.get(TransformComponent::class.java)?.setPosition(ballPos)
        }
    }

    /** 从手势投出：起点为右手追踪世界坐标（转 content），用弹道求解保证落到场地中心。 */
    fun throwBall(worldFrom: Vector3, worldVelocity: Vector3) {
        launchToArenaCenter(toContent(worldFrom))
    }

    /** 手柄兜底：从 content 局部坐标投出，同样弹道求解到场地中心。 */
    fun throwBallLocal(localFrom: Vector3, localVelocity: Vector3) {
        launchToArenaCenter(localFrom)
    }

    private fun launchToArenaCenter(from: Vector3) {
        if (ballInFlight) return
        val t = THROW_TIME
        val targetX = 0f
        val targetY = ARENA_LAND_Y
        val targetZ = ARENA_CENTER_Z
        val vx = (targetX - from.x) / t
        val vz = (targetZ - from.z) / t
        val vy = (targetY - from.y) / t - 0.5f * GRAVITY * t
        ballPhase = BallPhase.THROWN
        ballPos = from
        ballVel = Vector3(vx, vy, vz)
        ballPeakY = from.y
        ball?.apply {
            enabled = true
            components.get(TransformComponent::class.java)?.setPosition(ballPos)
        }
    }

    /** 投球前让程序化训练师在场边亮相。 */
    fun spawnEnemyTrainer() {
        spawnVoxelTrainer()
    }

    /** 双方伙伴入场（精灵球命中后）。训练师位置固定在场外边缘，不随精灵入场移动。 */
    fun spawnFangkemengs(playerSpecies: Species, enemySpecies: Species) {
        if (playerCreatureId != playerSpecies.creatureId || playerCreature != null) {
            playerCreatureId = playerSpecies.creatureId
            requestCreature(CreatureSlot.PLAYER, playerSpecies.creatureId)
        }
        if (enemyCreatureId != enemySpecies.creatureId || enemyCreature != null) {
            enemyCreatureId = enemySpecies.creatureId
            requestCreature(CreatureSlot.ENEMY, enemySpecies.creatureId)
        }
    }

    /** 换人：销毁旧模型，在同一站位生成新的程序化动物。 */
    fun swapCreature(isPlayer: Boolean, species: Species) {
        skillImpacts.clear()
        if (isPlayer) {
            playerCreatureId = species.creatureId
            requestCreature(CreatureSlot.PLAYER, species.creatureId)
        } else {
            enemyCreatureId = species.creatureId
            requestCreature(CreatureSlot.ENEMY, species.creatureId)
        }
    }

    /** 播放一次攻击动画，并按招式属性发射弹道粒子与命中冲击。[chargeIntensity] 蓄力强度放大规模。 */
    fun playAttack(isPlayer: Boolean, move: Move, chargeIntensity: Float = 1f) {
        val creature = if (isPlayer) playerCreature else enemyCreature
        val creatureId = (if (isPlayer) playerCreatureId else enemyCreatureId) ?: return
        val from = attackSourcePosition(isPlayer, creatureId) ?: return
        val to = attackTargetPosition(!isPlayer) ?: return
        val target = if (isPlayer) enemyCreature else playerCreature
        skillImpacts.launch(move, from, to, chargeIntensity, creature, target, externalClock = true)
    }

    fun playSkillImpact(isPlayer: Boolean, move: Move, intensity: Float = 1f) {
        skillImpacts.impact(move)
    }

    fun resolveSkillImpact(move: Move, hit: Boolean) = skillImpacts.resolveImpact(move, hit)
    fun advanceSkillTime(seconds: Float) = skillImpacts.advanceTo(seconds.toDouble())

    /** 敌方训练家指挥动作：出招前抬手指向擂台。 */
    fun playTrainerCommand() {
        trainer?.command()
    }

    /** 伴随猫援助技：在玩家方可梦身上播一束治疗粒子。 */
    fun playCompanionAssist() {
        val target = fangkemengHeadPosition(isPlayer = true) ?: companionPos
        val from = Vector3(target.x, target.y + 0.5f, target.z)
        attackParticles.play(AttackParticleType.HEAL, from, target, 1.4f)
    }

    /** 伴随猫世界坐标（content 局部）：供“摸猫充能”范围判定。 */
    fun companionPosition(): Vector3 = Vector3(companionPos.x, COMPANION_TARGET_HEIGHT * 0.6f, companionPos.z)

    /** 一方伙伴倒下：隐藏对应实体。 */
    fun faint(isPlayer: Boolean) {
        (if (isPlayer) playerCreature else enemyCreature)?.root?.enabled = false
    }

    /** 头顶世界坐标（供 HP 条面板定位）。 */
    fun fangkemengHeadPosition(isPlayer: Boolean): Vector3? {
        val mon = (if (isPlayer) playerCreature else enemyCreature)?.root ?: return null
        val t = mon.components.get(TransformComponent::class.java) ?: return null
        val p = t.position
        return Vector3(p.x, p.y + FANGKEMENG_HEIGHT + 0.25f, p.z)
    }

    /** 重新开始一局：移除程序化动物、训练师并把精灵球复位。 */
    fun resetEncounter() {
        skillImpacts.clear()
        destroyCreature(playerCreature)
        destroyCreature(enemyCreature)
        trainer?.dispose()
        trainer = null
        playerCreature = null
        enemyCreature = null
        playerCreatureId = null
        enemyCreatureId = null
        attackParticles.clear()

        ballPhase = BallPhase.IDLE
        ballVel = Vector3(0f, 0f, 0f)
        ballPos = Vector3(0f, 1.0f, -0.3f)
        ballPeakY = ballPos.y
        ball?.enabled = false
        ball?.components?.get(TransformComponent::class.java)?.setPosition(ballPos)
    }

    /** 每帧推进：程序化动物 idle/攻击动画 + 精灵球抛物线。 */
    fun tick(dtSec: Float) {
        flushBuiltCreatures()
        // 训练师：等待投球时托球前伸，开战后收手。
        trainer?.apply {
            armRaise = if (ballPhase == BallPhase.IDLE) 0.75f else 0.12f
            tick(dtSec)
        }
        companionCreature?.tick(dtSec)
        if(!skillImpacts.controls(playerCreature)) playerCreature?.tick(dtSec)
        if(!skillImpacts.controls(enemyCreature)) enemyCreature?.tick(dtSec)
        attackParticles.tick(dtSec)
        skillImpacts.tick(dtSec)
        voxelStage?.tick(dtSec)

        if (ballPhase != BallPhase.THROWN) return
        ballVel = Vector3(ballVel.x, ballVel.y + GRAVITY * dtSec, ballVel.z)
        ballPos = Vector3(
            ballPos.x + ballVel.x * dtSec,
            ballPos.y + ballVel.y * dtSec,
            ballPos.z + ballVel.z * dtSec,
        )
        if (ballPos.y > ballPeakY) ballPeakY = ballPos.y
        ball?.components?.get(TransformComponent::class.java)?.setPosition(ballPos)

        val reachedCenterZ = ballPos.z <= ARENA_CENTER_Z
        val landed = ballPos.y <= ARENA_LAND_Y + BALL_RADIUS
        if (reachedCenterZ || landed) {
            ballPhase = BallPhase.LANDED
            ball?.enabled = false
            onBallHitArena()
        }
    }

    /** 后台构建一只动物；构建完成后由渲染帧挂载，避免阻塞 UI 线程。 */
    private fun requestCreature(slot: CreatureSlot, creatureId: CreatureId) {
        buildScope.launch {
            val creature = runCatching { ProceduralCreatureFactory.create(creatureId) }.getOrNull()
                ?: return@launch
            builtCreatures.add(BuiltCreature(slot, creatureId, creature))
        }
    }

    /** 渲染帧调用：挂载后台构建完成的动物，丢弃已被新请求取代的旧构建。 */
    private fun flushBuiltCreatures() {
        while (true) {
            val built = builtCreatures.poll() ?: return
            when (built.slot) {
                CreatureSlot.PLAYER -> {
                    if (built.creatureId != playerCreatureId) {
                        runCatching { built.creature.root.destroy(true) }
                        continue
                    }
                    destroyCreature(playerCreature)
                    playerCreature = built.creature
                    placeCreature(
                        built.creature,
                        Vector3(-0.4f, 0f, ARENA_CENTER_Z + ARENA_D / 2f - 0.5f),
                        PLAYER_YAW,
                    )
                }
                CreatureSlot.ENEMY -> {
                    if (built.creatureId != enemyCreatureId) {
                        runCatching { built.creature.root.destroy(true) }
                        continue
                    }
                    destroyCreature(enemyCreature)
                    enemyCreature = built.creature
                    placeCreature(
                        built.creature,
                        Vector3(0.4f, 0f, ARENA_CENTER_Z - ARENA_D / 2f + 0.5f),
                        ENEMY_YAW,
                    )
                }
                CreatureSlot.COMPANION -> {
                    companionCreature = built.creature
                    companionScale = limitedScale(built.creature, COMPANION_TARGET_HEIGHT)
                    placeCreature(
                        built.creature,
                        Vector3(companionPos.x, 0f, companionPos.z),
                        companionYawDeg,
                        companionScale,
                    )
                }
            }
        }
    }

    private fun placeCreature(
        creature: BattleCreature,
        pos: Vector3,
        yawDeg: Float,
        scale: Float = limitedScale(creature, FANGKEMENG_HEIGHT),
    ) {
        creature.placeForSkill(pos,scale,yawDeg)
        content.addEntity(creature.root)
    }

    private fun attackSourcePosition(isPlayer: Boolean, creatureId: CreatureId): Vector3? {
        val p = creatureRootPosition(isPlayer) ?: return null
        val forward = if (isPlayer) Vector3(0f, 0f, -1f) else Vector3(0f, 0f, 1f)
        val y = when (creatureId) {
            CreatureId.WATER_TURTLE -> FANGKEMENG_HEIGHT * 0.68f
            CreatureId.SEED_CUB -> FANGKEMENG_HEIGHT * 0.56f
            else -> FANGKEMENG_HEIGHT * 0.55f
        }
        val forwardOffset = when (creatureId) {
            CreatureId.WATER_TURTLE -> 0.13f
            CreatureId.SEED_CUB -> 0.10f
            else -> 0.10f
        }
        return Vector3(
            p.x + forward.x * forwardOffset,
            p.y + y,
            p.z + forward.z * forwardOffset,
        )
    }

    private fun attackTargetPosition(isPlayer: Boolean): Vector3? {
        val p = creatureRootPosition(isPlayer) ?: return null
        return Vector3(p.x, p.y + FANGKEMENG_HEIGHT * 0.50f, p.z)
    }

    private fun creatureRootPosition(isPlayer: Boolean): Vector3? {
        val creature = if (isPlayer) playerCreature else enemyCreature
        return creature?.root?.components?.get(TransformComponent::class.java)?.position
    }

    private fun limitedScale(creature: BattleCreature, targetHeight: Float): Float {
        val targetScale = targetHeight / creature.nominalHeightM
        val limitScale = creature.maxAllowedDimensionM / creature.nominalMaxDimensionM
        return min(targetScale, limitScale)
    }

    private fun ensureCompanionCat(initialTarget: Vector3, initialYawDeg: Float) {
        if (companionCreature != null || companionRequested) return
        companionRequested = true
        companionPos = initialTarget
        followStart = initialTarget
        followTarget = initialTarget
        companionYawDeg = initialYawDeg
        companionSitWeight = 1f
        companionInitialized = true
        requestCreature(CreatureSlot.COMPANION, CreatureId.CAT)
    }

    private fun updateCompanionBehavior(
        dtSec: Float,
        target: Vector3,
        restYawDeg: Float,
        hmdPos: Vector3,
        hmdForward: Vector3,
    ) {
        if (!companionInitialized) return
        val distToCurrentTarget = horizontalDistance(followTarget, target)
        if (distToCurrentTarget > COMPANION_RETARGET_DISTANCE && followProgress >= 0.18f) {
            followStart = companionPos
            followTarget = target
            followProgress = 0f
            val travel = horizontalDistance(followStart, followTarget)
            followDuration = (0.45f + travel * 1.8f).coerceIn(0.55f, 1.45f)
        }

        val toPet = horizontalNormalized(
            Vector3(companionPos.x - hmdPos.x, 0f, companionPos.z - hmdPos.z),
            Vector3(0f, 0f, -1f),
        )
        val userLookingAtCat = angleDeg(hmdForward, toPet) < COMPANION_USER_LOOK_DEG

        val moving = followProgress < 1f
        val desiredYawBeforeMove = if (userLookingAtCat && !moving) {
            yawDeg(Vector3(hmdPos.x - companionPos.x, 0f, hmdPos.z - companionPos.z))
        } else {
            restYawDeg
        }
        val yawNeedBeforeMove = abs(normalizeDeg(desiredYawBeforeMove - companionYawDeg))
        val needsAction = moving || yawNeedBeforeMove > COMPANION_TURN_IDLE_DEG
        companionSitWeight = approach(
            companionSitWeight,
            if (needsAction) 0f else 1f,
            dtSec * if (needsAction) COMPANION_STAND_RATE else COMPANION_SIT_RATE,
        )

        val standReady = 1f - companionSitWeight
        var moveWeight: Float
        var desiredYaw: Float
        if (moving) {
            // 坐下状态启动跟随时先慢一点，形成“先起身再走”的观感。
            val followSpeedFactor = 0.22f + standReady * 0.78f
            followProgress = (followProgress + dtSec / followDuration * followSpeedFactor).coerceAtMost(1f)
            val eased = smoothStep(followProgress)
            companionPos = lerp(followStart, followTarget, eased)
            moveWeight = sin((followProgress * PI).toDouble()).toFloat().coerceIn(0f, 1f)
            val moveDir = Vector3(followTarget.x - followStart.x, 0f, followTarget.z - followStart.z)
            desiredYaw = yawDeg(horizontalNormalized(moveDir, Vector3(0f, 0f, 1f)))
        } else {
            companionPos = followTarget
            moveWeight = 0f
            desiredYaw = desiredYawBeforeMove
        }

        val yawDiff = abs(normalizeDeg(desiredYaw - companionYawDeg))
        val yawLerp = if (needsAction) 0.06f + standReady * 0.20f else 0.05f
        companionYawDeg = lerpAngle(companionYawDeg, desiredYaw, yawLerp)
        val turnWeight = (yawDiff / 45f).coerceIn(0f, 1f)
        val actionWeight = max(moveWeight, turnWeight) * (0.35f + standReady * 0.65f)

        val look = companionLookState(hmdPos, hmdForward, userLookingAtCat, actionWeight)
        companionCreature?.setCompanionState(
            headYawDeg = look.headYawDeg,
            headPitchDeg = look.headPitchDeg,
            moveWeight = actionWeight,
            sitWeight = companionSitWeight,
        )
        companionCreature?.root?.components?.get(TransformComponent::class.java)?.apply {
            setPosition(Vector3(companionPos.x, 0f, companionPos.z))
            setEulerAngles(EulerAngles(0f, companionYawDeg, 0f))
            setScaleVector(Vector3(companionScale, companionScale, companionScale))
        }
    }

    private fun companionLookState(
        hmdPos: Vector3,
        hmdForward: Vector3,
        userLookingAtCat: Boolean,
        actionWeight: Float,
    ): CompanionLookState {
        if (!userLookingAtCat) return CompanionLookState(null, null)
        val catHeadY = 0.43f * companionScale
        val toUser = Vector3(
            hmdPos.x - companionPos.x,
            hmdPos.y - catHeadY,
            hmdPos.z - companionPos.z,
        )
        val horizontal = sqrt(toUser.x * toUser.x + toUser.z * toUser.z).coerceAtLeast(0.001f)
        val worldYaw = yawDeg(Vector3(toUser.x, 0f, toUser.z))
        val localYaw = normalizeDeg(worldYaw - companionYawDeg).coerceIn(-150f, 150f)
        val pitchUpDeg = atan2(toUser.y, horizontal).toDegrees().coerceIn(-35f, 75f)
        return CompanionLookState(localYaw, -pitchUpDeg)
    }

    private fun destroyCreature(creature: BattleCreature?) {
        creature?.root?.let { runCatching { it.destroy(true) } }
    }

    fun dispose() {
        buildScope.cancel()
        resetEncounter()
        destroyCreature(companionCreature)
        companionCreature = null
        petTouch.reset()
        companionHearts.dispose()
        attackParticles.dispose()
        skillImpacts.dispose()
        voxelStage?.root?.let { runCatching { it.destroy(true) } }
        voxelStage = null
        listOf(light, ball, converter).forEach { e ->
            e?.let { runCatching { it.destroy(true) } }
        }
    }

    companion object {
        const val FANGKEMENG_HEIGHT = 0.7f

        // 程序化动物默认朝向 +Z；玩家方转到 -Z，敌方保持 +Z。
        const val PLAYER_YAW = 180f
        const val ENEMY_YAW = 0f

        const val ARENA_W = 2.5f
        const val ARENA_D = 3.5f
        const val ARENA_CENTER_Z = -2.5f

        const val BALL_RADIUS = 0.045f
        const val BALL_COLOR_HEX = "0xE3350DFF"
        const val GRAVITY = -4.5f
        const val THROW_TIME = 0.9f
        const val ARENA_LAND_Y = 0.1f

        const val NPC_TARGET_HEIGHT_M = 1.5f
        /** 训练家固定站位：战场后方边缘外侧，站在树冠之间，精灵入场后不再移动。 */
        val ENEMY_TRAINER_POS = Vector3(-0.35f, 0f, ARENA_CENTER_Z - ARENA_D / 2f - 0.75f)
        const val ENEMY_TRAINER_YAW = 5f
        const val COMPANION_RADIUS = 0.3f
        const val COMPANION_TARGET_HEIGHT = 0.46f
        const val COMPANION_RETARGET_DISTANCE = 0.06f
        const val COMPANION_USER_LOOK_DEG = 15f
        const val COMPANION_TURN_IDLE_DEG = 4f
        const val COMPANION_STAND_RATE = 5.5f
        const val COMPANION_SIT_RATE = 1.7f
    }
}

private data class CompanionLookState(
    val headYawDeg: Float?,
    val headPitchDeg: Float?,
)

private fun Quat.rotate(v: Vector3): Vector3 {
    val tx = 2f * (y * v.z - z * v.y)
    val ty = 2f * (z * v.x - x * v.z)
    val tz = 2f * (x * v.y - y * v.x)
    return Vector3(
        v.x + w * tx + (y * tz - z * ty),
        v.y + w * ty + (z * tx - x * tz),
        v.z + w * tz + (x * ty - y * tx),
    )
}

private fun horizontalNormalized(v: Vector3, fallback: Vector3): Vector3 {
    val len = sqrt(v.x * v.x + v.z * v.z)
    return if (len > 1e-4f) Vector3(v.x / len, 0f, v.z / len) else fallback
}

private fun horizontalDistance(a: Vector3, b: Vector3): Float {
    val dx = a.x - b.x
    val dz = a.z - b.z
    return sqrt(dx * dx + dz * dz)
}

private fun lerp(a: Vector3, b: Vector3, t: Float): Vector3 =
    Vector3(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t,
        a.z + (b.z - a.z) * t,
    )

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun approach(current: Float, target: Float, delta: Float): Float {
    val diff = target - current
    return when {
        abs(diff) <= delta -> target
        diff > 0f -> current + delta
        else -> current - delta
    }
}

private fun yawDeg(dir: Vector3): Float =
    atan2(dir.x, dir.z).toDegrees()

private fun angleDeg(a: Vector3, b: Vector3): Float {
    val dot = (a.x * b.x + a.z * b.z).coerceIn(-1f, 1f)
    return acos(dot.toDouble()).toFloat().toDegrees()
}

private fun lerpAngle(current: Float, target: Float, t: Float): Float =
    current + normalizeDeg(target - current) * t.coerceIn(0f, 1f)

private fun normalizeDeg(value: Float): Float {
    var out = value
    while (out > 180f) out -= 360f
    while (out < -180f) out += 360f
    return out
}

private fun Float.toDegrees(): Float = this * 180f / PI.toFloat()
