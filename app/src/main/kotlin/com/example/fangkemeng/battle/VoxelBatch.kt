package com.example.fangkemeng.battle

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * 体素合批：把同一父节点下的静态方块合并成**一个** `MeshResource`（顶点色表达配色）。
 *
 * 一次 draw call 的代价远低于十几个实体各自提交；对擂台这种全静态场景直接整场合并，
 * 对精灵/训练师则按「可动部件」分组合并（一个锚点一份网格），保留关节动画。
 */
internal class VoxelAccumulator(private val name: String) {
    private val positions = ArrayList<Vector3>()
    private val normals = ArrayList<Vector3>()
    private val colors = ArrayList<Color4>()
    private val indices = ArrayList<Int>()
    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var minZ = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE
    private var maxZ = -Float.MAX_VALUE

    val isEmpty: Boolean get() = positions.isEmpty()

    fun addBox(
        size: Vector3,
        center: Vector3,
        color: Color4,
        rotX: Float = 0f,
        rotY: Float = 0f,
        rotZ: Float = 0f,
    ) {
        val hx = size.x * 0.5f
        val hy = size.y * 0.5f
        val hz = size.z * 0.5f
        // 每个面：法线 + 两个切向轴（u×v = n，保证逆时针绕序）+ 各自的半尺寸。
        val faces = arrayOf(
            Face(Vector3(1f, 0f, 0f), hx, Vector3(0f, 1f, 0f), hy, Vector3(0f, 0f, 1f), hz),
            Face(Vector3(-1f, 0f, 0f), hx, Vector3(0f, 1f, 0f), hy, Vector3(0f, 0f, -1f), hz),
            Face(Vector3(0f, 1f, 0f), hy, Vector3(0f, 0f, 1f), hz, Vector3(1f, 0f, 0f), hx),
            Face(Vector3(0f, -1f, 0f), hy, Vector3(0f, 0f, 1f), hz, Vector3(-1f, 0f, 0f), hx),
            Face(Vector3(0f, 0f, 1f), hz, Vector3(1f, 0f, 0f), hx, Vector3(0f, 1f, 0f), hy),
            Face(Vector3(0f, 0f, -1f), hz, Vector3(1f, 0f, 0f), hx, Vector3(0f, -1f, 0f), hy),
        )

        for (f in faces) {
            val base = positions.size
            for (su in floatArrayOf(-1f, 1f)) {
                for (sv in floatArrayOf(-1f, 1f)) {
                    val ox = f.n.x * f.hn + f.u.x * f.hu * su + f.v.x * f.hv * sv
                    val oy = f.n.y * f.hn + f.u.y * f.hu * su + f.v.y * f.hv * sv
                    val oz = f.n.z * f.hn + f.u.z * f.hu * su + f.v.z * f.hv * sv
                    val p = rotate(Vector3(center.x + ox, center.y + oy, center.z + oz), rotX, rotY, rotZ)
                    positions.add(p)
                    // 法线朝外；绕序按 SDK 的左手系约定反向，正面才朝外（否则会看到模型内部）。
                    normals.add(rotate(f.n, rotX, rotY, rotZ))
                    colors.add(color)
                    expandBounds(p)
                }
            }
            indices.add(base); indices.add(base + 3); indices.add(base + 1)
            indices.add(base); indices.add(base + 2); indices.add(base + 3)
        }
    }

    private class Face(
        val n: Vector3,
        val hn: Float,
        val u: Vector3,
        val hu: Float,
        val v: Vector3,
        val hv: Float,
    )

    fun addSphere(radius: Float, center: Vector3, color: Color4, rings: Int = 6, segments: Int = 10) {
        val ringBase = positions.size
        for (r in 0..rings) {
            val phi = Math.PI * r / rings
            val y = cos(phi).toFloat() * radius
            val ringR = sin(phi).toFloat() * radius
            for (s in 0..segments) {
                val theta = 2.0 * Math.PI * s / segments
                val p = Vector3(
                    center.x + cos(theta).toFloat() * ringR,
                    center.y + y,
                    center.z + sin(theta).toFloat() * ringR,
                )
                positions.add(p)
                normals.add(
                    Vector3(
                        (p.x - center.x) / radius,
                        (p.y - center.y) / radius,
                        (p.z - center.z) / radius,
                    ),
                )
                colors.add(color)
                expandBounds(p)
            }
        }
        for (r in 0 until rings) {
            for (s in 0 until segments) {
                val a = ringBase + r * (segments + 1) + s
                val b = a + segments + 1
                indices.add(a); indices.add(a + 1); indices.add(b)
                indices.add(a + 1); indices.add(b + 1); indices.add(b)
            }
        }
    }

