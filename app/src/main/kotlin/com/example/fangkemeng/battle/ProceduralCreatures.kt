package com.example.fangkemeng.battle

import com.example.fangkemeng.model.CreatureId
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 战斗场景里使用的程序化动物实例。 */
internal interface BattleCreature {
    val root: Entity
    var skillPlacement: SkillPlacement
    val nominalHeightM: Float
    val nominalMaxDimensionM: Float
    val maxAllowedDimensionM: Float
    val joints: Map<String, SkillJoint>

    fun tick(dtSec: Float)

    /** 触发一次攻击演出，返回推荐演出时长（秒）。 */
    fun attack(): Float

    /** 伴随宠物专用：头部看向角度和起身/坐下跟随权重。普通战斗宠物忽略。 */
    fun setCompanionState(
        headYawDeg: Float?,
        headPitchDeg: Float?,
        moveWeight: Float,
        sitWeight: Float,
    ) = Unit
}

internal data class SkillPlacement(val position: P=v(),val scale: P=v(1,1,1),val yawDeg: Float=0f)
internal fun BattleCreature.placeForSkill(position: Vector3,scale: Float,yawDeg: Float) {
    skillPlacement=SkillPlacement(position.point(),v(scale,scale,scale),yawDeg)
    root.components.get(TransformComponent::class.java)?.apply {
        setPosition(position)
        setScaleVector(Vector3(scale,scale,scale))
        setEulerAngles(EulerAngles(0f,yawDeg,0f))
    }
}

/** 根据 [CreatureId] 生成程序化动物。 */
internal object ProceduralCreatureFactory {
    /** 合批构建：同一可动部件下的方块合并成一份网格，构造完成后立刻结束录制。 */
    fun create(creatureId: CreatureId): BattleCreature {
        VoxelBatch.begin("creature:${creatureId.name}")
        return try {
            val creature = when (creatureId) {
                CreatureId.SEED_CUB -> SeedCubCreature()
                CreatureId.VINE_CUB -> VineCubCreature()
                CreatureId.WATER_TURTLE -> WaterTurtleCreature()
                CreatureId.TIDE_TURTLE -> TideTurtleCreature()
                CreatureId.BIRD -> BirdCreature()
                CreatureId.FISH -> FishCreature()
                CreatureId.LION -> LionCreature()
                CreatureId.DRAGON -> DragonCreature()
                CreatureId.CAT -> CatCreature()
                CreatureId.GOLEM -> GolemCreature()
                CreatureId.SPARK_BUG -> SparkBugCreature()
                CreatureId.PUFF -> PuffCreature()
            }
            creature.bindJoints(requireNotNull(skillJointNames[creatureId]))
            creature
        } finally {
            VoxelBatch.end()
        }
    }
}

private data class Rot3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

private fun vec(x: Float, y: Float, z: Float) = Vector3(x, y, z)

private fun sinf(value: Float): Float = sin(value.toDouble()).toFloat()

private fun cosf(value: Float): Float = cos(value.toDouble()).toFloat()

/** 体素方块：[cornerRadius] 只分两档，实体与网格生成统一走 [voxelBox]。 */
private fun addBox(
    parent: Entity,
    size: Vector3,
    colorHex: String,
    pos: Vector3 = vec(0f, 0f, 0f),
    rot: Rot3 = Rot3(),
    cornerRadius: Float = 0.02f,
): Entity = voxelBox(
    parent = parent,
    size = size,
    colorHex = colorHex,
    pos = pos,
    rotX = rot.x,
    rotY = rot.y,
    rotZ = rot.z,
    rounded = cornerRadius >= 0.02f,
)

private fun addSphere(
    parent: Entity,
    radius: Float,
    colorHex: String,
    pos: Vector3 = vec(0f, 0f, 0f),
): Entity = voxelSphere(parent, radius, colorHex, pos)

private class PoseNode(
    val entity: Entity,
    val basePos: Vector3,
    val baseRot: Rot3 = Rot3(),
    val baseScale: Vector3 = vec(1f, 1f, 1f),
    val parent: Entity,
) {
    fun apply(
        dx: Float = 0f,
        dy: Float = 0f,
        dz: Float = 0f,
        rx: Float = 0f,
        ry: Float = 0f,
        rz: Float = 0f,
        sx: Float = 1f,
        sy: Float = 1f,
        sz: Float = 1f,
    ) {
        entity.components.get(TransformComponent::class.java)?.apply {
            setPosition(vec(basePos.x + dx, basePos.y + dy, basePos.z + dz))
            setEulerAngles(EulerAngles(baseRot.x + rx, baseRot.y + ry, baseRot.z + rz))
            setScaleVector(vec(baseScale.x * sx, baseScale.y * sy, baseScale.z * sz))
        }
    }
}

