package com.example.fangkemeng.battle

import com.example.fangkemeng.model.ArenaTheme
import com.example.fangkemeng.model.StageSceneOption
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.sin

/**
 * 擂台运行时：静态几何合批为 1 份网格，树冠/火焰/晶簇单独成实体做轻量动效。
 */
internal class VoxelStageRuntime(val root: Entity) {
    private val sway = mutableListOf<Pair<Entity, Float>>()
    private val flames = mutableListOf<Pair<Entity, Float>>()
    private val pulse = mutableListOf<Pair<Entity, Float>>()
    private var timeSec = 0f

    internal fun addSway(node: Entity, phase: Float) { sway += node to phase }
    internal fun addFlame(node: Entity, phase: Float) { flames += node to phase }
    internal fun addPulse(node: Entity, phase: Float) { pulse += node to phase }

    fun tick(dtSec: Float) {
        timeSec += dtSec
        val t = timeSec
        sway.forEach { (node, phase) ->
            node.components.get(TransformComponent::class.java)?.setEulerAngles(
                EulerAngles(
                    sin(t * 1.3f + phase) * 2.5f,
                    0f,
                    sin(t * 1.7f + phase) * 4.5f,
                ),
            )
        }
        flames.forEach { (node, phase) ->
            val k = 0.85f + 0.25f * sin(t * 9f + phase) + 0.1f * sin(t * 17f + phase)
            node.components.get(TransformComponent::class.java)?.apply {
                setScaleVector(Vector3(k, k * 1.15f, k))
                setPosition(Vector3(position.x, 0.55f + 0.04f * sin(t * 7f + phase), position.z))
            }
        }
        pulse.forEach { (node, phase) ->
            val k = 1f + 0.10f * sin(t * 2.2f + phase)
            node.components.get(TransformComponent::class.java)?.setScaleVector(Vector3(k, k, k))
        }
    }
}

/**
 * 代码体素擂台：替换 `assets/models/stage` 下的 GLB 场景。
 *
 * 9 个战场由 [ArenaTheme] 参数化生成（配色 + 道具组合），静态部分整体合批为 1 份网格；
 * 树冠/火焰/晶簇保留独立实体并做轻微摆动、跳动与呼吸，让场景不呆板。
 * 首领战场在标准布局上加双层看台、王座、八柱双旗与火盆。
 */
internal object VoxelStages {
    private val CENTER_Z = BattleScene.ARENA_CENTER_Z
    private val BASE_W = BattleScene.ARENA_W + 1.1f
    private val BASE_D = BattleScene.ARENA_D + 1.1f
    private const val PLATFORM_Y = -0.06f
    private const val BOSS_SCALE = 1.32f

    private val PLAYER_Z = CENTER_Z + BattleScene.ARENA_D / 2f - 0.5f
    private val ENEMY_Z = CENTER_Z - BattleScene.ARENA_D / 2f + 0.5f

