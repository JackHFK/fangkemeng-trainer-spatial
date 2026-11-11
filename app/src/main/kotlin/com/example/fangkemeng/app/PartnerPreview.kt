package com.example.fangkemeng.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fangkemeng.battle.BattleCreature
import com.example.fangkemeng.battle.ProceduralCreatureFactory
import com.example.fangkemeng.model.CreatureId
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System as SpatialSystem
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 伙伴 3D 预览：程序化方块兽，高度对齐视口并自转。 */
@Composable
fun PartnerPreviewView(creatureId: CreatureId = CreatureId.SEED_CUB, active: Boolean = true, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = active && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    // 每只精灵独立拥有 SpatialView，切换时一并释放模型、灯光和逐帧状态。
    key(creatureId) {
        PartnerPreviewScene(creatureId, visible, modifier)
    }
}

@Composable
private fun PartnerPreviewScene(creatureId: CreatureId, active: Boolean, modifier: Modifier) {
    val scene = remember { PartnerPreviewState().apply { this.active = active } }
    Box(modifier) {
        SpatialView(
            modifier = Modifier.fillMaxSize().onSizeChanged { scene.viewSize = it },
            update = { _, _ ->
                scene.active = active
            },
        ) { content, _ ->
            scene.attach(content, creatureId)
        }
    }

    DisposableEffect(scene) {
        PartnerPreviewSystem.add(scene)
        onDispose {
            PartnerPreviewSystem.remove(scene)
            scene.dispose()
        }
    }
}

/** 变换分层：owner 保持单位变换，fitRoot 缩放定位，spinner 围绕模型中心旋转。 */
internal class PartnerPreviewState {
    @Volatile var viewSize = IntSize.Zero
    @Volatile var active = true
    private var content: SpatialViewContent? = null
    private var owner: Entity? = null
    private var fitRoot: Entity? = null
    private var spinner: Entity? = null
    private var creature: BattleCreature? = null
    private var preparedSize: IntSize? = null
    private var fittedSize: IntSize? = null
    private var expectedExtent: Vector3? = null
    private var yawDeg = 0f
    private var disposed = false

    @Synchronized
    fun attach(content: SpatialViewContent, creatureId: CreatureId) {
        if (disposed || owner != null) return
        val placementOwner = Entity()
        placementOwner.enabled = active
        val fittedRoot = Entity().apply { enabled = false }
        val rotationRoot = Entity()
        val model = ProceduralCreatureFactory.create(creatureId)
        placementOwner.addChild(fittedRoot)
        fittedRoot.addChild(rotationRoot)
        rotationRoot.addChild(model.root)
        placementOwner.addChild(Entity().apply {
            components.set(DirectionalLightComponent(Color4.WHITE, 900f))
        })
        content.addEntity(placementOwner)
        this.content = content
        owner = placementOwner
        fitRoot = fittedRoot
        spinner = rotationRoot
        creature = model
    }