    /** 生成合并后的网格；空则返回 null。 */
    fun build(): MeshResource? {
        if (positions.isEmpty()) return null
        val model = MeshModel(
            positions = positions,
            triangleIndices = indices,
            normals = normals,
            colors = colors,
        )
        MeshModel.validateOrThrow(model)
        val bounds = BoundingBox(
            center = Vector3((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f),
            halfExtent = Vector3((maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f),
        )
        return MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
    }

    private fun expandBounds(p: Vector3) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.z < minZ) minZ = p.z
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
        if (p.z > maxZ) maxZ = p.z
    }

    private fun rotate(p: Vector3, rx: Float, ry: Float, rz: Float): Vector3 {
        if (rx == 0f && ry == 0f && rz == 0f) return p
        val sx = sin(Math.toRadians(rx.toDouble())).toFloat()
        val cx = cos(Math.toRadians(rx.toDouble())).toFloat()
        val sy = sin(Math.toRadians(ry.toDouble())).toFloat()
        val cy = cos(Math.toRadians(ry.toDouble())).toFloat()
        val sz = sin(Math.toRadians(rz.toDouble())).toFloat()
        val cz = cos(Math.toRadians(rz.toDouble())).toFloat()
        // Rx → Ry → Rz
        var y = p.y * cx - p.z * sx
        var z = p.y * sx + p.z * cx
        var x = p.x * cy + z * sy
        z = -p.x * sy + z * cy
        val x2 = x * cz - y * sz
        y = x * sz + y * cz
        x = x2
        return Vector3(x, y, z)
    }
}

/**
 * 合批记录器：`begin()` 之后的 [voxelBox] / [voxelSphere] 不再各自建实体，
 * 而是按父实体累积，`end()` 时为每个父实体生成一份合并网格。
 */
internal object VoxelBatch {
    private var recording = false
    private var cacheKey: String? = null
    private val groups = IdentityHashMap<Entity, VoxelAccumulator>()
    private val order = ArrayList<Entity>()
    /** 合并网格缓存：同一物种/同一擂台重复构建时直接复用 GPU 资源。 */
    private val meshCache = HashMap<String, MeshResource>()

    val isRecording: Boolean get() = recording

    /** [cacheKey] 非空时，本次构建的每份合并网格会按 键#序号 缓存。 */
    fun begin(cacheKey: String? = null) {
        groups.clear()
        order.clear()
        this.cacheKey = cacheKey
        recording = true
    }

    fun addBox(
        parent: Entity,
        size: Vector3,
        colorHex: String,
        pos: Vector3,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
    ) {
        val acc = group(parent)
        acc.addBox(size, pos, vertexColor(colorHex), rotX, rotY, rotZ)
    }

    fun addSphere(parent: Entity, radius: Float, colorHex: String, pos: Vector3) {
        group(parent).addSphere(radius, pos, vertexColor(colorHex))
    }

    private fun group(parent: Entity): VoxelAccumulator {
        groups[parent]?.let { return it }
        val acc = VoxelAccumulator("voxel-${order.size}")
        groups[parent] = acc
        order.add(parent)
        return acc
    }

    /** 顶点色需要线性值：渲染端把顶点色当线性输入，这里做一次 sRGB→线性，避免整体发白。 */
    private fun vertexColor(hex: String): Color4 {
        val s = hex.removePrefix("0x").removePrefix("0X")
        fun channel(at: Int): Float {
            val v = s.substring(at, at + 2).toInt(16) / 255f
            return Math.pow(v.toDouble(), 2.2).toFloat()
        }
        val a = if (s.length >= 8) s.substring(6, 8).toInt(16) / 255f else 1f
        return Color4(channel(0), channel(2), channel(4), a)
    }

    /** 结束记录并把每个父实体的合并网格挂上去（命中缓存则复用）。 */
    fun end() {
        recording = false
        val material = PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4(1f, 1f, 1f, 1f))
            setRoughness(0.78f)
            setMetallic(0.02f)
            // 一份材质被本次构建的多份网格共享，转为全局避免随单个实体销毁被释放。
            toGlobal()
        }
        order.forEachIndexed { index, parent ->
            val acc = groups[parent] ?: return@forEachIndexed
            if (acc.isEmpty) return@forEachIndexed
            val key = cacheKey?.let { "$it#$index" }
            val mesh = key?.let { meshCache[it] } ?: acc.build()?.also { built ->
                // 缓存网格跨实体复用，必须转为全局资源，否则随实体销毁被释放。
                if (key != null) {
                    built.toGlobal()
                    meshCache[key] = built
                }
            } ?: return@forEachIndexed
            Entity().apply {
                components.set(ModelComponent(mesh, material))
                parent.addChild(this)
            }
        }
        groups.clear()
        order.clear()
        cacheKey = null
    }
}