    fun build(stage: StageSceneOption): VoxelStageRuntime {
        val root = Entity()
        val runtime = VoxelStageRuntime(root)
        val scale = if (stage.boss) BOSS_SCALE else 1f
        val theme = Theme.of(stage.theme)
        val w = BASE_W * scale
        val d = BASE_D * scale

        // 静态部分：台面、边圈、栅栏、石柱、条幅、落点，全部合批。
        VoxelBatch.begin("stage:${stage.theme}:${stage.boss}")
        try {
            voxelBox(root, Vector3(w, 0.12f, d), theme.floor, Vector3(0f, PLATFORM_Y, CENTER_Z))
            rim(root, Vector3(w, 0.12f, d), theme.rim, 0.10f)
            rim(root, Vector3(w - 0.36f, 0.05f, d - 0.36f), theme.trim, 0.04f, 0.03f)
            when (stage.theme) {
                ArenaTheme.MEADOW, ArenaTheme.FOREST -> buildFence(root, w, d)
                ArenaTheme.ARENA -> buildPillarRing(root, w, d, theme, 6, banner = true)
                ArenaTheme.RUINS -> buildBrokenPillars(root, w, d, theme)
                ArenaTheme.VOLCANIC -> buildBrazierBases(root, w, d)
                ArenaTheme.STORM, ArenaTheme.CRYSTAL -> buildCrystalLines(root, w, d, theme)
                ArenaTheme.SKY -> buildCloudPylons(root, w, d, theme)
                ArenaTheme.THRONE -> buildPillarRing(root, w, d, theme, 8, banner = true)
            }
            if (stage.boss) buildBossSet(root, w, d, theme)
            spawnTiles(root, theme.tile)
        } finally {
            VoxelBatch.end()
        }

        // 动效部分：每件道具单独合批成一个实体，交给运行时做摆动/跳动。
        when (stage.theme) {
            ArenaTheme.MEADOW, ArenaTheme.FOREST -> buildSwayTrees(root, w, d, theme, runtime)
            ArenaTheme.VOLCANIC -> buildFlames(root, w, d, runtime)
            ArenaTheme.STORM, ArenaTheme.CRYSTAL -> buildPulsingCrystals(root, w, d, theme, runtime)
            ArenaTheme.THRONE -> buildFlames(root, w, d, runtime)
            else -> Unit
        }
        return runtime
    }

    // ─── 通用构件 ───

    private fun rim(root: Entity, size: Vector3, colorHex: String, thickness: Float, height: Float = 0.04f) {
        val y = 0.02f
        val halfW = size.x / 2f
        val halfD = size.z / 2f
        voxelBox(root, Vector3(size.x, height, thickness), colorHex, Vector3(0f, y, CENTER_Z - halfD + thickness / 2f), rounded = false)
        voxelBox(root, Vector3(size.x, height, thickness), colorHex, Vector3(0f, y, CENTER_Z + halfD - thickness / 2f), rounded = false)
        voxelBox(root, Vector3(thickness, height, size.z - thickness * 2f), colorHex, Vector3(-halfW + thickness / 2f, y, CENTER_Z), rounded = false)
        voxelBox(root, Vector3(thickness, height, size.z - thickness * 2f), colorHex, Vector3(halfW - thickness / 2f, y, CENTER_Z), rounded = false)
    }

    private fun spawnTiles(root: Entity, colorHex: String) {
        listOf(PLAYER_Z, ENEMY_Z).forEach { z ->
            listOf(-0.55f, 0.55f).forEach { x ->
                voxelBox(root, Vector3(0.44f, 0.02f, 0.44f), colorHex, Vector3(x, 0.062f, z), rounded = false)
            }
        }
    }

    // ─── 森林 / 草地 ───

    /** 栅栏与树干留在静态合批里；树冠单独成实体在 [buildSwayTrees] 中轻微摆动。 */
    private fun buildFence(root: Entity, w: Float, d: Float) {
        val posts = 8
        for (i in 0 until posts) {
            val t = i / (posts - 1f)
            val x = -w / 2f + t * w
            fencePost(root, Vector3(x, 0f, CENTER_Z - d / 2f + 0.10f))
            fencePost(root, Vector3(x, 0f, CENTER_Z + d / 2f - 0.10f))
        }
        val sidePosts = 6
        for (i in 0 until sidePosts) {
            val t = i / (sidePosts - 1f)
            val z = CENTER_Z - d / 2f + t * d
            fencePost(root, Vector3(-w / 2f + 0.10f, 0f, z))
            fencePost(root, Vector3(w / 2f - 0.10f, 0f, z))
        }
        treeSpots(w, d).forEach { p ->
            voxelBox(root, Vector3(0.16f, 0.62f, 0.16f), "0x8C5C33FF", Vector3(p.x, 0.31f, p.z), rounded = false)
        }
    }

