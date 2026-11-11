package com.example.fangkemeng.app

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System as SpatialSystem
import java.util.concurrent.CopyOnWriteArraySet

/** 容器拥有的帧循环，按所属 Scene 分发，离开 Stage 后立即停止。 */
internal class SpatialFrameLoop {
    private var owner: Entity? = null
    private var update: (Float) -> Unit = {}
    private var closed = false

    @Synchronized fun attach(owner: Entity, update: (Float) -> Unit) {
        if (closed) return
        this.owner = owner
        this.update = update
    }

    @Synchronized fun tick(context: SceneUpdateContext) {
        if (!closed && owner?.scene == context.scene) {
            update(context.deltaTime.coerceIn(0f, 0.1f))
        }
    }

    @Synchronized fun close() {
        closed = true
        owner = null
        update = {}
    }
}

internal class GameFrameSystem : SpatialSystem() {
    override fun update(context: SceneUpdateContext) {
        loops.forEach { it.tick(context) }
    }

    companion object {
        private val loops = CopyOnWriteArraySet<SpatialFrameLoop>()
        @Synchronized fun add(loop: SpatialFrameLoop) {
            if (loops.isEmpty()) SpatialSystem.register(GameFrameSystem::class.java)
            loops.add(loop)
        }
        @Synchronized fun remove(loop: SpatialFrameLoop) {
            loop.close()
            loops.remove(loop)
            if (loops.isEmpty()) SpatialSystem.unregister(GameFrameSystem::class.java)
        }
    }
}