private abstract class BaseCreature(
    final override val nominalHeightM: Float,
    final override val nominalMaxDimensionM: Float,
    final override val maxAllowedDimensionM: Float,
    private val attackDurationSec: Float,
) : BattleCreature {
    final override val root: Entity = Entity()
    final override var skillPlacement=SkillPlacement()

    private var timeSec = 0f
    private var attackLeftSec = 0f
    private val jointOrder = ArrayList<PoseNode>()
    final override var joints: Map<String, SkillJoint> = emptyMap()
        private set

    fun bindJoints(names: List<String>) {
        check(names.size == jointOrder.size) { "Creature joint order does not match approved source" }
        joints = names.zip(jointOrder).associate { (name,node) ->
            name to SkillJoint(node.entity,node.basePos.point(),
                v(node.baseRot.x,node.baseRot.y,node.baseRot.z),node.baseScale.point(),
                names.getOrNull(jointOrder.indexOfFirst { it.entity == node.parent }))
        }
    }

    protected fun anchor(
        parent: Entity = root,
        pos: Vector3 = vec(0f, 0f, 0f),
        rot: Rot3 = Rot3(),
        scale: Vector3 = vec(1f, 1f, 1f),
    ): PoseNode {
        val e = Entity().apply {
            components.get(TransformComponent::class.java)?.apply {
                setPosition(pos)
                setEulerAngles(EulerAngles(rot.x, rot.y, rot.z))
                setScaleVector(scale)
            }
            parent.addChild(this)
        }
        return PoseNode(e, pos, rot, scale, parent).also { jointOrder.add(it) }
    }

    final override fun tick(dtSec: Float) {
        timeSec += dtSec
        if (attackLeftSec > 0f) {
            attackLeftSec = (attackLeftSec - dtSec).coerceAtLeast(0f)
        }
        val attackProgress = if (attackDurationSec > 0f && attackLeftSec > 0f) {
            1f - attackLeftSec / attackDurationSec
        } else {
            0f
        }
        val attackWeight = if (attackLeftSec > 0f) {
            sin((attackProgress * PI).toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        animate(timeSec, attackWeight, attackProgress)
    }

    final override fun attack(): Float {
        attackLeftSec = attackDurationSec
        return attackDurationSec
    }

    protected abstract fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float)
}

private class SeedCubCreature : BaseCreature(
    nominalHeightM = 0.72f,
    nominalMaxDimensionM = 0.86f,
    maxAllowedDimensionM = 0.7f,
    attackDurationSec = 0.82f,
) {
    private val body = anchor(pos = vec(0f, 0.28f, 0f))
    private val face = anchor(pos = vec(0f, 0.34f, 0.29f))
    private val bulb = anchor(pos = vec(0f, 0.53f, -0.08f), rot = Rot3(x = -4f))
    private val earL = anchor(pos = vec(-0.20f, 0.43f, 0.20f), rot = Rot3(z = -10f))
    private val earR = anchor(pos = vec(0.20f, 0.43f, 0.20f), rot = Rot3(z = 10f))
    private val legFL = anchor(pos = vec(-0.19f, 0.15f, 0.20f))
    private val legFR = anchor(pos = vec(0.19f, 0.15f, 0.20f))
    private val legBL = anchor(pos = vec(-0.19f, 0.15f, -0.18f))
    private val legBR = anchor(pos = vec(0.19f, 0.15f, -0.18f))

    init {
        addBox(body.entity, vec(0.50f, 0.32f, 0.58f), "0x69DFAEFF", cornerRadius = 0.06f)
        addBox(body.entity, vec(0.42f, 0.10f, 0.44f), "0x58C995FF", pos = vec(0f, 0.05f, -0.02f), cornerRadius = 0.04f)
        addBox(bulb.entity, vec(0.46f, 0.18f, 0.36f), "0x77DB6CFF", cornerRadius = 0.05f)
        listOf(
            vec(-0.095f, 0.11f, -0.075f),
            vec(0.095f, 0.11f, -0.075f),
            vec(-0.095f, 0.11f, 0.075f),
            vec(0.095f, 0.11f, 0.075f),
        ).forEach { p ->
            addBox(bulb.entity, vec(0.18f, 0.10f, 0.14f), "0x63C75AFF", pos = p, cornerRadius = 0.03f)
        }
        addBox(bulb.entity, vec(0.05f, 0.045f, 0.30f), "0x4FA94DFF", pos = vec(0f, 0.18f, 0f))
        addBox(bulb.entity, vec(0.38f, 0.045f, 0.045f), "0x4FA94DFF", pos = vec(0f, 0.18f, 0f))

        addBox(earL.entity, vec(0.12f, 0.12f, 0.12f), "0x67D7A9FF", cornerRadius = 0.035f)
        addBox(earR.entity, vec(0.12f, 0.12f, 0.12f), "0x67D7A9FF", cornerRadius = 0.035f)
        addBox(face.entity, vec(0.06f, 0.08f, 0.025f), "0xDE3157FF", pos = vec(-0.12f, 0.03f, 0.015f))
        addBox(face.entity, vec(0.06f, 0.08f, 0.025f), "0xDE3157FF", pos = vec(0.12f, 0.03f, 0.015f))
        addBox(face.entity, vec(0.025f, 0.04f, 0.03f), "0xFFFFFFFF", pos = vec(-0.125f, 0.045f, 0.032f))
        addBox(face.entity, vec(0.025f, 0.04f, 0.03f), "0xFFFFFFFF", pos = vec(0.115f, 0.045f, 0.032f))
        addBox(face.entity, vec(0.20f, 0.05f, 0.035f), "0xF5A4B5FF", pos = vec(0f, -0.095f, 0.03f), cornerRadius = 0.012f)
        addBox(face.entity, vec(0.055f, 0.035f, 0.04f), "0xE9F0E3FF", pos = vec(-0.08f, -0.072f, 0.052f), cornerRadius = 0.008f)
        addBox(face.entity, vec(0.055f, 0.035f, 0.04f), "0xE9F0E3FF", pos = vec(0.08f, -0.072f, 0.052f), cornerRadius = 0.008f)
        addBox(face.entity, vec(0.05f, 0.035f, 0.025f), "0x489B72FF", pos = vec(-0.045f, 0.15f, 0.02f), cornerRadius = 0.01f)
        addBox(face.entity, vec(0.05f, 0.035f, 0.025f), "0x489B72FF", pos = vec(0.045f, 0.15f, 0.02f), cornerRadius = 0.01f)

        listOf(legFL, legFR, legBL, legBR).forEach { leg ->
            addBox(leg.entity, vec(0.12f, 0.16f, 0.13f), "0x5AC99AFF", pos = vec(0f, -0.08f, 0f), cornerRadius = 0.035f)
            addBox(leg.entity, vec(0.10f, 0.04f, 0.14f), "0x47B183FF", pos = vec(0f, -0.16f, 0.03f), cornerRadius = 0.02f)
        }
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 2.8f) * 0.012f
        val walk = sinf(timeSec * 7.5f)
        body.apply(
            dy = bob,
            dz = attackWeight * 0.09f,
            rx = -attackWeight * 6f,
            sy = 1f + sinf(timeSec * 2.8f) * 0.025f,
        )
        face.apply(dy = bob * 0.5f, dz = attackWeight * 0.08f, rx = -attackWeight * 10f)
        bulb.apply(dy = bob * 0.4f, dz = -attackWeight * 0.02f, rx = sinf(timeSec * 2.2f) * 2f - attackWeight * 5f)
        earL.apply(rz = -10f + sinf(timeSec * 4f) * 2f - attackWeight * 6f)
        earR.apply(rz = 10f - sinf(timeSec * 4f) * 2f + attackWeight * 6f)
        legFL.apply(rx = walk * 5f + attackWeight * 10f)
        legBR.apply(rx = walk * 5f + attackWeight * 4f)
        legFR.apply(rx = -walk * 5f + attackWeight * 10f)
        legBL.apply(rx = -walk * 5f + attackWeight * 4f)
    }
}