    private fun treeSpots(w: Float, d: Float): List<Vector3> = listOf(
        Vector3(-w / 2f + 0.25f, 0f, CENTER_Z - d / 2f + 0.55f),
        Vector3(w / 2f - 0.25f, 0f, CENTER_Z - d / 2f + 0.55f),
        Vector3(-w / 2f + 0.25f, 0f, CENTER_Z + d / 2f - 0.65f),
        Vector3(w / 2f - 0.25f, 0f, CENTER_Z + d / 2f - 0.65f),
        Vector3(-0.9f, 0f, CENTER_Z - d / 2f + 0.35f),
        Vector3(0.9f, 0f, CENTER_Z - d / 2f + 0.35f),
    )

    private fun fencePost(root: Entity, p: Vector3) {
        voxelBox(root, Vector3(0.10f, 0.34f, 0.10f), "0xA97443FF", Vector3(p.x, 0.17f, p.z), rounded = false)
        voxelBox(root, Vector3(0.12f, 0.04f, 0.12f), "0x8C5C33FF", Vector3(p.x, 0.36f, p.z), rounded = false)
    }

    /** 每个树冠一个实体，绕树干根部做 ±4.5° 摆动。 */
    private fun buildSwayTrees(root: Entity, w: Float, d: Float, theme: Theme, runtime: VoxelStageRuntime) {
        treeSpots(w, d).forEachIndexed { i, p ->
            val canopy = buildProp("canopy:${theme.propA}", Vector3(p.x, 0.62f, p.z)) { anchor ->
                voxelBox(anchor, Vector3(0.52f, 0.30f, 0.52f), theme.propA, Vector3(0f, 0.14f, 0f))
                voxelBox(anchor, Vector3(0.38f, 0.22f, 0.38f), theme.propB, Vector3(0f, 0.36f, 0f))
                voxelBox(anchor, Vector3(0.22f, 0.16f, 0.22f), theme.propA, Vector3(0f, 0.52f, 0f))
            }
            root.addChild(canopy)
            runtime.addSway(canopy, i * 0.9f)
        }
    }

    /** 把一段构件合批成独立实体（用于需要单独动效的道具）。 */
    private fun buildProp(key: String, position: Vector3, build: (Entity) -> Unit): Entity {
        val anchor = Entity()
        anchor.components.get(TransformComponent::class.java)?.setPosition(position)
        VoxelBatch.begin(key)
        try {
            build(anchor)
        } finally {
            VoxelBatch.end()
        }
        return anchor
    }

    // ─── 石柱竞技场 ───

    private fun buildPillarRing(root: Entity, w: Float, d: Float, theme: Theme, count: Int, banner: Boolean) {
        val px = w / 2f - 0.16f
        val pz = d / 2f - 0.16f
        val spots = ArrayList<Vector3>()
        if (count <= 6) {
            spots += Vector3(-px, 0f, CENTER_Z - pz)
            spots += Vector3(px, 0f, CENTER_Z - pz)
            spots += Vector3(-px, 0f, CENTER_Z + pz)
            spots += Vector3(px, 0f, CENTER_Z + pz)
            spots += Vector3(-px, 0f, CENTER_Z)
            spots += Vector3(px, 0f, CENTER_Z)
        } else {
            for (i in 0 until count) {
                val t = i / count.toFloat()
                val z = CENTER_Z - d / 2f + 0.18f + t * (d - 0.36f)
                spots += Vector3(-px, 0f, z)
                spots += Vector3(px, 0f, z)
            }
        }
        spots.forEachIndexed { i, p ->
            pillar(root, p, theme, i)
            if (banner) {
                val inward = if (p.x > 0f) -1f else 1f
                voxelBox(root, Vector3(0.03f, 0.36f, 0.15f), theme.banner, Vector3(p.x + inward * 0.13f, 0.66f, p.z), rounded = false)
                voxelBox(root, Vector3(0.035f, 0.07f, 0.17f), theme.trim, Vector3(p.x + inward * 0.13f, 0.86f, p.z), rounded = false)
            }
        }
    }

