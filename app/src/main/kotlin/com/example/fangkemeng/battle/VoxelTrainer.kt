package com.example.fangkemeng.battle

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.sin

/** 训练师外观：每个关卡一套，见 [TrainerLooks.forLevel]。 */
internal data class TrainerLook(
    val coat: String,
    val coatShade: String,
    val hat: String,
    val band: String,
    val pants: String,
    val accent: String,
    val skin: String = "0xF0C9A0FF",
    val hatStyle: HatStyle = HatStyle.CAP,
)

internal enum class HatStyle { CAP, BERET, HELMET, HOOD, CROWN }

/** 7 个战斗层各一套训练师外观：颜色 + 帽型 + 徽记。 */
internal object TrainerLooks {
    fun forLevel(level: Int): TrainerLook = when (level) {
        1 -> TrainerLook("0xE3350DFF", "0xCF2E0CFF", "0x1B1E24FF", "0xE3350DFF", "0x2B3550FF", "0xF3C96BFF", hatStyle = HatStyle.CAP)
        2 -> TrainerLook("0x2F6BD8FF", "0x2454AEFF", "0xF4E8C2FF", "0x2F6BD8FF", "0x22324CFF", "0x9FE8FFFF", hatStyle = HatStyle.BERET)
        3 -> TrainerLook("0x2E8B57FF", "0x226B43FF", "0x8C5C33FF", "0xF3C96BFF", "0x2A3B2EFF", "0xF3C96BFF", hatStyle = HatStyle.BERET)
        5 -> TrainerLook("0xE9743FFF", "0xC15427FF", "0x3B2F2AFF", "0xFFB24AFF", "0x402722FF", "0xFFB24AFF", hatStyle = HatStyle.HELMET)
        6 -> TrainerLook("0x8A8578FF", "0x6E6A5EFF", "0x5A564EFF", "0xB7AE9EFF", "0x3A352EFF", "0xB7AE9EFF", hatStyle = HatStyle.HOOD)
        7 -> TrainerLook("0x6C4BC4FF", "0x53399AFF", "0xC9C2B2FF", "0x9FE8FFFF", "0x2B2440FF", "0x9FE8FFFF", hatStyle = HatStyle.CAP)
        else -> TrainerLook("0xF3C96BFF", "0xD9A72CFF", "0x1B1E24FF", "0xE3350DFF", "0x2A2433FF", "0xFFFFFFFF", hatStyle = HatStyle.CROWN)
    }
}

/**
 * 体素训练师：1.6m，站姿，面向 +Z（与玩家相对）。
 *
 * 外观由 [TrainerLook] 决定（每个关卡不同的外套/帽型/徽记）。
 * 身体按可动部件合批：静止躯干 1 份、头 1 份、右臂 1 份。
 */
internal class VoxelTrainer(private val look: TrainerLook = TrainerLooks.forLevel(1)) {
    val root: Entity = Entity()
    val nominalHeightM: Float = 1.6f

    private val head = Entity()
    private val armR = Entity()
    private val ball: Entity

    private var timeSec = 0f
    private var basePos: Vector3 = Vector3.ZERO
    private var commandUntilMs = 0L

    /** 0 = 垂放，1 = 抬到胸前预备投球。 */
    var armRaise: Float = 0f

    /** 敌方精灵出招前，训练家抬手指向擂台。 */
    fun command() {
        commandUntilMs = System.currentTimeMillis() + (COMMAND_DURATION_SEC * 1000f).toLong()
    }

    init {
        VoxelBatch.begin("trainer:${look.hatStyle}:${look.coat}")
        try {
            buildBody()
        } finally {
            VoxelBatch.end()
        }
        // 精灵球需要单独显隐，保持独立实体。
        ball = voxelSphereNow(armR, 0.045f, "0xE3350DFF", Vector3(0f, -0.30f, 0.08f))
    }