private class WaterTurtleCreature : BaseCreature(
    nominalHeightM = 0.70f,
    nominalMaxDimensionM = 0.70f,
    maxAllowedDimensionM = 0.7f,
    attackDurationSec = 0.82f,
) {
    private val shell = anchor(pos = vec(0f, 0.32f, -0.10f), rot = Rot3(x = -3f))
    private val belly = anchor(pos = vec(0f, 0.30f, 0.06f))
    private val head = anchor(pos = vec(0f, 0.55f, 0.08f))
    private val armL = anchor(pos = vec(-0.28f, 0.38f, 0.06f), rot = Rot3(z = -12f))
    private val armR = anchor(pos = vec(0.28f, 0.38f, 0.06f), rot = Rot3(z = 12f))
    private val legL = anchor(pos = vec(-0.15f, 0.14f, 0.06f))
    private val legR = anchor(pos = vec(0.15f, 0.14f, 0.06f))
    private val tail = anchor(pos = vec(0f, 0.20f, -0.22f), rot = Rot3(x = 12f))

    init {
        addBox(shell.entity, vec(0.42f, 0.30f, 0.12f), "0xB8793EFF", cornerRadius = 0.06f)
        addBox(shell.entity, vec(0.34f, 0.08f, 0.09f), "0x9D5D2DFF", pos = vec(0f, 0.13f, 0f), cornerRadius = 0.035f)
        addBox(shell.entity, vec(0.05f, 0.05f, 0.09f), "0xE1C064FF", pos = vec(-0.12f, 0.19f, 0f))
        addBox(shell.entity, vec(0.05f, 0.05f, 0.09f), "0xE1C064FF", pos = vec(0.12f, 0.19f, 0f))
        addBox(shell.entity, vec(0.30f, 0.05f, 0.025f), "0xE1C064FF", pos = vec(0f, 0.19f, -0.035f))
        addBox(shell.entity, vec(0.30f, 0.05f, 0.025f), "0xE1C064FF", pos = vec(0f, 0.19f, 0.035f))

        addBox(belly.entity, vec(0.40f, 0.32f, 0.23f), "0x65CBE0FF", cornerRadius = 0.06f)
        addBox(belly.entity, vec(0.30f, 0.30f, 0.045f), "0xF4E8C2FF", pos = vec(0f, -0.005f, 0.18f), cornerRadius = 0.04f)
        addBox(belly.entity, vec(0.22f, 0.055f, 0.05f), "0xE6D49FFF", pos = vec(0f, 0.09f, 0.205f))
        addBox(belly.entity, vec(0.22f, 0.055f, 0.05f), "0xE6D49FFF", pos = vec(0f, -0.025f, 0.205f))
        addBox(belly.entity, vec(0.22f, 0.055f, 0.05f), "0xE6D49FFF", pos = vec(0f, -0.14f, 0.205f))

        addBox(head.entity, vec(0.26f, 0.24f, 0.22f), "0x66CFE5FF", cornerRadius = 0.06f)
        addBox(head.entity, vec(0.058f, 0.070f, 0.025f), "0x111111FF", pos = vec(-0.070f, 0.035f, 0.135f))
        addBox(head.entity, vec(0.058f, 0.070f, 0.025f), "0x111111FF", pos = vec(0.070f, 0.035f, 0.135f))
        addBox(head.entity, vec(0.030f, 0.032f, 0.03f), "0xFFFFFFFF", pos = vec(-0.080f, 0.055f, 0.155f))
        addBox(head.entity, vec(0.030f, 0.032f, 0.03f), "0xFFFFFFFF", pos = vec(0.058f, 0.055f, 0.155f))
        addBox(head.entity, vec(0.14f, 0.035f, 0.03f), "0x283B4AFF", pos = vec(0f, -0.065f, 0.15f), cornerRadius = 0.008f)

        listOf(armL, armR).forEach { arm ->
            addBox(arm.entity, vec(0.12f, 0.16f, 0.13f), "0x5EC4DDFF", cornerRadius = 0.035f)
            addBox(arm.entity, vec(0.10f, 0.04f, 0.12f), "0x4AB0CBFF", pos = vec(0f, -0.08f, 0.03f), cornerRadius = 0.018f)
        }
        listOf(legL, legR).forEach { leg ->
            addBox(leg.entity, vec(0.12f, 0.14f, 0.13f), "0x5EC4DDFF", cornerRadius = 0.035f)
            addBox(leg.entity, vec(0.10f, 0.04f, 0.12f), "0x4AB0CBFF", pos = vec(0f, -0.07f, 0.03f), cornerRadius = 0.018f)
        }
        addBox(tail.entity, vec(0.08f, 0.08f, 0.12f), "0x5EC4DDFF", cornerRadius = 0.03f)
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 2.5f) * 0.012f
        val wave = sinf(timeSec * 6.8f)
        shell.apply(dy = bob, dz = -attackWeight * 0.01f, rx = -3f - attackWeight * 4f)
        belly.apply(dy = bob, dz = attackWeight * 0.08f, rx = -attackWeight * 5f)
        head.apply(dy = bob * 0.6f, dz = attackWeight * 0.14f, rx = -attackWeight * 12f + sinf(timeSec * 2f) * 2f)
        armL.apply(rz = -12f + wave * 6f - attackWeight * 16f, dz = attackWeight * 0.04f)
        armR.apply(rz = 12f - wave * 6f + attackWeight * 16f, dz = attackWeight * 0.04f)
        legL.apply(rx = -wave * 4f + attackWeight * 5f)
        legR.apply(rx = wave * 4f + attackWeight * 5f)
        tail.apply(ry = sinf(timeSec * 5f) * 10f)
    }
}

private class BirdCreature : BaseCreature(
    nominalHeightM = 0.9f,
    nominalMaxDimensionM = 0.9f,
    maxAllowedDimensionM = 0.7f,
    attackDurationSec = 0.82f,
) {
    private val body = anchor(pos = vec(0f, 0.30f, 0f))
    private val head = anchor(pos = vec(0f, 0.47f, 0.13f))
    private val wingL = anchor(pos = vec(-0.17f, 0.30f, -0.02f), rot = Rot3(z = -18f))
    private val wingR = anchor(pos = vec(0.17f, 0.30f, -0.02f), rot = Rot3(z = 18f))
    private val tail = anchor(pos = vec(0f, 0.28f, -0.16f), rot = Rot3(x = -8f))
    private val legL = anchor(pos = vec(-0.06f, 0.10f, 0.03f))
    private val legR = anchor(pos = vec(0.06f, 0.10f, 0.03f))

    init {
        addSphere(body.entity, 0.18f, "0xF3C96BFF")
        addSphere(head.entity, 0.12f, "0xFFF0B8FF")
        addBox(head.entity, vec(0.05f, 0.05f, 0.10f), "0xE87D1FFF", pos = vec(0f, -0.01f, 0.12f))
        addSphere(head.entity, 0.018f, "0x111111FF", pos = vec(-0.04f, 0.02f, 0.08f))
        addSphere(head.entity, 0.018f, "0x111111FF", pos = vec(0.04f, 0.02f, 0.08f))
        addBox(wingL.entity, vec(0.05f, 0.15f, 0.21f), "0xD98D3EFF")
        addBox(wingR.entity, vec(0.05f, 0.15f, 0.21f), "0xD98D3EFF")
        addBox(tail.entity, vec(0.09f, 0.03f, 0.12f), "0xA26A24FF", rot = Rot3(x = 10f))
        addBox(legL.entity, vec(0.03f, 0.16f, 0.03f), "0x6B4A1BFF", pos = vec(0f, -0.08f, 0f))
        addBox(legR.entity, vec(0.03f, 0.16f, 0.03f), "0x6B4A1BFF", pos = vec(0f, -0.08f, 0f))
        addBox(legL.entity, vec(0.10f, 0.02f, 0.04f), "0x6B4A1BFF", pos = vec(0f, -0.16f, 0.03f))
        addBox(legR.entity, vec(0.10f, 0.02f, 0.04f), "0x6B4A1BFF", pos = vec(0f, -0.16f, 0.03f))
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 3.2f) * 0.018f
        body.apply(dy = bob, rz = sinf(timeSec * 1.8f) * 3f, dz = attackWeight * 0.04f)
        head.apply(
            dy = bob * 0.4f,
            dz = attackWeight * 0.11f,
            rx = -sinf(timeSec * 2.3f) * 5f - attackWeight * 20f,
        )
        wingL.apply(rz = -sinf(timeSec * 10f) * (7f + attackWeight * 10f) - attackWeight * 18f)
        wingR.apply(rz = sinf(timeSec * 10f) * (7f + attackWeight * 10f) + attackWeight * 18f)
        tail.apply(rx = sinf(timeSec * 5.5f) * 9f + attackWeight * 9f)
        legL.apply(rx = sinf(timeSec * 4f) * 5f)
        legR.apply(rx = -sinf(timeSec * 4f) * 5f)
    }
}