    private fun pillar(root: Entity, p: Vector3, theme: Theme, index: Int) {
        val h = if (index % 2 == 0) 0.86f else 0.72f
        voxelBox(root, Vector3(0.22f, h, 0.22f), theme.propA, Vector3(p.x, h / 2f, p.z), rounded = false)
        voxelBox(root, Vector3(0.26f, 0.08f, 0.26f), theme.propB, Vector3(p.x, h + 0.04f, p.z), rounded = false)
    }

    private fun buildBrokenPillars(root: Entity, w: Float, d: Float, theme: Theme) {
        val px = w / 2f - 0.18f
        val pz = d / 2f - 0.18f
        val spots = listOf(
            Vector3(-px, 0f, CENTER_Z - pz), Vector3(px, 0f, CENTER_Z - pz),
            Vector3(-px, 0f, CENTER_Z + pz), Vector3(px, 0f, CENTER_Z + pz),
        )
        spots.forEachIndexed { i, p ->
            val h = listOf(0.34f, 0.7f, 0.52f, 0.9f)[i % 4]
            voxelBox(root, Vector3(0.24f, h, 0.24f), theme.propA, Vector3(p.x, h / 2f, p.z), rotZ = (i - 1.5f) * 3f, rounded = false)
            // 倒下的断柱
            voxelBox(root, Vector3(0.22f, 0.22f, 0.6f), theme.propB, Vector3(p.x * 0.55f, 0.11f, p.z + (if (i < 2) 0.5f else -0.5f)), rounded = false)
        }
    }

    // ─── 熔岩 ───

    private fun brazierSpots(w: Float, d: Float): List<Vector3> {
        val px = w / 2f - 0.2f
        val pz = d / 2f - 0.2f
        return listOf(
            Vector3(-px, 0f, CENTER_Z - pz), Vector3(px, 0f, CENTER_Z - pz),
            Vector3(-px, 0f, CENTER_Z + pz), Vector3(px, 0f, CENTER_Z + pz),
            Vector3(-px, 0f, CENTER_Z), Vector3(px, 0f, CENTER_Z),
        )
    }

    /** 火盆石座留在静态层，火焰单独跳动。 */
    private fun buildBrazierBases(root: Entity, w: Float, d: Float) {
        brazierSpots(w, d).forEach { p ->
            voxelBox(root, Vector3(0.2f, 0.5f, 0.2f), "0x3B2F2AFF", Vector3(p.x, 0.25f, p.z), rounded = false)
            voxelBox(root, Vector3(0.3f, 0.1f, 0.3f), "0x4A3A34FF", Vector3(p.x, 0.55f, p.z), rounded = false)
        }
        // 场地熔岩裂缝
        listOf(-0.9f, 0.2f, 1.1f).forEachIndexed { i, z ->
            voxelBox(root, Vector3(1.2f, 0.02f, 0.08f), "0xFF7A22FF", Vector3(0f, 0.065f, CENTER_Z + z), rotY = 18f * i, rounded = false)
        }
    }

    /** 火焰：每盆两段橙色方块，做上下跳动与缩放呼吸。 */
    private fun buildFlames(root: Entity, w: Float, d: Float, runtime: VoxelStageRuntime) {
        brazierSpots(w, d).forEachIndexed { i, p ->
            val flame = buildProp("flame", Vector3(p.x, 0.62f, p.z)) { anchor ->
                voxelBox(anchor, Vector3(0.16f, 0.22f, 0.16f), "0xFFB24AFF", Vector3(0f, 0.06f, 0f), rounded = false)
                voxelBox(anchor, Vector3(0.10f, 0.16f, 0.10f), "0xFF6A22FF", Vector3(0f, 0.20f, 0f), rounded = false)
            }
            root.addChild(flame)
            runtime.addFlame(flame, i * 1.3f)
        }
    }

    // ─── 晶簇 / 风暴 ───