    @Synchronized
    fun update(context: SceneUpdateContext) {
        val placementOwner = owner ?: return
        if (disposed || placementOwner.scene != context.scene) return
        placementOwner.enabled = active
        if (!active) return
        val fittedRoot = fitRoot ?: return
        val rotationRoot = spinner ?: return
        val model = creature ?: return
        val content = content ?: return
        val size = viewSize
        if (size.width <= 0 || size.height <= 0) {
            fittedRoot.enabled = false
            return
        }

        if (preparedSize != size) {
            fittedRoot.enabled = false
            val halfW = viewHalfWidth(content)
            val halfH = viewHalfHeight(content)
            if (halfW <= 0f || halfH <= 0f) return
            val transform = fittedRoot.components.get(TransformComponent::class.java) ?: return
            // 每次适配从原始变换开始，避免窗口尺寸变化或包围盒晚到时累积缩放。
            transform.setPosition(Vector3.ZERO)
            transform.setScaleVector(Vector3(1f, 1f, 1f))
            rotationRoot.components.get(TransformComponent::class.java)
                ?.setEulerAngles(EulerAngles(0f, 0f, 0f))
            val modelTransform = model.root.components.get(TransformComponent::class.java) ?: return
            modelTransform.setPosition(Vector3.ZERO)
            // 在模型自身坐标中取原始尺寸，不受上一轮视口缩放和自转影响。
            val raw = model.root.getVisualBounds(model.root, recursive = true, enabledOnly = false)
            if (raw.isEmpty()) return
            val rawSize = raw.size
            if (rawSize.y <= 0f) return
            val footprint = kotlin.math.sqrt(rawSize.x * rawSize.x + rawSize.z * rawSize.z)
            val scale = min(
                halfH * 2f * PREVIEW_FILL / rawSize.y,
                halfW * 2f * PREVIEW_FILL / max(footprint, 1e-4f),
            )
            if (!scale.isFinite() || scale <= 0f) return
            modelTransform.setPosition(Vector3(-raw.center.x, -raw.center.y, -raw.center.z))
            transform.setScaleVector(Vector3(scale, scale, scale))
            expectedExtent = Vector3(rawSize.x * scale, rawSize.y * scale, rawSize.z * scale)
            preparedSize = size
            fittedSize = null
            // System 的变换在帧末同步，下一帧再用更新后的包围盒计算深度位置。
            return
        }

        if (fittedSize != size) {
            val transform = fittedRoot.components.get(TransformComponent::class.java) ?: return
            val fitted = model.root.getVisualBounds(placementOwner, recursive = true, enabledOnly = false)
            if (fitted.isEmpty()) return
            val expected = expectedExtent ?: return
            val extent = fitted.size
            if (abs(extent.x - expected.x) > 0.001f ||
                abs(extent.y - expected.y) > 0.001f ||
                abs(extent.z - expected.z) > 0.001f
            ) return
            // 将剩余垂直留白放在下方，让精灵脚部与玩法说明按钮保持间距。
            transform.setPosition(
                Vector3(
                    0f,
                    viewHalfHeight(content) * (1f - PREVIEW_FILL),
                    -(max(0.10f, fitted.size.z * 0.5f) + 0.06f),
                ),
            )
            fittedSize = size
        }

        fittedRoot.enabled = active
        if (!active) return
        val dt = context.deltaTime.coerceIn(0f, 0.05f)
        model.tick(dt)
        yawDeg = (yawDeg + 22f * dt) % 360f
        rotationRoot.components.get(TransformComponent::class.java)
            ?.setEulerAngles(EulerAngles(0f, yawDeg, 0f))
    }

    @Synchronized
    fun dispose() {
        disposed = true
        owner?.destroy(true)
        owner = null
        content = null
        fitRoot = null
        spinner = null
        creature = null
        preparedSize = null
        fittedSize = null
        expectedExtent = null
    }

    private companion object {
        const val PREVIEW_FILL = 0.72f
    }
}

/** 只在伙伴预览存在时注册，并按所属 Scene 分发逐帧更新。 */
internal class PartnerPreviewSystem : SpatialSystem() {
    override fun update(context: SceneUpdateContext) {
        previews.forEach { it.update(context) }
    }

    companion object {
        private val previews = CopyOnWriteArraySet<PartnerPreviewState>()

        @Synchronized
        fun add(preview: PartnerPreviewState) {
            if (previews.isEmpty()) {
                SpatialSystem.register(PartnerPreviewSystem::class.java)
            }
            previews.add(preview)
        }

        @Synchronized
        fun remove(preview: PartnerPreviewState) {
            previews.remove(preview)
            if (previews.isEmpty()) {
                SpatialSystem.unregister(PartnerPreviewSystem::class.java)
            }
        }
    }
}

private fun viewHalfWidth(content: SpatialViewContent): Float {
    val corner = content.convertPosition(
        Vector3.ZERO,
        ViewCoordinateSpace.Local,
        content.localSpatialCoordinateSpace,
    )
    return abs(corner.x)
}

private fun viewHalfHeight(content: SpatialViewContent): Float {
    val corner = content.convertPosition(
        Vector3.ZERO,
        ViewCoordinateSpace.Local,
        content.localSpatialCoordinateSpace,
    )
    return abs(corner.y)
}