private class FishCreature : BaseCreature(
    nominalHeightM = 0.82f,
    nominalMaxDimensionM = 0.82f,
    maxAllowedDimensionM = 0.7f,
    attackDurationSec = 0.78f,
) {
    private val body = anchor(pos = vec(0f, 0.28f, 0f))
    private val tailBase = anchor(pos = vec(0f, 0.28f, -0.18f))
    private val tailTip = anchor(parent = tailBase.entity, pos = vec(0f, 0f, -0.12f))
    private val finL = anchor(pos = vec(-0.13f, 0.26f, -0.02f), rot = Rot3(z = -22f))
    private val finR = anchor(pos = vec(0.13f, 0.26f, -0.02f), rot = Rot3(z = 22f))
    private val topFin = anchor(pos = vec(0f, 0.39f, -0.02f), rot = Rot3(x = 8f))

    init {
        addBox(body.entity, vec(0.22f, 0.16f, 0.34f), "0x4BD3DBFF", cornerRadius = 0.05f)
        addSphere(body.entity, 0.11f, "0x6CEBFFFF", pos = vec(0f, 0f, 0.16f))
        addSphere(body.entity, 0.02f, "0x13273FFF", pos = vec(-0.05f, 0.02f, 0.22f))
        addSphere(body.entity, 0.02f, "0x13273FFF", pos = vec(0.05f, 0.02f, 0.22f))
        addBox(body.entity, vec(0.05f, 0.05f, 0.06f), "0xA6F4FFFF", pos = vec(0f, -0.02f, 0.26f))
        addBox(tailBase.entity, vec(0.10f, 0.12f, 0.14f), "0x2A9BC8FF", rot = Rot3(y = 12f))
        addBox(tailTip.entity, vec(0.18f, 0.14f, 0.05f), "0x205F95FF", rot = Rot3(y = 18f))
        addBox(finL.entity, vec(0.04f, 0.12f, 0.16f), "0x235E8DFF")
        addBox(finR.entity, vec(0.04f, 0.12f, 0.16f), "0x235E8DFF")
        addBox(topFin.entity, vec(0.08f, 0.10f, 0.12f), "0x235E8DFF")
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 2.6f) * 0.03f
        val swim = sinf(timeSec * 8f)
        body.apply(dy = bob, ry = swim * 4f, dz = attackWeight * 0.12f)
        tailBase.apply(dy = bob, ry = -swim * (18f + attackWeight * 10f))
        tailTip.apply(ry = -swim * (26f + attackWeight * 12f))
        finL.apply(rz = -sinf(timeSec * 10f) * 16f - attackWeight * 10f)
        finR.apply(rz = sinf(timeSec * 10f) * 16f + attackWeight * 10f)
        topFin.apply(rx = cosf(timeSec * 6f) * 7f)
    }
}

private class LionCreature : BaseCreature(
    nominalHeightM = 0.98f,
    nominalMaxDimensionM = 0.98f,
    maxAllowedDimensionM = 1.0f,
    attackDurationSec = 0.92f,
) {
    private val body = anchor(pos = vec(0f, 0.28f, 0f))
    private val head = anchor(pos = vec(0f, 0.48f, 0.20f))
    private val tail = anchor(pos = vec(0f, 0.35f, -0.22f), rot = Rot3(x = 18f))
    private val frontLegL = anchor(pos = vec(-0.10f, 0.15f, 0.14f))
    private val frontLegR = anchor(pos = vec(0.10f, 0.15f, 0.14f))
    private val backLegL = anchor(pos = vec(-0.10f, 0.15f, -0.12f))
    private val backLegR = anchor(pos = vec(0.10f, 0.15f, -0.12f))

    init {
        addBox(body.entity, vec(0.28f, 0.20f, 0.42f), "0xD7A35FFF", cornerRadius = 0.05f)
        addBox(body.entity, vec(0.20f, 0.10f, 0.16f), "0xF7E0A5FF", pos = vec(0f, -0.03f, 0.12f))
        addBox(head.entity, vec(0.28f, 0.28f, 0.24f), "0x8C5422FF", cornerRadius = 0.05f)
        addBox(head.entity, vec(0.18f, 0.18f, 0.18f), "0xE0B776FF", pos = vec(0f, 0f, 0.04f), cornerRadius = 0.04f)
        addBox(head.entity, vec(0.12f, 0.08f, 0.09f), "0xF4E7C1FF", pos = vec(0f, -0.05f, 0.14f), cornerRadius = 0.03f)
        addSphere(head.entity, 0.018f, "0x111111FF", pos = vec(-0.05f, 0.03f, 0.16f))
        addSphere(head.entity, 0.018f, "0x111111FF", pos = vec(0.05f, 0.03f, 0.16f))
        addBox(head.entity, vec(0.05f, 0.09f, 0.05f), "0xE0B776FF", pos = vec(-0.09f, 0.12f, 0.02f), rot = Rot3(z = -14f))
        addBox(head.entity, vec(0.05f, 0.09f, 0.05f), "0xE0B776FF", pos = vec(0.09f, 0.12f, 0.02f), rot = Rot3(z = 14f))
        addBox(tail.entity, vec(0.04f, 0.04f, 0.20f), "0xB47B38FF")
        addSphere(tail.entity, 0.035f, "0x7A4319FF", pos = vec(0f, 0f, -0.13f))
        listOf(frontLegL, frontLegR, backLegL, backLegR).forEach { leg ->
            addBox(leg.entity, vec(0.06f, 0.20f, 0.06f), "0xC18A48FF", pos = vec(0f, -0.10f, 0f))
            addBox(leg.entity, vec(0.08f, 0.04f, 0.10f), "0x9E6931FF", pos = vec(0f, -0.20f, 0.02f))
        }
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val breath = 1f + sinf(timeSec * 2.2f) * 0.04f
        body.apply(dy = sinf(timeSec * 2.2f) * 0.01f, sx = breath, sy = 1f, sz = breath)
        head.apply(
            dy = sinf(timeSec * 2.2f) * 0.01f,
            dz = attackWeight * 0.12f,
            rx = -sinf(timeSec * 1.8f) * 3f - attackWeight * 10f,
        )
        tail.apply(ry = sinf(timeSec * 4.5f) * 18f + attackWeight * 12f)
        frontLegL.apply(rx = -attackWeight * 22f + sinf(timeSec * 3.2f) * 3f)
        frontLegR.apply(rx = -attackWeight * 18f - sinf(timeSec * 3.2f) * 3f)
        backLegL.apply(rx = attackWeight * 10f)
        backLegR.apply(rx = attackWeight * 8f)
    }
}