    /** 地面发光纹保留静态；晶簇在 [buildPulsingCrystals] 中呼吸。 */
    private fun buildCrystalLines(root: Entity, w: Float, d: Float, theme: Theme) {
        for (i in 0..2) {
            val z = CENTER_Z - d / 2f + (i + 1) * d / 4f
            voxelBox(root, Vector3(w - 0.3f, 0.02f, 0.04f), theme.trim, Vector3(0f, 0.012f, z), rounded = false)
        }
    }

    private fun crystalSpots(w: Float, d: Float): List<Vector3> {
        val px = w / 2f - 0.2f
        val pz = d / 2f - 0.2f
        return listOf(
            Vector3(-px, 0f, CENTER_Z - pz), Vector3(px, 0f, CENTER_Z - pz),
            Vector3(-px, 0f, CENTER_Z + pz), Vector3(px, 0f, CENTER_Z + pz),
            Vector3(-px, 0f, CENTER_Z), Vector3(px, 0f, CENTER_Z),
        )
    }

    private fun buildPulsingCrystals(root: Entity, w: Float, d: Float, theme: Theme, runtime: VoxelStageRuntime) {
        crystalSpots(w, d).forEachIndexed { i, p ->
            val color = if (i % 2 == 0) theme.propA else theme.propB
            val crystal = buildProp("crystal:$color", p) { anchor ->
                voxelBox(anchor, Vector3(0.16f, 0.78f, 0.16f), color, Vector3(0f, 0.39f, 0f), rotY = i * 12f, rounded = false)
                voxelBox(anchor, Vector3(0.10f, 0.46f, 0.10f), theme.propB, Vector3(0.16f, 0.23f, -0.06f), rotY = -i * 9f, rounded = false)
            }
            root.addChild(crystal)
            runtime.addPulse(crystal, i * 0.8f)
        }
    }

    // ─── 云端 ───

    private fun buildCloudPylons(root: Entity, w: Float, d: Float, theme: Theme) {
        val px = w / 2f - 0.22f
        val pz = d / 2f - 0.22f
        listOf(
            Vector3(-px, 0f, CENTER_Z - pz), Vector3(px, 0f, CENTER_Z - pz),
            Vector3(-px, 0f, CENTER_Z + pz), Vector3(px, 0f, CENTER_Z + pz),
        ).forEachIndexed { i, p ->
            voxelBox(root, Vector3(0.16f, 1.1f, 0.16f), theme.propA, Vector3(p.x, 0.55f, p.z), rounded = false)
            voxelBox(root, Vector3(0.34f, 0.14f, 0.34f), theme.propB, Vector3(p.x, 1.12f, p.z))
        }
        // 云团
        listOf(-1.2f to -1.6f, 0.4f to -2.0f, 1.3f to 1.4f).forEachIndexed { i, (x, z) ->
            voxelBox(root, Vector3(0.7f, 0.24f, 0.5f), theme.propB, Vector3(x, 0.12f + i * 0.02f, CENTER_Z + z))
            voxelBox(root, Vector3(0.44f, 0.18f, 0.34f), theme.floor, Vector3(x + 0.2f, 0.26f + i * 0.02f, CENTER_Z + z - 0.1f))
        }
    }

    // ─── 首领套件 ───