    private fun buildBody() {
        // 靴
        voxelBox(root, Vector3(0.15f, 0.10f, 0.24f), "0x5A4A3AFF", Vector3(-0.10f, 0.05f, 0.03f))
        voxelBox(root, Vector3(0.15f, 0.10f, 0.24f), "0x5A4A3AFF", Vector3(0.10f, 0.05f, 0.03f))
        // 长裤
        voxelBox(root, Vector3(0.14f, 0.62f, 0.16f), look.pants, Vector3(-0.10f, 0.42f, 0f))
        voxelBox(root, Vector3(0.14f, 0.62f, 0.16f), look.pants, Vector3(0.10f, 0.42f, 0f))
        // 外套与领口
        voxelBox(root, Vector3(0.38f, 0.46f, 0.22f), look.coat, Vector3(0f, 0.92f, 0f))
        voxelBox(root, Vector3(0.30f, 0.07f, 0.23f), VoxelPalette.CREAM, Vector3(0f, 1.12f, 0.005f))
        voxelBox(root, Vector3(0.12f, 0.30f, 0.02f), look.accent, Vector3(0f, 0.92f, 0.115f), rounded = false)
        // 左臂（垂放）
        voxelBox(root, Vector3(0.11f, 0.40f, 0.13f), look.coatShade, Vector3(-0.245f, 0.92f, 0f))
        voxelBox(root, Vector3(0.09f, 0.09f, 0.09f), look.skin, Vector3(-0.245f, 0.68f, 0.01f), rounded = false)

        // 右臂锚点（可抬臂）：用独立网格，确保关节旋转能驱动可见部件。
        root.addChild(head)
        root.addChild(armR)
        armR.components.get(TransformComponent::class.java)?.setPosition(Vector3(0.245f, 1.10f, 0f))
        voxelBoxNow(armR, Vector3(0.11f, 0.26f, 0.13f), look.coatShade, Vector3(0f, -0.14f, 0f))
        voxelBoxNow(armR, Vector3(0.09f, 0.09f, 0.09f), look.skin, Vector3(0f, -0.30f, 0.01f), rounded = false)

        // 头部锚点
        head.components.get(TransformComponent::class.java)?.setPosition(Vector3(0f, 1.34f, 0f))
        voxelBox(head, Vector3(0.27f, 0.25f, 0.24f), look.skin, Vector3(0f, 0f, 0f))
        voxelBox(head, Vector3(0.05f, 0.05f, 0.02f), "0x22262EFF", Vector3(-0.062f, 0.015f, 0.122f), rounded = false)
        voxelBox(head, Vector3(0.05f, 0.05f, 0.02f), "0x22262EFF", Vector3(0.062f, 0.015f, 0.122f), rounded = false)
        buildHat(head)
    }