private class DragonCreature : BaseCreature(
    nominalHeightM = 1.05f,
    nominalMaxDimensionM = 1.05f,
    maxAllowedDimensionM = 1.0f,
    attackDurationSec = 0.96f,
) {
    private val body = anchor(pos = vec(0f, 0.30f, 0f))
    private val neck = anchor(pos = vec(0f, 0.44f, 0.18f))
    private val jaw = anchor(parent = neck.entity, pos = vec(0f, -0.04f, 0.13f))
    private val wingL = anchor(pos = vec(-0.18f, 0.40f, -0.02f), rot = Rot3(z = -22f))
    private val wingR = anchor(pos = vec(0.18f, 0.40f, -0.02f), rot = Rot3(z = 22f))
    private val tailBase = anchor(pos = vec(0f, 0.32f, -0.20f), rot = Rot3(x = 10f))
    private val tailTip = anchor(parent = tailBase.entity, pos = vec(0f, 0f, -0.16f))
    private val foreLegL = anchor(pos = vec(-0.11f, 0.16f, 0.10f))
    private val foreLegR = anchor(pos = vec(0.11f, 0.16f, 0.10f))

    init {
        addBox(body.entity, vec(0.26f, 0.18f, 0.40f), "0xE9743FFF", cornerRadius = 0.05f)
        addBox(body.entity, vec(0.18f, 0.08f, 0.24f), "0xFFD79AFF", pos = vec(0f, -0.03f, 0.06f))
        addBox(neck.entity, vec(0.18f, 0.14f, 0.24f), "0xF0894BFF", cornerRadius = 0.04f)
        addBox(neck.entity, vec(0.10f, 0.06f, 0.11f), "0xFFD79AFF", pos = vec(0f, -0.03f, 0.13f), cornerRadius = 0.03f)
        addSphere(neck.entity, 0.018f, "0x111111FF", pos = vec(-0.04f, 0.03f, 0.16f))
        addSphere(neck.entity, 0.018f, "0x111111FF", pos = vec(0.04f, 0.03f, 0.16f))
        addBox(neck.entity, vec(0.04f, 0.10f, 0.04f), "0xFFC93CFF", pos = vec(-0.06f, 0.10f, 0.04f), rot = Rot3(z = -18f))
        addBox(neck.entity, vec(0.04f, 0.10f, 0.04f), "0xFFC93CFF", pos = vec(0.06f, 0.10f, 0.04f), rot = Rot3(z = 18f))
        addBox(jaw.entity, vec(0.10f, 0.03f, 0.12f), "0xFFB86BFF", pos = vec(0f, -0.02f, 0.02f))
        addBox(wingL.entity, vec(0.04f, 0.20f, 0.26f), "0xB8482AFF")
        addBox(wingR.entity, vec(0.04f, 0.20f, 0.26f), "0xB8482AFF")
        addBox(tailBase.entity, vec(0.06f, 0.06f, 0.22f), "0xB8482AFF")
        addBox(tailTip.entity, vec(0.08f, 0.08f, 0.16f), "0x8C3520FF")
        listOf(foreLegL, foreLegR).forEach { leg ->
            addBox(leg.entity, vec(0.06f, 0.20f, 0.06f), "0xD3602FFF", pos = vec(0f, -0.10f, 0f))
            addBox(leg.entity, vec(0.08f, 0.04f, 0.10f), "0xA5441FFF", pos = vec(0f, -0.20f, 0.02f))
        }
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val flap = sinf(timeSec * 4.4f)
        body.apply(dy = sinf(timeSec * 2.4f) * 0.012f, dz = attackWeight * 0.05f)
        neck.apply(dz = attackWeight * 0.14f, rx = -attackWeight * 16f + sinf(timeSec * 1.8f) * 3f)
        jaw.apply(rx = attackWeight * 24f)
        wingL.apply(rz = -flap * (12f + attackWeight * 10f) - attackWeight * 14f)
        wingR.apply(rz = flap * (12f + attackWeight * 10f) + attackWeight * 14f)
        tailBase.apply(ry = sinf(timeSec * 4.2f) * 14f)
        tailTip.apply(ry = sinf(timeSec * 5.4f) * 22f)
        foreLegL.apply(rx = -attackWeight * 14f)
        foreLegR.apply(rx = -attackWeight * 12f)
    }
}