    private fun buildBossSet(root: Entity, w: Float, d: Float, theme: Theme) {
        val farZ = CENTER_Z - d / 2f
        // 双层看台
        voxelBox(root, Vector3(w - 1.0f, 0.34f, 1.5f), theme.propA, Vector3(0f, 0.17f, farZ + 0.35f))
        voxelBox(root, Vector3(w - 1.6f, 0.3f, 1.0f), theme.propB, Vector3(0f, 0.49f, farZ + 0.55f))
        // 王座
        voxelBox(root, Vector3(0.86f, 0.9f, 0.2f), theme.trim, Vector3(0f, 1.09f, farZ + 0.2f), rounded = false)
        voxelBox(root, Vector3(0.62f, 0.42f, 0.5f), theme.banner, Vector3(0f, 0.85f, farZ + 0.5f), rounded = false)
        voxelBox(root, Vector3(0.7f, 0.1f, 0.56f), theme.trim, Vector3(0f, 0.7f, farZ + 0.5f), rounded = false)
        // 王座顶冠
        listOf(-0.32f, 0f, 0.32f).forEach { x ->
            voxelBox(root, Vector3(0.1f, 0.26f, 0.1f), theme.tile, Vector3(x, 1.66f, farZ + 0.2f), rounded = false)
        }
        // 中央徽记：金色菱形四片
        voxelBox(root, Vector3(0.9f, 0.02f, 0.1f), theme.tile, Vector3(0f, 0.07f, CENTER_Z), rotY = 45f, rounded = false)
        voxelBox(root, Vector3(0.1f, 0.02f, 0.9f), theme.tile, Vector3(0f, 0.07f, CENTER_Z), rotY = 45f, rounded = false)
        // 近端火盆
        listOf(-w / 2f + 0.3f, w / 2f - 0.3f).forEach { x ->
            voxelBox(root, Vector3(0.2f, 0.5f, 0.2f), "0x3B2F2AFF", Vector3(x, 0.25f, CENTER_Z + d / 2f - 0.35f), rounded = false)
            voxelBox(root, Vector3(0.16f, 0.22f, 0.16f), "0xFFB24AFF", Vector3(x, 0.62f, CENTER_Z + d / 2f - 0.35f), rounded = false)
        }
    }

    /** 主题配色表。 */
    private data class Theme(
        val floor: String,
        val rim: String,
        val trim: String,
        val propA: String,
        val propB: String,
        val banner: String,
        val tile: String,
    ) {
        companion object {
            fun of(theme: ArenaTheme): Theme = when (theme) {
                ArenaTheme.MEADOW -> Theme("0xC6E8A8FF", "0x8FCF8AFF", "0xF3C96BFF", "0x5FAE4CFF", "0x77DB6CFF", "0xE3350DFF", "0xE9F0D3FF")
                ArenaTheme.ARENA -> Theme("0xE7E2D6FF", "0xE3350DFF", "0xF3C96BFF", "0x9A938AFF", "0x7C756CFF", "0xE3350DFF", "0xF3C96BFF")
                ArenaTheme.FOREST -> Theme("0x8FCF8AFF", "0xC9A86AFF", "0xF3C96BFF", "0x5FAE4CFF", "0x77DB6CFF", "0xE3350DFF", "0xE9F0D3FF")
                ArenaTheme.VOLCANIC -> Theme("0x3A2A28FF", "0xE2431FFF", "0xFFB24AFF", "0x4A3A34FF", "0xFF7A22FF", "0xE2431FFF", "0xFFB24AFF")
                ArenaTheme.RUINS -> Theme("0xB9AE99FF", "0x7C756CFF", "0xB7AE9EFF", "0x9A938AFF", "0x7C756CFF", "0x8C3520FF", "0xD9CFBBFF")
                ArenaTheme.STORM -> Theme("0x37415CFF", "0x7FA7FFFF", "0x9FE8FFFF", "0x6C4BC4FF", "0x2FD9C9FF", "0x4666B8FF", "0x9FE8FFFF")
                ArenaTheme.CRYSTAL -> Theme("0x2B3350FF", "0x1C2238FF", "0x2FD9C9FF", "0x6C4BC4FF", "0x2FD9C9FF", "0x4666B8FF", "0x2FD9C9FF")
                ArenaTheme.SKY -> Theme("0xDDEBFFFF", "0x8FB6E8FF", "0xFFFFFFFF", "0x8FB6E8FF", "0xFFFFFFFF", "0xF3C96BFF", "0xEAF4FFFF")
                ArenaTheme.THRONE -> Theme("0x2A2433FF", "0xF3C96BFF", "0xE3350DFF", "0x4A4350FF", "0x1B1E24FF", "0xE3350DFF", "0xF3C96BFF")
            }
        }
    }
}
