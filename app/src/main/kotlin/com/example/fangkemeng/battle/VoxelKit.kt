package com.example.fangkemeng.battle

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/**
 * 体素构件的统一入口。
 *
 * `MeshResource.createBox/createSphere` 是同步 JNI 调用，模拟器上单个网格可达数百毫秒。
 * 这里所有方块共用 1×1×1 单位网格、球体共用单位球，尺寸差异全部由 `TransformComponent` 缩放表达：
 * 几十次网格生成被压成一次，同时保证精灵、训练师与擂台的一致体素观感。
 */
internal object VoxelMesh {
    private var roundedBox: MeshResource? = null
    private var sharpBox: MeshResource? = null
    private var unitSphere: MeshResource? = null

    fun box(rounded: Boolean): MeshResource {
        if (rounded) {
            roundedBox?.let { return it }
            return MeshResource.createBox(Vector3(1f, 1f, 1f), 0.09f)
                .also { it.toGlobal(); roundedBox = it }
        }
        sharpBox?.let { return it }
        return MeshResource.createBox(Vector3(1f, 1f, 1f), 0.001f)
            .also { it.toGlobal(); sharpBox = it }
    }

    fun sphere(): MeshResource =
        unitSphere ?: MeshResource.createSphere(0.5f)
            .also { it.toGlobal(); unitSphere = it }
}

/** 六属性配色 + 通用中性色，见 `docs/design/creature-design-sheet.md`。 */
internal object VoxelPalette {
    const val GRASS_BODY = "0x69DFAEFF"
    const val GRASS_SHADE = "0x58C995FF"
    const val GRASS_LEAF = "0x77DB6CFF"
    const val WATER_BODY = "0x65CBE0FF"
    const val WATER_SHADE = "0x4AB0CBFF"
    const val WATER_DEEP = "0x2A9BC8FF"
    const val FIRE_BODY = "0xE9743FFF"
    const val FIRE_SHADE = "0xD4552AFF"
    const val FIRE_BELLY = "0xFFD79AFF"
    const val ELECTRIC_BODY = "0xF5D14EFF"
    const val ELECTRIC_SHADE = "0xD9A72CFF"
    const val ROCK_BODY = "0x9A938AFF"
    const val ROCK_SHADE = "0x7C756CFF"
    const val NORMAL_BODY = "0xE7E2D6FF"
    const val NORMAL_SHADE = "0xC9C2B2FF"

    const val CREAM = "0xF4E8C2FF"
    const val WHITE = "0xFFFFFFFF"
    const val INK = "0x22262EFF"
    const val RED = "0xE3350DFF"
    const val GOLD = "0xF3C96BFF"
    const val MINT = "0x8FCF8AFF"
    const val TEAL = "0x2FD9C9FF"
    const val INDIGO = "0x2B3350FF"
    const val VIOLET = "0x6C4BC4FF"
}

private fun voxelMaterial(colorHex: String, roughness: Float): PhysicallyBasedMaterial =
    PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
        setBaseColor(Color4.fromSRGBHex(colorHex))
        setRoughness(roughness)
        setMetallic(0.02f)
    }

/** 在 [parent] 下挂一个体素方块；[size] 为世界尺寸（米）。录制中会并入该父实体的合并网格。 */
internal fun voxelBox(
    parent: Entity,
    size: Vector3,
    colorHex: String,
    pos: Vector3 = Vector3.ZERO,
    rotX: Float = 0f,
    rotY: Float = 0f,
    rotZ: Float = 0f,
    rounded: Boolean = true,
    roughness: Float = 0.78f,
): Entity {
    if (VoxelBatch.isRecording) {
        VoxelBatch.addBox(parent, size, colorHex, pos, rotX, rotY, rotZ)
        return parent
    }
    return voxelBoxNow(parent, size, colorHex, pos, rotX, rotY, rotZ, rounded, roughness)
}

/** 独立实体方块：用于需要单独显隐/逐帧变换的部件（例如精灵球）。 */
internal fun voxelBoxNow(
    parent: Entity,
    size: Vector3,
    colorHex: String,
    pos: Vector3 = Vector3.ZERO,
    rotX: Float = 0f,
    rotY: Float = 0f,
    rotZ: Float = 0f,
    rounded: Boolean = true,
    roughness: Float = 0.78f,
): Entity = Entity().apply {
    components.set(ModelComponent(VoxelMesh.box(rounded), voxelMaterial(colorHex, roughness)))
    components.get(TransformComponent::class.java)?.apply {
        setPosition(pos)
        setEulerAngles(EulerAngles(rotX, rotY, rotZ))
        setScaleVector(size)
    }
    parent.addChild(this)
}

/** 在 [parent] 下挂一个体素球；录制中会并入该父实体的合并网格。 */
internal fun voxelSphere(
    parent: Entity,
    radius: Float,
    colorHex: String,
    pos: Vector3 = Vector3.ZERO,
    roughness: Float = 0.78f,
): Entity {
    if (VoxelBatch.isRecording) {
        VoxelBatch.addSphere(parent, radius, colorHex, pos)
        return parent
    }
    return voxelSphereNow(parent, radius, colorHex, pos, roughness)
}

/** 独立实体球：用于需要单独显隐的部件。 */
internal fun voxelSphereNow(
    parent: Entity,
    radius: Float,
    colorHex: String,
    pos: Vector3 = Vector3.ZERO,
    roughness: Float = 0.78f,
): Entity = Entity().apply {
    components.set(ModelComponent(VoxelMesh.sphere(), voxelMaterial(colorHex, roughness)))
    components.get(TransformComponent::class.java)?.apply {
        setPosition(pos)
        setScaleVector(Vector3(radius * 2f, radius * 2f, radius * 2f))
    }
    parent.addChild(this)
}