private class CatCreature : BaseCreature(
    nominalHeightM = 0.86f,
    nominalMaxDimensionM = 0.86f,
    maxAllowedDimensionM = 0.7f,
    attackDurationSec = 0.84f,
) {
    private val body = anchor(pos = vec(0f, 0.26f, 0f))
    private val head = anchor(pos = vec(0f, 0.43f, 0.18f))
    private val tailBase = anchor(pos = vec(0f, 0.34f, -0.20f), rot = Rot3(x = 24f))
    private val tailTip = anchor(parent = tailBase.entity, pos = vec(0f, 0f, -0.14f))
    private val pawL = anchor(pos = vec(-0.09f, 0.14f, 0.12f))
    private val pawR = anchor(pos = vec(0.09f, 0.14f, 0.12f))
    private val hindLegL = anchor(pos = vec(-0.09f, 0.14f, -0.12f))
    private val hindLegR = anchor(pos = vec(0.09f, 0.14f, -0.12f))
    private var companionHeadYawDeg: Float? = null
    private var companionHeadPitchDeg: Float? = null
    private var companionMoveWeight: Float = 0f
    private var companionSitWeight: Float = 0f

    init {
        addBox(body.entity, vec(0.24f, 0.16f, 0.38f), "0x8FA4B9FF", cornerRadius = 0.05f)
        addBox(body.entity, vec(0.14f, 0.08f, 0.16f), "0xD9E1ECFF", pos = vec(0f, -0.03f, 0.10f))
        addBox(head.entity, vec(0.18f, 0.16f, 0.16f), "0xA7BBCEFF", cornerRadius = 0.04f)
        addBox(head.entity, vec(0.04f, 0.07f, 0.04f), "0x61788FFF", pos = vec(-0.05f, 0.09f, 0f), rot = Rot3(z = -18f))
        addBox(head.entity, vec(0.04f, 0.07f, 0.04f), "0x61788FFF", pos = vec(0.05f, 0.09f, 0f), rot = Rot3(z = 18f))
        addSphere(head.entity, 0.016f, "0x111111FF", pos = vec(-0.04f, 0.03f, 0.10f))
        addSphere(head.entity, 0.016f, "0x111111FF", pos = vec(0.04f, 0.03f, 0.10f))
        addBox(head.entity, vec(0.08f, 0.04f, 0.06f), "0xF7D7DBFF", pos = vec(0f, -0.02f, 0.12f))
        addBox(tailBase.entity, vec(0.04f, 0.04f, 0.18f), "0x7C90A7FF")
        addBox(tailTip.entity, vec(0.04f, 0.04f, 0.14f), "0x61788FFF")
        listOf(pawL, pawR, hindLegL, hindLegR).forEach { leg ->
            addBox(leg.entity, vec(0.05f, 0.18f, 0.05f), "0x7C90A7FF", pos = vec(0f, -0.09f, 0f))
            addBox(leg.entity, vec(0.07f, 0.04f, 0.08f), "0x61788FFF", pos = vec(0f, -0.18f, 0.02f))
        }
    }

    override fun setCompanionState(
        headYawDeg: Float?,
        headPitchDeg: Float?,
        moveWeight: Float,
        sitWeight: Float,
    ) {
        companionHeadYawDeg = headYawDeg?.coerceIn(-150f, 150f)
        // 本地约定：负 X 方向是抬头；最大抬头 75 度，避免头部翻折。
        companionHeadPitchDeg = headPitchDeg?.coerceIn(-75f, 35f)
        companionMoveWeight = moveWeight.coerceIn(0f, 1f)
        companionSitWeight = sitWeight.coerceIn(0f, 1f)
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val crouch = 1f - attackWeight * 0.08f
        val walk = sinf(timeSec * 10f)
        val standLift = companionMoveWeight * 0.05f
        val sit = companionSitWeight
        body.apply(
            dy = sinf(timeSec * 2.5f) * 0.01f + standLift - sit * 0.075f,
            rx = -sit * 5f,
            sy = crouch + companionMoveWeight * 0.04f - sit * 0.05f,
            dz = attackWeight * 0.03f,
        )
        head.apply(
            dy = standLift - sit * 0.035f,
            dz = attackWeight * 0.08f,
            rx = -attackWeight * 10f + sinf(timeSec * 2f) * 3f + (companionHeadPitchDeg ?: 0f),
            ry = companionHeadYawDeg ?: 0f,
        )
        tailBase.apply(ry = sinf(timeSec * 4.8f) * 18f + attackWeight * 14f, rx = -attackWeight * 6f - sit * 16f)
        tailTip.apply(ry = sinf(timeSec * 6.2f) * 22f, rx = -sit * 12f)
        pawL.apply(rx = -attackWeight * 28f + walk * 18f * companionMoveWeight - sit * 8f, dz = attackWeight * 0.08f + sit * 0.02f)
        pawR.apply(rx = -attackWeight * 10f - walk * 18f * companionMoveWeight - sit * 8f, dz = sit * 0.02f)
        hindLegL.apply(
            dy = -sit * 0.04f,
            dz = sit * 0.055f - attackWeight * 0.02f,
            rx = -walk * 14f * companionMoveWeight + sit * 68f,
        )
        hindLegR.apply(
            dy = -sit * 0.04f,
            dz = sit * 0.055f - attackWeight * 0.02f,
            rx = walk * 14f * companionMoveWeight + sit * 68f,
        )
    }
}

/* ─────────────────────── 扩展精灵（v2 图鉴） ─────────────────────── */

/** 藤甲兽：种子兽进化型，体型更大，头顶三根藤冠。 */
private class VineCubCreature : BaseCreature(
    nominalHeightM = 1.02f,
    nominalMaxDimensionM = 1.15f,
    maxAllowedDimensionM = 0.95f,
    attackDurationSec = 0.86f,
) {
    private val body = anchor(pos = vec(0f, 0.38f, 0f))
    private val face = anchor(pos = vec(0f, 0.46f, 0.39f))
    private val crown = anchor(pos = vec(0f, 0.72f, -0.10f))
    private val legL = anchor(pos = vec(-0.25f, 0.20f, 0.26f))
    private val legR = anchor(pos = vec(0.25f, 0.20f, 0.26f))
    private val legBL = anchor(pos = vec(-0.25f, 0.20f, -0.24f))
    private val legBR = anchor(pos = vec(0.25f, 0.20f, -0.24f))

    init {
        addBox(body.entity, vec(0.66f, 0.42f, 0.76f), "0x5ACB8FFF", cornerRadius = 0.08f)
        addBox(body.entity, vec(0.56f, 0.12f, 0.58f), "0x49B57AFF", pos = vec(0f, 0.07f, -0.02f), cornerRadius = 0.05f)
        addBox(crown.entity, vec(0.60f, 0.22f, 0.46f), "0x3FA85FFF", cornerRadius = 0.06f)
        listOf(-0.22f, 0f, 0.22f).forEach { x ->
            addBox(crown.entity, vec(0.12f, 0.34f, 0.12f), "0x2F8B4CFF", pos = vec(x, 0.24f, 0f), cornerRadius = 0.03f)
            addBox(crown.entity, vec(0.16f, 0.08f, 0.16f), "0x6BE07FFF", pos = vec(x, 0.40f, 0f), cornerRadius = 0.03f)
        }
        addBox(face.entity, vec(0.07f, 0.10f, 0.03f), "0xDE3157FF", pos = vec(-0.15f, 0.04f, 0.02f))
        addBox(face.entity, vec(0.07f, 0.10f, 0.03f), "0xDE3157FF", pos = vec(0.15f, 0.04f, 0.02f))
        addBox(face.entity, vec(0.03f, 0.05f, 0.035f), "0xFFFFFFFF", pos = vec(-0.155f, 0.055f, 0.04f))
        addBox(face.entity, vec(0.03f, 0.05f, 0.035f), "0xFFFFFFFF", pos = vec(0.145f, 0.055f, 0.04f))
        addBox(face.entity, vec(0.26f, 0.06f, 0.04f), "0xF5A4B5FF", pos = vec(0f, -0.11f, 0.04f), cornerRadius = 0.015f)
        listOf(legL, legR, legBL, legBR).forEach { leg ->
            addBox(leg.entity, vec(0.15f, 0.20f, 0.16f), "0x4CBB80FF", pos = vec(0f, -0.10f, 0f), cornerRadius = 0.04f)
            addBox(leg.entity, vec(0.13f, 0.05f, 0.17f), "0x3EA36AFF", pos = vec(0f, -0.20f, 0.03f), cornerRadius = 0.02f)
        }
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 2.4f) * 0.014f
        val walk = sinf(timeSec * 7f)
        body.apply(dy = bob, dz = attackWeight * 0.10f, rx = -attackWeight * 6f, sy = 1f + sinf(timeSec * 2.4f) * 0.02f)
        face.apply(dy = bob * 0.6f, dz = attackWeight * 0.09f, rx = -attackWeight * 10f)
        crown.apply(dy = bob * 0.4f, rz = sinf(timeSec * 1.8f) * 3f - attackWeight * 6f)
        legL.apply(rx = walk * 6f + attackWeight * 9f)
        legBR.apply(rx = walk * 6f + attackWeight * 4f)
        legR.apply(rx = -walk * 6f + attackWeight * 9f)
        legBL.apply(rx = -walk * 6f + attackWeight * 4f)
    }
}