    private fun buildHat(parent: Entity) {
        when (look.hatStyle) {
            HatStyle.CAP -> {
                voxelBox(parent, Vector3(0.31f, 0.11f, 0.28f), look.hat, Vector3(0f, 0.17f, -0.01f))
                voxelBox(parent, Vector3(0.31f, 0.03f, 0.285f), look.band, Vector3(0f, 0.135f, -0.005f), rounded = false)
                voxelBox(parent, Vector3(0.26f, 0.03f, 0.12f), look.hat, Vector3(0f, 0.125f, 0.16f), rounded = false)
            }
            HatStyle.BERET -> {
                voxelBox(parent, Vector3(0.34f, 0.08f, 0.30f), look.hat, Vector3(0f, 0.18f, -0.02f), rotZ = -4f)
                voxelBox(parent, Vector3(0.30f, 0.03f, 0.26f), look.band, Vector3(0f, 0.13f, -0.02f), rounded = false)
                voxelBox(parent, Vector3(0.07f, 0.07f, 0.07f), look.accent, Vector3(0f, 0.24f, -0.06f), rounded = false)
            }
            HatStyle.HELMET -> {
                voxelBox(parent, Vector3(0.33f, 0.13f, 0.30f), look.hat, Vector3(0f, 0.18f, -0.01f))
                voxelBox(parent, Vector3(0.05f, 0.20f, 0.26f), look.hat, Vector3(-0.185f, 0.05f, 0f), rounded = false)
                voxelBox(parent, Vector3(0.05f, 0.20f, 0.26f), look.hat, Vector3(0.185f, 0.05f, 0f), rounded = false)
                voxelBox(parent, Vector3(0.26f, 0.03f, 0.12f), look.accent, Vector3(0f, 0.135f, 0.16f), rounded = false)
            }
            HatStyle.HOOD -> {
                voxelBox(parent, Vector3(0.38f, 0.18f, 0.36f), look.hat, Vector3(0f, 0.20f, -0.03f))
                voxelBox(parent, Vector3(0.10f, 0.26f, 0.34f), look.hat, Vector3(-0.20f, 0.02f, -0.02f), rounded = false)
                voxelBox(parent, Vector3(0.10f, 0.26f, 0.34f), look.hat, Vector3(0.20f, 0.02f, -0.02f), rounded = false)
            }
            HatStyle.CROWN -> {
                voxelBox(parent, Vector3(0.33f, 0.08f, 0.30f), look.hat, Vector3(0f, 0.16f, -0.01f))
                listOf(-0.11f, 0f, 0.11f).forEachIndexed { i, x ->
                    val h = if (i == 1) 0.22f else 0.15f
                    voxelBox(parent, Vector3(0.07f, h, 0.07f), look.hat, Vector3(x, 0.20f + h / 2f, 0.02f), rounded = false)
                }
                voxelBox(parent, Vector3(0.06f, 0.06f, 0.06f), look.accent, Vector3(0f, 0.33f, 0.02f), rounded = false)
            }
        }
    }

    /** 摆到场景坐标；[yawDeg] 决定朝向。 */
    fun place(pos: Vector3, yawDeg: Float, scale: Float = 1f) {
        basePos = pos
        root.components.get(TransformComponent::class.java)?.apply {
            setPosition(pos)
            setScaleVector(Vector3(scale, scale, scale))
            setEulerAngles(EulerAngles(0f, yawDeg, 0f))
        }
    }

    /** 每帧：呼吸 + 头部环视 + 抬臂插值 + 指挥动作。 */
    fun tick(dtSec: Float) {
        timeSec += dtSec
        // 用墙钟时间而不是帧 delta，避免高刷新/多次回调把动作瞬间播完。
        val remainingSec = (commandUntilMs - System.currentTimeMillis()) / 1000f
        val cue = if (remainingSec > 0f) {
            val progress = 1f - remainingSec / COMMAND_DURATION_SEC
            when {
                progress < 0.16f -> progress / 0.16f
                progress < 0.78f -> 1f
                else -> ((1f - progress) / 0.22f).coerceAtLeast(0f)
            }
        } else {
            0f
        }
        val bob = sin(timeSec * 2.2f)
        root.components.get(TransformComponent::class.java)?.apply {
            setPosition(Vector3(basePos.x, basePos.y + bob * 0.002f, basePos.z))
        }
        head.components.get(TransformComponent::class.java)?.apply {
            setPosition(Vector3(0f, 1.34f, 0f))
            setEulerAngles(
                EulerAngles(
                    cue * 8f,
                    sin(timeSec * 0.9f) * 3f * (1f - cue),
                    0f,
                ),
            )
        }
        armR.components.get(TransformComponent::class.java)?.apply {
            setPosition(Vector3(0.245f, 1.10f, 0f))
            // 指挥时右臂向斜上方外侧抬起，形成清晰的剪影。
            setEulerAngles(EulerAngles(-(armRaise * 55f + cue * 35f), 0f, cue * 65f))
        }
        ball.enabled = armRaise > 0.35f && cue < 0.2f
    }

    fun dispose() {
        runCatching { root.destroy(true) }
    }

    private companion object {
        const val COMMAND_DURATION_SEC = 1.6f
    }
}