/** 潮甲龟：水龟进化型，更大甲壳与头冠鳍。 */
private class TideTurtleCreature : BaseCreature(
    nominalHeightM = 1.00f,
    nominalMaxDimensionM = 1.05f,
    maxAllowedDimensionM = 0.95f,
    attackDurationSec = 0.9f,
) {
    private val shell = anchor(pos = vec(0f, 0.44f, -0.12f))
    private val belly = anchor(pos = vec(0f, 0.40f, 0.10f))
    private val head = anchor(pos = vec(0f, 0.74f, 0.12f))
    private val finL = anchor(pos = vec(-0.42f, 0.30f, 0.02f))
    private val finR = anchor(pos = vec(0.42f, 0.30f, 0.02f))

    init {
        addBox(shell.entity, vec(0.86f, 0.46f, 0.96f), "0x2E7FC2FF", cornerRadius = 0.10f)
        addBox(shell.entity, vec(0.70f, 0.14f, 0.80f), "0x1F5F9CFF", pos = vec(0f, 0.26f, 0f), cornerRadius = 0.06f)
        addBox(shell.entity, vec(0.24f, 0.10f, 0.24f), "0x3E96DCFF", pos = vec(0f, 0.32f, 0f), cornerRadius = 0.05f)
        addBox(belly.entity, vec(0.62f, 0.34f, 0.34f), "0xF2E4C4FF", pos = vec(0f, 0f, 0.10f), cornerRadius = 0.09f)
        addBox(head.entity, vec(0.36f, 0.30f, 0.34f), "0x4FB2E8FF", cornerRadius = 0.07f)
        addBox(head.entity, vec(0.30f, 0.10f, 0.10f), "0x2E7FC2FF", pos = vec(0f, 0.20f, 0f), cornerRadius = 0.03f)
        addBox(head.entity, vec(0.07f, 0.09f, 0.03f), "0x14161CFF", pos = vec(-0.11f, 0.02f, 0.18f))
        addBox(head.entity, vec(0.07f, 0.09f, 0.03f), "0x14161CFF", pos = vec(0.11f, 0.02f, 0.18f))
        addBox(head.entity, vec(0.16f, 0.05f, 0.03f), "0xF5A4B5FF", pos = vec(0f, -0.10f, 0.19f), cornerRadius = 0.012f)
        addBox(finL.entity, vec(0.26f, 0.12f, 0.34f), "0x3E96DCFF", rot = Rot3(z = -18f), cornerRadius = 0.05f)
        addBox(finR.entity, vec(0.26f, 0.12f, 0.34f), "0x3E96DCFF", rot = Rot3(z = 18f), cornerRadius = 0.05f)
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 2.2f) * 0.016f
        shell.apply(dy = bob, dz = attackWeight * 0.08f, rx = -attackWeight * 5f)
        belly.apply(dy = bob, dz = attackWeight * 0.10f)
        head.apply(dy = bob * 0.8f, dz = attackWeight * 0.12f, rx = -attackWeight * 9f, ry = sinf(timeSec * 1.6f) * 4f)
        finL.apply(rx = sinf(timeSec * 4.4f) * 8f - attackWeight * 22f, rz = -18f)
        finR.apply(rx = sinf(timeSec * 4.4f + 0.6f) * 8f - attackWeight * 22f, rz = 18f)
    }
}

/** 岩块兽：三层岩块堆叠，短手臂，重心低。 */
private class GolemCreature : BaseCreature(
    nominalHeightM = 0.94f,
    nominalMaxDimensionM = 0.95f,
    maxAllowedDimensionM = 0.9f,
    attackDurationSec = 0.95f,
) {
    private val legs = anchor(pos = vec(0f, 0.16f, 0f))
    private val torso = anchor(pos = vec(0f, 0.46f, 0f))
    private val head = anchor(pos = vec(0f, 0.80f, 0.02f))
    private val armL = anchor(pos = vec(-0.38f, 0.50f, 0f))
    private val armR = anchor(pos = vec(0.38f, 0.50f, 0f))

    init {
        addBox(legs.entity, vec(0.30f, 0.30f, 0.30f), "0x8A8378FF", pos = vec(-0.18f, 0f, 0f), cornerRadius = 0.05f)
        addBox(legs.entity, vec(0.30f, 0.30f, 0.30f), "0x8A8378FF", pos = vec(0.18f, 0f, 0f), cornerRadius = 0.05f)
        addBox(torso.entity, vec(0.62f, 0.40f, 0.50f), "0x9E9789FF", cornerRadius = 0.08f)
        addBox(torso.entity, vec(0.46f, 0.16f, 0.40f), "0xB4AC9CFF", pos = vec(0f, 0.16f, 0.04f), cornerRadius = 0.05f)
        addBox(head.entity, vec(0.40f, 0.32f, 0.38f), "0xAAA394FF", cornerRadius = 0.07f)
        addBox(head.entity, vec(0.08f, 0.10f, 0.03f), "0xE9A23BFF", pos = vec(-0.10f, 0.03f, 0.20f))
        addBox(head.entity, vec(0.08f, 0.10f, 0.03f), "0xE9A23BFF", pos = vec(0.10f, 0.03f, 0.20f))
        addBox(armL.entity, vec(0.20f, 0.34f, 0.22f), "0x8F887BFF", pos = vec(0f, -0.14f, 0f), cornerRadius = 0.04f)
        addBox(armR.entity, vec(0.20f, 0.34f, 0.22f), "0x8F887BFF", pos = vec(0f, -0.14f, 0f), cornerRadius = 0.04f)
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 1.8f) * 0.010f
        torso.apply(dy = bob, dz = attackWeight * 0.06f, rx = -attackWeight * 4f)
        head.apply(dy = bob * 0.7f, dz = attackWeight * 0.10f, rx = -attackWeight * 7f, ry = sinf(timeSec * 1.2f) * 3f)
        armL.apply(rx = sinf(timeSec * 3.4f) * 4f - attackWeight * 26f)
        armR.apply(rx = sinf(timeSec * 3.4f + 0.8f) * 4f - attackWeight * 18f)
    }
}

/** 电光虫：小体型、四片翅膀、两根天线。 */
private class SparkBugCreature : BaseCreature(
    nominalHeightM = 0.52f,
    nominalMaxDimensionM = 0.62f,
    maxAllowedDimensionM = 0.55f,
    attackDurationSec = 0.7f,
) {
    private val body = anchor(pos = vec(0f, 0.26f, 0f))
    private val head = anchor(pos = vec(0f, 0.34f, 0.22f))
    private val wingL = anchor(pos = vec(-0.18f, 0.40f, -0.02f))
    private val wingR = anchor(pos = vec(0.18f, 0.40f, -0.02f))
    private val antennaL = anchor(pos = vec(-0.08f, 0.48f, 0.18f))
    private val antennaR = anchor(pos = vec(0.08f, 0.48f, 0.18f))

    init {
        addBox(body.entity, vec(0.30f, 0.26f, 0.40f), "0xF2C744FF", cornerRadius = 0.05f)
        addBox(body.entity, vec(0.24f, 0.08f, 0.32f), "0xD9A72CFF", pos = vec(0f, 0.14f, 0f), cornerRadius = 0.03f)
        addBox(head.entity, vec(0.22f, 0.20f, 0.20f), "0xFFE07AFF", cornerRadius = 0.04f)
        addBox(head.entity, vec(0.05f, 0.06f, 0.03f), "0x1B1C22FF", pos = vec(-0.06f, 0.02f, 0.11f))
        addBox(head.entity, vec(0.05f, 0.06f, 0.03f), "0x1B1C22FF", pos = vec(0.06f, 0.02f, 0.11f))
        addBox(wingL.entity, vec(0.20f, 0.03f, 0.28f), "0xFFFFFFCC", rot = Rot3(z = -14f), cornerRadius = 0.02f)
        addBox(wingR.entity, vec(0.20f, 0.03f, 0.28f), "0xFFFFFFCC", rot = Rot3(z = 14f), cornerRadius = 0.02f)
        addBox(antennaL.entity, vec(0.03f, 0.16f, 0.03f), "0x8A6B14FF", cornerRadius = 0.01f)
        addBox(antennaR.entity, vec(0.03f, 0.16f, 0.03f), "0x8A6B14FF", cornerRadius = 0.01f)
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val hover = sinf(timeSec * 5.2f) * 0.012f
        val flutter = sinf(timeSec * 18f)
        body.apply(dy = hover, dz = attackWeight * 0.08f)
        head.apply(dy = hover, dz = attackWeight * 0.10f, ry = sinf(timeSec * 2.6f) * 6f)
        wingL.apply(rz = -14f - flutter * 22f - attackWeight * 10f)
        wingR.apply(rz = 14f + flutter * 22f + attackWeight * 10f)
        antennaL.apply(rx = sinf(timeSec * 6f) * 6f)
        antennaR.apply(rx = sinf(timeSec * 6f + 0.7f) * 6f)
    }
}

/** 云绒兽：叠块云团，两只短耳，动作轻飘。 */
private class PuffCreature : BaseCreature(
    nominalHeightM = 0.68f,
    nominalMaxDimensionM = 0.72f,
    maxAllowedDimensionM = 0.65f,
    attackDurationSec = 0.78f,
) {
    private val body = anchor(pos = vec(0f, 0.32f, 0f))
    private val head = anchor(pos = vec(0f, 0.56f, 0.06f))
    private val earL = anchor(pos = vec(-0.14f, 0.72f, 0.04f), rot = Rot3(z = -8f))
    private val earR = anchor(pos = vec(0.14f, 0.72f, 0.04f), rot = Rot3(z = 8f))
    private val tail = anchor(pos = vec(0f, 0.30f, -0.28f))

    init {
        addBox(body.entity, vec(0.52f, 0.34f, 0.48f), "0xF4EFE2FF", cornerRadius = 0.12f)
        addBox(body.entity, vec(0.38f, 0.20f, 0.36f), "0xFFFDF6FF", pos = vec(0f, 0.06f, 0.04f), cornerRadius = 0.08f)
        addBox(head.entity, vec(0.44f, 0.32f, 0.40f), "0xFBF6EAFF", cornerRadius = 0.10f)
        addBox(head.entity, vec(0.06f, 0.07f, 0.03f), "0x2B2C33FF", pos = vec(-0.10f, 0.02f, 0.20f))
        addBox(head.entity, vec(0.06f, 0.07f, 0.03f), "0x2B2C33FF", pos = vec(0.10f, 0.02f, 0.20f))
        addBox(head.entity, vec(0.12f, 0.04f, 0.03f), "0xE7A2AEFF", pos = vec(0f, -0.09f, 0.21f), cornerRadius = 0.01f)
        addBox(earL.entity, vec(0.10f, 0.14f, 0.10f), "0xF1E7D4FF", cornerRadius = 0.04f)
        addBox(earR.entity, vec(0.10f, 0.14f, 0.10f), "0xF1E7D4FF", cornerRadius = 0.04f)
        addBox(tail.entity, vec(0.16f, 0.16f, 0.16f), "0xFBF6EAFF", cornerRadius = 0.06f)
    }

    override fun animate(timeSec: Float, attackWeight: Float, attackProgress: Float) {
        val bob = sinf(timeSec * 2.0f) * 0.018f
        body.apply(dy = bob, dz = attackWeight * 0.09f, sy = 1f + sinf(timeSec * 2.0f) * 0.03f)
        head.apply(dy = bob * 0.8f, dz = attackWeight * 0.11f, rx = -attackWeight * 8f, ry = sinf(timeSec * 1.5f) * 5f)
        earL.apply(rz = -8f + sinf(timeSec * 3.1f) * 5f)
        earR.apply(rz = 8f - sinf(timeSec * 3.1f) * 5f)
        tail.apply(ry = sinf(timeSec * 3.6f) * 12f)
    }
}
